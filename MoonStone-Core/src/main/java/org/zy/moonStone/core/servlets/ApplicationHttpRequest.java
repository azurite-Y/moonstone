package org.zy.moonStone.core.servlets;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import javax.servlet.http.PushBuilder;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.RequestFacade;
import org.zy.moonStone.core.http.Parameters;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.session.interfaces.Manager;
import org.zy.moonStone.core.session.interfaces.Session;
import org.zy.moonStone.core.util.ParameterMap;
import org.zy.moonStone.core.util.RequestUtil;
import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年10月5日;
 * @author zy(azurite-Y);
 * @description
 * 围绕 {@link HttpServletRequest } 的包装器，它将应用程序请求对象(它可能是传递给servlet的原始请求，
 * 也可能是基于 {@link HttpServletRequestWrapper } )类转换回内部的 {@link HttpRequest }。
 * <p>
 * 警告:由于Java缺乏对多重继承的支持，ApplicationRequest中的所有逻辑都在ApplicationHttpRequest中重复。确保在进行更改时保持这两个类同步!
 */
class ApplicationHttpRequest extends HttpServletRequestWrapper {
	/** 请求分派器专用的一组属性名称 */
	protected static final String specials[] ={
			RequestDispatcher.INCLUDE_REQUEST_URI,
			RequestDispatcher.INCLUDE_CONTEXT_PATH,
			RequestDispatcher.INCLUDE_SERVLET_PATH,
			RequestDispatcher.INCLUDE_PATH_INFO,
			RequestDispatcher.INCLUDE_QUERY_STRING,
			RequestDispatcher.INCLUDE_MAPPING,
			RequestDispatcher.FORWARD_REQUEST_URI,
			RequestDispatcher.FORWARD_CONTEXT_PATH,
			RequestDispatcher.FORWARD_SERVLET_PATH,
			RequestDispatcher.FORWARD_PATH_INFO,
			RequestDispatcher.FORWARD_QUERY_STRING,
			RequestDispatcher.FORWARD_MAPPING
	};

    private static final int SPECIALS_FIRST_FORWARD_INDEX = 6;
    
    /** 此请求的上下文 */
    protected final Context context;

    /** 此请求的上下文路径 */
    protected String contextPath = null;

    /** 如果该请求是跨上下文请求，因为这会更改会话访问行为 */
    protected final boolean crossContext;

    /** 当前调度程序类型 */
    protected DispatcherType dispatcherType = null;

    /** 此请求的请求参数。这是从包装的请求中初始化的 */
    protected Map<String, String[]> parameters = null;

    /** 此请求的参数是否已被解析？ */
    private boolean parsedParams = false;

    /** 此请求的路径信息 */
    protected String pathInfo = null;

    /** 当前请求的查询参数 */
    private String queryParamString = null;

    /** 此请求的查询字符串 */
    protected String queryString = null;

    /** 当前请求调度器路径 */
    protected Object requestDispatcherPath = null;

    /** 此请求的请求URI */
    protected String requestURI = null;

    /** 此请求的Servlet路径 */
    protected String servletPath = null;

    /** 此请求的映射 */
    private HttpServletMapping mapping = null;

    /** 此请求的当前活动会话 */
    protected Session session = null;

    /** 特殊属性 */
    protected final Object[] specialAttributes = new Object[specials.length];
    
    
	// -------------------------------------------------------------------------------------
	// 构造方法
	// -------------------------------------------------------------------------------------
    /**
     * 围绕指定的servlet请求构造一个新的包装请求。
     *
     * @param request - 包装的Servlet请求
     * @param context - 包装的请求的目标上下文
     * @param crossContext - 如果包装的请求将是跨上下文请求，则为 true，否则为 false
     */
    public ApplicationHttpRequest(HttpServletRequest request, Context context, boolean crossContext) {
        super(request);
        this.context = context;
        this.crossContext = crossContext;
        setRequest(request);
    }
    
    
	// -------------------------------------------------------------------------------------
	// 实现方法
	// -------------------------------------------------------------------------------------
    @Override
    public ServletContext getServletContext() {
        if (context == null) {
            return null;
        }
        return context.getServletContext();
    }

    /**
     * 覆盖包装请求的 <code>getAttribute()</code> 方法。
     *
     * @param name - 要检索的属性的名称
     */
    @Override
    public Object getAttribute(String name) {
        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            return dispatcherType;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            if ( requestDispatcherPath != null ){
                return requestDispatcherPath.toString();
            } else {
                return null;
            }
        }

        int pos = getSpecial(name);
        if (pos == -1) {
            return getRequest().getAttribute(name);
        } else {
            if ((specialAttributes[pos] == null) && (specialAttributes[SPECIALS_FIRST_FORWARD_INDEX] == null) && (pos >= SPECIALS_FIRST_FORWARD_INDEX)) {
                // 如果它是转发特殊属性，并且为空，则意味着这是一个包含，因此我们检查包装的请求，因为请求可能在包含之前被转发
                return getRequest().getAttribute(name);
            } else {
                return specialAttributes[pos];
            }
        }
    }

    /**
     * 重写包装请求的 <code>getAttributeNames()</code> 方法
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return new AttributeNamesEnumerator();
    }

    /**
     * 重写包装请求的 <code>removeAttribute()</code> 方法
     *
     * @param name - 要删除的属性的名称
     */
    @Override
    public void removeAttribute(String name) {
        if (!removeSpecial(name)) getRequest().removeAttribute(name);
    }

    /**
     * 重写包装请求的 <code>setAttribute()</code> 方法
     *
     * @param name - 要设置的属性的名称
     * @param value - 要设置的属性的值
     */
    @Override
    public void setAttribute(String name, Object value) {
        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = (DispatcherType)value;
            return;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        if (!setSpecial(name, value)) {
            getRequest().setAttribute(name, value);
        }
    }

    /**
     * 返回一个RequestDispatcher，该RequestDispatcher包装指定路径上的资源，该路径可能被解释为相对于当前请求路径。
     *
     * @param path - 要包装的资源的路径
     * @return 一个RequestDispatcher对象，用作指定路径上的资源的awRapper；如果Servlet容器不能转换RequestDispatcher，则为null
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        if (context == null)
            return null;

        if (path == null) {
            return null;
        }

        int fragmentPos = path.indexOf('#');
        if (fragmentPos > -1) {
            context.getLogger().warn("applicationHttpRequest..20", path);
            path = path.substring(0, fragmentPos);
        }

        // 如果路径已经是上下文相关的，则只需传递它
        if (path.startsWith("/")) {
            return context.getServletContext().getRequestDispatcher(path);
        }

        // 将请求相对路径转换为上下文相对路径
        String servletPath = (String) getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null)
            servletPath = getServletPath();

        // 添加路径信息, 如果有
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (context.getDispatchersUseEncodedPaths()) {
        	// TODO
            if (pos >= 0) {
//                relative = URLEncoder.DEFAULT.encode(requestPath.substring(0, pos + 1), StandardCharsets.UTF_8) + path;
            } else {
//                relative = URLEncoder.DEFAULT.encode(requestPath, StandardCharsets.UTF_8) + path;
            }
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
     * 重写包装的请求的getDispatcherType()方法
     * 
     * @return 此请求的调度程序类型
     */
    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }


	// -------------------------------------------------------------------------------------
	// HttpServletRequest Methods
	// -------------------------------------------------------------------------------------
    /**
     * 覆盖包装器请求的 <code>getContextPath()</code> 方法
     * 
     * @return 一个字符串，指定请求URI中指示请求上下文的部分
     */
    @Override
    public String getContextPath() {
        return this.contextPath;
    }

    /**
     * 覆盖包装的请求的 <code>getParameter()</code> 方法
     *
     * @param name - 请求的参数名称
     * @return 表示参数的单个值的字符串
     */
    @Override
    public String getParameter(String name) {
        parseParameters();

        String[] value = parameters.get(name);
        if (value == null) {
            return null;
        }
        return value[0];
    }

    /**
     * 重写包装请求的 <code>getParameterMap()</code> 方法
     * 
     * @return 一个不变的java.util.Map，包含参数名作为关键字，参数值作为映射值。参数映射中的键是字符串类型。参数映射中的值是类型字符串数组。
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        parseParameters();
        return parameters;
    }

    /**
     * 重写包装请求的 <code>getParameterNames()</code> 方法。
     * 
     * @return 字符串对象的枚举，每个字符串包含请求参数的名称；如果请求没有参数，则为空枚举
     */
    @Override
    public Enumeration<String> getParameterNames() {
        parseParameters();
        return Collections.enumeration(parameters.keySet());
    }

    /**
     * 重写包装请求的 <code>getParameterValues()</code> 方法
     *
     * @param name - 请求的参数名称
     */
    @Override
    public String[] getParameterValues(String name) {
        parseParameters();
        return parameters.get(name);
    }

    /**
     * 覆盖包装请求的 <code>getPathInfo()</code> 方法。
     * 
     * @return 一个字符串，由Web容器解码，指定在请求URL中的Servlet路径之后但在查询字符串之前的额外路径信息；如果URL没有任何额外路径信息，则为null
     */
    @Override
    public String getPathInfo() {
        return this.pathInfo;
    }

    /**
     * 覆盖包装器请求的 <code>getPathTranslated()</code> 方法
     * 
     * @return 指定实际路径的字符串，如果URL没有任何额外的路径信息，则为null
     */
    @Override
    public String getPathTranslated() {
        if (getPathInfo() == null || getServletContext() == null) {
            return null;
        }

        return getServletContext().getRealPath(getPathInfo());
    }

    /**
     * 覆盖包装器请求的 <code>getQueryString()</code> 方法
     * 
     * @return 一个包含查询字符串的字符串，如果URL不包含查询字符串，则为null。容器不会对该值进行解码。
     */
    @Override
    public String getQueryString() {
        return this.queryString;
    }

    /**
     * 覆盖包装器请求的 <code>getRequestURI()</code> 方法
     * 
     * @return 包含从协议名称到查询字符串的URL部分的字符串
     */
    @Override
    public String getRequestURI() {
        return this.requestURI;
    }

    /**
     * 覆盖包装器请求的 <code>getRequestURL()</code> 方法
     * 
     * @return 包含重新构建的URL的StringBuffer对象
     */
    @Override
    public StringBuffer getRequestURL() {
        return RequestUtil.getRequestURL(this);
    }

    /**
     * 覆盖包装器请求的 <code>getServletPath()</code> 方法
     * 
     * @return 一个字符串，包含被调用的Servlet的名称或路径，如请求URL中指定的，已解码；如果使用“/*”模式匹配用于处理请求的Servlet，则为空字符串。
     */
    @Override
    public String getServletPath() {
        return this.servletPath;
    }

    /**
     * 此方法的默认行为是在包装的请求对象上返回getServletMap()。
     * 
     * @return HttpServletMap的实例，描述调用当前请求的方式。
     */
    @Override
    public HttpServletMapping getHttpServletMapping() {
        return mapping;
    }

    /**
     * 返回与此请求相关联的会话，如有必要可创建一个。
     * 
     * @return 与此请求关联HttpSession
     */
    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    /**
     * 返回与此请求相关联的会话，如果需要并被请求，则创建一个。
     *
     * @param create - 如果不存在新会话，则创建一个新会话
     * @return 与此请求关联HttpSession；如果 create 为 false 且请求没有有效会话，则返回null
     */
    @Override
    public HttpSession getSession(boolean create) {
        if (crossContext) {
            // 如果尚未分配任何上下文，则不能进行会话
            if (context == null)
                return null;

            // 如果当前会话存在且有效，则返回当前会话
            if (session != null && session.isValid()) {
                return session.getSession();
            }

            HttpSession other = super.getSession(false);
            if (create && (other == null)) {
                // 首先在第一个上下文中创建一个会话:问题是顶级请求是唯一可以安全地创建cookie的请求
                other = super.getSession(true);
            }
            if (other != null) {
                Session localSession = null;
                try {
                    localSession = context.getManager().findSession(other.getId());
                    if (localSession != null && !localSession.isValid()) {
                        localSession = null;
                    }
                } catch (IOException e) {
                    // Ignore
                }
                if (localSession == null && create) {
                    localSession = context.getManager().createSession(other.getId());
                }
                if (localSession != null) {
                    localSession.access();
                    session = localSession;
                    return session.getSession();
                }
            }
            return null;

        } else {
            return super.getSession(create);
        }

    }

    /**
     * 如果请求指定了一个在此ApplicationHttpRequest上下文中有效的JSESSIONID，则返回true，否则返回false。
     *
     * @return 如果请求指定了一个在这个ApplicationHttpRequest上下文中有效的JSESSIONID，则为true，否则为false
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        if (crossContext) {

            String requestedSessionId = getRequestedSessionId();
            if (requestedSessionId == null)
                return false;
            if (context == null)
                return false;
            Manager manager = context.getManager();
            if (manager == null)
                return false;
            Session session = null;
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                // Ignore
            }
            if ((session != null) && session.isValid()) {
                return true;
            } else {
                return false;
            }

        } else {
            return super.isRequestedSessionIdValid();
        }
    }
    
    /**
     * 该方法的默认行为是在包装的请求对象上调用newPushBuilder()。
     * 
     * @return 用于从当前请求发出服务器推送响应的PushBuilder，如果不支持推送，则为空
     */
    @Override
    public PushBuilder newPushBuilder() {
        ServletRequest current = getRequest();
        while (current instanceof ServletRequestWrapper) {
            current = ((ServletRequestWrapper) current).getRequest();
        }
        if (current instanceof RequestFacade) {
        	// TODO
//            return ((RequestFacade) current).newPushBuilder(this);
        	return null;
        } else {
            return null;
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// HttpServletRequest Methods
	// -------------------------------------------------------------------------------------
    public void recycle() {
        if (session != null) {
            session.endAccess();
        }
    }

    /**
     * 设置此请求的上下文路径
     *
     * @param contextPath - 新的上下文路径
     */
    void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * 为这个请求设置路径信息
     *
     * @param pathInfo - 新的路径信息
     */
    void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    /**
     * 设置此请求的查询字符串
     *
     * @param queryString - 新的查询字符串
     */
    void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    Object getRequestDispatcherPath() {
		return requestDispatcherPath;
	}

	/**
     * 设置要包装的请求
     *
     * @param request - 新的包装请求
     */
    void setRequest(HttpServletRequest request) {
        super.setRequest(request);

        // 初始化此请求的属性
        dispatcherType = (DispatcherType)request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
        requestDispatcherPath = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);

        // 初始化此请求的路径元素
        contextPath = request.getContextPath();
        pathInfo = request.getPathInfo();
        queryString = request.getQueryString();
        requestURI = request.getRequestURI();
        servletPath = request.getServletPath();
        mapping = request.getHttpServletMapping();
    }

    /**
     * 设置此请求的请求URI
     *
     * @param requestURI - 新的请求URI
     */
    void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    /**
     * 为这个请求设置servlet路径
     *
     * @param servletPath - 新的servlet路径
     */
    void setServletPath(String servletPath) {
        this.servletPath = servletPath;
    }
    
    /**
     * 解析此请求的参数
     * <p>
     * 如果查询字符串和请求内容中都有参数，则将它们合并
     */
    void parseParameters() {
        if (parsedParams) {
            return;
        }

        parameters = new ParameterMap<>();
        parameters.putAll(getRequest().getParameterMap());
        mergeParameters();
        ((ParameterMap<String,String[]>) parameters).setLocked(true);
        parsedParams = true;
    }


    /**
     * 保存此请求的查询参数
     *
     * @param queryString - 包含此请求参数的查询字符串
     */
    void setQueryParams(String queryString) {
        this.queryParamString = queryString;
    }


    void setMapping(HttpServletMapping mapping) {
        this.mapping = mapping;
    }
    
    
	// -------------------------------------------------------------------------------------
	// Protected Methods
	// -------------------------------------------------------------------------------------
    /**
     * 这个属性名是仅为包含的servlet添加的特殊属性名之一吗?
     *
     * @param name - 要测试的属性名
     */
    protected boolean isSpecial(String name) {
        for (String special : specials) {
            if (special.equals(name))
                return true;
        }
        return false;
    }

    /**
     *	@param name - 获取一个特殊属性
     * @return 特殊属性pos，如果不是特殊属性则为-1
     */
    protected int getSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 设置一个特殊属性
     *
     * @return 如果属性是一个特殊属性，则为true，否则为false
     */
    protected boolean setSpecial(String name, Object value) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = value;
                return true;
            }
        }
        return false;
    }

    /**
     * 删除一个特殊属性
     *
     * @return 如果属性是一个特殊属性，则为true，否则为false
     */
    protected boolean removeSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = null;
                return true;
            }
        }
        return false;
    }

    
	// -------------------------------------------------------------------------------------
	// Private Methods
	// -------------------------------------------------------------------------------------
    /**
     * 合并保存的查询参数字符串中的参数(如果有)和此请求中已存在的参数(如果有)，以便如果存在重复的参数名称，则首先显示查询字符串中的参数值。
     */
    private void mergeParameters() {
        if ((queryParamString == null) || (queryParamString.length() < 1))
            return;

        // 解析来自调度目标的查询字符串
        Parameters paramParser = new Parameters();
        MessageBytes queryMB = MessageBytes.newInstance();
        queryMB.setString(queryParamString);

        /*
         * 只有在useBodyEncodingForURI为true时才应使用正文编码。否则，应该使用URIEncoding
         * 
         */
        String encoding = getCharacterEncoding();
        Charset charset = null;
        if (encoding != null) {
            charset = Charset.forName(encoding);
			queryMB.setCharset(charset);
        }

        paramParser.setQuery(queryMB);
        paramParser.setQueryStringCharset(charset);
        paramParser.handleQueryParameters();

        // 插入分派目标中的其他参数
        Enumeration<String> dispParamNames = paramParser.getParameterNames();
        while (dispParamNames.hasMoreElements()) {
            String dispParamName = dispParamNames.nextElement();
            String[] dispParamValues = paramParser.getParameterValues(dispParamName);
            String[] originalValues = parameters.get(dispParamName);
            if (originalValues == null) {
                parameters.put(dispParamName, dispParamValues);
                continue;
            }
            parameters.put(dispParamName, mergeValues(dispParamValues, originalValues));
        }
    }
    
    /**
     * 将两组参数值合并到一个String数组中
     *
     * @param values1 - 第一组值
     * @param values2 - 第二组值
     */
    private String[] mergeValues(String[] values1, String[] values2) {
        List<Object> results = new ArrayList<>();

        if (values1 == null) {
            // 跳过-没有东西合并
        } else {
            results.addAll(Arrays.asList(values1));
        }

        if (values2 == null) {
            // 跳过-没有东西合并
        } else {
            results.addAll(Arrays.asList(values2));
        }

        String values[] = new String[results.size()];
        return results.toArray(values);
    }
    
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    /**
     * 实用程序类，用于将特殊属性公开为可用的请求属性
     */
    protected class AttributeNamesEnumerator implements Enumeration<String> {
        protected int pos = -1;
        protected final int last;
        protected final Enumeration<String> parentEnumeration;
        protected String next = null;

        public AttributeNamesEnumerator() {
            int last = -1;
            parentEnumeration = getRequest().getAttributeNames();
            for (int i = specialAttributes.length - 1; i >= 0; i--) {
                if (getAttribute(specials[i]) != null) {
                    last = i;
                    break;
                }
            }
            this.last = last;
        }

        @Override
        public boolean hasMoreElements() {
            return ((pos != last) || (next != null) || ((next = findNext()) != null));
        }

        @Override
        public String nextElement() {
            if (pos != last) {
                for (int i = pos + 1; i <= last; i++) {
                    if (getAttribute(specials[i]) != null) {
                        pos = i;
                        return specials[i];
                    }
                }
            }
            String result = next;
            if (next != null) {
                next = findNext();
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }

        protected String findNext() {
            String result = null;
            while ((result == null) && (parentEnumeration.hasMoreElements())) {
                String current = parentEnumeration.nextElement();
                if (!isSpecial(current)) {
                    result = current;
                }
            }
            return result;
        }
    }
}
