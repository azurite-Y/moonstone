package org.zy.moonstone.core.servlets.function;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @dateTime 2022年12月22日;
 * @author zy(azurite-Y);
 * @description {@link HttpServlet } 的快捷方法
 */
@FunctionalInterface
public interface HttpServletServiceCallback {
	void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
	
	default void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String protocol = req.getProtocol();
        String msg = "http.method_post_not_supported";
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
	}
}
