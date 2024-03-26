package org.zy.moonstone.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.exceptions.CloseNowException;
import org.zy.moonstone.core.session.SessionConstants;
import org.zy.moonstone.core.util.RequestUtil;
import org.zy.moonstone.core.util.buf.ByteArrayUtils;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.net.SocketWrapperBase;
import org.zy.moonstone.core.util.net.interfaces.InputBuffer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * @dateTime 2022年6月1日;
 * @author zy(azurite-Y);
 * @description 废案代码记录
 */
@SuppressWarnings(value = {"deprecation", "unused"})
public class Http11InputBuffer2 implements InputBuffer {
//	private static final Logger logger = LoggerFactory.getLogger(Http11InputBuffer.class);

	/**
	 * 关联的请求对象
	 */
	private final Request request;

	/**
	 * 关联的请求标头
	 */
//	private final MimeHeaders headers;

	/**
	 * 是否拒绝非法请求头名称
	 */
//	private final boolean rejectIllegalHeaderName;

	/**
	 * State.
	 */
	private boolean parsingHeader;

	/**
	 * 读取缓冲区，存储着请求头的数据
	 */
	private ByteBuf byteBuf;
	
	/**
	 * 可重用读取缓冲区
	 */
	private ByteBuffer byteBuffer;

	/**
	 * 提供对底层套接字访问的包装器
	 */
	private SocketWrapperBase<?> socketWrapper;

	/**
	 * 解析状态 - 用于非阻塞解析，以便当更多数据到达时，可以从中断的地方继续
	 */
//	private boolean parsingRequestLine;

	/**
	 * HTTP head name 最大大小
	 */
	private final int headerNameBufferSize;

	/**
	 * HTTP head value 最大大小
	 */
	private final int headerValueBufferSize;

	/**
	 * NioChannel 读取缓冲区的已知大小
	 */
//	private int socketReadBufferSize;

	/**
	 * 请求头空行过后是否还有数据，即请求体
	 */
//	private boolean requestBodyFlag;

	// ----------------------------------------------------- 构造器 -----------------------------------------------------
	/**
	 * 根据给定的参数实例化一个 {@code Http11InputBuffer } 对象
	 * @param request
	 * @param headerNameBufferSize - 请求头参数名缓冲区极值
	 * @param headerValueBufferSize - 请求头参数值缓冲区极值
	 */
//	 * @param rejectIllegalHeaderName - 是否拒绝非法请求头名称
	public Http11InputBuffer2(Request request, int headerNameBufferSize, int headerValueBufferSize, int maxHttpHeaderSize) {
		this.request = request;
		this.headerNameBufferSize = headerNameBufferSize;
		this.headerValueBufferSize = headerValueBufferSize;
        this.byteBuf = ByteBufAllocator.DEFAULT.buffer(1024, maxHttpHeaderSize);
        this.byteBuffer = ByteBuffer.allocate(8192);

	}


	// ----------------------------------------------------- 方法 -----------------------------------------------------
	/**
	 * 回收输入缓冲区。 这应该在关闭连接时调用
	 */
	void recycle() {
		socketWrapper = null;
		request.recycle();

	}

	/**
	 * 读取和解析请求字节。请求体字节数据后续按需读取
	 * 
	 * @return true则代表读取解析完成
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常
	 * @since 1.1
	 */
	boolean readAndParseRequestBytes(boolean keptAlive, int connectionTimeout, int keepAliveTimeout) throws IOException {
		if (keptAlive) {
			this.socketWrapper.setKeepAliveLeft(keepAliveTimeout);
		}

		int nRead = -1;
		if (this.socketWrapper == null) throw new CloseNowException();

		RequestBufferEnhanceHanlder requestBufferEnhanceHanlder = new RequestBufferEnhanceHanlder();

		boolean readding = true;
		basic: while(readding) {
//			readData = this.socketWrapper.readByte();
			this.socketWrapper.read(true, this.byteBuffer);
			this.byteBuffer.flip();
			while (byteBuffer.hasRemaining()) {
				readding = requestBufferEnhanceHanlder.readAndParseRequestBytes(byteBuffer.get());
				if (!readding) {
					this.byteBuffer.compact();
					break basic;
				}
			} 
			byteBuffer.clear();
		}
		
		requestBufferEnhanceHanlder.prepareRequest(request);

		/*
		 * 至少已经接收到请求的一个字节。切换到套接字超时
		 * 
		 * 设置读取超时时间(毫秒)。值为-1表示无限超时，0或更小的值将被更改为1。
		 */
		this.socketWrapper.setReadTimeout(connectionTimeout);

		return nRead > 0 
				? true 
				: false ; // 未读到任何数据...
	}

	public void nextRequest() {
        request.recycle();

//        byteBuffer.clear();
        
        if (byteBuf.readableBytes()>0){
        	byteBuf.resetWriterIndex();
        	byteBuf.resetReaderIndex();
        }
        
        this.parsingHeader = false;
	}
	
	void init(SocketWrapperBase<?> socketWrapper) {
		this.socketWrapper = socketWrapper;
//		socketWrapper.setAppReadBufferHandler(this);
	}
	
	/**
	 * 延迟读取请求体数据
	 */
	void deferredReadRequestBody() {
//		System.out.println(this.byteBuffer.position());
//		System.out.println(this.byteBuffer.limit());
//		System.out.println(this.byteBuffer.remaining());
//		
//		while(this.byteBuffer.hasRemaining()) {
//			System.out.print((char)this.byteBuffer.get());
//		}
		
		Supplier<Byte> deferredSupplier = new Supplier<Byte>() {
			Byte data = null;
			
			@Override
			public Byte get() {
				try {
					data =  (Byte) socketWrapper.readByte();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return data;
			}
		};
//		request.setFirstBodyByte((byte)1);
		request.setRequestBodySupplier(deferredSupplier);
	}
	
	/**
	 * 读取全部请求字节。
	 *
	 * @return 如果正确输入数据，则为 true； 如果没有立即可用的数据并且应该释放线程，则为 false
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常，或者给定的缓冲区不足以容纳整行
	 * @apiNote 为了节省一次循环获得字节的时间而废弃，其与 {@linkplain #parseRequestBytes() } 配合使用
	 * @since 1.0
	 */
	@Deprecated
	boolean readRequestByte(boolean keptAlive, int connectionTimeout, int keepAliveTimeout) throws IOException {
		if (keptAlive) {
			this.socketWrapper.setKeepAliveLeft(keepAliveTimeout);
		} 

		SocketWrapperBase<?> socketWrapper = this.socketWrapper;
		int nRead = -1;
		if (socketWrapper != null) {
			nRead = socketWrapper.read(false, byteBuf);
		} else {
			throw new CloseNowException();
		}

		/*
		 * 至少已经接收到请求的一个字节。切换到套接字超时
		 * 
		 * 设置读取超时时间(毫秒)。值为-1表示无限超时，0或更小的值将被更改为1。
		 */
		this.socketWrapper.setReadTimeout(connectionTimeout);

		if (nRead > 0) {
			return true;
		} else if (nRead == 0) {
			throw new EOFException("未读到任何数据...");
		} else {
			return false;
		}
	}

	/**
	 * 解析 HTTP 标头
	 * @apiNote 为了节省一次循环获得字节的时间而废弃，其与 {@linkplain #readRequestByte(boolean, int, int) } 配合使用
	 * @since 1.0
	 */
	@Deprecated
	boolean parseRequestBytes() throws IOException {
		// 存储请求头信息
		MimeHeaders mimeHeaders = this.request.getMimeHeaders();

		byte[] uri = null;
		boolean uriArgs = false;
		int questionIndex = 0;
		// 记录上次读取的字节索引
		int laseEnd = 0;
		// 记录单行请求数据中空格的出现次数
		int space = 0;

		// 解析请求的行数
		int parsedLine = 1;

		byte[] lineName = new byte[this.headerNameBufferSize];
		byte[] lineValue = new byte[this.headerValueBufferSize];
		// 每行请求头参数名字节的末尾索引
		int lineNameIndex = 0;
		// 每行请求头参数值字节的末尾索引
		int lineValueIndex = 0;
		boolean nameByte = true;

		boolean prepareReadBody = false;

		byte readData;
		for (int i = 0; i < byteBuf.writerIndex(); i++) {
			readData = byteBuf.getByte(i);
			if (prepareReadBody) {
				// 准备存储请求体内容
				byte[] requestBodyLine = new byte[request.getContentLength()];

				byteBuf.getBytes(i, requestBodyLine);
				this.request.getParameters().getRequestBodyMB().setBytes(requestBodyLine, 0, requestBodyLine.length);
				break;
			} else if (parsedLine == 1) { // 请求首行字节内容
				if (space == 1 && readData == Constants.QUESTION) {
					questionIndex = i + 1 ; // 加一是为了跳过当前问号字节而在下一次直接存储空问号之后的uri参数
					uriArgs = true;
					continue;
				}

				if ( readData == Constants.SP ) { // " "
					if (space == 0) {
						// 存储行首到第一次出现空格之间的字符
						byte[] method = new byte[i]; 
						byteBuf.getBytes(0, method);
						this.request.method().setBytes(method, 0, method.length);
					} else if (space == 1) {
						if (uriArgs) { // 解析url参数
							uri = new byte[ questionIndex - laseEnd - 1 ];  // 存储第二次出现空格直接的字节，减去1是为了不读取?字节
							byteBuf.getBytes(laseEnd, uri); 

							byte[] args = new byte[i - questionIndex]; 
							byteBuf.getBytes(questionIndex , args);  // 从?字节后读取, 可能包含着片段标识符("&")
							this.request.queryString().setBytes(args, 0, args.length);
						} else {
							uri = new byte[ i - laseEnd ];  // 存储第二次出现空格直接的字节
							byteBuf.getBytes(laseEnd, uri); 
						}
						this.request.requestURI().setBytes(uri, 0, uri.length);
					}
					// 加一是为了跳过当前空格字节而在下一次直接存储空格之后的字节
					laseEnd = i + 1; 
					space++;
					continue;
				} else if (readData == Constants.CR) { // "/r"，到此字节可以视为已读取到行末
					// 存储第二次出现空格和回车之间的字节
					byte[] protocol = new byte[i-laseEnd]; 
					byteBuf.getBytes(laseEnd, protocol); 
					this.request.protocol().setBytes(protocol, 0, protocol.length);
					continue;
				}
			} else if (readData == Constants.LF && byteBuf.getByte(i+2) == Constants.LF) { // 在一个换行符之后两位还是一个换行符，那么之后会存在一个空行
				i += 2; // 跳过空行
				prepareReadBody = true;
				continue;
			} else if (parsedLine > 1 && readData != Constants.LF  && readData != Constants.CR && readData != Constants.SP) { // 只解析请求头中包含冒号的行，且忽略空格、、回车、换行符
				if (nameByte) {
					if (readData == Constants.COLON) { // 读取到冒号之后意味着之后的字节是请求头参数值
						nameByte = false;
					} else {
						if (readData >= Constants.uppercaseByteMin && readData <= Constants.uppercaseByteMax) {
							readData += Constants.LC_OFFSET;
						}

						lineName[lineNameIndex++] = readData;
					}
				} else  {
					lineValue[lineValueIndex++] = readData;
				}
				return true;
			}

			if (readData == Constants.LF ) { // 解析到换行符则意味着当前行已读取完成
				parsedLine++;
				if (lineNameIndex> 0) { // 确保已写入数据
					nameByte = true;
					MessageBytes messageByte = mimeHeaders.addHeadNameValue(lineName, 0, lineNameIndex + 1);
					// 有时字节数组中存储的数据并未充满整个数组
					messageByte.setBytes(lineValue, 0, lineValueIndex + 1);

					if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_LENGTH_BYTES) ) {
						request.setContentLength(messageByte.getLong());
					} else if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_TYPE_BYTES) ) {
						request.setContentType(messageByte);
					}

					lineName = new byte[this.headerNameBufferSize];
					lineValue = new byte[this.headerValueBufferSize];
					lineNameIndex = 0;
					lineValueIndex = 0;
				}
			}
		}

		return parsingHeader;
	}

	/**
	 * 读取和解析请求字节的内部类
	 * @author Azurite-Y
	 * @since 1.2
	 *
	 */
	private class RequestBufferEnhanceHanlder {
		private byte[] method = null;

		private byte[] uri = null;

		private byte[] uriArgs = null;
		private boolean uriArgsFlag = false;

		/** {@linkplain SessionConstants#QUESTION} 字节出现的索引下标 */
		private int questionIndex = 0;

		private byte[] protocol = null;
		/**
		 * 记录上次读取的字节索引
		 */
		private int laseEnd = 0;
		/**
		 * 记录单行请求数据中空格的出现次数
		 */
		private int space = 0;

		private byte[] lineName = new byte[headerNameBufferSize];
		private byte[] lineValue = new byte[headerValueBufferSize];
		
		/**
		 *  每行请求头参数名字节的末尾索引
		 */
		private int lineNameIndex = 0;
		/**
		 *  每行请求头参数值字节的末尾索引
		 */
		private int lineValueIindex = 0;
		/** 
		 * 标识当前读取的字节是否是请求头名称的一部分
		 */
		private boolean nameByte = true;

		/**
		 * 存储请求行字节的数组
		 */
//		private byte[] requestBodyLine = null;
		/**
		 * 开始读取请求体字节的标识
		 */
		private boolean prepareReadBody = false;

		/**
		 * 记录最近已读的三个字节
		 */
		private byte[] lastByte = new byte[3];
		/** 
		 * 已记录的字节数量
		 */
		private byte lastByteCount = 0;

		/**
		 * 已解析请求的行数
		 */
		private int parsedLine = 1;

		/**
		 * 记录当前读取字节在缓冲区中的索引
		 */
		private int i;

		private MimeHeaders mimeHeaders = request.getMimeHeaders();

		public RequestBufferEnhanceHanlder() {
			super();
		}

		/**
		 * 
		 * @param readData - 当前读取的字节
		 * @return true/false - 代表是否还需执行外层循环读取接下来的字节
		 * @throws IOException 
		 */
		boolean readAndParseRequestBytes(byte readData) throws IOException {
			byteBuf.writeByte(readData);
			i = byteBuf.writerIndex();

			System.out.print((char)readData);
			
//			if (prepareReadBody) {
					// 准备存储请求体内容
//				byte[] requestBodyLine = new byte[request.getContentLength()];
//				requestBodyLine[0] = readData;
			
//				@since 1.0 API 问题？会导致无法填充流中的后部分字节到数组中
//				socketWrapper.read(requestBodyLine, 1, requestBodyLine.length - 1);
				
//				@since 1.1 上传文件可能过大导致堆栈溢出
//				for (int j = 1; j < request.getContentLength(); j++) {
//					requestBodyLine[j] = (byte) socketWrapper.readByte();
//				}
//				request.setRequestBodyLineByte(requestBodyLine);
				
//				@since 1.2 延迟读取请求体数据，等到参数解析时按需读取
//				deferredReadRequestBody(readData);
				
//				return false;
//			} else 
				
			if (parsedLine == 1) { // 请求首行字节内容
				if (space == 1 && readData == Constants.QUESTION) {
					questionIndex = i + 1 ; // 加一是为了跳过当前问号字节而在下一次直接存储空问号之后的uri参数
					uriArgsFlag = true;
					return true;
				}

				if ( readData == Constants.SP ) { // " "
					if (space == 0) {
						// 存储行首到第一次出现空格之间的字符
						method = new byte[i -1]; 
						byteBuf.getBytes(0, method);
					} else if (space == 1) {
						if (uriArgsFlag) { // 解析url参数
							uri = new byte[ questionIndex - laseEnd - 1 ];  // 存储第二次出现空格直接的字节，减去1是为了不读取?字节
							byteBuf.getBytes(laseEnd, uri); 

							uriArgs = new byte[i - questionIndex]; 
							byteBuf.getBytes(questionIndex , uriArgs);  // 从?字节后读取
						} else {
							uri = new byte[ i - laseEnd -1];  // 存储第二次出现空格直接的字节
							byteBuf.getBytes(laseEnd, uri); 
						}
					}
					// 加一是为了跳过当前空格字节而在下一次直接存储空格之后的字节
					laseEnd = i; 
					space++;
					return true;
				} else if (readData == Constants.CR) { // "/r"，到此字节可以视为已读取到行末
					// 存储第二次出现空格和回车之间的字节
					protocol = new byte[i-laseEnd]; 
					byteBuf.getBytes(laseEnd, protocol); 
					return true;
				}
			} else if ( isReadNewLineByte(readData) ) { // 在一个换行符之后两位还是一个换行符，那么之后会存在一个空行
				prepareReadBody = true; // 接下来需要解析请求体
				
				// 读取到 get、head、delete时无需继续读取之后没有的请求体
				if ( RequestUtil.hasRequestBody(method) ) {
					deferredReadRequestBody();
				}
				return false;
				
			} else if (parsedLine > 1 && readData != Constants.LF  && readData != Constants.CR && readData != Constants.SP) { // 只解析请求头中包含冒号的行，且忽略空格、、回车、换行符
				if (nameByte) {
					if (readData == Constants.COLON) { // 读取到冒号之后意味着之后的字节是请求头参数值
						nameByte = false;
					} else {
						if (readData >= Constants.uppercaseByteMin && readData <= Constants.uppercaseByteMax) {
							readData += Constants.LC_OFFSET;
						}

						lineName[lineNameIndex++] = readData;
					}
				} else  {
					lineValue[lineValueIindex++] = readData;
				}
				return true;
			}

			if (readData == Constants.LF ) { // 解析到换行符则意味着当前行已读取完成
				parsedLine++;
				if (lineNameIndex> 0) { // 确保已写入数据
					nameByte = true;
					MessageBytes messageByte = mimeHeaders.addHeadNameValue(lineName, 0, lineNameIndex + 1);
					// 有时字节数组中存储的数据并未充满整个数组
					messageByte.setBytes(lineValue, 0, lineValueIindex + 1);

					if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_LENGTH_BYTES) ) {
						request.setContentLength(messageByte.getLong());
					} else if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_TYPE_BYTES) ) {
						request.setContentType(messageByte);
					}

					lineName = new byte[headerNameBufferSize];
					lineValue = new byte[headerValueBufferSize];
					lineNameIndex = 0;
					lineValueIindex = 0;
				}
			}

			return true;
		}

		/**
		 * 是否已读取到空行
		 * @param readData - 当前读取的字节
		 * @return true表示着已读取到请求内容的空行
		 */
		boolean isReadNewLineByte(byte readData) {
			if (lastByteCount <= 2) { // 一开始的数据填充阶段
				lastByte[lastByteCount++] = readData;
			} else { // 已记录了已读过的3个字节
				lastByte[0] = lastByte[1];
				lastByte[1] = lastByte[2];
				lastByte[2] = readData;

				if (readData == Constants.LF && lastByte[1] == Constants.CR && lastByte[0] == Constants.LF ) {
					return true;
				}
			}

			return false;
		}
		
		/**
		 * 填充解析到的相关请求参数
		 * @param request
		 */
		void prepareRequest(Request request) {
			// 相对较早的解析 ContentType
			request.parseContentType();
			
			request.scheme().setString("http");
//			request.scheme().setBytes(protocol);
			request.method().setBytes(method);
			
			request.protocol().setBytes(protocol);
			
			request.requestURI().setBytes(uri);
			if (uriArgsFlag) request.queryString().setBytes(uriArgs);
			
		}
		
		/**
		 * 延迟读取请求体数据
		 * 
		 * @param firstBodyByte - 已从流中读取到的第一个请求体数据
		 */
		void deferredReadRequestBody2(byte firstBodyByte) {
			Supplier<Byte> deferredSupplier = new Supplier<Byte>() {
				Byte data = null;
				
				@Override
				public Byte get() {
					try {
						data =  (Byte) socketWrapper.readByte();
					} catch (IOException e) {
						e.printStackTrace();
					}
					return data;
				}
			};
//			request.setFirstBodyByte(firstBodyByte);
			request.setRequestBodySupplier(deferredSupplier);
		}
	}
	
	/**
	 * 读取和解析请求字节的内部类
	 * 
	 * @author Azurite-Y
	 * @since 1.1
	 * @deprecated 考虑到上传文件可能很大，所以废弃
	 */
	private class RequestBufferHanlder {
		private byte[] method = null;

		private byte[] uri = null;

		private byte[] uriArgs = null;
		private boolean uriArgsFlag = false;

		/**
		 * {@linkplain SessionConstants#QUESTION} 字节出现的索引下标
		 */
		private int questionIndex = 0;

		private byte[] protocol = null;
		/**
		 * 记录上次读取的字节索引
		 */
		private int laseEnd = 0;
		/**
		 * 记录单行请求数据中空格的出现次数
		 */
		private int space = 0;

		private byte[] lineName = new byte[headerNameBufferSize];
		private byte[] lineValue = new byte[headerValueBufferSize];
		/**
		 *  每行请求头参数名字节的末尾索引
		 */
		private int lineNameIndex = 0;
		/**
		 *  每行请求头参数值字节的末尾索引
		 */
		private int lineValueIindex = 0;
		/** 
		 * 标识当前读取的字节是否是请求头名称的一部分
		 */
		private boolean nameByte = true;

		/**
		 * 存储请求行字节的数组
		 */
//		private byte[] requestBodyLine = null;
		/**
		 * 开始读取请求体字节的标识
		 */
		private boolean prepareReadBody = false;

		/**
		 * 记录最近已读的三个字节
		 */
		private byte[] lastByte = new byte[3];
		/** 
		 * 已记录的字节数量
		 */
		private byte lastByteCount = 0;

		/**
		 * 已解析请求的行数
		 */
		private int parsedLine = 1;

		/**
		 * 记录当前读取字节在缓冲区中的索引
		 */
		private int i;

		private MimeHeaders mimeHeaders = request.getMimeHeaders();

		/**
		 * 实例化一个 RequestBufferHanlder
		 */
		public RequestBufferHanlder() {
			super();
		}

		/**
		 * 
		 * @param readData - 当前读取的字节
		 * @return true/false 代表是否还需读取接下来的字节
		 * @throws IOException 
		 */
		boolean readAndParseRequestBytes(byte readData) throws IOException {
			byteBuf.writeByte(readData);
			i = byteBuf.writerIndex();

			if (prepareReadBody) {
					// 准备存储请求体内容
				byte[] requestBodyLine = new byte[request.getContentLength()];
				
				requestBodyLine[0] = readData;
				
				// API 问题？会导致无法填充流中的后部分字节到数组中
//				socketWrapper.read(requestBodyLine, 1, requestBodyLine.length - 1);
				
				
				for (int j = 1; j < request.getContentLength(); j++) {
					requestBodyLine[j] = (byte) socketWrapper.readByte();
				}
				request.setRequestBodyLineByte(requestBodyLine);
				
				return false;
			} else if (parsedLine == 1) { // 请求首行字节内容
				if (space == 1 && readData == Constants.QUESTION) {
					questionIndex = i + 1 ; // 加一是为了跳过当前问号字节而在下一次直接存储空问号之后的uri参数
					uriArgsFlag = true;
					return true;
				}

				if ( readData == Constants.SP ) { // " "
					if (space == 0) {
						// 存储行首到第一次出现空格之间的字符
						method = new byte[i -1]; 
						byteBuf.getBytes(0, method);
					} else if (space == 1) {
						if (uriArgsFlag) { // 解析url参数
							uri = new byte[ questionIndex - laseEnd - 1 ];  // 存储第二次出现空格直接的字节，减去1是为了不读取?字节
							byteBuf.getBytes(laseEnd, uri); 

							uriArgs = new byte[i - questionIndex]; 
							byteBuf.getBytes(questionIndex , uriArgs);  // 从?字节后读取
						} else {
							uri = new byte[ i - laseEnd -1];  // 存储第二次出现空格直接的字节
							byteBuf.getBytes(laseEnd, uri); 
						}
					}
					// 加一是为了跳过当前空格字节而在下一次直接存储空格之后的字节
					laseEnd = i; 
					space++;
					return true;
				} else if (readData == Constants.CR) { // "/r"，到此字节可以视为已读取到行末
					// 存储第二次出现空格和回车之间的字节
					protocol = new byte[i-laseEnd]; 
					byteBuf.getBytes(laseEnd, protocol); 
					return true;
				}
			} else if ( isReadNewLineByte(readData) ) { // 在一个换行符之后两位还是一个换行符，那么之后会存在一个空行
				prepareReadBody = true; // 接下来需要解析请求体
				return true;
			} else if (parsedLine > 1 && readData != Constants.LF  && readData != Constants.CR && readData != Constants.SP) { // 只解析请求头中包含冒号的行，且忽略空格、、回车、换行符
				if (nameByte) {
					if (readData == Constants.COLON) { // 读取到冒号之后意味着之后的字节是请求头参数值
						nameByte = false;
					} else {
						if (readData >= Constants.uppercaseByteMin && readData <= Constants.uppercaseByteMax) {
							readData += Constants.LC_OFFSET;
						}

						lineName[lineNameIndex++] = readData;
					}
				} else  {
					lineValue[lineValueIindex++] = readData;
				}
				return true;
			}

			if (readData == Constants.LF ) { // 解析到换行符则意味着当前行已读取完成
				parsedLine++;
				if (lineNameIndex> 0) { // 确保已写入数据
					nameByte = true;
					MessageBytes messageByte = mimeHeaders.addHeadNameValue(lineName, 0, lineNameIndex + 1);
					// 有时字节数组中存储的数据并未充满整个数组
					messageByte.setBytes(lineValue, 0, lineValueIindex + 1);

					if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_LENGTH_BYTES) ) {
						request.setContentLength(messageByte.getLong());
					} else if ( ByteArrayUtils.equalsByte(lineName, Constants.CONTENT_TYPE_BYTES) ) {
						request.setContentType(messageByte);
					}

					lineName = new byte[headerNameBufferSize];
					lineValue = new byte[headerValueBufferSize];
					lineNameIndex = 0;
					lineValueIindex = 0;
				}
			}

			return true;
		}

		/**
		 * 是否已读取到空行
		 * @param readData - 当前读取的字节
		 * @return true表示着已读取到请求内容的空行
		 */
		boolean isReadNewLineByte(byte readData) {
			if (lastByteCount <= 2) { // 一开始的数据填充阶段
				lastByte[lastByteCount++] = readData;
			} else { // 已记录了已读过的3个字节
				lastByte[0] = lastByte[1];
				lastByte[1] = lastByte[2];
				lastByte[2] = readData;

				if (readData == Constants.LF && lastByte[1] == Constants.CR && lastByte[0] == Constants.LF ) {
					return true;
				}
			}

			return false;
		}
		
		/**
		 * 填充解析到的相关请求参数
		 * @param request
		 */
		void prepareRequest(Request request) {
//			request.scheme().setString("http");
			request.method().setBytes(method);
			
			request.protocol().setBytes(protocol);
			
			request.requestURI().setBytes(uri);
			if (uriArgsFlag) request.queryString().setBytes(uriArgs);
		}
	}

}
