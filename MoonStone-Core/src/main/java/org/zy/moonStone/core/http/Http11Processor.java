package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.exceptions.HeadersTooLargeException;
import org.zy.moonstone.core.interfaces.connector.Adapter;
import org.zy.moonstone.core.util.ErrorState;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.ServerInfo;
import org.zy.moonstone.core.util.buf.ByteArrayUtils;
import org.zy.moonstone.core.util.buf.ByteChunk;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.http.FastHttpDateFormat;
import org.zy.moonstone.core.util.http.MimeHeaders;
import org.zy.moonstone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonstone.core.util.net.*;
import org.zy.moonstone.core.util.net.interfaces.HttpOutputBuffer;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

/**
 * @dateTime 2022年5月20日;
 * @author zy(azurite-Y);
 * @description
 */
public class Http11Processor extends AbstractProcessor {
	private static final Logger logger = LoggerFactory.getLogger(Http11Processor.class);

	private final AbstractHttp11Protocol<?> protocol;

	/**
	 * 输入缓冲区
	 */
	private final Http11InputBuffer httpInputBuffer;

	/**
	 * 输出缓冲区
	 */
	private final Http11OutputBuffer httpOutputBuffer;

	/**
	 * Keep-alive.
	 */
	private volatile boolean keepAlive = true;

	/**
	 * 用于指示套接字应保持打开状态的标志（例如，用于 keepalive 或发送文件。
	 */
	private boolean openSocket = false;

	/**
	 * 发送文件数据
	 */
	private SendfileDataBase sendfileData = null;

	/**
	 * 请求的内容分隔符(如果为false，连接将在请求结束时关闭)。
	 */
	private boolean contentDelimitation = true;

	/**
	 * 指示请求头是否已完全读取的标志
	 */
	private boolean readComplete = true;
	
	

	public Http11Processor(AbstractHttp11Protocol<?> protocol, Adapter adapter) {
		super(adapter);
		this.protocol = protocol;

		httpInputBuffer = new Http11InputBuffer(request, protocol.getMaxHttpHeaderNameSize(), protocol.getMaxHttpHeaderValueSize(), protocol.getMaxHttpHeaderSize());
		request.setInputBuffer(httpInputBuffer);

		httpOutputBuffer = new Http11OutputBuffer(response, protocol.getMaxHttpHeaderSize());
		response.setHttpOutputBuffer(httpOutputBuffer);

		//		httpOutputBuffer.addFilter(new IdentityOutputFilter());
		httpOutputBuffer.addFilter(new ChunkedOutputFilter());
		httpOutputBuffer.addFilter(new GzipOutputFilter());

	}

	@Override
	public SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
		RequestInfo rp = request.getRequestProcessor();
		rp.setStage(Globals.STAGE_PARSE);

		// 设置并初始化IO
		setSocketWrapper(socketWrapper);

		// Flags
		keepAlive = true;
		openSocket = false;
		readComplete = true;
		boolean keptAlive = false;
		SendfileState sendfileState = SendfileState.DONE;

		try {
			if (protocol.isPaused()) {
				// 503 - 服务不可用
				response.setStatus(503);
				setErrorState(ErrorState.CLOSE_CLEAN, null);
			} else {
				keptAlive = true;
				// 设置最大请求头数
				request.getMimeHeaders().setLimit(protocol.getMaxHeaderCount());

				// 读取请求数据，并解析请求头
				if (!httpInputBuffer.readAndParseRequestBytes(keptAlive, protocol.getConnectionTimeout(), protocol.getKeepAliveTimeout())) {
					handleIncompleteRequestLineRead();
				} else {
					/*
					 * 请求正常处理之后才进行如下逻辑
					 * 1. prepareRequest(): 解析请求头
					 * 2. getAdapter().service(request, response): 请求处理
					 * 3. endRequest(): 写入响应最后的数据
					 * 4. this.httpInputBuffer#nextRequest() this.httpOutputBuffer#nextRequest(): 重置输入输出缓冲区
					 * 5. sendfileState = processSendfile(socketWrapper): 处理设置的Sendfile数据
					 */
					if (getErrorState().isIoAllowed()) {
						rp.setStage(Globals.STAGE_PREPARE);
						try {
							prepareRequest();
						} catch (Throwable t) {
							ExceptionUtils.handleThrowable(t);
							if (logger.isDebugEnabled()) {
								logger.debug("准备请求错误", t);
							}
							// 500 - 服务器内部错误
							response.setStatus(500);
							setErrorState(ErrorState.CLOSE_CLEAN, t);
						}
					}

					int maxKeepAliveRequests = protocol.getMaxKeepAliveRequests();
					if (maxKeepAliveRequests == 1) {
						keepAlive = false;
					} else if (maxKeepAliveRequests > 0 && socketWrapper.decrementKeepAlive() <= 0) {
						keepAlive = false;
					}

					if (getErrorState().isIoAllowed()) {
						try {
							rp.setStage(Globals.STAGE_SERVICE);
							getAdapter().service(request, response);
							/**
							 * 在发生严重错误之前提交响应时处理。抛出ServletException应将状态设置为500并设置errorException。
							 * 如果在这里失败，那么响应可能已经提交，因此无法尝试设置头。
							 */
							if(keepAlive && !getErrorState().isError() && !isAsync() && statusDropsConnection(response.getStatus())) {
								setErrorState(ErrorState.CLOSE_CLEAN, null);
							}
						} catch (InterruptedIOException e) {
							setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
						} catch (HeadersTooLargeException e) {
							logger.error("请求解析异常", e);
							// 不应该提交响应，但无论如何都要检查它以确保安全
							if (response.isCommitted()) {
								setErrorState(ErrorState.CLOSE_NOW, e);
							} else {
								response.reset();
								response.setStatus(500);
								setErrorState(ErrorState.CLOSE_CLEAN, e);
								response.setHeader("Connection", "close");
							}
						} catch (Throwable t) {
							ExceptionUtils.handleThrowable(t);
							logger.error("请求解析异常", t);
							// 500 - 内部服务器错误
							response.setStatus(500);
							setErrorState(ErrorState.CLOSE_CLEAN, t);
							getAdapter().log(request, response, 0);
						}

						// 完成请求的处理
						rp.setStage(Globals.STAGE_ENDINPUT);
						if (!isAsync()) {
							// 如果这是一个异步请求，那么请求在完成时结束。在这种情况下，AsyncContext负责调用endRequest()。
							endRequest();
						}
						rp.setStage(Globals.STAGE_ENDOUTPUT);

						// 如果出现错误，请确保请求计数为和错误，并更新统计信息计数器
						if (getErrorState().isError()) {
							response.setStatus(500);
						}

						if (!isAsync() || getErrorState().isError()) {
							request.updateCounters();
							if (getErrorState().isIoAllowed()) {
								this.httpInputBuffer.nextRequest();
								this.httpOutputBuffer.nextRequest();
							}
						}

						if (!protocol.getDisableUploadTimeout()) {
							int connectionTimeout = protocol.getConnectionTimeout();
							if(connectionTimeout > 0) {
								socketWrapper.setReadTimeout(connectionTimeout);
							} else {
								socketWrapper.setReadTimeout(0);
							}
						}

						rp.setStage(Globals.STAGE_KEEPALIVE);
						sendfileState = processSendfile(socketWrapper);
					}
				}
				
			}
		} catch (IOException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Http11Processor 解析请求偷异常", e);
			}
			setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
		} catch (Throwable throwable) {
			ExceptionUtils.handleThrowable(throwable);

			if (logger.isDebugEnabled()) {
				logger.info("请求头解析异常", throwable);
			} else if (logger.isInfoEnabled()) {
				logger.debug("请求头解析异常", throwable);
			}
			// 400
			response.setStatus(400);
			setErrorState(ErrorState.CLOSE_CLEAN, throwable);
		}

		rp.setStage(Globals.STAGE_ENDED);

		if (getErrorState().isError() || (protocol.isPaused() && !isAsync())) {
			return SocketState.CLOSED;
		} else if (isAsync()) {
			return SocketState.LONG;
		} else if (isUpgrade()) {
			return SocketState.UPGRADING;
		} else {
			if (sendfileState == SendfileState.PENDING) {
				return SocketState.SENDFILE;
			} else {
				if (openSocket) {
					if (readComplete) {
						return SocketState.OPEN;
					} else {
						return SocketState.END;
					}
				} else {
					return SocketState.CLOSED;
				}
			}
		}
	}

	/*
	 * 不再向应用程序传递更多的输入。根据错误和期望状态，剩余的输入将被吞噬或连接将被丢弃。
	 */
	private void endRequest() {
		if (!getErrorState().isError()) {
			// 需要在此处再次检查此项，以防响应在需要关闭连接的错误发生之前提交
			checkExpectationAndResponseStatus();
		}

		if (getErrorState().isIoAllowed()) {
			try {
				action(ActionCode.COMMIT, null);
				httpOutputBuffer.end();
			} catch (IOException e) {
				setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				setErrorState(ErrorState.CLOSE_NOW, t);
				logger.error("响应刷新", t);
			}
		}
	}

	@Override
	public UpgradeToken getUpgradeToken() {
		return null;
	}

	@Override
	public void pause() {}

	@Override
	protected final void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
		super.setSocketWrapper(socketWrapper);
		httpInputBuffer.init(socketWrapper);
		httpOutputBuffer.init(socketWrapper);
	}

	@Override
	protected void prepareResponse() throws IOException {
		boolean entityBody = true;

		OutputFilter[] outputFilters = this.httpOutputBuffer.getFilters();

		int statusCode = response.getStatus();
		if (statusCode < 200 || statusCode == 204 || statusCode == 205 || statusCode == 304) {
			// 没有实体
			entityBody = false;
			if (statusCode == 205) {
				// RFC 7231 在这种情况下，要求服务器显式地发出空响应的信号
				response.setContentLength(0);
			} else {
				response.setContentLength(-1);
			}
		}

		// Sendfile support
		if (protocol.getUseSendfile()) {
			prepareSendfile(httpOutputBuffer);
		}

		// 检查压缩情况
		boolean useCompression = false;
		if (entityBody && sendfileData == null) {
			useCompression = protocol.useCompression(request, response);
		}

		MimeHeaders headers = response.getMimeHeaders();
		// 响应可能包含实体 header 头
		if (entityBody || statusCode == HttpServletResponse.SC_NO_CONTENT) {
			String contentType = response.getContentType();
			if (contentType != null) {
				headers.setValue("Content-Type").setString(contentType);
			}
			String contentLanguage = response.getContentLanguage();
			if (contentLanguage != null) {
				headers.setValue("Content-Language").setString(contentLanguage);
			}
		}

		// contentLength 由数据返回方设置，如 thymeleaf 或在其他地方手动设置
		long contentLength = response.getContentLengthLong();
		// 检查http连接是否被标记为close
		boolean connectionClosePresent = isConnectionToken(headers, Constants.CLOSE_TOKEN);
		if ( response.getTrailerFields() != null ) {
			httpOutputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
			headers.addHeadNameValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
		} else if (contentLength != -1) {
			headers.setValue("Content-Length").setLong(contentLength);
		} else {
			if ( entityBody && !connectionClosePresent ) {
				httpOutputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
				headers.addHeadNameValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
			}
		}

		if (useCompression) {
			httpOutputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
			headers.addHeadNameValue(Constants.CONTENT_ENCODING).setString(Constants.GZIP);
			
		}

		// 添加日期header头
		if (headers.getValue("Date") == null) {
			headers.addHeadNameValue("Date").setString(FastHttpDateFormat.getCurrentDate());
		}

		if ( (entityBody) && (!contentDelimitation) ) {
			// 在请求之后将连接标记为关闭，并添加connection: close标头
			keepAlive = false;
		}

		// 这可能会在处理Connection header头之前禁用keep-alive检查
		checkExpectationAndResponseStatus();

		// 如果这么早就知道请求是错误的，那么就添加Connection: close报头。
		if (keepAlive && statusDropsConnection(statusCode)) {
			keepAlive = false;
		}
		if (!keepAlive) {
			// 避免两次添加 close header
			if (!connectionClosePresent) {
				headers.addHeadNameValue(Constants.CONNECTION).setString(Constants.CLOSE_TOKEN);
			}
		} else if (!getErrorState().isError()) {
			headers.addHeadNameValue(Constants.CONNECTION).setString(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);

			if (protocol.getUseKeepAliveResponseHeader()) {
				// 检查之前的连接是否还在保持
				boolean connectionKeepAlivePresent = isConnectionToken(request.getMimeHeaders(), Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);

				if (connectionKeepAlivePresent) {
					int keepAliveTimeout = protocol.getKeepAliveTimeout();

					if (keepAliveTimeout > 0) {
						String value = "timeout=" + keepAliveTimeout / 1000L;
						headers.setValue(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN).setString(value);

						// 如果已经有连接header头，则追加，否则创建header头
						MessageBytes connectionHeaderValue = headers.getValue(Constants.CONNECTION);
						if (connectionHeaderValue == null) {
							headers.addHeadNameValue(Constants.CONNECTION).setString(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);
						} else {
							connectionHeaderValue.setString(connectionHeaderValue.getString() + ", " + Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);
						}
					}
				}
			}
		}

		// 添加服务器头
		String server = protocol.getServer();
		if (server == null) {
			if (protocol.getServerRemoveAppProvidedValues()) {
				headers.removeHeader("server");
			} else {
				headers.addHeadNameValue("server").setString(ServerInfo.getServerInfo());
			}
		} else {
			// 服务器总是覆盖应用程序可能设置的任何内容
			headers.setValue("Server").setString(server);
		}

		// 构建响应头
		try {
			httpOutputBuffer.sendStatus();

			int size = headers.size();
			for (int i = 0; i < size; i++) {
				httpOutputBuffer.sendHeader(headers.getHeadName(i), headers.getHeadValue(i));
			}
			httpOutputBuffer.endHeaders();
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			// 如果出现问题，需重置header头缓冲区，以便可以改为写入错误响应
			httpOutputBuffer.resetHeaderBuffer();
			throw t;
		}

		httpOutputBuffer.commit();
	}

	/**
	 * 检查指定 token 是否存在于 MimeHeaders 的 "connection" header下
	 * 
	 * @param headers - MimeHeaders 对象
	 * @param token - 测试token
	 * @return true表示是 "connection" 的值
	 * 
	 * @throws IOException
	 */
	private static boolean isConnectionToken(MimeHeaders headers, String token) throws IOException {
		MessageBytes connection = headers.getValue(Constants.CONNECTION);
		if (connection == null) {
			return false;
		}

		switch (token) {
		case Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN:
			return true;
		case Constants.CLOSE_TOKEN:
			return true;
		default: 
			return false;
		}
	}

	private void prepareSendfile(HttpOutputBuffer outputBuffer) {
		String fileName = (String) request.getAttribute(Constants.SENDFILE_FILENAME_ATTR);
		if (fileName == null) {
			sendfileData = null;
		} else {
			// 此处未发送实体
			contentDelimitation = true;
			long pos = ((Long) request.getAttribute(Constants.SENDFILE_FILE_START_ATTR)).longValue();
			long end = ((Long) request.getAttribute(Constants.SENDFILE_FILE_END_ATTR)).longValue();
			long len = end - pos;
			sendfileData = socketWrapper.createSendfileData(fileName, pos, end - pos);
			this.response.setContentLength(len);
		}
	}

	/**
	 * 如果需要，触发 Sendfile 处理
	 *
	 * @return 发送文件处理的状态
	 */
	private SendfileState processSendfile(SocketWrapperBase<?> socketWrapper) {
		openSocket = keepAlive;
		// 完成相当于未使用sendfile
		SendfileState result = SendfileState.DONE;
		// 根据需要发送文件:添加套接字发送文件和结束
		if (sendfileData != null && !getErrorState().isError()) {
			if (keepAlive) {
				sendfileData.keepAliveState = SendfileKeepAliveState.OPEN;
			} else {
				sendfileData.keepAliveState = SendfileKeepAliveState.NONE;
			}

			result = socketWrapper.processSendfile(sendfileData);

			switch (result) {
			case ERROR:
				// Write failed
				if (logger.isDebugEnabled()) {
					logger.debug("发送文件失败");
				}
				setErrorState(ErrorState.CLOSE_CONNECTION_NOW, null);
				//$FALL-THROUGH$
			default:
				sendfileData = null;
			}
		}
		return result;
	}

	@Override
	protected void finishResponse() throws IOException {
		httpOutputBuffer.end();
	}

	@Override
	protected void ack() {
		// 确认请求 如果有意义，则发回 100 状态（响应尚未提交，并且客户端指定了 100 继续的期望）
		if (!response.isCommitted() && request.hasExpectation()) {
			try {
				httpOutputBuffer.sendAck();
			} catch (IOException e) {
				setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
			}
		}		
	}

	@Override
	protected void flush() throws IOException {
		httpOutputBuffer.flush();
	}

	@Override
	protected void setSwallowResponse() {
		httpOutputBuffer.responseFinished = true;
	}

	@Override
	protected void registerReadInterest() {
		socketWrapper.registerReadInterest();
	}

	@Override
	protected boolean isTrailerFieldsReady() {
		// socket 输入流读取不支持对请求的chunked数据块解析
		return false;
	}

	/**
	 * 
	 * @see {@link NioSocketWrapper#doWrite(boolean, ByteBuffer)}
	 * @see {@link Http11OutputBuffer.SocketOutputBuffer#doWrite(ByteBuffer) }
	 * @see {@link NioChannel#write(ByteBuffer) }
	 */
	@Override
	protected boolean flushBufferedWrite() throws IOException {
		return false;
	}

	@Override
	protected SocketState dispatchEndRequest() throws IOException {
		if (!keepAlive || protocol.isPaused()) {
			return SocketState.CLOSED;
		} else {
			endRequest();
			httpInputBuffer.nextRequest();
			httpOutputBuffer.nextRequest();
			if (socketWrapper.isReadPending()) {
				return SocketState.LONG;
			} else {
				return SocketState.OPEN;
			}
		}
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	private void prepareRequest() {
		MimeHeaders mimeHeaders = request.getMimeHeaders();

		// 检查 connection 请求头
		MessageBytes connectionValueMB = mimeHeaders.getValue(Constants.CONNECTION);
		if (connectionValueMB != null && !connectionValueMB.isNull()) {
			if (ByteArrayUtils.equalsByte(connectionValueMB.getByteChunk().getBuffer(), Constants.CLOSE_BYTES)) {
				keepAlive = false;
			} else if (ByteArrayUtils.equalsByte(connectionValueMB.getByteChunk().getBuffer(), Constants.KEEP_ALIVE_HEADER_VALUE_BYTES)) {
				keepAlive = true;
			}
		}

		MessageBytes expectMB = mimeHeaders.getValue("expect");
		if (expectMB != null && !expectMB.isNull()) {
			if (expectMB.toString().trim().equalsIgnoreCase("100-continue")) {
				request.setExpectation(true);
			} else {
				response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
				setErrorState(ErrorState.CLOSE_CLEAN, null);
			}
		}

		// 检查 host 请求头
		MessageBytes hostValueMB = null;
		try {
			hostValueMB = mimeHeaders.getUniqueValue("host");
		} catch (IllegalArgumentException iae) {
			// 不允许有多个主机报头
			badRequest("不允许有多个主机报头");
		}
		if (hostValueMB == null) {
			badRequest("无 Host Header");
		} else {
			ByteChunk hostValueByteChunk = hostValueMB.getByteChunk();

			byte[] buffer = hostValueByteChunk.getBuffer();
			int leng = hostValueByteChunk.getEnd();
			for (int i = 0; i < leng; i++) {
				if (buffer[i] == Constants.COLON) {
					request.setServerPort( Integer.valueOf(new String(buffer, i + 1, leng - i - 1).trim()) );
					request.serverName().setBytes(buffer, 0 , i);
					break;
				}
			}
		}
	}

	private void badRequest(String errorKey) {
		response.setStatus(400);
		setErrorState(ErrorState.CLOSE_CLEAN, null);
		if (logger.isDebugEnabled()) {
			logger.debug(errorKey);
		}
	}

	/**
	 * 处理不完整的请求行读取
	 * @return
	 */
	private boolean handleIncompleteRequestLineRead() {
		// 请求未处理完，保持 socket连接
		openSocket = true;
		
		if (protocol.isPaused()) {
			// 部分处理请求，因此需要响应
			response.setStatus(503);
			setErrorState(ErrorState.CLOSE_CLEAN, null);
			return false;
		} 
		
		if (httpInputBuffer.getParsingRequestLinePhase() == 0) {
			// 需要保持处理器与套接字关联
			readComplete = false;
		}
		return true;
	}

	
    @Override
    public final void recycle() {
        getAdapter().checkRecycled(request, response);
        super.recycle();
        httpInputBuffer.recycle();
        httpOutputBuffer.recycle();
        socketWrapper = null;
        sendfileData = null;
        sslSupport = null;
    }
	
	/**
	 * 确定是否必须因为 HTTP 状态码而断开连接。 使用与 Apache/httpd 相同的代码列表。
	 */
	private static boolean statusDropsConnection(int status) {
		return status == 400 /* SC_BAD_REQUEST */ ||
				status == 408 /* SC_REQUEST_TIMEOUT */ ||
				status == 411 /* SC_LENGTH_REQUIRED */ ||
				status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
				status == 414 /* SC_REQUEST_URI_TOO_LONG */ ||
				status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
				status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
				status == 501 /* SC_NOT_IMPLEMENTED */;
	}

	/**
	 * 检查请求的异常状态和响应状态
	 */
	private void checkExpectationAndResponseStatus() {
		if (request.hasExpectation() && (response.getStatus() < 200 || response.getStatus() > 299)) {
			/*
			 * 客户端发送了期望:100-continue，但收到了非2xx的最终响应。禁用keep-alive(如果启用)，以确保连接已关闭。
			 * 有些客户端可能仍然发送主体，有些可能发送下一个请求。没有办法进行区分，所以关闭连接以迫使客户端发送下一个请求。
			 */
			keepAlive = false;
		}
	}
	
	@Override
	public String toString() {
		return "Http11Processor [protocol=" + protocol + ", keepAlive=" + keepAlive + ", openSocket=" + openSocket + ", sendfileData="+ sendfileData + "]";
	}
}
