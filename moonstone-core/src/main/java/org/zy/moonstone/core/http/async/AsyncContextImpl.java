package org.zy.moonstone.core.http.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.connector.HttpRequest;
import org.zy.moonstone.core.container.valves.StandardHostValve;
import org.zy.moonstone.core.http.Request;
import org.zy.moonstone.core.http.Response;
import org.zy.moonstone.core.interfaces.container.*;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.http.ActionCode;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @dateTime 2022年12月4日;
 * @author zy(azurite-Y);
 * @description
 */
public class AsyncContextImpl implements AsyncContext, AsyncContextCallback {
    private static final Logger logger = LoggerFactory.getLogger(AsyncContextImpl.class);
    
    /**
     * 当请求使用多个start()序列时;对于非容器线程，前一个Dispatch()可能会干扰后面的start()。
     * 这个锁可以防止这种情况发生。它是一个专用对象，因为用户代码可能锁定在AsyncContext上，所以如果容器代码也锁定在该对象上，就可能发生死锁。
     */
    private final Object asyncContextLock = new Object();

    private volatile ServletRequest servletRequest = null;
    private volatile ServletResponse servletResponse = null;
    private final List<AsyncListenerWrapper> listeners = new ArrayList<>();
    /** 是否用原始的请求和响应对象初始化 */
    private boolean hasOriginalRequestAndResponse = true;
    private volatile Runnable dispatch = null;
    private Context context = null;
    
    /** 缺省值为30000 (30s)由连接器设置 */
    private long timeout = -1;
    
    private AsyncEvent event = null;
    private volatile HttpRequest httpRequest;
    
    
    public AsyncContextImpl(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }
    
	@Override
	public void fireOnComplete() {
		if (logger.isDebugEnabled()) {
			logger.debug("AsyncContextImpl.fireOnComplete");
        }
        List<AsyncListenerWrapper> listenersCopy = new ArrayList<>(listeners);

        ClassLoader oldCL = context.bind(Globals.IS_SECURITY_ENABLED, null);
        try {
            for (AsyncListenerWrapper listener : listenersCopy) {
                try {
                    listener.fireOnComplete(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    logger.warn("调用监听器" + listener.getClass().getName() + ".fireOnComplete(AsyncEvent) 触发异常" , t);
                }
            }
        } finally {
            context.fireRequestDestroyEvent(httpRequest.getHttpServletRequest());
            clearServletRequestResponse();
            this.context.decrementInProgressAsyncCount();
            context.unbind(Globals.IS_SECURITY_ENABLED, oldCL);
        }
	}
	
	public boolean timeout() {
		AtomicBoolean result = new AtomicBoolean();
		httpRequest.getRequest().action(ActionCode.ASYNC_TIMEOUT, result);
		// 避免关闭期间的npe。循环调用将使该字段为空。
		Context context = this.context;

		if (result.get()) {
			if (logger.isDebugEnabled()) {
				logger.debug("AsyncContextImpl#fireOnTimeout");
			}
			ClassLoader oldCL = context.bind(false, null);
			try {
				List<AsyncListenerWrapper> listenersCopy = new ArrayList<>(listeners);
				for (AsyncListenerWrapper listener : listenersCopy) {
					try {
						listener.fireOnTimeout(event);
					} catch (Throwable t) {
						ExceptionUtils.handleThrowable(t);
	                    logger.warn("调用监听器" + listener.getClass().getName() + ".fireOnTimeout(AsyncEvent)() 触发异常" , t);
					}
				}
				httpRequest.getRequest().action(ActionCode.ASYNC_IS_TIMINGOUT, result);
			} finally {
				context.unbind(false, oldCL);
			}
		}
		return !result.get();
	}
	
	@Override
	public boolean isAvailable() {
        Context context = this.context;
        if (context == null) {
            return false;
        }
        return context.getState().isAvailable();
	}
	
	/**
	 * 获取用于通过调用 {@link ServletRequest#startAsync() } 或 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)} 来初始化此AsyncContext的请求。
	 * 
	 * @return 用于初始化此AsyncContext的请求
	 * @exception IllegalStateException - 如果 {@link #complete} ，或者在异步循环中调用了任何 {@link #dispatch} 方法
	 */
	@Override
	public ServletRequest getRequest() {
        check();
        if (servletRequest == null) {
            throw new IllegalStateException("servletRequest 属性不能为null");
        }
        return servletRequest;
	}
	
	/**
	 * 获取用于通过调用ServletRequest初始化此AsyncContext的 {@link ServletRequest#startAsync() } 或 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)}
	 * 
	 * @return 用于初始化AsyncContext的响应
	 * @exception IllegalStateException - 如果 {@link #complete} ，或者在异步循环中调用了任何 {@link #dispatch} 方法
	 */
	@Override
	public ServletResponse getResponse() {
		check();
        if (servletResponse == null) {
            throw new IllegalStateException("servletResponse 不能为null");
        }
        return servletResponse;
	}
	
	/**
	 * 检查这个AsyncContext是否用原始的或应用程序包装的请求和响应对象初始化。
	 * <p>
	 * 在将请求放入异步模式之后，在出站方向调用的筛选器可能会使用此信息，以确定在入站调用期间添加的任何请求和/或响应包装器是否需要在异步操作期间保留，或者可能需要释放。
	 * 
	 * @return 如果这个AsyncContext是通过调用 {@link ServletRequest#startAsync()} 用原始请求和响应对象初始化的，
	 * 或者是通过调用 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)} 且ServletRequest和ServletResponse参数都不携带任何应用程序提供的包装器初始化的。
	 */
	@Override
	
	public boolean hasOriginalRequestAndResponse() {
        check();
        return hasOriginalRequestAndResponse;
	}
	
	public void doInternalDispatch() throws ServletException, IOException {
        try {
            Runnable runnable = dispatch;
            dispatch = null;
            runnable.run();
            if (!httpRequest.isAsync()) {
                fireOnComplete();
            }
        } catch (RuntimeException x) {
            // doInternalComplete(true);
            if (x.getCause() instanceof ServletException) {
                throw (ServletException)x.getCause();
            }
            if (x.getCause() instanceof IOException) {
                throw (IOException)x.getCause();
            }
            throw new ServletException(x);
        }
    }
	
	
	/**
	 * 将此asynccontext的请求和响应对象分派给servlet容器。
     * 
     * <p>
     * 如果异步循环是用 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)} 启动的，并且传递的请求是HttpServletRequest的实例，
     * 那么调度将发送到 {@link HttpServletRequest#getRequestURI} 返回的URI。否则，将发送到容器上次发送请求时请求的URI。
     *
     * <p>以下顺序说明了这将如何工作:
     * <pre>{@code
     * // 请求发送至 /url/A
     * AsyncContext ac = httpRequest.startAsync();
     * ...
     * ac.dispatch(); // 异步分派到 /url/A
     * 
     * // 请求到 /url/A 转发至 /url/B
     * httpRequest.getRequestDispatcher("/url/B").forward(httpRequest,response);
     * // 从转发调度的目标内启动异步操作
     * ac = httpRequest.startAsync();
     * ...
     * ac.dispatch(); // 异步派送至 /url/A
     * 
     * // 请求到 /url/A 转发至 /url/B
     * httpRequest.getRequestDispatcher("/url/B").forward(httpRequest,response);
     * // 从转发调度的目标内启动异步操作
     * ac = httpRequest.startAsync(httpRequest,response);
     * ...
     * ac.dispatch(); // 异步派送至 /url/B
     * }</pre>
     *
     * <p>
     * 此方法在将请求和响应对象传递给容器管理的线程后立即返回，在此线程上将执行分派操作。
     * 如果该方法在名为 <tt>startAsync</tt> 的容器发起的调度返回到容器之前被调用，那么调度操作将被延迟到容器发起的调度返回到容器之后。
     *
     * <p>
     * 请求的调度程序类型设置为DispatcherType.ASYNC。与转发调度不同，响应缓冲区和标头不会被重置，即使响应已经提交，也可以进行调度。
     * 
     * 请求的调度程序类型设置为<tt>DispatcherType.ASYNC</tt>。
     * 与 {@link RequestDispatcher#forward(ServletRequest, ServletResponse)} 不同，响应缓冲区和标头不会被重置，即使响应已经提交，调度也是合法的。
     *
     * <p>
     * 对请求和响应的控制被委托给调度目标，当调度目标完成执行时，响应将被关闭，除非 {@link ServletRequest#startAsync()} 或 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)}
     * 
     * <p>
     * 在这个方法执行过程中可能发生的任何错误或异常都必须被容器捕获并处理，如下所示:
     * <ol>
     * 		<li>调用它们的 {@link AsyncListener#onError onError} 方法，所有注册到这个AsyncContext为之创建的ServletRequest的 {@link AsyncListener} 实例，并通过 {@link AsyncEvent#getThrowable} 使捕获的Throwable可用。</li>
     * 		<li>如果没有一个监听器调用 {@link #complete} 或任何 {@link #dispatch} 方法，则执行一个错误分派，其状态码等于<tt>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</tt>，并使上面的Throwable作为 <tt>RequestDispatcher.ERROR_EXCEPTION</tt> 请求属性的值可用。</li>
     * 		<li>如果未找到匹配的错误页，或者错误页未调用 {@link #complete} 或任何 {@link #dispatch} 方法，请调用 {@link #complete}</li>
     * </ol>
     *
     * <p>
     * 每个异步周期最多只能有一个异步分派操作，由对 {@link ServletRequest#startAsync} 方法之一的调用启动。
     * 在同一同步周期内执行额外异步分派操作的任何尝试都将导致IllegalStateException。
     * 如果随后在已分派的请求上调用startAsync，则可以调用任何分派或{@link #complete}方法。
     *
     * @throws IllegalStateException - 如果调用了其中一个分派方法，并且在生成的分派期间未调用startAsync方法，或者如果调用了 {@link #complete}
     *
     * @see ServletRequest#getDispatcherType
     */
	@Override
	public void dispatch() {
		check();
        String path;
        String contextPath;
        ServletRequest servletRequest = getRequest();
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest sr = (HttpServletRequest) servletRequest;
            path = sr.getRequestURI();
            contextPath = sr.getContextPath();
        } else {
            path = httpRequest.getRequestURI();
            contextPath = httpRequest.getContextPath();
        }
        if (contextPath.length() > 1) {
            path = path.substring(contextPath.length());
        }
        if (!context.getDispatchersUseEncodedPaths()) {
            try {
				path = URLDecoder.decode(path, "UTF_8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
        }
        dispatch(path);		
	}
	
	/**
	 * 将此asynccontext的请求和响应对象分派到给定的<tt>path</tt>。
     *
     * <p>
     * 在初始化这个AsyncContext的 {@link ServletContext} 范围内，<tt>path</tt>参数的解释方式与 {@link ServletRequest#getRequestDispatcher(String)} 相同。
     * 路径参数的解释方式与 {@link ServletRequest#getRequestDispatcher(String)} 中相同，在初始化thisAsyncContext的ServletContext范围内。
     *
     * <p>
     * 请求的所有与路径相关的查询方法都必须反映调度目标，而 原始请求URI、上下文路径、路径信息、servlet路径和查询字符串可以从请求的 
     * {@link #ASYNC_REQUEST_URI}, {@link #ASYNC_CONTEXT_PATH}, {@link #ASYNC_PATH_INFO}, {@link #ASYNC_SERVLET_PATH} 
     * 和 {@link #ASYNC_QUERY_STRING} 属性中恢复。这些属性将始终反映原始的路径元素，即使是不重复的分派。
     *
     * <p>
     * 每个异步周期最多只能有一个异步分派操作，由对 {@link ServletRequest#startAsync} 方法之一的调用启动。
     * 在同一同步周期内执行额外异步分派操作的任何尝试都将导致IllegalStateException。
     * 如果随后在已分派的请求上调用startAsync，则可以调用任何分派或 {@link #complete} 方法。
     *
     * <p>有关其他详细信息，包括错误处理，请参见 {@link #dispatch()}
     *
     * @param path - 分派目标的路径，作用域为初始化此AsyncContext的ServletContext
     *
     * @throws IllegalStateException - 如果调用了其中一个分派方法，并且在生成的分派期间未调用startAsync方法，或者如果调用了 {@link #complete}
     * @see ServletRequest#getDispatcherType
     */
	@Override
	public void dispatch(String path) {
		check();
        dispatch(getRequest().getServletContext(), path);		
	}
	
	/**
	 * 将此AsyncContext的请求和响应对象分派到作用于给定<tt>context</tt>的给定<tt>path</tt>。
	 * 
     * <p>
     * 路径参数的解释方式与 {@link ServletRequest#getRequestDispatcher(String)} 相同，不同的是它的作用域限定在给定的上下文中。
     * 
     * <p>
     * 请求的所有与路径相关的查询方法都必须反映调度目标，而 原始请求URI、上下文路径、路径信息、servlet路径和查询字符串可以从请求的 
     * {@link #ASYNC_REQUEST_URI}, {@link #ASYNC_CONTEXT_PATH}, {@link #ASYNC_PATH_INFO}, {@link #ASYNC_SERVLET_PATH} 
     * 和 {@link #ASYNC_QUERY_STRING} 属性中恢复。这些属性将始终反映原始的路径元素，即使是不重复的分派。
     *
     * <p>
     * 每个异步周期最多只能有一个异步分派操作，由对 {@link ServletRequest#startAsync} 方法之一的调用启动。
     * 在同一同步周期内执行额外异步分派操作的任何尝试都将导致IllegalStateException。
     * 如果随后在已分派的请求上调用startAsync，则可以调用任何分派或 {@link #complete} 方法。
     *
     * <p>有关其他详细信息，包括错误处理，请参见 {@link #dispatch()}
     *
     * @param servletContext - 分派目标的ServletContext
     * @param path - 分派目标的路径，作用域为给定的ServletContext
     *
     * @throws IllegalStateException - 如果调用了其中一个分派方法，并且在生成的分派期间未调用startAsync方法，或者如果调用了 {@link #complete}
     * @see ServletRequest#getDispatcherType
     */
	@Override
	public void dispatch(ServletContext servletContext, String path) {
		synchronized (asyncContextLock) {
            check();
            if (dispatch != null) {
                throw new IllegalStateException("分派已经开始");
            }
            if (httpRequest.getAttribute(ASYNC_REQUEST_URI)==null) {
                httpRequest.setAttribute(ASYNC_REQUEST_URI, httpRequest.getRequestURI());
                httpRequest.setAttribute(ASYNC_CONTEXT_PATH, httpRequest.getContextPath());
                httpRequest.setAttribute(ASYNC_SERVLET_PATH, httpRequest.getServletPath());
                httpRequest.setAttribute(ASYNC_PATH_INFO, httpRequest.getPathInfo());
                httpRequest.setAttribute(ASYNC_QUERY_STRING, httpRequest.getQueryString());
            }
            final RequestDispatcher requestDispatcher = servletContext.getRequestDispatcher(path);
            if (!(requestDispatcher instanceof AsyncDispatcher)) {
                throw new UnsupportedOperationException("无异步调度程序");
            }
            final AsyncDispatcher applicationDispatcher = (AsyncDispatcher) requestDispatcher;
            final ServletRequest servletRequest = getRequest();
            final ServletResponse servletResponse = getResponse();
            // 获取一个本地副本，因为分派可能完成请求/响应，进而可能在减少正在进行的计数之前触发此对象的回收
            final Context context = this.context;
            this.dispatch = new AsyncRunnable(httpRequest, applicationDispatcher, servletRequest, servletResponse);
            this.httpRequest.getRequest().action(ActionCode.ASYNC_DISPATCH, null);
            clearServletRequestResponse();
            context.decrementInProgressAsyncCount();
        }		
	}
	
	/**
	 * 完成对用于初始化此AsyncContext的请求启动的异步操作，关闭用于初始化此异步上下文的响应集。
     * <p>
     * 使用创建此AsyncContext的ServletRequest注册的任何 {@link AsyncListener} 类型的侦听器都将在其 {@link AsyncListener#onComplete(AsyncEvent) } 方法中调用。
     * <p>
     * 在调用 {@link ServletRequest#startAsync()} 或 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)} 之后的任何时间以及在调用该类的一个分派方法之前调用此方法都是合法的。
     * 如果该方法在容器发起的调度(调用startAsync)返回容器之前被调用，那么该调用将不会生效(并且 {@link AsyncListener#onComplete(AsyncEvent)} 的任何调用将被延迟)，直到容器发起的调度返回容器之后。
     */
	@Override
	public void complete() {
		check();
		httpRequest.getRequest().action(ActionCode.ASYNC_COMPLETE, null);				
	}
	
	/**
	 * 使容器分派线程（可能来自托管线程池）以运行指定的Runnable。容器可以向 <tt>Runnable</tt> 传播适当的上下文信息。
     *
     * @param run - 异步处理程序
     */
	@Override
	public void start(Runnable run) {
        check();
        Runnable wrapper = new RunnableWrapper(run, context, this.httpRequest.getRequest());
        this.httpRequest.getRequest().action(ActionCode.ASYNC_RUN, wrapper);		
	}
	
	/**
	 * 使用调用 {@link ServletRequest#startAsync} 方法之一启动的最新同步周期注册给定的 {@link AsyncListener}。
     * <p>
     * 当异步循环成功完成、超时、导致错误或正在通过 {@link ServletRequest#startAsync} 方法之一启动新的异步循环时，给定的AsyncListener将收到 {@link AsyncEvent}。
     * <p>
     * AsyncListener实例将按照其添加顺序进行通知.
     *
     * <p>
     * 如果调用了 {@link ServletRequest#startAsync(ServletRequest, ServletResponse)} 或 {@link ServletRequest#startAsync} ，
     * 则在通知 {@link AsyncListener} 时，{@link AsyncEvent} 中可以使用完全相同的请求和响应对象。
     *
     * @param listener - 要注册的AsyncListener
     * 
     * @throws IllegalStateException - 如果这个方法在容器发起的分派之后被调用，在此过程中 {@link ServletRequest#startAsync} 方法之一被调用，则返回到容器中
     */
	@Override
	public void addListener(AsyncListener listener) {
        check();
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        listeners.add(wrapper);		
	}
	
	/**
	 * 通过调用 {@link ServletRequest#startAsync} 方法之一，将给定的 {@link AsyncListener} 注册为最近的异步循环。
     *
     * <p>
     * 当异步循环成功完成、超时、产生错误或通过 {@link ServletRequest#startAsync} 方法之一启动一个新的异步循环时，给定的AsyncListener将收到一个 {@link AsyncEvent}。
     *
     * <p>
     * AsyncListener实例将按照它们被添加的顺序被通知.
     *
     * <p>
     * 给定的ServletRequest和ServletResponse对象将分别通过传递给它的AsyncEvent的 {@link AsyncEvent#getSuppliedRequest getSuppliedRequest} 
     * 和 {@link AsyncEvent#getSuppliedResponse getSuppliedResponse} 方法提供给给定的AsyncListener。
     * 在传递 {@link AsyncEvent} 时，不应分别读取或写入这些对象，因为在注册给定的AsyncListener后可能会发生额外的包装，但可能会使用这些对象释放与它们相关的任何资源。
     *
     * @param listener - 要注册的AsyncListener
     * @param servletRequest - 将包含在AsyncEvent中的ServletRequest
     * @param servletResponse - 将包含在AsyncEvent中的ServletResponse
     *
     * @throws IllegalStateException - 如果这个方法在容器发起的分派之后被调用，在此过程中 {@link ServletRequest#startAsync} 方法之一被调用，则返回到容器中
     */
	@Override
	public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
		check();
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        wrapper.setServletRequest(servletRequest);
        wrapper.setServletResponse(servletResponse);
        listeners.add(wrapper);		
	}
	
	/**
	 * 实例化给定的 {@link AsyncListener} 类
     *
     * <p>
     * 在通过调用 <code>addListener</code> 方法之一将返回的AsyncListener实例注册到此AsyncContext之前，可以对其进行进一步自定义。
     * <p>
     * 给定的AsyncListener类必须定义一个零参数构造函数，该构造函数用于实例化它
     * <p>
     * 如果给定的clazz表示托管Bean，则此方法支持资源注入。有关托管Bean和资源注入的其他详细信息，请参阅JavaEE平台和JSR299规范。
     * <p>
     * 此方法支持适用于AsyncListener的任何注释
     *
     * @param <T> - 要实例化的对象的类
     * @param clazz - 要实例化的AsyncListener类
     * @return 新的AsyncListener实例
     *
     * @throws ServletException - 如果给定的clazz无法实例化
     */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
		check();
        T listener = null;
        try {
             listener = (T) context.getInstanceManager().newInstance(clazz.getName(), clazz.getClassLoader());
        } catch (ReflectiveOperationException e) {
            ServletException se = new ServletException(e);
            throw se;
        } catch (Exception e) {
            ExceptionUtils.handleThrowable(e.getCause());
            ServletException se = new ServletException(e);
            throw se;
        }
        return listener;
	}
	
	/**
	 * 设置此AsyncContext的超时（以毫秒为单位）
     *
     * <p>
     * 一旦容器发起的调度(其中一个被调用的 {@link ServletRequest#startAsync} 方法被返回到容器中)，这个超时就会应用到这个AsyncContext。
     * <p>
     * 如果既没有调用 {@link #complete} 方法，也没有调用任何分派方法，则超时将过期。超时值为零或更小表示没有超时。
     * <p>
     * 如果未调用 {@link #setTimeout}，则将应用容器的默认超时（可通过调用 {@link #getTimeout} 获得）
     * <p>
     * 默认值为 <code>30000</code> ms
     *
     * @param timeout - 以毫秒为单位的超时时间
     *
     * @throws IllegalStateException - 如果这个方法在容器发起的分派之后被调用，在此过程中 {@link ServletRequest#startAsync} 方法之一被调用，则返回到容器中
     */
	@Override
	public void setTimeout(long timeout) {
        check();
        this.timeout = timeout;
        httpRequest.getRequest().action(ActionCode.ASYNC_SETTIMEOUT, Long.valueOf(timeout));		
	}
	
	/**
	 * 获取此AsyncContext的超时（以毫秒为单位）
     * <p>
     * 此方法返回同步操作的容器默认超时，或传递给 {@link #setTimeout} 的最新调用的超时值。
     * <p>
     * 超时值为零或更小表示没有超时
     *
     * @return 以毫秒为单位的超时时间
     */
	@Override
	public long getTimeout() {
		check();
        return timeout;
	}
	
	public void recycle() {
        context = null;
        dispatch = null;
        event = null;
        hasOriginalRequestAndResponse = true;
        listeners.clear();
        httpRequest = null;
        clearServletRequestResponse();
        timeout = -1;
    }

    public boolean isStarted() {
        AtomicBoolean result = new AtomicBoolean(false);
        httpRequest.getRequest().action(ActionCode.ASYNC_IS_STARTED, result);
        return result.get();
    }

    public void setStarted(Context context, ServletRequest request, ServletResponse response, boolean originalRequestResponse) {
        synchronized (asyncContextLock) {
            this.httpRequest.getRequest().action(ActionCode.ASYNC_START, this);

            this.context = context;
            context.incrementInProgressAsyncCount();
            this.servletRequest = request;
            this.servletResponse = response;
            this.hasOriginalRequestAndResponse = originalRequestResponse;
            this.event = new AsyncEvent(this, request, response);

            List<AsyncListenerWrapper> listenersCopy = new ArrayList<>(listeners);
            listeners.clear();
            if (logger.isDebugEnabled()) {
                logger.debug("AsyncContextImpl#fireOnStartAsync");
            }
            for (AsyncListenerWrapper listener : listenersCopy) {
                try {
                    listener.fireOnStartAsync(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    logger.warn("调用监听器" + listener.getClass().getName() + ".fireOnStartAsync(AsyncEvent) 触发异常" , t);
                }
            }
        }
    }
    
    public void setErrorState(Throwable t, boolean fireOnError) {
        if (t!=null) httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        httpRequest.getRequest().action(ActionCode.ASYNC_ERROR, null);

        if (fireOnError) {
            if (logger.isDebugEnabled()) {
                logger.debug("AsyncContextImpl#setErrorState(错误触发)");
            }
            AsyncEvent errorEvent = new AsyncEvent(event.getAsyncContext(), event.getSuppliedRequest(), event.getSuppliedResponse(), t);
            List<AsyncListenerWrapper> listenersCopy = new ArrayList<>(listeners);
            for (AsyncListenerWrapper listener : listenersCopy) {
                try {
                    listener.fireOnError(errorEvent);
                } catch (Throwable t2) {
                    ExceptionUtils.handleThrowable(t2);
                    logger.warn("调用监听器" + listener.getClass().getName() + ".fireOnError(AsyncEvent) 触发异常" , t);
                }
            }
        }


        AtomicBoolean result = new AtomicBoolean();
        httpRequest.getRequest().action(ActionCode.ASYNC_IS_ERROR, result);
        if (result.get()) {
        	/*
        	 * 没有调用dispatch()或complete()的侦听器。这是一个错误。如果另一个线程清除了这个错误，请在本地复制一个副本以避免线程问题(可能发生在非容器线程的错误处理过程中)。
        	 */
            ServletResponse servletResponse = this.servletResponse;
            if (servletResponse instanceof HttpServletResponse) {
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

            Host host = (Host) context.getParent();
            Valve stdHostValve = host.getPipeline().getBasic();
            if (stdHostValve instanceof StandardHostValve) {
                ((StandardHostValve) stdHostValve).throwable(httpRequest, httpRequest.getHttpResponse(), t);
            }

            httpRequest.getRequest().action(ActionCode.ASYNC_IS_ERROR, result);
            if (result.get()) {
                /*
                 * 仍然处于错误状态。错误页面没有调用complete()或dispatch()。
                 * 完成异步处理。
                 */
                complete();
            }
        }
    }

	private void check() {
	    if (httpRequest == null) {
	        // AsyncContext已经被回收，不应该被使用
	        throw new IllegalStateException("请求已结束");
	    }
	}

	private void clearServletRequestResponse() {
	    servletRequest = null;
	    servletResponse = null;
	}
	
	
	private static class RunnableWrapper implements Runnable {

        private final Runnable wrapped;
        private final Context context;
        private final Request request;

        public RunnableWrapper(Runnable wrapped, Context ctxt, Request request) {
            this.wrapped = wrapped;
            this.context = ctxt;
            this.request = request;
        }

        @Override
        public void run() {
            ClassLoader oldCL = context.bind(Globals.IS_SECURITY_ENABLED, null);
            try {
                wrapped.run();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.getLogger().error("AsyncContextImpl#异步调用 Runnable 出错", t);
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                Response response = request.getResponse();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setError();
            } finally {
                context.unbind(Globals.IS_SECURITY_ENABLED, oldCL);
            }

            // 因为这个可运行程序不是由于套接字事件而执行的，所以我们需要确保执行了所有注册的分派。
            request.action(ActionCode.DISPATCH_EXECUTE, null);
        }
    }


    private static class AsyncRunnable implements Runnable {
    	
        private final AsyncDispatcher applicationDispatcher;
        private final HttpRequest httpRequest;
        private final ServletRequest servletRequest;
        private final ServletResponse servletResponse;

        public AsyncRunnable(HttpRequest request, AsyncDispatcher applicationDispatcher, ServletRequest servletRequest, ServletResponse servletResponse) {
            this.httpRequest = request;
            this.applicationDispatcher = applicationDispatcher;
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }

        @Override
        public void run() {
            httpRequest.getRequest().action(ActionCode.ASYNC_DISPATCHED, null);
            try {
                applicationDispatcher.dispatch(servletRequest, servletResponse);
            } catch (Exception e) {
                throw new RuntimeException("AsyncContextImpl#异步调用出错", e);
            }
        }

    }
}
