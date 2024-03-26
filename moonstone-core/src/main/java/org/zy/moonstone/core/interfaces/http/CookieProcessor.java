package org.zy.moonstone.core.interfaces.http;

import java.nio.charset.Charset;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.http.ServerCookies;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description
 */
public interface CookieProcessor {
	/**
	 * 将提供的头文件解析为服务器cookie对象。
	 *
	 * @param headers - 要解析的 HTTP 请求头
	 * @param serverCookies - 使用解析结果填充的服务器 cookie 对象
	 */
	void parseCookieHeader(MimeHeaders headers, ServerCookies serverCookies);

	/**
	 * 为给定的 Cookie 生成 Set-Cookie HTTP 标头值。此方法接收 servlet 请求作为参数，以便它可以根据请求属性做出决定。 
	 * 一个这样的用例是决定是否应该根据 User-Agentor 其他请求标头将 SameSite 属性添加到 cookie，因为存在与 SameSite 属性不兼容的浏览器版本。
	 * 
	 * @param cookie - 将为其生成响应头的 cookie
	 * @param request - servlet 请求
	 * @return 可以直接添加到响应的表单中的响应头值
	 */
	String generateHeader(Cookie cookie, HttpServletRequest request);

    /**
     * 为给定的Cookie生成Set-Cookie HTTP标头值
     *
     * @param cookie - 为其生成报头的cookie
     * @return 可以直接添加到响应中的表单头值
     */
    String generateHeader(Cookie cookie);
	
	/**
	 * 获取在解析和/或生成 cookie 的 HTTP 请求头时在字节和字符之间转换时将使用的字符集
	 *
	 * @return 用于字节&lt;-&gt;字符转换的字符集
	 */
	Charset getCharset();
}
