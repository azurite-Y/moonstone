package org.zy.moonStone.core.container.valves;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.HttpResponse;
import org.zy.moonStone.core.util.ErrorPageSupport;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.IOTools;
import org.zy.moonStone.core.util.descriptor.ErrorPage;
import org.zy.moonStone.core.util.http.ActionCode;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 实现一个输出HTML错误页面的Valve。这个Valve应该连接到主机级别，尽管如果连接到上下文它也可以工作
 */
public class ErrorReportValve extends ValveBase {
	private boolean showReport = true;
	private boolean showServerInfo = true;
	private final ErrorPageSupport errorPageSupport = new ErrorPageSupport();

	public ErrorReportValve() {
		super(true);
	}

	@Override
	public void invoke(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException, ServletException {
		// 执行请求
		getNext().invoke(httpRequest, httpResponse);

		if (httpResponse.isCommitted()) {
			if (httpResponse.setErrorReported()) {
				// 错误之前没有报告，但我们不能写一个错误页面，因为响应已经提交。尝试清除仍要写入客户端的任何数据。
				try {
					httpResponse.flushBuffer();
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
				}
				// 立即关闭，向客户发出出错的信号
				httpResponse.getResponse().action(ActionCode.CLOSE_NOW, httpRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
			}
			return;
		}

		// 获得下一Valve保存到请求中的异常对象
		Throwable throwable = (Throwable) httpRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

		// 如果一个异步请求正在进行中，并且在这个容器线程完成后不会结束，则不在此处理任何错误页面.
		if (httpRequest.isAsync() && !httpRequest.isAsyncCompleting()) {
			return;
		}

		if (throwable != null && !httpResponse.isError()) {
			/*
			 * 确保在响应中调用了必要的方法。在此清除写入缓冲区的任何内容
			 */
			httpResponse.reset();
			httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		// 无论如何，httpResponse. senderror()将在执行到达这一点并暂停响应之前被调用。需要反转，这样阀门就可以写入响应.
		httpResponse.setSuspended(false);

		try {
			report(httpRequest, httpResponse, throwable);
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
		}
	}

	/**
	 * 打印错误报告.
	 *
	 * @param httpRequest - 正在处理的请求
	 * @param httpResponse - 生成的响应
	 * @param throwable - 发生的异常(可能包含一个根本原因异常)
	 */
	protected void report(HttpRequest httpRequest, HttpResponse httpResponse, Throwable throwable) {
		int statusCode = httpResponse.getStatus();

		/*
		 * 在1xx、2xx和3xx状态下不执行任何操作
		 * 如果已写入任何内容，则不执行任何操作
		 * 如果响应未明确标记为错误且未报告该错误，则不执行任何操作
		 */
		if (statusCode < 400 || httpResponse.getContentWritten() > 0 || !httpResponse.setErrorReported()) {
			return;
		}

		// 如果发生了阻止进一步I/O的错误，不要浪费时间生成永远不会被读取的错误报告
		AtomicBoolean result = new AtomicBoolean(false);
		httpResponse.getResponse().action(ActionCode.IS_IO_ALLOWED, result);
		if (!result.get()) {
			return;
		}

		ErrorPage errorPage = null;
		if (throwable != null) {
			errorPage = errorPageSupport.find(throwable);
		}
		if (errorPage == null) {
			errorPage = errorPageSupport.find(statusCode);
		}
		if (errorPage == null) {
			// 默认错误页面
			errorPage = errorPageSupport.find(0);
		}

		if (errorPage != null) {
			// 发送错误页面
			sendErrorPage(errorPage.getLocation(), httpResponse);
		}
	}

	private boolean sendErrorPage(String location, HttpResponse httpResponse) {
		File file = new File(location);
		if (!file.isAbsolute()) {
			file = new File(getContainer().getMoonBase(), location);
		}
		if (!file.isFile() || !file.canRead()) {
			getContainer().getLogger().warn("错误页面未找到", location);
			return false;
		}

		httpResponse.setContentType("text/html");
		httpResponse.setCharacterEncoding("UTF-8");

		try (OutputStream os = httpResponse.getOutputStream();InputStream is = new FileInputStream(file);){
			IOTools.flow(is, os);
		} catch (IOException e) {
			getContainer().getLogger().warn("错误页面io异常，by location：" + location, e);
			return false;
		}
		return true;
	}
	
	/**
     * 启用/禁用完整的错误报告
     *
     * @param showReport - True表示完整的错误数据
     */
    public void setShowReport(boolean showReport) {
        this.showReport = showReport;
    }
    public boolean isShowReport() {
        return showReport;
    }

    /**
     * 在错误页面上启用/禁用服务器信息
     *
     * @param showServerInfo - True表示显示服务器信息
     */
    public void setShowServerInfo(boolean showServerInfo) {
        this.showServerInfo = showServerInfo;
    }

    public boolean isShowServerInfo() {
        return showServerInfo;
    }
}
