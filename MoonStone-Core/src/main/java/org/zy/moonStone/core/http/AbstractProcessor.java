package org.zy.moonStone.core.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.RequestDispatcher;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.exceptions.CloseNowException;
import org.zy.moonStone.core.interfaces.connector.Adapter;
import org.zy.moonStone.core.interfaces.container.AsyncContextCallback;
import org.zy.moonStone.core.util.ErrorState;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.http.ActionCode;
import org.zy.moonStone.core.util.http.ActionHook;
import org.zy.moonStone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonStone.core.util.net.DispatchType;
import org.zy.moonStone.core.util.net.SocketEvent;
import org.zy.moonStone.core.util.net.SocketWrapperBase;
import org.zy.moonStone.core.util.net.UpgradeToken;
import org.zy.moonStone.core.util.net.interfaces.SSLSupport;

/**
 * @dateTime 2022年5月20日;
 * @author zy(azurite-Y);
 * @description 提供所有支持的协议（当前为 HTTP）通用的功能和属性，用于处理单个请求/响应。
 */
public abstract class AbstractProcessor extends AbstractProcessorLight implements ActionHook {
	protected final Adapter adapter;
	protected final AsyncStateMachine asyncStateMachine;
	private volatile long asyncTimeout = -1;

	/**
	 * 调度超时时跟踪当前的异步生成。 在分配容器线程和启动超时处理所需的时间内，应用程序可能完成这一代异步处理并启动新的异步处理。
	 * 如果超时是针对新一代处理的，则可能会发生响应混淆。 该字段用于确保处理的任何超时事件都是针对当前异步生成的。 这可以防止响应混淆。
	 */
	private volatile long asyncTimeoutGeneration = 0;
	protected final Request request;
	protected final Response response;
	protected volatile SocketWrapperBase<?> socketWrapper = null;
	protected volatile SSLSupport sslSupport;

	/**
	 * 当前正在处理的请求/响应的错误状态
	 */
	private ErrorState errorState = ErrorState.NONE;

	public AbstractProcessor(Adapter adapter) {
		this(adapter, new Request(), new Response());
	}

	protected AbstractProcessor(Adapter adapter, Request coyoteRequest, Response coyoteResponse) {
		this.adapter = adapter;
		asyncStateMachine = new AsyncStateMachine(this);
		request = coyoteRequest;
		response = coyoteResponse;
		response.setHook(this);
		request.setResponse(response);
		request.setHook(this);
		response.setRequest(request);
//		userDataHelper = new UserDataHelper(getLogger());
	}

	@Override
	public final SocketState dispatch(SocketEvent status) throws IOException {
		if (status == SocketEvent.OPEN_WRITE && response.getWriteListener() != null) {
			asyncStateMachine.asyncOperation();
			try {
				if (flushBufferedWrite()) {
					return SocketState.LONG;
				}
			} catch (IOException ioe) {
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("无法写入异步数据.", ioe);
				}
				status = SocketEvent.ERROR;
				request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
			}
		} else if (status == SocketEvent.OPEN_READ && request.getReadListener() != null) {
			dispatchNonBlockingRead();
		} else if (status == SocketEvent.ERROR) {
			// 非容器线程发生I/O错误。这包括:由轮询器(NIO和APR)触发的读/写超时- NIO 2 中的完成处理程序失败
			if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null) {
				// 因为容器线程上没有发生错误，所以尚未设置请求的错误属性。 如果 socketWrapper
				// 有异常可用，请使用它在此处设置请求的错误属性，以便错误处理可以看到它。
				request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, socketWrapper.getError());
			}

			if (request.getReadListener() != null || response.getWriteListener() != null) {
				// 错误发生在非阻塞 I/O 期间。 设置正确的状态，否则错误处理将触发 ISE。
				asyncStateMachine.asyncOperation();
			}
		}

		RequestInfo rp = request.getRequestProcessor();
		try {
			rp.setStage(Globals.STAGE_SERVICE);
			if (!getAdapter().asyncDispatch(request, response, status)) {
				setErrorState(ErrorState.CLOSE_NOW, null);
			}
		} catch (InterruptedIOException e) {
			setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			setErrorState(ErrorState.CLOSE_NOW, t);
			getLogger().error("请求处理", t);
		}

		rp.setStage(Globals.STAGE_ENDED);

		if (getErrorState().isError()) {
			request.updateCounters();
			return SocketState.CLOSED;
		} else if (isAsync()) {
			return SocketState.LONG;
		} else {
			request.updateCounters();
			return dispatchEndRequest();
		}

	}

	/**
	 * 如果新错误状态比当前错误状态更严重，则将当前错误状态更新为新错误状态。
	 * 
	 * @param errorState - 错误状态详细信息
	 * @param t          - 发生的错误
	 */
	protected void setErrorState(ErrorState errorState, Throwable t) {
		// 使用返回值可以避免在一个异步周期中处理多个异步错误
		 boolean setError = response.setError();
		boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
		this.errorState = this.errorState.getMostSevere(errorState);
		// 不要更改IOException的状态代码，因为这几乎肯定是客户端断开连接，在这种情况下，最好保持原始状态代码
		if (response.getStatus() < 400 && !(t instanceof IOException)) {
			response.setStatus(500);
		}
		if (t != null) {
			request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
		}
		if (blockIo && isAsync() && setError) {
			if (asyncStateMachine.asyncError()) {
				processSocketEvent(SocketEvent.ERROR, true);
			}
		}
			 
	}

	protected ErrorState getErrorState() {
		return errorState;
	}

	@Override
	public Request getRequest() {
		return request;
	}

	/**
	 * @return 关联的适配器
	 */
	public Adapter getAdapter() {
		return adapter;
	}

	/**
	 * 设置正在使用的套接字包装器
	 * 
	 * @param socketWrapper - 套接字包装器
	 */
	protected void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
		this.socketWrapper = socketWrapper;
	}

	/**
	 * @return 正在使用的套接字包装器
	 */
	protected final SocketWrapperBase<?> getSocketWrapper() {
		return socketWrapper;
	}

	@Override
	public final void setSslSupport(SSLSupport sslSupport) {
		this.sslSupport = sslSupport;
	}

	/**
	 * 提供一种在容器线程上触发处理的机制
	 * 
	 * @param runnable - 表示需要在容器线程上进行的处理的任务
	 */
	protected void execute(Runnable runnable) {
		SocketWrapperBase<?> socketWrapper = this.socketWrapper;
		if (socketWrapper == null) {
			throw new RejectedExecutionException("socketWrapper 不能为空");
		} else {
			socketWrapper.execute(runnable);
		}
	}

	@Override
	public boolean isAsync() {
		return asyncStateMachine.isAsync();
	}

	@Override
	public SocketState asyncPostProcess() {
		return asyncStateMachine.asyncPostProcess();
	}

	/**
	 * 在分派到适配器之前对非阻塞读取执行任何必要的处理
	 */
	protected void dispatchNonBlockingRead() {
		asyncStateMachine.asyncOperation();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 该基类的子类表示单个请求/响应对。因此，要处理的超时是 Servlet 异步处理超时。
	 */
	@Override
	public void timeoutAsync(long now) {
		if (now < 0) {
			doTimeoutAsync();
		} else {
			long asyncTimeout = getAsyncTimeout();
			if (asyncTimeout > 0) {
				long asyncStart = asyncStateMachine.getLastAsyncStart();
				if ((now - asyncStart) > asyncTimeout) {
					doTimeoutAsync();
				}
			} else if (!asyncStateMachine.isAvailable()) {
				// 如果关联的 Web 应用程序不再运行，则使异步进程超时
				doTimeoutAsync();
			}
		}
	}

	private void doTimeoutAsync() {
		// 避免多次超时
		setAsyncTimeout(-1);
		asyncTimeoutGeneration = asyncStateMachine.getCurrentGeneration();
		processSocketEvent(SocketEvent.TIMEOUT, true);
	}

	@Override
	public boolean checkAsyncTimeoutGeneration() {
		return asyncTimeoutGeneration == asyncStateMachine.getCurrentGeneration();
	}

	public void setAsyncTimeout(long timeout) {
		asyncTimeout = timeout;
	}

	public long getAsyncTimeout() {
		return asyncTimeout;
	}

	@Override
	public void recycle() {
		errorState = ErrorState.NONE;
		asyncStateMachine.recycle();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 实现HTTP升级的处理器必须重写此方法并提供必要的 token。
	 */
	@Override
	public UpgradeToken getUpgradeToken() {
		throw new IllegalStateException("当前协议不支持Http升级");
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 实现HTTP升级的处理器必须重写此方法。
	 */
	@Override
	public ByteBuffer getLeftoverInput() {
		throw new IllegalStateException("当前协议不支持Http升级");
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 实现HTTP升级的处理器必须重写此方法。
	 */
	@Override
	public boolean isUpgrade() {
		return false;
	}

	@Override
	public final void action(ActionCode actionCode, Object param) {
		switch (actionCode) {
			// '普通' servlet 支持
			case COMMIT: {
				if (!response.isCommitted()) {
					try {
						// 验证并写入响应头
						prepareResponse();
					} catch (IOException e) {
						setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
					}
				}
				break;
			}
			case CLOSE: {
				action(ActionCode.COMMIT, null);
				try {
					finishResponse();
				} catch (CloseNowException cne) {
					setErrorState(ErrorState.CLOSE_NOW, cne);
				} catch (IOException e) {
					setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
				}
				break;
			}
			case ACK: {
				ack();
				break;
			}
			case CLIENT_FLUSH: {
				action(ActionCode.COMMIT, null);
				try {
					flush();
				} catch (IOException e) {
					setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
					response.setErrorException(e);
				}
				break;
			}
//			case AVAILABLE: {
//				request.setAvailable(available(Boolean.TRUE.equals(param)));
//				break;
//			}
//			case REQ_SET_BODY_REPLAY: {
//				ByteChunk body = (ByteChunk) param;
//				setRequestBody(body);
//				break;
//			}
	
			// Error handling
			case IS_ERROR: {
				((AtomicBoolean) param).set(getErrorState().isError());
				break;
			}
			case IS_IO_ALLOWED: {
				((AtomicBoolean) param).set(getErrorState().isIoAllowed());
				break;
			}
			case CLOSE_NOW: {
				// 防止进一步写入响应
				setSwallowResponse();
				if (param instanceof Throwable) {
					setErrorState(ErrorState.CLOSE_NOW, (Throwable) param);
				} else {
					setErrorState(ErrorState.CLOSE_NOW, null);
				}
				break;
			}
//			 case DISABLE_SWALLOW_INPUT: {
//				 // 中止上传或类似操作。读取请求的其余部分没有意义。
//				 disableSwallowRequest();
//				 // 这是一个错误状态。 确保它被标记
//				 setErrorState(ErrorState.CLOSE_CLEAN, null);
//				 break;
//			 }
	
			// 请求属性支持
			case REQ_HOST_ADDR_ATTRIBUTE: {
				if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
					request.remoteAddr().setString(socketWrapper.getRemoteAddr());
				}
				break;
			}
			case REQ_HOST_ATTRIBUTE: {
				populateRequestAttributeRemoteHost();
				break;
			}
			case REQ_LOCALPORT_ATTRIBUTE: {
				if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
					request.setLocalPort(socketWrapper.getLocalPort());
				}
				break;
			}
			case REQ_LOCAL_ADDR_ATTRIBUTE: {
				if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
					request.localAddr().setString(socketWrapper.getLocalAddr());
				}
				break;
			}
			case REQ_LOCAL_NAME_ATTRIBUTE: {
				if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
					request.localName().setString(socketWrapper.getLocalName());
				}
				break;
			}
			case REQ_REMOTEPORT_ATTRIBUTE: {
				if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
					request.setRemotePort(socketWrapper.getRemotePort());
				}
				break;
			}
	
			// SSL 请求属性支持
			case REQ_SSL_ATTRIBUTE: {
				populateSslRequestAttributes();
				break;
			}
			case REQ_SSL_CERTIFICATE: {
				try {
					sslReHandShake();
				} catch (IOException ioe) {
					setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
				}
				break;
			}
	
			// Servlet 3.0 异步支持
			case ASYNC_START: {
				asyncStateMachine.asyncStart((AsyncContextCallback) param);
				break;
			}
			case ASYNC_COMPLETE: {
				clearDispatches();
				if (asyncStateMachine.asyncComplete()) {
					processSocketEvent(SocketEvent.OPEN_READ, true);
				}
				break;
			}
			case ASYNC_DISPATCH: {
				if (asyncStateMachine.asyncDispatch()) {
					processSocketEvent(SocketEvent.OPEN_READ, true);
				}
				break;
			}
			case ASYNC_DISPATCHED: {
				asyncStateMachine.asyncDispatched();
				break;
			}
			case ASYNC_ERROR: {
				asyncStateMachine.asyncError();
				break;
			}
			case ASYNC_IS_ASYNC: {
				((AtomicBoolean) param).set(asyncStateMachine.isAsync());
				break;
			}
			case ASYNC_IS_COMPLETING: {
				((AtomicBoolean) param).set(asyncStateMachine.isCompleting());
				break;
			}
			case ASYNC_IS_DISPATCHING: {
				((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
				break;
			}
			case ASYNC_IS_ERROR: {
				((AtomicBoolean) param).set(asyncStateMachine.isAsyncError());
				break;
			}
			case ASYNC_IS_STARTED: {
				((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
				break;
			}
			case ASYNC_IS_TIMINGOUT: {
				((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
				break;
			}
			case ASYNC_RUN: {
				asyncStateMachine.asyncRun((Runnable) param);
				break;
			}
			case ASYNC_SETTIMEOUT: {
				if (param == null) {
					return;
				}
				long timeout = ((Long) param).longValue();
				setAsyncTimeout(timeout);
				break;
			}
			case ASYNC_TIMEOUT: {
				AtomicBoolean result = (AtomicBoolean) param;
				result.set(asyncStateMachine.asyncTimeout());
				break;
			}
			case ASYNC_POST_PROCESS: {
				asyncStateMachine.asyncPostProcess();
				break;
			}
	
			// Servlet 3.1 非阻塞 I/O
			// case REQUEST_BODY_FULLY_READ: {
			// AtomicBoolean result = (AtomicBoolean) param;
			// result.set(isRequestBodyFullyRead());
			// break;
			// }
			// case NB_READ_INTEREST: {
			// AtomicBoolean isReady = (AtomicBoolean)param;
			// isReady.set(isReadyForRead());
			// break;
			// }
			// case NB_WRITE_INTEREST: {
			// AtomicBoolean isReady = (AtomicBoolean)param;
			// isReady.set(isReadyForWrite());
			// break;
			// }
			case DISPATCH_READ: {
				addDispatch(DispatchType.NON_BLOCKING_READ);
				break;
			}
			case DISPATCH_WRITE: {
				addDispatch(DispatchType.NON_BLOCKING_WRITE);
				break;
			}
			case DISPATCH_EXECUTE: {
				executeDispatches();
				break;
			}
	
			// Servlet 3.1 HTTP 升级
			case UPGRADE: {
				doHttpUpgrade((UpgradeToken) param);
				break;
			}
	
			// Servlet 4.0 推送请求
			case IS_PUSH_SUPPORTED: {
				AtomicBoolean result = (AtomicBoolean) param;
				result.set(isPushSupported());
				break;
			}
			case PUSH_REQUEST: {
				doPush((Request) param);
				break;
			}
	
			// Servlet 4.0 尾部字段
			case IS_TRAILER_FIELDS_READY: {
				AtomicBoolean result = (AtomicBoolean) param;
				result.set(isTrailerFieldsReady());
				break;
			}
			case IS_TRAILER_FIELDS_SUPPORTED: {
				AtomicBoolean result = (AtomicBoolean) param;
				result.set(isTrailerFieldsSupported());
				break;
			}
	
			// 与HTTP/2等多路复用协议相关的标识符
			case CONNECTION_ID: {
				@SuppressWarnings("unchecked")
				AtomicReference<Object> result = (AtomicReference<Object>) param;
				result.set(getConnectionID());
				break;
			}
			case STREAM_ID: {
				@SuppressWarnings("unchecked")
				AtomicReference<Object> result = (AtomicReference<Object>) param;
				result.set(getStreamID());
				break;
			}
		}
	}

	@Override
	protected final void logAccess(SocketWrapperBase<?> socketWrapper) throws IOException {
		// 设置套接字包装器，以便访问日志可以读取套接字相关信息（例如客户端 IP）
		setSocketWrapper(socketWrapper);
		request.setStartTime(System.currentTimeMillis());
		response.setStatus(400);
		response.setError();
		getAdapter().log(request, response, 0);
	}

	protected abstract void prepareResponse() throws IOException;

	protected abstract void finishResponse() throws IOException;

	protected abstract void ack();

	protected abstract void flush() throws IOException;

//	protected abstract int available(boolean doRead);

//	protected abstract void setRequestBody(ByteChunk body);

	/**
	 * 防止进一步写入响应
	 */
	protected abstract void setSwallowResponse();

	/**
	 * 禁止接受请求之后的数据
	 * 中止上传或类似操作。读取请求的其余部分没有意义。
	 */
//	protected abstract void disableSwallowRequest();

	/**
	 * 直接填充请求属性的处理器应覆盖此方法并返回 {@code false}。
	 *
	 * @return 如果应使用 SocketWrapper 填充请求属性，则为 {@code true}，否则为 {@code false}。
	 */
	protected boolean getPopulateRequestAttributesFromSocket() {
		return true;
	}

	/**
	 * 填充远程主机请求属性。 从替代来源填充此方法的处理器应覆盖此方法。
	 */
	protected void populateRequestAttributeRemoteHost() {
		if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
			request.remoteHost().setString(socketWrapper.getRemoteHost());
		}
	}

	/**
	 * 从与此处理器关联的 {@link SSLSupport} 实例填充与 TLS 相关的请求属性。 从不同来源填充 TLS 属性的协议应覆盖此方法。
	 */
	protected void populateSslRequestAttributes() {
		try {
			if (sslSupport != null) {
				Object sslO = sslSupport.getCipherSuite();
				if (sslO != null) {
					request.setAttribute(SSLSupport.CIPHER_SUITE_KEY, sslO);
				}
				sslO = sslSupport.getPeerCertificateChain();
				if (sslO != null) {
					request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
				}
				sslO = sslSupport.getKeySize();
				if (sslO != null) {
					request.setAttribute(SSLSupport.KEY_SIZE_KEY, sslO);
				}
				sslO = sslSupport.getSessionId();
				if (sslO != null) {
					request.setAttribute(SSLSupport.SESSION_ID_KEY, sslO);
				}
				sslO = sslSupport.getProtocol();
				if (sslO != null) {
					request.setAttribute(SSLSupport.PROTOCOL_VERSION_KEY, sslO);
				}
				request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
			}
		} catch (Exception e) {
			getLogger().warn("填充与 TLS 相关的请求属性异常", e);
		}
	}

	/**
	 * 可以执行 TLS 重新握手的处理器（例如 HTTP/1.1）应覆盖此方法并实现重新握手。
	 * 
	 * @throws IOException - 如果需要身份验证，则客户端将进行 I/O，如果出错将引发此异常
	 */
	protected void sslReHandShake() throws IOException {
	}

	protected void processSocketEvent(SocketEvent event, boolean dispatch) {
		SocketWrapperBase<?> socketWrapper = getSocketWrapper();
		if (socketWrapper != null) {
			socketWrapper.processSocket(event, dispatch);
		}
	}

//	protected boolean isReadyForRead() {
//		if (available(true) > 0) {
//			return true;
//		}
//
//		if (!isRequestBodyFullyRead()) {
//			registerReadInterest();
//		}
//
//		return false;
//	}

//	protected abstract boolean isRequestBodyFullyRead();

//	protected abstract boolean isReadyForWrite();
	
	protected abstract void registerReadInterest();

	/**
	 * 当通过在非容器线程中定义读取和/或写入侦听器来启动非阻塞 IO 时，将调用此方法。 一旦非容器线程完成，就会调用它，以便容器对
	 * onWritePossible() 和/或 onDataAvailable() 进行第一次调用。
	 * <p>
	 * 处理分派需要套接字已被添加到等待请求队列中。 在非容器线程完成对该方法的调用触发时，这可能还没有发生。
	 * 容器线程会在释放socketWrapper上的锁之前将socket加入到等待的Requests队列中。
	 * 因此，通过在处理分派之前获取socketWrapper上的锁，我们可以确保套接字已经添加到等待的请求队列中。
	 */
	protected void executeDispatches() {
		SocketWrapperBase<?> socketWrapper = getSocketWrapper();
		Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
		if (socketWrapper != null) {
			synchronized (socketWrapper) {
				while (dispatches != null && dispatches.hasNext()) {
					DispatchType dispatchType = dispatches.next();
					socketWrapper.processSocket(dispatchType.getSocketStatus(), false);
				}
			}
		}
	}

	/**
	 * 处理HTTP升级。支持HTTP升级的处理器应该覆盖此方法并处理所提供的 token。
	 *
	 * @param upgradeToken - 包含处理器处理升级所需的所有信息
	 * @throws UnsupportedOperationException - 如果协议不支持HTTP升级
	 */
	protected void doHttpUpgrade(UpgradeToken upgradeToken) {
		throw new UnsupportedOperationException("当前协议不支持Http升级");
	}

	/**
	 * 支持push的协议应该重写这个方法并返回{@code true}。
	 * 
	 * @return 如果此处理器支持push，则为true，否则为false。
	 */
	protected boolean isPushSupported() {
		return false;
	}

	/**
	 * 处理push。 支持push的处理器应覆盖此方法并处理提供的令牌
	 * 
	 * @param pushTarget - 包含处理器处理push请求所需的所有信息
	 * @throws UnsupportedOperationException - 如果协议不支持push
	 */
	protected void doPush(Request pushTarget) {
		throw new UnsupportedOperationException("当前协议不支持Push请求 ");
	}

	/**
	 * 支持尾部字段的协议应该重写此方法并返回true。
	 * 
	 * @return 如果这个处理器支持尾部字段，则为 true，否则为 false。
	 */
	protected boolean isTrailerFieldsSupported() {
		return false;
	}

	protected abstract boolean isTrailerFieldsReady();

	/**
	 * 刷新任何挂起的写入。 在非阻塞写入期间用于刷新先前未完成写入的任何剩余数据。
	 * 
	 * @return 如果数据在方法结束时仍要刷新，则为 true
	 * @throws IOException - 如果在尝试刷新数据时发生 I/O 错误
	 */
	protected abstract boolean flushBufferedWrite() throws IOException;

	/**
	 * 持多路复用的协议（例如HTTP/2）应覆盖此方法并返回适当的ID。
	 *
	 * @return 与此请求关联的流ID，如果未使用多路复用协议，则为空
	 */
	protected Object getConnectionID() {
		return null;
	}

	/**
	 * 支持多路复用的协议（例如HTTP/2）应覆盖此方法并返回适当的ID。
	 *
	 * @return 与此请求关联的流ID，如果未使用多路复用协议，则为空
	 */
	protected Object getStreamID() {
		return null;
	}

	/**
	 * 如果分派导致当前请求的处理完成，则执行任何必要的清理处理。
	 *
	 * @return 当前请求的清理完成后要返回的套接字状态
	 * @throws IOException - 如果在尝试结束请求时发生 I/O 错误
	 */
	protected abstract SocketState dispatchEndRequest() throws IOException;
}
