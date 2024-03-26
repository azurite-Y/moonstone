package org.zy.moonstone.core.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.exceptions.CloseNowException;
import org.zy.moonstone.core.util.RequestUtil;
import org.zy.moonstone.core.util.buf.ByteArrayUtils;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.net.SocketWrapperBase;
import org.zy.moonstone.core.util.net.interfaces.InputBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * @dateTime 2022年6月1日;
 * @author zy(azurite-Y);
 * @description
 */
public class Http11InputBuffer implements InputBuffer {
	private static final Logger logger = LoggerFactory.getLogger(Http11InputBuffer.class);

	/**
	 * 关联的请求对象
	 */
	private final Request request;

	/**
	 * 是否拒绝非法请求头名称
	 */
//	private final boolean rejectIllegalHeaderName;

	/** 记录HTTP请求行和请求头数据 */
	StringBuilder requestHeaderBuilder = new StringBuilder();

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
	 * HTTP head name 最大大小
	 */
	private final int headerNameBufferSize;

	/**
	 * HTTP head value 最大大小
	 */
	private final int headerValueBufferSize;

	/**
	 * 解析请求行进度. [0-初始 1-请求行 2-请求头 3-请求体]
	 */
	private int parsingRequestLinePhase = 0;

	
	// ----------------------------------------------------- 构造器 -----------------------------------------------------
	/**
	 * 根据给定的参数实例化一个 {@code Http11InputBuffer } 对象
	 * @param request
	 * @param headerNameBufferSize - 请求头参数名缓冲区极值
	 * @param headerValueBufferSize - 请求头参数值缓冲区极值
	 */
//	 * @param rejectIllegalHeaderName - 是否拒绝非法请求头名称
	public Http11InputBuffer(Request request, int headerNameBufferSize, int headerValueBufferSize, int maxHttpHeaderSize) {
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
		byteBuf.clear();
		parsingRequestLinePhase = 0;
		byteBuffer.clear();
		requestHeaderBuilder.delete(0, requestHeaderBuilder.length());
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

		int nRead = 0;
		if (this.socketWrapper == null) throw new CloseNowException();

		RequestBufferEnhanceHanlder requestBufferEnhanceHanlder = new RequestBufferEnhanceHanlder();

		boolean readding = true;
		
		if ( logger.isDebugEnabled() ) {
			logger.debug("开始读取请求数据...");
		}
		
		basic: while(readding) {
			nRead += this.socketWrapper.read(true, this.byteBuffer);
			this.byteBuffer.flip();
			if (byteBuffer.remaining() == 0) {
				return false;
			}
			while (byteBuffer.hasRemaining()) {
				readding = requestBufferEnhanceHanlder.readAndParseRequestBytes(byteBuffer.get());
				if (!readding) {
					// 不继续处理缓冲区数据
					break basic;
				}
			}
			// 清空请求报文缓冲区，再此清空之后方便继续读取之后的请求报文
			byteBuffer.clear();
		}
		
		byteBuf.clear();
		if ( logger.isDebugEnabled() ) {
			logger.debug("Request Header: \n{}", requestHeaderBuilder.toString().trim());
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
		socketWrapper = null;
		parsingRequestLinePhase = 0;
        request.recycle();
        byteBuf.clear();
        byteBuffer.clear();
		requestHeaderBuilder.delete(0, requestHeaderBuilder.length());
	}
	
	void init(SocketWrapperBase<?> socketWrapper) {
		this.socketWrapper = socketWrapper;
	}
	
    int getParsingRequestLinePhase() {
        return parsingRequestLinePhase;
    }
	
	/**
	 * 延迟读取请求体数据
	 */
	void deferredReadRequestBody() {
		if (logger.isDebugEnabled()) {
			logger.debug("InputStreamBuffer: [pos={} lim={} cap={}]", this.byteBuffer.position(), 
					this.byteBuffer.limit(), this.byteBuffer.remaining());
		}
		
		Supplier<Byte> deferredSupplier = new Supplier<Byte>() {
			
			@Override
			public Byte get() {
				try {
					if (!byteBuffer.hasRemaining()) {
						// 清空缓冲区数据，使之变为可写状态
						byteBuffer.clear();
						socketWrapper.read(true, byteBuffer);
						// 转换为可读状态
						byteBuffer.flip();
					}
					return byteBuffer.get();
					
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}
		};
		
		request.setRequestBodySupplier(deferredSupplier);
//		
//		while(this.byteBuffer.hasRemaining()) {
//			System.out.print( (char)(byte)deferredSupplier.get() );
//		}
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

		/** {@linkplain Constants#QUESTION} 字节出现的索引下标 */
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
			if (byteBuf.writerIndex() == byteBuf.maxCapacity()) {
				throw new IllegalArgumentException("请求头数据大于限制值[" + byteBuf.maxCapacity() + "]" );
			}
			byteBuf.writeByte(readData);
//			System.out.print((char)readData);
			
			i = byteBuf.writerIndex();

			if ( logger.isDebugEnabled() ) {
				requestHeaderBuilder.append((char)readData);
			}
			
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
							uri = new byte[ questionIndex - laseEnd - 2];  // 存储第二次出现空格直接的字节，减去2是为了不读取当前的空格符和?字节
							byteBuf.getBytes(laseEnd, uri); 
							
							uriArgs = new byte[i - questionIndex]; 
							byteBuf.getBytes(questionIndex - 1 , uriArgs);  // 从?字节后读取
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
					parsingRequestLinePhase = 1;
					return true;
				}
			} else if ( isReadNewLineByte(readData) ) { // 在一个换行符之后两位还是一个换行符，那么之后会存在一个空行
				if ( logger.isDebugEnabled() ) {
					logger.debug("请求头读取完毕...");
				}
				parsingRequestLinePhase = 2;
				// 读取到 get、head、delete时无需继续读取之后没有的请求体
				if ( RequestUtil.hasRequestBody(method) ) {
					parsingRequestLinePhase = 3;
					deferredReadRequestBody();
				}
				return false;
			} else if (parsedLine > 1 && readData != Constants.LF  && readData != Constants.CR && readData != Constants.SP) { // 只解析请求头中包含冒号的行，且忽略空格、、回车、换行符
				if (nameByte) {
					if (readData == Constants.COLON) { // 读取到冒号之后意味着之后的字节是请求头参数值
						parsingRequestLinePhase = 2;
						nameByte = false;
					} else {
						if (readData >= Constants.uppercaseByteMin && readData <= Constants.uppercaseByteMax) {
							readData -= Constants.LC_OFFSET;
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
					MessageBytes messageByte = mimeHeaders.addHeadNameValue(lineName, 0, lineNameIndex);
					// 有时字节数组中存储的数据并未充满整个数组
					messageByte.setBytes(lineValue, 0, lineValueIindex);

					
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
			request.method().setBytes(method);
			
			request.protocol().setBytes(protocol);
			
			request.requestURI().setBytes(uri);
			if (uriArgsFlag) request.queryString().setBytes(uriArgs);
		}
	}
}
