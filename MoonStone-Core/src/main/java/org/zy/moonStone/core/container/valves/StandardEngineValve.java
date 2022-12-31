package org.zy.moonStone.core.container.valves;

import java.io.IOException;

import javax.servlet.ServletException;

import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.HttpResponse;
import org.zy.moonStone.core.interfaces.container.Host;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description
 * 为StandardEngine容器实现实现默认基本行为的阀门。使用约束:这个实现可能只在处理HTTP请求时有用
 */
public final class StandardEngineValve extends ValveBase {
	public StandardEngineValve() {
        super(true);
    }
	
	@Override
    public final void invoke(HttpRequest httpRequest, HttpResponse httpResponse)
        throws IOException, ServletException {

        // 选择用于此请求的主机
        Host host = httpRequest.getHost();
        if (host == null) {
            // 当没有定义默认主机时，HTTP 0.9或HTTP 1.0请求没有主机。这是由 MoonAdapter处理的.
            return;
        }
        if (httpRequest.isAsyncSupported()) {
            httpRequest.setAsyncSupported(host.getPipeline().isAsyncSupported());
        }

        // 请求此主机处理此请求
        host.getPipeline().getFirst().invoke(httpRequest, httpResponse);
    }
}
