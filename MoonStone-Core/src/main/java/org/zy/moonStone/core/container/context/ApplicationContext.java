package org.zy.moonStone.core.container.context;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.connector.Connector;
import org.zy.moonStone.core.filter.ApplicationFilterRegistration;
import org.zy.moonStone.core.filter.FilterDef;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Engine;
import org.zy.moonStone.core.interfaces.container.Service;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.mapper.ApplicationMapping;
import org.zy.moonStone.core.mapper.MappingData;
import org.zy.moonStone.core.servlets.ApplicationDispatcher;
import org.zy.moonStone.core.servlets.ApplicationServletRegistration;
import org.zy.moonStone.core.session.ApplicationSessionCookieConfig;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.Introspection;
import org.zy.moonStone.core.util.RequestUtil;
import org.zy.moonStone.core.util.ServerInfo;
import org.zy.moonStone.core.util.buf.CharChunk;
import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description 代表 Web 应用程序执行环境的 ServletContext 的标准实现。 此类的一个实例与 StandardContext 的每个实例相关联
 */
public class ApplicationContext implements ServletContext {
//	private final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);

	protected static final boolean STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;
	protected static final boolean GET_RESOURCE_REQUIRE_SLASH = Globals.GET_RESOURCE_REQUIRE_SLASH;

	/**
	 * 此上下文的上下文属性
	 */
	protected Map<String,Object> attributes = new ConcurrentHashMap<>();

	/**
	 * 此上下文的只读属性列表
	 */
	private final Map<String,String> readOnlyAttributes = new ConcurrentHashMap<>();

	/**
	 * 与之关联的 MoonStone Context 实例
	 */
	private final StandardContext context;

	/**
	 * 与之关联的Service实例
	 */
	private final Service service;

	/**
	 * 空字符串集合作为空枚举的基础
	 */
//	private static final List<String> emptyString = Collections.emptyList();

	/**
	 * 空Servlet集合作为空枚举的基础
	 */
//	private static final List<Servlet> emptyServlet = Collections.emptyList();

	/**
	 * 上下文容器的镜像
	 */
	private final ServletContext facade = new ApplicationContextFacade(this);

	/**
	 * 此上下文的合并上下文初始化参数
	 */
	private final Map<String,String> parameters = new ConcurrentHashMap<>();

	/**
	 * 请求分派期间使用的线程本地数据
	 */
	private final ThreadLocal<DispatchData> dispatchData = new ThreadLocal<>();

	/**
	 * 会话Cookie配置
	 */
	private SessionCookieConfig sessionCookieConfig;

	/**
	 * 会话跟踪模式
	 */
	private Set<SessionTrackingMode> sessionTrackingModes = null;

	/**
	 * 默认会话跟踪模式
	 */
	private Set<SessionTrackingMode> defaultSessionTrackingModes = null;

	/**
	 * 支持的会话跟踪模式
	 */
	private Set<SessionTrackingMode> supportedSessionTrackingModes = null;

	/**
	 * 指示是否可以将新的{@link ServletContextListener}添加到应用程序的标志。
	 * 一旦调用了第一个{@link ServletContextListener}，就不能再添加了
	 */
	private boolean newServletContextListenerAllowed = true;

	
	// ------------------------------------------------------ 构造器 ------------------------------------------------------
	/**
	 * 构造这个类的一个新实例，与指定的Context 实例相关联
	 * @param context - 关联的 Context 实例
	 */
	public ApplicationContext(StandardContext context) {
		super();
		this.context = context;
		this.service = ((Engine) context.getParent().getParent()).getService();
		this.sessionCookieConfig = new ApplicationSessionCookieConfig(context);

		// 填充会话跟踪模式
		populateSessionTrackingModes();
	}

	
	// ------------------------------------------------------ getter、setter ------------------------------------------------------
	protected void setNewServletContextListenerAllowed(boolean allowed) {
		this.newServletContextListenerAllowed = allowed;
	}

	
	// ------------------------------------------------------ 其他 ServletContext 实现方法 ------------------------------------------------------
	/**
	 * 返回 Web 应用程序的上下文路径
	 */
	@Override
	public String getContextPath() {
		return context.getPath();
	}

	/**
	 * 返回具有给定名称的 servlet 容器属性，如果没有该名称的属性，则返回 null。属性允许 servlet 容器向 servlet 提供此接口尚未提供的附加信息。 
	 * 有关其属性的信息，请参阅您的服务器文档。 可以使用 getAttributeNames 检索支持的属性列表。
	 * <p>
	 * 该属性作为 java.lang.Object 或某个子类返回。 属性名称应遵循与包名称相同的约定。 Java Servlet API 规范保留与 java.*、javax.* 和 sun.* 匹配的名称。
	 */
	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	/**
	 * 返回一个枚举，其中包含此 ServletContext 中可用的属性名称。
	 * <p>
	 * 使用带有属性名称的 getAttribute 方法来获取属性的值。
	 */
	@Override
	public Enumeration<String> getAttributeNames() {
		Set<String> names = new HashSet<>();
		names.addAll(attributes.keySet());
		return Collections.enumeration(names);
	}

	/**
	 * 返回对应于服务器上指定 URL 的 ServletContext 对象。
	 * <p>
	 * 该方法允许 servlet 访问服务器各个部分的上下文，并根据需要从上下文中获取 RequestDispatcher 对象。
	 * 给定的路径必须以 / 开头，相对于服务器的文档根进行解释，并与其他 Web 的上下文根匹配 托管在此容器上的应用程序。
	 * <p>
	 * 在有安全意识的环境中，servlet 容器可能会为给定的 URL 返回 null。
	 */
	@Override
	public ServletContext getContext(String uri) {
		// 验证指定参数的格式
		if (uri == null || !uri.startsWith("/")) {
			return null;
		}

		Context child = null;
		try {
			// 寻找完全匹配的上下文
			Container host = context.getParent();
			child = (Context) host.findChild(uri);

			// 非运行上下文应该被忽略
			if (child != null && !child.getState().isAvailable()) {
				child = null;
			}

			// 删除任何版本信息并使用映射器
			if (child == null) {
				int i = uri.indexOf("##");
				if (i > -1) {
					uri = uri.substring(0, i);
				}
				// Note: 使用专用的Mapper方法可能会更有效，但这样的实现需要对Mapper进行一些重构，以避免复制/粘贴现有代码。
				MessageBytes hostMB = MessageBytes.newInstance();
				hostMB.setString(host.getName());

				MessageBytes pathMB = MessageBytes.newInstance();
				pathMB.setString(uri);

				MappingData mappingData = new MappingData();
				service.getMapper().map(hostMB, pathMB, null, mappingData);
				child = mappingData.context;
			}
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			return null;
		}

		if (child == null) {
			return null;
		}

		if (context.getCrossContext()) {
			// 如果crossContext被启用，可以总是返回上下文
			return child.getServletContext();
		} else if (child == context) {
			// 仍然可以返回当前上下文吗
			return context.getServletContext();
		} else {
			// 没有回报
			return null;
		}
	}

	/**
	 * 返回包含命名的上下文范围初始化参数值的字符串，如果参数不存在，则返回null。
	 * <p>
	 * 这种方法可以使可用的配置信息对整个“web应用程序”有用。例如，它可以提供网站管理员的电子邮件地址或保存关键数据的系统的名称。
	 */
	@Override
	public String getInitParameter(final String name) {
		return parameters.get(name);
	}

	/**
	 * 返回指定文件的 MIME 类型，如果无法确定 MIME 类型，则返回 null。
	 *
	 * @param file - 用于识别 MIME 类型的文件名
	 */
	@Override
	public String getMimeType(String file) {
		if (file == null) return null;
		
		int period = file.lastIndexOf('.');
		
		if (period < 0) return null;
		// 截取“.”之后的字符
		String extension = file.substring(period + 1);
		
		if (extension.length() < 1) return null;
		return context.findMimeMapping(extension);
	}
	
	/**
     * 返回充当命名 servlet 的包装器的 RequestDispatcher 对象。
     *
     * @param name - 为其请求调度程序的 servlet 的名称
     */
    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        // 验证名称参数
        if (name == null) return null;

        // 创建并返回对应的请求调度器
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null) return null;

        return new ApplicationDispatcher(wrapper, null, null, null, null, null, name);
    }

    /**
     * 获取给定虚拟路径对应的真实路径。
     * <p>
     * 例如，如果 path 等于 /index.html，此方法将返回服务器文件系统上的绝对文件路径，格式为 http://<host>:<port>/<contextPath>/index .html 将被映射，其中 <contextPath> 对应于此 ServletContext 的上下文路径。
     * <p>
     * 返回的真实路径将采用适合运行 servlet 容器的计算机和操作系统的格式，包括正确的路径分隔符。
     * <p>
     * 仅当容器从包含 JAR 文件中解压它们时，必须考虑捆绑在应用程序的 /WEB-INF/lib 目录中的 JAR 文件的 /META-INF/resources 目录中的资源，在这种情况下，必须返回解压位置的路径。
     * <p>
     * 如果 servlet 容器无法将给定的虚拟路径转换为真实路径，则此方法返回 null。
     */
    @Override
    public String getRealPath(String path) {
        String validatedPath = validateResourcePath(path, true);
        return context.getRealPath(validatedPath);
    }

    /**
     * 如果输入路径无效或者是resources.getResource()可以接受的路径，则返回null
     * @param allowEmptyPath - 允许空路径
     */
    private String validateResourcePath(String path, boolean allowEmptyPath) {
        if (path == null) {
            return null;
        }

        if (path.length() == 0 && allowEmptyPath) {
            return path;
        }

        if (!path.startsWith("/")) {
            if (GET_RESOURCE_REQUIRE_SLASH) {
                return null;
            } else {
                return "/" + path;
            }
        }
        return path;
    }

    /**
     * 定义一个对象，它接收来自客户端的请求并将它们发送到服务器上的任何资源（例如 servlet、HTML 文件或 JSP 文件）。
     * servlet 容器创建 RequestDispatcher 对象，该对象用作围绕位于特定路径或由特定名称指定的服务器资源的包装器。
     * <p>
     * 此接口旨在包装 servlet，但 servlet 容器可以创建 RequestDispatcher 对象来包装任何类型的资源。
     */
    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        // 验证路径参数
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(String.format("请求路径不以“/”开头, by path: %s", path));
        }

        // 与 InputBuffer/MoonAdapter 相同的处理顺序首先删除查询字符串
        String uri;
        String queryString;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            uri = path.substring(0, pos);
            queryString = path.substring(pos + 1);
        } else {
            uri = path;
            queryString = null;
        }

        // 删除路径参数
        String uriNoParams = stripPathParams(uri);

        String normalizedUri = RequestUtil.normalize(uriNoParams);
        if (normalizedUri == null) {
            return null;
        }

        try {
	        // 映射是针对规范化uri的
	        if (getContext().getDispatchersUseEncodedPaths()) {
	            // Decode
	             String decodedUri = URLDecoder.decode(normalizedUri,"utf-8");
	            // 安全检查以捕获尝试编码/../序列
	            normalizedUri = RequestUtil.normalize(decodedUri);
	            if (!decodedUri.equals(normalizedUri)) {
	                getContext().getLogger().warn("非法调度路径, by path: " + path, new IllegalArgumentException());
	                return null;
	            }
	
	            // URI需要包含上下文路径
	            uri = URLDecoder.decode(getContextPath(),"utf-8") + uri;
	        } else {
	            // uri被传递给ApplicationDispatcher的构造函数，并最终用作getRequestURI（）的值，该值返回编码值。因此，由于为路径传递的值已解码，因此在此处对uri进行编码。
	        	uri = URLDecoder.decode(getContextPath()+ uri ,"utf-8") ;
	        }
        } catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

        // 使用线程本地URI和映射数据
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        }

        MessageBytes uriMB = dd.uriMB;
        uriMB.recycle();

        // 使用线程本地映射数据
        MappingData mappingData = dd.mappingData;
        
        try {
            // 映射URI
            CharChunk uriCC = uriMB.getCharChunk();
            try {
                uriCC.append(context.getPath());
                uriCC.append(normalizedUri);
                // uri路径映射
                service.getMapper().map(context, uriMB, mappingData);
                if (mappingData.wrapper == null) {
                    return null;
                }
            } catch (Exception e) {
                // 不应该发生
                log("applicationContext.mapping.error", e);
                return null;
            }

            Wrapper wrapper = mappingData.wrapper;
            String wrapperPath = mappingData.wrapperPath.toString();
            String pathInfo = mappingData.pathInfo.toString();
            HttpServletMapping mapping = new ApplicationMapping(mappingData).getHttpServletMapping();

            // 构造一个RequestDispatcher来处理此请求
            return new ApplicationDispatcher(wrapper, uri, wrapperPath, pathInfo, queryString, mapping, null);
        } finally {
            // 在请求结束时回收线程本地数据，这样引用就不会保存到已完成的请求，因为如果卸载上下文，可能会触发内存泄漏。
            mappingData.recycle();
        }
    }
    
    /**
     * 除去路径参数
     * 
     * @param input
     * @return
     */
    static String stripPathParams(String input) {
        if (input.indexOf(';') < 0) {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        int pos = 0;
        int limit = input.length();
        while (pos < limit) {
            int nextSemiColon = input.indexOf(';', pos);
            if (nextSemiColon < 0) {
                nextSemiColon = limit;
            }
            sb.append(input.substring(pos, nextSemiColon));
            int followingSlash = input.indexOf('/', nextSemiColon);
            if (followingSlash < 0) {
                pos = limit;
            } else {
                pos = followingSlash;
            }
        }

        return sb.toString();
    }
    
    /**
     * 返回映射到给定路径的资源的 URL。
     * <p>
     * 该路径必须以 / 开头，并且被解释为相对于当前上下文根目录，或相对于 Web 应用程序的 /WEB-INF/lib 目录中 JAR 文件的 /META-INF/resources 目录。
     * 此方法将首先搜索文档根目录在搜索 /WEB-INF/lib 中的任何 JAR 文件之前，搜索所请求资源的 Web 应用程序。
     * 未定义搜索 /WEB-INF/lib 中的 JAR 文件的顺序。
     * <p>
     * 此方法允许 servlet 容器使资源对任何来源的 servlet 可用。资源可以位于本地或远程文件系统、数据库或 .war 文件中。
     * <p>
     * servlet 容器必须实现访问资源所必需的 URL 处理程序和 URLConnection 对象。
     * <p>
     * 如果没有资源映射到路径名，则此方法返回 null。
     * <p>
     * 某些容器可能允许使用 URL 类的方法写入此方法返回的 URL。
     * <p>
     * 此方法与 java.lang.Class.getResource 的用途不同，后者基于类加载器查找资源。此方法不使用类加载器。
     */
    @Override
    public URL getResource(String path) throws MalformedURLException {
        String validatedPath = validateResourcePath(path, false);

        if (validatedPath == null) {
            throw new MalformedURLException(String.format("URL格式错误异常，by path：%s", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getURL();
        }

        return null;
    }
    
    /**
     * 将位于命名路径的资源作为 InputStream 对象返回。
     * <p>
     * InputStream 中的数据可以是任何类型或长度。 路径必须根据getResource中给出的规则指定。如果指定路径不存在资源，该方法返回null。
     */
    @Override
    public InputStream getResourceAsStream(String path) {
        String validatedPath = validateResourcePath(path, false);
        if (validatedPath == null) {
            return null;
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getInputStream();
        }
        return null;
    }
    
    /**
     * 返回 Web 应用程序中所有资源路径的类目录列表，其中最长的子路径与提供的路径参数匹配。
     * <p>
     * 指示子目录路径的路径以 / 结尾。
     * <p>
     * 返回的路径都是相对于 web 应用程序的根目录，或者相对于 web 应用程序的 /WEB-INF/lib 目录中的 JAR 文件的 /META-INF/resources 目录，并且有一个前导 /。
     * <p>
     * 返回的集合不受 ServletContext 对象的支持，因此返回集合中的更改不会反映在 ServletContext 对象中，反之亦然。
     */
    @Override
    public Set<String> getResourcePaths(String path) {
        // 验证path参数
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException (String.format("请求路径不以“/”开头，by path：", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.listWebAppPaths(path);
        }

        return null;
    }

    /**
     * 返回运行 servlet 的 servlet 容器的名称和版本
     * <p>
     * 返回字符串的形式为服务器名称/版本号。例如JavaServer Web Development Kit可能返回字符串JavaServer Web Dev Kit/1.0。
     */
    @Override
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }
    
    /**
     * 返回与此 ServletContext 对应的此 Web 应用程序的名称
     */
    @Override
    public String getServletContextName() {
        return context.getDisplayName();
    }
    
    @Override
    public void log(String message) {
        context.getLogger().info(message);
    }
    
    @Override
    public void log(String message, Throwable throwable) {
        context.getLogger().error(message, throwable);
    }
    
    /**
     * 从此 ServletContext 中移除具有给定名称的属性。 删除后，后续调用 getAttribute 以检索属性值将返回 null。
     * <p>
     * 如果在 ServletContext 上配置了侦听器，则容器会相应地通知它们。
     */
    @Override
    public void removeAttribute(String name) {
        Object value = null;

        // 无法删除只读属性
        if (readOnlyAttributes.containsKey(name)){
            return;
        }
        value = attributes.remove(name);
        
        if (value == null) {
            return;
        }

//        if (logger.isDebugEnabled()) {
//        	logger.debug("Attribute Remove. name: {}, value: {}. this: {}", name, value, this);
//        }
        
        // 通知感兴趣的应用程序事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        ServletContextAttributeEvent event = new ServletContextAttributeEvent(context.getServletContext(), name, value);
        for (Object obj : listeners) {
            if (!(obj instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener = (ServletContextAttributeListener) obj;
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved", listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved", listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.fireContainerEvent("afterContextAttributeRemoved", listener);
                log(String.format("监听器 [%s] 处理 ServletContext 属性删除事件异常，by attribute：%s", listener, name), t);
            }
        }
    }

    /**
     * 将对象绑定到此 ServletContext 中的给定属性名称。 如果指定的名称已用于某个属性，则此方法将用新属性替换该属性。
     * <p>
     * 如果在 ServletContext 上配置了侦听器，则容器会相应地通知它们。
     * <p>
     * 如果传入一个空值，效果与调用removeAttribute() 相同。
     * <p>
     * 属性名称应遵循与包名称相同的约定。 Java Servlet API 规范保留与 java.*、javax.* 和 sun.* 匹配的名称。
     */
    @Override
    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new NullPointerException("ServletContext 属性名不能为空");
        }

        // Null 值与 removeAttribute() 相同
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // 不能添加或替换只读属性
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }
        
        Object oldValue = attributes.put(name, value);
        boolean replaced = oldValue != null;

//        if (logger.isDebugEnabled()) {
//        	if (replaced) {
//        		logger.debug("Attribute Replaced. name: {}, newValue: {}, oldValue: {}. this: {}", name, value, oldValue, this);
//        	} else {
//        		logger.debug("Attribute Put. name: {}, value: {}. this: {}", name, value, this);
//        	}
//        }
        
        // 通知感兴趣的应用程序事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        ServletContextAttributeEvent event = null;
        if (replaced) {
            event = new ServletContextAttributeEvent(context.getServletContext(), name, oldValue);
        } else {
            event = new ServletContextAttributeEvent(context.getServletContext(), name, value);
        }

        for (Object obj : listeners) {
            if (!(obj instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener = (ServletContextAttributeListener) obj;
            try {
                if (replaced) {
                    context.fireContainerEvent("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced", listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded", listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded", listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (replaced) {
                    context.fireContainerEvent("afterContextAttributeReplaced", listener);
                } else {
                    context.fireContainerEvent("afterContextAttributeAdded", listener);
                }
                log(String.format("监听器 [%s] 处理 ServletContext 属性%s事件异常，by attribute：%s", listener, (replaced ? "替换" : "添加"), name), t);
            }
        }
    }

    /**
     * 将具有给定名称和类名的过滤器添加到此 servlet 上下文。
     * <p>
     * 注册的过滤器可以通过返回的 FilterRegistration 对象进一步配置。
     * <p>
     * 将使用与此 ServletContext 表示的应用程序关联的类加载器来加载指定的类名。
     * <p>
     * 如果此 ServletContext 已包含具有给定过滤器名称的过滤器的初步过滤器注册，它将完成（通过为其分配给定的类名称）并返回。
     * <p>
     * 如果具有给定类名的类表示托管 Bean，则此方法支持资源注入。有关托管 Bean 和资源注入的更多详细信息，请参阅 Java EE 平台和 JSR 299 规范。
     */
    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return addFilter(filterName, className, null);
    }

    /**
     * 在给定的 filterName 下使用此 ServletContext 注册给定的过滤器实例。
     * <p>
     * 注册的过滤器可以通过返回的 FilterRegistration 对象进一步配置。
     * <p>
     * 如果此 ServletContext 已经包含具有给定过滤器名称的过滤器的初步过滤器注册，它将完成（通过将给定过滤器实例的类名分配给它）并返回。
     */
    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return addFilter(filterName, null, filter);
    }

    /**
     * 将具有给定名称和类类型的过滤器添加到此 servlet 上下文。
     * <p>
     * 注册的过滤器可以通过返回的 FilterRegistration 对象进一步配置。
     * <p>
     * 如果此 ServletContext 已经包含具有给定 filterName 的过滤器的初步FilterRegistration，它将完成（通过将给定 filterClass 的名称分配给它）并直接返回。
     * <p>
     * 如果给定的 filterClass 表示托管 Bean，则此方法支持资源注入。有关托管 Bean 和资源注入的更多详细信息，请参阅 Java EE 平台和 JSR 299 规范。
     */
    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return addFilter(filterName, filterClass.getName(), null);
    }

    private FilterRegistration.Dynamic addFilter(String filterName, String filterClass, Filter filter) throws IllegalStateException {
        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(String.format("无效的过滤器名称，by name：%s", filterName));
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文组件 [%s] 还未启动完成，无法添加过滤器 [%s]", getContextPath(), filterName));
        }

        FilterDef filterDef = context.findFilterDef(filterName);

        // 假设“完整”过滤器注册是一个具有类和名称的注册
        if (filterDef == null) {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            context.addFilterDef(filterDef);
        } else {
            if (filterDef.getFilterName() != null && filterDef.getFilterClass() != null) {
                return null;
            }
        }

        if (filter == null) {
            filterDef.setFilterClass(filterClass);
        } else {
            filterDef.setFilterClass(filter.getClass().getName());
            filterDef.setFilter(filter);
        }
        return new ApplicationFilterRegistration(filterDef, context);
    }
    
    /**
     * 实例化给定的过滤器类。
     * <p>
     * 返回的 Filter 实例可以在通过调用 addFilter(String, Filter) 注册到这个 ServletContext 之前进一步定制。
     * <p>
     * 给定的过滤器类必须定义一个无参构造函数，用于实例化它。
     * <p>
     * 如果给定的 clazz 表示托管 Bean，则此方法支持资源注入。有关托管 Bean 和资源注入的更多详细信息，请参阅 Java EE 平台和 JSR 299 规范。
     * 
     * @param c - 要实例化的过滤器类
     * @param T - 要创建的过滤器的类类型
     * @return 一个新的过滤器实例
     */
    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T filter = (T) context.getInstanceManager().newInstance(c.getName());
            return filter;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException e) {
            throw new ServletException(e);
        }
    }

    /**
     * 获取与给定 filterName 的过滤器对应的 FilterRegistration。
     * @param filterName - 过滤器名称
     * @return 具有给定过滤器名称的过滤器的（完整或初步）过滤器注册，如果在该名称下不存在过滤器注册，则返回 null。
     */
    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        FilterDef filterDef = context.findFilterDef(filterName);
        if (filterDef == null) {
            return null;
        }
        return new ApplicationFilterRegistration(filterDef, context);
    }

    /**
     * 将具有给定名称和类名的 servlet 添加到此 servlet 上下文。
     * <p>
     * 注册的 servlet 可以通过返回的 ServletRegistration 对象进一步配置。
     * <p>
     * 将使用与此 ServletContext 表示的应用程序关联的类加载器来加载指定的类名。
     * <p>
     * 如果此 ServletContext 已包含具有给定 servletName 的 servlet 的初步ServletRegistration，它将完成（通过为其分配给定的 className）并直接返回。
     */
    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return addServlet(servletName, className, null, null);
    }

    /**
     * 在给定的 servletName 下使用此 ServletContext 注册给定的 servlet 实例。
     * <p>
     * 注册的 servlet 可以通过返回的 ServletRegistration 对象进一步配置。
     * <p>
     * 如果此 ServletContext 已经包含具有给定 servletName 的 servlet 的初步ServletRegistration，它将完成（通过将给定 servlet 实例的类名分配给它）并返回。
     */
    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return addServlet(servletName, null, servlet, null);
    }

    /**
     * 将具有给定名称和类类型的 servlet 添加到此 servletcontext。
     * <p>
     * 注册的 servlet 可以通过返回的 ServletRegistration 对象进一步配置。
     * <p>
     * 如果此 ServletContext 已经包含具有给定 servletName 的 servlet 的初步ServletRegistration，它将完成（通过将给定 servletClass 的名称分配给它）并返回。
     */
    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return addServlet(servletName, servletClass.getName(), null, null);
    }
    
    private ServletRegistration.Dynamic addServlet(String servletName, String servletClass, Servlet servlet, Map<String,String> initParams) throws IllegalStateException {
        if (servletName == null || servletName.equals("")) {
            throw new IllegalArgumentException(String.format("无效的Servlet 名称，by name：", servletName));
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文组件 [%s] 还未启动完成，无法添加Servlet [%s]", getContextPath(), servletName));
        }

        Wrapper wrapper = (Wrapper) context.findChild(servletName);

        // 假设一个“完整”的 ServletRegistration 是一个具有类和名称的注册
        if (wrapper == null) {
            wrapper = context.createWrapper();
            wrapper.setName(servletName);
            context.addChild(wrapper);
        } else {
            if (wrapper.getName() != null && wrapper.getServletClass() != null) {
                if (wrapper.isOverridable()) {
                    wrapper.setOverridable(false);
                } else {
                    return null;
                }
            }
        }
        
        ServletSecurity annotation = null;
        if (servlet == null) {
            wrapper.setServletClass(servletClass);
            Class<?> clazz = Introspection.loadClass(context, servletClass);
            if (clazz != null) {
                annotation = clazz.getAnnotation(ServletSecurity.class);
            }
        } else {
            wrapper.setServletClass(servlet.getClass().getName());
            wrapper.setServlet(servlet);
            if (context.wasCreatedDynamicServlet(servlet)) {
                annotation = servlet.getClass().getAnnotation(ServletSecurity.class);
            }
        }

        if (initParams != null) {
            for (Map.Entry<String, String> initParam: initParams.entrySet()) {
                wrapper.addInitParameter(initParam.getKey(), initParam.getValue());
            }
        }

        ServletRegistration.Dynamic registration = new ApplicationServletRegistration(wrapper, context);
        if (annotation != null) {
            registration.setServletSecurity(new ServletSecurityElement(annotation));
        }
        return registration;
    }

    /**
     * 实例化给定的 Servlet 类。
     * <p>
     * 返回的 Servlet 实例可以在通过调用 addServlet(String, Servlet) 注册到这个 ServletContext 之前进一步定制。
     * <p>
     * 给定的 Servlet 类必须定义一个无参构造函数，用于实例化它。
     */
    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T servlet = (T) context.getInstanceManager().newInstance(c.getName());
            context.dynamicServletCreated(servlet);
            return servlet;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException e) {
            throw new ServletException(e);
        }
    }

    /**
     * 获取与具有给定 servletName 的 servlet 对应的 ServletRegistration。
     * @return 具有给定 servletName 的 servlet 的（完整或初步） ServletRegistration，如果在该名称下不存在任何 ServletRegistration，则为 null。
     */
    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        Wrapper wrapper = (Wrapper) context.findChild(servletName);
        if (wrapper == null) {
            return null;
        }

        return new ApplicationServletRegistration(wrapper, context);
    }

    /**
     * 获取此 ServletContext 默认支持的会话跟踪模式。
     * <p>
     * 返回的集合不受 ServletContext 对象的支持，因此返回集合中的更改不会反映在 ServletContext 对象中，反之亦然。
     */
    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return defaultSessionTrackingModes;
    }
    
    /**
     * 填充会话跟踪模式
     */
    private void populateSessionTrackingModes() {
        // 默认情况下始终启用 URL 重写
        defaultSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);
        supportedSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);

        if (context.getCookies()) {
            defaultSessionTrackingModes.add(SessionTrackingMode.COOKIE);
            supportedSessionTrackingModes.add(SessionTrackingMode.COOKIE);
        }

        // 默认情况下未启用 SSL，因为它只能在其自己的 Context > Host > Engine > Service 上使用
        Connector[] connectors = service.findConnectors();
        // 需要至少一个启用 SSL 的连接器才能使用 SSL 会话 ID。
        for (Connector connector : connectors) {
            if ( Boolean.TRUE.equals(connector.isSSLEnabled()) ) {
                supportedSessionTrackingModes.add(SessionTrackingMode.SSL);
                break;
            }
        }
    }

    /**
     * 获取对此 ServletContext 有效的会话跟踪模式。
     * <p>
     * 有效的会话跟踪模式是提供给 setSessionTrackingModes 的那些。
     * <p>
     * 返回的集合不受 ServletContext 对象的支持，因此返回集合中的更改不会反映在 ServletContext 对象中，反之亦然。
     * 
     * @return 对此 ServletContext 有效的会话跟踪模式集
     */
    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        if (sessionTrackingModes != null) {
            return sessionTrackingModes;
        }
        return defaultSessionTrackingModes;
    }
    
    /**
     * 获取 SessionCookieConfig 对象，通过该对象可以配置代表此 ServletContext 创建的会话跟踪 cookie 的各种属性。
     * <p>
     * 重复调用此方法将返回相同的 SessionCookieConfig 实例。
     * 
     * @return SessionCookieConfig 对象，通过它可以配置代表此 ServletContext 创建的会话跟踪 cookie 的各种属性
     */
    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }

    /**
     * 设置对该 ServletContext 生效的会话跟踪模式。
     * <p>
     * 给定的 sessionTrackingModes 替换了之前在此 ServletContext 上调用此方法设置的任何会话跟踪模式。
     * 
     * @param  sessionTrackingModes - 对这个 ServletContext 生效的会话跟踪模式集
     */
    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置会话跟踪模式，by context：%s", getContextPath()));
        }

        // 检查是否只请求了支持的跟踪模式
        for (SessionTrackingMode sessionTrackingMode : sessionTrackingModes) {
            if (!supportedSessionTrackingModes.contains(sessionTrackingMode)) {
                throw new IllegalArgumentException(String.format("无法为上下文 [%s] 设置不支持的会话跟踪模式 [%s]", getContextPath(), sessionTrackingMode.toString()));
            }
        }

        if (sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            if (sessionTrackingModes.size() > 1) {
                throw new IllegalArgumentException(String.format("设置了支持SSL，那么就无需再支持其他会话跟踪方式，by context：[%s]", getContextPath()));
            }
        }

        this.sessionTrackingModes = sessionTrackingModes;
    }

    /**
     * 在此 ServletContext 上设置具有给定名称和值的上下文初始化参数。
     * 
     * @return 如果在此 ServletContext 上成功设置了具有给定名称和值的上下文初始化参数，则为 true；如果未设置，因为此 ServletContext 已包含具有匹配名称的上下文初始化参数，则为 false
     */
    @Override
    public boolean setInitParameter(String name, String value) {
        if (name == null) {
            throw new NullPointerException("设置的初始化参数名不能为null");
        }
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置会话跟踪模式，by context：%s", getContextPath()));
        }

        return parameters.putIfAbsent(name, value) == null;
    }

    /**
     * 将给定类类型的侦听器添加到此 ServletContext。
     * 给定的listenerClass必须实现以下一个或多个接口:
     * <ul>
     * <li>ServletContextAttributeListener</li>
     * <li>ServletRequestListener</li>
     * <li>ServletRequestAttributeListener</li>
     * <li>javax.servlet.http.HttpSessionAttributeListener </li>
     * <li>javax.servlet.http.HttpSessionIdListener</li>
     * <li>javax.servlet.http.HttpSessionListener </li>
     * </ul>
     * 如果这个 ServletContext 被传递给 ServletContainerInitializer.onStartup，那么除了上面列出的接口之外，给定的 listenerClass 还可以实现 ServletContextListener。
     * <p>
     * 如果给定的listenerClass实现了一个listenerinterface，它的调用顺序对应于声明顺序(即。，如果它实现了ServletRequestListener、ServletContextListener或javax.servlet.http.HttpSessionListener)，那么新的监听器将被添加到该接口的监听器的有序列表的末尾。
     * @param listenerClass - 要实例化的监听器类
     */
    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        EventListener listener;
        try {
            listener = createListener(listenerClass);
        } catch (ServletException e) {
            throw new IllegalArgumentException(String.format("实例化监听器异常，by listener：%s", listenerClass.getName()), e);
        }
        addListener(listener);
    }

    /**
     * 将具有给定类名的监听器添加到此ServletContext。
     * <p>
     * 将使用与此ServletContext表示的应用程序关联的ClassLoader加载具有给定名称的类，并且必须实现以下一个或多个接口：
     * <ul>
     * <li>ServletContextAttributeListener</li>
     * <li>ServletRequestListener</li>
     * <li>ServletRequestAttributeListener</li>
     * <li>javax.servlet.http.HttpSessionAttributeListener </li>
     * <li>javax.servlet.http.HttpSessionIdListener</li>
     * <li>javax.servlet.http.HttpSessionListener </li>
     * </ul>
     * 如果将此ServletContext传递给ServletContainerInitializer。在启动时，除了上面列出的接口之外，具有给定名称的类还可以实现ServletContextListener。
     * <p>
     * 作为此方法调用的一部分，容器必须加载具有指定类名的类，以确保实现所需接口之一。
     * <p>
     * 如果具有给定名称的类实现了一个监听器接口，其调用顺序对应于声明顺序（即，如果它实现了ServletRequestListener、ServletContextListener或javax.servlet.http.HttpSessionListener），那么新的监听器将被添加到该接口的监听器有序列表的末尾。
     * @param className - 监听器的完全限定类名
     */
    @Override
    public void addListener(String className) {
        try {
            if (context.getInstanceManager() != null) { 
            	Object obj = context.getInstanceManager().newInstance(className);

                if (!(obj instanceof EventListener)) {
                    throw new IllegalArgumentException(String.format("添加的监听器必须实现 EventListener 接口，by className：%s", className));
                }

                EventListener listener = (EventListener) obj;
                addListener(listener);
            }
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new IllegalArgumentException(String.format("监听器实例化异常，by className：%s", className), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(String.format("由指定的监听器全限定类名反射异常，by className：%s", className), e);
        }

    }

    /**
   * 将给定的侦听器添加到此ServletContext。
     * <p>
     * 将使用与此ServletContext表示的应用程序关联的ClassLoader加载具有给定名称的类，并且必须实现以下一个或多个接口：
     * <ul>
     * <li>ServletContextAttributeListener</li>
     * <li>ServletRequestListener</li>
     * <li>ServletRequestAttributeListener</li>
     * <li>javax.servlet.http.HttpSessionAttributeListener </li>
     * <li>javax.servlet.http.HttpSessionIdListener</li>
     * <li>javax.servlet.http.HttpSessionListener </li>
     * </ul>
     * 如果将此ServletContext传递给ServletContainerInitializer。在启动时，除了上面列出的接口之外，具有给定名称的类还可以实现ServletContextListener。
     * <p>
     * 如果具有给定名称的类实现了一个监听器接口，其调用顺序对应于声明顺序（即，如果它实现了ServletRequestListener、ServletContextListener或javax.servlet.http.HttpSessionListener），那么新的监听器将被添加到该接口的监听器有序列表的末尾。
     * @param T - 添加监听器的类型
     * @param t - 添加的监听器实例
     */
    @Override
    public <T extends EventListener> void addListener(T t) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置会话跟踪模式，by context：%s", getContextPath()));
        }

        boolean match = false;
        if (t instanceof ServletContextAttributeListener || t instanceof ServletRequestListener || t instanceof ServletRequestAttributeListener || t instanceof HttpSessionIdListener || t instanceof HttpSessionAttributeListener) {
            context.addApplicationEventListener(t);
            match = true;
        }

        if (t instanceof HttpSessionListener || (t instanceof ServletContextListener && newServletContextListenerAllowed)) {
            // 将监听器直接添加到实例列表中，而不是类名称列表中
            context.addApplicationLifecycleListener(t);
            match = true;
        }

        if (match) return;

        if (t instanceof ServletContextListener) {
            throw new IllegalArgumentException(String.format("不允许添加新的 ServletContextListener 实现 [%s] 到此上下文 [%s] 中，", t.getClass().getName(), context.getPath()));
        } else {
            throw new IllegalArgumentException(String.format("错误类型（非限定类型）的添加，by wrongType：", t.getClass().getName()));
        }
    }

    /**
     * 实例化给定的 EventListener 类。
     * <p>
     * 指定的 EventListener 类必须至少实现以下接口之一：
     * <ul>
     * <li>ServletContextAttributeListener</li>
     * <li>ServletRequestListener</li>
     * <li>ServletRequestAttributeListener</li>
     * <li>javax.servlet.http.HttpSessionAttributeListener </li>
     * <li>javax.servlet.http.HttpSessionIdListener</li>
     * <li>javax.servlet.http.HttpSessionListener </li>
     * </ul>
     * <p>
     * 返回的 EventListener 实例可以在通过调用 addListener(EventListener) 注册到此 ServletContext 之前进一步定制。
     * <p>
     * 给定的 EventListener 类必须定义一个无参构造函数，用于实例化它。
     * @throws ServletException 
     */
    @Override
    public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T listener = (T) context.getInstanceManager().newInstance(c);
            if (listener instanceof ServletContextListener || listener instanceof ServletContextAttributeListener || listener instanceof ServletRequestListener || listener instanceof ServletRequestAttributeListener ||
                    listener instanceof HttpSessionListener || listener instanceof HttpSessionIdListener || listener instanceof HttpSessionAttributeListener) {
                return listener;
            }
            
            throw new IllegalArgumentException(String.format("错误类型（非限定类型）监听器的创建，by wrongType：", listener.getClass().getName()));
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException e) {
        	throw new ServletException(e);
        }
    }
    
    /**
     * 获取与使用此 ServletContext 注册的所有过滤器相对应的 FilterRegistration 对象（由过滤器名称键入）的（可能为空）映射。
     * <p>
     * 返回的 Map 包括与所有已声明和注释的过滤器相对应的 FilterRegistration 对象，以及与通过 addFilter 方法之一添加的所有过滤器相对应的FilterRegistration 对象。
     * <p>
     * 对返回的 Map 的任何更改都不得影响 thisServletContext。
     * 
     * @return 对应于当前使用此 ServletContext 注册的所有过滤器的（完整和初步）FilterRegistration 对象的映射
     */
    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        Map<String, ApplicationFilterRegistration> result = new HashMap<>();

        FilterDef[] filterDefs = context.findFilterDefs();
        for (FilterDef filterDef : filterDefs) {
            result.put(filterDef.getFilterName(), new ApplicationFilterRegistration(filterDef, context));
        }
        return result;
    }
    
    /**
     * 获取与使用此 ServletContext 注册的所有 servlet 对应的 ServletRegistration 对象（key为 servletName）的（可能为空）映射。
     * <p>
     * 返回的 Map 包括与所有已声明和注释的 servlet 对应的 ServletRegistration 对象，以及与通过 addServlet 方法之一添加的所有 servlet 对应的 ServletRegistration 对象。
     * <p>
     * 如果允许，对返回映射的任何更改都不得影响此ServletContext。
     * 
     * @return (完整的和初步的)servleregistration对象的映射，对应于当前在这个ServletContext中注册的所有servlet
     */
    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        Map<String, ApplicationServletRegistration> result = new HashMap<>();

        Container[] wrappers = context.findChildren();
        for (Container wrapper : wrappers) {
            result.put(wrapper.getName(), new ApplicationServletRegistration((Wrapper) wrapper, context));
        }
        return result;
    }
    
    /**
     * 获取此ServletContext默认支持的会话超时时间(以分钟为单位)
     * @return 这个ServletContext默认支持的会话超时时间，以分钟为单位
     */
    @Override
    public int getSessionTimeout() {
        return context.getSessionTimeout();
    }
    
    /**
     * 为这个ServletContext设置会话超时时间(以分钟为单位)。
     * @param sessionTimeout - 会话超时时间(分钟)
     */
    @Override
    public void setSessionTimeout(int sessionTimeout) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置会话超时时间，by context：%s", getContextPath()));
        }

        context.setSessionTimeout(sessionTimeout);
    }

    /**
     * 获取此ServletContext默认支持的请求字符编码。
     * 如果在部署描述符或容器的特定配置中(对于容器中的所有web应用程序)没有指定requestencoding字符编码，则该方法返回null。
     */
    @Override
    public String getRequestCharacterEncoding() {
        return context.getRequestCharacterEncoding();
    }

    /**
     * 设置此ServletContext的请求字符编码。
     */
    @Override
    public void setRequestCharacterEncoding(String encoding) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置请求字符编码，by context：%s", getContextPath()));
        }

        context.setRequestCharacterEncoding(encoding);
    }

    /**
     * 获取此ServletContext默认支持的响应字符编码。
     * 如果在部署描述符或容器特定配置中没有指定responseencoding字符编码(对于容器中的所有web应用程序)，则该方法返回null。
     */
    @Override
    public String getResponseCharacterEncoding() {
        return context.getResponseCharacterEncoding();
    }

    /**
     * 设置此ServletContext的响应字符编码。
     */
    @Override
    public void setResponseCharacterEncoding(String encoding) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("上下文未启动完成，无法设置响应字符编码，by context：%s", getContextPath()));
        }

        context.setResponseCharacterEncoding(encoding);
    }


    // -------------------------------------------------------- Package Methods
    protected StandardContext getContext() {
        return this.context;
    }

    /**
     * 清空创建的应用程序属性
     */
    protected void clearAttributes() {
        // 创建要删除的属性列表
        List<String> list = new ArrayList<>();
        for (String s : attributes.keySet()) {
            list.add(s);
        }

        // 删除源自应用程序的属性(只读属性将保留在原处)
        for (String key : list) {
            removeAttribute(key);
        }
    }


    /**
     * @return 与ApplicationContext相关联的facade
     */
    protected ServletContext getFacade() {
        return this.facade;
    }


    /**
     * 设置属性为只读
     */
    void setAttributeReadOnly(String name) {
        if (attributes.containsKey(name)) readOnlyAttributes.put(name, name);
    }

    /**
     * 返回此 servlet 容器支持的 Servlet API 的主要版本。 所有符合 4.0 版本的实现都必须让这个方法返回整数 4。
     */
	@Override
	public int getMajorVersion() {
		return 0;
	}

	/**
	 * 返回此 servlet 容器支持的 Servlet API 的次要版本。 所有符合 4.0 版本的实现都必须让这个方法返回整数 0。
	 */
	@Override
	public int getMinorVersion() {
		return 0;
	}

	/**
	 * 获取此 ServletContext 表示的应用程序所基于的 Servlet 规范的主要版本。
	 * <p>
	 * 返回的值可能与 getMajorVersion 不同，getMajorVersion 返回的是 Servlet 容器支持的 Servlet 规范的主要版本。
	 * @return 此 ServletContext 表示的应用程序所基于的 Servlet 规范的主要版本
	 */
	@Override
	public int getEffectiveMajorVersion() {
		return 0;
	}
	
	/**
	 * 获取此 ServletContext 表示的应用程序所基于的 Servlet 规范的次要版本。
	 * <p>
	 * 返回的值可能与 getMinorVersion 不同，后者返回 Servlet 容器支持的 Servlet 规范的次要版本。
	 * @return 此 ServletContext 表示的应用程序所基于的 Servlet 规范的次要版本
	 */
	@Override
	public int getEffectiveMinorVersion() {
		return 0;
	}

	/**
	 * @param name - servlet 名称
	 * @return 具有给定名称的 javax.servlet.Servlet Servlet
	 */
	@Override
	public Servlet getServlet(String name) throws ServletException {
		return null;
	}

	/**
	 * @return javax.servlet.Servlet Servlet 的枚举
	 */
	@Override
	public Enumeration<Servlet> getServlets() {
		return null;
	}

	/**
	 * @return javax.servlet.Servlet Servlet 名称的枚举
	 */
	@Override
	public Enumeration<String> getServletNames() {
		return null;
	}

	/**
	 * @param exception - 异常错误对象
	 * @param msg - 异常错误信息
	 */
	@Override
	public void log(Exception exception, String msg) {
		
	}

	/**
	 * 包含上下文初始化参数名称的字符串对象的枚举
	 */
	@Override
	public Enumeration<String> getInitParameterNames() {
		return null;
	}

	/**
	 * 将具有给定 jsp 文件的 servlet 添加到此 servlet 上下文。
	 * <p>
	 * 注册的 servlet 可以通过返回的 ServletRegistration 对象进一步配置。
	 * <p>
	 * 如果此 ServletContext 已经包含具有给定 servletName 的 servlet 的初步 ServletRegistration，它将完成（通过将给定的 jspFile 分配给它）并返回。
	 * 
	 * @param servletName - servlet名称
	 * @param jspFile - 以“/”开头的 Web 应用程序中 JSP 文件的完整路径。
	 * @return 一个 ServletRegistration 对象，可用于进一步配置已注册的 servlet，如果此 ServletContext 已包含具有给定 servletName 的 servlet 的完整 ServletRegistration，则为 null。
	 */
	@Override
	public Dynamic addJspFile(String servletName, String jspFile) {
		return null;
	}

	/**
	 * <jsp-config> 相关配置，从由该 ServletContext 表示的 Web 应用程序的 web.xml 和 web-fragment.xml 描述符文件聚合，如果不存在此类配置，则为 null
	 */
	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		return null;
	}

	/**
	 * 此 ServletContext 表示的 Web 应用程序的类加载器
	 */
	@Override
	public ClassLoader getClassLoader() {
		return null;
	}

	/**
	 * 声明使用 isUserInRole 测试的角色名称。
	 * <p>
	 * 由于在 ServletRegistration 接口的 setServletSecurity 或 setRunAsRole 方法中使用而隐式声明的角色无需声明。
	 */
	@Override
	public void declareRoles(String... roleNames) {
	}

	/**
	 * @return 一个字符串，其中包含部署 servlet 上下文的逻辑主机的配置名称。
	 */
	@Override
	public String getVirtualServerName() {
		return null;
	}
    
	/**
     * 在分派期间执行路径映射时，用作线程本地存储的内部类。
     */
    private static final class DispatchData {
        public MessageBytes uriMB;
        public MappingData mappingData;

        public DispatchData() {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            mappingData = new MappingData();
        }
    }
}
