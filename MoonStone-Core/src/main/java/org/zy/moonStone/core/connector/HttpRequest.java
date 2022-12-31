package org.zy.moonStone.core.connector;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletResponse;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import javax.servlet.http.PushBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.exceptions.FileUploadException;
import org.zy.moonStone.core.exceptions.InvalidContentTypeException;
import org.zy.moonStone.core.exceptions.SizeException;
import org.zy.moonStone.core.filter.ApplicationFilterChain;
import org.zy.moonStone.core.http.Parameters;
import org.zy.moonStone.core.http.Parameters.FailReason;
import org.zy.moonStone.core.http.Request;
import org.zy.moonStone.core.http.async.AsyncContextImpl;
import org.zy.moonStone.core.http.fileupload.ApplicationPart;
import org.zy.moonStone.core.http.fileupload.DiskFileItemFactory;
import org.zy.moonStone.core.http.fileupload.ServletFileUpload;
import org.zy.moonStone.core.http.fileupload.ServletRequestContext;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Host;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.interfaces.http.CookieProcessor;
import org.zy.moonStone.core.interfaces.http.fileupload.FileItem;
import org.zy.moonStone.core.mapper.ApplicationMapping;
import org.zy.moonStone.core.mapper.MappingData;
import org.zy.moonStone.core.session.ApplicationSessionCookieConfig;
import org.zy.moonStone.core.session.interfaces.Manager;
import org.zy.moonStone.core.session.interfaces.Session;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.ParameterMap;
import org.zy.moonStone.core.util.RequestUtil;
import org.zy.moonStone.core.util.TLSUtil;
import org.zy.moonStone.core.util.buf.MessageBytes;
import org.zy.moonStone.core.util.http.ActionCode;
import org.zy.moonStone.core.util.http.FastHttpDateFormat;
import org.zy.moonStone.core.util.http.ServerCookie;
import org.zy.moonStone.core.util.http.ServerCookies;
import org.zy.moonStone.core.util.http.parser.AcceptLanguage;
import org.zy.moonStone.core.util.net.interfaces.SSLSupport;

/**
 * @dateTime 2022年6月16日;
 * @author zy(azurite-Y);
 * @description {@link org.zy.moonStone.core.http.Request } 的包装类
 */
public class HttpRequest implements HttpServletRequest {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);

    /** 关联的原初请求 */
    protected Request request;
    
    /** 与此请求关联的 cookie 集 */
    protected Cookie[] cookies = null;
    
    /** 默认语言环境 */
    protected static final Locale defaultLocale = Locale.getDefault();

    /** 与此请求关联的属性，以属性名称为键 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 指示是否已解析 SSL 属性以提高多次调用 {@link Request#getAttributeNames()} 的应用程序（通常是框架）的性能的标志  */
    protected boolean sslAttributesParsed = false;

    /** 与此请求关联的首选语言环境 */
    protected final ArrayList<Locale> locales = new ArrayList<>();

    /** 组件和事件侦听器与此请求相关的内部注释 */
    private final transient HashMap<String, Object> notes = new HashMap<>();
    
    /** Servlet 输入流，存储这请求正文的字节数据 */
    protected ServletByteInputStream inputStream = null;

    /** Reader */
    protected CharBufferReader reader = null;

    /** InputStream 流使用的标志 */
    protected boolean usingInputStream = false;

    /** Reader 流使用的标志 */
    protected boolean usingReader = false;
    
    /** 认证类型 */
    protected String authType = null;
    
    /** 安全标识 */
    protected boolean secure = false;
    
    /** 用户主体 */
    protected Principal userPrincipal = null;
    
    /** 当前调度程序类型 */
    protected DispatcherType internalDispatcherType = null;
    
    /** 请求参数解析标志 */
    protected boolean parametersParsed = false;

    /** Cookie 标头已解析标志。 表示cookie头已经解析成ServerCookies */
    protected boolean cookiesParsed = false;

    /** Cookie 解析标志。 表示 ServerCookies 已转换为面向用户的 Cookie 对象 */
    protected boolean cookiesConverted = false;
    
    /** getParameterMap 方法中使用的 HashMap */
    protected ParameterMap<String, String[]> parameterMap = new ParameterMap<>();
    
    /** 与此请求一起上载的部件（如果有） */
    protected Collection<Part> parts = null;
    
    /** 解析部件时抛出的异常（如果有） */
    protected Exception partsParseException = null;

    /** 此请求的当前活动会话 */
    protected Session session = null;

    /** 当前请求调度程序路径 */
    protected Object requestDispatcherPath = null;

    /** 请求的会话 ID 是否在 cookie 中收到？ */
    protected boolean requestedSessionCookie = false;

    /** 此请求的请求会话 ID（如果有） */
    protected String requestedSessionId = null;

    /** 请求的会话 ID 是否在 URL 中收到? */
    protected boolean requestedSessionURL = false;

    /** 请求的会话 ID 是从 SSL 会话中获得的吗? */
    protected boolean requestedSessionSSL = false;

    /** Locale 解析标识 */
    protected boolean localesParsed = false;

    /** 本地端口 */
    protected int localPort = -1;

    /** 远程地址 */
    protected String remoteAddr = null;

    /** 远程主机 */
    protected String remoteHost = null;

    /** 远程端口 */
    protected int remotePort = -1;

    /** 本地地址 */
    protected String localAddr = null;

    /** 本地地址名 */
    protected String localName = null;

    /** AsyncContext */
    private volatile AsyncContextImpl asyncContext = null;

    protected Boolean asyncSupported = null;

    private HttpServletRequest applicationRequest = null;
    
    /** 关联的连接器 */
    protected final Connector connector;
    
    /** 与请求关联的过滤器链 */
    protected FilterChain filterChain = null;
    
    /** 映射数据 */
    protected final MappingData mappingData = new MappingData();
    
    private final ApplicationMapping applicationMapping = new ApplicationMapping(mappingData);

    /** 与此请求关联的外观对象 */
    protected RequestFacade facade = null;
    
    /** 与此请求关联的响应 */
    protected HttpResponse httpResponse = null;
    
    /** post缓存尺寸 */
    protected static final int CACHED_POST_LEN = 8192;

	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    public HttpRequest(Connector connector) {
    	this.connector = connector;
	}
    
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = Boolean.valueOf(asyncSupported);
    }

    /**
     * 释放所有对象引用，并初始化实例变量，以准备重用该对象。
     */
    public void recycle() {
        internalDispatcherType = null;
        requestDispatcherPath = null;

        usingInputStream = false;
        usingReader = false;
        parametersParsed = false;
        if (parts != null) {
            for (Part part: parts) {
                try {
                    part.delete();
                } catch (IOException ignored) {
                    // ApplicationPart.delete() 从不抛出 IOEx
                }
            }
            parts = null;
        }
        partsParseException = null;
        locales.clear();
        localesParsed = false;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;

        attributes.clear();
        sslAttributesParsed = false;
        notes.clear();

        recycleSessionInfo();
        recycleCookieInfo(false);

        if (Connector.RECYCLE_FACADES) {
            parameterMap = new ParameterMap<>();
        } else {
            parameterMap.setLocked(false);
            parameterMap.clear();
        }

        mappingData.recycle();
        applicationMapping.recycle();

        applicationRequest = null;
        if (Globals.IS_SECURITY_ENABLED || Connector.RECYCLE_FACADES) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
        }

        asyncSupported = null;
        if (asyncContext!=null) {
            asyncContext.recycle();
        }
        asyncContext = null;
    }
    
	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    protected void addPathParameter(String name, String value) {
    	request.addPathParameter(name, value);
    }

    protected String getPathParameter(String name) {
        return request.getPathParameter(name);
    }

    protected void recycleSessionInfo() {
        if (session != null) {
            try {
                session.endAccess();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                logger.warn("会话结束访问失败", t);
            }
        }
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;
        requestedSessionSSL = false;
    }

    protected void recycleCookieInfo(boolean recycleCoyote) {
        cookiesParsed = false;
        cookiesConverted = false;
        cookies = null;
        if (recycleCoyote) {
            getRequest().getCookies().recycle();
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 请求方法
	// -------------------------------------------------------------------------------------
    /**
     * @return 接收此请求的连接器
     */
    public Connector getConnector() {
        return this.connector;
    }
    
    /**
     * 返回正在处理此请求的上下文。
     * <p>
     * 只要识别出适当的上下文，就可以使用它。请注意，上下文的可用性允许 <code> getContextPath() <code/> 返回一个值，因此可以解析请求 URI。
     *
     * @return 与请求映射的上下文
     */
    public Context getContext() {
        return mappingData.context;
    }
    
    /**
     * 获取与请求关联的过滤器链
     * @return 关联的过滤器链
     */
    public FilterChain getFilterChain() {
        return this.filterChain;
    }
    /**
     * 设置与请求关联的过滤器链
     *
     * @param filterChain - 新的过滤器链 
     */
    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }
    
    /**
     * @return 正在处理此请求的主机
     */
    public Host getHost() {
        return mappingData.host;
    }
    
    /**
     * @return mapping data.
     */
    public MappingData getMappingData() {
        return mappingData;
    }
    
    /**
     * 获取请求路径
     *
     * @return 请求路径
     */
    public MessageBytes getRequestPathMB() {
        return mappingData.requestPath;
    }
    
    /**
     * 设置原初请求对象
     *
     * @param coyoteRequest - 原初请求对象
     */
    public void setRequest(Request request) {
        this.request = request;
    }
    /**
     * 获得原初请求对象
     *
     * @return 原初请求对象
     */
    public Request getRequest() {
        return this.request;
    }
    
    /**
     * @return 与此请求关联的响应
     */
    public HttpResponse getHttpResponse() {
        return this.httpResponse;
    }
    /**
     * 设置与此请求关联的响应
     *
     * @param httpResponse - 新的关联响应
     */
    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    /**
     * @return <code>ServletRequest</code>的外观。此方法必须由子类实现。
     */
    public HttpServletRequest getHttpServletRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        }
        if (applicationRequest == null) {
            applicationRequest = facade;
        }
        return applicationRequest;
    }
    /**
     * 设置一个封装的 HttpServletRequest 以传递给应用程序。 想要包装请求的组件应该通过 {@link #getRequest()} 获取请求，包装它，然后使用包装的请求调用此方法。
     *
     * @param applicationRequest - 要传递给应用程序的包装请求
     */
    public void setHttpServletRequest(HttpServletRequest applicationRequest) {
        // 检查包装器是否包装了这个请求
        ServletRequest r = applicationRequest;
        while (r instanceof HttpServletRequestWrapper) {
            r = ((HttpServletRequestWrapper) r).getRequest();
        }
        if (r != facade) {
            throw new IllegalArgumentException("request.illegalWrap");
        }
        this.applicationRequest = applicationRequest;
    }
    
    /**
     * @return 处理此请求的包装器
     */
    public Wrapper getWrapper() {
        return mappingData.wrapper;
    }
    
    
	// -------------------------------------------------------------------------------------
	// 公共请求方法
	// -------------------------------------------------------------------------------------
    /**
     * @param name - 要返回的 note 的名称
     * @return 将指定名称绑定到此请求的内部notes的对象，如果不存在这样的绑定，则为null。
     */
    public Object getNote(String name) {
        return notes.get(name);
    }
    /**
     * 在与此请求相关联的内部说明中将对象绑定到指定的名称，替换该名称的任何现有绑定。
     *
     * @param name - 对象应该绑定到的名称
     * @param value - 应该绑定到指定名称的对象
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }
    /**
     * 移除此请求的内部 notes 中绑定到指定名称的任何对象
     *
     * @param name - 要删除的注释的名称
     */
    public void removeNote(String name) {
        notes.remove(name);
    }
    
    /**
     * 设置处理此请求的服务器的端口号
     *
     * @param port - 服务器的端口号
     */
    public void setLocalPort(int port) {
        localPort = port;
    }
    /**
	 * 返回接收请求的接口的 Internet 协议 (IP) 端口号。
	 * 
	 * @return 指定端口号的整数
	 */
	@Override
	public int getLocalPort() {
		if (localPort == -1){
			request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
	        localPort = request.getLocalPort();
	    }
	    return localPort;
	}

	/**
     * 设置与此请求关联的远程客户端的IP地址
     *
     * @param remoteAddr - 远端IP地址
     */
    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }
    /**
	 * 返回发送请求的客户端或最后一个代理的 Internet 协议 (IP) 地址。对于 HTTP servlet，与 CGI 变量 REMOTE_ADDR 的值相同。
	 * 
	 * @return 一个字符串，包含发送请求的客户端的 IP 地址
	 */
	@Override
	public String getRemoteAddr() {
		if (remoteAddr == null) {
			request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
			remoteAddr = request.remoteAddr().toString();
		}
		return remoteAddr;
	}

	/**
     * 设置与此请求关联的远程客户端的完全限定名称
     *
     * @param remoteHost - 远程主机名
     */
    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }
    /**
	 * 返回发送请求的客户端或最后一个代理的完全限定名称。如果引擎不能或选择不解析主机名（以提高性能），则此方法返回 IP 地址的点分字符串形式。 对于 HTTP servlet，与 CGI 变量 REMOTE_HOST 的值相同。
	 * 
	 * @return 包含客户端完全限定名称的字符串
	 */
	@Override
	public String getRemoteHost() {
		if (remoteHost == null) {
	        if (!connector.getEnableLookups()) {
	            remoteHost = getRemoteAddr();
	        } else {
	            request.action(ActionCode.REQ_HOST_ATTRIBUTE, request);
	            remoteHost = request.remoteHost().toString();
	        }
	    }
	    return remoteHost;
	}

	/**
     * 设置处理此请求的服务器的端口号
     *
     * @param port - 服务器端口
     */
    public void setServerPort(int port) {
        request.setServerPort(port);
    }
    /**
	 * 返回请求发送到的端口号。它是 Host请求头值中“:”之后部分的值，如果有的话，或者是接受客户端连接的服务器端口。
	 */
	@Override
	public int getServerPort() {
	    return request.getServerPort();
	}

	/**
     * 获取已解码的请求URI
     *
     * @return URL已解码的请求URI
     */
    public String getDecodedRequestURI() {
        return request.decodedURI().toString();
    }
    
    
	// -------------------------------------------------------------------------------------
	// ServletRequest 方法
	// -------------------------------------------------------------------------------------
	/**
	 * 将命名属性的值作为对象返回，如果不存在给定名称的属性，则返回 null。
	 * <p>
	 * 可以通过两种方式设置属性。 servlet 容器可以设置属性以提供有关请求的自定义信息。
	 * 例如，对于使用 HTTPS 发出的请求，属性 javax.servlet.request.X509Certificate 可用于检索有关客户端证书的信息。 
	 * 也可以使用 ServletRequest.setAttribute 以编程方式设置属性。 这允许在 RequestDispatcher 调用之前将信息嵌入到请求中。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 本规范保留与 java.*、javax.* 和 sun.* 匹配的名称。
	 * @param name - 指定属性名称的字符串
	 * @return 包含属性值的对象，如果属性不存在，则返回 null
	 */
    @Override
	public Object getAttribute(String name) {
    	 // 特殊属性
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            return adapter.get(this, name);
        }

        Object attr = attributes.get(name);

        if (attr != null) {
            return attr;
        }

        attr = request.getAttribute(name);
        if (attr != null) {
            return attr;
        }
        if (TLSUtil.isTLSRequestAttribute(name)) {
            request.action(ActionCode.REQ_SSL_ATTRIBUTE, request);
            attr = request.getAttribute(Globals.CERTIFICATES_ATTR);
            if (attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = request.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if (attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = request.getAttribute(Globals.KEY_SIZE_ATTR);
            if (attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = request.getAttribute(Globals.SSL_SESSION_ID_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_ID_ATTR, attr);
            }
            attr = request.getAttribute(Globals.SSL_SESSION_MGR_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_MGR_ATTR, attr);
            }
            attr = request.getAttribute(SSLSupport.PROTOCOL_VERSION_KEY);
            if (attr != null) {
                attributes.put(SSLSupport.PROTOCOL_VERSION_KEY, attr);
            }
            attr = attributes.get(name);
            sslAttributesParsed = true;
        }
        return attr;
	}

    /**
     * 返回一个枚举，其中包含此请求可用的属性的名称。如果请求没有可用的属性，则此方法返回一个空枚举。
     * @return 包含请求属性名称的字符串枚举
     */
	@Override
	public Enumeration<String> getAttributeNames() {
        if (isSecure() && !sslAttributesParsed) {
            getAttribute(Globals.CERTIFICATES_ATTR);
        }
        // 如果用于删除属性，需复制以防止 ConcurrentModificationExceptions
        Set<String> names = new HashSet<>();
        names.addAll(attributes.keySet());
        return Collections.enumeration(names);
	}

	/**
	 * 在此请求中存储一个属性。在请求之间重置属性。 此方法最常与 RequestDispatcher 结合使用。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 以 java.*、javax.* 和 com.sun.* 开头的名称保留供 Sun Micro 系统使用。如果传入的对象为null，效果和调用removeAttribute一样。
	 * 
	 * @apiNote 警告当请求从 servlet 分派时，RequestDispatcher 驻留在不同的 Web 应用程序中，此方法设置的对象可能无法在调用方 servlet 中正确检索。
	 * @param name - 一个字符串，指定属性的名称
	 * @param o - 要存储的对象
	 */
	@Override
	public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("属性名不能为 null");
        }

        // 空值与removeAttribute()相同
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // 特殊属性
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            adapter.set(this, name, value);
            return;
        }

        //添加或替换指定的属性, 在进行任何更新之前进行安全检查
        if (Globals.IS_SECURITY_ENABLED && name.equals(Globals.SENDFILE_FILENAME_ATTR)) {
            // 使用规范文件名以避免任何可能的符号链接和相对路径问题
            String canonicalPath;
            try {
                canonicalPath = new File(value.toString()).getCanonicalPath();
            } catch (IOException e) {
                throw new SecurityException("获取规范路径名失败 by value: " + value, e);
            }
            // 发送文件是在安全上下文中执行的，所以需要检查web应用程序是否被允许在web应用程序的安全上下文中访问该文件
            System.getSecurityManager().checkRead(canonicalPath);
            // 更新该值，以便使用规范路径
            value = canonicalPath;
        }

        Object oldValue = attributes.put(name, value);

        // 将特殊属性传递给本地层
        if (name.startsWith("org.zy.moonStone.")) {
            request.setAttribute(name, value);
        }

        // 通知感兴趣的应用程序事件侦听器
        notifyAttributeAssigned(name, value, oldValue);		
	}

	/**
	 * 从此请求中删除一个属性。 通常不需要此方法，因为属性仅在处理请求时才持续存在。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 以 java.*、javax.* 和 com.sun.* 开头的名称保留供 Sun Micro 系统使用。
	 * 
	 * @param name - 一个字符串，指定要删除的属性的名称
	 */
	@Override
	public void removeAttribute(String name) {
		// 删除指定的属性并将特殊属性传递给本地层
        if (name.startsWith("org.apache.tomcat.")) {
            request.getAttributes().remove(name);
        }

        boolean found = attributes.containsKey(name);
        if (found) {
            Object value = attributes.get(name);
            attributes.remove(name);

            // 通知感兴趣的应用程序事件侦听器
            notifyAttributeRemoved(name, value);
        }		
	}
	
	/**
	 * 返回此请求正文中使用的字符编码的名称。 如果未指定请求编码字符编码，则此方法返回 null。 
	 * 以下方法用于指定请求字符编码，按优先级降序排列：每个请求、每个 Web 应用程序（使用 ServletContext.setRequestCharacterEncoding、deploymentdescriptor）
	 * 和每个容器（对于部署在该容器中的所有 Web 应用程序，使用提供的特定配置）。
	 * @return 包含字符编码名称的字符串，如果请求未指定字符编码，则返回 null
	 */
	@Override
	public String getCharacterEncoding() {
		String characterEncoding = request.getCharacterEncoding();
        if (characterEncoding != null) {
            return characterEncoding;
        }

        Context context = getContext();
        if (context != null) {
            return context.getRequestCharacterEncoding();
        }

        return null;
	}

	/**
	 * 覆盖此请求正文中使用的字符编码的名称。 此方法必须在读取请求参数或使用 getReader() 读取输入之前调用。 否则，它没有效果。
	 * @param env - 包含字符编码名称的字符串
	 * @throws UnsupportedEncodingException - 如果这个ServletRequest仍然处于可以设置字符编码的状态，但是指定的编码无效
	 */
	@Override
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
		if (usingReader) {
            return;
        }

        // 保存经过验证的编码
        request.setCharset(Charset.forName(env));
	}

	/**
	 * 返回请求正文的长度（以字节为单位）并由输入流提供，如果长度未知或大于 Integer.MAX_VALUE则返回 -1 。 对于 HTTP servlet，与 CGI 变量 CONTENT_LENGTH 的值相同。
	 * @return 一个包含请求正文长度的整数，如果长度未知或大于  {@code Integer.MAX_VALUE } ，则为 -1。
	 */
	@Override
	public int getContentLength() {
        return (int) getContentLengthLong();
	}

	/**
	 * 返回请求正文的长度（以字节为单位）并由输入流提供，如果长度未知，则返回 -1。 对于 HTTP servlet，与 CGI 变量 CONTENT_LENGTH 的值相同。
	 * 
	 * @return 包含请求正文长度的 long 或 -1L 如果长度未知
	 */
	@Override
	public long getContentLengthLong() {
		return request.getContentLengthLong();
	}

	/**
	 * 返回请求正文的 MIME 类型，如果类型未知，则返回 null。 对于 HTTP servlet，与 CGI 变量 CONTENT_TYPE 的值相同。
	 * 
	 * @return 包含请求的 MIME 类型名称的字符串，如果类型未知，则返回 null
	 */
	@Override
	public String getContentType() {
		return request.getContentType();
	}
    /**
     * 设置此请求的内容类型
     *
     * @param contentType - 内容类型
     */
    public void setContentType(String contentType) {
    	request.setContentType(contentType);
    }
    
	/**
	 * 使用 ServletInputStream 将请求的主体作为二进制数据检索。 可以调用此方法或 getReader 来读取正文，但不能同时调用两者。
	 * 
	 * @return 包含请求正文的 ServletInputStream 对象
	 * @throws IOException - 如果发生输入或输出异常
	 * @throws IllegalStateException - 如果已经为此请求调用 <code>getReader()</code>
	 */
	@Override
	public ServletByteInputStream getInputStream() throws IOException {
		if (usingReader)  throw new IllegalStateException("已获取字符流(BufferedReader) 以读取请求正文数据，不能再获取字节流(ServletInputStream)");
		
		if (this.inputStream == null) {
			this.usingInputStream = true;
			this.inputStream = new ServletByteInputStream(this.request);
		}
		
		return inputStream;
	}

	/**
	 * 使用 BufferedReader 将请求的主体作为字符数据检索。 阅读器根据正文使用的字符编码转换字符数据。可以调用此方法或 getInputStream 来读取正文，不能同时调用两者。
	 * 
	 * @return 包含请求正文的 BufferedReader
	 * @apiNote 建议使用 {@linkplain #getInputStream() } 的实现 {@link ServletByteInputStream } 流读取大数据字节
	 * @throws IOException - 如果发生输入或输出异常
	 * @throws IllegalStateException - 如果已经为此请求调用 <code>getInputStream()</code>
	 */
	@Override
	public CharBufferReader getReader() throws IOException {
		if (usingInputStream)  throw new IllegalStateException("已获取字节流(ServletInputStream) 以读取请求正文数据，不能再获取字符流(BufferedReader)");
		
        if (request.getCharacterEncoding() == null) {
            // 当前没有明确设置。 检查上下文
            Context context = getContext();
            if (context != null) {
                String enc = context.getRequestCharacterEncoding();
                if (enc != null) {
                    setCharacterEncoding(enc);
                }
            }
        }

        if (reader == null) {
        	int contentLength = this.request.getContentLength();
        	ByteBuffer byteBuffer = ByteBuffer.allocate(contentLength);
        	
        	Supplier<Byte> requestBodySupplier = this.request.getRequestBodySupplier();
			for (int j = 0; j < contentLength; j++) {
				byteBuffer.put( requestBodySupplier.get() );
			}
			
			byteBuffer.flip(); // 切换为写模式
            reader = new CharBufferReader(request.getCharset().decode(byteBuffer), request.getContentLength());
            usingReader = true;
        }
        return reader;
	}
	
	/**
	 * 将请求参数的值作为字符串返回，如果参数不存在，则返回 null。 请求参数是随请求发送的额外信息。 对于 HTTP servlet，参数包含在查询字符串或发布的表单数据中。
	 * <p>
	 * 仅当您确定参数只有一个值时，才应使用此方法。 如果参数可能有多个值，请使用 getParameterValues。
	 * <p>
	 * 如果将此方法与多值参数一起使用，则返回的值等于 getParameterValues 返回的数组中的第一个值。
	 * <p>
	 * 如果参数数据是在请求体中发送的，例如发生在 HTTP POST 请求中，那么直接通过 getInputStream 或 getReader 读取请求体会干扰该方法的执行。
	 * 
	 * @param name - 指定参数名称的字符串
	 * @return 一个字符串，表示参数的单个值
	 */
	@Override
	public String getParameter(String name) {
		if (!parametersParsed) {
            parseParameters();
        }

        return request.getParameters().getParameter(name);
	}

	/**
	 * 返回包含此请求中包含的参数名称的字符串对象的枚举。 如果请求没有参数，则该方法返回一个空 Enumeration。
	 * 
	 * @return 一个字符串对象的枚举，每个字符串包含一个请求参数的名称； 如果请求没有参数，则为空 Enumeration
	 */
	@Override
	public Enumeration<String> getParameterNames() {
		if (!parametersParsed) {
            parseParameters();
        }

        return request.getParameters().getParameterNames();
	}

	/**
	 * 返回包含给定请求参数具有的所有值的 String 对象数组，如果参数不存在，则返回 null。
	 * <p>
	 * 如果参数只有一个值，则数组的长度为 1。
	 * 
	 * @return 包含请求其值的参数名称的字符串
	 */
	@Override
	public String[] getParameterValues(String name) {
		if (!parametersParsed) {
            parseParameters();
        }

        return request.getParameters().getParameterValues(name);
	}

	/**
	 * 返回此请求参数的 java.util.Map。
	 * <p>
	 * 请求参数是与请求一起发送的额外信息。对于 HTTP servlet，参数包含在查询字符串或发布的表单数据中。
	 * 
	 * @return 一个不可变的 java.util.Map，包含参数名称作为键和参数值作为映射值。 参数映射中的键是字符串类型。 参数映射中的值是 String 数组 类型。
	 */
	@Override
	public Map<String, String[]> getParameterMap() {
		if (parameterMap.isLocked()) {
            return parameterMap;
        }

        Enumeration<String> enumeration = getParameterNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;
	}

	/**
	 * 返回请求使用的协议的名称和版本，格式为 protocol/majorVersion.minorVersion，例如 HTTP/1.1。 
	 * 对于 HTTP servlet，返回的值与 CGI 变量 SERVER_PROTOCOL 的值相同。
	 * 
	 * @return 包含协议名称和版本号的字符串
	 */
	@Override
	public String getProtocol() {
        return request.protocol().toString();
	}

	/**
	 * 返回用于发出此请求的方案的名称，例如 http、https 或 ftp。不同的方案具有不同的 URL 构造规则，如 RFC 1738 中所述。
	 * 
	 * @return 包含用于发出此请求的方案名称的字符串
	 */
	@Override
	public String getScheme() {
        return request.scheme().toString();
	}

	/**
	 * 返回发出此请求的 HTTP 方法的名称，例如 GET、POST 或 PUT。与 CGI 变量 REQUEST_METHOD 的值相同。
	 * 
	 * @return 一个字符串，指定发出此请求的方法的名称
	 */
	@Override
	public String getMethod() {
		return request.method().toString();
	}

	/**
	 * 返回路径后面的 requestURL 中包含的查询字符串。 如果 URL 没有查询字符串，则此方法返回 null。 与 CGI 变量 QUERY_STRING 的值相同。
	 * 
	 * @return 一个包含查询字符串的字符串，如果 URL 不包含查询字符串，则返回 null。 该值未被容器解码。
	 */
	@Override
	public String getQueryString() {
	    return request.queryString().toString();
	}

	/**
	 * 返回请求发送到的服务器的主机名。它是 Host 请求头值中“：”之前部分的值（如果有），或者解析的服务器名称，或者服务器 IP 地址。
	 * 
	 * @return 包含服务器名称的字符串
	 */
	@Override
	public String getServerName() {
        return request.serverName().toString();
	}

	/**
	 * 返回发送请求的客户端或最后一个代理的 Internet 协议 (IP) 源端口。
	 * 
	 * @return 指定端口号的整数
	 */
	@Override
	public int getRemotePort() {
		if (remotePort == -1) {
            request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
            remotePort = request.getRemotePort();
        }
        return remotePort;
	}

	/**
	 * 根据 Accept-Language 标头返回客户端将接受内容的首选区域设置。如果客户端请求未提供 Accept-Language 标头，则此方法返回服务器的默认区域设置。
	 * 
	 * @return 客户端的首选语言环境
	 */
	@Override
	public Locale getLocale() {
		parseLocales();

        if (locales.size() > 0) {
            return locales.get(0);
        }

        return defaultLocale;
	}

	/**
	 * 返回一个区域设置对象的枚举，从首选区域设置开始按降序表示客户端可以接受的基于 Accept-Language 标头的区域设置。
	 * 如果客户端请求未提供 Accept-Language 标头，则此方法返回一个包含 一种语言环境，服务器的默认语言环境。
	 * 
	 * @return 客户端首选区域设置对象的枚举
	 */
	@Override
	public Enumeration<Locale> getLocales() {
		parseLocales();

        if (locales.size() > 0) {
            return Collections.enumeration(locales);
        }
        ArrayList<Locale> results = new ArrayList<>();
        results.add(defaultLocale);
        return Collections.enumeration(results);
	}

	/**
	 * 返回接收请求的 Internet 协议 (IP) 接口的主机名。
	 * 
	 * @return 一个字符串，其中包含接收请求的 IP 的主机名。
	 */
	@Override
	public String getLocalName() {
		if (localName == null) {
            request.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, request);
            localName = request.localName().toString();
        }
        return localName;
	}

	/**
	 * 返回接收请求的接口的 Internet 协议 (IP) 地址。
	 * 
	 * @return 包含接收请求的 IP 地址的字符串。
	 */
	@Override
	public String getLocalAddr() {
		if (localAddr == null) {
            request.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, request);
            localAddr = request.localAddr().toString();
        }
        return localAddr;
	}

	/**
     * 指示此请求的请求会话 ID 是否通过 cookie 传递。 这通常由 HTTP 连接器在解析请求标头时调用。
     */
    public void setRequestedSessionCookie(boolean flag) {
        this.requestedSessionCookie = flag;
    }

    /**
     * 为此请求设置请求的会话 ID。 这通常由 HTTP 连接器在解析请求标头时调用。
     *
     * @param id - 新的会话ID
     */
    public void setRequestedSessionId(String id) {
        this.requestedSessionId = id;
    }


    /**
     * 指示此请求的请求会话 ID 是否通过 URL 传递。 这通常由 HTTP 连接器在解析请求标头时调用。
     */
    public void setRequestedSessionURL(boolean flag) {
        this.requestedSessionURL = flag;
    }

    /**
     * 指示此请求的请求会话 ID 是否通过 SSL 传递。 这通常由 HTTP 连接器在解析请求标头时调用。
     *
     * @param flag The new flag
     */
    public void setRequestedSessionSSL(boolean flag) {
        this.requestedSessionSSL = flag;
    }
	
	/**
	 * 返回一个布尔值，指示此请求是否使用安全通道（例如 HTTPS）发出。
	 * 
	 * @return 一个布尔值，指示是否使用安全通道发出请求
	 */
	@Override
	public boolean isSecure() {
		return secure;
	}

    /**
     * 设置 isSecure() 为该请求返回的值
     *
     * @param secure 新的 secure 值
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }
	
	/**
	 * 返回一个 RequestDispatcher 对象，该对象充当位于给定路径的资源的包装器。RequestDispatcher 对象可用于将请求转发到资源或将资源包含在响应中。资源可以是动态的或静态的。
	 * <p>
	 * 指定的路径名可以是相对的，尽管它不能扩展到当前 servlet 上下文之外。 如果路径以“/”开头，则将其解释为相对于当前上下文根。如果 servlet 容器无法返回 RequestDispatcher，则此方法返回 null。
	 * <p>
	 * 该方法与 ServletContext.getRequestDispatcher 的区别在于该方法可以走相对路径。
	 * 
	 * @param path - 一个字符串，指定资源的路径名。 如果它是相对的，它必须与当前的 servlet 相对。
	 * @return 一个 RequestDispatcher 对象，它充当指定路径上资源的包装器，如果 servlet 容器无法返回 RequestDispatcher，则为 null
	 */
	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		Context context = getContext();
        if (context == null) {
            return null;
        }

        if (path == null) {
            return null;
        }

        int fragmentPos = path.indexOf('#');
        if (fragmentPos > -1) {
            logger.warn("request.fragmentInDispatchPath", path);
            path = path.substring(0, fragmentPos);
        }

        // 如果路径已经是上下文相关的，就直接传递它
        if (path.startsWith("/")) {
            return context.getServletContext().getRequestDispatcher(path);
        }

        /*
         * From the Servlet 4.0 Javadoc:
         * - 指定的路径名可以是相对的，尽管它不能扩展到当前servlet上下文之外.
         * - 如果它是相对的，它必须是相对于当前的servlet
         *
         * 规范第9.1节:
         * - servlet容器使用请求对象中的信息将给定的相对路径与当前servlet转换为完整路径.
         *
         * 使用requestURI还是使用servletPath和pathInfo是未定义的。
         * 假设RequestURI包含contextPath(并且提取它很麻烦)，使用servletPath和pathInfo看起来是更合理的选择。
         */

        // 将请求相对路径转换为上下文相对路径
        String servletPath = (String) getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null) {
            servletPath = getServletPath();
        }

        // 添加路径信息，如果有的话
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        // 若path不以 "/" 开头则相对于当前servlet映射，在此截取最后的 "/" 找到当前servlet的父级
        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (context.getDispatchersUseEncodedPaths()) {
            	try {
            		if (pos >= 0) {
            			relative = URLEncoder.encode(requestPath.substring(0, pos + 1), "utf-8");
            		} else {
            			relative = URLEncoder.encode(requestPath, "utf-8") + path;
            		}
				} catch (UnsupportedEncodingException e) {} // nave, 忽略这个异常
        } else {
            if (pos >= 0) {
                relative = requestPath.substring(0, pos + 1) + path;
            } else {
                relative = requestPath + path;
            }
        }
        return context.getServletContext().getRequestDispatcher(relative);
	}

	/**
	 * @param path - 要返回真实路径的路径。
	 * @return 真实路径，如果无法执行翻译，则返回 null
	 * @deprecated 从 Java Servlet API 2.1 版开始，使用 ServletContext.getRealPath()。
	 */
	@Override
	@Deprecated
	public String getRealPath(String path) {
		return null;
	}

	/**
	 * 获取此 ServletRequest 上次分派到的 servlet 上下文。
	 * 
	 * @return 此 ServletRequest 上次分派到的 servlet 上下文
	 */
	@Override
	public ServletContext getServletContext() {
		return getContext().getServletContext();
	}

	/**
	 * 将此请求置于异步模式，并使用原始（未包装的）ServletRequest 和 ServletResponse 对象初始化其 AsyncContext。
	 * <p>
	 * 调用此方法将导致关联响应的提交延迟，直到在返回的 AsyncContext 上调用 AsyncContext.complete，或者异步操作已超时。
	 * <p>
	 * 在返回的 AsyncContext 上调用 AsyncContext.hasOriginalRequestAndResponse() 将返回 true。在此请求进入异步模式后在出站方向上调用的任何过滤器都可以将此用作指示它们在入站调用期间添加的任何请求和/或响应包装器在异步操作期间不需要停留，因此它们的任何相关资源可能会被释放。
	 * <p>
	 * 在调用每个 AsyncListener 的 onStartAsync 方法后，此方法清除使用先前调用其中一个 startAsync 方法返回的 AsyncContext 注册的 AsyncListener 实例列表（如果有）。
	 * <p>
	 * 此方法或其重载变量的后续调用将返回相同的 AsyncContext 实例，并根据需要重新初始化。
	 * 
	 * @return （重新）初始化的 AsyncContext
	 * @throws IllegalStateException - 如果此请求在不支持异步操作的过滤器或 servlet 的范围内（即 isAsyncSupported 返回 false），
	 * 或者如果在没有任何异步调度的情况下再次调用此方法（由 AsyncContext.dispatch 方法之一产生），
	 * 则为 在任何此类调度的范围之外调用，或者在同一调度的范围内再次调用，或者如果响应已经关闭
	 */
	@Override
	public AsyncContext startAsync() throws IllegalStateException {
        return startAsync(getHttpServletRequest(), httpResponse.getHttpServletResponse());
	}

	/**
	 * 将此请求置于异步模式，并使用给定的请求和响应对象初始化其 AsyncContext。
	 * <p>
	 * ServletRequest 和 ServletResponse 参数必须是相同的实例，或者包装它们的 ServletRequestWrapper 和 ServletResponseWrapper 的实例，
	 * 它们分别被传递给 Servlet 的 service 方法或 Filter 的 doFilter 方法，在其范围内调用此方法。
	 * <p>
	 * 调用此方法将导致关联响应的提交延迟，直到在返回的 AsyncContext 上调用 AsyncContext.complete，或者异步操作已超时。
	 * <p>
	 * 在返回的 AsyncContext 上调用 AsyncContext.hasOriginalRequestAndResponse() 将返回 false，
	 * 除非传入的 ServletRequest 和 ServletResponse 参数是原始参数或不携带任何应用程序提供的包装器。在此请求进入异步模式后，
	 * 在出站方向调用的任何过滤器都可以使用这表明他们在入站调用期间添加的一些请求和/或响应包装器可能需要在异步操作期间保持原位，并且可能不会释放它们相关联的资源。
	 * 在过滤器的入站调用期间应用的 ServletRequestWrapper 可能仅当用于初始化 AsyncContext 并将通过调用 AsyncContext.getRequest() 返回的给定 servletRequest 不包含所述 ServletRequestWrapper 时，
	 * 才通过过滤器的出站调用释放。 ServletResponseWrapper 实例也是如此。
	 * <p>
	 * 在调用每个 AsyncListener 的 onStartAsync 方法后，此方法清除使用先前调用其中一个 startAsync 方法返回的 AsyncContext 注册的 AsyncListener 实例列表（如果有）。
	 * <p>
	 * 此方法或其零参数变量的后续调用将返回相同的 AsyncContext 实例，并根据需要重新初始化。如果调用此方法之后调用其零参数变体，
	 * 则指定的（并且可能包装的）请求和响应对象将保持锁定在返回的 AsyncContext 中。
	 * @param servletRequest - 用于初始化 AsyncContext 的 ServletRequest
	 * @param servletResponse - 用于初始化 AsyncContext 的 ServletResponse
	 * 
	 * @throws IllegalStateException - 如果此请求在不支持异步操作的过滤器或 servlet 的范围内（即 isAsyncSupported 返回 false），
	 * 或者如果在没有任何异步调度的情况下再次调用此方法（由 AsyncContext.dispatch 方法之一产生），则为 在任何此类调度的范围之外调用，或者在同一调度的范围内再次调用，或者如果响应已经关闭
	 */
	@Override
	public AsyncContext startAsync(ServletRequest httpServletRequest, ServletResponse httpServletResponse) throws IllegalStateException {
		if (!isAsyncSupported()) {
			String format = String.format("不支持异步, ", getNonAsyncClassNames());
            IllegalStateException ise = new IllegalStateException(format);
            logger.warn(format, ise);
            throw ise;
        }

        if (asyncContext == null) {
            asyncContext = new AsyncContextImpl(this);
        }

        asyncContext.setStarted(getContext(), httpServletRequest, httpServletResponse,
        		httpServletRequest==getHttpServletRequest() && httpServletResponse==getHttpResponse().getHttpServletResponse());
        asyncContext.setTimeout(getConnector().getAsyncTimeout());

        return asyncContext;
	}

	private Set<String> getNonAsyncClassNames() {
        Set<String> result = new HashSet<>();

        Wrapper wrapper = getWrapper();
        if (!wrapper.isAsyncSupported()) {
            result.add(wrapper.getServletClass());
        }

        FilterChain filterChain = getFilterChain();
        if (filterChain instanceof ApplicationFilterChain) {
            ((ApplicationFilterChain) filterChain).findNonAsyncFilters(result);
        } else {
            result.add("过滤器异步支持未知");
        }

        Container c = wrapper;
        while (c != null) {
            c.getPipeline().findNonAsyncValves(result);
            c = c.getParent();
        }

        return result;
    }
	
	/**
	 * 检查此请求是否已进入异步模式。
	 * <p>
	 * 通过在其上调用 startAsync 或 startAsync(ServletRequest, ServletResponse) 将 ServletRequest 置于异步模式。
	 * <p>
	 * 如果此请求被置于异步模式，则此方法返回 false，但此后已使用 AsyncContext.dispatch 方法之一调度或通过调用 AsyncContext.complete 从异步模式释放。
	 * 
	 * @return 如果此请求已进入异步模式，则为 true，否则为 false
	 */
	@Override
	public boolean isAsyncStarted() {
		if (asyncContext == null) {
            return false;
        }

        return asyncContext.isStarted();
	}

	public boolean isAsyncDispatching() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        request.action(ActionCode.ASYNC_IS_DISPATCHING, result);
        return result.get();
    }

    public boolean isAsyncCompleting() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        request.action(ActionCode.ASYNC_IS_COMPLETING, result);
        return result.get();
    }

    public boolean isAsync() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        request.action(ActionCode.ASYNC_IS_ASYNC, result);
        return result.get();
    }
	
	/**
	 * 检查此请求是否支持异步操作。
	 * <p>
	 * 如果此请求在部署描述符中未注释或标记为能够支持异步处理的过滤器或 servlet 的范围内，则此请求的异步操作被禁用。
	 * 
	 * @return 如果此请求支持异步操作，则为 true，否则为 false
	 */
	@Override
	public boolean isAsyncSupported() {
		if (this.asyncSupported == null) {
            return true;
        }

        return asyncSupported.booleanValue();
	}

	/**
	 * 获取由最近调用此请求的 startAsync 或 startAsync(ServletRequest, ServletResponse) 创建或重新初始化的 AsyncContext。
	 * 
	 * @return 在此请求上最近调用 startAsync 或 startAsync(ServletRequest, ServletResponse) 创建或重新初始化的 AsyncContext
	 */
	@Override
	public AsyncContext getAsyncContext() {
		if (!isAsyncStarted()) {
			throw new IllegalStateException("request 不支持异步");
		}
		return asyncContext;
	}

	public AsyncContextImpl getAsyncContextInternal() {
        return asyncContext;
    }
	
	/**
	 * 获取此请求的调度程序类型。
	 * <p>
	 * 容器使用请求的调度器类型来选择需要应用于请求的过滤器：只有匹配调度器类型和 url 模式的过滤器才会被应用。
	 * <p>
	 * 允许为多个调度程序类型配置的过滤器查询其调度程序类型的请求允许过滤器根据其调度程序类型以不同方式处理请求。
	 * <p>
	 * 请求的初始调度程序类型定义为 DispatcherType.REQUEST。
	 * 通过 RequestDispatcher.forward(ServletRequest, ServletResponse) 或 RequestDispatcher.include(ServletRequest, ServletResponse) 调度的请求的
	 * 调度程序类型分别以 ​​DispatcherType.FORWARD 或 DispatcherType.INCLUDE 给出，而异步请求的调度程序类型通过 AsyncContext 调度。调度方法以 DispatcherType.ASYNC 形式给出。
	 * 最后，由容器的错误处理机制分派到错误页面的请求的分派器类型为 DispatcherType.ERROR。
	 */
	@Override
	public DispatcherType getDispatcherType() {
		if (internalDispatcherType == null) {
            return DispatcherType.REQUEST;
        }

        return this.internalDispatcherType;
	}

	/**
	 * 返回一个数组，其中包含客户端随此请求发送的所有 Cookie 对象。如果未发送任何 cookie，则此方法返回 null。
	 * 
	 * @return 此请求中包含的所有 Cookie 的数组，如果请求没有 cookie，则为 null
	 */
	@Override
	public Cookie[] getCookies() {
		if (!cookiesConverted) {
            convertCookies();
        }
        return cookies;
	}

	/**
	 * 返回指定请求头的值，该值为表示Date对象的长值。对于包含日期的标题，例如If-Modified-Since，使用此方法。
	 * <p>
	 * 日期返回为自1970格林尼治标准时间1月1日以来的毫秒数。标题名称不区分大小写。
	 * <p>
	 * 如果请求没有指定名称的消息头，这个方法返回-1。如果头文件不能转换为日期，该方法会抛出IllegalArgumentException。
	 * 
	 * @param name - 指定请求头名称的字符串
	 * @return 一个 long 值，表示标头中指定的日期，表示为自 1970 年 1 月 1 日 GMT 以来的毫秒数，如果请求中未包含命名标头，则为 -1
	 * 
	 * @apiNote If-Modified-Since是标准的HTTP请求头标签，在发送HTTP请求时，把浏览器端缓存页面的最后修改时间一起发到服务器去，
	 * 服务器会把这个时间与服务器上实际文件的最后修改时间进行比较。
	 * <p>
	 * 如果时间一致，那么返回HTTP状态码304（不返回文件内容），客户端接到之后，就直接把本地缓存文件显示到浏览器中。
	 * <p>
	 * 如果时间不一致，就返回HTTP状态码200和新的文件内容，客户端接到之后，会丢弃旧文件，把新文件缓存起来，并显示到浏览器中。
	 */
	@Override
	public long getDateHeader(String name) {
		String value = getHeader(name);
        if (value == null) {
            return -1L;
        }

        // 尝试以多种格式转换日期标题
        long result = FastHttpDateFormat.parseDate(value);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);
	}

	/**
	 * 以字符串形式返回指定请求头的值。 如果请求不包含指定名称的header，该方法返回null。
	 * 如果有多个同名的header，该方法返回请求中的第一个header。header名称不区分大小写。
	 * 
	 * @param name - 指定请求头名称的字符串
	 */
	@Override
	public String getHeader(String name) {
		return request.getHeader(name);
	}

	/**
	 * 返回指定请求部的所有值，作为String对象的枚举值。
	 * <p>
	 * 有些报头，例如Accept-Language，可以由客户端以多个报头的形式发送，每个报头具有不同的值，而不是以逗号分隔的列表形式发送报头。
	 * <p>
	 * 如果请求不包含任何指定名称的头文件，此方法将返回一个空的枚举。标题名称不区分大小写。您可以对任何请求头使用此方法。
	 * 
	 * @param name - 指定请求头名称的字符串
	 * @return 一个包含所请求头值的枚举。 如果请求没有该名称的任何请求头名称，则返回一个空枚举。 如果容器不允许访问header信息，则返回null
	 */
	@Override
	public Enumeration<String> getHeaders(String name) {
        return request.getMimeHeaders().values(name);
	}

	/**
	 * 返回此请求包含的所有标请求头名称的枚举。 如果请求没有请求头，则此方法返回一个空枚举。
	 * <p>
	 * 某些 servlet 容器不允许 servlet 使用此方法访问标头，在这种情况下此方法返回 null
	 * 
	 * @return 与此请求一起发送的所有请求头名称的枚举； 如果请求没有请求头，则为空枚举；如果 servlet 容器不允许 servlet 使用此方法，则为 null
	 */
	@Override
	public Enumeration<String> getHeaderNames() {
        return request.getMimeHeaders().names();
	}

	/**
	 * 以 int 形式返回指定请求头的值。 如果请求没有指定名称的请求头，则此方法返回 -1。 如果请求头值无法转换为整数，则此方法将引发 NumberFormatException。
	 * <p>
	 * 请求头名称不区分大小写。
	 * 
	 * @param name - 一个字符串，指定请求头的名称
	 * @return 表示请求头值的整数。如果请求没有指定名称的请求头，则此方法返回 -1
	 */
	@Override
	public int getIntHeader(String name) {
		String value = getHeader(name);
        if (value == null) {
            return -1;
        }

        return Integer.parseInt(value);
	}

	/**
	 * 返回与客户端发出此请求时发送的 URL 关联的任何额外路径信息。额外路径信息在 servlet 路径之后，但在查询字符串之前，并以“/”字符开头。即为uri。
	 * <p>
	 * 如果没有额外的路径信息，此方法返回 null。
	 * <p>
	 * 与 CGI 变量 PATH_INFO 的值相同。
	 * 
	 * @return 一个字符串，由 web 容器解码，指定请求 URL 中 servlet 路径之后但查询字符串之前的额外路径信息；如果 URL 没有任何额外路径信息，则为 null
	 */
	@Override
	public String getPathInfo() {
        return mappingData.pathInfo.toString();
	}

	/**
	 * 返回 servlet 名称之后但查询字符串之前的任何额外路径信息，并将其转换为真实路径。 与 CGI 变量 PATH_TRANSLATED 的值相同。
	 * <p>
	 * 如果 URL 没有任何额外的路径信息，则此方法返回 null 或 servlet 容器由于任何原因（例如从存档执行 Web 应用程序时）
	 * 无法将虚拟路径转换为真实路径。Web 容器不解码这个字符串。
	 * 
	 * @return 指定实际路径的字符串，如果 URL 没有任何额外的路径信息，则为 null
	 */
	@Override
	public String getPathTranslated() {
		Context context = getContext();
        if (context == null) {
            return null;
        }

        if (getPathInfo() == null) {
            return null;
        }

        return context.getServletContext().getRealPath(getPathInfo());
	}

	/**
	 * 返回请求 URI 中指示请求上下文的部分。 上下文路径总是出现在 requestURI 中。 路径以“/”字符开头，但不以“/”字符结尾。 
	 * 对于默认（根）上下文中的 servlet，此方法返回“”。 容器不解码此字符串。
	 * <p>
	 * 一个 servlet 容器可能通过多个上下文路径匹配一个上下文。 
	 * 在这种情况下，此方法将返回请求使用的实际上下文路径，它可能与 {@link ServletContext#getContextPath()} 方法返回的路径不同。 
	 * {@link ServletContext.getContextPath() } 返回的上下文路径应该是 被视为应用程序的主要或首选上下文路径。
	 * 
	 * @return 一个字符串，指定请求 URI 中指示请求上下文的部分
	 */
	@Override
	public String getContextPath() {
        int lastSlash = mappingData.contextSlashCount;
        // 根上下文的特殊情况处理
        if (lastSlash == 0) {
            return "";
        }
		return this.getContext().getPath();
	}

	/**
	 * 返回此请求URL中从协议名称到HTTP请求第一行的查询字符串的部分。web容器不解码此字符串。例如:
	 * <table summary="Examples of Returned Values">
     * 		<tr>
     * 			<th width='250px'>First line of HTTP request</th>
     * 			<th>Returned Value</th>
     * 		</tr>
     * 		<tr>
     * 			<td>POST /some/path.html HTTP/1.1<td>/some/path.html</td>
     * 		</tr>
     *  	<tr>
     *  		<td>GET http://foo.bar/a.html HTTP/1.0<td>/a.html</td>
     * 		</tr>>
     *  	<tr>
     *  		<td>HEAD /xyz?a=b HTTP/1.1<td>/xyz</td>
     * 		</tr>>
     * </table>
     * <p>
     * 
     * @return 包含从协议名称到查询字符串的部分URL的字符串
	 */
	@Override
	public String getRequestURI() {
        return request.requestURI().toString();
	}

	/**
	 * 重构客户端用于发出请求的 URL。返回的 URL 包含协议、服务器名称、端口号和服务器路径，但不包含查询字符串参数。
	 * <p>
	 * 如果此请求已使用 javax.servlet.RequestDispatcher.forward 转发，
	 * 则重新构造的 URL 中的服务器路径必须反映用于获取 RequestDispatcher 的路径，而不是客户端指定的服务器路径。
	 * <p>
	 * 由于此方法返回的是 StringBuffer，而不是字符串，因此可以轻松修改 URL，例如附加查询参数。
	 * <p>
	 * 此方法对于创建重定向消息和报告错误很有用。
	 * 
	 * @return 包含重构 URL 的 StringBuffer 对象
	 */
	@Override
	public StringBuffer getRequestURL() {
        return RequestUtil.getRequestURL(this);
	}

	/**
	 * 返回此请求的 URL 中调用 servlet 的部分。 此路径以“/”字符开头，包括 servlet 名称或 servlet 路径，
	 * 但不包括任何额外的路径信息或查询字符串。 与 CGI 变量 SCRIPT_NAME 的值相同。
	 * <p>
	 * 如果用于处理此请求的 servlet 使用“/*”模式匹配，则此方法将返回一个空字符串 ("")。
	 * 
	 * @return 一个字符串，其中包含被调用的 servlet 的名称或路径，如请求 URL 中所指定，已解码，或者如果用于处理请求的 servlet 使用“/*”模式匹配，则为空字符串。
	 */
	@Override
	public String getServletPath() {
        return mappingData.wrapperPath.toString();
	}

	/**
	 * 返回与此请求关联的当前 HttpSession，或者，如果没有当前会话并且 create 为 true，则返回一个新会话。
	 * <p>
	 * 如果 create 为 false 并且请求没有有效的 HttpSession，则此方法返回 null。
	 * <p>
	 * 为了确保会话得到正确维护，您必须在提交响应之前调用此方法。 
	 * 如果容器使用 cookie 来维护会话完整性，并且在提交响应时被要求创建新会话，则会引发 IllegalStateException。
	 * 
	 * @param create - 如有必要，为该请求创建一个新会话； 如果没有当前会话，则返回 null
	 * @return 与此请求关联的 HttpSession 或 null 如果 create 为 false 并且请求没有有效会话
	 */
	@Override
	public HttpSession getSession(boolean create) {
		Session session = doGetSession(create);
        if (session == null) {
            return null;
        }

        return session.getSession();
	}

	/**
	 * 返回与此请求关联的当前会话，或者如果请求没有会话，则创建一个。
	 * 
	 * @return 与此请求关联的 HttpSession
	 */
	@Override
	public HttpSession getSession() {
        Session session = doGetSession(true);
        if (session == null) {
            return null;
        }

        return session.getSession();
	}

    /**
     * @return 与此请求相关联的会话，如果需要的话创建一个
     */
    public Session getSessionInternal() {
        return doGetSession(true);
    }
	
	/**
	 * 返回客户端指定的会话ID。这可能与此请求的当前有效会话的ID不相同。如果客户端没有指定会话ID，这个方法返回null。
	 * 
	 * @return 指定sessionID的String，如果请求没有指定会话ID，则为null
	 */
	@Override
	public String getRequestedSessionId() {
		return requestedSessionId;
	}

	/**
	 * 更改与此请求关联的当前会话的会话 ID，并返回新的会话 ID。
	 * 
	 * @return 新的会话 ID
	 */
	@Override
	public String changeSessionId() {
		Session session = this.getSessionInternal(false);
        if (session == null) {
            throw new IllegalStateException("创建新会话失败，无法更换 会话ID");
        }

        Manager manager = this.getContext().getManager();
        manager.changeSessionId(session);

        String newSessionId = session.getId();
        this.changeSessionId(newSessionId);

        return newSessionId;
	}

	/**
	 * 检查请求的会话 ID 是否仍然有效。
	 * <p>
	 * 如果客户端未指定任何会话 ID，则此方法返回 false。
	 * 
	 * @return 如果此请求在当前会话上下文中具有有效会话的 ID，则为 true； 否则为假
	 */
	@Override
	public boolean isRequestedSessionIdValid() {
		return false;
	}

	/**
	 * 检查请求的会话 ID 是否作为 HTTP cookie 传送到服务器。
	 * 
	 * @return 如果会话 ID 通过 HTTPcookie 传递给服务器，则为 true； 否则为false。
	 */
	@Override
	public boolean isRequestedSessionIdFromCookie() {
		 if (requestedSessionId == null) {
	            return false;
	        }

	        return requestedSessionCookie;
	}

	/**
	 * 检查请求的会话 ID 是否作为请求 URL 的一部分传送给服务器。
	 * 
	 * @return 如果会话 ID 作为 URL 的一部分传送给服务器，则为 true； 否则为false。
	 */
	@Override
	public boolean isRequestedSessionIdFromURL() {
		if (requestedSessionId == null) {
            return false;
        }

        return requestedSessionURL;
	}

	/**
	 * 如果会话 ID 作为 URL 的一部分传送给服务器，则为 true； 否则为false
	 */
	@Override
	@Deprecated
	public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
	}
	
	/**
	 * 返回用于保护 servlet 的身份验证方案的名称。 所有 servlet 容器都支持基本、表单和客户端证书身份验证，并且可能还支持摘要身份验证。如果 servlet 未通过身份验证，则返回 null。
	 * <p>
	 * 与 CGI 变量 AUTH_TYPE 的值相同。
	 * 
	 * @return 静态成员 BASIC_AUTH、FORM_AUTH、CLIENT_CERT_AUTH、DIGEST_AUTH（适合 == 比较）或指示身份验证方案的容器特定字符串之一，如果请求未通过身份验证，则为 null。
	 */
	@Override
	public String getAuthType() {
		return authType;
	}

	/**
	 * 如果用户已通过身份验证，则返回发出此请求的用户的登录名；如果用户尚未通过身份验证，则返回 null。
	 * 是否随每个后续请求发送用户名取决于浏览器和身份验证类型。 与 CGI 变量 REMOTE_USER 的值相同。
	 */
	@Override
	public String getRemoteUser() {
	    if (userPrincipal == null) {
	        return null;
	    }
	
	    return userPrincipal.getName();
	}

	/**
	 * 返回一个布尔值，指示通过身份验证的用户是否包含在指定的逻辑“角色”中。可以使用部署描述符定义角色和角色成员关系。如果用户没有通过身份验证，该方法返回false。
	 * <p>
	 * 在调用isUserInRole时，角色名“*”绝对不能用作参数。使用"*"调用isUserInRole必须返回false。
	 * 如果要测试的安全角色的role-name为“**”，并且应用程序没有声明一个role-name为“**”的应用安全角色，
	 * 那么isUserInRole必须只在用户通过认证的情况下返回true;也就是说，只有当getRemoteUser和getUserPrincipal都返回非空值时。
	 * 否则，容器必须检查用户在应用程序角色中的成员资格。
	 * 
	 * @param role - 指定角色名称的字符串
	 * @return 一个布尔值，指示发出此请求的用户是否属于给定角色;如果用户没有被验证，则为false
	 */
	@Override
	public boolean isUserInRole(String role) {
		// TODO 自动生成的方法存根
		return false;
	}

	/**
	 * 返回一个java.security.Principal对象，该对象包含当前通过身份验证的用户的名称。如果用户没有通过身份验证，该方法返回null。
	 * 
	 * @return principal包含发出请求的用户名;如果用户没有通过身份验证，则为Null
	 */
	@Override
	public Principal getUserPrincipal() {
		// TODO 自动生成的方法存根
		return null;
	}

	/**
	 * 使用为 ServletContext 配置的容器登录机制来验证发出此请求的用户。
	 * <p>
	 * 此方法可能会修改并提交参数 HttpServletResponse。
	 * 
	 * @param response  - 与此 HttpServletRequest 关联的 HttpServletResponse
	 * @return 当getUserPrincipal、getRemoteUser和getAuthType返回的值为或已经确定为非null值时，为true。
	 * 如果认证不完整且底层登录机制已经提交，则返回false，在响应中，将返回给用户的消息(例如，挑战)和HTTP状态码。
	 * 
	 * @throws IOException - 如果在读取此请求或写入给定响应时发生输入或输出错误
	 * @throws ServletException - 如果身份验证失败并且调用者负责处理错误（即，底层登录机制没有建立要返回给用户的消息和 HTTP 状态代码）
	 */
	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		// TODO 自动生成的方法存根
		return false;
	}

	/**
	 * 在为 ServletContext 配置的 Web 容器登录机制使用的密码验证域中验证提供的用户名和密码。
	 * <p>
	 * 当为 ServletContext 配置的机制支持用户名密码验证时，并且在调用 login 时，请求调用者的身份尚未建立（即 getUserPrincipal、getRemoteUser 和 getAuthType 返回 null)，并且当提供的凭据验证成功时。
	 * 否则，此方法将引发 ServletException，如下所述。
	 * <p>
	 * 当此方法返回而不抛出异常时，它必须已确定非空值作为 getUserPrincipal、getRemoteUser 和 getAuthType 返回的值。
	 * @param username - 用户登录标识对应的 String 值
	 * @param password - 与已识别用户对应的密码字符串
	 * @throws ServletException - 如果配置的登录机制不支持用户名密码身份验证，或者如果已经建立了非空调用者身份（在调用登录之前），或者如果提供的用户名和密码的验证失败。
	 */
	@Override
	public void login(String username, String password) throws ServletException {
		// TODO 自动生成的方法存根
		
	}

	/**
	 * 建立null作为请求调用getUserPrincipal、getRemoteUser和getAuthType时返回的值。
	 * 
	 * @throws ServletException - 如果注销失败
	 */
	@Override
	public void logout() throws ServletException {
		// TODO 自动生成的方法存根
		
	}

	/**
	 * 为给定类创建 HttpUpgradeHandler 的实例，并将其用于 http 协议升级处理。
	 * 
	 * @param <T> - 继承handlerClass的HttpUpgradeHandler的类
	 * @param handlerClass - 用于升级的 HttpUpgradeHandler 类。
	 * @return HttpUpgradeHandler 的一个实例
	 * @throws IOException - 如果在升级过程中发生 I/O 错误
	 * @throws ServletException - 如果给定的 handlerClass 无法实例化
	 * 
	 * @see javax.servlet.http.HttpUpgradeHandler
	 * @see javax.servlet.http.WebConnection
	 */
	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		// TODO 自动生成的方法存根
		return null;
	}
	
	/**
	 * 获取此请求的所有 Part 组件，前提是它的类型为 multipart/form-data。
	 * <p>
	 * 如果此请求是 multipart/form-data 类型，但不包含任何 Part 组件，则返回的 Collection 将为空。
	 * <p>
	 * 对返回的 Collection 的任何更改都不得影响此 HttpServletRequest。
	 * 
	 * @return 此请求的 Part 组件的（可能为空）集合
	 * @throws IOException - 如果在检索此请求的 Part 组件期间发生 I/O 错误
	 * @throws ServletException - 如果此请求不是 multipart/form-data 类型
	 * 
	 * @see javax.servlet.annotation.MultipartConfig#maxFileSize
	 * @see javax.servlet.annotation.MultipartConfig#maxRequestSize
	 */
	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		parseParts(true);

        if (partsParseException != null) {
            if (partsParseException instanceof IOException) {
                throw (IOException) partsParseException;
            } else if (partsParseException instanceof IllegalStateException) {
                throw (IllegalStateException) partsParseException;
            } else if (partsParseException instanceof ServletException) {
                throw (ServletException) partsParseException;
            }
        }

        return parts;
	}

	/**
	 * 获取具有给定名称的 Part。
	 * 
	 * @param name - 被请求 Part 的名称
	 * @return 具有给定名称的 Part，如果此请求的类型为 multipart/form-data，但不包含请求的部件，则为 null
	 * @throws IOException      - 如果在检索此请求的 Part 组件期间发生 I/O 错误
	 * @throws ServletException - 如果此请求不是 multipart/form-data 类型
	 * 
	 * @see javax.servlet.annotation.MultipartConfig#maxFileSize
	 * @see javax.servlet.annotation.MultipartConfig#maxRequestSize
	 */
	@Override
	public Part getPart(String name) throws IOException, ServletException {
		for (Part part : getParts()) {
            if (name.equals(part.getName())) {
                return part;
            }
        }
        return null;
	}

	/**
	 * 使用getTrailerFields返回一个布尔值，指示尾部字段是否可以读取。
	 * 如果知道请求中没有尾部字段，该方法立即返回true，例如，底层协议(如HTTP 1.0)不支持尾部字段，或者请求在HTTP 1.1中不是块编码。
	 * 如果满足以下两个条件，该方法也返回true:
	 * 
	 * <ol type="a">
	 * 		<li>应用程序已读取所有请求数据，并且已从 getReader 或 getInputStream 返回 EOF 讯号（-1）
	 * 		<li>客户端发送的所有尾部字段都已收到。请注意，客户端可能未发送任何尾部字段。
	 * </ol>
	 * 
	 * @return 一个布尔值，尾部字段是否可以读取
	 */
    @Override
    public boolean isTrailerFieldsReady() {
        return request.isTrailerFieldsReady();
    }

    
    /**
     * 获取请求尾字段。
     * <p>
     * 返回的映射不受 HttpServletRequest 对象的支持，因此返回的映射中的更改不会反映在 HttpServletRequest 对象中，反之亦然。
     * <p>
     * 应首先调用 isTrailerFieldsReady() 以确定调用此方法是否安全而不会导致异常。
     * 
     * @return 尾部字段的映射，其中所有键都是小写的，无论它们在协议级别上是什么情况。如果没有尾部字段，而 isTrailerFieldsReady  返回true，则返回空映射。
     */
    @Override
    public Map<String, String> getTrailerFields() {
        if (!isTrailerFieldsReady()) {
            throw new IllegalStateException("trailersNotReady");
        }
        Map<String,String> result = new HashMap<>();
        result.putAll(request.getTrailerFields());
        return result;
    }
    
    
    /**
     * 实例化 PushBuilder 的新实例，用于从当前请求发出服务器推送响应。 
     * 如果当前连接不支持服务器推送，或者客户端通过 SETTINGS_ENABLE_PUSH 设置帧值 0（零）禁用了服务器推送，则此方法返回 null。
     * 
     * @return 用于从当前请求发出服务器推送响应的 PushBuilder，如果不支持推送，则为 null
     */
    @Override
    public PushBuilder newPushBuilder() {
        return newPushBuilder(this);
    }
    
    public PushBuilder newPushBuilder(HttpServletRequest httpServletRequest) {
        AtomicBoolean result = new AtomicBoolean();
        request.action(ActionCode.IS_PUSH_SUPPORTED, httpServletRequest);
        if (result.get()) {
            return new ApplicationPushBuilder(this, httpServletRequest);
        } else {
            return null;
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// HttpRequest Methods
	// -------------------------------------------------------------------------------------
    /**
     * 设置用于此请求的身份验证类型（如果有）； 否则将类型设置为 null。 典型值为“BASIC”、“DIGEST”或“SSL”。
     *
     * @param type - 使用的身份验证类型
     */
    public void setAuthType(String type) {
        this.authType = type;
    }
    
    /**
     * 返回与此请求关联的 cookie 的服务器表示。 如果请求头尚未被解析，则触发对 Cookie HTTP 标头的解析（但不转换为 Cookie 对象）。
     *
     * @return 服务器 cookie
     */
    public ServerCookies getServerCookies() {
        parseCookies();
        return request.getCookies();
    }
    
    /**
     * @param create - 如果不存在，则创建一个新会话。
     * @return 与此请求关联的会话，如有必要和请求创建一个。
     */
    public Session getSessionInternal(boolean create) {
        return doGetSession(create);
    }
    
    /**
     * 更改此请求关联的会话ID。有几种情况可能会触发ID更改。这包括在集群中的节点之间移动和在认证过程中防止会话固定。
     *
     * @param newSessionId - 要更改会话ID的会话
     */
    public void changeSessionId(String newSessionId) {
        // 只有在存在旧会话ID时才应调用此命令，但请仔细检查以确保
        if (requestedSessionId != null && requestedSessionId.length() > 0) {
            requestedSessionId = newSessionId;
        }

        Context context = getContext();
        if (context != null && !context.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.COOKIE)) {
            return;
        }

        if (httpResponse != null) {
            Cookie newCookie = ApplicationSessionCookieConfig.createSessionCookie(context, newSessionId, isSecure());
            httpResponse.addSessionCookieInternal(newCookie);
        }
    }
    
    /**
     * 在单个操作中执行刷新和关闭输入流或读取器所需的任何操作
     *
     * @exception IOException - 如果发生输入/输出错误
     */
    public void finishRequest() throws IOException {
    	// 状态码(413)，表示服务器拒绝处理请求，因为请求实体大于服务器愿意或能够处理的请求实体。
        if (httpResponse.getStatus() == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE) {
        	this.request.action(ActionCode.CLOSE_NOW, null);
        }
    }
    
    /**
     * 将区域设置添加到此请求的首选区域设置集合中。第一个添加的Locale将是 getLocales() 返回的第一个Locale。
     *
     * @param locale - 新首选区域设置
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }
	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    /**
     * 解析请求参数
     */
    protected void parseParameters() {
    	parametersParsed = true;

    	Parameters parameters = request.getParameters();
    	boolean success = false;
    	try {
    		parameters.setLimit(getConnector().getMaxParameterCount());

    		// getCharacterEncoding() 可能已被覆盖以搜索包含请求编码的隐藏表单字段
    		Charset charset = getCharset();
    		parameters.setCharset(charset);

    		boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
    		if (useBodyEncodingForURI) {

    			parameters.setQueryStringCharset(charset);
    		}
    		// 注意：如果 !useBodyEncodingForURI 为true则查询字符串编码是在 MoonAdapter.service() 的开头设置的, 其来自于 Connector 配置
    		parameters.handleQueryParameters();

    		if (usingInputStream || usingReader) {
    			success = true;
    			return;
    		}

    		// 已解析不包含子项的Http "ContentType" 请求头
    		String contentType = this.request.getParsedContentType();
    		if (contentType == null) {
    			contentType = "";
    		}

    		if ("multipart/form-data".equals(contentType)) {
    			parseParts(false);
    			success = true;
    			return;
    		}

    		if( !getConnector().isParseBodyMethod(getMethod()) ) {
    			success = true;
    			return;
    		}

    		if (!("application/x-www-form-urlencoded".equals(contentType))) {
    			success = true;
    			return;
    		}

    		int len = getContentLength();

    		if (len > 0) {
    			int maxPostSize = connector.getMaxPostSize();
    			if ((maxPostSize >= 0) && (len > maxPostSize)) {
    				Context context = getContext();
    				if (context != null && context.getLogger().isDebugEnabled()) {
    					context.getLogger().debug("请求体数据过大");
    				}
    				parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
    				return;
    			}
    			parameters.processRequestBodyParameters( this.getRequest().getRequestBodyByte() );
    			success = true;
    		}
    	} finally {
    		if (!success) {
    			parameters.setParseFailedReason(FailReason.UNKNOWN);
    		}
    	}

    }
    
    /**
     * Parse request locales.
     */
    protected void parseLocales() {
    	if (!localesParsed) {
    		return ;
        }
    	
        localesParsed = true;

        /*
         * 将已请求的累积 languages 存储在本地集合中，按质量值排序(这样我们就可以按降序添加区域设置)。
         * 这些值将是包含要添加的相应区域设置的ArrayList
         */
        TreeMap<Double, ArrayList<Locale>> locales = new TreeMap<>();

        Enumeration<String> values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String acceptLanguageValue = values.nextElement();
            parseLocalesHeader(acceptLanguageValue, locales);
        }

        // 按最高->最低顺序处理质量值（由于在创建密钥时否定Double值）
        for (ArrayList<Locale> list : locales.values()) {
            for (Locale locale : list) {
                addLocale(locale);
            }
        }
    }
    
    /**
     * 解析 accept-language 请求值
     *
     * @param acceptLanguageValue - 解析的 accept-language 头值
     * @param locales - 保存结果的 {@link Map }
     */
    protected void parseLocalesHeader(String acceptLanguageValue, TreeMap<Double, ArrayList<Locale>> locales) {
        List<AcceptLanguage> acceptLanguages;
        
        try {
            acceptLanguages = AcceptLanguage.parse(acceptLanguageValue);
        } catch (IOException e) {
            // 忽略格式错误的http 请求头。在不太可能发生IOException的情况下，也要这样做。
            return;
        }

        for (AcceptLanguage acceptLanguage : acceptLanguages) {
            // 将新区域设置添加到此质量级别的区域列表中
            Double key = Double.valueOf(-acceptLanguage.getQuality());  // 倒序
            ArrayList<Locale> values = locales.get(key);
            if (values == null) {
                values = new ArrayList<>();
                locales.put(key, values);
            }
            values.add(acceptLanguage.getLocale());
        }
    }
    
    protected Session doGetSession(boolean create) {
        // 如果尚未分配上下文，则不能有会话
        Context context = getContext();
        if (context == null) {
            return null;
        }

        // 如果当前会话存在并且有效，则返回它
        if ((session != null) && !session.isValid()) {
            session = null;
        }
        if (session != null) {
            return session;
        }

        // 如果请求的会话存在并且有效，则返回它
        Manager manager = context.getManager();
        if (manager == null) {
            return null;      // 不支持会话
        }
        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                session = null;
            }
            if ((session != null) && !session.isValid()) {
                session = null;
            }
            if (session != null) {
                session.access();
                return session;
            }
        }

        //  如果请求并且未提交响应，则创建一个新会话
        if (!create) {
            return null;
        }
        
        // 是否是通过 cookie 跟踪会话模式
        boolean trackModesIncludesCookie = context.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.COOKIE);
        if (trackModesIncludesCookie && httpResponse.getHttpServletResponse().isCommitted()) {
            throw new IllegalStateException("需新建会话但响应已提交");
        }

        // 在非常有限的情况下重用客户端提供的会话 ID
        String sessionId = getRequestedSessionId();
        if (requestedSessionSSL) {
            // 如果会话 ID 已从 SSL 握手中获得，则使用它
        } else if (("/".equals(context.getSessionCookiePath()) && isRequestedSessionIdFromCookie())) {
            /* 
             * 这是常见的用例：对同一主机上的多个 Web 应用程序使用相同的会话 ID。 通常这由 Portlet 实现使用。 
             * 它仅在通过 cookie 跟踪会话时才有效。 cookie 的路径必须为“/”，否则不会为所有 Web 应用程序的请求提供它。
             * 
             * 客户端提供的任何会话 ID 都应该用于主机某处已经存在的会话。 检查是否配置了上下文以进行确认。
             */
            if (context.getValidateClientProvidedNewSessionId()) {
                boolean found = false;
                for (Container container : getHost().findChildren()) {
                    Manager m = ((Context) container).getManager();
                    if (m != null) {
                        try {
                            if (m.findSession(sessionId) != null) {
                                found = true;
                                break;
                            }
                        } catch (IOException e) {
                            // 忽略，这样的问题将在其他地方处理
                        }
                    }
                }
                if (!found) {
                    sessionId = null;
                }
            }
        } else {
            sessionId = null;
        }
        session = manager.createSession(sessionId);

        // 基于该会话创建一个新的会话cookie
        if (session != null && trackModesIncludesCookie) {
            Cookie cookie = ApplicationSessionCookieConfig.createSessionCookie(context, session.getIdInternal(), isSecure());

           httpResponse.addSessionCookieInternal(cookie);
        }

        if (session == null) {
            return null;
        }

        session.access();
        return session;
    }
    
    /**
     * 解析 cookie。 这只会将 cookie 解析为内存高效的 ServerCookies 结构。 它不填充 Cookie 对象。
     */
    protected void parseCookies() {
        if (cookiesParsed) {
            return;
        }

        cookiesParsed = true;

        ServerCookies serverCookies = request.getCookies();
        serverCookies.setLimit(connector.getMaxCookieCount());
        CookieProcessor cookieProcessor = getContext().getCookieProcessor();
        cookieProcessor.parseCookieHeader(request.getMimeHeaders(), serverCookies);
    }
    
    /**
     * 将已解析的Cookie（如果尚未解析，则首先解析Cookie标头）转换为Cookie对象。
     */
    protected void convertCookies() {
        if (cookiesConverted) {
            return;
        }

        cookiesConverted = true;

        if (getContext() == null) {
            return;
        }

        parseCookies();

        ServerCookies serverCookies = request.getCookies();
        CookieProcessor cookieProcessor = getContext().getCookieProcessor();

        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        cookies = new Cookie[count];

        int idx=0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                // 必须取消 '\\' 转义字符的转义
                Cookie cookie = new Cookie(scookie.getName().toString(),null);
                int version = scookie.getVersion();
                cookie.setVersion(version);
                scookie.getValue().getByteChunk().setCharset(cookieProcessor.getCharset());
                cookie.setValue(unescape(scookie.getValue().toString()));
                cookie.setPath(unescape(scookie.getPath().toString()));
                String domain = scookie.getDomain().toString();
                if (domain!=null) {
                    cookie.setDomain(unescape(domain));//avoid NPE
                }
                String comment = scookie.getComment().toString();
                cookie.setComment(version==1?unescape(comment):null);
                cookies[idx++] = cookie;
            } catch(IllegalArgumentException e) {
                // 忽略坏cookie
            }
        }
        if( idx < count ) {
            Cookie [] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }
    }
    
    protected String unescape(String s) {
        if (s==null) {
            return null;
        }
        if (s.indexOf('\\') == -1) {
            return s;
        }
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (c!='\\') {
                buf.append(c);
            } else {
                if (++i >= s.length()) {
                    throw new IllegalArgumentException();// 无效转义，因此无效cookie
                }
                c = s.charAt(i);
                buf.append(c);
            }
        }
        return buf.toString();
    }
    
    /**
     * 检查中止上传的配置，如果配置了这样做，禁用任何剩余输入的吞咽，并在响应写入后关闭连接。
     */
    protected void checkSwallowInput() {
        Context context = getContext();
        if (context != null && !context.getSwallowAbortedUploads()) {
        	
        }
    }
    
	// -------------------------------------------------------------------------------------
	// Other Methods
	// -------------------------------------------------------------------------------------
    /**
     * 通知感兴趣的监听程序已为属性赋值
     *
     * @param name - 属性名
     * @param value - 新的属性值
     * @param oldValue - 老的属性值
     */
    private void notifyAttributeAssigned(String name, Object value, Object oldValue) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        boolean replaced = (oldValue != null);
        ServletRequestAttributeEvent event = null;
        if (replaced) {
            event = new ServletRequestAttributeEvent(context.getServletContext(), getHttpServletRequest(), name, oldValue);
        } else {
            event = new ServletRequestAttributeEvent(context.getServletContext(), getHttpServletRequest(), name, value);
        }

        for (Object o : listeners) {
            if (!(o instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener = (ServletRequestAttributeListener) o;
            try {
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 错误 Valve 将拾取此异常并将其显示给用户
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error("ServletRequestAttributeEvent 事件异常", t);
            }
        }
    }
    
    /**
     * 通知感兴趣的监听者属性已被删除
     *
     * @param name - 属性名
     * @param value - 属性值
     */
    private void notifyAttributeRemoved(String name, Object value) {
        Context context = getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(context.getServletContext(), getHttpServletRequest(), name, value);
        for (Object o : listeners) {
            if (!(o instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener = (ServletRequestAttributeListener) o;
            try {
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 错误 Valve 将拾取此异常并将其显示给用户
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error("ServletRequestAttributeEvent 事件异常", t);
            }
        }
    }
    
    private Charset getCharset() {
        Charset charset = null;
        try {
            charset = request.getCharset();
        } catch (UnsupportedEncodingException e) {
            // Ignore
        }
        if (charset != null) {
            return charset;
        }

        Context context = getContext();
        if (context != null) {
            if (context.getRequestCharacterEncoding() != null) {
            	charset = Charset.forName( context.getRequestCharacterEncoding() );
            }
        }

        return charset == null ? Globals.DEFAULT_BODY_CHARSET : charset;
    }
    

    private void parseParts(boolean explicit) {
	    // 如果已经解析了部分，则立即返回
	    if (parts != null || partsParseException != null) {
	        return;
	    }
	
	    Parameters parameters = request.getParameters();
	    parameters.setLimit(getConnector().getMaxParameterCount());
	
	    int maxPostSize = getConnector().getMaxPostSize();
	    int requestBodyLength = this.request.getContentLength();
	    if (requestBodyLength > maxPostSize) {
	        parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
	        throw new IllegalStateException("请求体数据过大, by maxPostSize: " + maxPostSize  + ", requestBodyLength: "+ requestBodyLength);
	    }
	
	    Context context = getContext();
	    MultipartConfigElement mce = getWrapper().getMultipartConfigElement();
	
	    if (mce == null) {
	        if(context.getAllowCasualMultipartParsing()) {
	        	/**
	        	 * 用指定的所有值构造一个实例
	        	 * 
	        	 * @param location - 将存储文件的目录位置
	        	 * @param maxFileSize - 上载文件允许的最大大小
	        	 * @param maxRequestSize - multipart/form-data 数据请求允许的最大大小
	        	 * @param fileSizeThreshold - 文件写入磁盘的大小阈值
	        	 */
	            mce = new MultipartConfigElement(null, connector.getMaxPostSize(), connector.getMaxPostSize() , connector.getMaxPostSize());
	        } else {
	            if (explicit) {
	                partsParseException = new IllegalStateException("无 MultipartConfig");
	                return;
	            } else {
	                parts = Collections.emptyList();
	                return;
	            }
	        }
	    }
	
	    boolean success = false;
	    try {
	        File location;
	        String locationStr = mce.getLocation();
	        if (locationStr == null || locationStr.length() == 0) {
	        	// context doc目录
	            location = ((File) context.getServletContext().getAttribute(ServletContext.TEMPDIR));
	        } else {
	            // 如果是相对的，则它是相对于TEMPDIR的
	            location = new File(locationStr);
	            if (!location.isAbsolute()) {
	                location = new File((File) context.getServletContext().getAttribute(ServletContext.TEMPDIR), locationStr).getAbsoluteFile();
	            }
	        }
	
	        if (!location.exists() && context.getCreateUploadTargets()) {
	            logger.warn("容器 [{}] 尝试创建不存在的文件存储目录: {}", getMappingData().wrapper.getName(), location.getAbsolutePath());
	            if (!location.mkdirs()) {
	                logger.warn("尝试创建不存在的文件存储目录失败, by: ",location.getAbsolutePath());
	            }
	        }
	
	        if (!location.isDirectory()) {
	            parameters.setParseFailedReason(FailReason.MULTIPART_CONFIG_INVALID);
	            partsParseException = new IOException("无效的文件存储目录: " + location);
	            return;
	        }
	
	
	        // 创建一个新的文件上传处理程序
	        DiskFileItemFactory factory = new DiskFileItemFactory();
	        try {
	            factory.setRepository(location.getCanonicalFile());
	            factory.setDefaultCharset(getCharacterEncoding());
	        } catch (IOException ioe) {
	            parameters.setParseFailedReason(FailReason.IO_ERROR);
	            partsParseException = ioe;
	            return;
	        }
	        factory.setSizeThreshold(mce.getFileSizeThreshold());
	
	        ServletFileUpload upload = new ServletFileUpload();
	        upload.setFileItemFactory(factory);
	        upload.setFileSizeMax(mce.getMaxFileSize());
	        upload.setSizeMax(mce.getMaxRequestSize());
	
	        parts = new ArrayList<>();
	        try {
	            List<FileItem> items = upload.parseRequest(new ServletRequestContext(this));
	            
	            Charset charset = getCharset();
	            for (FileItem item : items) {
	                ApplicationPart part = new ApplicationPart(item, location);
	                parts.add(part);
	                if (part.getSubmittedFileName() == null) {
	                    String name = part.getName();
	                    String value = null;
	                    try {
	                        value = part.getString(charset.name());
	                    } catch (UnsupportedEncodingException uee) {
	                        // 不可能的
	                    }
	                    parameters.addParameter(name, value);
	                }
	            }
	
	            success = true;
	        } catch (InvalidContentTypeException e) {
	            parameters.setParseFailedReason(FailReason.INVALID_CONTENT_TYPE);
	            partsParseException = new ServletException(e);
	        } catch (SizeException e) {
	            parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
	            partsParseException = new IllegalStateException(e);
	        } catch (FileUploadException e) {
	            parameters.setParseFailedReason(FailReason.IO_ERROR);
	            partsParseException = new IOException(e);
	        } catch (IllegalStateException e) {
	            // addParameters() 将设置解析失败原因
	            checkSwallowInput();
	            partsParseException = e;
	        }
	    } finally {
	        if (partsParseException != null || !success) {
	            parameters.setParseFailedReason(FailReason.UNKNOWN);
	        }
	    }
	}

	// -------------------------------------------------------------------------------------
	// 特殊属性处理
	// -------------------------------------------------------------------------------------
    private static interface SpecialAttributeAdapter {
        Object get(HttpRequest request, String name);

        void set(HttpRequest request, String name, Object value);

        // void remove(Request request, String name);
    }

    private static final Map<String, SpecialAttributeAdapter> specialAttributes = new HashMap<>();
}
