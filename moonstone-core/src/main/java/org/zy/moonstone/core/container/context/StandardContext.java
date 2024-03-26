package org.zy.moonstone.core.container.context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.container.ContainerBase;
import org.zy.moonstone.core.container.StandardHost;
import org.zy.moonstone.core.container.StandardWrapper;
import org.zy.moonstone.core.container.valves.StandardContextValve;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.filter.ApplicationFilterConfig;
import org.zy.moonstone.core.filter.FilterDef;
import org.zy.moonstone.core.filter.FilterMap;
import org.zy.moonstone.core.interfaces.InstanceManager;
import org.zy.moonstone.core.interfaces.container.Container;
import org.zy.moonstone.core.interfaces.container.ContainerListener;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.container.Lifecycle;
import org.zy.moonstone.core.interfaces.container.LifecycleListener;
import org.zy.moonstone.core.interfaces.container.Wrapper;
import org.zy.moonstone.core.interfaces.http.CookieProcessor;
import org.zy.moonstone.core.interfaces.loader.Loader;
import org.zy.moonstone.core.interfaces.loader.ThreadBindingListener;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.loaer.WebappLoader;
import org.zy.moonstone.core.security.PrivilegedContextClassLoaderGetter;
import org.zy.moonstone.core.security.PrivilegedContextClassLoaderSetter;
import org.zy.moonstone.core.session.StandardManager;
import org.zy.moonstone.core.session.interfaces.Manager;
import org.zy.moonstone.core.util.CharsetMapper;
import org.zy.moonstone.core.util.ContextName;
import org.zy.moonstone.core.util.ErrorPageSupport;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.InstanceManagerBindings;
import org.zy.moonstone.core.util.IntrospectionUtils;
import org.zy.moonstone.core.util.SimpleInstanceManager;
import org.zy.moonstone.core.util.compat.JreCompat;
import org.zy.moonstone.core.util.descriptor.ErrorPage;
import org.zy.moonstone.core.util.http.Rfc6265CookieProcessor;
import org.zy.moonstone.core.webResources.StandardRoot;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardContext extends ContainerBase implements Context {
	// -------------------------------------------------------------------------------------
	// 实例变量
	// -------------------------------------------------------------------------------------
	/**
	 * 运行此模块的Java虚拟机
	 */
	private String[] javaVMs = null;

	/**
	 * 若目标 servlet 未指定 @MultipartConfig 是否允许解析 multipart/form-data 请求？
	 */
	protected boolean allowCasualMultipartParsing = false;

	/**
	 * 即使请求违反数据大小约束, 也控制是否读取（吞咽）剩余的请求数据。
	 */
	private boolean swallowAbortedUploads = true;

	private InstanceManager instanceManager = null;

	/**
	 * 为此应用程序配置的应用程序侦听器类名称集.
	 */
	private String applicationListeners[] = new String[0];

	/**
	 * 应用程序监听器锁
	 */
	private final Object applicationListenersLock = new Object();

	/**
	 * 需要对 ServletContext 方法进行有限访问的应用程序监听器集。 请参阅 Servlet 3.1 部分 4.4
	 */
	private final Set<Object> noPluggabilityListeners = new HashSet<>();

	/**
	 * 实例化的应用程序事件侦听器对象的列表.
	 */
	private List<Object> applicationEventListenersList = new CopyOnWriteArrayList<>();

	/**
	 * 实例化的应用程序生命周期侦听器对象集.
	 */
	private Object applicationLifecycleListenersObjects[] = new Object[0];


	/**
	 * 此 Web 应用程序的有序 ServletContainerInitializers 集.
	 */
	private Map<ServletContainerInitializer,Set<Class<?>>> initializers = new LinkedHashMap<>();

	/**
	 * 语言环境与字符集的映射器.
	 */
	private CharsetMapper charsetMapper = null;

	/**
	 * 要创建的 CharsetMapper 类的 Java 类名.
	 */
	private String charsetMapperClass = "org.zy.moonstone.core.util.CharsetMapper";

	/**
	 * 此上下文的 配置文件描述符的 URL.
	 */
	private URL configFile = null;

	/**
	 * 此上下文的“正确配置”标志.
	 */
	private boolean configured = false;

	/**
	 * 与此 Context 关联的 ServletContext 实现.
	 */
	protected ApplicationContext context = null;

	/**
	 * 呈现给需要对 ServletContext 方法进行有限访问的侦听器的关联 ServletContext 的包装版本。 请参阅 Servlet 3.1 部分 4.4.
	 */
	private NoPluggabilityServletContext noPluggabilityServletContext = null;

	/**
	 * 是否应该尝试使用 cookie 进行会话 id 通信？
	 */
	private boolean cookies = true;

	/**
	 * 是否应该允许 ServletContext.getContext() 方法访问此服务器中其他 Web 应用程序的上下文？
	 */
	private boolean crossContext = false;

	/**
	 * 编码路径.
	 */
	private String encodedPath = null;

	/**
	 * 此 Web 应用程序的未编码路径.
	 */
	private String path = null;

	/**
	 * “遵循标准委托模型”标志, 它将用于配置我们的ClassLoader.<br/>
	 * Graal实际上不能从webapp的类加载器中加载类, 所以默认情况下委托.
	 */
	private boolean delegate = JreCompat.isGraalAvailable();

	private boolean denyUncoveredHttpMethods;

	/**
	 * 此web应用程序的显示名称.
	 */
	private String displayName = null;

	/**
	 * 此web应用程序的可分派标志.
	 */
	private boolean distributable = false;

	/**
	 * web应用程序的文档根目录.
	 */
	private String docBase = null;

	private final ErrorPageSupport errorPageSupport = new ErrorPageSupport();

	/**
	 * 已经初始化的一组过滤器配置（和关联的过滤器实例）,使用过滤器名称作为key.
	 */
	private Map<String, ApplicationFilterConfig> filterConfigs = new HashMap<>();

	/**
	 * 此应用程序的过滤器定义集,使用过滤器名称作为key.
	 */
	private Map<String, FilterDef> filterDefs = new HashMap<>();

	/**
	 * 此应用程序的过滤器映射集, 按照它们在部署描述符中定义的顺序, 
	 * 以及通过 {@link ServletContext} 添加的附加映射, 可能位于部署描述符中定义的映射之前和之后.
	 */
	private final ContextFilterMaps filterMaps = new ContextFilterMaps();

	/**
	 * 忽略注解.
	 */
	private boolean ignoreAnnotations = false;

	/**
	 * 此 Container 关联的 Loader 实现.
	 */
	private Loader loader = null;
	private final ReadWriteLock loaderLock = new ReentrantReadWriteLock();

	/**
	 * 此 Container 关联的 Manager 实现.
	 */
	protected Manager manager = null;
	private final ReadWriteLock managerLock = new ReentrantReadWriteLock();

	/**
	 * 此 Web 应用程序的 MIME 映射, key是扩展名.
	 */
	private Map<String, String> mimeMappings = new HashMap<>();

	/**
	 * 此 Web 应用程序的上下文初始化参数, key是参数名.
	 */
	private final Map<String, String> parameters = new ConcurrentHashMap<>();

	/**
	 * 请求处理暂停标志（发生重新加载时）
	 */
	private volatile boolean paused = false;

	/**
	 * 此 Web 应用程序的可重新加载标识.
	 */
	private boolean reloadable = false;

	/**
	 * 解压 WAR 包
	 */
	private boolean unpackWAR = true;

	/**
	 * 此 Web 应用程序的默认上下文覆盖标志.
	 */
	private boolean override = false;

	/**
	 * 此 Web 应用程序的原始文档根目录.
	 */
	private String originalDocBase = null;

	/**
	 * 此 Web 应用程序的特权标志.
	 */
	private boolean privileged = false;

	/**
	 * 下一次调用 <code> addWelcomeFile() </code> 是否会导致替换任何现有的欢迎文件？ 
	 * 这将在处理 web 应用程序的部署描述符之前设置, 以便应用程序指定的选项替换而不是附加到全局描述符中定义的选项
	 */
	private boolean replaceWelcomeFiles = false;

	/**
	 * 此 Web 应用程序的 servlet 映射, 以匹配模式为键.
	 */
	private Map<String, String> servletMappings = new HashMap<>();

	private final Object servletMappingsLock = new Object();

	/**
	 * 此 Web 应用程序的会话超时（以分钟为单位）.
	 */
	private int sessionTimeout = 30;

	/**
	 * 将 flag 设置为 true 以在执行 servlet 时将 system.out 和 system.err 重定向到记录器.
	 */
	private boolean swallowOutput = false;

	/**
	 * 容器等待 servlet 卸载的毫秒数.
	 */
	private long unloadDelay = 2000;

	/**
	 * 此应用程序的 welcome 文件.
	 */
	private String welcomeFiles[] = new String[0];

	private final Object welcomeFilesLock = new Object();

	/**
	 * LifecycleListener 的类名集合, 将通过 createWrapper() 添加到每个新创建的 Wrapper.
	 */
	private String wrapperLifecycles[] = new String[0];

	private final Object wrapperLifecyclesLock = new Object();

	/**
	 * 将由 createWrapper() 添加到每个新创建的 Wrapper 的 ContainerListener 的类名集.
	 */
	private String wrapperListeners[] = new String[0];

	private final Object wrapperListenersLock = new Object();

	/** 此上下文的工作目录的路径名（如果不是绝对的, 则相对于服务器的主目录）. 上下文工作目录, 为ServletContext提供的私有临时目录 */
	private String workDir = null;

	/**
	 * 使用的 Wrapper 类实现的 Java 类名.
	 */
	private String wrapperClassName = StandardWrapper.class.getName();
	private Class<?> wrapperClass = null;

	private WebResourceRoot resources;
	private final ReadWriteLock resourcesLock = new ReentrantReadWriteLock();

	private long startupTime;
	private long startTime;

	/**
	 * 用于会话 cookie 的名称。 null 表示名称由应用程序控制.
	 */
	private String sessionCookieName;

	/**
	 * 指示会话 cookie 应使用 HttpOnly 的标志
	 */
	private boolean useHttpOnly = true;

	/**
	 * 用于会话 cookie 的域。 null 表示域由应用程序控制。
	 */
	private String sessionCookieDomain;

	/**
	 * 用于会话 cookie 的路径。 null 表示路径由应用程序控制。
	 */
	private String sessionCookiePath;

	/**
	 * 在会话cookie路径的末尾添加了一个“/”以确保浏览器, 尤其是IE, 不会针对于/foo的请求发送用于 /foobar请求的session cookie。
	 */
	private boolean sessionCookiePathUsesTrailingSlash = false;

	/**
	 * 是否应该尝试终止已由 Web 应用程序启动的线程？如果未指定, 将使用默认值 false。
	 */
	private boolean clearReferencesStopThreads = false;

	/**
	 * 是否应该尝试终止任何已由 Web 应用程序启动的{@link java.util.TimerThread }s？ 如果未指定, 将使用默认值 false
	 */
	private boolean clearReferencesStopTimerThreads = false;

	/**
	 * 是否应该在应用停止时更新线程池的线程, 以避免由于未清理的 ThreadLocal 变量而导致内存泄漏。 
	 * 这也要求将StandardThreadExecutor或ThreadPoolExecutor的threadRenewalDelay属性设置为正值。
	 */
	private boolean renewThreadsWhenStoppingContext = true;

	/**
	 * 是否应该尝试从 ObjectStream 类缓存中清除对 Web 应用程序类加载器加载的类的引用？
	 */
	private boolean clearReferencesObjectStreamClassCaches = true;

	/**
	 * 是否应该尝试从 ThreadLocals 清除对该类加载器加载的类的引用？
	 */
	private boolean clearReferencesThreadLocals = true;

	/**
	 * 在关闭JVM的过程中, 当web应用程序被关闭时, Tomcat是否应该跳过内存泄漏检查？
	 */
	private boolean skipMemoryLeakChecksOnJvmShutdown = false;

	private String webappVersion = "";

	/**
	 * 期望提供资源的 servlet 集合
	 */
	private Set<String> resourceOnlyServlets = new HashSet<>();

	/**
	 * 在转发时触发请求监听器？默认值为false
	 */
	private boolean fireRequestListenersOnForwards = false;

	/**
	 * 通过 {@link ApplicationContext#createServlet(Class)} 创建用于跟踪目的的servlet
	 */
	private Set<Servlet> createdServlets = new HashSet<>();

	private boolean sendRedirectBody = false;

	private CookieProcessor cookieProcessor;

	/**
	 * 验证客户端提供的新 SessionId
	 */
	private boolean validateClientProvidedNewSessionId = true;

	/**
	 * 启用映射器上下文根重定向？默认为true
	 */
	private boolean mapperContextRootRedirectEnabled = true;

	/**
	 * 启用映射器目录重定向？默认为false
	 */
	private boolean mapperDirectoryRedirectEnabled = false;

	/**
	 * 使用相对重定向
	 */
	private boolean useRelativeRedirects = !Globals.STRICT_SERVLET_COMPLIANCE;

	/**
	 * dispatchers使用编码路径
	 */
	private boolean dispatchersUseEncodedPaths = true;

	/**
	 * 请求编码
	 */
	private String requestEncoding = null;

	/**
	 * 响应编码
	 */
	private String responseEncoding = null;

	/**
	 * 允许多个前导正向斜杠路径
	 */
	private boolean allowMultipleLeadingForwardSlashInPath = false;

	/**
	 * 进行中的异步计数
	 */
	private final AtomicLong inProgressAsyncCount = new AtomicLong(0);

	/**
	 * 创建上传目标
	 */
	private boolean createUploadTargets = false;

	protected static final ThreadBindingListener DEFAULT_NAMING_LISTENER = (new ThreadBindingListener() {
        @Override
        public void bind() {}
        @Override
        public void unbind() {}
    });
	
    protected ThreadBindingListener threadBindingListener = DEFAULT_NAMING_LISTENER;
    
    /**
	 * 此应用程序的受监视资源
	 */
	private String watchedResources[] = new String[0];

	private final Object watchedResourcesLock = new Object();

	/**
	 * 用于搜索可能包含TLD或Web-Fragment.xml文件的 Jar 扫描器
	 */
//	private JarScanner jarScanner = null;

	/**
	 * 如果这个web应用程序已经启动了一个HttpClient保持活动计时器线程，并且仍然在运行，
	 * moonstone 是否应该将上下文类加载器从当前的 {@link ClassLoader} 更改为 {@link ClassLoader#getParent()} 以防止内存泄漏？
	 * 请注意，一旦keep-alive超时，keep-alive计时器线程将自动停止，但是在一个繁忙的系统上，这可能在一段时间内都不会发生。
	 */
	private boolean clearReferencesHttpClientKeepAliveThread = true;

	private int effectiveMajorVersion = 3;

	private int effectiveMinorVersion = 0;

	/** 指示 /WEB-INF/classes 是否应被视为分解的 JAR 和 JAR 资源的标识，就像它们在 JAR 中一样。 */
	private boolean addWebinfClassesResources = false;

//	private String containerSciFilter;

	/** 如果Servlet启动失败则Ctx是否失败 */
	private Boolean failCtxIfServletStartFails;
	
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 使用默认的基础Valve创建一个新的 StandardContext.
	 */
	public StandardContext() {
		super();
		pipeline.setBasic(new StandardContextValve());
	}


	// -------------------------------------------------------------------------------------
	// getter、setter 方法
	// -------------------------------------------------------------------------------------
	@Override
	public void setCreateUploadTargets(boolean createUploadTargets) {
		this.createUploadTargets = createUploadTargets;
	}
	@Override
	public boolean getCreateUploadTargets() {
		return createUploadTargets;
	}

	@Override
	public void incrementInProgressAsyncCount() {
		inProgressAsyncCount.incrementAndGet();
	}
	@Override
	public void decrementInProgressAsyncCount() {
		inProgressAsyncCount.decrementAndGet();
	}

	public long getInProgressAsyncCount() {
		return inProgressAsyncCount.get();
	}

	@Override
	public void setAllowMultipleLeadingForwardSlashInPath(
			boolean allowMultipleLeadingForwardSlashInPath) {
		this.allowMultipleLeadingForwardSlashInPath = allowMultipleLeadingForwardSlashInPath;
	}
	@Override
	public boolean getAllowMultipleLeadingForwardSlashInPath() {
		return allowMultipleLeadingForwardSlashInPath;
	}

	@Override
	public String getRequestCharacterEncoding() {
		return requestEncoding;
	}
	@Override
	public void setRequestCharacterEncoding(String requestEncoding) {
		this.requestEncoding = requestEncoding;
	}

	@Override
	public String getResponseCharacterEncoding() {
		return responseEncoding;
	}
	@Override
	public void setResponseCharacterEncoding(String responseEncoding) {
		// 确保上下文响应编码由唯一的 String 对象表示
		if (responseEncoding == null) {
			this.responseEncoding = null;
		} else {
			this.responseEncoding = new String(responseEncoding);
		}
	}

	@Override
	public void setDispatchersUseEncodedPaths(boolean dispatchersUseEncodedPaths) {
		this.dispatchersUseEncodedPaths = dispatchersUseEncodedPaths;
	}
	@Override
	public boolean getDispatchersUseEncodedPaths() {
		return dispatchersUseEncodedPaths;
	}

	@Override
	public void setUseRelativeRedirects(boolean useRelativeRedirects) {
		this.useRelativeRedirects = useRelativeRedirects;
	}
	@Override
	public boolean getUseRelativeRedirects() {
		return useRelativeRedirects;
	}

	@Override
	public void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled) {
		this.mapperContextRootRedirectEnabled = mapperContextRootRedirectEnabled;
	}
	@Override
	public boolean getMapperContextRootRedirectEnabled() {
		return mapperContextRootRedirectEnabled;
	}

	@Override
	public void setMapperDirectoryRedirectEnabled(boolean mapperDirectoryRedirectEnabled) {
		this.mapperDirectoryRedirectEnabled = mapperDirectoryRedirectEnabled;
	}
	@Override
	public boolean getMapperDirectoryRedirectEnabled() {
		return mapperDirectoryRedirectEnabled;
	}

	@Override
	public void setValidateClientProvidedNewSessionId(boolean validateClientProvidedNewSessionId) {
		this.validateClientProvidedNewSessionId = validateClientProvidedNewSessionId;
	}
	@Override
	public boolean getValidateClientProvidedNewSessionId() {
		return validateClientProvidedNewSessionId;
	}

	@Override
	public void setCookieProcessor(CookieProcessor cookieProcessor) {
		if (cookieProcessor == null) {
			throw new IllegalArgumentException("cookieProcessor 不能为 null");
		}
		this.cookieProcessor = cookieProcessor;
	}
	@Override
	public CookieProcessor getCookieProcessor() {
		return cookieProcessor;
	}

	@Override
	public boolean getSendRedirectBody() {
		return sendRedirectBody;
	}
	@Override
	public void setSendRedirectBody(boolean sendRedirectBody) {
		this.sendRedirectBody = sendRedirectBody;
	}

	@Override
	public void setFireRequestListenersOnForwards(boolean enable) {
		fireRequestListenersOnForwards = enable;
	}
	@Override
	public boolean getFireRequestListenersOnForwards() {
		return fireRequestListenersOnForwards;
	}

	@Override
	public void setWebappVersion(String webappVersion) {
		if (null == webappVersion) {
			this.webappVersion = "";
		} else {
			this.webappVersion = webappVersion;
		}
	}
	@Override
	public String getWebappVersion() {
		return webappVersion;
	}

	@Override
	public String getBaseName() {
		return new ContextName(path, webappVersion).getBaseName();
	}

	@Override
	public boolean addResourceOnlyServlets(String servletName) {
		return resourceOnlyServlets.add(servletName);
	}

	@Override
	public boolean isResourceOnlyServlet(String servletName) {
		return resourceOnlyServlets.contains(servletName);
	}

	@Override
	public int getEffectiveMajorVersion() {
		return effectiveMajorVersion;
	}
	@Override
	public void setEffectiveMajorVersion(int effectiveMajorVersion) {
		this.effectiveMajorVersion = effectiveMajorVersion;
	}

	@Override
	public int getEffectiveMinorVersion() {
		return effectiveMinorVersion;
	}
	@Override
	public void setEffectiveMinorVersion(int effectiveMinorVersion) {
		this.effectiveMinorVersion = effectiveMinorVersion;
	}

//	@Override
//	public JarScanner getJarScanner() {
//		if (jarScanner == null) {
//			jarScanner = new StandardJarScanner();
//		}
//		return jarScanner;
//	}
//	@Override
//	public void setJarScanner(JarScanner jarScanner) {
//		this.jarScanner = jarScanner;
//	}

	@Override
	public InstanceManager getInstanceManager() {
		return instanceManager;
	}
	@Override
	public void setInstanceManager(InstanceManager instanceManager) {
		this.instanceManager = instanceManager;
	}

	@Override
	public String getEncodedPath() {
		return encodedPath;
	}

	@Override
	public void setAllowCasualMultipartParsing(boolean allowCasualMultipartParsing) {
		this.allowCasualMultipartParsing = allowCasualMultipartParsing;
	}
	@Override
	public boolean getAllowCasualMultipartParsing() {
		return this.allowCasualMultipartParsing;
	}

	@Override
	public void setSwallowAbortedUploads(boolean swallowAbortedUploads) {
		this.swallowAbortedUploads = swallowAbortedUploads;
	}
	@Override
	public boolean getSwallowAbortedUploads() {
		return this.swallowAbortedUploads;
	}

	public boolean getDelegate() {
		return this.delegate;
	}
	/**
	 * 设置“遵循标准委托模型”标志, 它将用于配置我们的ClassLoader。
	 */
	public void setDelegate(boolean delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object[] getApplicationEventListeners() {
		return applicationEventListenersList.toArray();
	}
	@Override
	public void setApplicationEventListeners(Object listeners[]) {
		applicationEventListenersList.clear();
		if (listeners != null && listeners.length > 0) {
			applicationEventListenersList.addAll(Arrays.asList(listeners));
		}
	}

	@Override
	public Object[] getApplicationLifecycleListeners() {
		return applicationLifecycleListenersObjects;
	}
	@Override
	public void setApplicationLifecycleListeners(Object listeners[]) {
		applicationLifecycleListenersObjects = listeners;
	}

	@Override
	public String getCharset(Locale locale) {
		return getCharsetMapper().getCharset(locale);
	}

	@Override
	public boolean getConfigured() {
		return this.configured;
	}
	@Override
	public void setConfigured(boolean configured) {
		this.configured = configured;
	}

	@Override
	public boolean getCookies() {
		return this.cookies;
	}
	@Override
	public void setCookies(boolean cookies) {
		this.cookies = cookies;
	}

	@Override
	public String getSessionCookieName() {
		return sessionCookieName;
	}
	@Override
	public void setSessionCookieName(String sessionCookieName) {
		this.sessionCookieName = sessionCookieName;
	}

	@Override
	public boolean getUseHttpOnly() {
		return useHttpOnly;
	}
	@Override
	public void setUseHttpOnly(boolean useHttpOnly) {
		this.useHttpOnly = useHttpOnly;
	}

	@Override
	public String getSessionCookieDomain() {
		return sessionCookieDomain;
	}
	@Override
	public void setSessionCookieDomain(String sessionCookieDomain) {
		this.sessionCookieDomain = sessionCookieDomain;
	}

	@Override
	public String getSessionCookiePath() {
		return sessionCookiePath;
	}
	@Override
	public void setSessionCookiePath(String sessionCookiePath) {
		this.sessionCookiePath = sessionCookiePath;
	}

	@Override
	public boolean getSessionCookiePathUsesTrailingSlash() {
		return sessionCookiePathUsesTrailingSlash;
	}
	@Override
	public void setSessionCookiePathUsesTrailingSlash(boolean sessionCookiePathUsesTrailingSlash) {
		this.sessionCookiePathUsesTrailingSlash =sessionCookiePathUsesTrailingSlash;
	}

	@Override
	public boolean getCrossContext() {
		return this.crossContext;
	}
	@Override
	public void setCrossContext(boolean crossContext) {
		this.crossContext = crossContext;
	}

	public long getStartupTime() {
		return startupTime;
	}
	public void setStartupTime(long startupTime) {
		this.startupTime = startupTime;
	}

	@Override
	public boolean getDenyUncoveredHttpMethods() {
		return denyUncoveredHttpMethods;
	}
	@Override
	public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods) {
		this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
	}

	@Override
	public String getDisplayName() {
		return this.displayName;
	}
	@Override
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public boolean getDistributable() {
		return this.distributable;
	}
	@Override
	public void setDistributable(boolean distributable) {
		this.distributable = distributable;
	}

	@Override
	public String getDocBase() {
		return this.docBase;
	}
	@Override
	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}

	@Override
	public Loader getLoader() {
		Lock readLock = loaderLock.readLock();
		readLock.lock();
		try {
			return loader;
		} finally {
			readLock.unlock();
		}
	}
	@Override
	public void setLoader(Loader loader) {
		Lock writeLock = loaderLock.writeLock();
		writeLock.lock();
		Loader oldLoader = null;
		try {
			// 必要时更换组件
			oldLoader = this.loader;
			if (oldLoader == loader)
				return;
			this.loader = loader;

			// 必要时停止组件
			if (getState().isAvailable() && (oldLoader != null) && (oldLoader instanceof Lifecycle)) {
				try {
					((Lifecycle) oldLoader).stop();
				} catch (LifecycleException e) {
					logger.error("组件停止异常", e);
				}
			}

			// 必要时启动新组件
			if (loader != null)
				loader.setContext(this);
			if (getState().isAvailable() && (loader != null) && (loader instanceof Lifecycle)) {
				try {
					((Lifecycle) loader).start();
				} catch (LifecycleException e) {
					logger.error("组件启动异常", e);
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public Manager getManager() {
		Lock readLock = managerLock.readLock();
		readLock.lock();
		try {
			return manager;
		} finally {
			readLock.unlock();
		}
	}
	@Override
	public void setManager(Manager manager) {
		Lock writeLock = managerLock.writeLock();
		writeLock.lock();
		Manager oldManager = null;
		try {
			// 必要时更换组件
			oldManager = this.manager;
			if (oldManager == manager)
				return;
			this.manager = manager;

			// 必要时停止旧组件
			if (oldManager instanceof Lifecycle) {
				try {
					((Lifecycle) oldManager).stop();
					((Lifecycle) oldManager).destroy();
				} catch (LifecycleException e) {
					logger.error("standardContext.setManager.stop", e);
				}
			}

			// 必要时启动新组件
			if (manager != null) {
				manager.setContext(this);
			}
			if (getState().isAvailable() && manager instanceof Lifecycle) {
				try {
					((Lifecycle) manager).start();
				} catch (LifecycleException e) {
					logger.error("standardContext.setManager.start", e);
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public boolean getIgnoreAnnotations() {
		return this.ignoreAnnotations;
	}
	@Override
	public void setIgnoreAnnotations(boolean ignoreAnnotations) {
		this.ignoreAnnotations = ignoreAnnotations;
	}

	@Override
	public String getRealPath(String path) {
		// WebResources API期望所有路径都以/开头。
		if ("".equals(path)) {
			path = "/";
		}
		if (resources != null) {
			try {
				WebResource resource = resources.getResource(path);
				String canonicalPath = resource.getCanonicalPath();
				if (canonicalPath == null) {
					return null;
				} else if ( (resource.isDirectory() && !canonicalPath.endsWith(File.separator) || !resource.exists()) && path.endsWith("/") ) {
					return canonicalPath + File.separatorChar;
				} else {
					return canonicalPath;
				}
			} catch (IllegalArgumentException iae) {
				// ServletContext.getRealPath() 不允许抛出此异常
			}
		}
		return null;
	}

	@Override
	public String getPath() {
		return path;
	}
	@Override
	public void setPath(String path) {
		boolean invalid = false;
		if (path == null || path.equals("/")) {
			invalid = true;
			this.path = "";
		} else if ("".equals(path) || path.startsWith("/")) {
			this.path = path;
		} else {
			invalid = true;
			this.path = "/" + path;
		}

		if (this.path.endsWith("/")) {
			invalid = true;
			this.path = this.path.substring(0, this.path.length() - 1);
		}
		if (invalid) {
			logger.warn("指定的上下文路径无效, by parg: {}, 更正为: {}", path, this.path);
		}
		encodedPath = this.path;
		if (getName() == null) {
			setName(this.path);
		}
	}

	@Override
	public boolean getReloadable() {
		return this.reloadable;
	}
	@Override
	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	@Override
	public boolean getOverride() {
		return this.override;
	}
	@Override
	public void setOverride(boolean override) {
		this.override = override;
	}

	/**
	 * @return 此上下文的原始文档根。这可以是绝对路径名、相对路径名或URL。
	 */
	public String getOriginalDocBase() {
		return this.originalDocBase;
	}
	/**
	 * 设置此上下文的原始文档根目录。这可以是绝对路径名、相对路径名或URL。
	 * @param docBase - 原始文档根目录
	 */
	public void setOriginalDocBase(String docBase) {
		this.originalDocBase = docBase;
	}

    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (getPrivileged()) {
            return this.getClass().getClassLoader();
        } else if (parent != null) {
            return parent.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }
	
	@Override
	public boolean getPrivileged() {
		return this.privileged;
	}
	@Override
	public void setPrivileged(boolean privileged) {
		this.privileged = privileged;
	}

	/**
	 * 设置“replace welcome files”属性
	 * @param replaceWelcomeFiles - 新的属性值
	 */
	public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
		this.replaceWelcomeFiles = replaceWelcomeFiles;
	}

	@Override
	public ServletContext getServletContext() {
		if (context == null) {
			logger.info("ApplicationContext Create. this: {}", this);
			context = new ApplicationContext(this);
		}
		return context.getFacade();
	}

	@Override
	public int getSessionTimeout() {
		return this.sessionTimeout;
	}
	@Override
	public void setSessionTimeout(int timeout) {
		/*
		 * 如果超时为0或更少, 容器确保会话的默认行为永远不会超时。
		 */
		this.sessionTimeout = (timeout == 0) ? -1 : timeout;
	}

	@Override
	public boolean getSwallowOutput() {
		return this.swallowOutput;
	}
	@Override
	public void setSwallowOutput(boolean swallowOutput) {
		this.swallowOutput = swallowOutput;
	}

	@Override
	public long getUnloadDelay() {
		return this.unloadDelay;
	}
	/**
	 * 设置 unloadDelay 标志的值, 它表示容器在卸载 servlet 时将等待的毫秒数。
	 * 将此值设置为较小的值可能会导致停止 Web 应用程序时更多请求无法完成。
	 *
	 * @param unloadDelay The new value
	 */
	public void setUnloadDelay(long unloadDelay) {
		this.unloadDelay = unloadDelay;
	}

	/**
	 * @return 解压war包的标志
	 */
	public boolean getUnpackWAR() {
		return unpackWAR;
	}
	/**
	 * @param unpackWAR - 设置为true时在部署时展开war包
	 */
	public void setUnpackWAR(boolean unpackWAR) {
		this.unpackWAR = unpackWAR;
	}

	public String[] getJavaVMs() {
		return javaVMs;
	}
	public String[] setJavaVMs(String[] javaVMs) {
		return this.javaVMs = javaVMs;
	}

	@Override
	public String getWrapperClass() {
		return this.wrapperClassName;
	}
	@Override
	public void setWrapperClass(String wrapperClassName) {
		this.wrapperClassName = wrapperClassName;

		try {
			wrapperClass = Class.forName(wrapperClassName);
			if (!StandardWrapper.class.isAssignableFrom(wrapperClass)) {
				throw new IllegalArgumentException(String.format("无效的StandardWrapper 全限定类名, by ",wrapperClassName));
			}
		} catch (ClassNotFoundException cnfe) {
			throw new IllegalArgumentException(cnfe.getMessage());
		}
	}

	@Override
	public WebResourceRoot getResources() {
		Lock readLock = resourcesLock.readLock();
		readLock.lock();
		try {
			return resources;
		} finally {
			readLock.unlock();
		}
	}
	@Override
	public void setResources(WebResourceRoot resources) {
		Lock writeLock = resourcesLock.writeLock();
		writeLock.lock();
		WebResourceRoot oldResources = null;
		try {
			if (getState().isAvailable()) {
				throw new IllegalStateException(" WebResourceRoot 已启动");
			}

			oldResources = this.resources;
			if (oldResources == resources) return;

			this.resources = resources;
			if (oldResources != null) {
				oldResources.setContext(null);
			}
			if (resources != null) {
				resources.setContext(this);
			}
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * @return 此上下文的语言环境到字符集映射器类
	 */
	public String getCharsetMapperClass() {
		return this.charsetMapperClass;
	}
	/**
	 * 将此上下文的语言环境设置为字符集映射器类
	 *
	 * @param mapper - 新的映射器类
	 */
	public void setCharsetMapperClass(String mapper) {
		this.charsetMapperClass = mapper;
	}

	public String getWorkPath() {
		if (getWorkDir() == null) {
			return null;
		}
		File workDir = new File(getWorkDir());
		if (!workDir.isAbsolute()) {
			try {
				workDir = new File(getMoonBase().getCanonicalFile(), getWorkDir());
			} catch (IOException e) {
				logger.warn("获取工作目录异常", e);
			}
		}
		return workDir.getAbsolutePath();
	}

	/**
	 * @return 此上下文的工作目录
	 */
	public String getWorkDir() {
		return this.workDir;
	}
	/**
	 * 设置此上下文的工作目录
	 *
	 * @param workDir - 新的工作目录
	 */
	public void setWorkDir(String workDir) {
		this.workDir = workDir;

		if (getState().isAvailable()) {
			postWorkDirectory();
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

	public boolean getRenewThreadsWhenStoppingContext() {
		return this.renewThreadsWhenStoppingContext;
	}
	public void setRenewThreadsWhenStoppingContext(boolean renewThreadsWhenStoppingContext) {
		this.renewThreadsWhenStoppingContext = renewThreadsWhenStoppingContext;
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

	/**
	 * @return 此上下文的语言环境到字符集映射器
	 */
	public CharsetMapper getCharsetMapper() {
		// 第一次请求时创建映射器
		if (this.charsetMapper == null) {
			try {
				Class<?> clazz = Class.forName(charsetMapperClass);
				this.charsetMapper = (CharsetMapper) clazz.getConstructor().newInstance();
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				this.charsetMapper = new CharsetMapper();
			}
		}
		return this.charsetMapper;
	}
	public void setCharsetMapper(CharsetMapper mapper) {
		this.charsetMapper = mapper;
		if( mapper != null ) this.charsetMapperClass= mapper.getClass().getName();

	}

	@Override
	public boolean getPaused() {
		return this.paused;
	}
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	@Override
	public void setAddWebinfClassesResources(boolean addWebinfClassesResources) {
		this.addWebinfClassesResources = addWebinfClassesResources;
	}
	@Override
	public boolean getAddWebinfClassesResources() {
		return addWebinfClassesResources;
	}

	public boolean getClearReferencesHttpClientKeepAliveThread() {
		return this.clearReferencesHttpClientKeepAliveThread;
	}
	public void setClearReferencesHttpClientKeepAliveThread(boolean clearReferencesHttpClientKeepAliveThread) {
		this.clearReferencesHttpClientKeepAliveThread = clearReferencesHttpClientKeepAliveThread;
	}

	public Boolean getFailCtxIfServletStartFails() {
		return failCtxIfServletStartFails;
	}
	public void setFailCtxIfServletStartFails(Boolean failCtxIfServletStartFails) {
		this.failCtxIfServletStartFails = failCtxIfServletStartFails;
	}

	/**
	 * 获取此 StandardContext 中所有servlet的累计处理时间。
	 *
	 * @return 此 StandardContext 中所有servlet的累积处理时间
	 */
	public long getProcessingTime() {

		long result = 0;

		Container[] children = findChildren();
		if (children != null) {
			for (Container child : children) {
				result += ((StandardWrapper) child).getProcessingTime();
			}
		}

		return result;
	}

	/**
	 * 获取此 StandardContext 中所有servlet的最大处理时间。
	 *
	 * @return 此 StandardContext 中所有servlet的最大处理时间
	 */
	public long getMaxTime() {

		long result = 0;
		long time;

		Container[] children = findChildren();
		if (children != null) {
			for (Container child : children) {
				time = ((StandardWrapper) child).getMaxTime();
				if (time > result)
					result = time;
			}
		}

		return result;
	}

	/**
	 * 获取此 StandardContext 中所有servlet的最小处理时间
	 *
	 * @return 此 StandardContext 中所有servlet的最短处理时间
	 */
	public long getMinTime() {
		long result = -1;
		long time;

		Container[] children = findChildren();
		if (children != null) {
			for (Container child : children) {
				time = ((StandardWrapper) child).getMinTime();
				if (result < 0 || time < result)
					result = time;
			}
		}

		return result;
	}
	
	protected boolean getComputedFailCtxIfServletStartFails() {
		if(failCtxIfServletStartFails != null) {
			return failCtxIfServletStartFails.booleanValue();
		}
		
		if(getParent() instanceof StandardHost) {
			return ((StandardHost)getParent()).isFailCtxIfServletStartFails();
		}

		return false;
	}

	private void setClassLoaderProperty(String name, boolean value) {
		ClassLoader cl = getLoader().getClassLoader();
		if (!IntrospectionUtils.setProperty(cl, name, Boolean.toString(value))) {
			// 设置失败
			logger.info("设置 WebappClassLoader 属性失败, 该属性缺失. by name: {}, value: {}", name, Boolean.toString(value));
		}
	}

    @Override
    public ThreadBindingListener getThreadBindingListener() {
        return threadBindingListener;
    }

    @Override
    public void setThreadBindingListener(ThreadBindingListener threadBindingListener) {
        this.threadBindingListener = threadBindingListener;
    }
    
	
	// -------------------------------------------------------------------------------------
	// 容器操作
	// -------------------------------------------------------------------------------------
	@Override
	public void addServletContainerInitializer(ServletContainerInitializer sci, Set<Class<?>> classes) {
		initializers.put(sci, classes);
	}

	@Override
	public void addApplicationListener(String listener) {
		synchronized (applicationListenersLock) {
			String results[] = new String[applicationListeners.length + 1];
			for (int i = 0; i < applicationListeners.length; i++) {
				if (listener.equals(applicationListeners[i])) {
					logger.info("重复的监听器, by {}",listener);
					return;
				}
				results[i] = applicationListeners[i];
			}
			results[applicationListeners.length] = listener;
			applicationListeners = results;
		}
		fireContainerEvent("addApplicationListener", listener);
	}

	public void addApplicationEventListener(Object listener) {
		applicationEventListenersList.add(listener);
	}

	public void addApplicationLifecycleListener(Object listener) {
		int len = applicationLifecycleListenersObjects.length;
		Object[] newListeners = Arrays.copyOf(applicationLifecycleListenersObjects, len + 1);
		newListeners[len] = listener;
		applicationLifecycleListenersObjects = newListeners;
	}

	@Override
	public void addChild(Container child) {
		if (!(child instanceof Wrapper)) {
			throw new IllegalArgumentException("添加的子容器不是 Wrapper 实现");
		}

		super.addChild(child);
	}

	@Override
	public void addErrorPage(ErrorPage errorPage) {
		if (errorPage == null)
			throw new IllegalArgumentException("errorPage不能为null");

		String location = errorPage.getLocation();
		if ((location != null) && !location.startsWith("/")) {
			errorPage.setLocation("/" + location);
		}
		errorPageSupport.add(errorPage);
		fireContainerEvent("addErrorPage", errorPage);
	}

	@Override
	public void addFilterDef(FilterDef filterDef) {
		synchronized (filterDefs) {
			filterDefs.put(filterDef.getFilterName(), filterDef);
		}
		fireContainerEvent("addFilterDef", filterDef);
	}

	@Override
	public void addFilterMap(FilterMap filterMap) {
		// 将这个过滤器映射添加到注册的集合
		filterMaps.add(filterMap);
		fireContainerEvent("addFilterMap", filterMap);
	}

	@Override
	public void addFilterMapBefore(FilterMap filterMap) {
		validateFilterMap(filterMap);
		// 将此过滤器映射添加到注册集
		filterMaps.addBefore(filterMap);
		fireContainerEvent("addFilterMap", filterMap);
	}

	@Override
	public void addMimeMapping(String extension, String mimeType) {
		synchronized (mimeMappings) {
			mimeMappings.put(extension.toLowerCase(Locale.ENGLISH), mimeType);
		}
		fireContainerEvent("addMimeMapping", extension);
	}

	@Override
	public void addParameter(String name, String value) {
		// 验证建议的上下文初始化参数
		if ((name == null) || (value == null)) {
			throw new IllegalArgumentException("上下文参数命和参数值不能为null");
		}

		// 如果该参数不存在, 则将其添加到定义的集合中
		String oldValue = parameters.putIfAbsent(name, value);

		if (oldValue != null) {
			throw new IllegalArgumentException("重复的参数, by name: " + name);
		}
		fireContainerEvent("addParameter", name);
	}

	@Override
	public void addWelcomeFile(String name) {
		synchronized (welcomeFilesLock) {
			if (replaceWelcomeFiles) {
				fireContainerEvent(Context.CLEAR_WELCOME_FILES_EVENT, null);
				welcomeFiles = new String[0];
				setReplaceWelcomeFiles(false);
			}
			String[] results = Arrays.copyOf(welcomeFiles, welcomeFiles.length + 1);
			results[welcomeFiles.length] = name;
			welcomeFiles = results;
		}
		if(this.getState().equals(LifecycleState.STARTED)) fireContainerEvent(Context.ADD_WELCOME_FILE_EVENT, name);
	}

	@Override
	public void addWrapperLifecycle(String listener) {
		synchronized (wrapperLifecyclesLock) {
			String[] results = Arrays.copyOf(wrapperLifecycles, wrapperLifecycles.length + 1);
			results[wrapperLifecycles.length] = listener;
			wrapperLifecycles = results;
		}
		fireContainerEvent("addWrapperLifecycle", listener);
	}

	@Override
	public void addWrapperListener(String listener) {
		synchronized (wrapperListenersLock) {
			String[] results = Arrays.copyOf(wrapperListeners, wrapperListeners.length + 1);
			results[wrapperListeners.length] = listener;
			wrapperListeners = results;
		}
		fireContainerEvent("addWrapperListener", listener);
	}

	@Override
	public void addLocaleEncodingMappingParameter(String locale, String encoding) {
		getCharsetMapper().addCharsetMappingFromDeploymentDescriptor(locale, encoding);		
	}

	/**
	 * 添加一个新的servlet映射，替换指定模式的任何现有映射。
	 *
	 * @param pattern - 要映射的URL模式
	 * @param name - 要执行的相应servlet的名称
	 * @param jspWildCard - 如果name标识JspServlet并且模式包含通配符，则为true；否则为false
	 * @exception IllegalArgumentException - 如果指定的Servlet名称对于此上下文未知
	 */
	@Override
	public void addServletMappingDecoded(String pattern, String name, boolean jspWildCard) {
		// 验证建议的映射
		if (findChild(name) == null)
			throw new IllegalArgumentException("name 无效, 未找到与之相关联的 Container 实例, name: " + name);
		
		String adjustedPattern = adjustURLPattern(pattern);
		if (!validateURLPattern(adjustedPattern))
			throw new IllegalArgumentException("不符合规范要求的 pattern, by: urlPattern: " + adjustedPattern);

		// 将此映射添加到的注册集
		synchronized (servletMappingsLock) {
			String name2 = servletMappings.get(adjustedPattern);
			if (name2 != null) {
				// 不允许同一模式上有多个Servlet
				Wrapper wrapper = (Wrapper) findChild(name2);
				wrapper.removeMapping(adjustedPattern);
			}
			servletMappings.put(adjustedPattern, name);
		}
		Wrapper wrapper = (Wrapper) findChild(name);
		wrapper.addMapping(adjustedPattern);

		fireContainerEvent("addServletMapping", adjustedPattern);
	}

	/**
	 * 向此上下文识别的集合中添加一个新的被监视资源
	 *
	 * @param name - 新监视的资源文件名
	 */
	@Override
	public void addWatchedResource(String name) {
		synchronized (watchedResourcesLock) {
			String[] results = Arrays.copyOf(watchedResources, watchedResources.length + 1);
			results[watchedResources.length] = name;
			watchedResources = results;
		}
		fireContainerEvent("addWatchedResource", name);
	}
	
	@Override
	public String[] findApplicationListeners() {
		return applicationListeners;
	}

	@Override
	public ErrorPage findErrorPage(int errorCode) {
		return errorPageSupport.find(errorCode);
	}

	@Override
	public ErrorPage findErrorPage(Throwable exceptionType) {
		return errorPageSupport.find(exceptionType);
	}

	@Override
	public ErrorPage[] findErrorPages() {
		return errorPageSupport.findAll();
	}

	@Override
	public FilterDef findFilterDef(String filterName) {
		synchronized (filterDefs) {
			return filterDefs.get(filterName);
		}
	}

	@Override
	public FilterDef[] findFilterDefs() {
		synchronized (filterDefs) {
			FilterDef results[] = new FilterDef[filterDefs.size()];
			return filterDefs.values().toArray(results);
		}
	}

	@Override
	public FilterMap[] findFilterMaps() {
		return filterMaps.asArray();
	}

	@Override
	public String findMimeMapping(String extension) {
		return mimeMappings.get(extension.toLowerCase(Locale.ENGLISH));
	}

	@Override
	public String[] findMimeMappings() {
		synchronized (mimeMappings) {
			String results[] = new String[mimeMappings.size()];
			return mimeMappings.keySet().toArray(results);
		}
	}

	@Override
	public String findParameter(String name) {
		return parameters.get(name);
	}

	@Override
	public String[] findParameters() {
		List<String> parameterNames = new ArrayList<>(parameters.size());
		parameterNames.addAll(parameters.keySet());
		return parameterNames.toArray(new String[parameterNames.size()]);
	}

	@Override
	public String findServletMapping(String pattern) {
		synchronized (servletMappingsLock) {
			return servletMappings.get(pattern);
		}
	}

	@Override
	public String[] findServletMappings() {
		synchronized (servletMappingsLock) {
			String results[] = new String[servletMappings.size()];
			return servletMappings.keySet().toArray(results);
		}
	}

	@Override
	public boolean findWelcomeFile(String name) {
		synchronized (welcomeFilesLock) {
			for (int i = 0; i < welcomeFiles.length; i++) {
				if (name.equals(welcomeFiles[i]))
					return true;
			}
		}
		return false;
	}

	@Override
	public String[] findWelcomeFiles() {
		synchronized (welcomeFilesLock) {
			return welcomeFiles;
		}
	}

	@Override
	public String[] findWrapperLifecycles() {
		synchronized (wrapperLifecyclesLock) {
			return wrapperLifecycles;
		}
	}

	@Override
	public String[] findWrapperListeners() {
		synchronized (wrapperListenersLock) {
			return wrapperListeners;
		}
	}

	/**
	 * @return 此上下文的被监视资源集。如果没有定义，则返回一个长度为零的数组。
	 */
	@Override
	public String[] findWatchedResources() {
		synchronized (watchedResourcesLock) {
			return watchedResources;
		}
	}
	
	@Override
	public void removeApplicationListener(String listener) {
		synchronized (applicationListenersLock) {
			// 确保此监听器当前存在
			int n = -1;
			for (int i = 0; i < applicationListeners.length; i++) {
				if (applicationListeners[i].equals(listener)) {
					n = i;
					break;
				}
			}
			if (n < 0) return;

			// 移除指定的监听器
			int j = 0;
			String results[] = new String[applicationListeners.length - 1];
			for (int i = 0; i < applicationListeners.length; i++) {
				if (i != n) results[j++] = applicationListeners[i];
			}
			applicationListeners = results;
		}

		fireContainerEvent("removeApplicationListener", listener);
	}

	@Override
	public void removeChild(Container child) {
		if (!(child instanceof Wrapper)) {
			throw new IllegalArgumentException(String.format("指定移除的子容器必须是 Wrapper 实现, by class: {}", child));
		}
		super.removeChild(child);
	}

	@Override
	public void removeErrorPage(ErrorPage errorPage) {
		errorPageSupport.remove(errorPage);
		fireContainerEvent("removeErrorPage", errorPage);
	}

	@Override
	public void removeFilterDef(FilterDef filterDef) {
		synchronized (filterDefs) {
			filterDefs.remove(filterDef.getFilterName());
		}
		fireContainerEvent("removeFilterDef", filterDef);
	}

	@Override
	public void removeFilterMap(FilterMap filterMap) {
		filterMaps.remove(filterMap);
		fireContainerEvent("removeFilterMap", filterMap);
	}

	@Override
	public void removeMimeMapping(String extension) {
		synchronized (mimeMappings) {
			mimeMappings.remove(extension);
		}
		fireContainerEvent("removeMimeMapping", extension);
	}

	@Override
	public void removeParameter(String name) {
		parameters.remove(name);
		fireContainerEvent("removeParameter", name);
	}

	@Override
	public void removeServletMapping(String pattern) {
		String name = null;
		synchronized (servletMappingsLock) {
			name = servletMappings.remove(pattern);
		}
		Wrapper wrapper = (Wrapper) findChild(name);
		if( wrapper != null ) {
			wrapper.removeMapping(pattern);
		}
		fireContainerEvent("removeServletMapping", pattern);
	}

	@Override
	public void removeWelcomeFile(String name) {
		synchronized (welcomeFilesLock) {

			// 确保此 welcome 文件当前存在
			int n = -1;
			for (int i = 0; i < welcomeFiles.length; i++) {
				if (welcomeFiles[i].equals(name)) {
					n = i;
					break;
				}
			}
			if (n < 0) return;

			// 删除指定的 welcome 文件
			int j = 0;
			String results[] = new String[welcomeFiles.length - 1];
			for (int i = 0; i < welcomeFiles.length; i++) {
				if (i != n)
					results[j++] = welcomeFiles[i];
			}
			welcomeFiles = results;
		}

		if(this.getState().equals(LifecycleState.STARTED))
			fireContainerEvent(Context.REMOVE_WELCOME_FILE_EVENT, name);
	}

	@Override
	public void removeWrapperLifecycle(String listener) {
		synchronized (wrapperLifecyclesLock) {
			// 确保当前存在此生命周期监听器
			int n = -1;
			for (int i = 0; i < wrapperLifecycles.length; i++) {
				if (wrapperLifecycles[i].equals(listener)) {
					n = i;
					break;
				}
			}
			if (n < 0) return;

			// 删除指定的生命周期监听器
			int j = 0;
			String results[] = new String[wrapperLifecycles.length - 1];
			for (int i = 0; i < wrapperLifecycles.length; i++) {
				if (i != n)
					results[j++] = wrapperLifecycles[i];
			}
			wrapperLifecycles = results;
		}
		fireContainerEvent("removeWrapperLifecycle", listener);
	}

	@Override
	public void removeWrapperListener(String listener) {
		synchronized (wrapperListenersLock) {
			int n = -1;
			for (int i = 0; i < wrapperListeners.length; i++) {
				if (wrapperListeners[i].equals(listener)) {
					n = i;
					break;
				}
			}
			if (n < 0) return;

			int j = 0;
			String results[] = new String[wrapperListeners.length - 1];
			for (int i = 0; i < wrapperListeners.length; i++) {
				if (i != n)
					results[j++] = wrapperListeners[i];
			}
			wrapperListeners = results;

		}
		fireContainerEvent("removeWrapperListener", listener);
	}

	/**
	 * 从与此上下文关联的列表中删除指定的被监视资源名。
	 *
	 * @param name - 要删除的被监视资源的名称
	 */
	@Override
	public void removeWatchedResource(String name) {
		synchronized (watchedResourcesLock) {

			// 确保此受监视的资源当前存在
			int n = -1;
			for (int i = 0; i < watchedResources.length; i++) {
				if (watchedResources[i].equals(name)) {
					n = i;
					break;
				}
			}
			if (n < 0)
				return;

			// 删除指定的监视资源
			int j = 0;
			String results[] = new String[watchedResources.length - 1];
			for (int i = 0; i < watchedResources.length; i++) {
				if (i != n)
					results[j++] = watchedResources[i];
			}
			watchedResources = results;

		}

		fireContainerEvent("removeWatchedResource", name);
	}

	
	// -------------------------------------------------------------------------------------
	// 普通方法
	// -------------------------------------------------------------------------------------
	@Override
	public Wrapper createWrapper() {
		Wrapper wrapper = null;
		if (wrapperClass != null) {
			try {
				wrapper = (Wrapper) wrapperClass.getConstructor().newInstance();
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				logger.error(String.format("创建 Wrapper 实例异常, by class: {}", wrapperClass), t);
				return null;
			}
		} else {
			wrapper = new StandardWrapper();
			if (logger.isDebugEnabled()) {
				logger.debug("使用默认实现StandardWrapper创建Wrapper实例");
			}
		}

		synchronized (wrapperLifecyclesLock) {
			Class<?> clazz = null;
			for (int i = 0; i < wrapperLifecycles.length; i++) {
				try {
					clazz = Class.forName(wrapperLifecycles[i]);
					LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
					wrapper.addLifecycleListener(listener);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
					logger.error(String.format("创建 LifecycleListener 实例异常, by LifecycleListener : {}", clazz), t);
					return null;
				}
			}
		}

		synchronized (wrapperListenersLock) {
			Class<?> clazz = null;
			for (int i = 0; i < wrapperListeners.length; i++) {
				try {
					clazz = Class.forName(wrapperListeners[i]);
					ContainerListener listener = (ContainerListener) clazz.getConstructor().newInstance();
					wrapper.addContainerListener(listener);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
					logger.error(String.format("创建容器事件监听器实例异常, by ContainerListener : {}", clazz), t);
					return null;
				}
			}
		}

		return wrapper;
	}

	@Override
	public synchronized void reload() {
		// 验证当前的组件状态
		if (!getState().isAvailable()) throw new IllegalStateException(String.format("StandardContext Not Started. by name: [%s]", getName()));

		if(logger.isInfoEnabled()) logger.info(String.format("StandardContext Reloading Started. by name: [%s]", getName()));

		// 暂时停止接受请求
		setPaused(true);

		try {
			stop();
		} catch (LifecycleException e) {
			logger.error(String.format("上下文停止异常. by name: [%s]", getName()), e);
		}

		try {
			start();
		} catch (LifecycleException e) {
			logger.error(String.format("上下文启动异常. by name: [%s]", getName()), e);
		}

		setPaused(false);
		
		if(logger.isInfoEnabled()) logger.info(String.format("StandardContext Reloading Completed. by name: [%s]", getName()));
	}

	/**
	 * 获取此 StandardContext 中所有 servlet 的累积请求计数
	 *
	 * @return 此 StandardContext 中所有 servlet 的累积请求数
	 */
	public int getRequestCount() {
		int result = 0;
		Container[] children = findChildren();
		if (children != null) {
			for( int i=0; i< children.length; i++ ) {
				result += ((StandardWrapper)children[i]).getRequestCount();
			}
		}
		return result;
	}

	/**
	 * 获取此 StandardContext 中所有 servlet 的累积错误计数
	 *
	 * @return 此 StandardContext 中所有 servlet 的累积错误计数
	 */
	public int getErrorCount() {
		int result = 0;
		Container[] children = findChildren();
		if (children != null) {
			for( int i=0; i< children.length; i++ ) {
				result += ((StandardWrapper)children[i]).getErrorCount();
			}
		}
		return result;
	}

	/**
	 * 挂钩以跟踪通过 {@link ServletContext#createServlet(Class)} 创建的 Servlet
	 */
	public void dynamicServletCreated(Servlet servlet) {
		createdServlets.add(servlet);
	}

	public boolean wasCreatedDynamicServlet(Servlet servlet) {
		return createdServlets.contains(servlet);
	}

	
	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
	/**
	 * 为此上下文配置和初始化过滤器集
	 * @return 如果所有过滤器初始化成功完成, 则为 true, 否则为 false
	 */ 
	public boolean filterStart() {
		if (logger.isDebugEnabled()) {
			logger.debug("Filters Starting");
		}
		// 为每个定义的过滤器实例化并记录一个 FilterConfig
		boolean ok = true;
		synchronized (filterConfigs) {
			filterConfigs.clear();
			for (Entry<String,FilterDef> entry : filterDefs.entrySet()) {
				String name = entry.getKey();
				if (logger.isDebugEnabled()) {
					logger.debug(" Starting filter '" + name + "'");
				}
				try {
					ApplicationFilterConfig filterConfig = new ApplicationFilterConfig(this, entry.getValue());
					filterConfigs.put(name, filterConfig);
				} catch (Throwable t) {
					t = ExceptionUtils.unwrapInvocationTargetException(t);
					ExceptionUtils.handleThrowable(t);
					logger.error(String.format("filter启动异常, by name: {}", name), t);
					ok = false;
				}
			}
		}
		return ok;
	}

	/**
	 * 最终确定并释放此上下文的过滤器集
	 * @return 如果所有过滤器最终确定成功完成, 则为 true, 否则为 false
	 */
	public boolean filterStop() {
		if (logger.isDebugEnabled())
			getLogger().debug("Filters Stopping ");

		// 释放所有 Filter 和 FilterConfig 实例
		synchronized (filterConfigs) {
			for (Entry<String, ApplicationFilterConfig> entry : filterConfigs.entrySet()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Stopping filter '" + entry.getKey() + "'");
				}
				ApplicationFilterConfig filterConfig = entry.getValue();
				filterConfig.release();
			}
			filterConfigs.clear();
		}
		return true;
	}

	/**
	 * 查找并返回指定过滤器名称的初始化过滤器配置（如果有）； 否则返回null。
	 *
	 * @param name - 所需过滤器的名称
	 * @return 过滤器配置对象
	 */
	public FilterConfig findFilterConfig(String name) {
		return filterConfigs.get(name);
	}

	/**
	 * 为此上下文配置一组实例化的应用程序事件监听器
	 * 
	 * @return 如果所有侦听器都成功初始化, 则为 true, 否则为 false。
	 */
	public boolean listenerStart() {
		if (logger.isDebugEnabled()) logger.debug("Listeners Starting ");

		// 实例化所需的监听器
		String listeners[] = findApplicationListeners();
		Object results[] = new Object[listeners.length];
		boolean ok = true;
		for (int i = 0; i < results.length; i++) {
			if (logger.isDebugEnabled())
				logger.debug("实例化 listener '" + listeners[i] + "'");
			try {
				String listener = listeners[i];
				results[i] = getInstanceManager().newInstance(listener);
			} catch (Throwable t) {
				t = ExceptionUtils.unwrapInvocationTargetException(t);
				ExceptionUtils.handleThrowable(t);
				logger.error(String.format("实例化监听器异常, by name: {}", listeners[i]), t);
				ok = false;
			}
		}
		if (!ok) {
			logger.error("未成功实例化所有 Listener, 跳过后续处理");
			return false;
		}

		// 对监听器进行排序
		List<Object> eventListeners = new ArrayList<>();
		List<Object> lifecycleListeners = new ArrayList<>();
		for (int i = 0; i < results.length; i++) {
			if ((results[i] instanceof ServletContextAttributeListener) || (results[i] instanceof ServletRequestAttributeListener) || (results[i] instanceof ServletRequestListener)
					|| (results[i] instanceof HttpSessionIdListener) || (results[i] instanceof HttpSessionAttributeListener)) {
				eventListeners.add(results[i]);
			}
			if ((results[i] instanceof ServletContextListener) || (results[i] instanceof HttpSessionListener)) {
				lifecycleListeners.add(results[i]);
			}
		}

		/**
		 * 监听器实例可能已由 ServletContextInitializers 和其他代码通过可插入性 API 直接添加到此上下文中。
		 * 将这些监听器放在注解定义的监听器之后, 然后用新的完整列表覆盖实例列表。
		 */
		Object[] applicationEventListeners = getApplicationEventListeners();
		if (applicationEventListeners != null) {
			for (Object eventListener: getApplicationEventListeners()) {
				eventListeners.add(eventListener);
			}
			setApplicationEventListeners(eventListeners.toArray());
		}
		
		Object[] applicationLifecycleListeners = getApplicationLifecycleListeners();
		if (applicationLifecycleListeners != null) {
			for (Object lifecycleListener: getApplicationLifecycleListeners()) {
				lifecycleListeners.add(lifecycleListener);
				if (lifecycleListener instanceof ServletContextListener) {
					noPluggabilityListeners.add(lifecycleListener);
				}
			}
		}
		setApplicationLifecycleListeners(lifecycleListeners.toArray());
		getServletContext();
		context.setNewServletContextListenerAllowed(false);

		Object instances[] = getApplicationLifecycleListeners();
		if (instances == null || instances.length == 0) {
			return ok;
		}

		ServletContextEvent event = null;
		ServletContextEvent tldEvent = null;
		if (noPluggabilityListeners.size() > 0) {
			noPluggabilityServletContext = new NoPluggabilityServletContext(getServletContext());
			tldEvent = new ServletContextEvent(noPluggabilityServletContext);
		} else {
			event = new ServletContextEvent(getServletContext());
		}

		for (int i = 0; i < instances.length; i++) {
			if (!(instances[i] instanceof ServletContextListener)) {
				continue;
			}
			ServletContextListener listener = (ServletContextListener) instances[i];
			try {
				// 发布容器事件
				fireContainerEvent("beforeContextInitialized", listener);

				/*
				 * Web 应用程序初始化过程开始的通知。
				 * 
				 * 在 Web 应用程序中的任何过滤器或 servlet 初始化之前, 通知所有 ServletContextListener 上下文初始化。
				 */
				if (noPluggabilityListeners.contains(listener)) {
					listener.contextInitialized(tldEvent);
				} else {
					listener.contextInitialized(event);
				}

				fireContainerEvent("afterContextInitialized", listener);
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				fireContainerEvent("afterContextInitialized", listener);
				logger.error(String.format("监听器启动异常, by name: {}", instances[i].getClass().getName()), t);
				ok = false;
			}
		}
		return ok;
	}

	/**
	 * 向所有感兴趣的侦听器发送应用程序停止事件
	 * @return 如果所有事件都成功发送, 则为 true, 否则为 false。
	 */
	public boolean listenerStop() {
		if (logger.isDebugEnabled()) logger.debug("Listener Stopping.");

		boolean ok = true;
		Object listeners[] = getApplicationLifecycleListeners();
		if (listeners != null && listeners.length > 0) {
			ServletContextEvent event = null;
			ServletContextEvent tldEvent = null;
			if (noPluggabilityServletContext != null) { // 启动时被创建 noPluggabilityServletContext
				tldEvent = new ServletContextEvent(noPluggabilityServletContext);
			} else {
				event = new ServletContextEvent(getServletContext());
			}

			for (int i = 0; i < listeners.length; i++) {
				int j = (listeners.length - 1) - i;
				if (listeners[j] == null)
					continue;
				if (listeners[j] instanceof ServletContextListener) {
					ServletContextListener listener = (ServletContextListener) listeners[j];
					try {
						fireContainerEvent("beforeContextDestroyed", listener);

						/*
						 * Servlet 上下文即将关闭的通知。 在通知任何ServletContextListener 上下文销毁之前, 所有servlet 和过滤器都已销毁。
						 */
						if (noPluggabilityListeners.contains(listener)) {
							listener.contextDestroyed(tldEvent);
						} else {
							listener.contextDestroyed(event);
						}

						fireContainerEvent("afterContextDestroyed", listener);
					} catch (Throwable t) {
						ExceptionUtils.handleThrowable(t);
						fireContainerEvent("afterContextDestroyed", listener);
						logger.error(String.format("监听器停止异常, by name: {}", listeners[j].getClass().getName()), t);
						ok = false;
					}
				}
			}
		}
		setApplicationEventListeners(null);
		setApplicationLifecycleListeners(null);

		noPluggabilityServletContext = null;
		noPluggabilityListeners.clear();

		return ok;
	}

	/**
	 * 加载并初始化在 web 应用程序部署描述符中标记为“启动时加载”的所有 servlet
	 *
	 * @param children - 所有当前定义的servlet的包装器数组(包括那些在启动时没有声明的)
	 * @return 如果认为启动时加载成功, 则为 true
	 */
	public boolean loadOnStartup(Container children[]) {
		// 收集需要初始化的“启动时加载”servlet
		TreeMap<Integer, ArrayList<Wrapper>> map = new TreeMap<>();
		for (int i = 0; i < children.length; i++) {
			Wrapper wrapper = (Wrapper) children[i];
			int loadOnStartup = wrapper.getLoadOnStartup();

			if (loadOnStartup < 0) continue; // 稍后实例化

			Integer key = Integer.valueOf(loadOnStartup);
			ArrayList<Wrapper> list = map.get(key);
			if (list == null) {
				list = new ArrayList<>();
				map.put(key, list);
			}
			list.add(wrapper);
		}

		// 加载收集的“启动时加载”servlet
		for (ArrayList<Wrapper> list : map.values()) {
			for (Wrapper wrapper : list) {
				try {
					wrapper.load();
				} catch (ServletException e) {
					getLogger().error(String.format("servlet 加载异常, by name: {}", getName(), wrapper.getName()), e);
					return false;
				}
			}
		}
		return true;
	}

	@Override
	protected synchronized void startInternal() throws LifecycleException {
		if(logger.isDebugEnabled()) logger.debug("Context Starting, by name: " + getBaseName());

		setConfigured(false);
		boolean ok = true;

		postWorkDirectory();

		// 根据需要添加缺少的组件
		if (getResources() == null) {   // (1) 对于Loader是必须的
			if (logger.isDebugEnabled())
				logger.debug("Configuring default Resources");

			try {
				setResources(new StandardRoot(this));
			} catch (IllegalArgumentException e) {
				logger.error("资源初始化异常", e);
				ok = false;
			}
		}
		if (ok) {
			resourcesStart();
		}

		if (getLoader() == null) {
			WebappLoader webappLoader = new WebappLoader();
			webappLoader.setDelegate(getDelegate());
			setLoader(webappLoader);
		}

		if (cookieProcessor == null) {
			cookieProcessor = new Rfc6265CookieProcessor();
		}

		// 若字符集映射器未创建则进行初始化
		getCharsetMapper();

		// Binding thread
		ClassLoader oldCCL = bindThread();

		try {
			if (ok) {
				// 启动所有的从属组件
				Loader loader = getLoader();
				if (loader instanceof Lifecycle) {
					((Lifecycle) loader).start();
				}

				setClassLoaderProperty("clearReferencesStopThreads", getClearReferencesStopThreads());
				setClassLoaderProperty("clearReferencesStopTimerThreads", getClearReferencesStopTimerThreads());
				setClassLoaderProperty("clearReferencesObjectStreamClassCaches", getClearReferencesObjectStreamClassCaches());
				setClassLoaderProperty("clearReferencesObjectStreamClassCaches", getClearReferencesObjectStreamClassCaches());
				setClassLoaderProperty("clearReferencesThreadLocals", getClearReferencesThreadLocals());

				unbindThread(oldCCL);
				oldCCL = bindThread();

				getLogger();

				fireLifecycleEvent(Lifecycle.CONFIGURE_START_EVENT, null);

				// 启动子容器（如果尚未启动）
				for (Container child : findChildren()) {
					if (!child.getState().isAvailable()) {
						child.start();
					}
				}

				// 启动管道中的Valve（包括基本Valve）, 如果有的话
				if (pipeline instanceof Lifecycle) {
					((Lifecycle) pipeline).start();
				}

				Manager contextManager = null;
				Manager manager = getManager();
				if (manager == null) {
					if ((getCluster() != null) && distributable) {
						try {
							contextManager = getCluster().createManager(getName());
						} catch (Exception ex) {
							logger.error("从集群中获得会话管理器错误", ex);
							ok = false;
						}
					} else {
						contextManager = new StandardManager();
					}
				}

				// 如果未指定，则配置默认管理器
				if (contextManager != null) {
					if (logger.isDebugEnabled()) {
						logger.debug("设置会话管理器, by managerName: {}", contextManager.getClass().getName());
					}
					setManager(contextManager);
				}

//				if (manager!=null && (getCluster() != null) && distributable) {
//					getCluster().registerManager(manager);
//				}
			}

			if (!getConfigured()) {
				logger.error("上下文配置错误");
				ok = false;
			}

			// 将资源放入servlet上下文中
			if (ok)
				getServletContext().setAttribute(Globals.RESOURCES_ATTR, getResources());

			if (ok ) {
				if (getInstanceManager() == null) {
					setInstanceManager(createInstanceManager());
				}
				getServletContext().setAttribute(InstanceManager.class.getName(), getInstanceManager());

				InstanceManagerBindings.bind(getLoader().getClassLoader(), getInstanceManager());
			}

			// 回调 ServletContainerInitializers
			for (Map.Entry<ServletContainerInitializer, Set<Class<?>>> entry : initializers.entrySet()) {
				try {
					entry.getKey().onStartup(entry.getValue(), getServletContext());
				} catch (ServletException e) {
					logger.error(String.format("回调 ServletContainerInitializer 逻辑发生异常, by class: [{}", entry.getKey().getClass().getName()), e);
					ok = false;
					break;
				}
			}

			// 配置和调用应用程序事件监听器
			if (ok) {
				if (!listenerStart()) {
					logger.error("调用应用程序监听器失败");
					ok = false;
				}
			}

			try {
				Manager manager = getManager();
				if (manager instanceof Lifecycle) {
					((Lifecycle) manager).start();
				}
			} catch(Exception e) {
				logger.error("会话管理器启动失败", e);
				ok = false;
			}

			// 配置和调用应用程序过滤器
			if (ok) {
				if (!filterStart()) {
					logger.error("调用应用程序过滤器失败");
					ok = false;
				}
			}

			// 加载并初始化所有“load on startup”servlet
			if (ok) {
				if (!loadOnStartup(findChildren())){
					logger.error("加载并初始化Servlet失败");
					ok = false;
				}
			}

			// 启动 Container BackgroundProcessor线程
			super.threadStart();
		} finally {
			// 释放线程
			unbindThread(oldCCL);
		}

		// 根据启动成功情况设置可用状态
		if (ok) {
			if (logger.isDebugEnabled())
				logger.debug("Starting Context Completed.");
		} else {
			logger.error("上下文启动失败, by name: {}", getName());
		}

		startTime=System.currentTimeMillis();
		getResources().gc();

		// 如果出现问题, 重新初始化
		if (!ok) {
			setState(LifecycleState.FAILED);
		} else {
			setState(LifecycleState.STARTING);
		}
	}

	@Override
	public InstanceManager createInstanceManager() {
		return new SimpleInstanceManager();
	}

	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		// 给正在进行的异步请求一个完成的机会
		long limit = System.currentTimeMillis() + unloadDelay;
		/*
		 * inProgressAsyncCount.get(): 返回大于0代表这当前正在进行异步计数, 需等待返回等于0之后才能停止等待
		 * 而inProgressAsyncCount在有其他程序调用Servlet之时会递增加一。如: DispatchServlet处理返回值时。
		 */
		while (inProgressAsyncCount.get() > 0 && System.currentTimeMillis() < limit) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				logger.info("异步等待中断", e);
				break;
			}
		}

		setState(LifecycleState.STOPPING);

		 // Binding thread
        ClassLoader oldCCL = bindThread();
		try {
			// 停止拥有的字容器
			final Container[] children = findChildren();

			// Stop ContainerBackgroundProcessor thread
			threadStop();

			for (int i = 0; i < children.length; i++) {
				children[i].stop();
			}

			// 停止过滤器
			filterStop();

			Manager manager = getManager();
			if (manager instanceof Lifecycle && ((Lifecycle) manager).getState().isAvailable()) {
				((Lifecycle) manager).stop();
			}

			// 停止应用程序监听器
			listenerStop();

			// 取消关联的语言环境字符集映射
			setCharsetMapper(null);

			if (logger.isDebugEnabled()) 
				logger.debug("Processing standard container shutdown...");

			fireLifecycleEvent(Lifecycle.CONFIGURE_STOP_EVENT, null);

			// 停止管道内的Valve, 包括基础Valve
			if (pipeline instanceof Lifecycle && ((Lifecycle) pipeline).getState().isAvailable()) {
				((Lifecycle) pipeline).stop();
			}

			// 清除所有应用程序存储的 servlet 上下文属性
			if (context != null) context.clearAttributes();

			Loader loader = getLoader();
			if (loader instanceof Lifecycle) {
				ClassLoader classLoader = loader.getClassLoader();
				((Lifecycle) loader).stop();
				if (classLoader != null) {
					InstanceManagerBindings.unbind(classLoader);
				}
			}
			
            // Stop resources
            resourcesStop();
		} finally {
			// 线程解绑
			unbindThread(oldCCL);
		}

		// 重置应用程序上下文
		context = null;

		startupTime = 0;
		startTime = 0;
		createdServlets.clear();

		/*
		// 此对象将不再可见或使用
		try {
			resetContext();
		} catch( Exception ex ) {
			logger.error(String.format("重置上下文异常, by context: {}", this,getName()), ex );
		}
		 */
		initializers.clear();

		setInstanceManager(null);

		if (logger.isDebugEnabled()) 
			logger.debug("Stopping Context Complete.");
	}

	protected void resetContext() throws Exception {
		for (Container child : findChildren()) {
			removeChild(child);
		}
		startupTime = 0;
		startTime = 0;

		distributable = false;

		applicationListeners = new String[0];
		applicationEventListenersList.clear();
		applicationLifecycleListenersObjects = new Object[0];

		initializers.clear();

		createdServlets.clear();

		if(logger.isDebugEnabled())
			logger.debug("上下文重置, by name: [{}]", getName());
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		Loader loader = getLoader();
		if (loader instanceof Lifecycle) {
			((Lifecycle) loader).destroy();
		}

		Manager manager = getManager();
		if (manager instanceof Lifecycle) {
			((Lifecycle) manager).destroy();
		}

		if (resources != null) {
			resources.destroy();
		}
		super.destroyInternal();
	}

	/**
	 * 分配资源，包括代理
	 * 
	 * @throws LifecycleException - 如果发生启动错误
	 */
	public void resourcesStart() throws LifecycleException {
		// 如果添加了已经启动的资源，则检查当前状态
		if (!resources.getState().isAvailable()) {
			resources.start();
		}

		if (effectiveMajorVersion >=3 && addWebinfClassesResources) {
			WebResource webinfClassesResource = resources.getResource("/WEB-INF/classes/META-INF/resources");
			if (webinfClassesResource.isDirectory()) {
				getResources().createWebResourceSet(WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/", webinfClassesResource.getURL(), "/");
			}
		}
	}

	/**
	 * 释放资源并销毁代理
	 * 
	 * @return 如果没有发生错误，则为<code>true</code>
	 */
	public boolean resourcesStop() {
		boolean ok = true;

		Lock writeLock = resourcesLock.writeLock();
		writeLock.lock();
		try {
			if (resources != null) {
				resources.stop();
			}
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			logger.error("StandardContext#resourcesStop", t);
			ok = false;
		} finally {
			writeLock.unlock();
		}
		return ok;
	}
	
	@Override
	public void backgroundProcess() {
		if (!getState().isAvailable())
			return;

		Loader loader = getLoader();
		if (loader != null) {
			try {
				loader.backgroundProcess();
			} catch (Exception e) {
                logger.warn("Loader#backgroundProcess() 方法调用异常, by loader: " + loader, e);
			}
		}
		Manager manager = getManager();
		if (manager != null) {
			try {
				manager.backgroundProcess();
			} catch (Exception e) {
                logger.warn( "Manager#backgroundProcess() 方法调用异常, by manager: " + manager, e);
			}
		}
		WebResourceRoot resources = getResources();
		if (resources != null) {
			try {
				resources.backgroundProcess();
			} catch (Exception e) {
                logger.warn("WebResourceRoot#backgroundProcess() 方法调用异常, by resources: " + resources, e);
			}
		}
		
		InstanceManager instanceManager = getInstanceManager();
		if (instanceManager != null) {
			try {
				instanceManager.backgroundProcess();
			} catch (Exception e) {
                logger.warn("InstanceManager#backgroundProcess() 方法调用异常, by instanceManager: " + instanceManager, e);
			}
		}
		// 当前可不理睬 StandardContextValvel的后台处理策略
		super.backgroundProcess();
	}

	@Override
	public boolean fireRequestInitEvent(ServletRequest request) {
		Object instances[] = getApplicationEventListeners();

		if ((instances != null) && (instances.length > 0)) {
			ServletRequestEvent event = new ServletRequestEvent(getServletContext(), request);

			for (int i = 0; i < instances.length; i++) {
				if (instances[i] == null) continue;
				if (!(instances[i] instanceof ServletRequestListener)) continue;

				ServletRequestListener listener = (ServletRequestListener) instances[i];

				try {
					listener.requestInitialized(event);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
					logger.error(String.format("ServletRequestListener [{}] 处理请求初始化事件发生异常", instances[i].getClass().getName()), t);
					request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean fireRequestDestroyEvent(ServletRequest request) {
		Object instances[] = getApplicationEventListeners();
		if ((instances != null) && (instances.length > 0)) {
			ServletRequestEvent event =new ServletRequestEvent(getServletContext(), request);

			for (int i = 0; i < instances.length; i++) {
				int j = (instances.length -1) -i;
				if (instances[j] == null) continue;
				if (!(instances[j] instanceof ServletRequestListener)) continue;

				ServletRequestListener listener = (ServletRequestListener) instances[j];

				try {
					listener.requestDestroyed(event);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
					logger.error(String.format("ServletRequestListener [{}] 处理请求销毁事件发生异常", instances[j].getClass().getName()), t);
					request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * @return 为这个上下文定义的 welcome 文件集。如果未定义, 则返回一个零长度的数组.
	 */
	public String[] getWelcomeFiles() {
		return findWelcomeFiles();
	}

	/**
	 * 获取此上下文的启动时间
	 *
	 * @return 启动此上下文的时间（自 1970 年 1 月 1 日 00:00:00 以来的毫秒数）
	 */
	public long getStartTime() {
		return startTime;
	}


	// -------------------------------------------------------------------------------------
	// other
	// -------------------------------------------------------------------------------------
    @Override
    public ClassLoader bind(boolean usePrivilegedAction, ClassLoader originalClassLoader) {
        Loader loader = getLoader();
        ClassLoader webApplicationClassLoader = null;
        if (loader != null) {
            webApplicationClassLoader = loader.getClassLoader();
        }

        if (originalClassLoader == null) {
            if (usePrivilegedAction) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedContextClassLoaderGetter();
                originalClassLoader = AccessController.doPrivileged(pa);
            } else {
                originalClassLoader = Thread.currentThread().getContextClassLoader();
            }
        }

        if (webApplicationClassLoader == null || webApplicationClassLoader == originalClassLoader) {
            // 不可能或没有必要切换类装入器。返回null表示这一点。
            return null;
        }

        ThreadBindingListener threadBindingListener = getThreadBindingListener();

        if (usePrivilegedAction) {
            PrivilegedAction<Void> pa = new PrivilegedContextClassLoaderSetter(webApplicationClassLoader);
            AccessController.doPrivileged(pa);
        } else {
            Thread.currentThread().setContextClassLoader(webApplicationClassLoader);
        }
        if (threadBindingListener != null) {
            try {
                threadBindingListener.bind();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                logger.error(String.format("ThreadBindingListener#bind() 调用异常, by listener: %s, context: %s", threadBindingListener.getClass().getName(), getName()), t);
            }
        }
        return originalClassLoader;
    }

    @Override
    public void unbind(boolean usePrivilegedAction, ClassLoader originalClassLoader) {
        if (originalClassLoader == null) {
            return;
        }

        if (threadBindingListener != null) {
            try {
                threadBindingListener.unbind();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                logger.error(String.format("ThreadBindingListener#unbind() 调用异常, by listener: %s, context: %s", threadBindingListener.getClass().getName(), getName()), t);
            }
        }

        if (usePrivilegedAction) {
            PrivilegedAction<Void> pa = new PrivilegedContextClassLoaderSetter(originalClassLoader);
            AccessController.doPrivileged(pa);
        } else {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

	/**
	 * 如果合适的话，调整URL模式，使其以前斜杠开头(即正在运行Servlet 2.2应用程序)。否则，返回指定的URL模式不变。
	 *
	 * @param urlPattern - 要调整的URL模式(如果需要)并返回
	 * @return 如果需要，使用前导斜杠的URL模式
	 */
	protected String adjustURLPattern(String urlPattern) {
		if (urlPattern == null)
			return urlPattern;

		if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
			return urlPattern;

		if(logger.isDebugEnabled())
			logger.debug("UrlPattern 警告, 需调整的URI: {}", urlPattern);

		return "/" + urlPattern;

	}
	
	/**
     * 绑定当前线程，用于启动、关闭和重新加载上下文
     *
     * @return 之前的上下文类加载器
     */
    protected ClassLoader bindThread() {
        return bind(false, null);
    }

    /**
     * 解除绑定线程并恢复指定的上下文类加载器。
     *
     * @param oldContextClassLoader - 之前的上下文类加载器
     */
    protected void unbindThread(ClassLoader oldContextClassLoader) {
        unbind(false, oldContextClassLoader);
    }

	/**
	 * 验证提供的FilterMap
	 *
	 * @param filterMap - 提供的FilterMap
	 */
	private void validateFilterMap(FilterMap filterMap) {
		// 验证建议的筛选器映射
		String filterName = filterMap.getFilterName();
		String[] servletNames = filterMap.getServletNames();
		String[] urlPatterns = filterMap.getURLPatterns();
		if (findFilterDef(filterName) == null)
			throw new IllegalArgumentException("filterName 无效, 未找到与之相关联的 FilterDef 实例, filterName: " + filterName) ;

		if (!filterMap.getMatchAllServletNames() && !filterMap.getMatchAllUrlPatterns() && (servletNames.length == 0) && (urlPatterns.length == 0))
			throw new IllegalArgumentException("FilterMap 无效, by filterMap: " + filterMap);
		
		for (String urlPattern : urlPatterns) {
			if (!validateURLPattern(urlPattern)) {
				throw new IllegalArgumentException("不符合规范要求的 pattern, by: urlPattern: " + urlPattern);
			}
		}
	}

//	private void checkConstraintsForUncoveredMethods(SecurityConstraint[] constraints) {
//		SecurityConstraint[] newConstraints = SecurityConstraint.findUncoveredHttpMethods(constraints, getDenyUncoveredHttpMethods(), getLogger());
//		for (SecurityConstraint constraint : newConstraints) {
//			addConstraint(constraint);
//		}
//	}

	/**
	 * 为工作目录设置适当的上下文属性
	 */
	private void postWorkDirectory() {
		// 获取（或计算）工作目录路径
		String workDir = getWorkDir();
		if (workDir == null || workDir.length() == 0) {
			String hostName = null;
			String engineName = null;
			String hostWorkDir = null;

			Container parentHost = getParent();
			if (parentHost != null) {
				hostName = parentHost.getName();
				if (parentHost instanceof StandardHost) {
					hostWorkDir = ((StandardHost)parentHost).getWorkDir();
				}
				Container parentEngine = parentHost.getParent();
				if (parentEngine != null) {
					engineName = parentEngine.getName();
				}
			}

			if ((hostName == null) || (hostName.length() < 1))
				hostName = "_";
			if ((engineName == null) || (engineName.length() < 1))
				engineName = "_";

			String temp = getBaseName();
			if (temp.startsWith("/"))
				temp = temp.substring(1);

			temp = temp.replace('/', '_');
			temp = temp.replace('\\', '_');
			if (temp.length() < 1)
				temp = ContextName.ROOT_NAME;

			if (hostWorkDir != null ) {
				workDir = hostWorkDir + File.separator + temp;
			} else {
				workDir = "work" + File.separator + engineName + File.separator + hostName + File.separator + temp;
			}
			setWorkDir(workDir);
		}

		// 如有必要，创建此目录
		File dir = new File(workDir);
		if (!dir.isAbsolute()) {
			String moonHomePath = null;
			try {
				moonHomePath = getMoonBase().getCanonicalPath();
				dir = new File(moonHomePath, workDir);
				if (logger.isDebugEnabled()) {
					// (上下文工作目录, 为ServletContext提供的私有临时目录)
					logger.debug("Context WorkDirectory Create. contextName: [{}], contextPath: [{}], workDir: {}", getName(), getPath(), dir.getAbsolutePath());
				}
			} catch (IOException e) {
				logger.warn("工作目录创建异常, dir: {}, moonHomePath: {}, contextName: [{}]", workDir, moonHomePath, getName(), e);
			}
		}
		if (!dir.mkdirs() && !dir.isDirectory()) {
			logger.warn("工作目录创建失败, dir: {}, contextName: [{}], contextPath: [{}]", getName(), dir.getAbsolutePath(), getPath());
		}

		// 设置适当的 servlet 上下文属性
		if (context == null) {
			getServletContext();
		}
		// ServletContext属性的名称，该属性存储servlet容器为ServletContext提供的私有临时目录（类型为java.io.File）
		context.setAttribute(ServletContext.TEMPDIR, dir);
		context.setAttributeReadOnly(ServletContext.TEMPDIR);
	}

	/**
	 * 验证建议的 <code>&lt;url-pattern&gt;</code> 的语法是否符合规范要求
	 *
	 * @param urlPattern - 要验证的URL模式
	 * @return 如果URL模式一致，则为 <code>true</code>
	 */
	private boolean validateURLPattern(String urlPattern) {
		if (urlPattern == null)
			return false;
		if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
			return false;
		}
		if (urlPattern.equals("")) {
			return true;
		}
		if (urlPattern.startsWith("*.")) {
			if (urlPattern.indexOf('/') < 0) {
				checkUnusualURLPattern(urlPattern);
				return true;
			} else
				return false;
		}
		if (urlPattern.startsWith("/") && !urlPattern.contains("*.")) {
			checkUnusualURLPattern(urlPattern);
			return true;
		} else
			return false;
	}

	/**
	 * 检查不正常但有效的 <code>&lt;url-pattern&gt;</code>s
	 */
	private void checkUnusualURLPattern(String urlPattern) {
		if (logger.isInfoEnabled()) {
			// 第一组检查 "*" 或 "/foo*" 样式模式
			// 第二组检查 "*.foo.bar" 样式模式
			if((urlPattern.endsWith("*") && (urlPattern.length() < 2 || urlPattern.charAt(urlPattern.length()-2) != '/')) ||
					urlPattern.startsWith("*.") && urlPattern.length() > 2 && urlPattern.lastIndexOf('.') > 1) {
				logger.info("可疑的Url, by urlPattern: {}, context: {}", urlPattern, getName());
			}
		}
	}


	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    /**
	 * 在上下文中管理过滤器映射的帮助器类
	 */
	private static final class ContextFilterMaps {
		private final Object lock = new Object();

		/**
		 * 此应用程序的一组过滤器映射, 按照它们在部署描述符中定义的顺序, 并通过 {@link ServletContext} 添加额外的映射, 可能在部署描述符中定义的映射之前或之后。
		 */
		private FilterMap[] array = new FilterMap[0];

		/**
		 * 通过 ServletContext 添加的过滤器映射可能必须插入到部署描述符中的映射之前, 但必须按照调用 ServletContext 方法的顺序插入。
		 * 这对于在部署描述符之后添加的映射不是问题 - 它们只是添加到末尾 - 但正确地在部署描述符映射之前添加映射需要知道最后一个“之前”映射是在哪里添加的。
		 */
		private int insertPoint = 0;

		public FilterMap[] asArray() {
			synchronized (lock) {
				return array;
			}
		}

		/**
		 * 在当前过滤器映射集的末尾添加过滤器映射
		 *
		 * @param filterMap - 添加的过滤器映射集
		 *            
		 */
		public void add(FilterMap filterMap) {
			synchronized (lock) {
				FilterMap results[] = Arrays.copyOf(array, array.length + 1);
				results[array.length] = filterMap;
				array = results;
			}
		}

		/**
		 * 在部署描述符中定义的映射之前添加过滤器映射, 但在通过此方法添加的任何其他映射之后。即总是添加到当前末尾过滤器映射之前
		 * 
		 * @param filterMap - 添加的过滤器映射集
		 */
		public void addBefore(FilterMap filterMap) {
			synchronized (lock) {
				FilterMap results[] = new FilterMap[array.length + 1];
				System.arraycopy(array, 0, results, 0, insertPoint);
				System.arraycopy(array, insertPoint, results, insertPoint + 1, array.length - insertPoint);
				results[insertPoint] = filterMap;
				array = results;
				insertPoint++;
			}
		}

		/**
		 * 删除过滤器映射
		 *
		 * @param filterMap - 需删除的过滤器映射
		 */
		public void remove(FilterMap filterMap) {
			synchronized (lock) {
				int n = -1;
				for (int i = 0; i < array.length; i++) {
					if (array[i] == filterMap) {
						n = i;
						break;
					}
				}
				if (n < 0)
					return;

				FilterMap results[] = new FilterMap[array.length - 1];
				System.arraycopy(array, 0, results, 0, n);
				System.arraycopy(array, n + 1, results, n, (array.length - 1) - n);
				array = results;
				if (n < insertPoint) {
					insertPoint--;
				}
			}
		}
	}

	/** 限制访问的Servlet上下文 */
	private static class NoPluggabilityServletContext implements ServletContext {
		private final ServletContext servletContext;

		public NoPluggabilityServletContext(ServletContext sc) {
			this.servletContext = sc;
		}

		@Override
		public String getContextPath() {
			return servletContext.getContextPath();
		}

		@Override
		public ServletContext getContext(String uripath) {
			return servletContext.getContext(uripath);
		}

		@Override
		public int getMajorVersion() {
			return servletContext.getMajorVersion();
		}

		@Override
		public int getMinorVersion() {
			return servletContext.getMinorVersion();
		}

		@Override
		public int getEffectiveMajorVersion() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public int getEffectiveMinorVersion() {
			throw new UnsupportedOperationException(("不支持"));
		}

		@Override
		public String getMimeType(String file) {
			return servletContext.getMimeType(file);
		}

		@Override
		public Set<String> getResourcePaths(String path) {
			return servletContext.getResourcePaths(path);
		}

		@Override
		public URL getResource(String path) throws MalformedURLException {
			return servletContext.getResource(path);
		}

		@Override
		public InputStream getResourceAsStream(String path) {
			return servletContext.getResourceAsStream(path);
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			return servletContext.getRequestDispatcher(path);
		}

		@Override
		public RequestDispatcher getNamedDispatcher(String name) {
			return servletContext.getNamedDispatcher(name);
		}

		@Override
		@Deprecated
		public Servlet getServlet(String name) throws ServletException {
			return servletContext.getServlet(name);
		}

		@Override
		@Deprecated
		public Enumeration<Servlet> getServlets() {
			return servletContext.getServlets();
		}

		@Override
		@Deprecated
		public Enumeration<String> getServletNames() {
			return servletContext.getServletNames();
		}

		@Override
		public void log(String msg) {
			servletContext.log(msg);
		}

		@Override
		@Deprecated
		public void log(Exception exception, String msg) {
			servletContext.log(exception, msg);
		}

		@Override
		public void log(String message, Throwable throwable) {
			servletContext.log(message, throwable);
		}

		@Override
		public String getRealPath(String path) {
			return servletContext.getRealPath(path);
		}

		@Override
		public String getServerInfo() {
			return servletContext.getServerInfo();
		}

		@Override
		public String getInitParameter(String name) {
			return servletContext.getInitParameter(name);
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			return servletContext.getInitParameterNames();
		}

		@Override
		public boolean setInitParameter(String name, String value) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Object getAttribute(String name) {
			return servletContext.getAttribute(name);
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			return servletContext.getAttributeNames();
		}

		@Override
		public void setAttribute(String name, Object object) {
			servletContext.setAttribute(name, object);
		}

		@Override
		public void removeAttribute(String name) {
			servletContext.removeAttribute(name);
		}

		@Override
		public String getServletContextName() {
			return servletContext.getServletContextName();
		}

		@Override
		public Dynamic addServlet(String servletName, String className) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Dynamic addServlet(String servletName, Servlet servlet) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Dynamic addServlet(String servletName,
				Class<? extends Servlet> servletClass) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Dynamic addJspFile(String jspName, String jspFile) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public ServletRegistration getServletRegistration(String servletName) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Map<String,? extends ServletRegistration> getServletRegistrations() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public FilterRegistration getFilterRegistration(String filterName) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Map<String,? extends FilterRegistration> getFilterRegistrations() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public SessionCookieConfig getSessionCookieConfig() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void addListener(String className) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public <T extends EventListener> void addListener(T t) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void addListener(Class<? extends EventListener> listenerClass) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public JspConfigDescriptor getJspConfigDescriptor() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public ClassLoader getClassLoader() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void declareRoles(String... roleNames) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public String getVirtualServerName() {
			return servletContext.getVirtualServerName();
		}

		@Override
		public int getSessionTimeout() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void setSessionTimeout(int sessionTimeout) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public String getRequestCharacterEncoding() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void setRequestCharacterEncoding(String encoding) {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public String getResponseCharacterEncoding() {
			throw new UnsupportedOperationException("不支持");
		}

		@Override
		public void setResponseCharacterEncoding(String encoding) {
			throw new UnsupportedOperationException("不支持");
		}
	}


//	@Override
//	public Authenticator getAuthenticator() {
//		// TODO 自动生成的方法存根
//		return null;
//	}

	@Override
	public Set<String> addServletSecurity(Dynamic registration, ServletSecurityElement servletSecurityElement) {
		// TODO 自动生成的方法存根
		return null;
	}

}