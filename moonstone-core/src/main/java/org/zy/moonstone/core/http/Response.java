package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.http.ActionHook;
import org.zy.moonstone.core.util.http.MediaType;
import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.net.ContainerThreadMarker;
import org.zy.moonstone.core.util.net.interfaces.HttpOutputBuffer;

import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @dateTime 2022年5月25日;
 * @author zy(azurite-Y);
 * @description 服务器原初响应对象
 */
public final class Response {
	private static final Logger logger = LoggerFactory.getLogger(Response.class);

	/**
	 * 规范要求的默认语言环境。
	 */
	private static final Locale DEFAULT_LOCALE = Locale.getDefault();

	/**
	 * 状态码
	 */
	private int status = 200;


	/**
	 * 状态信息
	 */
	private String message = null;


	/**
	 * 响应头
	 */
	private final MimeHeaders headers = new MimeHeaders();

	/**
	 *  
	 */
	private Supplier<Map<String,String>> trailerFieldsSupplier = null;

	/**
	 * http 数据流输出缓冲区
	 */
	private HttpOutputBuffer httpOutputBuffer;

	/**
	 * 响应注释
	 */
	private final Object notes[] = new Object[Globals.MAX_NOTES];

	/**
	 * 提交标识
	 */
	private volatile boolean committed = false;

	private volatile ActionHook hook;

	/**
	 * 响应类型
	 */
	private String contentType = null;

	/**
	 * 响应报文使用语言
	 */
	private String contentLanguage = null;

	/**
	 * 使用字符集
	 */
	private Charset charset = null;

	/**
	 * 保留用于设置字符集的原始名称，以便在 ContentType 标头中使用该名称。
	 */
	private String characterEncoding = null;

	/**
	 * 响应内容长度
	 */
	private long contentLength = -1;

	/**
	 * 本地语言环境
	 */
	private Locale locale = DEFAULT_LOCALE;

	/**
	 * 写入内容长度
	 */
	private long contentWritten = 0;

	/**
	 * 响应提交时间
	 */
	private long commitTime = -1;

	/**
	 * 保留的请求错误异常
	 */
	private Exception errorException = null;

	/**
	 * 当前错误状态
	 */
	private final AtomicInteger errorState = new AtomicInteger(0);

	private Request req;

	private volatile WriteListener listener;

	
	public Request getRequest() {
		return req;
	}
	public void setRequest( Request req ) {
		this.req=req;
	}

	public void setHttpOutputBuffer(HttpOutputBuffer HttpOutputBuffer) {
		this.httpOutputBuffer = HttpOutputBuffer;
	}

	public MimeHeaders getMimeHeaders() {
		return headers;
	}

	protected void setHook(ActionHook hook) {
		this.hook = hook;
	}

	public final void setNote(int pos, Object value) {
		notes[pos] = value;
	}
	public final Object getNote(int pos) {
		return notes[pos];
	}

	public void action(ActionCode actionCode, Object param) {
		if (hook != null) {
			if (param == null) {
				hook.action(actionCode, this);
			} else {
				hook.action(actionCode, param);
			}
		}
	}

	public int getStatus() {
		return status;
	}
	/**
	 * 设置响应状态
	 * @param status
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * 获得响应状态消息
	 *
	 * @return 与当前状态相关的消息
	 */
	public String getMessage() {
		return message;
	}
	/**
	 * 设置响应状态消息
	 * @param message - 要设置的状态消息
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	public boolean isCommitted() {
		return committed;
	}
	public void setCommitted(boolean v) {
		if (v && !this.committed) {
			this.commitTime = System.currentTimeMillis();
		}
		this.committed = v;
	}

	/**
	 *
	 * @return 响应提交的时间（基于 System.currentTimeMillis）
	 */
	public long getCommitTime() {
		return commitTime;
	}

	/**
	 * 设置请求处理过程中发生的错误
	 * @param ex - 发生的异常
	 */
	public void setErrorException(Exception ex) {
		errorException = ex;
	}
	/**
	 * 获取请求处理过程中发生的异常
	 * @return 发生的异常
	 */
	public Exception getErrorException() {
		return errorException;
	}

	public boolean isExceptionPresent() {
		return ( errorException != null );
	}

	/**
	 * 设置错误标识
	 * @return 如果已经设置了错误标志，则为 <code>false</code>
	 */
	public boolean setError() {
		return errorState.compareAndSet(0, 1);
	}

	/**
	 * @return 如果响应遇到错误，则为 <code>true</code>
	 */
	public boolean isError() {
		return errorState.get() > 0;
	}

	/**
	 * @return 是否需要错误报告，需要则为true
	 */
	public boolean isErrorReportRequired() {
		return errorState.get() == 1;
	}

	/**
	 * @return 设置需错误报告的标识，返回true则代表设置成功
	 */
	public boolean setErrorReported() {
		return errorState.compareAndSet(1, 2);
	}

	/**
	 * 重置当前请求
	 */
	public void reset() throws IllegalStateException {
		if (committed) {
			throw new IllegalStateException("请求已提交，无法重置!");
		}

		recycle();
	}

	/**
	 * 响应是否包含给定的响应头
	 *
	 * @param name - 感兴趣的响应头名称
	 * @return 如果响应包含响应头，则为 {@code true}
	 */
	public boolean containsHeader(String name) {
		MessageBytes value = headers.getValue(name);
		return !value.isNull() || (value.getString() != null && !value.getString().isEmpty());
	}

	public void setHeader(String name, String value) {
		char cc=name.charAt(0);
		if( cc=='C' || cc=='c' ) {
			if( checkSpecialHeader(name, value) )
				return;
		}
		headers.setValue(name).setString( value);
	}

	public void addHeader(String name, String value) {
		addHeader(name, value, null);
	}

	public void addHeader(String name, String value, Charset charset) {
		char cc=name.charAt(0);
		if( cc=='C' || cc=='c' ) {
			if( checkSpecialHeader(name, value) )
				return;
		}
		MessageBytes mb = headers.addHeadNameValue(name);
		if (charset != null) {
			mb.setCharset(charset);
		}
		mb.setString(value);
	}

	public void setTrailerFields(Supplier<Map<String, String>> supplier) {
		AtomicBoolean trailerFieldsSupported = new AtomicBoolean(false);
		action(ActionCode.IS_TRAILER_FIELDS_SUPPORTED, trailerFieldsSupported);
		if (!trailerFieldsSupported.get()) {
			throw new IllegalStateException("该响应不支持尾部字段");
		}

		this.trailerFieldsSupplier = supplier;
	}

	public Supplier<Map<String, String>> getTrailerFields() {
		return trailerFieldsSupplier;
	}

	/**
	 * 设置特殊头名的内部字段。从set/addHeader中调用。如果头是特殊的，则对这些特殊请求头调用对应的setter方法并返回true。
	 */
	private boolean checkSpecialHeader( String name, String value) {
		// 消除冗余字段（标题和特殊字段）
		if( name.equalsIgnoreCase( "Content-Type" ) ) {
			setContentType( value );
			return true;
		}
		if( name.equalsIgnoreCase( "Content-Length" ) ) {
			try {
				long cL=Long.parseLong( value );
				setContentLength( cL );
				return true;
			} catch( NumberFormatException ex ) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 表明我们已经完成了 headers，body 将随之而来。任何实现都需要通知 ContextManager，以允许拦截器修复 headers。
	 */
	public void sendHeaders() {
		action(ActionCode.COMMIT, this);
		setCommitted(true);
	}

	public Locale getLocale() {
		return locale;
	}

	/**
	 * 由用户显式调用以设置Content-Language和默认编码
	 * @param locale - 用于此响应的Locale设置
	 */
	public void setLocale(Locale locale) {
		if (locale == null) {
			return;
		}

		// 保存区域设置供getLocale()使用
		this.locale = locale;

		// 设置标题输出的contentLanguage
		contentLanguage = locale.toLanguageTag();
	}

	/**
	 * @return 当前与此响应关联的语言
	 */
	public String getContentLanguage() {
		return contentLanguage;
	}

	/**
	 * 覆盖响应体中使用的字符编码。必须在使用getWriter()写入输出之前调用此方法。
	 * @param characterEncoding - 字符编码的名称
	 * @throws UnsupportedEncodingException - 如果指定的名称不被识别
	 */
	public void setCharacterEncoding(String characterEncoding) throws UnsupportedEncodingException {
		if (isCommitted()) {
			return;
		}
		if (characterEncoding == null) {
			return;
		}

		this.charset  = Charset.forName(characterEncoding);
		this.characterEncoding = characterEncoding;
	}

	public Charset getCharset() {
		return charset;
	}

	/**
	 * @return 当前编码的名称
	 */
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	/**
	 * 设置响应内容类型<br/>
	 *
	 * 此方法必须保留可能已经通过调用 httpResponse.setContentType()、httpResponse.setLocale() 或 httpResponse.setCharacterEncoding() 设置的任何响应字符集。
	 *
	 * @param type - 响应内容类型
	 */
	public void setContentType(String type) {
		if (type == null) {
			this.contentType = null;
			return;
		}

		MediaType m = null;
		try {
			m = MediaType.parseMediaType(type);
		} catch (IOException e) {
			// 忽略 - 由下面的空判断处理
		}
		if (m == null) {
			// 设置的响应内容类型格式无效，但仍保存提供的任何内容
			this.contentType = type;
			return;
		}

		this.contentType = m.toStringNoCharset();

		String charsetValue = m.getCharset();

		if (charsetValue != null) {
			charsetValue = charsetValue.trim();
			if (charsetValue.length() > 0) {
				try {
					charset = Charset.forName(characterEncoding);
				} catch (Exception e) {
					logger.warn(String.format("无效的字符集编码，by %s", charsetValue), e);
				}
			}
		}
	}

	public void setContentTypeNoCharset(String type) {
		this.contentType = type;
	}

	public String getContentType() {
		String ret = contentType;

		if (ret != null && charset != null) {
			ret = ret + ";charset=" + characterEncoding;
		}

		return ret;
	}

	public void setContentLength(long contentLength) {
		this.contentLength = contentLength;
	}

	public int getContentLength() {
		long length = getContentLengthLong();

		if (length < Integer.MAX_VALUE) {
			return (int) length;
		}
		return -1;
	}

	public long getContentLengthLong() {
		return contentLength;
	}

	/**
	 * 写入字节块
	 * @param chunk - 要写入的 ByteBuffer
	 * @throws IOException - 如果在写入过程中发生 I/O 错误
	 */
	public void doWrite(ByteBuffer chunk) throws IOException {
		// 缓冲区中的剩余唯独字节
		int len = chunk.remaining();
		httpOutputBuffer.doWrite(chunk);
		contentWritten += len - chunk.remaining();
	}

	public void recycle() {
		contentType = null;
		contentLanguage = null;
		locale = DEFAULT_LOCALE;
		charset = null;
		characterEncoding = null;
		contentLength = -1;
		status = 200;
		message = null;
		committed = false;
		commitTime = -1;
		errorException = null;
		errorState.set(0);
		headers.clear();
		trailerFieldsSupplier = null;
		// Servlet 3.1 监听非阻塞写
		listener = null;

		// 更新计数器
		contentWritten=0;
	}

	/**
	 * 应用程序写入的字节数 - 即在压缩、分块等之前。
	 * @return 应用程序写入响应的总字节数。 这不会是写入网络的字节数，可能大于或小于此值。
	 */
	public long getContentWritten() {
		return contentWritten;
	}

	/**
	 * 写入套接字的字节数 - 即在压缩、分块等之后。
	 *
	 * @param flush - 在返回总数之前是否应该刷新任何剩余的字节？ 如果缓冲区中剩余的假字节将不包含在返回值中
	 * @return 为此响应写入套接字的总字节数
	 */
	public long getBytesWritten(boolean flush) {
		if (flush) {
			action(ActionCode.CLIENT_FLUSH, this);
		}
		return httpOutputBuffer.getBytesWritten();
	}

	public WriteListener getWriteListener() {
		return listener;
	}

	public void setWriteListener(WriteListener listener) {
		if (listener == null) {
			throw new NullPointerException("设置的WriteListener为空");
		}
		if (getWriteListener() != null) {
			throw new IllegalStateException("响应对象持有的writeListener为空");
		}
		// 注意：这个类不用于 HTTP 升级，所以只需要测试异步
		AtomicBoolean result = new AtomicBoolean(false);
		action(ActionCode.ASYNC_IS_ASYNC, result);
		if (!result.get()) {
			throw new IllegalStateException("异步回调报错");
		}

		this.listener = listener;

		action(ActionCode.DISPATCH_WRITE, null);
		if (!ContainerThreadMarker.isContainerThread()) {
			// 不在容器线程上，因此需要执行调度
			action(ActionCode.DISPATCH_EXECUTE, null);
		}
	}

}
