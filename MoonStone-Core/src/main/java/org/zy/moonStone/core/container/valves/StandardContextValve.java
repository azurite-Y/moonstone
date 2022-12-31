package org.zy.moonStone.core.container.valves;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.HttpResponse;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年5月17日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardContextValve extends ValveBase {
	 public StandardContextValve() {
	        super(true);
	    }
	 
	@Override
	public void invoke(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException, ServletException {
		// 禁止直接访问WEB-INF或META-INF下的资源
        MessageBytes requestPathMB = httpRequest.getRequestPathMB();
        if ((requestPathMB.startsWithIgnoreCase("/META-INF/", 0))  || (requestPathMB.equalsIgnoreCase("/META-INF"))  || (requestPathMB.startsWithIgnoreCase("/WEB-INF/", 0)) || (requestPathMB.equalsIgnoreCase("/WEB-INF"))) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Wrapper wrapper = httpRequest.getWrapper();
        if (wrapper == null || wrapper.isUnavailable()) {
        	if (logger.isDebugEnabled()) {
        		logger.debug("Send Response Error. by wrapper 为null或wrapper不可用");
        	}
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
        	// 发送对请求的确认
            httpResponse.sendAcknowledgement();
        } catch (IOException ioe) {
            container.getLogger().error("请求确认异常", ioe);
            httpRequest.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        if (httpRequest.isAsyncSupported()) {
            httpRequest.setAsyncSupported(wrapper.getPipeline().isAsyncSupported());
        }
        wrapper.getPipeline().getFirst().invoke(httpRequest, httpResponse);
	}
}
