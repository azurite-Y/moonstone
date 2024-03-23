package org.zy.moonstone.core.container.valves;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.connector.HttpRequest;
import org.zy.moonstone.core.connector.HttpResponse;
import org.zy.moonstone.core.container.StandardWrapper;
import org.zy.moonstone.core.exceptions.ClientAbortException;
import org.zy.moonstone.core.exceptions.CloseNowException;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.filter.ApplicationFilterChain;
import org.zy.moonstone.core.filter.ApplicationFilterFactory;
import org.zy.moonstone.core.interfaces.container.Container;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description 为StandardWrapper容器实现实现默认基本行为的阀门
 */
public final class StandardWrapperValve extends ValveBase {
	private volatile long processingTime;
	private volatile long maxTime;
	private volatile long minTime = Long.MAX_VALUE;
	private final AtomicInteger requestCount = new AtomicInteger(0);
	private final AtomicInteger errorCount = new AtomicInteger(0);

	public StandardWrapperValve() {
		super(true);
	}

	@Override
	public void invoke(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException, ServletException {
		boolean unavailable = false;
		Throwable throwable = null;

		// 一个请求属性
		long t1=System.currentTimeMillis();

		// 以原子方式将当前值加1
		requestCount.incrementAndGet();

		StandardWrapper wrapper = (StandardWrapper) getContainer();
		Servlet servlet = null;
		Context context = (Context) wrapper.getParent();

		// 检查标记为不可用的应用程序
		if (!context.getState().isAvailable()) {
			httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "当前上下文对象不可用");
			unavailable = true;
		}

		// 检查标记为不可用的servlet
		if (!unavailable && wrapper.isUnavailable()) {
			container.getLogger().info("当前Servlet不可用，by name：" + wrapper.getName());
			long available = wrapper.getAvailable();
			if ((available > 0L) && (available < Long.MAX_VALUE)) {
				httpResponse.setDateHeader("Retry-After", available);
				httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "当前Servlet不可用，by name：" + wrapper.getName());
			} else if (available == Long.MAX_VALUE) {
				httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "未找到可用的Servlet实例，by name：" + wrapper.getName());
			}
			unavailable = true;
		}

		// 分配一个servlet实例来处理这个请求
		try {
			if (!unavailable) {
				servlet = wrapper.allocate();
			}
		} catch (UnavailableException e) {
			container.getLogger().error("分配Servlet实例异常，by name：" + wrapper.getName(), e);
			long available = wrapper.getAvailable();
			if ((available > 0L) && (available < Long.MAX_VALUE)) {
				httpResponse.setDateHeader("Retry-After", available);
				httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "当前Servlet不可用，by name：" + wrapper.getName());
			} else if (available == Long.MAX_VALUE) {
				httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "未找到可用的Servlet实例，by name：" + wrapper.getName());
			}
		} catch (ServletException e) {
			container.getLogger().error("分配Servlet实例异常，by name：" + wrapper.getName(), StandardWrapper.getRootCause(e));
			throwable = e;
			exception(httpRequest, httpResponse, e);
		} catch (Throwable e) {
			ExceptionUtils.handleThrowable(e);
			container.getLogger().error("分配Servlet实例异常，by name：" + wrapper.getName(), e);
			throwable = e;
			exception(httpRequest, httpResponse, e);
			servlet = null;
		}

		MessageBytes requestPathMB = httpRequest.getRequestPathMB();
		DispatcherType dispatcherType = DispatcherType.REQUEST;

		if (httpRequest.getDispatcherType()==DispatcherType.ASYNC) 
			dispatcherType = DispatcherType.ASYNC;

		// 请求调度程序状态.
		httpRequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, dispatcherType);
		// 请求调度程序状态.
		httpRequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, requestPathMB);
		
		// 为此请求创建筛选器链
		ApplicationFilterChain filterChain = ApplicationFilterFactory.createFilterChain(httpRequest, wrapper, servlet);

		Container container = this.container;
		try {
			if ((servlet != null) && (filterChain != null)) {
				if (httpRequest.isAsyncDispatching()) {
					httpRequest.getAsyncContextInternal().doInternalDispatch();
				} else {
					filterChain.doFilter(httpRequest.getHttpServletRequest(), httpResponse.getHttpServletResponse());
				}
			}
		} catch (ClientAbortException | CloseNowException e) {
			if (container.getLogger().isDebugEnabled()) {
				container.getLogger().debug(String.format("Service异常，by serviceName：%s ，contextName：%s", wrapper.getName(), context.getName()) , e);
			}
			throwable = e;
			exception(httpRequest, httpResponse, e);
		} catch (IOException e) {
			container.getLogger().debug(String.format("Service异常，by serviceName：%s ，contextName：%s", wrapper.getName(), context.getName()) , e);
			throwable = e;
			exception(httpRequest, httpResponse, e);
		} catch (UnavailableException e) {
			container.getLogger().debug(String.format("Service异常，by serviceName：%s ，contextName：%s", wrapper.getName(), context.getName()) , e);
			wrapper.unavailable(e);
			long available = wrapper.getAvailable();
			if ((available > 0L) && (available < Long.MAX_VALUE)) {
				httpResponse.setDateHeader("Retry-After", available);
				httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "当前Servlet不可用，by name：" + wrapper.getName());
			} else if (available == Long.MAX_VALUE) {
				httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "未找到可用的Servlet实例，by name：" + wrapper.getName());
			}
		} catch (ServletException e) {
			Throwable rootCause = e.getRootCause();
			if (!(rootCause instanceof ClientAbortException)) {
				container.getLogger().error(String.format("Service异常，by serviceName：%s ，contextName：%s", wrapper.getName(), context.getName()) , rootCause);
			}
			throwable = e;
			exception(httpRequest, httpResponse, e);
		} catch (Throwable e) {
			ExceptionUtils.handleThrowable(e);
			container.getLogger().debug(String.format("Service异常，by serviceName：%s ，contextName：%s", wrapper.getName(), context.getName()) , e);
			throwable = e;
			exception(httpRequest, httpResponse, e);
		} finally {
			// 释放此请求的过滤器链(如果有的话)
			if (filterChain != null) {
				filterChain.release();
			}

			// 取消分配的servlet实例
			try {
				if (servlet != null) {
					wrapper.deallocate(servlet);
				}
			} catch (Throwable e) {
				ExceptionUtils.handleThrowable(e);
				container.getLogger().error("Servlet实例释放异常，by name：" + wrapper.getName(), e);
				if (throwable == null) {
					throwable = e;
					exception(httpRequest, httpResponse, e);
				}
			}

			// 如果这个servlet被标记为永久不可用，卸载它并释放这个实例
			try {
				if ((servlet != null) && (wrapper.getAvailable() == Long.MAX_VALUE)) {
					wrapper.unload();
				}
			} catch (Throwable e) {
				ExceptionUtils.handleThrowable(e);
				container.getLogger().error("Servlet卸载异常，by name：" + wrapper.getName(), e);
				if (throwable == null) {
					throwable = e;
					exception(httpRequest, httpResponse, e);
				}
			}
			long t2=System.currentTimeMillis();

			long time=t2-t1;
			processingTime += time;
			if( time > maxTime) maxTime=time;
			if( time < minTime) minTime=time;
		}
	}
	
	/**
     * 处理在处理指定的请求以产生指定的响应时遇到的指定的ServletException。在生成异常报告期间发生的任何异常都将被记录和处理.
     *
     * @param httpRequest - 正在处理的请求
     * @param httpResponse - 生成的响应
     * @param exception - 发生的异常(可能包含一个根本原因异常)
     */
    private void exception(HttpRequest httpRequest, HttpResponse httpResponse, Throwable exception) {
    	// 传播的异常对象
        httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        // 500
        httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        httpResponse.setError();
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }
    
    @Override
    protected void initInternal() throws LifecycleException {
    	// 不需要获取日志对象
    }
}
