package org.zy.moonStone.core.startup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.LifecycleEvent;
import org.zy.moonStone.core.connector.Connector;
import org.zy.moonStone.core.container.StandardEngine;
import org.zy.moonStone.core.container.StandardHost;
import org.zy.moonStone.core.container.StandardServer;
import org.zy.moonStone.core.container.StandardService;
import org.zy.moonStone.core.container.StandardWrapper;
import org.zy.moonStone.core.container.context.StandardContext;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.container.ConfigurationSource;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Engine;
import org.zy.moonStone.core.interfaces.container.Host;
import org.zy.moonStone.core.interfaces.container.Lifecycle;
import org.zy.moonStone.core.interfaces.container.LifecycleListener;
import org.zy.moonStone.core.interfaces.container.Server;
import org.zy.moonStone.core.interfaces.container.Service;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.ContextName;
import org.zy.moonStone.core.util.IOTools;

// TODO: 临时目录的惰性初始化-只有在调用getTempDir()时，我们才需要创建它。这将避免需要baseDir
// TODO: 允许没有基本目录的上下文，即只允许编程。这将禁用默认servlet。
/**
 * @dateTime 2022年4月1日;
 * @author zy(azurite-Y);
 * @description
 * 用于嵌入/单元测试的最小 MoonStone 启动器。
 * <p>
 * 此类用于嵌入 MoonStone 的应用程序。
 * 
 * <p>
 * 要求:
 * <ul>
 *   <li>所有 MoonStone 类和servlet都在类路径中。(例如，所有都在一个大罐子中，或在 编程工具类路径 中，或在任何其他组合中)</li>
 *   <li>需要一个工作文件的临时目录</li>
 *   <li>无需配置文件。如果项目有一个带有web.xml文件的webapp，这个类提供了一些方法，但是它是可选的——你可以使用你自己的servlet。</li>
 * </ul>
 *
 * <p>
 * 有多种“添加”方法来配置servlet和webapps。默认情况下，这些方法创建一个简单的内存安全领域并应用它。若需要更复杂的安全处理，可以定义此类的子类。
 *
 * <p>
 * 这个类提供了一组配置web应用程序上下文的方便方法; <code>addWebapp()</code> 方法的所有重载。
 * 这些方法等同于将一个web应用程序添加到主机的appbase(通常是webapps目录)。
 * 这些方法创建一个Context，配置它与 <code>conf/web.xml</code> 提供的默认值等价(详细信息请参阅 (see {@link #initWebappDefaults(String)}，并将Context添加到主机。
 * 这些方法不使用全局默认的web.xml;相反，他们添加一个 {@link LifecycleListener} 来配置默认值。
 * 任何与应用程序打包的WEB-INF/web.xml和META-INF/context.xml都将正常处理。
 * 将应用普通的web片段和 {@link javax.servlet.ServletContainerInitializer} 处理。
 *
 * <p>
 * 在复杂的情况下，您可能更喜欢使用普通的MoonStone API来创建webapp上下文；例如，您可能需要在调用 {@link Host#addChild(Container)} 之前安装自定义Loader。
 * 要复制 <code>addWebapp</code> 方法的基本行为，您可能需要调用此类的两个方法：{@link #noDefaultWebXmlPath()} 和 {@link #getDefaultWebXmlListener()}
 *
 * <p>
 * {@link #getDefaultWebXmlListener()} 返回一个 {@link LifecycleListener} ，它添加了标准的DefaultServlet、JSP处理和欢迎文件。
 * 如果你添加这个监听器，你必须防止Tomcat应用任何标准的全局web.xml…
 *
 * <p>
 * {@link #noDefaultWebXmlPath()}  返回一个虚拟路径名来配置防止 {@link ContextConfig} 试图应用全局web.xml文件。
 *
 * <p>
 * 该类提供了main() 和几个简单的CLI参数。它可以用于简单的测试和演示
 * 
 */
public class MoonStone {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected Server server;

    protected int port = 8080;
    
    protected String hostname = "localhost";
    
    /** MoonStone 基本目录 */
    protected String basedir;
    
    /** 设置应用程序加载根目录，若使用的是相对目录则相对于 {@link ServerProperties.MoonStone#basedir } */
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
     * 设置应用程序加载根目录，若使用的是相对目录则相对于 {@link ServerProperties.MoonStone#basedir }
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
     * 使用指定的配置源初始化服务器。服务器将根据源文件中包含的 MoonStone 配置文件（server.xml、web.xml、context.xml、SSL证书等）加载。
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
     * 获取嵌入式 MoonStone 使用的默认 HTTP 连接器。
     * 它首先在服务中配置连接器。如果没有定义连接器，它将使用此 MoonStone 实例中指定的端口和地址创建并添加一个默认连接器，并将其返回以供进一步定制。
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
     * 将指定的WAR文件复制到主机的appBase，然后使用新复制的WAR调用 {@link #addWebapp(String, String)}。
     * 当Tomcat实例停止时，WAR不会从主机的appBase中删除。
     * 注意，{@link ExpandWar} 提供了实用程序方法，如果需要，可以使用这些方法删除WAR和/或扩展目录。
     *
     * @param contextPath - 要使用的上下文映射，""用于根上下文
     * @param source - 应复制WAR的位置
     * @return 部署的上下文
     *
     * @throws IOException - 如果在将WAR文件从指定URL复制到appBase时发生I/O错误
     */
    public Context addWebapp(String contextPath, URL source) throws IOException {
        ContextName cn = new ContextName(contextPath, null);

        // 确保尚未部署冲突的web应用程序
        Host h = getHost();
        if (h.findChild(cn.getName()) != null) {
            throw new IllegalArgumentException(String.format("addWebapp - 子项冲突. url: %s, addContext: %s, originalContext: %s",
                    source, contextPath, cn.getName()));
        }

        // 确保appBase不包含冲突的web应用程序
        File targetWar = new File(h.getAppBaseFile(), cn.getBaseName() + ".war");
        File targetDir = new File(h.getAppBaseFile(), cn.getBaseName());

        if (targetWar.exists()) {
            throw new IllegalArgumentException(String.format("addWebapp - war文件已存在. url: %s, contextName: %s, targetWar: %s",
                    source, contextPath, targetWar.getAbsolutePath()));
        }
        if (targetDir.exists()) {
            throw new IllegalArgumentException(String.format("addWebapp - 工作目录已存在. url: %s, contextName: %s, targetDir: %s",
                    source, contextPath, targetDir.getAbsolutePath()));
        }

        URLConnection uConn = source.openConnection();

        try (
        		InputStream is = uConn.getInputStream();
                OutputStream os = new FileOutputStream(targetWar)) {
            IOTools.flow(is, os);
        }

        return addWebapp(contextPath, targetWar.getAbsolutePath());
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
	// 可以使用内部api调优各个 MoonStone 对象
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
     * 这相当于将一个web应用程序添加到主机的appBase(通常是 MoonStone 的webapps目录)
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
        Wrapper servlet = addServlet(ctx, "default", "org.zy.moonStone.core.servlets.DefaultServlet");
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
        try (InputStream is = MoonStone.class.getResourceAsStream("MimeTypeMappings.properties")) {
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
	        basedir = System.getProperty("user.dir") + "/MoonStone." + port;
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
        MoonStone moonStone = new MoonStone();
        
        // 创建一个 Moon 实例并让它解析配置文件。它还将设置一个关机挂钩，以便在需要时停止服务器。使用默认配置源
        moonStone.init(null);
        boolean await = false;
        String path = "";
        // 处理命令行参数
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--war")) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException("无效的命令行, by args: " + args[i - 1]);
                }
                File war = new File(args[i]);
                moonStone.addWebapp(path, war.getAbsolutePath());
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
        moonStone.start();
        // 理想情况下，实用程序线程是非守护进程
        if (await) {
        	moonStone.getServer().await();
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
