package org.zy.moonstone.core.servlets;

import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.connector.HttpRequest;
import org.zy.moonstone.core.connector.HttpResponse;
import org.zy.moonstone.core.connector.RequestFacade;
import org.zy.moonstone.core.connector.ResponseFacade;
import org.zy.moonstone.core.container.StandardWrapper;
import org.zy.moonstone.core.exceptions.ClientAbortException;
import org.zy.moonstone.core.filter.ApplicationFilterChain;
import org.zy.moonstone.core.filter.ApplicationFilterFactory;
import org.zy.moonstone.core.interfaces.container.AsyncDispatcher;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.container.Wrapper;
import org.zy.moonstone.core.util.ExceptionUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * @dateTime 2022年9月29日;
 * @author zy(azurite-Y);
 * @description
 * RequestDispatcher的标准实现，允许将请求转发到不同的资源以创建最终响应，或将另一个资源的输出包括在来自该资源的响应中。
 * 此实现允许应用程序级Servlet包装传递给调用资源的请求和/或响应对象，
 * 只要包装类扩展了 {@link javax.servlet.ServletRequestWrapper } 和 {@link javax.servlet.ServletResponseWrapper}。
 */
//@SuppressWarnings("unused")
public class ApplicationDispatcher implements AsyncDispatcher, RequestDispatcher {
    static final boolean STRICT_SERVLET_COMPLIANCE;

    public static final boolean WRAP_SAME_OBJECT;
    
    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String wrapSameObject = System.getProperty("org.zy.moonstone.core.servlets.ApplicationDispatcher.WRAP_SAME_OBJECT");
        if (wrapSameObject == null) {
            WRAP_SAME_OBJECT = STRICT_SERVLET_COMPLIANCE;
        } else {
            WRAP_SAME_OBJECT = Boolean.parseBoolean(wrapSameObject);
        }
    }
    
	/** 此RequestDispatcher关联的上下文 */
	private final Context context;

	/** 命名调度程序的Servlet名称 */
	private final String name;

	/** 此RequestDispatcher的额外路径信息 */
	private final String pathInfo;

	/** 此RequestDispatcher的查询字符串参数 */
	private final String queryString;

	/** 此RequestDispatcher的请求URI */
	private final String requestURI;

	/** 此RequestDispatcher的Servlet路径 */
	private final String servletPath;

	/** 此RequestDispatcher的映射 */
	private final HttpServletMapping mapping;

	/** 与要转发或包含的资源相关联的包装器 */
	private final Wrapper wrapper;


	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 构造此类的新实例，并根据指定的参数进行配置。如果servletPath和pathInfo都为空，则将假定此RequestDispatcher是通过名称而不是路径获取的。
	 *
	 * @param wrapper - 与将被转发或包含的资源相关联的包装器(必需的)
	 * @param requestURI - 指向此资源的请求URI(如果有)
	 * @param servletPath - 此资源的修订后的Servlet路径(如果有)
	 * @param pathInfo - 修改后的指向此资源的额外路径信息(如果有)
	 * @param queryString - 此请求中包含的查询字符串参数(如果有)
	 * @param mapping - 此资源的映射(如果有的话)
	 * @param name - Servlet名称（如果创建了命名的调度程序）否则为null
	 */
	public ApplicationDispatcher(Wrapper wrapper, String requestURI, String servletPath, String pathInfo, String queryString, HttpServletMapping mapping, String name) {
		super();

		// 保存所有配置参数
		this.wrapper = wrapper;
		this.context = (Context) wrapper.getParent();
		this.requestURI = requestURI;
		this.servletPath = servletPath;
		this.pathInfo = pathInfo;
		this.queryString = queryString;
		this.mapping = mapping;
		this.name = name;
	}


	// -------------------------------------------------------------------------------------
	// 实现方法
	// -------------------------------------------------------------------------------------
	/**
	 * 将请求从servlet转发到服务器上的另一个资源(servlet、JSP文件或html文件)。该方法允许一个servlet对请求进行初步处理，另一个资源生成响应。
	 * <p>
	 * 对于通过 <code>getRequestDispatcher()</code> 获得的 <code>RequestDispatcher</code>, <code>ServletRequest</code>
	 * 对象的路径元素和参数进行了调整，以匹配目标资源的路径。
	 * <p>
	 * Forward应该在响应提交给客户端之前调用(在响应体输出被刷新之前)。如果响应已经被提交，这个方法抛出一个IllegalStateException。
	 * 响应缓冲区中未提交的输出会在转发之前自动清除。
	 * <p>
	 * 请求和响应参数必须是传递给调用servlet的服务方法的相同对象，或者是包装它们的 {@link ServletRequestWrapper} 或 {@link ServletResponseWrapper} 类的子类。
	 * <p>
	 * 该方法将给定请求的调度程序类型设置为<code>DispatcherType.FORWARD</code>。
	 * 
	 * @param request - 一个{@link ServletRequest}对象，表示客户端对servlet的请求
	 * @param response - {@link ServletResponse}对象，表示servlet返回给客户端的响应
	 * @throws ServletException - 如果目标资源抛出此异常
	 * @thows IOException - 如果目标资源抛出此异常
	 */
	@Override
	public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		if (Globals.IS_SECURITY_ENABLED) {
			try {
				PrivilegedInclude dp = new PrivilegedInclude(request,response);
				AccessController.doPrivileged(dp);
			} catch (PrivilegedActionException pe) {
				Exception e = pe.getException();

				if (e instanceof ServletException) throw (ServletException) e;
				throw (IOException) e;
			}
		} else {
			doForward(request, response);
		}
	}
	

	/**
	 * 在响应中包含资源(servlet、JSP页面、HTML文件)的内容。本质上，这个方法支持编程的服务器端包含。
	 * <p>
	 * ServletResponse对象的路径元素和参数与调用者的保持不变。所包含的servlet不能更改响应状态代码或设置标头;任何更改的尝试都会被忽略。
	 * <p>
	 * 请求和响应参数必须是传递给调用servlet的服务方法的相同对象，或者是包装它们的ServletRequestWrapper或ServletResponseWrapper类的子类。
	 * <p>
	 * 该方法将给定请求的调度程序类型设置为DispatcherType.INCLUDE。
	 * 
	 * @param request - 包含客户端请求的{@link ServletRequest}对象
	 * @param response - 一个包含servlet响应的{@link ServletResponse}对象
	 * @throws ServletException - 如果包含的资源抛出此异常
	 * @throws IOException - 如果所包含的资源抛出此异常
	 */
	@Override
	public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		if (Globals.IS_SECURITY_ENABLED) {
			try {
				PrivilegedInclude dp = new PrivilegedInclude(request,response);
				AccessController.doPrivileged(dp);
			} catch (PrivilegedActionException pe) {
				Exception e = pe.getException();

				if (e instanceof ServletException) throw (ServletException) e;
				throw (IOException) e;
			}
		} else {
			doInclude(request, response);
		}
	}
	
	
	@Override
	public void dispatch(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		if (Globals.IS_SECURITY_ENABLED) {
			try {
				PrivilegedDispatch dp = new PrivilegedDispatch(request,response);
				AccessController.doPrivileged(dp);
			} catch (PrivilegedActionException pe) {
				Exception e = pe.getException();

				if (e instanceof ServletException) throw (ServletException) e;
				throw (IOException) e;
			}
		} else {
			doDispatch(request, response);
		}
	}

	
	// -------------------------------------------------------------------------------------
	// 私有方法
	// -------------------------------------------------------------------------------------
	private void checkSameObjects(ServletRequest appRequest, ServletResponse appResponse) throws ServletException {
        ServletRequest originalRequest = ApplicationFilterChain.getLastServicedRequest();
        ServletResponse originalResponse = ApplicationFilterChain.getLastServicedResponse();

        // 有些转发(如来自Valve)将不设置原始值
        if (originalRequest == null || originalResponse == null) {
            return;
        }

        boolean same = false;
        ServletRequest dispatchedRequest = appRequest;

        // 查找传递到服务方法中的请求
        while (originalRequest instanceof ServletRequestWrapper && ((ServletRequestWrapper) originalRequest).getRequest()!=null ) {
            originalRequest = ((ServletRequestWrapper) originalRequest).getRequest();
        }
        // 与已分派的请求比较
        while (!same) {
            if (originalRequest.equals(dispatchedRequest)) {
                same = true;
            }
            if (!same && dispatchedRequest instanceof ServletRequestWrapper) {
                dispatchedRequest = ((ServletRequestWrapper) dispatchedRequest).getRequest();
            } else {
                break;
            }
        }
        if (!same) {
            throw new ServletException("违反规范的请求");
        }

        same = false;
        ServletResponse dispatchedResponse = appResponse;

        // 找到传递到服务方法中的响应
        while (originalResponse instanceof ServletResponseWrapper && ((ServletResponseWrapper) originalResponse).getResponse() != null ) {
            originalResponse = ((ServletResponseWrapper) originalResponse).getResponse();
        }
        // 与调度的响应进行比较
        while (!same) {
            if (originalResponse.equals(dispatchedResponse)) {
                same = true;
            }

            if (!same && dispatchedResponse instanceof ServletResponseWrapper) {
                dispatchedResponse = ((ServletResponseWrapper) dispatchedResponse).getResponse();
            } else {
                break;
            }
        }

        if (!same) {
            throw new ServletException("违反规范的响应");
        }
    }
	
	
	/**
	 * 创建并返回已插入请求链中适当位置的请求包装
     */
    private ServletRequest wrapRequest(State state) {
        // 找到要插入的请求
        ServletRequest previous = null;
        ServletRequest current = state.outerRequest;
        while (current != null) {
        	if(state.httpServletRequest == null && (current instanceof HttpServletRequest))
                state.httpServletRequest = (HttpServletRequest)current;
            if (!(current instanceof ServletRequestWrapper))
                break;
            if (current instanceof ApplicationHttpRequest)
                break;
            if (current instanceof ApplicationRequest)
                break;
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }

        // 此时实例化一个新的包装器，并将其插入到链中
        ServletRequest wrapper = null;
        if ((current instanceof ApplicationHttpRequest) || (current instanceof HttpRequest) || (current instanceof HttpServletRequest)) {
            // 计算一个跨上下文标志
            HttpServletRequest hcurrent = (HttpServletRequest) current;
            boolean crossContext = false;
            if ((state.outerRequest instanceof ApplicationHttpRequest) || (state.outerRequest instanceof HttpRequest) || (state.outerRequest instanceof HttpServletRequest)) {
                HttpServletRequest houterRequest = (HttpServletRequest) state.outerRequest;
                // RequestDispatcher.INCLUDE_CONTEXT_PATH: 请求属性的名称，在该属性下存储包含目标的上下文路径
                Object contextPath = houterRequest.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH);
                if (contextPath == null) {
                    // Forward
                    contextPath = houterRequest.getContextPath();
                }
                crossContext = !(context.getPath().equals(contextPath));
            }
            wrapper = new ApplicationHttpRequest(hcurrent, context, crossContext);
        } else {
            wrapper = new ApplicationRequest(current);
        }
        
        if (previous == null) {
        	state.outerRequest = wrapper;
        } else {
        	((ServletRequestWrapper) previous).setRequest(wrapper);
        }
        state.wrapperRequest = wrapper;
        return wrapper;
    }

    
    /**
     * 创建并返回已插入响应链中适当位置的响应包装
     */
	private ServletResponse wrapResponse(State state) {
        // 找到应该在前面插入的响应
        ServletResponse previous = null;
        ServletResponse current = state.outerResponse;
        while (current != null) {
            if(state.httpServletResponse == null && (current instanceof HttpServletResponse)) {
                state.httpServletResponse = (HttpServletResponse)current;
                if(!state.including) // 转发只需要响应
                    return null;
            }
            
            if (!(current instanceof ServletResponseWrapper))
                break;
            if (current instanceof ApplicationHttpResponse)
                break;
            if (current instanceof ApplicationResponse)
                break;
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }

        // 此时实例化一个新的包装器，并将其插入到链中
        ServletResponse wrapper = null;
        if ((current instanceof ApplicationHttpResponse) || (current instanceof HttpResponse) || (current instanceof HttpServletResponse)) {
        	wrapper = new ApplicationHttpResponse((HttpServletResponse) current, state.including);
        } else {
        	wrapper = new ApplicationResponse(current, state.including);
        }
        
        if (previous == null) {
        	state.outerResponse = wrapper;
        } else {
        	((ServletResponseWrapper) previous).setResponse(wrapper);
        }
        state.wrapperResponse = wrapper;
        return wrapper;
    }
	
	
	private void doInclude(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        // 设置以处理指定的请求和响应
        State state = new State(request, response, true);

        if (WRAP_SAME_OBJECT) {
            // 检查SRV.8.2 / SRV.14.2.5.1符合性
            checkSameObjects(request, response);
        }

        // 创建用于此请求的包装响应
        wrapResponse(state);

        // 处理HTTP命名的 include 调度程序
        if (name != null) {
        	ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
        	wrequest.setAttribute(Globals.NAMED_DISPATCHER_ATTR, name);
        	
        	if (servletPath != null)
        		wrequest.setServletPath(servletPath);
        	
        	wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.INCLUDE);
        	wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, getCombinedPath());
        	invoke(state.outerRequest, state.outerResponse, state);
        } else {// 处理基于HTTP路径的 include
            ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
            String contextPath = context.getPath();
            if (requestURI != null)
            	// RequestDispatcher.INCLUDE_REQUEST_URI: 请求属性的名称，在该属性下存储包含目标的请求URI
                wrequest.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, requestURI);
            
            
            if (contextPath != null)
            	// RequestDispatcher.INCLUDE_CONTEXT_PATH: 请求属性的名称，在该属性下存储包含目标的上下文路径
                wrequest.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH, contextPath);
            
            if (servletPath != null)
            	// RequestDispatcher.INCLUDE_SERVLET_PATH: 请求属性的名称，在该属性下存储包含目标的servlet路径
                wrequest.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, servletPath);
            
            if (pathInfo != null)
            	// RequestDispatcher.INCLUDE_PATH_INFO: 请求属性的名称，在该属性下存储包含目标的路径信息
                wrequest.setAttribute(RequestDispatcher.INCLUDE_PATH_INFO, pathInfo);
            
            if (queryString != null) {
            	// RequestDispatcher.INCLUDE_QUERY_STRING: 请求属性的名称，在该属性下存储包含目标的查询字符串
                wrequest.setAttribute(RequestDispatcher.INCLUDE_QUERY_STRING, queryString);
                wrequest.setQueryParams(queryString);
            }
            if (mapping != null) {
            	// RequestDispatcher.INCLUDE_MAPPING: 请求属性的名称，包含目标的 javax.servlet.http.HttpServletMapping 存储在该属性下
                wrequest.setAttribute(RequestDispatcher.INCLUDE_MAPPING, mapping);
            }

            wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.INCLUDE);
            wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, getCombinedPath());
            invoke(state.outerRequest, state.outerResponse, state);
        }
    }
	
	
	private void doDispatch(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        // 设置以处理指定的请求和响应
        State state = new State(request, response, false);

        // 创建用于此请求的包装响应
        wrapResponse(state);

        ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
        HttpServletRequest hrequest = state.httpServletRequest;

        wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ASYNC);
        wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, getCombinedPath());
        // AsyncContext.ASYNC_MAPPING: 请求属性的名称，在该名称下原始的 javax.servlet.http.HttpServletMapping 对 dispatch(String) 或 dispatch(ServletContext, String) 的目标可用。
        wrequest.setAttribute(AsyncContext.ASYNC_MAPPING, hrequest.getHttpServletMapping());

        wrequest.setContextPath(context.getEncodedPath());
        wrequest.setRequestURI(requestURI);
        wrequest.setServletPath(servletPath);
        wrequest.setPathInfo(pathInfo);
        if (queryString != null) {
            wrequest.setQueryString(queryString);
            wrequest.setQueryParams(queryString);
        }
        if (!Globals.STRICT_SERVLET_COMPLIANCE) {
            wrequest.setMapping(mapping);
        }

        invoke(state.outerRequest, state.outerResponse, state);
    }
	
	
	private void doForward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		// 重置任何已缓冲的输出，但保留headers/cookie
		if (response.isCommitted()) {
			throw new IllegalStateException("响应已提交");
		}

		try {
			response.resetBuffer();
		} catch (IllegalStateException e) {
			throw e;
		}

		// 设置以处理指定的请求和响应
		State state = new State(request, response, false);
		if (WRAP_SAME_OBJECT) {
            // 检查SRV.8.2/SRV.14.2.5.1合规性
            checkSameObjects(request, response);
        }

        wrapResponse(state);
        // 处理HTTP命名的调度程序转发
        if ((servletPath == null) && (pathInfo == null)) {
            ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
            HttpServletRequest hrequest = state.httpServletRequest;
            wrequest.setRequestURI(hrequest.getRequestURI());
            wrequest.setContextPath(hrequest.getContextPath());
            wrequest.setServletPath(hrequest.getServletPath());
            wrequest.setPathInfo(hrequest.getPathInfo());
            wrequest.setQueryString(hrequest.getQueryString());

            processRequest(request,response,state);
        } else {
        	// 处理基于HTTP路径的转发
            ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
            HttpServletRequest hrequest = state.httpServletRequest;
            if (hrequest.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) == null) {
            	// RequestDispatcher.FORWARD_REQUEST_URI: 请求属性的名称，转发的目标可以在该属性下使用原始请求URI
                wrequest.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, hrequest.getRequestURI());
                
                // RequestDispatcher.FORWARD_CONTEXT_PATH: 请求属性的名称，转发的目标可以在该属性下使用原始上下文路径
                wrequest.setAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH, hrequest.getContextPath());
                
                // RequestDispatcher.FORWARD_SERVLET_PATH: 请求属性的名称，转发的目标可以在该属性下使用原始servlet路径
                wrequest.setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH, hrequest.getServletPath());
                
                // RequestDispatcher.FORWARD_PATH_INFO: 请求属性的名称，转发的目标可以在该属性下获得原始路径信息
                wrequest.setAttribute(RequestDispatcher.FORWARD_PATH_INFO, hrequest.getPathInfo());
                
                // RequestDispatcher.FORWARD_QUERY_STRING: 请求属性的名称，在该属性下原始查询字符串可用于转发的目标
                wrequest.setAttribute(RequestDispatcher.FORWARD_QUERY_STRING, hrequest.getQueryString());
                
                // RequestDispatcher.FORWARD_MAPPING: 请求属性的名称，在该属性下原始的 javax.servlet.http.HttpServletMapping 对转发的目标可用
                wrequest.setAttribute(RequestDispatcher.FORWARD_MAPPING, hrequest.getHttpServletMapping());
            }

            wrequest.setContextPath(context.getEncodedPath());
            wrequest.setRequestURI(requestURI);
            wrequest.setServletPath(servletPath);
            wrequest.setPathInfo(pathInfo);
            if (queryString != null) {
                wrequest.setQueryString(queryString);
                wrequest.setQueryParams(queryString);
            }
            wrequest.setMapping(mapping);

            processRequest(request,response,state);
        }

        if (request.isAsyncStarted()) {
            // 异步请求在转发期间启动，不要关闭响应，因为它可能在异步处理期间被写入
            return;
        }

        // 为了支持错误处理，这不是一个真正的关闭
        if (wrapper.getLogger().isDebugEnabled() )
            wrapper.getLogger().debug("禁用响应以进一步输出.");

        if  (response instanceof ResponseFacade) {
            ((ResponseFacade) response).finish();
        } else {
            // Servlet SRV.6.2.2。请求/响应可能已经被包装，可能不再是RequestFacade的实例
            if (wrapper.getLogger().isDebugEnabled()){
                wrapper.getLogger().debug( "使用包装器装载响应: " + response.getClass().getName() );
            }

            // 仍要关闭
            try {
                PrintWriter writer = response.getWriter();
                writer.close();
            } catch (IllegalStateException e) {
                try {
                    ServletOutputStream stream = response.getOutputStream();
                    stream.close();
                } catch (IllegalStateException f) {
                    // Ignore
                } catch (IOException f) {
                    // Ignore
                }
            } catch (IOException e) {
                // Ignore
            }
        }
	}
	
	
	/**
	 * 组合Servlet路径和 pathInfo。如果pathInfo为 <code>null</code>，它将被忽略。如果Servlet路径为<code>null</code>，则返回<code>null</code>。
	 * 
     * @return 将pathInfo附加到servletInfo的组合路径
     */
    private String getCombinedPath() {
        if (servletPath == null) {
            return null;
        }
        if (pathInfo == null) {
            return servletPath;
        }
        return servletPath + pathInfo;
    }
	
    
    /**
     * 根据过滤器配置准备请求
     * 
     * @param request - 正在处理的servlet请求
     * @param response - 正在创建的servlet响应
     * @param state - RD状态
     *
     * @exception IOException - 如果发生输入/输出错误
     * @exception ServletException - 如果发生servlet错误
     */
    private void processRequest(ServletRequest request, ServletResponse response, State state) throws IOException, ServletException {
        DispatcherType disInt = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
        if (disInt != null) {
            boolean doInvoke = true;
            if (context.getFireRequestListenersOnForwards() && !context.fireRequestInitEvent(request)) {
                doInvoke = false;
            }

            if (doInvoke) {
                if (disInt != DispatcherType.ERROR) {
                    state.outerRequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, getCombinedPath());
                    state.outerRequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.FORWARD);
                    invoke(state.outerRequest, response, state);
                } else {
                    invoke(state.outerRequest, response, state);
                }

                if (context.getFireRequestListenersOnForwards()) {
                    context.fireRequestDestroyEvent(request);
                }
            }
        }
    }
    
    
	/**
	  * 请求此RequestDispatcher表示的资源处理相关请求，并创建（或附加）相关响应。
	  * 
	 * <p>
	 * <strong>实现说明</strong>: 
	 * 此实现假定没有筛选器应用于转发或包含的资源，因为它们已经针对原始请求进行了筛选。
	 *
	 * @param request - 正在处理的servlet请求
     * @param response - 正在创建的servlet响应
	 *
     * @exception IOException - 如果发生输入/输出错误
     * @exception ServletException - 如果发生servlet错误
	 */
	private void invoke(ServletRequest request, ServletResponse response, State state) throws IOException, ServletException {
	    // 检查上下文类加载器是否为当前上下文类加载器。如果不是，则保存它，并将上下文类加载器设置为context类加载器
	    ClassLoader oldCCL = context.bind(false, null);
	
	    // 初始化可能需要的局部变量
	    HttpServletResponse hresponse = state.httpServletResponse;
	    Servlet servlet = null;
	    IOException ioException = null;
	    ServletException servletException = null;
	    RuntimeException runtimeException = null;
	    boolean unavailable = false;
	
	    // 检查servlet是否标记为不可用
	    if (wrapper.isUnavailable()) {
	        wrapper.getLogger().warn("不可用的 Wrapper, by wrapperName: {}", wrapper.getName());
	        long available = wrapper.getAvailable();
	        if ((available > 0L) && (available < Long.MAX_VALUE)) {
	        	// 设置用户代理需要等待多长时间之后才能继续发送请求
	        	hresponse.setDateHeader("Retry-After", available);
	        }
	        hresponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "不可用的 Wrapper, by wrapperName: " + wrapper.getName());
	        unavailable = true;
	    }
	
	    // 分配一个Servlet实例来处理此请求
	    try {
	        if (!unavailable) {
	            servlet = wrapper.allocate();
	        }
	    } catch (ServletException e) {
	        wrapper.getLogger().error("Servlet 分配异常, by name: " + wrapper.getName(), e);
	        servletException = e;
	    } catch (Throwable e) {
	        ExceptionUtils.handleThrowable(e);
	        wrapper.getLogger().error("Servlet 分配异常, by name: " + wrapper.getName(), e);
	        servletException = new ServletException("Servlet 分配异常, by name: " + wrapper.getName());
	        servlet = null;
	    }
	
	    // 在此处获取过滤器链
	    ApplicationFilterChain filterChain = ApplicationFilterFactory.createFilterChain(request, wrapper, servlet);
	
	    // 为分配的servlet实例调用service()方法
	    try {
	        // 对于 includes/forwards
	        if ((servlet != null) && (filterChain != null)) {
	           filterChain.doFilter(request, response);
	         }
	        // Servlet的service()方法由FilterChain调用
	    } catch (ClientAbortException e) {
	        ioException = e;
	    } catch (IOException e) {
	        wrapper.getLogger().error("service() 方法调用异常, by name: " + wrapper.getName(), e);
	        ioException = e;
	    } catch (UnavailableException e) {
	    	wrapper.getLogger().error("service() 方法调用异常, by name: " + wrapper.getName(), e);
	        servletException = e;
	        wrapper.unavailable(e);
	    } catch (ServletException e) {
	        Throwable rootCause = StandardWrapper.getRootCause(e);
	        if (!(rootCause instanceof ClientAbortException)) {
	        	wrapper.getLogger().error("service() 方法调用异常, by name: " + wrapper.getName(), e);
	        }
	        servletException = e;
	    } catch (RuntimeException e) {
	    	wrapper.getLogger().error("service() 方法调用异常, by name: " + wrapper.getName(), e);
	        runtimeException = e;
	    }
	
	    // 释放此请求的过滤器链(如果有的话)
	    if (filterChain != null) {
	        filterChain.release();
	    }
	
	    // 释放分配的servlet实例
	    try {
	        if (servlet != null) {
	            wrapper.deallocate(servlet);
	        }
	    } catch (ServletException e) {
	        wrapper.getLogger().error("释放异常, by wrapperName: " + wrapper.getName(), e);
	        servletException = e;
	    } catch (Throwable e) {
	        ExceptionUtils.handleThrowable(e);
	        wrapper.getLogger().error("释放异常, by wrapperName: " + wrapper.getName(), e);
	        servletException = new ServletException("释放异常, by wrapperName: " + wrapper.getName(), e);
	    }
	
	    // 重置旧的上下文类加载器
	    context.unbind(false, oldCCL);
	
	    // 如果需要，打开请求/响应
	    // See Bugzilla 30949
	    unwrapRequest(state);
	    unwrapResponse(state);
	    // 如有需要，回收要求
	    recycleRequestWrapper(state);
	
	    // 如果被调用的servlet抛出异常，则重新抛出异常
	    if (ioException != null)
	        throw ioException;
	    if (servletException != null)
	        throw servletException;
	    if (runtimeException != null)
	        throw runtimeException;
	}

	
	/**
     * 如果已经对请求进行了包装，则将其展开
     */
    private void unwrapRequest(State state) {
        if (state.wrapperRequest == null)
            return;

        if (state.outerRequest.isAsyncStarted()) {
            if (!state.outerRequest.getAsyncContext().hasOriginalRequestAndResponse()) {
                return;
            }
        }

        ServletRequest previous = null;
        ServletRequest current = state.outerRequest;
        while (current != null) {
            // 如果遇到容器请求，就算完成了
            if ((current instanceof HttpRequest) || (current instanceof RequestFacade))
                break;

            // 如果当前请求是包装器，则删除它
            if (current == state.wrapperRequest) {
                ServletRequest next = ((ServletRequestWrapper) current).getRequest();
                if (previous == null)
                    state.outerRequest = next;
                else
                    ((ServletRequestWrapper) previous).setRequest(next);
                break;
            }

            // 前进到链中的下一个请求
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }
    }

    
    /**
     * 如果已经对响应进行了包装，则将其展开
     */
    private void unwrapResponse(State state) {
        if (state.wrapperResponse == null)
            return;

        if (state.outerRequest.isAsyncStarted()) {
            if (!state.outerRequest.getAsyncContext().hasOriginalRequestAndResponse()) {
                return;
            }
        }

        ServletResponse previous = null;
        ServletResponse current = state.outerResponse;
        while (current != null) {
            // 如果遇到容器响应，就算完成了
            if ((current instanceof HttpResponse) || (current instanceof ResponseFacade))
                break;

            // 如果当前响应是包装器，则删除它
            if (current == state.wrapperResponse) {
                ServletResponse next = ((ServletResponseWrapper) current).getResponse();
                if (previous == null)
                    state.outerResponse = next;
                else
                    ((ServletResponseWrapper) previous).setResponse(next);
                break;
            }

            // 前进到链中的下一个响应
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }
    }
    

    private void recycleRequestWrapper(State state) {
        if (state.wrapperRequest instanceof ApplicationHttpRequest) {
            ((ApplicationHttpRequest) state.wrapperRequest).recycle();
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
	protected class PrivilegedForward implements PrivilegedExceptionAction<Void> {
		private final ServletRequest request;
		private final ServletResponse response;

		PrivilegedForward(ServletRequest request, ServletResponse response) {
			this.request = request;
			this.response = response;
		}

		@Override
		public Void run() throws java.lang.Exception {
			doForward(request,response);
			return null;
		}
	}

	protected class PrivilegedInclude implements PrivilegedExceptionAction<Void> {
		private final ServletRequest request;
		private final ServletResponse response;

		PrivilegedInclude(ServletRequest request, ServletResponse response) {
			this.request = request;
			this.response = response;
		}

		@Override
		public Void run() throws ServletException, IOException {
			doInclude(request, response);
			return null;
		}
	}

	protected class PrivilegedDispatch implements PrivilegedExceptionAction<Void> {
		private final ServletRequest request;
		private final ServletResponse response;

		PrivilegedDispatch(ServletRequest request, ServletResponse response) {
			this.request = request;
			this.response = response;
		}

		@Override
		public Void run() throws ServletException, IOException {
			doDispatch(request, response);
			return null;
		}
	}

	
	 /**
	 * 当使用请求调度程序时，用于传递状态。使用实例变量会导致线程问题，状态太复杂，无法传递和返回单个ServletRequest或ServletResponse对象。
	 */
	private static class State {
		State(ServletRequest request, ServletResponse response, boolean including) {
			this.outerRequest = request;
			this.outerResponse = response;
			this.including = including;
		}

		/** 将被传递到被调用servlet的最外层请求 */
		ServletRequest outerRequest = null;

		/** 将传递给被调用servlet的最外层响应 */
		ServletResponse outerResponse = null;

		/** 已经创建和安装的请求包装器(如果有的话) */
		ServletRequest wrapperRequest = null;

		/** 创建和安装的响应包装器(如果有的话) */
		ServletResponse wrapperResponse = null;

		/** 执行的是include()而不是forward()吗? */
		boolean including = false;

		/** 链中最外层的HttpServletRequest */
		HttpServletRequest httpServletRequest = null;

		/** 链中最外层的HttpServletResponse */
		HttpServletResponse httpServletResponse = null;
	}
}
