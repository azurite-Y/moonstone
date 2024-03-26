package org.zy.moonstone.core.servlets;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;

/**
 * @dateTime 2022年11月16日;
 * @author zy(azurite-Y);
 * @description
 * 围绕 {@link HttpServletResponse } 的包装器，它将应用程序响应对象(可能是传递给servlet的原始对象，
 * 也可能是基于 {@link HttpServletResponseWrapper} 类)转换回内部的 {@link HttpResponse}。
 * <p>
 * <strong>WARNING</strong>: 
 * 由于Java不支持多重继承，{@link ApplicationResponse }中的所有逻辑都在ApplicationHttpResponses中重复。在进行更改时，需确保这两个类保持同步！
 */
class ApplicationHttpResponse extends HttpServletResponseWrapper {
	/**
	 * 此包装的响应是否Include()主体方法调用
	 */
	protected boolean included = false;

	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 *	在指定的Servlet响应周围构造一个新的包装响应
	 *
	 * @param response - 包装的Servlet响应
	 * @param included - 如果此响应正在由<code>RequestDispatcher.include()</code>调用处理，则返回<code>true</code>
	 */
	public ApplicationHttpResponse(HttpServletResponse response, boolean included) {
		super(response);
		setIncluded(included);
	}


	// -------------------------------------------------------------------------------------
	// ServletResponse 实现方法
	// -------------------------------------------------------------------------------------
	/**
	 * 禁止在 included 响应上调用<code>reset()</code>。
	 *
	 * @exception IllegalStateException - 如果响应已经提交
	 */
	@Override
	public void reset() {
		// 如果已经提交，包装的响应将抛出ISE
		if (!included || getResponse().isCommitted())
			getResponse().reset();
	}


	/**
	 *禁止在 included 响应上调用<code>setContentLength(int</code>。
	 *
	 * @param len - 新内容长度
	 */
	@Override
	public void setContentLength(int len) {
		if (!included)
			getResponse().setContentLength(len);
	}


	/**
	 * 禁止在 included 响应上调用 <code>setContentLengthLong(long)</code>
	 *
	 * @param len - 新内容长度
	 */
	@Override
	public void setContentLengthLong(long len) {
		if (!included)
			getResponse().setContentLengthLong(len);
	}


	/**
	 * 禁止在 included 响应上调用<code>setContentType()</code>
	 *
	 * @param type - 新的内容类型
	 */
	@Override
	public void setContentType(String type) {
		if (!included)
			getResponse().setContentType(type);
	}


	/**
	 * 忽略在 included 响应上调用<code>setLocale()</code>
	 *
	 * @param loc - 新的地址
	 */
	@Override
	public void setLocale(Locale loc) {
		if (!included)
			getResponse().setLocale(loc);
	}


	/**
	 * 忽略在 included 响应上调用<code>setBufferSize()</code>
	 *
	 * @param size - 缓存尺寸
	 */
	@Override
	public void setBufferSize(int size) {
		if (!included)
			getResponse().setBufferSize(size);
	}


	// -------------------------------------------------------------------------------------
	// HttpServletResponse 实现方法
	// -------------------------------------------------------------------------------------
	/**
	 * 禁止在 included 响应上调用<code>addCookie()</code>
	 *
	 * @param cookie - 新的 cookie
	 */
	@Override
	public void addCookie(Cookie cookie) {
		if (!included)
			((HttpServletResponse) getResponse()).addCookie(cookie);
	}


	// -------------------------------------------------------------------------------------
	// HttpServletResponseWrapper 实现方法
	// -------------------------------------------------------------------------------------
	/**
	 * 禁止在 included 响应上调用<code>addDateHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void addDateHeader(String name, long value) {
		if (!included)
			((HttpServletResponse) getResponse()).addDateHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用<code>addHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void addHeader(String name, String value) {
		if (!included)
			((HttpServletResponse) getResponse()).addHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用<code>addIntHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void addIntHeader(String name, int value) {
		if (!included)
			((HttpServletResponse) getResponse()).addIntHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用 <code>sendError()</code>
	 *
	 * @param sc - 新状态码
	 *
	 * @exception IOException - 如果发生输入/输出错误
	 */
	@Override
	public void sendError(int sc) throws IOException {
		if (!included)
			((HttpServletResponse) getResponse()).sendError(sc);
	}


	/**
	 * 禁止在 included 响应上调用<code>sendError()</code>
	 *
	 * @param sc - 新状态码
	 * @param msg - 新消息
	 *
	 * @exception IOException - 如果发生输入/输出错误
	 */
	@Override
	public void sendError(int sc, String msg) throws IOException {
		if (!included)
			((HttpServletResponse) getResponse()).sendError(sc, msg);
	}


	/**
	 * 禁止在 included 响应上调用<code>sendRedirect()</code>
	 *
	 * @param location - 新地址
	 * @exception IOException - 如果发生输入/输出错误
	 */
	@Override
	public void sendRedirect(String location) throws IOException {
		if (!included)
			((HttpServletResponse) getResponse()).sendRedirect(location);
	}


	/**
	 * 禁止在 included 响应上调用<code>setDateHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void setDateHeader(String name, long value) {
		if (!included)
			((HttpServletResponse) getResponse()).setDateHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用<code>setHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void setHeader(String name, String value) {
		if (!included)
			((HttpServletResponse) getResponse()).setHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用<code>setIntHeader()</code>
	 *
	 * @param name - 新的请求头名称
	 * @param value - 新的请求头值
	 */
	@Override
	public void setIntHeader(String name, int value) {
		if (!included)
			((HttpServletResponse) getResponse()).setIntHeader(name, value);
	}


	/**
	 * 禁止在 included 响应上调用<code>setStatus()</code>
	 *
	 * @param sc - 新状态码
	 */
	@Override
	public void setStatus(int sc) {
		if (!included)
			((HttpServletResponse) getResponse()).setStatus(sc);
	}


	/**
	 * 禁止在 included 响应上调用<code>setStatus()</code>
	 *
	 * @param sc - 新状态码
	 * @param msg - 新消息
	 * @deprecated 从2.1版开始，由于message形参的含义不明确。
	 * 设置状态码使用 <code>setStatus(int)</code>，发送带有描述的错误使用 <code>sendError(int, String)</code>。
	 */
	@Deprecated
	@Override
	public void setStatus(int sc, String msg) {
		if (!included)
			((HttpServletResponse) getResponse()).setStatus(sc, msg);
	}


	// -------------------------------------------------------------------------------------
	// 包方法
	// -------------------------------------------------------------------------------------
	/**
	 * 设置此响应的 included 标志
	 *
	 * @param included - 新的 included 标志
	 */
	void setIncluded(boolean included) {
		this.included = included;
	}


	/**
	 * 设置正在包装的响应
	 *
	 * @param response - 新包装的响应
	 */
	void setResponse(HttpServletResponse response) {
		super.setResponse(response);
	}
}
