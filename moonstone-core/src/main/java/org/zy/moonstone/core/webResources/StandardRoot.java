package org.zy.moonstone.core.webResources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.container.Host;
import org.zy.moonstone.core.interfaces.container.Lifecycle;
import org.zy.moonstone.core.interfaces.webResources.TrackedWebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.interfaces.webResources.WebResourceSet;
import org.zy.moonstone.core.loaer.WebappClassLoaderBase;
import org.zy.moonstone.core.util.RequestUtil;
import org.zy.moonstone.core.util.buf.UriUtil;
import org.zy.moonstone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description
 * <p>
 * 提供web应用程序的资源实现。其 {@link Lifecycle} 应与关联  {@link Context} 的生命周期保持一致.
 * {@linkplain  }
 * <p>
 * 此实现假定提供给  {@link StandardRoot#createWebResourceSet(ResourceSetType, String, String, String, String) } 的基本属性表示文件的绝对路径
 */
public class StandardRoot extends LifecycleBase implements WebResourceRoot {
	private static final Logger logger = LoggerFactory.getLogger(StandardRoot.class);

	private static final WebResourceSet[] WEB_RESOURCE_SET_EMPTY = new WebResourceSet[0];
	
	private Context context;

	private boolean allowLinking = false;
	private WebResourceSet main;
	
	/** 是context.xml中定义的preResources资源 */
	private final List<WebResourceSet> preResources = new ArrayList<>();
	
	/** 代表从web应用或者目录，指的是当前应用目录的"/"路径，war包中解析出来的资源 */
	private final List<WebResourceSet> mainResources = new ArrayList<>();
	
	/** 解析出来的class的资源，指的是web-inf/classes */
	private final List<WebResourceSet> classResources = new ArrayList<>();
	
	/** servlet规范定制的webinf/lib下面的jar包的资源集台 */
	private final List<WebResourceSet> jarResources = new ArrayList<>();
	
	/** 是context.xml中定义的postResources资源 */
	private final List<WebResourceSet> postResources = new ArrayList<>();

	private final WebResourceCache cache = new WebResourceCache(this);
	
	private List<FileResource> classesFile = new ArrayList<>();
	
	/** 此 Web 应用程序是否允许缓存 */
	private boolean cachingAllowed = true;

	private boolean trackLockedFiles = false;
	
	private final Set<TrackedWebResource> trackedResources = Collections.newSetFromMap(new ConcurrentHashMap<TrackedWebResource, Boolean>());

	// 使对所有 WebResourceSets 的迭代更简单的构造
	private final List<List<WebResourceSet>> allResources = new ArrayList<>();
	
	{
		allResources.add(preResources);
		allResources.add(mainResources);
		allResources.add(classResources);
		allResources.add(jarResources);
		allResources.add(postResources);
	}

	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	public StandardRoot() {}
	public StandardRoot(Context context) {
		this.context = context;
	}

	
	// -------------------------------------------------------------------------------------
	// WebResourceRoot 方法实现
	// -------------------------------------------------------------------------------------
	@Override
	public WebResource getResource(String path) {
        return getResource(path, true, false);
	}
	
	@Override
	public WebResource[] getResources(String path) {
        return getResources(path, false);
	}

//	@Override
//	public WebResource getClassLoaderResource(String path) {
//        return getResource("/WEB-INF/classes" + path, true, true);
//	}

//	@Override
//	public WebResource[] getClassLoaderResources(String path) {
//        return getResources("/WEB-INF/classes" + path, true);
//	}

	@Override
	public WebResource getWebClassLoaderResource(String path) {
      return getResource("/" + path, true, false);
	}
	
	@Override
	public WebResource[] getWebClassLoaderResources(String path) {
		return getResources("/" + path, false);
	}
	
	
	@Override
	public String[] list(String path) {
		return list(path, true);
	}
    
	@Override
	public Set<String> listWebAppPaths(String path) {
		path = validate(path);

        Set<String> result = new HashSet<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!webResourceSet.getClassLoaderOnly()) {
                    result.addAll(webResourceSet.listWebAppPaths(path));
                }
            }
        }
        if (result.size() == 0) {
            return null;
        }
        return result;
	}

	@Override
	public WebResource[] listResources(String path) {
		return listResources(path, true);
	}
	
	@Override
	public boolean mkdir(String path) {
		path = validate(path);

        if (preResourceExists(path)) {
            return false;
        }

        boolean mkdirResult = main.mkdir(path);

        if (mkdirResult && isCachingAllowed()) {
            // 从缓存中删除条目，以便新目录可见
            cache.removeCacheEntry(path);
        }
        return mkdirResult;
	}

	@Override
	public boolean write(String path, InputStream is, boolean overwrite) {
		path = validate(path);

        if (!overwrite && preResourceExists(path)) {
            return false;
        }

        boolean writeResult = main.write(path, is, overwrite);

        if (writeResult && isCachingAllowed()) {
        	// 从缓存中删除条目，以便新目录可见
            cache.removeCacheEntry(path);
        }

        return writeResult;
	}

	@Override
	public void createWebResourceSet(ResourceSetType type, String webAppMount, URL url, String internalPath) {
		BaseLocation baseLocation = new BaseLocation(url);
        createWebResourceSet(type, webAppMount, baseLocation.getBasePath(), baseLocation.getArchivePath(), internalPath);
	}

	@Override
	public void createWebResourceSet(ResourceSetType type, String webAppMount, String base, String archivePath, String internalPath) {
		List<WebResourceSet> resourceList;
        WebResourceSet resourceSet;

        switch (type) { // 获取引用
            case PRE:
                resourceList = preResources;
                break;
            case CLASSES_JAR:
                resourceList = classResources;
                break;
            case RESOURCE_JAR:
                resourceList = jarResources;
                break;
            case POST:
                resourceList = postResources;
                break;
            default:
                throw new IllegalArgumentException("未知的 ResourceSetType, by " + type);
        }

        // 此实现假定所有资源的基础都是一个文件
        File file = new File(base);

        if (file.isFile()) {
            if (archivePath != null) {
                // 如果 archivePath 不为空，则必然是嵌套在 WAR 中的 JAR
                resourceSet = new JarWarResourceSet(this, webAppMount, base, archivePath, internalPath);
            } else if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                resourceSet = new JarResourceSet(this, webAppMount, base, internalPath);
            } else {
                resourceSet = new FileResourceSet(this, webAppMount, base, internalPath);
            }
        } else if (file.isDirectory()) {
            resourceSet = new DirResourceSet(this, webAppMount, base, internalPath);
        } else {
            throw new IllegalArgumentException("创建的 File 无效, by " + file.getAbsolutePath());
        }

        if (type.equals(ResourceSetType.CLASSES_JAR)) {
            resourceSet.setClassLoaderOnly(true);
        } else if (type.equals(ResourceSetType.RESOURCE_JAR)) {
            resourceSet.setStaticOnly(true);
        }
        
        try {
			resourceSet.start();
		} catch (LifecycleException e) {
			e.printStackTrace();
		}
        
        resourceList.add(resourceSet);
	}

	@Override
	public void addPreResources(WebResourceSet webResourceSet) {
		webResourceSet.setRoot(this);
        preResources.add(webResourceSet);
	}

	@Override
	public WebResourceSet[] getPreResources() {
        return preResources.toArray(WEB_RESOURCE_SET_EMPTY);
	}

	@Override
	public void addJarResources(WebResourceSet webResourceSet) {
		webResourceSet.setRoot(this);
        jarResources.add(webResourceSet);
	}

	@Override
	public WebResourceSet[] getJarResources() {
        return jarResources.toArray(WEB_RESOURCE_SET_EMPTY);
	}
	

	@Override
	public void addPostResources(WebResourceSet webResourceSet) {
		webResourceSet.setRoot(this);
        postResources.add(webResourceSet);
	}

	@Override
	public WebResourceSet[] getPostResources() {
		return postResources.toArray(WEB_RESOURCE_SET_EMPTY);
	}

	protected void addClassResources(WebResourceSet webResourceSet) {
		webResourceSet.setRoot(this);
		classResources.add(webResourceSet);
	}
	
	protected WebResourceSet[] getClassResources() {
        return classResources.toArray(WEB_RESOURCE_SET_EMPTY);
    }
	
	@Override
	public Context getContext() {
        return context;
	}

	@Override
	public void setContext(Context context) {
        this.context = context;
	}

	@Override
	public void setAllowLinking(boolean allowLinking) {
		if (this.allowLinking != allowLinking && cachingAllowed) {
            // 如果 allowLinking 更改，则使缓存无效
            cache.clear();
        }
        this.allowLinking = allowLinking;
	}

	@Override
	public boolean getAllowLinking() {
		return allowLinking;
	}

	@Override
	public void setCachingAllowed(boolean cachingAllowed) {
		this.cachingAllowed = cachingAllowed;
	}

	@Override
	public boolean isCachingAllowed() {
		return cachingAllowed;
	}

	@Override
	public void setCacheTtl(long ttl) {
		cache.setTtl(ttl);
	}

	@Override
	public long getCacheTtl() {
		return cache.getTtl();
	}

	@Override
	public void setCacheMaxSize(long cacheMaxSize) {
        cache.setMaxSize(cacheMaxSize);
	}

	@Override
	public long getCacheMaxSize() {
        return cache.getMaxSize();
	}

	@Override
    public void setCacheObjectMaxSize(int cacheObjectMaxSize) {
        cache.setObjectMaxSize(cacheObjectMaxSize);
        // 未运行时不要强制限制，因为属性可以按任何顺序设置
        if (getState().isAvailable()) {
            cache.enforceObjectMaxSizeLimit();
        }
    }

    @Override
    public int getCacheObjectMaxSize() {
        return cache.getObjectMaxSize();
    }

	@Override
	public void setTrackLockedFiles(boolean trackLockedFiles) {
		this.trackLockedFiles = trackLockedFiles;
		if (!trackLockedFiles) {
			trackedResources.clear();
		}
	}

	@Override
	public boolean getTrackLockedFiles() {
		return trackLockedFiles;
	}


    public List<String> getTrackedResources() {
        List<String> result = new ArrayList<>(trackedResources.size());
        for (TrackedWebResource resource : trackedResources) {
            result.add(resource.toString());
        }
        return result;
    }
	
	@Override
	public void backgroundProcess() {
        cache.backgroundProcess();
        gc();
	}

	@Override
	public void registerTrackedResource(TrackedWebResource trackedResource) {
        trackedResources.add(trackedResource);
	}

	@Override
	public void deregisterTrackedResource(TrackedWebResource trackedResource) {
        trackedResources.remove(trackedResource);
	}

	@Override
	public List<URL> getBaseUrls() {
		List<URL> result = new ArrayList<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!webResourceSet.getClassLoaderOnly()) {
                    URL url = webResourceSet.getBaseUrl();
                    if (url != null) {
                        result.add(url);
                    }
                }
            }
        }
        return result;
	}

	@Override
	public void gc() {
		for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.gc();
            }
        }
	}

	
	protected void registerURLStreamHandlerFactory() {
        if (!JreCompat.isGraalAvailable()) {
            // 确保对 jar;war;file;/ URL的支持将可用(打包的WAR文件中的资源JAR需要)
            MoonstoneURLStreamHandlerFactory.register();
        }
    }

	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
	@Override
	protected void initInternal() throws LifecycleException {
	    registerURLStreamHandlerFactory();
	
	    if (context == null) {
	        throw new IllegalStateException("StandardRoot Context 不能为 null");
	    }
	
	    for (List<WebResourceSet> list : allResources) {
	        for (WebResourceSet webResourceSet : list) {
	            webResourceSet.init();
	        }
	    }
	}
	
	@Override
	protected void startInternal() throws LifecycleException {
		mainResources.clear();

        main = createMainResourceSet();

        mainResources.add(main);

        for (List<WebResourceSet> list : allResources) {
            // 跳过类资源，因为它们从下面开始
            if (list != classResources) {
                for (WebResourceSet webResourceSet : list) {
                    webResourceSet.start();
                }
            }
        }
        
        // 这必须在其他资源启动后调用，否则它将无法找到所有匹配的资源
//        processWebInfLib();
        
        processStaticResources();
        
        processClassesFile();
        
        // 需要启动新找到的资源
        for (WebResourceSet classResource : classResources) {
            classResource.start();
        }

        cache.enforceObjectMaxSizeLimit();

        setState(LifecycleState.STARTING);
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.stop();
            }
        }

        if (main != null) {
            main.destroy();
        }
        mainResources.clear();

        for (WebResourceSet webResourceSet : jarResources) {
            webResourceSet.destroy();
        }
        jarResources.clear();

        for (WebResourceSet webResourceSet : classResources) {
            webResourceSet.destroy();
        }
        classResources.clear();

        for (TrackedWebResource trackedResource : trackedResources) {
            logger.error("StandardRoot 文件已锁, by context: " + context.getName() + ", resource: " + trackedResource.getName(), trackedResource.getCreatedBy());
            try {
                trackedResource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        cache.clear();

        setState(LifecycleState.STOPPING);
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.destroy();
            }
        }
	}
	

	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
	protected WebResourceSet createMainResourceSet() {
        String docBase = context.getDocBase();
        
        WebResourceSet mainResourceSet;
        if (docBase == null) {
            mainResourceSet = new EmptyResourceSet(this);
        } else {
            File f = new File(docBase);
            if (!f.isAbsolute()) {
                f = new File(((Host)context.getParent()).getAppBaseFile(), f.getPath());
            }
            
            if (f.isDirectory()) {
                mainResourceSet = new DirResourceSet(this, "/", f.getAbsolutePath(), "/");
            } else if(f.isFile() && docBase.endsWith(".war")) {
                mainResourceSet = new WarResourceSet(this, "/", f.getAbsolutePath());
            } else {
                throw new IllegalArgumentException("StandardRoot 启动无效的 Main WebResourceSet, by path: " + f.getAbsolutePath());
            }
        }

        return mainResourceSet;
    }
	
	/**
	 * 类加载器资源通过将 WEB-INF/lib 中的 JAR 视为安装在 WEB-INF/classes（而不是 Web 应用根目录）
	 * 的资源 JAR（没有内部 META-INF/resources/ 前缀）来处理。 这可以重用资源处理管道。
	 * <p>
	 * 这些资源仅标记为类加载器，因此它们仅用于明确定义以返回类加载器资源的方法。这可以防止对 getResource("/WEB-INF/classes") 的调用从而返回 一个或多个 JAR 文件。
     *
     * @throws LifecycleException - 如果发生应阻止 Web 应用程序启动的错误
     */
    protected void processWebInfLib() throws LifecycleException {
        WebResource[] possibleJars = listResources("/WEB-INF/lib", false);

        for (WebResource possibleJar : possibleJars) {
            if (possibleJar.isFile() && possibleJar.getName().endsWith(".jar")) {
            	/*
            	 * （1）为web应用程序根目录的 docBase/WEB-INF/lib 路径下中所有的文件都创建 WebResourceSet 并挂载到 docBase/WEB-INF/classes 路径下。
            	 * （2）注册为 "CLASSES_JAR"，存储于 classResources 集合中
            	 */
            	if (logger.isDebugEnabled()) {
            		logger.debug("Mounting Resources. originalDIr: '/WEB-INF/lib', mountDir: '/WEB-INF/classes', url: {}", possibleJar.getURL());
            	}
                createWebResourceSet(ResourceSetType.CLASSES_JAR, "/WEB-INF/classes", possibleJar.getURL(), "/");
            }
        }
    }
    
    /**
     * 递归查找类加载路径下的所有class文件
     */
    protected void processClassesFile() {
    	WebResource resource = this.getResourceInternal("/", false);
		Manifest manifest = resource.getManifest();
		File rootFIle = new File(resource.getCanonicalPath());
		String rootPath = resource.getCanonicalPath();
		
		for (File fileResource : rootFIle.listFiles()) {
			if (fileResource.getName().equals("static") || fileResource.getName().equals("templates")) {
				continue;
			}
			
			recursionListFileItem(fileResource, rootPath, manifest);
		}
		
		this.classesFile = Collections.unmodifiableList(this.classesFile);
    }
	
    /**
     * 递归查找指定File对象下的所有符合条件文件
     * 
     * @param WebResource - 递归的个体FIle
     * @param rootPath - 根路径
     * @param manifest - Manifest类维护的Manifest条目名称及其关联的属性信息
     */
    private void recursionListFile(File WebResource, String rootPath, Manifest manifest) {
		for (File fileResource : WebResource.listFiles()) {
			recursionListFileItem(fileResource, rootPath, manifest);
		}
	}
    
    /**
     * 递归查找下，对于个体FIle的处理
     * 
     * @param fileResource - 递归的个体FIle
     * @param rootPath - 根路径
     * @param manifest - Manifest类维护的Manifest条目名称及其关联的属性信息
     */
    private void recursionListFileItem(File fileResource, String rootPath, Manifest manifest) {
    	if (fileResource.isFile() && fileResource.getName().endsWith(".class") && fileResource.canRead()) {
			String substrPath = fileResource.getPath().substring(rootPath.length());
			substrPath = substrPath.replace("\\", "/");
			
			if (logger.isDebugEnabled()) {
				logger.debug("Mounting Classes Resources. path: {}", substrPath);
			}
			
			this.classesFile.add(new FileResource(this, substrPath, fileResource, !fileResource.canWrite(), manifest));
		} else if (fileResource.isDirectory()) {
			recursionListFile(fileResource, rootPath, manifest);
		}
    }

    @Override
    public void additionalResourceMonitoring(WebappClassLoaderBase webappClassLoaderBase) {
    	for (WebResource webResource : this.classesFile) {
    		webappClassLoaderBase.additionalResourceMonitoring(webResource, false);
		}
    }
    
    /**
	 * 将项目根目录下的static和templates下的目录和文件映射到根目录下
     *
     * @throws LifecycleException - 如果发生应阻止 Web 应用程序启动的错误
     */
    protected void processStaticResources() throws LifecycleException {
        WebResource[] staticResources = listResources("/static", false);
        for (WebResource staticResource : staticResources) {
        	String name = staticResource.getName();
        	if (logger.isDebugEnabled()) {
        		logger.debug("Mounting Resources. originalDIr: '{}', mountDir: '/{}', url: {}", "/static/" + staticResource.getName(), name, staticResource.getURL());
        	}
        	createWebResourceSet(ResourceSetType.PRE, "/" + name, staticResource.getURL(), "/");
        }
        
        WebResource[] templatesResources = listResources("/templates", false);
        for (WebResource templatesResource : templatesResources) {
        	String fullName = templatesResource.getName();
        	String resourceName = null;
        	if (templatesResource.isFile()) {
        		resourceName = fullName.substring(0, fullName.lastIndexOf("."));
        		if (logger.isDebugEnabled()) {
        			logger.debug("Mounting Resources. originalPath: '{}', mountPath: '/{}', url: {}", 
        					"/templates/" + templatesResource.getName(), resourceName, templatesResource.getURL());
        		}
            	createWebResourceSet(ResourceSetType.PRE, "/" + resourceName, templatesResource.getURL(), "/");
        	} else {
        		if (logger.isDebugEnabled()) {
        			logger.debug("Mounting Resources. originalDIr: '{}', mountDir: '/{}', url: {}", "/templates/" + templatesResource.getName(), resourceName, templatesResource.getURL());
        		}
            	createWebResourceSet(ResourceSetType.PRE, "/" + fullName, templatesResource.getURL(), "/");
        	}
        }
    }
    
    protected WebResource[] listResources(String path, boolean validate) {
        if (validate) {
            path = validate(path);
        }

        String[] resources = list(path, false);
        WebResource[] result = new WebResource[resources.length];
        for (int i = 0; i < resources.length; i++) {
            if (path.charAt(path.length() - 1) == '/') { //  path 末尾是否有 "/"
                result[i] = getResource(path + resources[i], false, false);
            } else {
                result[i] = getResource(path + '/' + resources[i], false, false);
            }
        }
        return result;
    }
    
    /**
	 * 获取表示给定路径上资源对象，如果启用缓存则从缓存当中查找
	 * 
	 * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
	 * @param validate - 是否验证路径有效性
	 * @param useClassLoaderResources - 如果这些资源应仅用于类加载器资源查找，则为 true，否则为 false
	 * @return 表示给定路径上的资源的对象
	 */
    protected WebResource getResource(String path, boolean validate, boolean useClassLoaderResources) {
        if (validate) {
            path = validate(path);
        }

        if (isCachingAllowed()) {
            return cache.getResource(path, useClassLoaderResources);
        } else {
            return getResourceInternal(path, useClassLoaderResources);
        }
    }
    
    /**
	 * 获取表示给定路径上资源对象，如果启用缓存则从缓存当中查找
	 * 
	 * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
	 * @param useClassLoaderResources - 如果这些资源应仅用于类加载器资源查找，则为 true，否则为 false
	 * @return 表示给定路径上的资源的对象
	 */
    protected final WebResource getResourceInternal(String path, boolean useClassLoaderResources) {
        WebResource result = null;
        WebResource virtual = null;
        WebResource mainEmpty = null;
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!useClassLoaderResources &&  !webResourceSet.getClassLoaderOnly() || useClassLoaderResources && !webResourceSet.getStaticOnly()) {
//            	if ( (useClassLoaderResources && !webResourceSet.getStaticOnly()) || !webResourceSet.getClassLoaderOnly() ) {
                    result = webResourceSet.getResource(path);
                    if (result.exists()) {
                        return result;
                    }
                    if (virtual == null) {
                        if (result.isVirtual()) {
                            virtual = result;
                        } else if (main.equals(webResourceSet)) {
                            mainEmpty = result;
                        }
                    }
                }
            }
        }

        // 如果没有找到真实结果，则使用第一个虚拟结果
        if (virtual != null) {
            return virtual;
        }

        // 默认是主资源中的空资源
        return mainEmpty;
    }
    
	/**
	 * 获取表示给定路径上资源的对象
	 * 
	 * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
	 * @param useClassLoaderResources - 如果这些资源应仅用于类加载器资源查找，则为 true，否则为 false
	 * @return 表示给定路径上的资源的对象集
	 */
	protected WebResource[] getResourcesInternal(String path, boolean useClassLoaderResources) {
        List<WebResource> result = new ArrayList<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (useClassLoaderResources || !webResourceSet.getClassLoaderOnly()) {
                    WebResource webResource = webResourceSet.getResource(path);
                    if (webResource.exists()) {
                        result.add(webResource);
                    }
                }
            }
        }

        if (result.size() == 0) {
            result.add(main.getResource(path));
        }

        return result.toArray(new WebResource[0]);
    }
	
    /*
     * 当且仅当此web应用程序的所有资源都通过打包的WAR文件提供时返回true。在这种情况下，它用于在WAR文件不会更改的基础上优化缓存验证。
     */
    protected boolean isPackedWarFile() {
        return main instanceof WarResourceSet && preResources.isEmpty() && postResources.isEmpty();
    }
	
    /**
     * 用于单元测试
     */
    protected final void setMainResources(WebResourceSet main) {
        this.main = main;
        mainResources.clear();
        mainResources.add(main);
    }
	
    
	// -------------------------------------------------------------------------------------
	// 私有方法
	// -------------------------------------------------------------------------------------
    /**
	 * 获取表示给定路径上资源对象集，如果启用缓存则从缓存当中查找
	 * 
	 * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
	 * @param useClassLoaderResources - 如果这些资源应仅用于类加载器资源查找，则为 true，否则为 false
	 * @return 表示给定路径上的资源的对象集
	 */
	private WebResource[] getResources(String path, boolean useClassLoaderResources) {
        path = validate(path);

        if (isCachingAllowed()) {
            return cache.getResources(path, useClassLoaderResources);
        } else {
            return getResourcesInternal(path, useClassLoaderResources);
        }
    }
	
	/**
	 * 获取位于指定目录中的所有文件和目录的名称列表
	 * 
	 * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
	 * @param validate - 是否验证路径有效性
	 * @return 资源列表。 如果路径不引用目录，则将返回零长度数组
	 */
	private String[] list(String path, boolean validate) {
		if (validate) {
			path = validate(path);
		}

		// 此设置是因为不希望重复的 LinkedHashSet 保留顺序。 重要的是 WebResourceSet 的顺序，但保留所有 JAR 的顺序更简单。
		HashSet<String> result = new LinkedHashSet<>();
		for (List<WebResourceSet> list : allResources) {
			for (WebResourceSet webResourceSet : list) {
				if (!webResourceSet.getClassLoaderOnly()) {
					String[] entries = webResourceSet.list(path);
					result.addAll(Arrays.asList(entries));
				}
			}
		}
		return result.toArray(new String[0]);
	}

	/**
	 * 确保此对象处于有效状态以提供资源，检查路径是否是以“/”开头的字符串，并检查路径是否可以在不超出根目录的情况下进行规范化。
     *
     * @param path
     * @return 规范化路径
     */
    private String validate(String path) {
        if (!getState().isAvailable()) {
            throw new IllegalStateException("StandardRoot 未启动");
        }

        if (path == null || path.length() == 0 || !path.startsWith("/")) {
            throw new IllegalArgumentException("无效 path, by " + path);
        }

        String result;
        if (File.separatorChar == '\\') {
            // 在 Windows 上，'\\' 是一个分隔符，因此如果 Windows 样式的分隔符成功进入路径，需替换它
            result = RequestUtil.normalize(path, true);
        } else {
            // 在 UNIX 和类似系统上，'\\' 是一个有效的文件名，因此不要将其转换为 '/'
            result = RequestUtil.normalize(path, false);
        }
        if (result == null || result.length() == 0 || !result.startsWith("/")) {
            throw new IllegalArgumentException("无效的正常路径, by pth: " + path + ", normalizedResult: " + result);
        }

        return result;
    }
    
    private boolean preResourceExists(String path) {
        for (WebResourceSet webResourceSet : preResources) {
            WebResource webResource = webResourceSet.getResource(path);
            if (webResource.exists()) {
                return true;
            }
        }
        return false;
    }
	
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    // 单元测试需要访问这个类
    static class BaseLocation { 
    	/** 资源的绝对路径, 若当前资源是jar包下的资源则为该 jar 包的路径 */
        private final String basePath;
        /** 资源在 war/jar 包下的相对路径，即分隔符之后的路径 */
        private final String archivePath;

        /**
         * @param url - 资源对象的绝对路径，其中可能包含 jar 或 war 包资源路径分隔符（其URL：jar 或 war 包文件资源路径 + 分隔符 + jar或war包中资源文件相对路径）
         */
        BaseLocation(URL url) {
            File f = null;

            if ("jar".equals(url.getProtocol()) || "war".equals(url.getProtocol())) {
                String jarUrl = url.toString();
                int endOfFileUrl = -1;
                if ("jar".equals(url.getProtocol())) {
                    endOfFileUrl = jarUrl.indexOf("!/");
                } else {
                    endOfFileUrl = jarUrl.indexOf(UriUtil.getWarSeparator());
                }
                // 截去开头的 url 协议字符串，得到 jar 或 war 包文件资源路径
                String fileUrl = jarUrl.substring(4, endOfFileUrl);
                try {
                    f = new File(new URL(fileUrl).toURI());
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                
                int startOfArchivePath = endOfFileUrl + 2;
                if (jarUrl.length() >  startOfArchivePath) {
                	// 截取资源在 war/jar 包下的相对路径，即分隔符之后的内容
                    archivePath = jarUrl.substring(startOfArchivePath);
                } else {
                    archivePath = null;
                }
            } else if ("file".equals(url.getProtocol())){
                try {
                    f = new File(url.toURI());
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                archivePath = null;
            } else {
                throw new IllegalArgumentException("不支持的协议, by " + url.getProtocol());
            }

            basePath = f.getAbsolutePath();
        }

        String getBasePath() {
            return basePath;
        }

        String getArchivePath() {
            return archivePath;
        }
    }
}
