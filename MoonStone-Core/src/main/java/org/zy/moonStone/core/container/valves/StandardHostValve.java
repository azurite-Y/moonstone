package org.zy.moonStone.core.container.valves;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.HttpResponse;
import org.zy.moonStone.core.exceptions.ClientAbortException;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.descriptor.ErrorPage;
import org.zy.moonStone.core.util.http.ActionCode;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description
 * 为StandardHost容器实现实现默认基本行为的Valve。使用约束:这个实现可能只在处理HTTP请求时有用。
 */
public class StandardHostValve extends ValveBase {
	static final boolean ACCESS_SESSION = Globals.ACCESS_SESSION;

	public StandardHostValve() {
		super(true);
	}

	// ------------------------------------------------------- 公共方法 -------------------------------------------------------
	@Override
	public void invoke(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException, ServletException {
		// 选择要用于此请求的上下文
		Context context = httpRequest.getContext();
		if (context == null) {
			return;
		}

		if (httpRequest.isAsyncSupported()) {
			// 依照管道中的Valve是否能处理异步请求来确定此值
			httpRequest.setAsyncSupported(context.getPipeline().isAsyncSupported());
		}

		boolean asyncAtStart = httpRequest.isAsync();

		try {
			if ( !asyncAtStart && !context.fireRequestInitEvent(httpRequest.getHttpServletRequest()) ) {
				/*
				 * 在异步处理期间不要启动侦听器(为调用startAsync()的请求启动的侦听器)。
				 * 如果请求初始化侦听器引发异常，则请求将中止
				 */
				return;
			}

			/*
			 * 请求此Context处理此请求。
			 * 已经出错的请求必须被路由到这里，以检查应用程序定义的错误页面，所以不要将它们转发给应用程序进行处理.
			 */
			try {
				if (!httpResponse.isErrorReportRequired()) {
					context.getPipeline().getFirst().invoke(httpRequest, httpResponse);
				}
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				container.getLogger().error("Exception Processing " + httpRequest.getRequestURI(), t);
				// 如果在尝试报告以前的错误时发生新错误，请允许报告原始错误
				if (!httpResponse.isErrorReportRequired()) {
					httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
					throwable(httpRequest, httpResponse, t);
				}
			}

			// 既然请求或响应对已回到容器控制之下，就取消暂停，以便完成错误处理和/或容器可以清除任何剩余的数据
			httpResponse.setSuspended(false);

			Throwable t = (Throwable) httpRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
			if (!context.getState().isAvailable()) {
				return;
			}

			// 查找(如果找到并呈现)应用程序级别的错误页面
			if (httpResponse.isErrorReportRequired()) {
				AtomicBoolean result = new AtomicBoolean(false);
				httpResponse.getResponse().action(ActionCode.IS_IO_ALLOWED, result);
				if (result.get()) {
					if (t != null) {
						throwable(httpRequest, httpResponse, t);
					} else {
						status(httpRequest, httpResponse);
					}
				}
			}

			if (!httpRequest.isAsync() && !asyncAtStart) {
				context.fireRequestDestroyEvent(httpRequest.getHttpServletRequest());
			}
		} finally {
			// 根据对规范的严格解释，访问会话(如果存在)以更新上次访问时间
			if (ACCESS_SESSION) {
				httpRequest.getSession(false);
			}
		}
	}

	/**
	 * 处理HTTP状态码(和相应的消息)，同时处理指定的请求以产生指定的响应。
	 * 在生成错误报告期间发生的任何异常都将被记录并接受.
	 *
	 * @param httpRequest - 正在处理的请求
	 * @param httpResponse - 生成的响应
	 */
	private void status(HttpRequest httpRequest, HttpResponse httpResponse) {
		int statusCode = httpResponse.getStatus();

		// 处理此状态码的自定义错误页
		Context context = httpRequest.getContext();
		if (context == null) {
			return;
		}

		/*
		 * 仅在设置了isError()时查找错误页面。isError()是在调用response.sendError()时设置的
		 */
		if (!httpResponse.isError()) {
			return;
		}

		ErrorPage errorPage = context.findErrorPage(statusCode);
		if (errorPage == null) {
			// 查找默认错误页面
			errorPage = context.findErrorPage(0);
		}
		if (errorPage != null && httpResponse.isErrorReportRequired()) {
			httpResponse.setAppCommitted(false);
			// 传播的响应状态
			httpRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, Integer.valueOf(statusCode));

			String message = httpResponse.getMessage();
			if (message == null) {
				message = "";
			}
			// 传播的异常消息
			httpRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
			// 请求调度程序路径
			httpRequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, errorPage.getLocation());
			// 请求调度程序状态
			httpRequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ERROR);

			Wrapper wrapper = httpRequest.getWrapper();
			if (wrapper != null) {
				// 传播发生错误servlet的名称
				httpRequest.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, wrapper.getName());
			}
			// 传播导致错误的请求uri
			httpRequest.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, httpRequest.getRequestURI());
			if (custom(httpRequest, httpResponse, errorPage)) {
				httpResponse.setErrorReported();
				try {
					httpResponse.finishResponse();
				} catch (ClientAbortException e) {} catch (IOException e) {
					container.getLogger().warn("异常处理:" + errorPage, e);
				}
			}
		}
	}

	/**
	 * 处理在处理指定请求时遇到的指定Throwable以产生指定的响应。在生成异常报告期间发生的任何异常都将被记录和处理。
	 *
	 * @param httpRequest - 正在处理的请求
	 * @param httpResponse - 生成的响应
	 * @param throwable - 发生的异常(它可能包装了一个根本原因异常)
	 */
	public void throwable(HttpRequest httpRequest, HttpResponse httpResponse, Throwable throwable) {
		Context context = httpRequest.getContext();
		if (context == null) {
			return;
		}

		Throwable realError = throwable;

		if (realError instanceof ServletException) {
			realError = ((ServletException) realError).getRootCause();
			if (realError == null) {
				realError = throwable;
			}
		}

		// 如果这是一个从客户端中止的请求，只需记录它并返回
		if (realError instanceof ClientAbortException ) {
			if (logger.isDebugEnabled()) {
				logger.debug("客户端终止请求，{}", realError.getCause().getMessage());
			}
			return;
		}

		ErrorPage errorPage = context.findErrorPage(throwable);
		if ((errorPage == null) && (realError != throwable)) {
			errorPage = context.findErrorPage(realError);
		}

		if (errorPage != null) {
			if (httpResponse.setErrorReported()) {
				httpResponse.setAppCommitted(false);
				// 请求调度程序路径
				httpRequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, errorPage.getLocation());
				// 请求调度程序状态
				httpRequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ERROR);
				// 传播的响应状态
				httpRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, Integer.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
				// 传播的异常消息
				httpRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, throwable.getMessage());
				// 传播的异常对象
				httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, realError);
				Wrapper wrapper = httpRequest.getWrapper();
				if (wrapper != null) {
					httpRequest.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, wrapper.getName());
				}
				// 传播发生错误servlet的名称
				httpRequest.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, httpRequest.getRequestURI());
				// 传播的异常对象类型
				httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, realError.getClass());
				if (custom(httpRequest, httpResponse, errorPage)) {
					try {
						httpResponse.finishResponse();
					} catch (IOException e) {
						container.getLogger().warn("Exception Processing " + errorPage, e);
					}
				}
			}
		} else {
			// 没有为请求处理期间抛出的异常定义自定义错误页。检查是否指定了错误码500的错误页，如果是，则将该页作为响应发送回去。
			httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			httpResponse.setError();

			status(httpRequest, httpResponse);
		}
	}

	/**
	 * 通过将控件转发到指定的errorPage对象中包含的位置来处理HTTP状态代码或Java异常。
	 * 假定调用者已经记录了要转发到此页面的所有请求属性。
	 * 如果我们成功地利用了指定的错误页面位置，则返回true;如果应该呈现默认的错误报告，则返回false.
	 *
	 * @param httpRequest - 正在处理的请求
	 * @param httpResponse - 生成的响应
	 * @param errorPage - 正在遵守的ErrorPage指令
	 */
	private boolean custom(HttpRequest httpRequest, HttpResponse httpResponse, ErrorPage errorPage) {
		if (container.getLogger().isDebugEnabled()) {
			container.getLogger().debug("处理 " + errorPage);
		}

		try {
			// 将控制转发到指定位置
			ServletContext servletContext = httpRequest.getContext().getServletContext();
			RequestDispatcher rd = servletContext.getRequestDispatcher(errorPage.getLocation());

			if (rd == null) {
				container.getLogger().error("自定义状态失败", errorPage.getLocation());
				return false;
			}

			if (httpResponse.isCommitted()) {
				// 响应被提交 - 包括错误页面
				rd.include(httpRequest.getHttpServletRequest(), httpResponse.getHttpServletResponse());
			} else {
				// 重置响应(保留真实的错误代码和消息)
				httpResponse.resetBuffer(true);
				httpResponse.setContentLength(-1);

				rd.forward(httpRequest.getHttpServletRequest(), httpResponse.getHttpServletResponse());

				// 如果转发，响应再次被暂停
				httpResponse.setSuspended(false);
			}
			// 指示已经成功地处理了这个自定义页面
			return true;
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			// 报告处理此自定义页面失败
			container.getLogger().error("异常处理 " + errorPage, t);
			return false;
		}
	}
}
