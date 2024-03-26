package org.zy.moonstone.core.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.LifecycleEvent;
import org.zy.moonstone.core.connector.Connector;
import org.zy.moonstone.core.container.*;
import org.zy.moonstone.core.container.context.StandardContext;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.*;

import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebServlet;
import java.io.*;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

/**
 * @dateTime 2022年4月1日;
 * @author zy(azurite-Y);
 * @description
 * 用于嵌入/单元测试的最小 moonstone 启动器。
 */
public class Moonstone {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected Server server;

    protected int port = 8080;
    
    protected String hostname = "localhost";
    
    /** moonstone 基本目录 */
    protected String basedir;
    
    /** 设置应用程序加载根目录，若使用的是相对目录则相对于 {@link #basedir } */
    protected String appBaseDir;
    

    // -------------------------------------------------------------------------------------
    // getter、setter
    // -------------------------------------------------------------------------------------
    /**
     * 服务器所使用的根目录，若未设置则使用操作系统的临时目录
     * @param basedir - 应用程序的基本文件夹，所有其他文件夹都将基于该文件夹派生
     */
    public void setBaseDir(String basedir) {
        this.basedir = basedir;
    }
    /**
     * 设置默认连接器的端口。只有在调用getConnector时，才会创建默认连接器
     * @param port - 端口号
     */
    public void setPort(int port) {
        this.port = port;
    }
    /**
     * 默认Host的hostname，默认值为'localhost'
     * @param hostName - 
     */
    public void setHostname(String hostName) {
        this.hostname = hostName;
    }
    /**
     * 设置基本目录。如果未指定，将使用临时目录
     * @param basedir - 应用程序根目录
     */
    public void setBasedir(String basedir) {
		this.basedir = basedir;
	}
    /**
     * 设置应用程序加载根目录，若使用的是相对目录则相对于 {@link #basedir }
     * 
     * @param appBaseDir - 应用程序加载根目录
     */
	public void setAppBaseDir(String appBaseDir) {
		this.appBaseDir = appBaseDir;
	}

	
	// -------------------------------------------------------------------------------------
	// method
	// -------------------------------------------------------------------------------------
	/**
     * 使用指定的配置源初始化服务器。服务器将根据源文件中包含的 moonstone 配置文件（server.xml、web.xml、context.xml、SSL证书等）加载。
     * 如果未指定配置源，则将使用这些文件的默认位置。
     * 
     * @param source - 配置源
     */
    public void init(ConfigurationSource source) {
    	// TODO
//        ConfigFileLoader.setSource(source);
//        addDefaultWebXmlToWebapp = false;
        Moon moon = new Moon();
        // 使用指定源中的常规配置文件加载 Moon 实例
        moon.load();
        // 检索并设置服务器
        server = moon.getServer();
    }
    
    /**
     * 初始化Server
     *
     * @throws LifecycleException - 初始化错误
     */
    public void init() throws LifecycleException {
        getServer();
        server.init();
    }
    
    /**
     * 启动 server
     *
     * @throws LifecycleException - 启动错误
     */
    public void start() throws LifecycleException {
        getServer();
        server.start();
    }

    /**
     * 停止 Server
     *
     * @throws LifecycleException - 停止错误
     */
    public void stop() throws LifecycleException {
        getServer();
        server.stop();
    }


    /**
     * 销毁Server，一但调用本方法则此对象将不能被使用
     *
     * @throws LifecycleException - 销毁错误
     */
    public void destroy() throws LifecycleException {
        getServer();
        server.destroy();
    }
    
    
    /**
     * 获取嵌入式 moonstone 使用的默认 HTTP 连接器。
     * 它首先在服务中配置连接器。如果没有定义连接器，它将使用此 moonstone 实例中指定的端口和地址创建并添加一个默认连接器，并将其返回以供进一步定制。
     * @return
     */
    public Connector getConnector() {
        Service service = getService();
        if (service.findConnectors().length > 0) {
            return service.findConnectors()[0];
        }

        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(port);
        service.addConnector(connector);
        return connector;
    }
    
    /**
     * 在服务中设置指定的连接器（如果它尚不存在）
     * @param connector - 添加的连接器
     */
    public void setConnector(Connector connector) {
        Service service = getService();
        boolean found = false;
        for (Connector serviceConnector : service.findConnectors()) {
            if (connector == serviceConnector) {
                found = true;
            }
        }
        if (!found) { // 未找到则添加
            service.addConnector(connector);
        }
    }
    
    /**
	 * 获取服务器对象。 可以添加侦听器和更多自定义项。
	 * 
	 * @return The Server
	 */
	public Server getServer() {
	    if (server != null) {
	        return server;
	    }
	
	    server = new StandardServer();
	
	    initBaseDir();
	
	    // 设置配置源 TODO
//	    ConfigFileLoader.setSource(new CatalinaBaseConfigurationSource(new File(basedir), null));
	
	    server.setPort( -1 );
	
	    Service service = new StandardService();
	    service.setName(Globals.WEB_APPLICATION_NAME);
	    server.addService(service);
	    return server;
	}

	/**
     * 获取 Service 对象。 可用于添加更多连接器和一些其他全局设置
     * @return
     */
    public Service getService() {
        return getServer().findServices()[0];
    }
    
    public Host getHost() {
	    Engine engine = getEngine();
	    if (engine.findChildren().length > 0) {
	        return (Host) engine.findChildren()[0];
	    }
	
	    Host host = new StandardHost();
	    host.setName(hostname);
	    host.setAppBase(appBaseDir);
	    getEngine().addChild(host);
	    return host;
	}

	/**
     * 设置当前Host - 所有未来的 web 应用程序都将添加到此Host。 当服务器启动时，此为默认Host。
     *
     * @param host - 设置的 Host
     */
    public void setHost(Host host) {
        Engine engine = getEngine();
        boolean found = false;
        for (Container engineHost : engine.findChildren()) {
            if (engineHost == host) {
                found = true;
            }
        }
        if (!found) {
            engine.addChild(host);
        }
    }

    /**
     * 获得Engine，以进行进一步的定制
     * @return engine 对象
     */
    public Engine getEngine() {
        Service service = getServer().findServices()[0];
        if (service.getContainer() != null) {
            return service.getContainer();
        }
        Engine engine = new StandardEngine();
        engine.setName(Globals.WEB_APPLICATION_NAME);
        engine.setDefaultHost(hostname);
        service.setContainer(engine);
        return engine;
    }

    /**
     * 这相当于将web应用程序添加到主机的appBase（通常是Tomcat的webapps目录）
     *
     * @param contextPath - 要用于根上下文的上下文映射“”
     * @param docBase -上下文的基本目录，用于静态文件。必须存在，相对于 server home
     *
     * @return 部署的 context
     */
    public Context addWebapp(String contextPath, String docBase) {
        return addWebapp(getHost(), contextPath, docBase);
    }

    /**
     * 添加上下文编程模式，无默认web。这意味着没有DefaultServlet和web socket支持。也没有 {@link ServletContainerInitializer} 处理，也没有注释处理。
     * 如果以编程方式添加 {@link ServletContainerInitializer} ，则仍然不会扫描 {@link HandlesTypes} 匹配项。
     *
     * <p>
     * 等效的API调用:
     *
     * <pre>{@code
     *  // context-param
     *  ctx.addParameter("name", "value");
     *
     *
     *  // error-page
     *  ErrorPage ep = new ErrorPage();
     *  ep.setErrorCode(500);
     *  ep.setLocation("/error.html");
     *  ctx.addErrorPage(ep);
     *
     *  ctx.addMimeMapping("ext", "type");
     * }</pre>
     *
     * <p>
     * 注意:如果重新加载Context，所有的配置都会丢失。如果您需要重新加载支持，请考虑使用LifecycleListener来提供您的配置。
     *
	 * @param contextPath - 要使用的上下文映射，""用于根上下文
	 * @param docBase - context的基本目录，用于静态文件。必须存在，相对于server home
     * @return 部署的 context
     */
    public Context addContext(String contextPath, String docBase) {
        return addContext(getHost(), contextPath, docBase);
    }

    
    // -------------------------------------------------------------------------------------
	// 额外的定制
	// 可以使用内部api调优各个 moonstone 对象
	// -------------------------------------------------------------------------------------
	/**
	 * @param host - 将在其中部署 context 的 host
	 * @param contextPath - 要使用的上下文映射，""用于根上下文
	 * @param dir - context的基本目录，用于静态文件。必须存在，相对于server home
	 * @return 部署的 context
	 * 
	 * @see #addContext(String, String)
	 */
	public Context addContext(Host host, String contextPath, String dir) {
	    return addContext(host, contextPath, contextPath, dir);
	}

	/**
	 * @param host - 将在其中部署 context 的 host
	 * @param contextPath - 要使用的上下文映射，""用于根上下文
	 * @param contextName - 上下文名称
	 * @param dir - context的基本目录，用于静态文件。必须存在，相对于server home
	 * @return 部署的 context
	 * 
	 * @see #addContext(String, String)
	 */
	public Context addContext(Host host, String contextPath, String contextName, String dir) {
	    Context ctx = createContextInstance(host);
	    ctx.setName(contextName);
	    ctx.setPath(contextPath);
	    ctx.setDocBase(dir);
	    ctx.addLifecycleListener(new FixContextListener());
	
	    if (host == null) {
	        getHost().addChild(ctx);
	    } else {
	        host.addChild(ctx);
	    }
	    return ctx;
	}

	
    /**
     * 一般来说，使用以servlet为参数的方法更好/更快——如果servlet不常用，并且想避免加载所有的deps，可以使用这个方法。
     *
     * 可以自定义返回的servlet，例如:
     *  <pre>
     *    wrapper.addInitParameter("name", "value");
     *  </pre>
     *
     * @param contextPath - 要向其中添加Servlet的上下文
     * @param servletName - Servlet名称(映射时使用)
     * @param servletClass - 要用于Servlet的类
     * @return servlet的包装器
     */
    public Wrapper addServlet(String contextPath, String servletName, String servletClass) {
        Container ctx = getHost().findChild(contextPath);
        return addServlet((Context) ctx, servletName, servletClass);
    }

    /**
     * {@link #addServlet(String, String, String)} 的静态版本
     * 
     * @param ctx - 要将Servlet添加到的上下文
     * @param servletName - Servlet名称(映射时使用)
     * @param servletClass - 要用于Servlet的类
     * @return servlet的包装器
     */
    public static Wrapper addServlet(Context ctx, String servletName, String servletClass) {
        // 将做类名称和设置初始化参数
        Wrapper sw = ctx.createWrapper();
        sw.setServletClass(servletClass);
        sw.setName(servletName);
        ctx.addChild(sw);

        return sw;
    }

    /**
     * 将现有Servlet添加到没有 Context 类或初始化的上下文中
     * 
     * @param contextPath - 要将Servlet添加到的上下文
     * @param servletName - Servlet名称(映射时使用)
     * @param servlet - 添加的 Servlet
     * @return servlet的包装器
     */
    public Wrapper addServlet(String contextPath, String servletName, Servlet servlet) {
        Container ctx = getHost().findChild(contextPath);
        return addServlet((Context) ctx, servletName, servlet);
    }

    /**
     * {@link #addServlet(String, String, Servlet)} 的静态版本
     * @param ctx - 要向其中添加Servlet的上下文
     * @param servletName - Servlet名称(映射时使用)
     * @param servlet - 添加的 Servlet
     * @return servlet的包装器
     */
    public static Wrapper addServlet(Context ctx, String servletName, Servlet servlet) {
        // 将做类名称和设置初始化参数
        Wrapper sw = new ExistingStandardWrapper(servlet);
        sw.setName(servletName);
        ctx.addChild(sw);

        return sw;
    }

	/**
     *
     * @param host - 将在其中部署 context 的 host
     * @param contextPath - 要使用的上下文映射，""用于根上下文
     * @param docBase - context的基本目录，用于静态文件。必须存在，相对于server home
     *
     * @return 部署的 context
     */
    public Context addWebapp(Host host, String contextPath, String docBase) {
        LifecycleListener listener = null;
        try {
            Class<?> clazz = Class.forName(getHost().getConfigClass());
            listener = (LifecycleListener) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            // 包装在IAE中，因为不能轻易地将方法签名更改为以抛出特定的检查异常
            throw new IllegalArgumentException(e);
        }

        return addWebapp(host, contextPath, docBase, listener);
    }


    /**
     * 这相当于将一个web应用程序添加到主机的appBase(通常是 moonstone 的webapps目录)
     *
     * @param host - 将在其中部署 context 的 host
     * @param contextPath - 要使用的上下文映射，""用于根上下文。
     * @param docBase - context的基本目录，用于静态文件。必须存在，相对于server home
     * @param config - 自定义上下文配置帮助器。任何配置都是上面描述的默认web.xml配置的补充。
     *
     * @return 部署的 context
     */
    public Context addWebapp(Host host, String contextPath, String docBase, LifecycleListener config) {
        Context ctx = createContextInstance(host);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);

        // TODO
        /*
        if (addDefaultWebXmlToWebapp) {
            ctx.addLifecycleListener(getDefaultWebXmlListener());
        }

        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));

        if (addDefaultWebXmlToWebapp && (config instanceof ContextConfig)) {
            // prevent it from looking ( if it finds one - it'll have dup error )
            ((ContextConfig) config).setDefaultWebXml(noDefaultWebXmlPath());
        }
         */
        
        ctx.addLifecycleListener(config);

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }

        return ctx;
    }

    /**
     * 为上下文提供默认配置。它大致相当于默认的web.xml，提供了以下特性:
     * <ul>
     * <li>默认servlet映射到 "/"</li>
     * <li>会话超时30分钟s</li>
     * <li>MIME mappings (subset of those in conf/web.xml)</li>
     * <li>Welcome files</li>
     * </ul>
     *
     * @param contextPath - 要为其设置默认值的上下文的路径
     */
    public void initWebappDefaults(String contextPath) {
        Container ctx = getHost().findChild(contextPath);
        initWebappDefaults((Context) ctx);
    }

    /**
     * {@link #initWebappDefaults(String)} 的静态版本
     *
     * @param ctx - 要设置其默认值的上下文
     */
    public static void initWebappDefaults(Context ctx) {
        // Default servlet
        Wrapper servlet = addServlet(ctx, "default", "org.zy.moonstone.core.servlets.DefaultServlet");
        servlet.setLoadOnStartup(1);
        servlet.setOverridable(true);

        // Servlet mappings
        ctx.addServletMappingDecoded("/", "default");

        // Sessions
        ctx.setSessionTimeout(30);

        // MIME type mappings
        addDefaultMimeTypeMappings(ctx);

        // Welcome files
        ctx.addWelcomeFile("index.html");
        ctx.addWelcomeFile("index.htm");
    }

    /**
     * 将默认MIME类型映射添加到提供上下文中
     *
     * @param context  - 应向其添加默认MIME类型映射的web应用程序上下文
     */
    public static void addDefaultMimeTypeMappings(Context context) {
        Properties defaultMimeMappings = new Properties();
        try (InputStream is = Moonstone.class.getResourceAsStream("MimeTypeMappings.properties")) {
            defaultMimeMappings.load(is);
            for (Map.Entry<Object, Object>  entry: defaultMimeMappings.entrySet()) {
                context.addMimeMapping((String) entry.getKey(), (String) entry.getValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 MimeTypeMappings.properties 文件失败", e);
        }
    }

    protected void initBaseDir() {
	    String applicationHome = System.getProperty(Globals.WEB_APPLICATION_HOME);
	    if (basedir == null) {
	        basedir = System.getProperty(Globals.WEB_APPLICATION_BASE);
	    }
	    if (basedir == null) {
	        basedir = applicationHome;
	    }
	    if (basedir == null) {
	        // 使用临时目录
	        basedir = System.getProperty("user.dir") + "/moonstone." + port;
	    }
	
	    File baseFile = new File(basedir);
	    if (baseFile.exists()) {
	        if (!baseFile.isDirectory()) {
	            throw new IllegalArgumentException(String.format("指定的根路径不指向一个文件夹，by path：%", basedir));
	        }
	    } else {
	        if (!baseFile.mkdirs()) {
	            // 可能是权限问题导致错误
	            throw new IllegalStateException(String.format("创建Base目录失败，by path：%", baseFile));
	        }
	    }
	    try {
	    	// 获得规范化路径
	        baseFile = baseFile.getCanonicalFile();
	    } catch (IOException e) {
	    	// 获得绝对路径
	        baseFile = baseFile.getAbsoluteFile();
	    }
	    server.setMoonBase(baseFile);
	    basedir = baseFile.getPath();
	    System.setProperty(Globals.WEB_APPLICATION_BASE, basedir);
	
	    if (applicationHome == null) {
	        server.setMoonHome(baseFile);
	    } else {
	        File homeFile = new File(applicationHome);
	        if (!homeFile.isDirectory() && !homeFile.mkdirs()) {
	            throw new IllegalStateException(String.format("创建Home目录失败，by path：%", applicationHome));
	        }
	        try {
	            homeFile = homeFile.getCanonicalFile();
	        } catch (IOException e) {
	            homeFile = homeFile.getAbsoluteFile();
	        }
	        server.setMoonHome(homeFile);
	    }
	    System.setProperty(Globals.WEB_APPLICATION_HOME, server.getMoonHome().getPath());
	}

    
    /**
     * 用于Maven打包程序的主要可执行方法
     * 
     * @param args - 命令行参数
     * @throws Exception - 如果发生错误
     */
    public static void main(String[] args) throws Exception {
        Moonstone moonstone = new Moonstone();
        
        // 创建一个 Moon 实例并让它解析配置文件。它还将设置一个关机挂钩，以便在需要时停止服务器。使用默认配置源
        moonstone.init(null);
        boolean await = false;
        String path = "";
        // 处理命令行参数
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--war")) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException("无效的命令行, by args: " + args[i - 1]);
                }
                File war = new File(args[i]);
                moonstone.addWebapp(path, war.getAbsolutePath());
            } else if (args[i].equals("--path")) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException("无效的命令行, by args: " + args[i - 1]);
                }
                path = args[i];
            } else if (args[i].equals("--await")) {
                await = true;
            } else if (args[i].equals("--no-jmx")) {
                // 之前已处理过
            } else {
                throw new IllegalArgumentException("无效的命令行, by args: " + args[i]);
            }
        }
        moonstone.start();
        // 理想情况下，实用程序线程是非守护进程
        if (await) {
        	moonstone.getServer().await();
        }
    }
    
    
    // -------------------------------------------------------------------------------------
 	// 辅助方法和类
 	// -------------------------------------------------------------------------------------
 	/**
 	 * 为给定的 Host 创建已配置的上下文。使用 {@link StandardHost#setContextClass(String)} 配置的类的默认构造函数进行实例化
 	 *
 	 * @param host - 给定的Host，若使用默认Host则为null
 	 * @return newly created {@link Context}
 	 */
 	private Context createContextInstance(Host host) {
 	    String contextClass = StandardContext.class.getName();
 	    if (host == null) {
 	        host = this.getHost();
 	    }
 	    if (host instanceof StandardHost) {
 	        contextClass = ((StandardHost) host).getContextClass();
 	    }
 	    try {
 	        return (Context) Class.forName(contextClass).getConstructor().newInstance();
 	    } catch (ReflectiveOperationException  | IllegalArgumentException | SecurityException e) {
 	        throw new IllegalArgumentException(String.format("无上下文Class, by contextClass：%s", contextClass), e);
 	    }
 	}
 	
    
    // -------------------------------------------------------------------------------------
    // 内部类
    // -------------------------------------------------------------------------------------
    /**
	 * 用于包装现有servlet的Helper类。这将禁用servlet生命周期和正常的重新加载，但也减少了开销，并提供了对servlet更直接的控制。
     */
    public static class ExistingStandardWrapper extends StandardWrapper {
        private final Servlet existing;
        private boolean singleThreadModel;
        
        @SuppressWarnings("deprecation")
        public ExistingStandardWrapper( Servlet existing ) {
            this.existing = existing;
            if (existing instanceof javax.servlet.SingleThreadModel) {
                singleThreadModel = true;
                instancePool = new Stack<>();
            }
            this.asyncSupported = hasAsync(existing);
        }

        private static boolean hasAsync(Servlet existing) {
            boolean result = false;
            Class<?> clazz = existing.getClass();
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws != null) {
                result = ws.asyncSupported();
            }
            return result;
        }

        @Override
        public synchronized Servlet loadServlet() throws ServletException {
            if (singleThreadModel) {
                Servlet instance;
                try {
                    instance = existing.getClass().getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new ServletException(e);
                }
                instance.init(facade);
                return instance;
            } else {
                if (!instanceInitialized) {
                    existing.init(facade);
                    instanceInitialized = true;
                }
                return existing;
            }
        }
        
        @Override
        public long getAvailable() {
            return 0;
        }
        @Override
        public boolean isUnavailable() {
            return false;
        }
        @Override
        public Servlet getServlet() {
            return existing;
        }
        @Override
        public String getServletClass() {
            return existing.getClass().getName();
        }
    }
    
    /**
     * 修复启动顺序-如果你不使用web.xml，这是必需的。
     * 
     * <p>
     * context中的start()方法会将'configured'设置为false，并期望侦听器将其设置为true。
     */
    public static class FixContextListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            try {
                Context context = (Context) event.getLifecycle();
                if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
                    context.setConfigured(true);

                }
            } catch (ClassCastException e) {
            }
        }
    }
}
