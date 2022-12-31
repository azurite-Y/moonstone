package org.zy.moonStone.core.interfaces.container;

import java.util.Locale;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletSecurityElement;

import org.zy.moonStone.core.filter.FilterDef;
import org.zy.moonStone.core.filter.FilterMap;
import org.zy.moonStone.core.interfaces.InstanceManager;
import org.zy.moonStone.core.interfaces.http.CookieProcessor;
import org.zy.moonStone.core.interfaces.loader.Loader;
import org.zy.moonStone.core.interfaces.loader.ThreadBindingListener;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.session.interfaces.Manager;
import org.zy.moonStone.core.util.descriptor.ErrorPage;


/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * Context是一个代表servlet上下文的容器，因此在 servlet Engine 中是一个单独的web应用程序。可适配适当的Wrapper来处理这个请求。它还提供了一种方便的机制来使用拦截器来查看这个特定web应用程序处理的每个请求。
 * <p>
 * 连接到Context的父容器通常是一个Host，但也可能是一些其他的实现，或者如果没有必要的话可以省略。
 * <p>
 * 附加到Context的子容器通常是Wrapper的实现(表示单独的servlet定义)。
 */
public interface Context extends Container, ContextBind {
	// ----------------------------------------------------- 常量 -----------------------------------------------------
	/**
	 * 用于添加Welcome文件的容器事件.
	 */
	public static final String ADD_WELCOME_FILE_EVENT = "addWelcomeFile";

	/**
	 * 用于删除包装器的容器事件.
	 */
	public static final String REMOVE_WELCOME_FILE_EVENT = "removeWelcomeFile";

	/**
	 * 用于清除Welcome文件的容器事件.
	 */
	public static final String  CLEAR_WELCOME_FILES_EVENT = "clearWelcomeFiles";

	/**
	 * 更改会话ID的容器事件.
	 */
	public static final String CHANGE_SESSION_ID_EVENT = "changeSessionId";

	// ------------------------------------------------------------- 属性 -------------------------------------------------------------
	/**
	 *
	 * @return 如果为True则允许将请求映射到没有显式声明@MultipartConfig 的指定servlet来解析multipart/form-data请求.
	 */
	public boolean getAllowCasualMultipartParsing();

	/**
	 * 设置为true，允许将请求映射到没有显式声明@MultipartConfig指定的servlet来解析multipart/form-data请求.
	 *
	 * @param allowCasualMultipartParsing - true表示允许这种临时解析，false表示不允许.
	 */
	public void setAllowCasualMultipartParsing(boolean allowCasualMultipartParsing);

	/**
	 * 获取注册的应用程序事件监听器.
	 *
	 * @return 一个数组，包含这个web应用程序的应用程序事件监听器实例，按照它们在web应用程序部署描述符中指定的顺序
	 */
	public Object[] getApplicationEventListeners();

	/**
	 * 按照web应用程序部署描述符中指定的顺序，存储此应用程序的初始化的应用程序事件监听器对象集.
	 *
	 * @param listeners - 实例化的监听器对象的集合.
	 */
	public void setApplicationEventListeners(Object listeners[]);

	/**
	 * 获取已注册的应用程序生命周期监听器.
	 *
	 * @return 一个包含这个web应用程序的应用程序生命周期监听器实例的数组，按照它们在web应用程序部署描述符中指定的顺序
	 */
	public Object[] getApplicationLifecycleListeners();

	/**
	 * 为这个应用程序存储初始化的应用程序生命周期监听器对象集，按照它们在web应用程序部署描述符中指定的顺序.
	 *
	 * @param listeners - 实例化的监听器对象的集合.
	 */
	public void setApplicationLifecycleListeners(Object listeners[]);

	/**
	 * 获取用于给定区域设置的字符集名称。请注意，不同的上下文可能具有不同的Locale到characterset的映射.
	 *
	 * @param locale - 应为其启用映射字符集的区域设置返回
	 * @return 要与给定区域设置一起使用的字符集的名称
	 */
	public String getCharset(Locale locale);

	/**
	 * 返回此上下文的“正确配置”标志.
	 *
	 * @return 如果正确配置了上下文，则为true，否则为false
	 */
	public boolean getConfigured();

	/**
	 * 为此上下文设置“正确配置”标志。如果启动监听器检测到致命的配置错误以避免应用程序可用，则会将此设置为false.
	 *
	 * @param configured - 新的正确配置标志
	 */
	public void setConfigured(boolean configured);

	/**
	 * 返回“将cookies用于会话ID”标志.
	 *
	 * @return 如果允许使用cookies跟踪此web应用程序的会话ID，则为true，否则为false
	 */
	public boolean getCookies();

	/**
	 * 设置“将cookies用于会话ID”标志.
	 */
	public void setCookies(boolean cookies);

	/**
	 * 获取用于会话cookie的名称。覆盖应用程序可能指定的任何设置.
	 *
	 * @return  默认会话cookie名称的值，如果未指定，则为null
	 */
	public String getSessionCookieName();

	/**
	 * 设置用于会话cookie的名称。覆盖应用程序可能指定的任何设置.
	 *
	 * @param sessionCookieName
	 */
	public void setSessionCookieName(String sessionCookieName);

	/**
	 * 获取用于会话cookie标志的use HttpOnly cookie的值.
	 *
	 * @return 如果HttpOnly标志应该设置在会话cookie上，则为true
	 */
	public boolean getUseHttpOnly();

	/**
	 * 设置使用HttpOnly cookie为会话cookie标志.
	 *
	 * @param useHttpOnly - 设置为true表示会话cookie使用HttpOnly cookie
	 */
	public void setUseHttpOnly(boolean useHttpOnly);

	/**
	 * 获取要用于会话cookie的域。覆盖应用程序可能指定的任何设置.
	 *
	 * @return  缺省会话cookie域的值，如果没有指定则为空
	 */
	public String getSessionCookieDomain();

	/**
	 * 设置会话cookie使用的域。覆盖应用程序可能指定的任何设置.
	 *
	 * @param sessionCookieDomain - 要使用的域
	 */
	public void setSessionCookieDomain(String sessionCookieDomain);

	/**
	 * 获取会话cookie使用的路径。覆盖应用程序可能指定的任何设置.
	 *
	 * @return  默认会话cookie路径的值，如果没有指定则为空
	 */
	public String getSessionCookiePath();

	/**
	 * 设置会话cookie使用的路径。覆盖应用程序可能指定的任何设置.
	 *
	 * @param sessionCookiePath - 要使用路径
	 */
	public void setSessionCookiePath(String sessionCookiePath);

	/**
	 * 在会话cookie路径的末尾添加了一个“/”以确保浏览器，尤其是IE，不会针对于/foo的请求发送用于 /foobar请求的session cookie.
	 *
	 * @return 如需添加斜杠则为true，否则为false
	 */
	public boolean getSessionCookiePathUsesTrailingSlash();


	/**
	 * 配置在会话cookie路径的末尾是否需添加了一个“/”以确保浏览器，尤其是IE，不会针对于/foo的请求发送用于 /foobar请求的session cookie 的标识.
	 * @param sessionCookiePathUsesTrailingSlash - 如果斜杠应该被添加，则为True，否则为false
	 */
	public void setSessionCookiePathUsesTrailingSlash(boolean sessionCookiePathUsesTrailingSlash);


	/**
	 * 返回“允许跨servlet上下文”标志.
	 *
	 * @return 如果这个web应用程序允许交叉竞争请求，则为True，否则为false
	 */
	public boolean getCrossContext();

	/**
	 * 设置“允许跨servlet上下文”标志.
	 *
	 * @param crossContext - 新的跨上下文标志
	 */
	public void setCrossContext(boolean crossContext);

	/**
	 * @return 此web应用程序的deny-uncovered-http-methods（拒绝未覆盖的HTTP方法）标志
	 */
	public boolean getDenyUncoveredHttpMethods();

	/**
	 * 为此web应用程序设置拒绝未覆盖的http方法标志.
	 *
	 * @param denyUncoveredHttpMethods - 新的拒绝公开http方法标志
	 */
	public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods);

	/**
	 * 
	 * @return 返回此web应用程序的显示名称
	 */
	public String getDisplayName();

	/**
	 * 设置此web应用程序的显示名称.
	 */
	public void setDisplayName(String displayName);

	/**
	 * 获取此web应用程序的可分发标志.
	 *
	 * @return 此web应用程序的可分发标志的值.
	 */
	public boolean getDistributable();

	/**
	 * 设置此web应用程序的可分发标志.
	 *
	 * @param distributable - 新的可分发标志
	 */
	public void setDistributable(boolean distributable);

	/**
	 * 获取此 Context 的文档根目录.
	 *
	 * @return 绝对路径名或相对（Host的appBase）路径名.
	 */
	public String getDocBase();


	/**
	 * 设置此上下文的文档根目录。这可以是绝对路径名或相对路径名。应用程序的路径名是相对于应用程序库的.
	 *
	 */
	public void setDocBase(String docBase);

	/**
	 * 返回URL编码的上下文路径
	 *
	 * @return URL编码（使用UTF-8）上下文路径
	 */
	public String getEncodedPath();

	/**
	 * 确定当前是否禁用注解解析
	 *
	 * @return 如果此Web应用程序禁用注释分析，则为true
	 */
	public boolean getIgnoreAnnotations();


	/**
	 * 设置此webapplication的批注解析上的布尔值.
	 *
	 * @param ignoreAnnotations 标注解析中的布尔值
	 */
	public void setIgnoreAnnotations(boolean ignoreAnnotations);

	/**
	 * @return 此web应用程序的上下文路径.
	 */
	public String getPath();

	/**
	 * 设置此web应用程序的上下文路径.
	 *
	 */
	public void setPath(String path);

	/**
	 * @return 此web应用程序的可重新加载标志.
	 */
	public boolean getReloadable();

	/**
	 * 设置此web应用程序的可重新加载标志.
	 *
	 */
	public void setReloadable(boolean reloadable);

	/**
	 * @return 此web应用程序的覆盖标志.
	 */
	public boolean getOverride();

	/**
	 * 设置此web应用程序的覆盖标志.
	 */
	public void setOverride(boolean override);


	/**
	 * @return 此上下文是其外观的Servlet上下文.
	 */
	public ServletContext getServletContext();

	/**
	 * @return 此Web应用程序的默认会话超时（分钟）.
	 */
	public int getSessionTimeout();

	/**
	 * 设置此Web应用程序的默认会话超时（以分钟为单位）.默认30分钟
	 *
	 * @param timeout - 新的默认会话超时
	 */
	public void setSessionTimeout(int timeout);

	/**
	 * 如果请求即使违反了数据大小约束，仍将读取（吞没）剩余的请求数据，则返回true.
	 *
	 * @return 如果数据将被吞没，则为true（默认值），否则为false.
	 */
	public boolean getSwallowAbortedUploads();

	/**
	 * 置为 false 以在上传因大小限制而中止后禁用请求数据吞咽.
	 *
	 * @param swallowAbortedUploads - false表示禁用吞咽，否则为true（默认）
	 */
	public void setSwallowAbortedUploads(boolean swallowAbortedUploads);

	/**
	 * @return 若为 true 则在执行 servlet 时将 system.out 和 system.err 重定向到记录器.
	 */
	public boolean getSwallowOutput();

	/**
	 * 设置swallowOutput 标志的值。 如果设置为 true，则 system.out 和 system.err 将在 servlet 执行期间重定向到记录器.
	 *
	 */
	public void setSwallowOutput(boolean swallowOutput);

	/**
	 * @return 用于在此上下文中注册的 servlet 的 Wrapper 实现的 Java 类名.
	 */
	public String getWrapperClass();

	/**
	 * 设置用于在此上下文中注册的 servlet 的 Wrapper 实现的 Java 类名.
	 *
	 * @param wrapperClass - 新的包装类名称
	 * @throws IllegalArgumentException - 如果找不到指定的包装类或者不是 StandardWrapper 的子类
	 */
	public void setWrapperClass(String wrapperClass);

	/**
	 * @return servlet卸载等待时间.
	 */
	public long getUnloadDelay();

	/**
	 * 获取Jar Scanner用于扫描此上下文的Jar资源.
	 * @return  为此上下文配置的Jar Scanner.
	 */
//	public JarScanner getJarScanner();

	/**
	 * 设置Jar Scanner用于扫描此上下文的Jar资源.
	 * @param jarScanner - 用于此上下文的Jar Scanner.
	 */
//	public void setJarScanner(JarScanner jarScanner);

	/**
	 * @return 此上下文使用的验证器。对于已启动的Context，这总是非空的
	 */
//	Authenticator getAuthenticator();

	/**
	 * @return 与此上下文关联的实例管理器.
	 */
	public InstanceManager getInstanceManager();

	/**
	 * 设置与此上下文关联的实例管理器.
	 *
	 * @param instanceManager - 新的实例管理器实例
	 */
	public void setInstanceManager(InstanceManager instanceManager);

	// --------------------------------------------------------- 公共方法 ---------------------------------------------------------
	/**
	 * 向为该应用程序配置的监听器集添加一个新的监听器类名.
	 *
	 * @param listener - 监听器类的Java类名
	 */
	public void addApplicationListener(String listener);

	/**
	 * 为指定的错误或Java异常添加一个错误页面.
	 *
	 * @param errorPage - 要添加的错误页定义
	 */
	public void addErrorPage(ErrorPage errorPage);

	/**
	 * 向此上下文添加筛选器定义.
	 *
	 * @param filterDef - 要添加的过滤器定义
	 */
	public void addFilterDef(FilterDef filterDef);

	/**
	 * 添加区域编码映射(参见Servlet规范2.4的第5.4节)
	 *
	 * @param locale - 要映射的编码的区域设置
	 * @param encoding - 用于给定区域设置的编码
	 */
	public void addLocaleEncodingMappingParameter(String locale, String encoding);

	/**
	 * 添加一个新的MIME映射，替换指定扩展的任何现有映射.
	 *
	 * @param extension - 被映射的文件名扩展名
	 * @param mimeType - 相应的MIME类型
	 */
	public void addMimeMapping(String extension, String mimeType);

	/**
	 * 添加新的上下文初始化参数，替换指定名称的任何现有值.
	 *
	 * @param name - 新参数的名称
	 * @param value - 新参数的值
	 */
	public void addParameter(String name, String value);

	/**
	 * 将一个新的welcome文件添加到此上下文可识别的集合中.
	 *
	 * @param name -  welcome文件名
	 */
	public void addWelcomeFile(String name);

	/**
	 * 将LifecycleListener的类名添加到附加到这个Context的每个包装器中.
	 *
	 * @param listener - LifecycleListener类的Java类名
	 */
	public void addWrapperLifecycle(String listener);

	/**
	 * 添加一个ContainerListener的类名，附加到这个Context的每个包装器中.
	 *
	 * @param listener - ContainerListener类的Java类名
	 */
	public void addWrapperListener(String listener);

	/**
	 * 方法创建并返回一个新的InstanceManagerinstance。这可以用于框架集成或自定义上下文实现的更简单配置.
	 */
	public InstanceManager createInstanceManager();

	/**
	 * Factory方法创建并返回一个新的Wrapper实例，这个Java实现类适合这个Contextimplementation。
	 * 实例化的wrapper的构造函数将被调用，但没有设置任何属性.
	 *
	 * @return 用于包装Servlet的新创建的包装器实例
	 */
	public Wrapper createWrapper();

	/**
	 * @return 为该应用程序配置的应用程序监听器类名集.
	 */
	public String[] findApplicationListeners();

	/**
	 * @return 指定HTTP错误码的错误页面条目(如果有的话);否则返回null.
	 *
	 * @param errorCode - 要查找的错误码
	 */
	public ErrorPage findErrorPage(int errorCode);

	/**
	 * 查找并返回指定的异常类的ErrorPage实例，或者最近的超类的ErrorPage实例(该超类有这样的定义)。如果没有找到相关的ErrorPage实例，则返回null.
	 *
	 * @param throwable - 要找到ErrorPage的异常类型
	 *
	 * @return 用于查找指定Java异常类型错误页条目的异常类型(如果有的话);否则返回null.
	 */
	public ErrorPage findErrorPage(Throwable throwable);

	/**
	 * @return 为所有指定的错误代码和异常类型定义的错误页集.
	 */
	public ErrorPage[] findErrorPages();

	/**
	 * @return 要查找的过滤器名称.
	 *
	 * @param filterName - 要查找的过滤器名称
	 */
	public FilterDef findFilterDef(String filterName);

	/**
	 * @return 为这个上下文定义的过滤器集.
	 */
	public FilterDef[] findFilterDefs();

	/**
	 * @return 此上下文的筛选器映射集.
	 */
	public FilterMap[] findFilterMaps();

	/**
	 * @param extension - 映射到MIME类型的扩展
	 * @return 指定扩展映射到的MIME类型(如果有的话);否则返回null.
	 *
	 */
	public String findMimeMapping(String extension);

	/**
	 * @return 定义MIME映射的扩展。如果不存在，则返回一个零长度的数组.
	 */
	public String[] findMimeMappings();

	/**
	 * @param name - 要返回的参数的名称
	 * @return 指定上下文初始化参数名的值，如果有的话;否则返回null.
	 */
	public String findParameter(String name);

	/**
	 * @return 为该context定义的所有上下文初始化参数的名称。如果没有定义参数，则返回一个零长度数组.
	 */
	public String[] findParameters();

	/**
	 * @param pattern - 请求映射的模式
	 * @return 指定模式映射的servlet名称(如果有的话);否则返回null.
	 *
	 */
	public String findServletMapping(String pattern);

	/**
	 * @return 此上下文的所有已定义 servlet 映射的模式。 如果未定义映射，则返回零长度数组.
	 */
	public String[] findServletMappings();

	/**
	 * @param name - 要验证的welcome文件
	 * @return 如果为此上下文定义了指定的welcome文件，则为 true； 否则返回false
	 */
	public boolean findWelcomeFile(String name);

	/**
	 * @return 为这个上下文定义的welcome文件集。如果未定义，则返回一个零长度的数组.
	 */
	public String[] findWelcomeFiles();

	/**
	 * @return 将自动添加到新创建的 Wrappers 的 LifecycleListener 类集.
	 */
	public String[] findWrapperLifecycles();

	/**
	 * @return 将自动添加到新创建的 Wrappers 的 ContainerListener 类集.
	 */
	public String[] findWrapperListeners();

	/**
	 * 通知所有 {@link javax.servlet.ServletRequestListener} 请求已启动
	 *
	 * @param request - 将传递给监听器的请求对象
	 * @return 如果监听器成功触发，则为 true，否则为 false
	 */
	public boolean fireRequestInitEvent(ServletRequest request);

	/**
	 * 通知所有 {@link javax.servlet.ServletRequestListener} 一个请求已结束.
	 *
	 * @param request - 将传递给监听器的请求对象
	 * @return 如果监听器成功触发，则为True，否则为false
	 */
	public boolean fireRequestDestroyEvent(ServletRequest request);

	/**
	 * 如果支持重新加载，请重新加载此web应用程序.
	 *
	 * @exception IllegalStateException - 如果reloadable属性设置为false.
	 */
	public void reload();

	/**
	 * 从此应用程序的监听器集合中删除指定的应用程序监听器类.
	 *
	 * @param listener - 要删除的监听器的Java类名
	 */
	public void removeApplicationListener(String listener);

	/**
	 * 删除指定错误代码或java语言异常的错误页面(如果存在);否则，不执行任何操作.
	 *
	 * @param errorPage - 要删除的错误页定义
	 */
	public void removeErrorPage(ErrorPage errorPage);

	/**
	 * 如果指定的过滤器定义存在，则从该Context中删除它;否则，将不执行任何操作.
	 *
	 * @param filterDef - 要删除的过滤器定义
	 */
	public void removeFilterDef(FilterDef filterDef);

	/**
	 * 从这个Context中移除过滤器映射.
	 *
	 * @param filterMap - 要删除的筛选器映射
	 */
	public void removeFilterMap(FilterMap filterMap);

	/**
	 * 如果存在指定扩展的MIME映射，则删除它;否则，不采取任何操作.
	 *
	 * @param extension - 要移除映射的扩展
	 */
	public void removeMimeMapping(String extension);

	/**
	 * 如果指定的名称存在，移除上下文初始化参数;否则，不执行任何操作.
	 *
	 * @param name - 要删除的参数的名称
	 */
	public void removeParameter(String name);

	/**
	 * 如果存在，则删除指定模式的任何servlet映射;否则，不会采取任何操作.
	 *
	 * @param pattern - 要删除的映射的URL模式
	 */
	public void removeServletMapping(String pattern);

	/**
	 * 从此上下文可识别的列表中删除指定的welcome文件名.
	 *
	 * @param name - 要删除的welcome文件的名称
	 */
	public void removeWelcomeFile(String name);

	/**
	 * 从将添加到新创建的包装器的LifecycleListener类集合中删除类名.
	 *
	 * @param listener - 要删除的LifecycleListener类的类名
	 */
	public void removeWrapperLifecycle(String listener);

	/**
	 * 从将添加到新创建的Wrappers的ContainerListener类集中删除类名.
	 *
	 * @param listener - 要删除的ContainerListener类的类名
	 */
	public void removeWrapperListener(String listener);

	/**
	 * 向这个web应用程序添加一个ServletContainerInitializer实例.
	 *
	 * @param sci - 要添加的实例
	 * @param classes - 初始化器表示兴趣的类
	 */
	public void addServletContainerInitializer(ServletContainerInitializer sci, Set<Class<?>> classes);

	/**
	 * 此上下文是否在重新加载时暂停
	 *
	 * @return 如果上下文已暂停，则为true
	 */
	public boolean getPaused();

	/**
	 * 配置是否在此上下文的转发时触发请求监听器.
	 *
	 * @param enable - 在转发时触发请求监听器
	 */
	public void setFireRequestListenersOnForwards(boolean enable);

	/**
	 * @return 请求监听器是否会在此上下文的转发中被触发.
	 */
	public boolean getFireRequestListenersOnForwards();

	/**
	 * 配置向客户端发送重定向响应时是否包含响应正文
	 * 
	 * @param enable - 如果为true，则发送重定向的响应正文
	 */
	public void setSendRedirectBody(boolean enable);

	/**
	 * @return 如果上下文配置为在重定向响应中包含响应正文则返回true.
	 */
	public boolean getSendRedirectBody();

	/**
	 * @return 与此上下文关联的加载程序.
	 */
	public Loader getLoader();

	/**
	 * 设置与此上下文关联的加载程序.
	 *
	 * @param loader - 新关联的加载程序
	 */
	public void setLoader(Loader loader);

	/**
	 * @return 与此上下文关联的资源.
	 */
	public WebResourceRoot getResources();

	/**
	 * 设置与此上下文关联的资源对象.
	 *
	 * @param resources - 新关联的资源
	 */
	public void setResources(WebResourceRoot resources);

	/**
	 * @return 与此 Context 关联的 Manager。 如果没有关联的 Manager，则返回 null.
	 */
	public Manager getManager();

	/**
	 * 与此 Context 关联的 Manager。 如果没有关联的 Manager，则返回 null.
	 */
	public void setManager(Manager manager);

	/**
	 * 设置用于处理此上下文的cookie的{@link CookieProcessor}
	 *
	 * @param cookieProcessor - 新的cookie处理器
	 * @throws IllegalArgumentException - 如果指定了一个空的CookieProcessor
	 */
	public void setCookieProcessor(CookieProcessor cookieProcessor);

	/**
	 * @return 用于处理此上下文的cookie的{@link CookieProcessor} 
	 */
	public CookieProcessor getCookieProcessor();

	/**
	 * 当客户端为新会话提供ID时，该ID是否应该被验证? 使用客户端提供的会话ID的唯一用例是在多个web应用程序中使用一个通用的会话ID。<br/>
	 * 因此，任何客户端提供的会话ID应该已经存在于另一个web应用程序中。<br/>
	 * 如果启用了该检查，客户端提供的会话ID将只在当前主机的至少一个其他web应用程序中存在该会话ID时使用。
	 * 请注意，无论此设置如何，始终会应用以下附加测试:
	 * <ul>
	 * <li>会话ID由cookie提供</li>
	 * <li>会话cookie的路径为“/”</li>
	 * </ul>
	 *
	 * @param validateClientProvidedNewSessionId - 如果应应用验证，则为true
	 */
	public void setValidateClientProvidedNewSessionId(boolean validateClientProvidedNewSessionId);

	/**
	 * 客户端提供的会话id在使用之前是否会被验证
	 *
	 * @return 如果验证将被应用，则为True。否则false
	 * @see #setValidateClientProvidedNewSessionId(boolean)
	 */
	public boolean getValidateClientProvidedNewSessionId();

	/**
	 * 如果启用，对 Web 应用程序上下文根的请求将由 Mapper 重定向（添加尾部斜杠）。 这更有效，但具有确认上下文路径有效的副作用。
	 *
	 * @param mapperContextRootRedirectEnabled - 是否启用
	 */
	public void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled);

	/**
	 * 确定对web应用上下文根的请求是否会被Mapper重定向(添加一个斜杠)。这是更有效的，但有一个副作用，即确认上下文路径是有效的。
	 *
	 * @return 如果为此上下文启用了Mapper级重定向，则为true。
	 */
	public boolean getMapperContextRootRedirectEnabled();

	/**
	 * 如果启用，则 Mapper 将重定向对目录的请求（添加斜杠）。 这更有效，但具有确认目录有效的副作用。
	 *
	 * @param mapperDirectoryRedirectEnabled - 是否启用
	 */
	public void setMapperDirectoryRedirectEnabled(boolean mapperDirectoryRedirectEnabled);

	/**
	 * 确定对目录的请求是否会被映射器重定向(添加尾斜杠)。这样效率更高，但会产生确认目录有效的副作用.
	 *
	 * @return 如果为此上下文启用了 wrapper 级别重定向，则为 true
	 */
	public boolean getMapperDirectoryRedirectEnabled();

	/**
	 * 
	 * 控制调用 {@link javax.servlet.http.HttpServletResponse#sendRedirect(String)} 生成的请求是使用相对重定向还是绝对重定向。
	 *
	 * @param useRelativeRedirects - 使用相对重定向为true，使用绝对重定向为false
	 */
	public void setUseRelativeRedirects(boolean useRelativeRedirects);

	/**
	 * 控制调用 {@link javax.servlet.http.HttpServletResponse#sendRedirect(String)} 生成的请求是使用相对重定向还是绝对重定向
	 * 
	 * @return 如果使用相对重定向则为 true ，如果使用绝对重定向则为 false
	 * @see #setUseRelativeRedirects(boolean)
	 */
	public boolean getUseRelativeRedirects();

	/**
	 * 调用中用于获取requestdispatcher的路径是否需要编码?<br/>
	 * 这将影响MoonStone如何处理获取Requestdispatcher的调用，以及MoonStone如何在内部生成用于获取Requestdispatcher的路径.
	 *
	 * @param dispatchersUseEncodedPaths - true表示使用编码路径，否则为false
	 */
	public void setDispatchersUseEncodedPaths(boolean dispatchersUseEncodedPaths);

	/**
	 * 调用中用于获取requestdispatcher的路径是否需要编码?
	 * 这将影响MoonStone如何处理获取Requestdispatcher的调用，以及MoonStone如何在内部生成用于获取Requestdispatcher的路径.
	 *
	 * @return 如果使用编码过的路径，则为True，否则为false
	 */
	public boolean getDispatchersUseEncodedPaths();

	/**
	 * 为这个web应用程序设置默认的请求体编码.
	 *
	 * @param encoding - 默认编码
	 */
	public void setRequestCharacterEncoding(String encoding);

	/**
	 * 获取此web应用程序的默认请求体编码.
	 *
	 * @return 默认的请求体编码
	 */
	public String getRequestCharacterEncoding();

	/**
	 * 设置此web应用程序的默认响应正文编码.
	 *
	 * @param encoding - 默认编码
	 */
	public void setResponseCharacterEncoding(String encoding);

	/**
	 * 获取此web应用程序的默认响应正文编码.
	 *
	 * @return 默认的响应体编码
	 */
	public String getResponseCharacterEncoding();

	/**
	 * 配置当从 {@link javax.servlet.http.HttpServletRequest#getContextPath()}, 返回上下文路径时，是否允许返回值包含多个前导'/'字符.
	 *
	 */
	public void setAllowMultipleLeadingForwardSlashInPath( boolean allowMultipleLeadingForwardSlashInPath);

	/**
	 * 当从 {@link javax.servlet.http.HttpServletRequest#getContextPath()}, 返回上下文路径时，是否允许包含多个前导'/'字符
	 *
	 * @return 如果允许多个前导'/'字符，则为true，否则为false
	 */
	public boolean getAllowMultipleLeadingForwardSlashInPath();

	/**
	 * 递增正在进行的异步计数
	 */
	public void incrementInProgressAsyncCount();

	/**
	 * 递减正在进行的异步计数
	 */
	public void decrementInProgressAsyncCount();

	/**
	 * 配置当 Web 应用程序尝试使用它时，如果该 Web 应用程序使用的上传目标不存在，MoonStone 是否会尝试创建它。
	 *
	 * @param createUploadTargets - 如果 MoonStone 应该尝试创建上传目标，则为 true，否则为 false
	 */
	public void setCreateUploadTargets(boolean createUploadTargets);

	/**
	 * 如果 Web 应用程序尝试使用它时不存在，MoonStone 是否会尝试创建此 Web 应用程序使用的上传目标？
	 *
	 * @return 如果 MoonStone 将尝试创建上载目标，则为true，否则为false
	 */
	public boolean getCreateUploadTargets();

	/**
	 * @return 此web应用程序的特权标志.
	 */
	public boolean getPrivileged();

	/**
	 * 设置此web应用程序的特权标志.
	 *
	 */
	public void setPrivileged(boolean privileged);

	/**
	 * 向此上下文添加筛选器映射.
	 *
	 * @param filterMap - 要添加的过滤器映射
	 */
	public void addFilterMap(FilterMap filterMap);

	/**
	 * 在部署描述符中定义的映射之前，但在通过此方法添加的任何其他映射之后，向此上下文添加一个过滤器映射.
	 *
	 * @param filterMap - 要添加的过滤器映射
	 *
	 * @exception IllegalArgumentException - 如果指定的筛选器名称与现有的筛选器定义不匹配，或者筛选器映射是畸形的
	 */
	public void addFilterMapBefore(FilterMap filterMap);

	/**
	 * 设置此 Web 应用程序的版本 - 用于在使用并行部署时区分同一 Web 应用程序的不同版本
	 * @param webappVersion - 与上下文关联的 webapp 版本，应该是唯一的
	 */
	public void setWebappVersion(String webappVersion);

	/**
	 * @return 此 Web 应用程序的版本 - 用于在使用并行部署时区分同一 Web 应用程序的不同版本
	 */
	public String getWebappVersion();
	
	/**
     * @return 用于此上下文的 WAR、目录的基本名称。
     */
    public String getBaseName();
    
    /**
     * 添加期望提供资源的servlet。用于确保当没有资源时，与servlet关联的、期望资源出现的 Welcome 文件不会被映射。
     *
     * @param servletName - Servlet 名称
     */
    public boolean addResourceOnlyServlets(String servletName);

    /**
     * 检查命名的 Servlet 以查看它是否期望资源存在
     *
     * @param servletName - 要检查的Servlet的名称（根据web.xml）
     * @return 如果 Servlet 需要资源，则为 <code>true</code>，否则为 <code>false</code>
     */
    public boolean isResourceOnlyServlet(String servletName);
    
    /**
     * @return 如果无法将给定的虚拟路径转换为真实路径，则此方法返回 null。
     *
     * @param path - 所需资源的路径
     */
    public String getRealPath(String path);

    /**
     * 添加新的 servlet 映射，替换指定模式的任何现有映射
     *
     * @param pattern - 要映射的 URL 模式
     * @param name - 要执行的相应 servlet 的名称
     */
    public default void addServletMappingDecoded(String pattern, String name) {
        addServletMappingDecoded(pattern, name, false);
    }

    /**
     * 添加新的 servlet 映射，替换指定模式的任何现有映射。
     *
     * @param pattern - 要映射的 URL 模式
     * @param name - 要执行的相应 servlet 的名称
     */
    public void addServletMappingDecoded(String pattern, String name, boolean jspWildcard);

    /**
     * 添加一个资源，Host 自动部署程序将监视该资源的重新加载。注意:这不会在嵌入式模式下使用。
     *
     * @param name - 相对于docBase的资源路径
     */
    public void addWatchedResource(String name);

    /**
     * @return 此 Context 的监视资源集。 如果没有定义，将返回一个长度为零的数组。
     */
    public String[] findWatchedResources();

    /**
     * 从与此上下文关联的列表中删除指定的监视资源名称
     *
     * @param name - 要删除的监视资源的名称
     */
    public void removeWatchedResource(String name);

    /**
     * @return 此 Context 使用的 Servlet 规范的有效主要版本
     */
    public int getEffectiveMajorVersion();

    /**
     * 设置此 Context 使用的 Servlet 规范的有效主版本
     *
     * @param major - 设置版本号
     */
    public void setEffectiveMajorVersion(int major);

    /**
     * @return 此 Context 使用的 Servlet 规范的有效次要版本
     */
    public int getEffectiveMinorVersion();

    /**
     * 设置此 Context 使用的 Servlet 规范的有效次要版本
     *
     * @param minor - 设置版本号
     */
    public void setEffectiveMinorVersion(int minor);

    /**
     * 设置标识，指示是否应将 /WEB-INF/classes 视为已分解的 JAR 和 JAR 资源，就像它们在 JAR 中一样
     *
     * @param addWebinfClassesResources - 新标识
     */
    public void setAddWebinfClassesResources(boolean addWebinfClassesResources);

    /**
     * @return 指示 /WEB-INF/classes 是否应被视为分解的 JAR 和 JAR 资源的标识，就像它们在 JAR 中一样。
     */
    public boolean getAddWebinfClassesResources();
    
    /**
     * @return 相关的ThreadBindingListener
     */
    public ThreadBindingListener getThreadBindingListener();

    /**
     * 获得相关的ThreadBindingListener
     *
     * @param threadBindingListener - 设置在进入和退出应用程序作用域时接收通知的侦听器
     */
    public void setThreadBindingListener(ThreadBindingListener threadBindingListener);
    
    /**
     * 在 {@link javax.servlet.ServletRegistration.Dynamic} 中动态设置Servlet安全性的通知
     * 
     * @param registration - 修改Servlet安全性的 {@link javax.servlet.ServletRegistration.Dynamic}
     * @param servletSecurityElement - 此Servlet的新安全约束
     * @return 当前映射到此注册的url已经存在配置中
     */
    Set<String> addServletSecurity(ServletRegistration.Dynamic registration, ServletSecurityElement servletSecurityElement);
}
