package org.zy.moonstone.core.http;

import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.http.ActionHook;
import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.http.ServerCookies;
import org.zy.moonstone.core.util.http.parser.HttpParser;
import org.zy.moonstone.core.util.net.interfaces.InputBuffer;

import javax.servlet.ReadListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @dateTime 2022年5月25日;
 * @author zy(azurite-Y);
 * @description 服务器原初请求的低级、高效表示。 大多数字段是无 GC 的，昂贵的操作会延迟到用户代码需要信息。处理委托给模块，使用回调机制。
 * 这个类不用于用户代码 - 它由 moonstone 内部使用，用于以最有效的方式处理请求 方法。 用户（servlet）可以使用外观访问信息，该外观提供请求的高级视图。
 */
public final class Request {
	/** 每个请求的预期最大 cookie 数 */
	private static final int INITIAL_COOKIE_SIZE = 4;
	/** 服务器端口号 */
	private int serverPort = -1;
	/** 服务器名 */
	private final MessageBytes serverNameMB = MessageBytes.newInstance();

	/** 远程端口 */
	private int remotePort;
	/** 本地端口 */
	private int localPort;

	/** 通信协议 */
	private final MessageBytes schemeMB = MessageBytes.newInstance();
	/** http 方法 */
	private final MessageBytes methodMB = MessageBytes.newInstance();
	/** Http URI */
	private final MessageBytes uriMB = MessageBytes.newInstance();
	/** 已解码的Http URI */
	private final MessageBytes decodedUriMB = MessageBytes.newInstance();
	/** uri路径参数 */
	private final MessageBytes queryMB = MessageBytes.newInstance();
	/** 访问端口 */
	private final MessageBytes protoMB = MessageBytes.newInstance();

	/** 远程地址 */
	private final MessageBytes remoteAddrMB = MessageBytes.newInstance();
	/** 本地主机名 */
	private final MessageBytes localNameMB = MessageBytes.newInstance();
	/** 远程主机 */
	private final MessageBytes remoteHostMB = MessageBytes.newInstance();
	/** 本地地址 */
	private final MessageBytes localAddrMB = MessageBytes.newInstance();

	/** 请求头 */
	private final MimeHeaders headers = new MimeHeaders();
	/** */
	private final Map<String,String> trailerFields = new HashMap<>();

	/** 路径参数  */
	private final Map<String,String> pathParameters = new HashMap<>();

	private final Object notes[] = new Object[Globals.MAX_NOTES];

	/** 相关的输入缓冲区 */
	private InputBuffer inputBuffer = null;

	/** 请求内容长度 */
	private long contentLength = -1;
	/** 请求正文类型 */
	private MessageBytes contentTypeMB;
	/** 已解析不包含子项的Http "ContentType" 请求头 */
	private String parsedContentType;
	private Charset charset = null;
	/** 保留原始的、用户指定的字符编码，即使它无效也可以返回 */
	private String characterEncoding = null;

	/** 
	 * 存储请求行数据
	 * @deprecated 考虑到上传文件可能很大，所以废弃
	 */
	@Deprecated
	private byte[] requestBodyLine = null;
	
	/** 请求行数据的延迟提供者 */
	private Supplier<Byte> deferredSupplier = null;
	
	/** 已从流中读取到的第一个请求体数据 */
//	private byte firstBodyByte;
	
	/** 是否已读取请求体数据 */
	private boolean requestBodyReaded = false;
	
	/** POST 请求体数据边界字节数组 */
	private byte[] boundaryArray = null;
	
	private boolean parseContentType = false;
	
	/** 存储ContextType子项解析后的值数据，如：boundary、charset */
	private Map<String, String> parseContentTypeMap;
	
	private boolean expectation = false;

	private final ServerCookies serverCookies = new ServerCookies(INITIAL_COOKIE_SIZE);
	private final Parameters parameters = new Parameters();

    private final HashMap<String,Object> attributes = new HashMap<>();
	
	private Response response;
	private volatile ActionHook hook;

	/** 读取字节数 */
	private long bytesRead=0;
	/** 请求的时间，用于避免重复调用System.currentTime */
	private long startTime = -1;
	/** 可用标识 */
	private int available = 0;
	/** 请求处理的统计信息 */
	private final RequestInfo reqProcessorMX=new RequestInfo(this);

	/** 是否发送文件 */
	private boolean sendfile = true;

	/** 这个类表示一个回调机制，当HTTP请求数据可以读取而不阻塞时，它将通知实现 */
	private volatile ReadListener listener;

	/** 已发送所有数据读取事件 */
	private final AtomicBoolean allDataReadEventSent = new AtomicBoolean(false);

	// ----------------------------------------------------- 构造器 -----------------------------------------------------
	public Request() {
		parameters.setQuery(queryMB);
	}

	// ----------------------------------------------------- getter、setter -----------------------------------------------------
	public boolean sendAllDataReadEvent() {
		return allDataReadEventSent.compareAndSet(false, true);
	}

	public MimeHeaders getMimeHeaders() {
		return headers;
	}

	public boolean isTrailerFieldsReady() {
		AtomicBoolean result = new AtomicBoolean(false);
		action(ActionCode.IS_TRAILER_FIELDS_READY, result);
		return result.get();
	}
	public Map<String,String> getTrailerFields() {
		return trailerFields;
	}

	// ----------------------------------------------------- 
	// 请求数据 
	// -----------------------------------------------------
	/**
	 * @return 请求协议
	 */
	public MessageBytes scheme() {
		return schemeMB;
	}

	/**
	 * @return 请求方法
	 */
	public MessageBytes method() {
		return methodMB;
	}

	/**
	 * @return 未解码的 http uri
	 */
	public MessageBytes requestURI() {
		return uriMB;
	}

	/**
	 * @return 已解码的 http uri
	 */
	public MessageBytes decodedURI() {
		return decodedUriMB;
	}

	/**
	 * @return uri路径参数 
	 */
	public MessageBytes queryString() {
		return queryMB;
	}

	/**
	 * @return 访问端口 
	 */
	public MessageBytes protocol() {
		return protoMB;
	}

	/**
	 * 获取“虚拟主机”，派生自与此请求相关联的host:报头。
	 * @return 保存服务器名的缓冲区(如果有的话)。使用isNull()检查是否没有设置值。
	 */
	public MessageBytes serverName() {
		return serverNameMB;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort ) {
		this.serverPort=serverPort;
	}

	public MessageBytes remoteAddr() {
		return remoteAddrMB;
	}

	public MessageBytes remoteHost() {
		return remoteHostMB;
	}

	public MessageBytes localName() {
		return localNameMB;
	}

	public MessageBytes localAddr() {
		return localAddrMB;
	}

	public int getRemotePort(){
		return remotePort;
	}
	public void setRemotePort(int port){
		this.remotePort = port;
	}

	public int getLocalPort(){
		return localPort;
	}
	public void setLocalPort(int port){
		this.localPort = port;
	}

	protected void setRequestBodySupplier(Supplier<Byte> deferredSupplier) {
		this.deferredSupplier = deferredSupplier;
	}
	
	public Supplier<Byte> getRequestBodySupplier() {
		if (requestBodyReaded) {
			throw new IllegalStateException("请求体字节数据已在'getRequestBodyByte()'方法读取，不支持可重复读");
		}
		return deferredSupplier;
	}

	public byte[] getRequestBodyByte() {
		if (requestBodyReaded) {
			throw new IllegalStateException("请求体字节数据不支持可重复读");
		}
		requestBodyReaded = true;

		byte[] arr = new byte[(int) contentLength];
    	
		for (int j = 0; j < contentLength; j++) {
			arr[j] = deferredSupplier.get(); 
		}
		
		return arr;
	}
	
	@Deprecated
	protected void setRequestBodyLineByte(byte[] requestBodyLine) {
		this.requestBodyLine = requestBodyLine;
	}
	/**
	 * 向外传递出请求正文的字节数据
	 * 
	 * @return 请求行数据
	 */
	@Deprecated
	public byte[] getRequestBodyLineByte() {
		return this.requestBodyLine;
	}
	
	// ----------------------------------------------------- 编码/类型 -----------------------------------------------------
	/**
	 * @return POST 请求体数据边界字节数组 
	 */
	public byte[] getBoundaryArray() {
		if (boundaryArray == null) {
			parseContentType();
			boundaryArray = ("--" + parseContentTypeMap.get("boundary").trim()).getBytes();
		}
		return boundaryArray;
	}
	
	/**
	 * 获取用于此请求的字符编码
	 *
	 * @return 通过 setCharset(Charset) 设置的值，或者如果没有调用该方法，则尝试从 ContentType 获取
	 */
	public String getCharacterEncoding() {
		if (characterEncoding == null) {
			parseContentType();
			if (parseContentTypeMap != null && parseContentTypeMap.isEmpty()) {
				String charsetString = parseContentTypeMap.get("charset");
				characterEncoding = charsetString == null ? null : charsetString.trim();
			}
    		
//			characterEncoding = getCharsetFromContentType(getContentType());
		}

		return characterEncoding;
	}

	/**
	 * 获取用于此请求的字符编码
	 * @return 通过 setCharset(Charset) 设置的值，或者如果没有调用该方法，则尝试从 ContentType 获取
	 * @throws UnsupportedEncodingException - 如果用户代理指定了无效的字符编码
	 */
	public Charset getCharset() throws UnsupportedEncodingException {
		if (charset == null) {
			getCharacterEncoding();
			if (characterEncoding != null) {
				charset = Charset.forName(characterEncoding);
			}
		}

		return charset;
	}
	public void setCharset(Charset charset) {
		this.charset = charset;
		this.characterEncoding = charset.name();
	}

	public void setContentLength(long len) {
		this.contentLength = len;
	}
	public int getContentLength() {
		long length = getContentLengthLong();

		if (length < Integer.MAX_VALUE) {
			return (int) length;
		}
		return -1;
	}

	public long getContentLengthLong() {
		if( contentLength > -1 ) {
			return contentLength;
		}

		MessageBytes clB = headers.getUniqueValue("content-length");
		contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

		return contentLength;
	}

	/**
	 * @return 已解析不包含子项的Http "ContentType" 请求头
	 */
	public String getParsedContentType() {
		parseContentType();
		return parsedContentType;
		
	}
	/**
	 * @return 未解析的Http "ContentType" 请求头
	 */
	public String getContentType() {
		if (contentTypeMB == null) {
			contentTypeMB = headers.getValue("content-type");
		}
		if ((contentTypeMB == null) || contentTypeMB.isNull()) {
			return null;
		}
		return contentTypeMB.toString();
	}
	public void setContentType(MessageBytes mb) {
		contentTypeMB=mb;
	}
    public void setContentType(String type) {
        contentTypeMB.setString(type);
    }

	public String getHeader(String name) {
		return headers.getValue(name).toString();
	}

	public void setExpectation(boolean expectation) {
		this.expectation = expectation;
	}
	public boolean hasExpectation() {
		return expectation;
	}
	
	public ReadListener getReadListener() {
        return listener;
    }
    public void setReadListener(ReadListener listener) {
        if (listener == null) {
            throw new NullPointerException("ReadListener 不能为空");
        }
        if (getReadListener() != null) {
            throw new IllegalStateException("ReadListener 已被设置");
        }
        // 注意：这个类不用于 HTTP 升级，所以只需要测试异步
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException("异步回调错误");
        }

        this.listener = listener;
    }

	// ----------------------------------------------------- 关联对象 -----------------------------------------------------
	public Response getResponse() {
		return response;
	}

	public void setResponse(Response response) {
		this.response = response;
		response.setRequest(this);
	}

	protected void setHook(ActionHook hook) {
		this.hook = hook;
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

	// ----------------------------------------------------- Cookies -----------------------------------------------------
	public ServerCookies getCookies() {
		return serverCookies;
	}

	// ----------------------------------------------------- Parameters -----------------------------------------------------
	public Parameters getParameters() {
		return parameters;
	}

	public void addPathParameter(String name, String value) {
		pathParameters.put(name, value);
	}

	public String getPathParameter(String name) {
		return pathParameters.get(name);
	}

	public void setAttribute( String name, Object o ) {
		attributes.put( name, o );
	}

	public HashMap<String,Object> getAttributes() {
		return attributes;
	}

	public Object getAttribute(String name ) {
		return attributes.get(name);
	}

	public int getAvailable() {
		return available;
	}

	public void setAvailable(int available) {
		this.available = available;
	}

	public boolean getSendfile() {
		return sendfile;
	}

	public void setSendfile(boolean sendfile) {
		this.sendfile = sendfile;
	}

	/**
	 * @return 是否支持相对重定向，仅 HTTP/1.1 支持相对重定向
	 */
	public boolean getSupportsRelativeRedirects() {
		if (protocol().equals("") || protocol().equals("HTTP/1.0")) {
			return false;
		}
		return true;
	}

	// ----------------------------------------------------- Input Buffer -----------------------------------------------------
	public InputBuffer getInputBuffer() {
		return inputBuffer;
	}
	public void setInputBuffer(InputBuffer inputBuffer) {
		this.inputBuffer = inputBuffer;
	}

	/**
	 * 从输入缓冲区读取数据并将其放入 ApplicationBufferHandler。<br/>
	 * 
	 * 缓冲区由协议实现拥有 - 它将在下一次读取时重用。 适配器必须就地处理数据，或者如果需要保存数据，则将其复制到单独的缓冲区。 
	 * 在大多数情况下，这是在 byte->char 转换期间或通过 InputStream 完成的。 与InputStream不同，该接口允许应用程序就地处理数据，无需复制。
	 *
	 * @param handler - 要将数据复制到的目标
	 * @return 复制的字节数
	 * @throws IOException - 如果在复制过程中发生 I/O 错误
	 */
//	public int doRead(ApplicationBufferHandler handler) throws IOException {
//		asd
//		int n = inputBuffer.doRead(handler);
//		if (n > 0) {
//			bytesRead+=n;
//		}
//		return n;
//	}

	@Override
	public String toString() { // 应用于debug
		return "R( " + requestURI().toString() + ")";
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	// ----------------------------------------------------- 每个请求的 Note -----------------------------------------------------
	/**
	 * 用于存储私有数据。可以使用线程数据,但如果有要求，获取/设置注释只是一个数组访问，对于非常频繁的操作，可能比ThreadLocal更快。<br/>
	 * 为避免冲突， 0 - 8 范围内为 servlet 容器（catalina 连接器等）保留，9 - 16 中的值供连接器使用。17-31 范围未分配或使用。
	 * @param pos - 用来存储笔记的索引
	 * @param value - 要存储在该索引中的值
	 */
	public final void setNote(int pos, Object value) {
		notes[pos] = value;
	}

	public final Object getNote(int pos) {
		return notes[pos];
	}

	/**
	 * 回收资源
	 */
	public void recycle() {
		bytesRead=0;

		contentLength = -1;
		contentTypeMB = null;
		parsedContentType = null;
		parseContentType = false;
		parseContentTypeMap = null;
		boundaryArray = null;
		
		charset = null;
		characterEncoding = null;
		expectation = false;
		headers.recycle();
		trailerFields.clear();
		serverNameMB.recycle();
		serverPort=-1;
		localAddrMB.recycle();
		localNameMB.recycle();
		localPort = -1;
		remoteAddrMB.recycle();
		remoteHostMB.recycle();
		remotePort = -1;
		available = 0;
		sendfile = true;

		serverCookies.recycle();
		parameters.recycle();
		pathParameters.clear();

		uriMB.recycle();
		decodedUriMB.recycle();
		queryMB.recycle();
		methodMB.recycle();
		protoMB.recycle();

		schemeMB.recycle();

		attributes.clear();

		listener = null;
		allDataReadEventSent.set(false);

		startTime = -1;
		
		deferredSupplier = null;
		requestBodyLine = null;
		requestBodyReaded = false;
	}

	/**
     * 解析http报文的 "Content-Type" 请求头
     */
    public void parseContentType() {
    	if (parseContentType) {
    		return ;
    	}
    	
    	parseContentType = true;
    	
    	String contextType = getContentType();
    	if (contextType == null) {
    		return ;
    	}
    	
    	int len = contextType.length();
		int semicolon = contextType.indexOf(';');
		if (semicolon >= 0) {
			parsedContentType = contextType.substring(0, semicolon).trim();
            parseContentTypeMap = HttpParser.parseSeparator(contextType.substring(semicolon + 1, len), ";", "=");
            
            if (parseContentTypeMap != null && parseContentTypeMap.isEmpty() && parseContentTypeMap.containsKey("charset")) {
            	try {
					getCharset();
					
					this.serverNameMB.setCharset(this.charset);
					this.schemeMB.setCharset(this.charset);
					this.methodMB.setCharset(this.charset);
					this.uriMB.setCharset(this.charset);
					this.decodedUriMB.setCharset(this.charset);
					this.queryMB.setCharset(this.charset);
					this.	protoMB.setCharset(this.charset);
					this.remoteAddrMB.setCharset(this.charset);
					this.localNameMB.setCharset(this.charset);
					this.remoteHostMB.setCharset(this.charset);
					this.localAddrMB.setCharset(this.charset);
					this.contentTypeMB.setCharset(this.charset);
					
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
            }
		} else {
			parsedContentType = contextType.trim();
		}
    }
	
	/**
	 * 解析指定内容类型标头中的字符编码。如果内容类型为null，或者没有显式的字符编码，则返回null。
	 * @param contentType - 内容类型标头
	 * @deprecated 改为由 {@link Request#parseContentType()} 方法统一解析 "Content-Type" 请求头值
	 */
	@Deprecated
	public String getCharsetFromContentType(String contentType) {
		if (contentType == null) {
			return null;
		}
		int start = contentType.indexOf("charset=");
		if (start < 0) {
			return null;
		}
		String encoding = contentType.substring(start + 8);
		int end = encoding.indexOf(';');
		if (end >= 0) {
			encoding = encoding.substring(0, end);
		}
		encoding = encoding.trim();
		if ((encoding.length() > 2) && (encoding.startsWith("\"")) && (encoding.endsWith("\""))) {
			encoding = encoding.substring(1, encoding.length() - 1);
		}
	
		return encoding.trim();
	}

	// ----------------------------------------------------- 收集统计信息 -----------------------------------------------------
	public void updateCounters() {
		reqProcessorMX.updateCounters();
	}

	public RequestInfo getRequestProcessor() {
		return reqProcessorMX;
	}

	public long getBytesRead() {
		return bytesRead;
	}

	public boolean isProcessing() {
		return reqProcessorMX.getStage()==Globals.STAGE_SERVICE;
	}
}


