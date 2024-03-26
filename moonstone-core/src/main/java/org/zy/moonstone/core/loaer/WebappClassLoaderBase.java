package org.zy.moonstone.core.loaer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Lifecycle;
import org.zy.moonstone.core.interfaces.container.LifecycleListener;
import org.zy.moonstone.core.interfaces.loader.InstrumentableClassLoader;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.security.PermissionCheck;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.IntrospectionUtils;
import org.zy.moonstone.core.util.compat.JreCompat;
import org.zy.moonstone.core.util.http.FastHttpDateFormat;
import org.zy.moonstone.core.webResources.MoonstoneURLStreamHandlerFactory;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

/**
 * @dateTime 2022年8月22日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class WebappClassLoaderBase extends URLClassLoader implements Lifecycle, InstrumentableClassLoader, PermissionCheck {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 扫描需要关闭的Web应用程序启动的线程时要忽略的线程组名称列表
     */
    private static final List<String> JVM_THREAD_GROUP_NAMES = new ArrayList<>();

    private static final String JVM_THREAD_GROUP_SYSTEM = "system";

    private static final String CLASS_FILE_SUFFIX = ".class";

    static {
        if (!JreCompat.isGraalAvailable()) {
            ClassLoader.registerAsParallelCapable();
        }
        JVM_THREAD_GROUP_NAMES.add(JVM_THREAD_GROUP_SYSTEM);
        JVM_THREAD_GROUP_NAMES.add("RMI Runtime");
    }
    
    /**
     * 此 Web 应用的关联 Web 资源
     */
    protected WebResourceRoot resources = null;

    /**
     * 我们已加载的类和资源的ResourceEntry缓存，key按资源路径而不是二进制名称排序。
     * 路径被用作key，因为资源可以由二进制名称（类）或路径（其他资源，如属性文件）请求，并且从二进制名称到路径的映射是明确的，但反向映射是不明确的。
     */
    protected final Map<String, ResourceEntry> resourceEntries = new ConcurrentHashMap<>();

    /**
     * 这个类加载器应该在搜索自己的存储库(即通常的Java2委托模型) <strong>之前</strong>委托给父类加载器吗?
     * 如果设置为false，这个类加载器将首先搜索自己的存储库，只有在本地找不到类或资源时才委托给父类。注意，默认值false是servlet规范所调用的行为。
     */
    protected boolean delegate = false;

//    private final Map<String,Long> jarModificationTimes = new HashMap<>();
    
    /** 除了web项目类之外的所有需监测资源的映射，包括配置文件(application.properties)和maven pom.xml其余class路径下的文件资源 */
    private final Map<String,Long> resourcesModificationTimes = new HashMap<>();

    /**
     * 如果这个加载器是用于web应用程序上下文，则需要一个读文件权限列表
     */
    protected final ArrayList<Permission> permissionList = new ArrayList<>();

    /**
     * web应用上下文的每个代码源的权限集合
     */
    protected final HashMap<String, PermissionCollection> loaderPC = new HashMap<>();

    /**
     * 安装的SecurityManager实例
     */
    protected final SecurityManager securityManager;

    /**
     * 父级类加载器
     */
    protected final ClassLoader parent;

    /**
     * 用于加载 JavaSE 类的引导类加载器。
     * 在某些实现中，此类加载器始终为空，在这些情况下，{@link ClassLoader#getParent()} 将在系统类加载器上递归调用，并使用最后一个非空结果。
     */
    private ClassLoader javaseClassLoader;
    
    /**
     * 是否应该尝试终止已由 Web 应用程序启动的线程？
     */
    private boolean clearReferencesStopThreads = false;

    /**
     *  是否应该尝试终止任何已由 Web 应用程序启动的 {@link java.util.TimerThread}s？ 如果未指定，将使用默认值 false
     */
    private boolean clearReferencesStopTimerThreads = false;

    /**
     * 如果一个http客户端 keep-alive timer 线程已经被这个 web 应用程序启动并且仍在运行，
     * 是否应该将上下文类加载器从当前的 {@link ClassLoader} 更改为 {@link ClassLoader#getParent()} 以防止内存泄漏？ 
     * 请注意，一旦keep-alives 过期，keep-alive 计时器线程将自行停止，但是在一段时间内可能不会发生在繁忙系统上。
     * 
     */
    private boolean clearReferencesHttpClientKeepAliveThread = true;

    /**
     * 是否应该尝试从 ObjectStream 类缓存中清除对此类加载器加载的类的引用？
     */
    private boolean clearReferencesObjectStreamClassCaches = true;

    /**
     * 是否应该尝试从 ThreadLocals 清除对该类加载器加载类的引用？
     */
    private boolean clearReferencesThreadLocals = true;

    /**
     * 当 Web 应用程序在关闭 JVM 的过程中停止时，是否应该跳过内存泄漏检查？
     */
    private boolean skipMemoryLeakChecksOnJvmShutdown = false;

    /**
     * 保存装饰这个类加载器的类文件转换器。 CopyOnWriteArrayList 是线程安全的。 写入成本很高，但应该很少见。 
     * 读取速度非常快，因为实际上并未使用同步。 重要的是，ClassLoader 永远不会在加载类时对转换器进行阻塞迭代。
     * 
     */
    private final List<ClassFileTransformer> transformers = new CopyOnWriteArrayList<>();

    /**
     * 表示 {@link #addURL(URL)} 被调用的标志，它创建了一个在搜索资源时检查超类的要求
     */
    private boolean hasExternalRepositories = false;

    /**
     * 由这个类而不是超类管理的存储库。
     */
    private List<URL> localRepositories = new ArrayList<>();


    private volatile LifecycleState state = LifecycleState.NEW;
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 构造一个没有定义存储库和父类加载器的新类加载器
     */
    protected WebappClassLoaderBase() {
        super(new URL[0]);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }
    
    /**
     * 构造一个新的ClassLoader，没有定义存储库和给定的父类loader。
     * <p>
     * 此方法通过反射调用
     * 
     * @param parent - 父级加载器
     * @see WebappLoader#createClassLoader()
     */
    protected WebappClassLoaderBase(ClassLoader parent) {
        super(new URL[0], parent);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }
    
	// -------------------------------------------------------------------------------------
	// getter、setter
	// -------------------------------------------------------------------------------------
    /**
     * @return 相关的资源
     */
    public WebResourceRoot getResources() {
        return this.resources;
    }

    /**
     * 设置相关的资源
     * 
     * @param resources - 类加载器将从中加载类的资源
     */
    public void setResources(WebResourceRoot resources) {
        this.resources = resources;
    }

    /**
     * @return 此类加载器的上下文名称
     */
    public String getContextName() {
        if (resources == null) {
            return "Unknown";
        } else {
            return resources.getContext().getBaseName();
        }
    }

    /**
     * 返回此类加载器的“委托优先”标志
     * 
     * @return 如果类查找将首先委托给父级，则为 <code>true</code>。 Servlet 规范中的默认值为 <code>false</code>
     */
    public boolean getDelegate() {
        return this.delegate;
    }

    /**
     * 为此类加载器设置“委托优先”标志。如果此标志为 true，则此类加载器在搜索其自己的存储库之前委托给父类加载器，就像一个普通的(非servlet) Java类装入器链一样。
     * 如果设置为 false （默认），这个类加载器将首先搜索它自己的存储库，并且仅当根据 servlet 规范在本地找不到类或资源时才委托给父级。
     *
     * @param delegate - 新的“委托优先”标志
     */
    public void setDelegate(boolean delegate) {
        this.delegate = delegate;
    }

    /**
     * 如果存在 Java 安全管理器，酌情为给定 URL 的目标创建读取权限
     *
     * @param url - 本地系统上文件或目录的URL
     */
    void addPermission(URL url) {
        if (url == null) {
            return;
        }
        if (securityManager != null) {
        	// 获取此URL的协议名称
            String protocol = url.getProtocol();
            if ("file".equalsIgnoreCase(protocol)) {
                URI uri;
                File f;
                String path;
                try {
                    uri = url.toURI();
                    f = new File(uri);
                    // 这个抽象路径名的规范路径名字符串
                    path = f.getCanonicalPath();
                } catch (IOException | URISyntaxException e) {
                    logger.warn("为非规范文件添加权限, by uri: {}", url.toExternalForm());
                    return;
                }
                if (f.isFile()) {
                    // 允许读取文件
                    addPermission(new FilePermission(path, "read"));
                } else if (f.isDirectory()) {
                    addPermission(new FilePermission(path, "read"));
                    addPermission(new FilePermission(path + File.separator + "-", "read"));
                } else {
                    // 文件不存在-忽略(不应该发生)
                }
            } else {
                // 不支持的URL协议
                logger.warn("WebappClassLoader 不支持的URL协议, by protocol: {}, url: {}", protocol, url.toExternalForm());
            }
        }
    }

    /**
     * 如果存在 Java 安全管理器则添加权限
     */
    void addPermission(Permission permission) {
        if ((securityManager != null) && (permission != null)) {
            permissionList.add(permission);
        }
    }

    public boolean getClearReferencesStopThreads() {
        return this.clearReferencesStopThreads;
    }
    public void setClearReferencesStopThreads(boolean clearReferencesStopThreads) {
        this.clearReferencesStopThreads = clearReferencesStopThreads;
    }
    
    public boolean getClearReferencesStopTimerThreads() {
        return this.clearReferencesStopTimerThreads;
    }
    public void setClearReferencesStopTimerThreads(boolean clearReferencesStopTimerThreads) {
        this.clearReferencesStopTimerThreads = clearReferencesStopTimerThreads;
    }

    public boolean getClearReferencesHttpClientKeepAliveThread() {
        return this.clearReferencesHttpClientKeepAliveThread;
    }
    public void setClearReferencesHttpClientKeepAliveThread(boolean clearReferencesHttpClientKeepAliveThread) {
        this.clearReferencesHttpClientKeepAliveThread = clearReferencesHttpClientKeepAliveThread;
    }

    public boolean getClearReferencesObjectStreamClassCaches() {
        return clearReferencesObjectStreamClassCaches;
    }
    public void setClearReferencesObjectStreamClassCaches(boolean clearReferencesObjectStreamClassCaches) {
        this.clearReferencesObjectStreamClassCaches = clearReferencesObjectStreamClassCaches;
    }

    public boolean getClearReferencesThreadLocals() {
        return clearReferencesThreadLocals;
    }
    public void setClearReferencesThreadLocals(boolean clearReferencesThreadLocals) {
        this.clearReferencesThreadLocals = clearReferencesThreadLocals;
    }

    public boolean getSkipMemoryLeakChecksOnJvmShutdown() {
        return skipMemoryLeakChecksOnJvmShutdown;
    }
    public void setSkipMemoryLeakChecksOnJvmShutdown(boolean skipMemoryLeakChecksOnJvmShutdown) {
        this.skipMemoryLeakChecksOnJvmShutdown = skipMemoryLeakChecksOnJvmShutdown;
    }
    
    
	// -------------------------------------------------------------------------------------
	// Reloader 方法
	// -------------------------------------------------------------------------------------
    /**
     * 将指定的类文件转换器添加到此类加载器。 然后，在调用此方法后，转换器将能够修改由此类加载器加载的任何类的字节码。
     *
     * @param transformer - 添加到类加载器的转换器
     */
    @Override
    public void addTransformer(ClassFileTransformer transformer) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer 不能为 null");
        }

        if (this.transformers.contains(transformer)) {
            // 如果已经添加了该转换器的相同实例，则退出
            logger.warn("重复添加 ClassFileTransformer, by context: {}, transformer: {}", getContextName(), transformer.getClass().getName());
            return;
        }
        this.transformers.add(transformer);

        logger.info("添加 ClassFileTransformer, by context: {}, transformer: {}", getContextName(), transformer.getClass().getName());
    }

    @Override
    public void removeTransformer(ClassFileTransformer transformer) {
        if (transformer == null) {
            return;
        }

        if (this.transformers.remove(transformer)) {
        	logger.info("删除 ClassFileTransformer, by context: {}, transformer: {}", getContextName(), transformer.getClass().getName());
        }
    }

    protected void copyStateWithoutTransformers(WebappClassLoaderBase base) {
        base.resources = this.resources;
        base.delegate = this.delegate;
        base.state = LifecycleState.NEW;
        base.clearReferencesStopThreads = this.clearReferencesStopThreads;
        base.clearReferencesStopTimerThreads = this.clearReferencesStopTimerThreads;
        base.clearReferencesHttpClientKeepAliveThread = this.clearReferencesHttpClientKeepAliveThread;
//        base.jarModificationTimes.putAll(this.jarModificationTimes);
        base.resourcesModificationTimes.putAll(this.resourcesModificationTimes);
        base.permissionList.addAll(this.permissionList);
        base.loaderPC.putAll(this.loaderPC);
    }

    /**
     * 是否修改了一个或多个类或资源，以便重新加载？
     * @return 如果有修改，则为 <code>true</code>
     */
    public boolean modified() {
        for (Entry<String,ResourceEntry> entry : resourceEntries.entrySet()) {
            long cachedLastModified = entry.getValue().lastModified;
            // 获得类加载器路径下资源的最后修改时间
            long lastModified = resources.getWebClassLoaderResource(entry.getKey()).getLastModified();
            if (lastModified != cachedLastModified) {
                if( logger.isDebugEnabled() ) {
                	logger.debug( "Resource Modified. by resource: {}, cachedLastModified: {}, lastModified: {}", entry.getKey(), new Date(cachedLastModified), new Date(lastModified) );
                }
                return true;
            }
        }

        for (Entry<String,Long> entry : resourcesModificationTimes.entrySet()) {
            long cachedLastModified = entry.getValue();
            // 获得类加载器路径下资源的最后修改时间
            long lastModified = resources.getWebClassLoaderResource(entry.getKey()).getLastModified();
            if (lastModified != cachedLastModified) {
                if( logger.isDebugEnabled() ) {
                	logger.debug( "Resource Modified. by resource: {}, cachedLastModified: {}, lastModified: {}", entry.getKey(), 
                			FastHttpDateFormat.formatDayTime(cachedLastModified), FastHttpDateFormat.formatDayTime(lastModified) );
                }
                return true;
            }
        }
        
        // 检查是否已添加或删除JAR
//        WebResource[] jars = resources.listResources("/WEB-INF/lib");

        // 筛选出非JAR资源
//        int jarCount = 0;
//        for (WebResource jar : jars) {
//            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
//                jarCount++;
//                Long recordedLastModified = jarModificationTimes.get(jar.getName());
//                if (recordedLastModified == null) {
//                    // 已添加的Jar
//                    logger.info("jar 新增, by context: [{}], jar: []", resources.getContext().getName(), jar.getName());
//                    return true;
//                }
//                if (recordedLastModified.longValue() != jar.getLastModified()) {
//                    // jar 已被修改
//                    logger.info("jar 被修改, by context: {}", resources.getContext().getName());
//                    return true;
//                }
//            }
//        }

//        if (jarCount < jarModificationTimes.size()){
//            logger.info("jar 被删除, by context: {}", resources.getContext().getName());
//            return true;
//        }

        // 未修改任何类
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
        sb.append("\r\n  context: ");
        sb.append(getContextName());
        sb.append("\r\n  delegate: ");
        sb.append(delegate);
        sb.append("\r\n");
        if (this.parent != null) {
            sb.append("----------> Parent Classloader:\r\n");
            sb.append(this.parent.toString());
            sb.append("\r\n");
        }
        if (this.transformers.size() > 0) {
            sb.append("----------> Class file transformers:\r\n");
            for (ClassFileTransformer transformer : this.transformers) {
                sb.append(transformer).append("\r\n");
            }
        }
        return sb.toString();
    }
    
    
	/**
     * 在我们的本地存储库中找到指定的资源，并返回一个引用它的 URL，如果找不到该资源，则返回 null。
     *
     * @param name - 要查找的资源名称
     */
    @Override
    public URL findResource(final String name) {
        if (logger.isDebugEnabled())
            logger.debug("findLoaclResource(" + name + ")");

        checkStateForResourceLoading(name);

        URL url = null;

        String path = nameToPath(name);

        WebResource resource = resources.getWebClassLoaderResource(path);
        if (resource.exists()) {
            url = resource.getURL();
            trackLastModified(path, resource);
        }

        if ((url == null) && hasExternalRepositories) {
            url = super.findResource(name);
        }

        if (logger.isDebugEnabled()) {
            if (url != null)
                logger.debug("--> Returning '" + url.toString() + "'");
            else
                logger.debug("--> 未找到指定资源, 返回 null");
        }
        return url;
    }

    /**
     * 返回代表具有给定名称的所有资源的 URL 的枚举。 如果没有找到具有此名称的资源，则返回一个空枚举。
     * 
     * @param name - 要查找的资源名称
     * @exception IOException - 如果发生输入/输出错误
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        if (logger.isDebugEnabled())
            logger.debug("findLocalResources(" + name + ")");

        checkStateForResourceLoading(name);

        LinkedHashSet<URL> result = new LinkedHashSet<>();

        String path = nameToPath(name);

        // 添加本地的符合资源
        WebResource[] webResources = resources.getWebClassLoaderResources(path);
        for (WebResource webResource : webResources) {
            if (webResource.exists()) {
                result.add(webResource.getURL());
            }
        }

        // 添加到超类的调用结果
        if (hasExternalRepositories) {
            Enumeration<URL> otherResourcePaths = super.findResources(name);
            while (otherResourcePaths.hasMoreElements()) {
                result.add(otherResourcePaths.nextElement());
            }
        }

        return Collections.enumeration(result);
    }


    /**
     * 查找具有给定名称的资源。资源是可以通过类代码访问的一些数据(图像、音频、文本等)，它们与代码的位置无关。资源的名称是一个“/”分隔的路径名，用于标识资源。如果找不到资源，则返回null。
     * <p>
     * 该方法根据以下算法进行搜索，一旦找到合适的URL就返回。如果不能找到资源，返回null。
     * <ul>
     * 		<li>如果 <code>delegate </code>属性设置为true，调用父类加载器的 <code>getResource()</code> 方法。</li>
     * 		<li>调用 <code>findResource()</code> 在我们本地定义的存储库中查找该资源。</li>
     * 		<li>调用父类加载器的 <code>getResource()</code> 方法，如果有的话。</li>
     * </ul>
     *
     * @param name - 要为其返回 URL 的资源的名称
     * @return 用于读取资源的 URL 对象，如果找不到资源或调用者没有足够的权限来获取资源，则返回 null。
     */
    @Override
	public URL getResource(String name) {
		if (logger.isDebugEnabled())
			logger.debug("getResource(" + name + ")");

		checkStateForResourceLoading(name);

		URL url = null;

		boolean delegateFirst = delegate || filter(name, false);

		// 如必须则委托给父级
		if (delegateFirst) {
			if (logger.isDebugEnabled())
				logger.debug("委托给父级类加载器 " + parent);

			url = parent.getResource(name);
			if (url != null) {
				if (logger.isDebugEnabled())
					logger.debug("-->Returning '" + url.toString() + "'");
				return url;
			}
		}

		// (2) 搜索本地存储库
		url = findResource(name);
		if (url != null) {
			if (logger.isDebugEnabled())
				logger.debug("  --> Returning '" + url.toString() + "'");
			return url;
		}

		// (3) 如果尚未尝试，则无条件地委托给父级
		if (!delegateFirst) {
			url = parent.getResource(name);
			if (url != null) {
				if (logger.isDebugEnabled())
					logger.debug("--> Returning '" + url.toString() + "'");
				return url;
			}
		}

		// (4) 找不到资源
		if (logger.isDebugEnabled())
			logger.debug("--> 资源未找到, 返回 null");
		return null;
	}

    
    /**
     * 查找具有给定名称的所有资源。 资源是可以由类代码以独立于代码位置的方式访问的一些数据（图像、音频、文本等）。
     * <p>
     * 资源的名称是标识资源的 / 分隔的路径名。
     * <p>
     * getResource(String) 的文档中描述了搜索顺序。
     * 
     * @param name - 资源名称
     * @return 资源的 URL 对象的枚举。 如果找不到资源，则枚举为空。 类加载器无权访问的资源不会出现在枚举中。
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
		if (logger.isDebugEnabled())
			logger.debug("getResourcesToEnumeration(" + name + ")");
		
        Enumeration<URL> parentResources = getParent().getResources(name);
        Enumeration<URL> localResources = findResources(name);

        // 需要合并这些枚举。组合枚举的顺序取决于如何配置委托
        boolean delegateFirst = delegate || filter(name, false);

        if (delegateFirst) {
            return new CombinedEnumeration(parentResources, localResources);
        } else {
            return new CombinedEnumeration(localResources, parentResources);
        }
    }


    /**
     * 查找具有给定名称的资源，并返回可用于读取它的输入流。 
     * 搜索顺序如 <code>getResource()</code> 所述，在检查资源数据是否先前已被缓存之后。 
     * 如果找不到资源，则返回 <code>null</code>。
     *
     * @param name - 要为其返回输入流的资源的名称
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        if (logger.isDebugEnabled())
            logger.debug("getResourceAsStream(" + name + ")");

        checkStateForResourceLoading(name);

        InputStream stream = null;

        boolean delegateFirst = delegate || filter(name, false);

        // (1) 如必须则委托父级
        if (delegateFirst) {
            if (logger.isDebugEnabled())
                logger.debug("  委托给父级类加载器 " + parent.getClass().getName());
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (logger.isDebugEnabled())
                    logger.debug("--> Returning stream, by parent: {}", parent.getClass().getName());
                return stream;
            }
        }

        // (2) 搜索本地存储库
        String path = nameToPath(name);
        if (logger.isDebugEnabled())
        	logger.debug("Search Local Repository. by path: {}", path);
        
        WebResource resource = resources.getWebClassLoaderResource(path);
        if (resource.exists()) {
            stream = resource.getInputStream();
            trackLastModified(path, resource);
        }
        
        try {
            if (hasExternalRepositories && stream == null) {
            	// 本地存储库未找到则委派父级
                URL url = super.findResource(name);
                if (url != null) {
                    stream = url.openStream();
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        if (stream != null) {
            if (logger.isDebugEnabled())
                logger.debug("--> Returning stream, by local: {}", this.getClass().getName());
            return stream;
        }

        // (3) 无条件委托给父级
        if (!delegateFirst) {
            if (logger.isDebugEnabled())
                logger.debug("  无条件委托给父级 " + parent);
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (logger.isDebugEnabled())
                    logger.debug("--> Returning stream, by parent: {}", parent.getClass().getName());
                return stream;
            }
        }

        // (4) 找不到资源
        if (logger.isDebugEnabled())
            logger.debug("--> 未找到指定资源, 返回 null");
        return null;
    }


    /**
     * 加载具有指定名称的类。 此方法以与 <code>loadClass(String, boolean)</code> 相同的方式搜索类，第二个参数为 <code>false</code>。
     *
     * @param name - 要加载的类的全限定类名称
     *
     * @exception ClassNotFoundException - 如果未找到类
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }


    /**
     * 加载具有指定名称的类，使用以下算法进行搜索，直到找到并返回该类。如果找不到该类，返回ClassNotFoundException。
     * <ul>
     * 		<li>调用findloaddclass (String)检查类是否已经加载。如果有，则返回相同的Class对象。</li>
     * 		<li>如果delegate属性设置为true，调用父类加载器的loadClass()方法。</li>
     * 		<li>调用findClass()在本地定义的存储库中找到这个类。</li>
     * 		<li>调用父类加载器的loadClass()方法，如果有的话。</li>
     * </ul>
     * 如果使用上述步骤找到了类，并且resolve标志为真，则该方法将对生成的class对象调用resolveClass(class)。
     * 
     * @param name - 要加载的类的二进制名称
     * @param resolve - 如果为<code>true</code>，则解析类
     *
     * @exception ClassNotFoundException - 如果未找到类
     */
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (JreCompat.isGraalAvailable() ? this : getClassLoadingLock(name)) {
            if (logger.isDebugEnabled())
                logger.debug("WebappClassLoaderBase#loadClass(" + name + ", " + resolve + ")");
            Class<?> clazz = null;

            // 记录对停止的类加载器的访问
            checkStateForClassLoading(name);

            // (0) 检查之前加载的本地类缓存
            clazz = findLoadedClass0(name);
            if (clazz != null) {
                if (logger.isDebugEnabled())
                    logger.debug("Returning Class From Cache. class: [{}]", clazz);
                if (resolve)
                	/*
                	 * 链接指定的类。类加载器可能使用此（命名错误）方法来链接类。如果类c已经被链接，那么这个方法简单地返回。
                	 * 否则，类将按照Java™语言规范的“执行”一章中描述的那样进行链接。
                	 * 
                	 * @param c - 要链接的类
                	 * @exception NullPointerException - 如果 c 为 null
                	 */
                    resolveClass(clazz);
                return clazz;
            }

            // (0.1) 检查父类之前加载的类缓存
            /*
             * findLoadedClass:
             * 如果Java虚拟机已将此加载器记录为具有给定二进制名称的类的初始化加载器，则返回具有给定二进制名的类。否则返回null。
             * 
             * @param name - 全限定类名
             * @return Class 对象，如果尚未加载该类，则为 null
             */
            clazz = JreCompat.isGraalAvailable() ? null : findLoadedClass(name);
            if (clazz != null) {
                if (logger.isDebugEnabled())
                    logger.debug("Returning Class From Cache On Parent. class: [{}]", clazz);
                if (resolve)
                    resolveClass(clazz);
                return clazz;
            }

            // (0.2) 尝试使用系统类加载器加载类，以防止 webapp 覆盖 Java SE 类。 这实现了 SRV.10.7.2
            String resourceName = binaryNameToPath(name, false);

            ClassLoader javaseLoader = getJavaseClassLoader();
            boolean tryLoadingFromJavaseLoader;
            try {
            	/*
            	 * 使用 getResource，因为如果 Java SE 类加载器中的资源不可用，它不会触发昂贵的 ClassNotFoundException。 
            	 * 但是（有关详细信息，请参阅 https://bz.apache.org/bugzilla/show_bug.cgi?id=58125）在安全管理器下运行时，在极少数情况下，此调用可能会触发 ClassCircularityError。 
            	 * 有关这如何触发 StackOverflowError 的详细信息，请参阅 https://bz.apache.org/bugzilla/show_bug.cgi?id=61424。
            	 * 鉴于这些报告的错误，捕获 Throwable 以确保也捕获任何其他边缘情况
            	 */
                URL url;
                if (securityManager != null) {
                    PrivilegedAction<URL> dp = new PrivilegedJavaseGetResource(resourceName);
                    url = AccessController.doPrivileged(dp);
                } else {
                	/*
                	 * getResource:
                	 * 查找具有给定名称的资源。 资源是可以由类代码以独立于代码位置的方式访问的一些数据（图像、音频、文本等）。
                	 * 
                	 * 资源的名称是一个以“/”分隔的路径名，用于标识该资源。
                	 * 
                	 * 该方法将首先在父类加载器中搜索资源； 如果 parent 为 null，则搜索内置到虚拟机的类加载器的路径。 如果失败，此方法将调用 findResource(String) 来查找资源。
                	 * 
                	 * @param name: 资源名称
                	 * @return 用于读取资源的URL对象，如果找不到资源或调用者没有足够的权限获取资源，则为null。
                	 */
                    url = javaseLoader.getResource(resourceName);
                }
                tryLoadingFromJavaseLoader = (url != null);
            } catch (Throwable t) {
                // 吞下除必须重新抛出的异常之外的所有异常
                ExceptionUtils.handleThrowable(t);
                // getResource()技巧对这个类不起作用。必须尝试直接加载它，并接受可能会得到ClassNotFoundException的事实
                tryLoadingFromJavaseLoader = true;
            }

            if (tryLoadingFromJavaseLoader) {
                try {
                	/**
                	 * 加载具有指定二进制名称的类。此方法以与 loadClass(String, boolean) 方法相同的方式搜索类。 Java 虚拟机调用它来解析类引用。 调用此方法等效于调用 loadClass(name,false)。
                	 * 
                	 * @param name: 类的二进制名称
                	 * @return 生成的 Class 对象
                	 * @exception ClassNotFoundException - 如果找不到类
                	 */
                    clazz = javaseLoader.loadClass(name);
                    if (clazz != null) {
                    	 if (logger.isDebugEnabled())
                             logger.debug("Loaded From JavaseLoader. clazz: [{}], javaseLoader: {}", clazz, javaseLoader);
                        if (resolve)
                            resolveClass(clazz);
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (0.5) 使用 SecurityManager 时访问此类的权限
            if (securityManager != null) {
                int i = name.lastIndexOf('.');
                if (i >= 0) {
                    try {
                        securityManager.checkPackageAccess(name.substring(0,i));
                    } catch (SecurityException se) {
                        String error = "限制访问的包, by name: " + name;
                        logger.info(error, se);
                        throw new ClassNotFoundException(error, se);
                    }
                }
            }

            boolean delegateLoad = delegate || filter(name, true);

            // (1) 如必须则委派给父级
            if (delegateLoad) {
                if (logger.isDebugEnabled())
                	logger.debug("Delegating to parent classloader. parent: {}", parent.getClass().getName());
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (logger.isDebugEnabled())
                            logger.debug("Loaded Class From Parent. by parent: {}, clazz: [{}]", parent.getClass().getName(), clazz);
                        if (resolve)
                            resolveClass(clazz);
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (2) 搜索本地存储库
            if (logger.isDebugEnabled())
                logger.debug("Search Local Repository.");
            try {
                clazz = findClass(name);
                if (clazz != null) {
                    if (logger.isDebugEnabled())
                        logger.debug("Load Class From Local Repository. by clazz: [{}]", this.getClass().getTypeName());
                    if (resolve)
                        resolveClass(clazz);
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }

            // (3) 无条件委派父级
            if (!delegateLoad) {
                if (logger.isDebugEnabled())
                    logger.debug("Finally choose to delegate to the parent class loader to try loading: " + parent);
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (logger.isDebugEnabled())
                            logger.debug("Loaded Class From Parent. by parent: {}, clazz: [{}]", parent.getClass().getName(), clazz);
                        if (resolve)
                            resolveClass(clazz);
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }
        }

        throw new ClassNotFoundException(name);
    }


    // -------------------------------------------------------------------------------------
	// ClassLoader 方法
	// -------------------------------------------------------------------------------------
	// Note: 暴露供测试使用
	protected final Class<?> doDefineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
	    return super.defineClass(name, b, off, len, protectionDomain);
	}

	/**
	 * 如果可能，在本地存储库中查找指定的类。如果未找到，则抛出 <code>ClassNotFoundException</code>。
	 *
	 * @param name - 要加载的类的二进制名称
	 * @return 生成的类
	 * @exception ClassNotFoundException - 如果找不到类
	 */
	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
	    if (logger.isDebugEnabled()) {
	    	logger.debug("WebappClassLoaderBase#findClass(" + name + ")");
	    }
	
	    checkStateForClassLoading(name);
	
	    // (1) 使用SecurityManager时定义此类的权限
	    if (securityManager != null) {
	        int i = name.lastIndexOf('.');
	        if (i >= 0) {
	            try {
	                if (logger.isTraceEnabled()) 
	                	logger.trace("SecurityManager#checkPackageDefinition");
	                
	                /**
	                 * 如果不允许调用线程在参数指定的包中定义类，则抛出 SecurityException。
	                 * <p>
	                 * 这个方法被一些类加载器的 loadClass 方法使用。
	                 * <p>
	                 * 此方法首先通过从对 java.security.Security.getProperty("package.definition") 的调用中获取逗号分隔列表来获取受限包列表，并检查 pkg 是否以或等于任何受限包开头。
	                 *  如果是这样，则使用 RuntimePermission("defineClassInPackage."+pkg) 权限调用 checkPermission。
	                 * <p>
	                 * 如果此方法被覆盖，则 super.checkPackageDefinition 应作为被覆盖方法的第一行调用。
	                 * 
	                 * @parme pkg - 包名
	                 * @exception SecurityException - 如果调用线程没有在指定包中定义类的权限
	                 */
	                securityManager.checkPackageDefinition(name.substring(0, i));
	            } catch (Exception se) {
	                if (logger.isTraceEnabled()) 
	                	logger.trace("-->Exception-->ClassNotFoundException", se);
	                throw new ClassNotFoundException(name, se);
	            }
	        }
	    }
	
	    // 如果可能的话，请求父类查找此类(如果没有找到，抛出ClassNotFoundException)
	    Class<?> clazz = null;
	    try {
	        if (logger.isDebugEnabled())
	            logger.debug("WebappClassLoaderBase#findClassInternal(" + name + ")");
	        try {
	            if (securityManager != null) {
	                PrivilegedAction<Class<?>> dp = new PrivilegedFindClassByName(name);
	                clazz = AccessController.doPrivileged(dp);
	            } else {
	            	// 查找本地仓库
	                clazz = findClassInternal(name);
	            }
	        } catch(AccessControlException ace) {
	            logger.warn("SecurityException, by name: " + name + ", message: " + ace.getMessage(), ace);
	            throw new ClassNotFoundException(name, ace);
	        } catch (RuntimeException e) {
	            if (logger.isTraceEnabled())
	                logger.trace("-->RuntimeException Rethrown", e);
	            throw e;
	        }
	        if ((clazz == null) && hasExternalRepositories) {
	            try {
	            	// 查找父类缓存
	                clazz = super.findClass(name);
	            } catch(AccessControlException ace) {
	            	logger.warn("SecurityException, by name: {}, message: {}", name, ace.getMessage(), ace);
	                throw new ClassNotFoundException(name, ace);
	            } catch (RuntimeException e) {
	                if (logger.isTraceEnabled())
	                    logger.trace("-->RuntimeException Rethrown", e);
	                throw e;
	            }
	        }
	        if (clazz == null) {
	            if (logger.isDebugEnabled())
	                logger.debug("--> Returning ClassNotFoundException");
	            throw new ClassNotFoundException(name);
	        }
	    } catch (ClassNotFoundException e) {
	        if (logger.isTraceEnabled())
	            logger.trace("--> Passing on ClassNotFoundException");
	        throw e;
	    }
	
	    // 返回找到的类
	    if (logger.isDebugEnabled()) {
	    	ClassLoader cl;
	    	if (Globals.IS_SECURITY_ENABLED){
	    		cl = AccessController.doPrivileged(new PrivilegedGetClassLoader(clazz));
	    	} else {
	    		cl = clazz.getClassLoader();
	    	}
	    	logger.debug("Returning class. clazz: [{}], loaded By: {}", clazz, cl.getClass().getSimpleName());
	    }
	
	    return clazz;
	}

	/**
	 * 如果该类加载器先前已加载并缓存了具有给定名称的类，则查找具有给定名称的类，并返回 Class 对象。如果尚未缓存该类，则返回 <code>null</code>。
	 *
	 * @param name - 要返回的资源的二进制名称
	 * @return 加载的类
	 */
	protected Class<?> findLoadedClass0(String name) {
	    String path = binaryNameToPath(name, true);
	
	    ResourceEntry entry = resourceEntries.get(path);
	    if (entry != null) {
	    	
	        return entry.loadedClass;
	    }
	    return null;
	}

	/**
	 * 在本地存储库中查找指定的类
	 *
	 * @param name - 要加载的类的二进制名称
	 * @return 加载的类，如果找不到该类，则返回 null
	 */
	protected Class<?> findClassInternal(String name) {
	    checkStateForResourceLoading(name);
	
	    if (name == null) {
	        return null;
	    }
	    String path = binaryNameToPath(name, true);
	
	    ResourceEntry entry = resourceEntries.get(path);
	    WebResource resource = null;
	
	    if (entry == null) {
	        resource = resources.getWebClassLoaderResource(path);
	
	        if (!resource.exists()) {
	            return null;
	        }
	
	        entry = new ResourceEntry();
	        entry.lastModified = resource.getLastModified();
	
	        // 在本地资源库中添加条目
	        synchronized (resourceEntries) {
	            // 确保所有竞相加载特定类的线程最终都使用相同的ResourceEntry实例
	            ResourceEntry entry2 = resourceEntries.get(path);
	            if (entry2 == null) {
	                resourceEntries.put(path, entry);
	            } else {
	                entry = entry2;
	            }
	        }
	    }
	
	    Class<?> clazz = entry.loadedClass;
	    if (clazz != null)
	        return clazz;
	
	    /**
	     * getClassLoadingLock(String): 
	     * 返回类加载操作的锁对象。为了向后兼容，该方法的默认实现如下。 
	     * 如果此 ClassLoader 对象注册为具有并行能力，则该方法返回与指定类名关联的专用对象。 
	     * 否则，该方法返回此 ClassLoader 对象。
	     */
	    synchronized (JreCompat.isGraalAvailable() ? this : getClassLoadingLock(name)) {
	        clazz = entry.loadedClass;
	        if (clazz != null)
	            return clazz;
	
	        if (resource == null) {
	            resource = resources.getWebClassLoaderResource(path);
	        }
	
	        if (!resource.exists()) {
	            return null;
	        }
	
	        byte[] binaryContent = resource.getContent();
	        if (binaryContent == null) {
	            // 读取类字节出现问题（并将在 DEBUG 级别记录）
	            return null;
	        }
	        Manifest manifest = resource.getManifest();
	        URL codeBase = resource.getCodeBase();
	        Certificate[] certificates = resource.getCertificates();
	
	        if (transformers.size() > 0) {
	            // 如果资源是刚刚加载的类，则使用任何附加的转换器来装饰它
	
	        	// 忽略 头'/'和末尾 CLASS_FILE_SUFFIX 要比替换 name 中的 '.' 为 '/' 要高效
	            String internalName = path.substring(1, path.length() - CLASS_FILE_SUFFIX.length());
	
	            for (ClassFileTransformer transformer : this.transformers) {
	                try {
	                    byte[] transformed = transformer.transform(this, internalName, null, null, binaryContent);
	                    if (transformed != null) {
	                        binaryContent = transformed;
	                    }
	                } catch (IllegalClassFormatException e) {
	                    logger.error("ClassFileTransformer 转换错误, by name: " + name, e);
	                    return null;
	                }
	            }
	        }
	
	        // 查看 package
	        String packageName = null;
	        int pos = name.lastIndexOf('.');
	        if (pos != -1)
	            packageName = name.substring(0, pos);
	
	        Package pkg = null;
	        if (packageName != null) {
	            pkg = getPackage(packageName);
	            if (pkg == null) {
	                try {
	                    if (manifest == null) {
	                    	/**
	                    	 * definePackage(...): 
	                    	 * 在此ClassLoader中按名称定义包。这允许类加载器为其类定义包。
	                    	 * 必须在定义类之前创建包，包的名称在类装入器中必须是唯一的，并且一旦创建就不能重新定义或更改
	                    	 * 
	                    	 * @param name: 包名
	                    	 * @param specTitle: 规范的标题
	                    	 * @param specVersion: 规范版本
	                    	 * @param specVendor: 规范供应商
	                    	 * @param implTitle: 实现的标题
	                    	 * @param implVersion: 此实现的版本
	                    	 * @param implVendor: 提供此实现的组织、供应商或公司的名称
	                    	 * @param sealBase: 如果不为 null，则此包相对于给定的代码源 URL 对象是密封的。 否则，包装不密封。
	                    	 * @return 新定义的Package对象
	                    	 * 
	                    	 * @exception IllegalArgumentException - 如果包名称与此类加载器或其祖先之一中的现有包重复
	                    	 */
	                        definePackage(packageName, null, null, null, null, null, null, null);
	                    } else {
	                    	/**
	                    	 * definePackage():
	                    	 * 在此 ClassLoader 中按名称定义新包。指定 Manifest 中包含的属性将用于获取包版本和封装信息。
	                    	 * 对于封装包，附加URL指定从中加载包的代码源URL。
	                    	 * 
	                    	 * @param name: 包名
	                    	 * @param man:  包含包版本和封装信息的Manifest
	                    	 * @param url: 包的源代码url，如果没有则为null
	                    	 * @return 新定义的Package对象
	                    	 * 
	                    	 * @exception IllegalArgumentException - 如果包名称与此类加载器或其祖先之一中的现有包重复
	                    	 */
	                        definePackage(packageName, manifest, codeBase);
	                    }
	                } catch (IllegalArgumentException e) {
	                    // 忽略:由于包的双重定义导致的正常错误
	                }
	                /**
	                 * 返回一个由该类加载器或其任何父级类加载器定义的包。
	                 */
	                pkg = getPackage(packageName);
	            }
	        }
	
	        if (securityManager != null) {
	            if (pkg != null) {
	                boolean sealCheck = true;
	                /**
	                 * @return 如果包是密封的，则为 true ，否则为 false
	                 */
	                if (pkg.isSealed()) {
	                	/**
	                	 * @param url: 代码源地址
	                	 * @return 如果此包相对于指定的代码源 url 是密封的，则返回 true
	                	 */
	                    sealCheck = pkg.isSealed(codeBase);
	                } else {
	                    sealCheck = (manifest == null) || !isPackageSealed(packageName, manifest);
	                }
	                if (!sealCheck)
	                    throw new SecurityException("密封违规加载 " + name + " : Package " + packageName + " 已密封.");
	            }
	        }
	
	        try {
	        	/**
	        	 * defineClass(...)
	        	 * 使用可选的CodeSource将字节数组转换为类class的实例。在使用类之前，必须先解析该类。
	        	 * <p>
	        	 * 如果提供了一个非空的CodeSource，则构造保护域并将其与所定义的类相关联。
	        	 * 
	        	 * @param name - 类的预期名称，如果未知，则为 null，使用 '.' 而不是 '/' 作为分隔符并且没有尾随的“.class”后缀。
	        	 * @param b - 构成类数据的字节。 从 off 到位置 off+len-1 的字节应具有Java™ 虚拟机规范定义的有效类文件的格式。
	        	 * @param off - 类数据在 b 中的起始偏移量
	        	 * @param len - 类数据的长度
	        	 * @param cs - 关联的 CodeSource，如果没有则为 null
	        	 * @return 从数据创建的 Class 对象和可选的 CodeSource。
	        	 * 
	        	 * @exception ClassFormatError - 如果数据不包含有效的类
	        	 * @exception IndexOutOfBoundsException - 如果 off 或 len 为负数，或者 off+len 大于 b.length
	        	 * @exception SecurityException - 如果试图将此类添加到包含由与此类不同的一组证书签名的类的包中，或者如果类名以“java.”开头。
	        	 */
	            clazz = defineClass(name, binaryContent, 0, binaryContent.length, new CodeSource(codeBase, certificates));
	        } catch (UnsupportedClassVersionError ucve) {
	            throw new UnsupportedClassVersionError("错误的版本号, by name: " + name + " " + ucve.getLocalizedMessage() );
	        }
	        entry.loadedClass = clazz;
	        if (logger.isDebugEnabled()) {
	        	logger.debug("Add ResourceEntry Cache. loadedClass: [{}], lastModified: {}, url: {}", 
	        			clazz, FastHttpDateFormat.formatDayTime(entry.lastModified), codeBase);
	        }
	    }
	
	    return clazz;
	}

	protected void checkStateForClassLoading(String className) throws ClassNotFoundException {
        // 一旦 Web 应用程序停止，就不允许加载新的类
        try {
            checkStateForResourceLoading(className);
        } catch (IllegalStateException ise) {
            throw new ClassNotFoundException(ise.getMessage(), ise);
        }
    }
	
	/**
     * 检查资源加载时状态
     * @param resource - 资源路径
     * @throws IllegalStateException - 若资源状态为不可用 
     */
    protected void checkStateForResourceLoading(String resource) throws IllegalStateException {
        // 停止Web应用程序后，不允许加载资源
        if (!state.isAvailable()) {
            String msg = "Web应用程序已停止, 不允许加载资源, by resource: " + resource;
            IllegalStateException ise = new IllegalStateException(msg);
            logger.info(msg, ise);
            throw ise;
        }
    }
    
    
    /**
	 * Filter classes.
	 *
	 * @param name - 类名
	 * @param isClassName - 如果name是类名，则为 <code>true</code>，如果name是资源名，则为 <code>false</code>
	 * @return 如果应过滤该类，则为 <code>true</code>
	 */
	protected boolean filter(String name, boolean isClassName) {
	    if (name == null)
	        return false;
	
	    char ch;
	    if (name.startsWith("javax")) {
	        /* 5 == length("javax") */
	        if (name.length() == 5) {
	            return false;
	        }
	        ch = name.charAt(5);
	        if (isClassName && ch == '.') {
	            /* 6 == length("javax.") */
	            if (name.startsWith("servlet.jsp.jstl.", 6)) {
	                return false;
	            }
	            if (name.startsWith("el.", 6) ||
	                name.startsWith("servlet.", 6) ||
	                name.startsWith("websocket.", 6) ||
	                name.startsWith("security.auth.message.", 6)) {
	                return true;
	            }
	        } else if (!isClassName && ch == '/') {
	            /* 6 == length("javax/") */
	            if (name.startsWith("servlet/jsp/jstl/", 6)) {
	                return false;
	            }
	            if (name.startsWith("el/", 6) ||
	                name.startsWith("servlet/", 6) ||
	                name.startsWith("websocket/", 6) ||
	                name.startsWith("security/auth/message/", 6)) {
	                return true;
	            }
	        }
	    }
	    return false;
	}

	/**
     * 全限定类名转为资源路径
     * @param binaryName - 全限定类名
     * @param withLeadingSlash - 带前导斜杠
     * @return 转换的资源路径
     */
    private String binaryNameToPath(String binaryName, boolean withLeadingSlash) {
        // 1 为 头'/', 6 为 ".class"
        StringBuilder path = new StringBuilder(7 + binaryName.length());
        if (withLeadingSlash) {
            path.append('/');
        }
        path.append(binaryName.replace('.', '/'));
        path.append(CLASS_FILE_SUFFIX);
        return path.toString();
    }
    
    /**
     * 名称转换为路径
     * @param name - 资源名称
     * @return 转换的资源路径
     */
    private String nameToPath(String name) {
        if (name.startsWith("/")) {
            return name;
        }
        StringBuilder path = new StringBuilder(1 + name.length());
        path.append('/');
        path.append(name);
        return path.toString();
    }
    
    /**
	 * 记录最后修改时间
	 * @param path
	 * @param resource
	 */
	private void trackLastModified(String path, WebResource resource) {
	    if (resourceEntries.containsKey(path)) {
	        return;
	    }
	    ResourceEntry entry = new ResourceEntry();
	    entry.lastModified = resource.getLastModified();
	    synchronized(resourceEntries) {
	        resourceEntries.putIfAbsent(path, entry);
	    }
	}


	// -------------------------------------------------------------------------------------
	// PermissionCheck 方法
	// -------------------------------------------------------------------------------------
	/**
	 * 获取 CodeSource 的权限。 如果此 WebappClassLoaderBase 实例用于 Web 应用程序上下文，请为适当的资源添加读文件权限。
	 *
	 * @param codeSource - 代码加载位置
	 * @return CodeSource 的权限集合
	 */
	@Override
	protected PermissionCollection getPermissions(CodeSource codeSource) {
	    String codeUrl = codeSource.getLocation().toString();
	    PermissionCollection pc;
	    if ((pc = loaderPC.get(codeUrl)) == null) {
	    	/**
	    	 * 返回给定 CodeSource（代码源）对象的权限。该方法的实现首先调用super.getPermissions()，然后根据codesource的URL添加权限
	    	 * 
	    	 * 如果该URL的协议为 “jar”，则授予的权限是基于 Jar 文件的URL所需要的权限。
	    	 * 
	    	 * 如果协议是“file”并且存在权限组件，则可以授予连接到该权限并接受来自该权限连接的权限。
	    	 * 如果协议为“file”，且路径指定了一个文件，则授予读取该文件的权限。
	    	 * 如果协议为“file”，路径为目录，则授予读取该目录下所有文件以及(递归)所有文件和子目录的权限。
	    	 * 
	    	 * 如果协议不是“文件”，则授予连接URL主机和接受URL主机连接的权限。
	    	 * 
	    	 * @param codesource: 代码源对象
	    	 * @return 授予代码源的权限
	    	 * @exception NullPointerException - 如果 codesource 为空
	    	 */
	        pc = super.getPermissions(codeSource);
	        if (pc != null) {
	            for (Permission p : permissionList) {
	                pc.add(p);
	            }
	            loaderPC.put(codeUrl,pc);
	        }
	    }
	    return pc;
	}

    @Override
    public boolean check(Permission permission) {
        if (!Globals.IS_SECURITY_ENABLED) {
            return true;
        }
        Policy currentPolicy = Policy.getPolicy();
        if (currentPolicy != null) {
            URL contextRootUrl = resources.getResource("/").getCodeBase();
            CodeSource cs = new CodeSource(contextRootUrl, (Certificate[]) null);
            PermissionCollection pc = currentPolicy.getPermissions(cs);
            if (pc.implies(permission)) {
                return true;
            }
        }
        return false;
    }

    
	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    public void addLifecycleListener(LifecycleListener listener) {}


    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return new LifecycleListener[0];
    }


    @Override
    public void removeLifecycleListener(LifecycleListener listener) {}


    @Override
    public LifecycleState getState() {
        return state;
    }


    @Override
    public String getStateName() {
        return getState().toString();
    }


    @Override
    public void init() {
        state = LifecycleState.INITIALIZED;
    }

    /**
     * 追加监视的资源
     * @param webResource - 资源对象
     * @param exposedURL - 是否暴露此资源的URL
     * @see #getURLs()
     */
    public void additionalResourceMonitoring(WebResource webResource, boolean exposedURL) {
    	if (exposedURL) {
    		localRepositories.add(webResource.getURL());
    	}
       this.resourcesModificationTimes.put(webResource.getWebappPath(), Long.valueOf(webResource.getLastModified()));
    }
    
    /**
     * 启动类加载器
     *
     * @exception LifecycleException - 如果发生生命周期错误
     */
    @Override
    public void start() throws LifecycleException {
        state = LifecycleState.STARTING_PREP;

//        WebResource[] classesResources = resources.getResources("/WEB-INF/classes");
//        for (WebResource classes : classesResources) {
//            if (classes.isDirectory() && classes.canRead()) {
//                localRepositories.add(classes.getURL());
//            }
//        }
//        WebResource[] jars = resources.listResources("/WEB-INF/lib");
//        for (WebResource jar : jars) {
//            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
//                localRepositories.add(jar.getURL());
//                jarModificationTimes.put(jar.getName(), Long.valueOf(jar.getLastModified()));
//            }
//        }
        // 监视类加载路径下的class文件是否有修改
        resources.additionalResourceMonitoring(this);
        
        // fluorite 支持的配置文件路径
        WebResource resource = resources.getResource("/config/application.properties");
        if (resource.exists()) {
        	localRepositories.add(resource.getURL());
    		resourcesModificationTimes.put( resource.getName(), Long.valueOf( resource.getLastModified() ));
        }
        WebResource applicationProperties = resources.getResource("/application.properties");
        if (applicationProperties.exists()) {
        	localRepositories.add(applicationProperties.getURL());
        	resourcesModificationTimes.put( applicationProperties.getName(), Long.valueOf( applicationProperties.getLastModified() ));
        }
        
        // maven 项目支持
        WebResource[] mavenResources = resources.getResources("/META-INF/maven");
        for (WebResource webResource : mavenResources) {
        	if (webResource.exists() && webResource.getName().equals("pom.xml")) {
        		resourcesModificationTimes.put( resource.getName(), Long.valueOf( resource.getLastModified() ));
        		break;
        	}
        	
        	File file = new File(webResource.getCanonicalPath());
   		 	StringBuilder builder = new StringBuilder();
   		 	builder.append("/META-INF/maven/");

        	findMavenPomXml(file, "pom.xml", webResource, builder);
		}
        
        state = LifecycleState.STARTED;
    }

    private boolean findMavenPomXml(File file, String fileName, WebResource resource, StringBuilder builder) {
		File[] listFiles = file.listFiles();
		for (File file2 : listFiles) {
			if (file2.isDirectory()) {
				builder.append(file2.getName()).append("/");
				boolean flag = findMavenPomXml(file2, fileName, resource, builder);
				if (flag) {
					return flag;
				}
			} else if (file2.getName().equals(fileName)) {
//				fileName = file.getPath().substring(resources.getContext().getDocBase().length());
        		resourcesModificationTimes.put( builder.toString() + fileName, Long.valueOf( file2.lastModified() ));
				return true;
			}
		}
		
		return false;
    }
    
    /**
     * 停止类加载器
     *
     * @exception LifecycleException - 如果发生生命周期错误
     */
    @Override
    public void stop() throws LifecycleException {
        state = LifecycleState.STOPPING_PREP;

        clearReferences();

        state = LifecycleState.STOPPING;

        resourceEntries.clear();
//        jarModificationTimes.clear();
        resourcesModificationTimes.clear();
        resources = null;

        permissionList.clear();
        loaderPC.clear();

        state = LifecycleState.STOPPED;
    }


    @Override
    public void destroy() {
        state = LifecycleState.DESTROYING;

        try {
            super.close();
        } catch (IOException ioe) {
            logger.warn("WebappClassLoader 父级类加载器 Close 失败", ioe);
        }
        state = LifecycleState.DESTROYED;
    }


	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    protected ClassLoader getJavaseClassLoader() {
        return javaseClassLoader;
    }

    protected void setJavaseClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("WebappClassLoader javaseClassLoader 不能为 Null");
        }
        javaseClassLoader = classLoader;
    }

    /**
     * 清除引用
     */
    protected void clearReferences() {
        // 如果JVM正在关闭，则跳过内存泄漏检查
        if (skipMemoryLeakChecksOnJvmShutdown && !resources.getContext().getParent().getState().isAvailable()) {
            // 在重新加载/重新部署期间，父进程预计可用。父进程不可用，因此可能是JVM关闭。
            try {
            	// 一旦关闭序列开始，就不可能注册新的 ShutdownHook 或取消之前注册的 ShutdownHook 。尝试这些操作中的任何一个都将导致抛出 IllegalStateException。
                Thread dummyHook = new Thread();
                Runtime.getRuntime().addShutdownHook(dummyHook);
                Runtime.getRuntime().removeShutdownHook(dummyHook);
            } catch (IllegalStateException ise) {
                return;
            }
        }

        if (!JreCompat.isGraalAvailable()) {
            // 注销所有剩余的JDBC驱动程序
            clearReferencesJdbc();
        }

        // 停止 Web 应用程序启动的所有线程
//        clearReferencesThreads();

        // 清除序列化缓存中保留的所有引用
        if (clearReferencesObjectStreamClassCaches && !JreCompat.isGraalAvailable()) {
            clearReferencesObjectStreamClassCaches();
        }

        // 检查由此类加载器加载的 ThreadLocals 触发的泄漏
        if (clearReferencesThreadLocals && !JreCompat.isGraalAvailable()) {
            checkThreadLocalsForLeaks();
        }

         // 清除 IntrospectionUtils 缓存
        IntrospectionUtils.clear();

        // 清除 VM 的 bean introspector 中的类加载器引用
        java.beans.Introspector.flushCaches();

        // 清除任何自定义 URLStreamHandler
        MoonstoneURLStreamHandlerFactory.release(this);
    }


    private final void clearReferencesJdbc() {
    	try {
    		Class<?> lpClass = Class.forName("org.zy.moonstone.core.loaer.JdbcLeakPrevention");
    		Object obj = lpClass.getConstructor().newInstance();
    		
    		@SuppressWarnings("unchecked")
    		List<String> driverNames = (List<String>) obj.getClass().getMethod("clearJdbcDriverRegistrations").invoke(obj);
    		
    		for (String name : driverNames) {
    			logger.warn("WebappClassLoader Clear JDBC, by driver: {}, context: {}", name, getContextName());
    		}
    	} catch (Exception e) {
    		Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
    		ExceptionUtils.handleThrowable(t);
    		logger.warn("WebappClassLoader 移除 JDBC 失败,by context: " + getContextName(), t);
    	}
    }


    @SuppressWarnings({ "deprecation", "unused" })  // thread.stop()
    private void clearReferencesThreads() {
        Thread[] threads = getThreads();
        List<Thread> threadsToStop = new ArrayList<>();

        // 迭代线程集
        for (Thread thread : threads) {
            if (thread != null) {
                ClassLoader ccl = thread.getContextClassLoader();
                if (ccl == this) {
                	// 不要对当前线程发出警告
                    if (thread == Thread.currentThread()) {
                        continue;
                    }

                    final String threadName = thread.getName();
                    // JVM 控制的线程
                    ThreadGroup tg = thread.getThreadGroup();
                    if (tg != null && JVM_THREAD_GROUP_NAMES.contains(tg.getName())) {
                        // HttpClient keep-alive threads
                        if (clearReferencesHttpClientKeepAliveThread && threadName.equals("Keep-Alive-Timer")) {
                            thread.setContextClassLoader(parent);
                            logger.debug("webappClassLoader.checkThreadsHttpClient");
                        }

                        // 不要对剩余的JVM控制线程发出警告
                        continue;
                    }

                    // 跳过已经死亡的线程
                    if (!thread.isAlive()) {
                        continue;
                    }

                    // TimerThread可以安全地停止，因此分别处理Sun/Oracle JDK中的"java.util.TimerThread", Apache Harmony和IBM JDK中的"java.util.Timer$TimerImpl"
                    if (thread.getClass().getName().startsWith("java.util.Timer") && clearReferencesStopTimerThreads) {
                        clearReferencesStopTimerThread(thread);
                        continue;
                    }

                    if (isRequestThread(thread)) {
                        logger.warn("调用 MoonAdapter 的堆栈请求方法, by context: {}, thread: {}, StackTrace: {}", getContextName(), threadName, getStackTrace(thread));
                    } else {
                        logger.warn("普通堆栈方法, by context: {}, thread: {}, StackTrace: {}", getContextName(), threadName, getStackTrace(thread));
                    }

                    // 除非明确配置为这样做，否则不要尝试停止线程
                    if (!clearReferencesStopThreads) {
                        continue;
                    }

                    // 如果线程已通过执行程序启动，则尝试关闭执行程序
                    boolean usingExecutor = false;
                    try {

                    	/*
                    	 *  Runnable 包装在 Thread 中的属性名:
                    	 *  (1). Sun/Oracle JDK 为 "target"
                    	 *  (2). IBM JDK 为 "target"
                    	 *  (3). Apache Harmony 为 "action"
                    	 */
                        Object target = null;
                        for (String fieldName : new String[] { "target", "runnable", "action" }) {
                            try {
                                Field targetField = thread.getClass().getDeclaredField(fieldName);
                                targetField.setAccessible(true);
                                target = targetField.get(thread);
                                break;
                            } catch (NoSuchFieldException nfe) {
                                continue;
                            }
                        }

                        // "java.util.concurrent" 代码属于公共域, 所以所有的实现都是类似的
                        if (target != null && target.getClass().getCanonicalName() != null 
                        		&& target.getClass().getCanonicalName().equals("java.util.concurrent.ThreadPoolExecutor.Worker")) {
                        	
                            Field executorField = target.getClass().getDeclaredField("this$0");
                            executorField.setAccessible(true);
                            // 获取属性值
                            Object executor = executorField.get(target);
                            if (executor instanceof ThreadPoolExecutor) {
                                ((ThreadPoolExecutor) executor).shutdownNow();
                                usingExecutor = true;
                            }
                        }
                    } catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
                        logger.warn(String.format("WebappClassLoader 停止线程失败, thread: %s, context: %s", thread.getName(), getContextName()), e);
                    }

                    // 停止执行器会自动中断关联的线程. 对于非执行线程, 在此处中断它们.
                    if (!usingExecutor && !thread.isInterrupted()) {
                        thread.interrupt();
                    }

                    // 线程被中断后需要很短的时间才能停止。记录下所有将要停止的线程，以便在此方法结束时检查它们。
                    threadsToStop.add(thread);
                }
            }
        }

        /*
         * 如果启用了线程停止，那么线程应该在执行程序关闭或线程被中断时停止，但这取决于线程正确地处理了中断。
         * 检查每个线程，如果有任何线程仍在运行，给所有线程总计不超过2秒的时间关闭。
         */
        int count = 0;
        for (Thread t : threadsToStop) {
        	/**
        	 * isAlive:
        	 * 测试此线程是否处于活动状态。如果线程已启动且尚未死亡，则该线程是活动的。
        	 * 
        	 * @return 如果此线程处于活动状态，则为true；否则为false
        	 */
            while (t.isAlive() && count < 100) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    // 退出while循环
                    break;
                }
                count++;
            }
            if (t.isAlive()) {
                // 此方法已被弃用，这是有充分理由的。这是非常危险的代码，但这是目前唯一的选择。这是应用程序自己进行清理的一个非常好的理由。
                t.stop();
            }
        }
    }


    /**
     * 停止 Timer 线程
     * @param thread
     */
    private void clearReferencesStopTimerThread(Thread thread) {
	    try {
	
	    	/**
	    	 * Sun/Oracle JDK:
	    	 * newTasksMayBeScheduled 属性 和 queue 属性都在 java.util.TimerThread 类中，取消 Timer 则:
	    	 * newTasksMayBeScheduled = false
	    	 * TaskQueue.clear()
	    	 */
	        try {
	            Field newTasksMayBeScheduledField = thread.getClass().getDeclaredField("newTasksMayBeScheduled");
	            newTasksMayBeScheduledField.setAccessible(true);
	            
	            Field queueField = thread.getClass().getDeclaredField("queue");
	            queueField.setAccessible(true);
	
	            Object queue = queueField.get(thread);
	            Method clearMethod = queue.getClass().getDeclaredMethod("clear");
	            clearMethod.setAccessible(true);
	
	            synchronized(queue) {
	                newTasksMayBeScheduledField.setBoolean(thread, false);
	                // 调用 TaskQueue 的 clear() 方法
	                clearMethod.invoke(queue);
	                // 如果队列已经为空。应该只有一个线程在等待，但使用 notifyAll()  来确保安全。
	                queue.notifyAll();
	            }
	
	        }catch (NoSuchFieldException nfe){
	        	/**
	        	 * IBM JDK, Apache Harmony: 
	        	 * cancel() 在 java.util.Timer$TimerImpl 类中
	        	 */
	            Method cancelMethod = thread.getClass().getDeclaredMethod("cancel");
	            synchronized(thread) {
	                cancelMethod.setAccessible(true);
	                cancelMethod.invoke(thread);
	            }
	        }
	
	        logger.warn("WebappClassLoader Timer Thread stoped, by thread: {}, context: {}", thread.getName(), getContextName());
	    } catch (Exception e) {
	        Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
	        ExceptionUtils.handleThrowable(t);
	        logger.warn("WebappClassLoader 停止 Timer 线程失败. By Thread: " + thread.getName() + ", Context: '" +  getContextName() + "'", t);
	    }
	}

    
	/**
     * 查看线程堆栈跟踪以查看它是否是请求线程。
     */
    private boolean isRequestThread(Thread thread) {
        StackTraceElement[] elements = thread.getStackTrace();

        if (elements == null || elements.length == 0) {
            // 应该已经停止了。 忽略它为时已晚。 假设不是请求处理线程
            return false;
        }

        // 以相反的顺序逐步执行这些方法，以查找对任何 MoonAdapter 方法的调用。
        for (int i = elements.length - 1; i >= 0; i--) {
            StackTraceElement element = elements[i];
            if ("org.zy.moonstone.core.connector.MoonAdapter".equals(element.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private void checkThreadLocalsForLeaks() {
        Thread[] threads = getThreads();

        try {
            // 使 Thread 类中 ThreadLocals 的字段可访问
            Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            
            // 使 Thread 类中 inheritableThreadLocals 的字段可访问
            Field inheritableThreadLocalsField = Thread.class.getDeclaredField("inheritableThreadLocals");
            inheritableThreadLocalsField.setAccessible(true);
            
            // 使 java.lang.ThreadLocal$ThreadLocalMap.Entry 对象的底层数组可访问
            Class<?> tlmClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            /*
             * java.lang.ThreadLocal$ThreadLocalMap$Entry.table 属性:
             * 根据需要调整表的大小。表的长度必须始终是2的幂
             */
            Field tableField = tlmClass.getDeclaredField("table");
            tableField.setAccessible(true);
            
            // 使 java.lang.ThreadLocal.ThreadLocalMap.expungeStaleEntries() 方法可访问
            Method expungeStaleEntriesMethod = tlmClass.getDeclaredMethod("expungeStaleEntries");
            expungeStaleEntriesMethod.setAccessible(true);

            for (Thread thread : threads) {
                Object threadLocalMap;
                if (thread != null) {

                	// 清空当前 Thread 实例的 threadLocals 属性
                    threadLocalMap = threadLocalsField.get(thread);
                    if (null != threadLocalMap) {
                    	// 释放 threadLocals 属性内部 ThreadLocalMap 中的数据
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }

                    // 清空当前 Thread 实例的 inheritableThreadLocals 属性
                    threadLocalMap = inheritableThreadLocalsField.get(thread);
                    if (null != threadLocalMap) {
                    	// 释放 inheritableThreadLocals 属性内部 ThreadLocalMap 中的数据
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }
                }
            }
        } catch (Throwable t) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(t)) {
                // 必须在没有必要命令行选项的 Java 9 上运行
                logger.warn("webappClassLoader.addExportsThreadLocal");
            } else {
                ExceptionUtils.handleThrowable(t);
                logger.warn("WebappClassLoader 检查 ThreadLocal 是否泄漏失败, by context: " + getContextName(), t);
            }
        }
    }


    /**
     * 分析给定的线程本地映射对象。还要传入指向内部表的字段，以避免在每次调用此方法时重新计算它。
     */
    private void checkThreadLocalMapForLeaks(Object map, Field internalTableField) throws IllegalAccessException, NoSuchFieldException {
    	if (map != null) {
    		Object[] table = (Object[]) internalTableField.get(map);
    		if (table != null) {
    			for (Object obj : table) {
    				if (obj != null) {
    					boolean keyLoadedByWebapp = false;
    					boolean valueLoadedByWebapp = false;
    					// Check the key
    					Object key = ((Reference<?>) obj).get();
    					if (this.equals(key) || loadedByThisOrChild(key)) {
    						keyLoadedByWebapp = true;
    					}
    					// Check the value
    					Field valueField = obj.getClass().getDeclaredField("value");
    					valueField.setAccessible(true);
    					Object value = valueField.get(obj);
    					if (this.equals(value) || loadedByThisOrChild(value)) {
    						valueLoadedByWebapp = true;
    					}
    					if (keyLoadedByWebapp || valueLoadedByWebapp) {
    						Object[] args = new Object[5];
    						args[0] = getContextName();
    						if (key != null) {
    							args[1] = getPrettyClassName(key.getClass());
    							try {
    								args[2] = key.toString();
    							} catch (Exception e) {
    								logger.warn("WebappClassLoader.checkThreadLocalsForLeaks.badKey - " + args[1], e);
    								args[2] = "webappClassLoader.checkThreadLocalsForLeaks.unknown";
    							}
    						}
    						if (value != null) {
    							args[3] = getPrettyClassName(value.getClass());
    							try {
    								args[4] = value.toString();
    							} catch (Exception e) {
    								logger.warn("webappClassLoader.checkThreadLocalsForLeaks.badValue - " + args[3], e);
    								args[4] = "webappClassLoader.checkThreadLocalsForLeaks.unknown";
    							}
    						}
    						if (valueLoadedByWebapp) {
    							logger.error("webappClassLoader.checkThreadLocalsForLeaks - " + args);
    						} else if (value == null) {
    							if (logger.isDebugEnabled()) {
    								logger.debug("webappClassLoader.checkThreadLocalsForLeaksNull - " + args);
    							}
    						} else {
    							if (logger.isDebugEnabled()) {
    								logger.debug("webappClassLoader.checkThreadLocalsForLeaksNone - " + args);
    							}
    						}
    					}
    				}
    			}
    		}
    	}
    }

    
    /**
     * @return clazz 类全限定名称
     */
    private String getPrettyClassName(Class<?> clazz) {
        String name = clazz.getCanonicalName();
        if (name==null){
            name = clazz.getName();
        }
        return name;
    }

    
    private String getStackTrace(Thread thread) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement ste : thread.getStackTrace()) {
            builder.append("\n ").append(ste);
        }
        return builder.toString();
    }


    /**
     * 将当前线程集作为数组获取
     */
    private Thread[] getThreads() {
        // 获取当前线程组
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        // 找到根线程组
        try {
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
        } catch (SecurityException se) {
            String msg = String.format("WebappClassLoader 获取线程组异常, by group: %s", tg.getName());
            if (logger.isDebugEnabled()) {
                logger.debug(msg, se);
            } else {
                logger.warn(msg);
            }
        }

        /**
         * activeCount():
         * 返回此线程组及其子组中活动线程数的估计值。 递归迭代此线程组中的所有子组。
         * 
         * 返回的值只是一个估计值，因为在此方法遍历内部数据结构时线程数可能会动态变化，并且可能会受到某些系统线程的存在的影响。
         * 此方法主要用于调试和监视目的。
         * 
         * @return 在这个线程组和任何其他以这个线程组作为父级的线程组中的活动线程数的估计
         */
        int threadCountGuess = tg.activeCount() + 50;
        Thread[] threads = new Thread[threadCountGuess];
        /**
         * 将此线程组及其子组中的每个活动线程复制到指定的数组中。
         * 
         * @param list: 要放入线程列表的数组
         * @return 放入数组的线程数
         * 
         * @exception 如果 ThreadGroup.CheckAccess() 确定当前线程无法访问此线程组
         */
        int threadCountActual = tg.enumerate(threads);
        
        // 确保不会错过任何线程
        while (threadCountActual == threadCountGuess) {
            threadCountGuess *= 2;
            threads = new Thread[threadCountGuess];
            // 注意 tg.enumerate(Thread[]) 会静默地忽略任何无法放入数组的线程
            threadCountActual = tg.enumerate(threads);
        }

        return threads;
    }


    private void clearReferencesObjectStreamClassCaches() {
        try {
            Class<?> clazz = Class.forName("java.io.ObjectStreamClass$Caches");
            clearCache(clazz, "localDescs");
            clearCache(clazz, "reflectors");
        } catch (ReflectiveOperationException | SecurityException | ClassCastException e) {
            logger.warn("WebappClassLoader clear ObjectStreamClassCaches 失败, context: {}", getContextName(), e);
        }
    }


    private void clearCache(Class<?> target, String mapName) throws ReflectiveOperationException, SecurityException, ClassCastException {
        Field f = target.getDeclaredField(mapName);
        f.setAccessible(true);
        
        /**
         * 获得属性值
         * 
         * 因 'ObjectStreamClass$Caches.localDescs' 和 'ObjectStreamClass$Caches.reflectors' 否是静态变量所以 map 属性非属性类默认值
         */
        Map<?,?> map = (Map<?,?>) f.get(null);
        Iterator<?> keys = map.keySet().iterator();
        while (keys.hasNext()) {
            Object key = keys.next();
            if (key instanceof Reference) {
            	/**
            	 * get():
            	 * 返回此引用对象的引用。如果该引用对象已被程序或垃圾收集器清除，则此方法返回null。
            	 * 
            	 * @return 此引用所引用的对象，如果此引用对象已被清除，则为 null
            	 */
                Object clazz = ((Reference<?>) key).get();
                if (loadedByThisOrChild(clazz)) {
                    keys.remove();
                }
            }
        }
    }

    
    /**
     * @param o - 测试的对象，可能为 null
     * @return 如果 o 已被当前类加载器或它的一个子类加载，则为<code>true</code>
     */
    private boolean loadedByThisOrChild(Object o) {
        if (o == null) {
            return false;
        }

        Class<?> clazz;
        if (o instanceof Class) {
            clazz = (Class<?>) o;
        } else {
            clazz = o.getClass();
        }

        ClassLoader cl = clazz.getClassLoader();
        while (cl != null) {
            if (cl == this) {
                return true;
            }
            cl = cl.getParent();
        }

        if (o instanceof Collection<?>) {
            Iterator<?> iter = ((Collection<?>) o).iterator();
            try {
                while (iter.hasNext()) {
                    Object entry = iter.next();
                    if (loadedByThisOrChild(entry)) {
                        return true;
                    }
                }
            } catch (ConcurrentModificationException e) {
                logger.warn("WebappClassLoader 加载失败, by class: " + clazz.getName() + ", ContextName: '" + getContextName() + "'", e);
            }
        }
        return false;
    }

    /**
     * 如果根据给定的 manifest 密封了指定的包名，则返回 true
     *
     * @param name - 要检查的路径名
     * @param man 相关的 manifest
     * @return 如果关联的 manifest 说它是密封的，则为 <code>true</code>
     */
    protected boolean isPackageSealed(String name, Manifest man) {
        String path = name.replace('.', '/') + '/';
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);

    }

    

    /**
     * 刷新系统策略文件，以获取最终的更改
     */
    protected void refreshPolicy() {
        try {
            // 策略文件可能已被修改以调整权限，因此在加载或重新加载上下文时重新加载它
            Policy policy = Policy.getPolicy();
            policy.refresh();
        } catch (AccessControlException e) {
            // 某些策略文件可能会限制这一点，即使对于核心也是如此，因此忽略此异常
        }
    }

    /**
     * 返回用于加载类和资源的 URL 的搜索路径。这包括指定给构造函数的 URL 的原始列表，以及随后由 addURL() 方法附加的任何 URL。
     * <p>
     * 请注意，此方法返回的 URL 列表可能不完整。 Web 应用类加载器通过WebResourceRoot 访问类加载器资源，
     * WebResourceRoot 支持WEB-INF/classes 下的附加文件、目录和JAR 文件内容的任意映射。 任何此类资源都不会包含在此处返回的 URL 中。
     * 
     * @return 用于加载类和资源的 URL 的搜索路径。
     */
    @Override
    public URL[] getURLs() {
        ArrayList<URL> result = new ArrayList<>();
        result.addAll(localRepositories);
        result.addAll(Arrays.asList(super.getURLs()));
        return result.toArray(new URL[0]);
    }
    
    @Override
    protected void addURL(URL url) {
        super.addURL(url);
        hasExternalRepositories = true;
    }
    
	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
    protected class PrivilegedFindClassByName implements PrivilegedAction<Class<?>> {
        private final String name;

        PrivilegedFindClassByName(String name) {
            this.name = name;
        }

        @Override
        public Class<?> run() {
            return findClassInternal(name);
        }
    }
    
    protected static final class PrivilegedGetClassLoader implements PrivilegedAction<ClassLoader> {
        private final Class<?> clazz;

        public PrivilegedGetClassLoader(Class<?> clazz){
            this.clazz = clazz;
        }

        @Override
        public ClassLoader run() {
            return clazz.getClassLoader();
        }
    }

    protected final class PrivilegedJavaseGetResource implements PrivilegedAction<URL> {
        private final String name;

        public PrivilegedJavaseGetResource(String name) {
            this.name = name;
        }

        @Override
        public URL run() {
            return javaseClassLoader.getResource(name);
        }
    }
    
    
    // -------------------------------------------------------------------------------------
    // 内部类
    // -------------------------------------------------------------------------------------
    /**
     * 合并指定的 URL 枚举 
     */
    private static class CombinedEnumeration implements Enumeration<URL> {
        private final Enumeration<URL>[] sources;
        private int index = 0;

        /**
         * 实例化一个以构造器参数顺序进行迭代的 CombinedEnumeration 实例。迭代第一个集合完成之后才会继续迭代第二个集合的数据
         * @param enum1 - 迭代的第一个集合
         * @param enum2 - 迭代的第二个集合
         */
        public CombinedEnumeration(Enumeration<URL> enum1, Enumeration<URL> enum2) {
            @SuppressWarnings("unchecked")
            Enumeration<URL>[] sources = new Enumeration[] { enum1, enum2 };
            this.sources = sources;
        }

        @Override
        public boolean hasMoreElements() {
            return inc();
        }

        @Override
        public URL nextElement() {
            if (inc()) {
                return sources[index].nextElement();
            }
            throw new NoSuchElementException();
        }

        /**
         * 每次迭代都检查是否已索引完当前 URL 枚举, 若索引到枚举末尾之时 index 才会递增1
         * @return
         */
        private boolean inc() {
            while (index < sources.length) {
                if (sources[index].hasMoreElements()) {
                    return true;
                }
                index++;
            }
            return false;
        }
    }
}
