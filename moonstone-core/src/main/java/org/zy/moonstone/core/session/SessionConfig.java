package org.zy.moonstone.core.session;

import org.zy.moonstone.core.interfaces.container.Context;

import javax.servlet.SessionCookieConfig;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description
 */
public class SessionConfig {
	private static final String DEFAULT_SESSION_COOKIE_NAME = "JSESSIONID";
	private static final String DEFAULT_SESSION_PARAMETER_NAME = "jsessionid";

	/**
	 * 确定用于提供的上下文的会话 cookie 的名称
	 * @param context
	 * @return 上下文的cookie名称
	 */
	public static String getSessionCookieName(Context context) {
		String result = getConfiguredSessionCookieName(context);

		if (result == null) {
			result = DEFAULT_SESSION_COOKIE_NAME;
		}

		return result;
	}

	/**
	 * 确定所提供上下文的会话路径参数的名称
	 * @param context
	 * @return 会话的参数名
	 */
	public static String getSessionUriParamName(Context context) {
		String result = getConfiguredSessionCookieName(context);

		if (result == null) {
			result = DEFAULT_SESSION_PARAMETER_NAME;
		}
		return result;
	}

	/**
	 * 获得已配置的会话Cookie名称
	 * <ul>优先级是:
	 * <li>1. 上下文中定义的cookie名称</li>
	 * <li>2. 为应用配置的 Cookie 名称</li>
	 * <li>3. 默认定义</li>
	 * <ul/>
	 * @return 已配置的会话Cookie名称
	 */
	private static String getConfiguredSessionCookieName(Context context) {
		if (context != null) {
			String cookieName = context.getSessionCookieName();
			if (cookieName != null && cookieName.length() > 0) {
				return cookieName;
			}

			SessionCookieConfig scc = context.getServletContext().getSessionCookieConfig();
			cookieName = scc.getName();
			if (cookieName != null && cookieName.length() > 0) {
				return cookieName;
			}
		}

		return null;
	}


	/**
	 * 确定用于所提供上下文的会话cookie路径的值
	 *
	 */
	public static String getSessionCookiePath(Context context) {
		SessionCookieConfig scc = context.getServletContext().getSessionCookieConfig();

		String contextPath = context.getSessionCookiePath();
		if (contextPath == null || contextPath.length() == 0) {
			contextPath = scc.getPath();
		}
		if (contextPath == null || contextPath.length() == 0) {
			contextPath = context.getEncodedPath();
		}
		if (context.getSessionCookiePathUsesTrailingSlash()) {
			if (!contextPath.endsWith("/")) {
				contextPath = contextPath + "/";
			}
		} else {
			// 仅处理根上下文的特殊情况，其中 cookie 需要路径为 '/' 但 servlet 规范使用空字符串
			if (contextPath.length() == 0) {
				contextPath = "/";
			}
		}

		return contextPath;
	}

	/**
	 * 工具类，隐藏默认构造器
	 */
	private SessionConfig() {}
}
