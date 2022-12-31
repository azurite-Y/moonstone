package org.zy.moonStone.core.interfaces.container;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * @dateTime 2022年9月29日;
 * @author zy(azurite-Y);
 * @description
 */
public interface AsyncDispatcher {
	
	/**
	 * 执行异步调度。该方法不检查请求是否处于合适的状态；这是调用方的责任。
	 * 
     * @param request - 要传递给分派目标的请求对象
     * @param response 要传递给分派目标的响应对象
     * @throws ServletException - 如果由调度目标抛出
     * @throws IOException - 如果在处理调度时发生I/O错误
     */
    public void dispatch(ServletRequest request, ServletResponse response) throws ServletException, IOException;
}
