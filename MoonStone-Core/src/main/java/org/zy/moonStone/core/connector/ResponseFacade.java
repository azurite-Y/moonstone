package org.zy.moonStone.core.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.security.SecurityUtil;

/**
 * @dateTime 2022年6月29日;
 * @author zy(azurite-Y);
 * @description
 */
public class ResponseFacade implements HttpServletResponse {
	/**
	 * 包装的响应对象
	 */
	protected HttpResponse httpResponse = null;

	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 为指定的响应构造一个包装器
	 *
	 * @param response - 要包装的响应
	 */
	public ResponseFacade(HttpResponse httpResponse) {
		if (httpResponse == null) {
			throw new IllegalStateException("RequestFacade 包装的 HttpResponse 不能为null");
		}

		this.httpResponse = httpResponse;
	}

	
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
	/**
	 * Clear facade.
	 */
	public void clear() {
		httpResponse = null;
	}

	/**
	 * 防止克隆 facade.
	 */
	@Override
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	public void finish() {
		httpResponse.setSuspended(true);
	}

	public boolean isFinished() {
		return httpResponse.isSuspended();
	}

	public long getContentWritten() {
		return httpResponse.getContentWritten();
	}

	
	// -------------------------------------------------------------------------------------
	// ServletRequest 方法
	// -------------------------------------------------------------------------------------
	@Override
	public String getCharacterEncoding() {
		return httpResponse.getCharacterEncoding();
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		ServletOutputStream sos = httpResponse.getOutputStream();
		if (isFinished()) {
			httpResponse.setSuspended(true);
		}
		return sos;

	}

	@Override
	public PrintWriter getWriter() throws IOException {
		PrintWriter writer = httpResponse.getWriter();
		if (isFinished()) {
			httpResponse.setSuspended(true);
		}
		return writer;

	}

	@Override
	public void setContentLength(int len) {
		if (isCommitted()) {
			return;
		}
		httpResponse.setContentLength(len);
	}

	@Override
	public void setContentLengthLong(long length) {
		if (isCommitted()) {
			return;
		}
		httpResponse.setContentLengthLong(length);
	}

	@Override
	public void setContentType(String type) {
		if (isCommitted()) {
			return;
		}

		if (SecurityUtil.isPackageProtectionEnabled()) {
			AccessController.doPrivileged(new SetContentTypePrivilegedAction(type));
		} else {
			httpResponse.setContentType(type);
		}
	}

	@Override
	public void setBufferSize(int size) {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 setBufferSize ");
		}

		httpResponse.setBufferSize(size);

	}

	@Override
	public int getBufferSize() {
		return httpResponse.getBufferSize();
	}

	@Override
	public void flushBuffer() throws IOException {
		if (isFinished()) {
			return;
		}

		if (SecurityUtil.isPackageProtectionEnabled()) {
			try {
				AccessController.doPrivileged(new FlushBufferPrivilegedAction(httpResponse));
			} catch (PrivilegedActionException e) {
				Exception ex = e.getException();
				if (ex instanceof IOException) {
					throw (IOException) ex;
				}
			}
		} else {
			httpResponse.setAppCommitted(true);
			httpResponse.flushBuffer();
		}
	}

	@Override
	public void resetBuffer() {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 resetBuffer");
		}

		httpResponse.resetBuffer();
	}

	@Override
	public boolean isCommitted() {
		return httpResponse.isAppCommitted();
	}

	@Override
	public void reset() {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 reset");
		}

		httpResponse.reset();
	}

	@Override
	public void setLocale(Locale loc) {
		if (isCommitted()) {
			return;
		}

		httpResponse.setLocale(loc);
	}

	@Override
	public Locale getLocale() {
		return httpResponse.getLocale();
	}

	@Override
	public void addCookie(Cookie cookie) {
		if (isCommitted()) {
			return;
		}

		httpResponse.addCookie(cookie);
	}

	@Override
	public boolean containsHeader(String name) {
		return httpResponse.containsHeader(name);
	}

	@Override
	public String encodeURL(String url) {
		return httpResponse.encodeURL(url);
	}

	@Override
	public String encodeRedirectURL(String url) {
		return httpResponse.encodeRedirectURL(url);
	}

	@Override
	public String encodeUrl(String url) {
		return httpResponse.encodeURL(url);
	}

	@Override
	public String encodeRedirectUrl(String url) {
		return httpResponse.encodeRedirectURL(url);
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 sendError");
		}

		httpResponse.setAppCommitted(true);
		httpResponse.sendError(sc, msg);
	}

	@Override
	public void sendError(int sc) throws IOException {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 sendError");
		}

		httpResponse.setAppCommitted(true);
		httpResponse.sendError(sc);
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		if (isCommitted()) {
			throw new IllegalStateException("响应已提交，不允许 sendRedirect");
		}

		httpResponse.setAppCommitted(true);
		httpResponse.sendRedirect(location);
	}

	@Override
	public void setDateHeader(String name, long date) {
		if (isCommitted()) {
			return;
		}

		if (Globals.IS_SECURITY_ENABLED) {
			AccessController.doPrivileged(new DateHeaderPrivilegedAction(name, date, false));
		} else {
			httpResponse.setDateHeader(name, date);
		}
	}

	@Override
	public void addDateHeader(String name, long date) {
		if (isCommitted()) {
			return;
		}

		if (Globals.IS_SECURITY_ENABLED) {
			AccessController.doPrivileged(new DateHeaderPrivilegedAction(name, date, true));
		} else {
			httpResponse.addDateHeader(name, date);
		}
	}

	@Override
	public void setHeader(String name, String value) {
		if (isCommitted()) {
			return;
		}

		httpResponse.setHeader(name, value);
	}

	@Override
	public void addHeader(String name, String value) {
		if (isCommitted()) {
			return;
		}

		httpResponse.addHeader(name, value);
	}

	@Override
	public void setIntHeader(String name, int value) {
		if (isCommitted()) {
			return;
		}

		httpResponse.setIntHeader(name, value);
	}

	@Override
	public void addIntHeader(String name, int value) {
		if (isCommitted()) {
			return;
		}

		httpResponse.addIntHeader(name, value);
	}

	@Override
	public void setStatus(int sc) {
		if (isCommitted()) {
			return;
		}

		httpResponse.setStatus(sc);

	}

	/**
	 * @deprecated 从 Java Servlet API 的 2.1 版开始，由于 message 参数的含义不明确，此方法已被弃用。
	 */
	@Override
	@Deprecated
	public void setStatus(int sc, String sm) {
		if (isCommitted()) {
			return;
		}

		httpResponse.setStatus(sc, sm);
	}

	@Override
	public String getContentType() {
		return httpResponse.getContentType();
	}

	@Override
	public void setCharacterEncoding(String arg0) {
		httpResponse.setCharacterEncoding(arg0);
	}

	@Override
	public int getStatus() {
		return httpResponse.getStatus();
	}

	@Override
	public String getHeader(String name) {
		return httpResponse.getHeader(name);
	}

	@Override
	public Collection<String> getHeaderNames() {
		return httpResponse.getHeaderNames();
	}

	@Override
	public Collection<String> getHeaders(String name) {
		return httpResponse.getHeaders(name);
	}

	@Override
	public void setTrailerFields(Supplier<Map<String, String>> supplier) {
		httpResponse.setTrailerFields(supplier);
	}

	@Override
	public Supplier<Map<String, String>> getTrailerFields() {
		return httpResponse.getTrailerFields();
	}

	
	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
	private final class SetContentTypePrivilegedAction implements PrivilegedAction<Void> {
		private final String contentType;

		public SetContentTypePrivilegedAction(String contentType) {
			this.contentType = contentType;
		}

		@Override
		public Void run() {
			httpResponse.setContentType(contentType);
			return null;
		}
	}

	private final class DateHeaderPrivilegedAction implements PrivilegedAction<Void> {
		private final String name;
		private final long value;
		private final boolean add;

		DateHeaderPrivilegedAction(String name, long value, boolean add) {
			this.name = name;
			this.value = value;
			this.add = add;
		}

		@Override
		public Void run() {
			if (add) {
				httpResponse.addDateHeader(name, value);
			} else {
				httpResponse.setDateHeader(name, value);
			}
			return null;
		}
	}

	private static class FlushBufferPrivilegedAction implements PrivilegedExceptionAction<Void> {
		private final HttpResponse httpResponse;

		public FlushBufferPrivilegedAction(HttpResponse httpResponse) {
			this.httpResponse = httpResponse;
		}

		@Override
		public Void run() throws IOException {
			httpResponse.setAppCommitted(true);
			httpResponse.flushBuffer();
			return null;
		}
	}
}
