package org.zy.moonStone.core.http;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.ws.Endpoint;

import org.slf4j.Logger;
import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.interfaces.connector.Adapter;
import org.zy.moonStone.core.interfaces.connector.Processor;
import org.zy.moonStone.core.interfaces.connector.ProtocolHandler;
import org.zy.moonStone.core.interfaces.connector.UpgradeProtocol;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.collections.SynchronizedStack;
import org.zy.moonStone.core.util.net.AbstractEndpoint;
import org.zy.moonStone.core.util.net.AbstractEndpoint.Handler;
import org.zy.moonStone.core.util.net.ContainerThreadMarker;
import org.zy.moonStone.core.util.net.SocketEvent;
import org.zy.moonStone.core.util.net.SocketWrapperBase;

/**
 * @dateTime 2022年1月11日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractProtocol<S> implements ProtocolHandler {
	/**
	 * 计数器, 用于为使用自动端口绑定的连接器生成唯一名称
	 */
	private static final AtomicInteger nameCounter = new AtomicInteger(0);
	/**
	 * 此连接器的唯一ID。仅在连接器配置为使用随机端口时使用, 因为如果调用stop()、start(), 端口将改变
	 */
	private int nameIndex = 0;

	/**
	 * 提供低级网络 I/O 的端点 - 必须与 ProtocolHandler 实现相匹配（使用 NIO 的 ProtocolHandler, 需要 NIOEndpoint 等）.
	 */
	private final AbstractEndpoint<S,?> endpoint;

	private Handler<S> handler;

	private final Set<Processor> waitingProcessors = Collections.newSetFromMap(new ConcurrentHashMap<Processor, Boolean>());

	/**
	 * 处理调度超时的控制器
	 */
	private ScheduledFuture<?> timeoutFuture = null;

	private ScheduledFuture<?> monitorFuture;

	// ----------------------------------------------- 由ProtocolHandler管理的属性 -----------------------------------------------
	/**
	 * 适配器提供ProtocolHandler和连接器之间的链接.
	 */
	protected Adapter adapter;

	/**
	 * 将保留在缓存中并随后续请求重用的空闲处理器的最大数量。默认值是200。值为-1表示无限。
	 * 在无限的情况下, 理论上缓存处理器对象的最大数量是{@link #getMaxConnections()}, 尽管它通常更接近{@link #getMaxThreads()}。
	 */
	protected int processorCache = 200;

	private String clientCertProvider = null;

	private int maxHeaderCount = 100;

	public AbstractProtocol(AbstractEndpoint<S,?> endpoint) {
		this.endpoint = endpoint;
		setConnectionLinger(Globals.DEFAULT_CONNECTION_LINGER);
		setTcpNoDelay(Globals.DEFAULT_TCP_NO_DELAY);
	}

	@Override
	public void setAdapter(Adapter adapter) { this.adapter = adapter; }

	@Override
	public Adapter getAdapter() { return adapter; }

	/**
	 * @return 空闲处理器最大缓存数
	 */
	public int getProcessorCache() { return this.processorCache; }

	public void setProcessorCache(int processorCache) {
		this.processorCache = processorCache;
	}

	/**
	 * 当客户端证书信息以java.security.cert实例以外的形式显示时。
	 * X509Certificate它需要在使用之前进行转换, 这个属性控制使用哪个JSSEprovider来执行转换。
	 * 例如, 它与AJP连接器, HTTP APR连接器和org.apache.catalina.valve . sslvalve一起使用。
	 * 如果未指定, 将使用默认提供程序。
	 *
	 * @return 要使用的JSSE提供程序的名称
	 */
	public String getClientCertProvider() { return clientCertProvider; }

	public void setClientCertProvider(String s) { this.clientCertProvider = s; }

	public int getMaxHeaderCount() {
		return maxHeaderCount;
	}

	public void setMaxHeaderCount(int maxHeaderCount) {
		this.maxHeaderCount = maxHeaderCount;
	}

	@Override
	public boolean isAprRequired() {
		return false;
	}

	@Override
	public boolean isSendfileSupported() {
		return endpoint.getUseSendfile();
	}

	// ----------------------------------------------- 传递给端点的属性 -----------------------------------------------
	@Override
	public Executor getExecutor() { return endpoint.getExecutor(); }
	@Override
	public void setExecutor(Executor executor) {
		endpoint.setExecutor(executor);
	}

	@Override
	public ScheduledExecutorService getUtilityExecutor() { return endpoint.getUtilityExecutor(); }
	@Override
	public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
		endpoint.setUtilityExecutor(utilityExecutor);
	}


	public int getMaxThreads() { return endpoint.getMaxThreads(); }
	public void setMaxThreads(int maxThreads) {
		endpoint.setMaxThreads(maxThreads);
	}

	public int getMaxConnections() { return endpoint.getMaxConnections(); }
	public void setMaxConnections(int maxConnections) {
		endpoint.setMaxConnections(maxConnections);
	}


	public int getMinSpareThreads() { return endpoint.getMinSpareThreads(); }
	public void setMinSpareThreads(int minSpareThreads) {
		endpoint.setMinSpareThreads(minSpareThreads);
	}


	public int getThreadPriority() { return endpoint.getThreadPriority(); }
	public void setThreadPriority(int threadPriority) {
		endpoint.setThreadPriority(threadPriority);
	}


	public int getAcceptCount() { return endpoint.getAcceptCount(); }
	public void setAcceptCount(int acceptCount) { endpoint.setAcceptCount(acceptCount); }


	public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
	public void setTcpNoDelay(boolean tcpNoDelay) {
		endpoint.setTcpNoDelay(tcpNoDelay);
	}


	public int getConnectionLinger() { return endpoint.getConnectionLinger(); }
	public void setConnectionLinger(int connectionLinger) {
		endpoint.setConnectionLinger(connectionLinger);
	}


	/**
	 * 在关闭连接之前等待后续请求的时间。默认值是{@link #getConnectionTimeout()}
	 *
	 * @return 超时(以毫秒为单位)
	 */
	public int getKeepAliveTimeout() { return endpoint.getKeepAliveTimeout(); }
	public void setKeepAliveTimeout(int keepAliveTimeout) {
		endpoint.setKeepAliveTimeout(keepAliveTimeout);
	}

	public InetAddress getAddress() { return endpoint.getAddress(); }
	public void setAddress(InetAddress ia) {
		endpoint.setAddress(ia);
	}

	public void setBindOnInit(boolean bindOnInit) {
		endpoint.setBindOnInit(bindOnInit);
	}
	

	public int getPort() { return endpoint.getPort(); }
	public void setPort(int port) {
		endpoint.setPort(port);
	}


	public int getPortOffset() { return endpoint.getPortOffset(); }
	public void setPortOffset(int portOffset) {
		endpoint.setPortOffset(portOffset);
	}


	public int getPortWithOffset() { return endpoint.getPortWithOffset(); }


	public int getLocalPort() { return endpoint.getLocalPort(); }

	/**
	 * 当期望从客户机获得数据时, 这是在关闭连接之前等待数据到达的时间.
	 */
	public int getConnectionTimeout() {
		return endpoint.getConnectionTimeout();
	}
	public void setConnectionTimeout(int timeout) {
		endpoint.setConnectionTimeout(timeout);
	}

	public long getConnectionCount() {
		return endpoint.getConnectionCount();
	}

	public void setAcceptorThreadPriority(int threadPriority) {
		endpoint.setAcceptorThreadPriority(threadPriority);
	}
	public int getAcceptorThreadPriority() {
		return endpoint.getAcceptorThreadPriority();
	}

	// ---------------------------------------------------------- 公共方法 ----------------------------------------------------------
	public void addWaitingProcessor(Processor processor) {
		waitingProcessors.add(processor);
	}


	public void removeWaitingProcessor(Processor processor) {
		waitingProcessors.remove(processor);
	}
	// ---------------------------------------------------------- 访问器的子类 ----------------------------------------------------------
	protected AbstractEndpoint<S,?> getEndpoint() {
		return endpoint;
	}


	protected Handler<S> getHandler() {
		return handler;
	}

	protected void setHandler(Handler<S> handler) {
		this.handler = handler;
	}


	// ---------------------------------------------------------- 抽象方法 ----------------------------------------------------------
	/**
	 * 提供对其日志记录器的访问, 以供抽象类使用
	 */
	protected abstract Logger getLogger();


	/**
	 * 获取在构造此协议处理程序的名称时要使用的前缀。名称为prefix-address-port
	 */
	protected abstract String getNamePrefix();


	/**
	 * 获取协议名称(Http).
	 * @return 此协议名称
	 */
	protected abstract String getProtocolName();


	/**
	 * 为网络层协商的协议找到一个合适的处理器.
	 * @param name - 请求的协商协议的名称
	 * @return {@link UpgradeProtocol#getAlpnName()}与请求的协议匹配的实例 +
	 */
	protected abstract UpgradeProtocol getNegotiatedProtocol(String name);


	/**
	 * 为指定的协议升级名称找到合适的处理程序。这用于直接连接协议选择.
	 * @param name - 请求的协商协议的名称
	 * @return {@link UpgradeProtocol#getAlpnName()}与请求的协议匹配的实例
	 */
	protected abstract UpgradeProtocol getUpgradeProtocol(String name);


	/**
	 * 为当前协议实现创建并配置一个新的Processor实例.
	 *
	 * @return 一个完全配置的处理器实例, 可以随时使用
	 */
	protected abstract Processor createProcessor();


//	protected abstract Processor createUpgradeProcessor(SocketWrapperBase<?> socket, UpgradeToken upgradeToken);

	// ------------------------------------------------------- 生命周期方法 -------------------------------------------------------
	private void logPortOffset() {
		if (getPort() != getPortWithOffset()) {
			getLogger().info("ProtocolHandler 端口偏移量, port: {}, portOffset: {}, by name: {}", String.valueOf(getPort()), String.valueOf(getPortOffset()), getName());
		}
	}

	@Override
	public void init() throws Exception {
		if (getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler init, by name: {}", getName());
			logPortOffset();
		}

		endpoint.setName(getName());
		endpoint.init();
	}


	@Override
	public void start() throws Exception {
		if (getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler Start, by name: {}", getName());
			logPortOffset();
		}

		endpoint.start();
		monitorFuture = getUtilityExecutor().scheduleWithFixedDelay(() -> {
			if (!isPaused()) {
				startAsyncTimeout();
			}
		}, 0, 60, TimeUnit.SECONDS);
	}

	public boolean isPaused() {
		return endpoint.isPaused();
	}

	/**
	 * 表示与套接字读/写超时无关的超时.
	 */
	protected void startAsyncTimeout() {
		if (timeoutFuture == null || (timeoutFuture != null && timeoutFuture.isDone())) {
			if (timeoutFuture != null && timeoutFuture.isDone()) {
				// 执行计划任务时出现错误, 获取并记录它
				try {
					timeoutFuture.get();
				} catch (InterruptedException | ExecutionException e) {
					getLogger().error("ProtocolHandler 异步超时错误", e);
				}
			}
			timeoutFuture = getUtilityExecutor().scheduleAtFixedRate(() -> {
				long now = System.currentTimeMillis();
				for (Processor processor : waitingProcessors) {
					processor.timeoutAsync(now);
				}
			}, 1, 1, TimeUnit.SECONDS);
		}
	}

	protected void stopAsyncTimeout() {
		if (timeoutFuture != null) {
			timeoutFuture.cancel(false);
			timeoutFuture = null;
		}
	}

	@Override
	public void pause() throws Exception {
		if (getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler 暂停, by name: {}", getName());
		}

		stopAsyncTimeout();
		endpoint.pause();
	}


	@Override
	public void resume() throws Exception {
		if(getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler 恢复, by name: {}", getName());
		}

		endpoint.resume();
		startAsyncTimeout();
	}


	@Override
	public void stop() throws Exception {
		if(getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler 暂停, by name: {}", getName());
			logPortOffset();
		}

		if (monitorFuture != null) {
			monitorFuture.cancel(true);
			monitorFuture = null;
		}
		stopAsyncTimeout();
		// 任何等待的处理器超时
		for (Processor processor : waitingProcessors) {
			processor.timeoutAsync(-1);
		}

		endpoint.stop();
	}


	@Override
	public void destroy() throws Exception {
		if(getLogger().isInfoEnabled()) {
			getLogger().info("ProtocolHandler 销毁, by name: {}", getName());
			logPortOffset();
		}

		endpoint.destroy();
	}

	@Override
	public void closeServerSocketGraceful() {
		endpoint.closeServerSocketGraceful();
	}

	public synchronized int getNameIndex() {
		if (nameIndex == 0) {
			nameIndex = nameCounter.incrementAndGet();
		}

		return nameIndex;
	}
	public String getName() {
		return getNameInternal();
	}
	/**
	 * 获得内部使用的名称
	 * @return
	 */
	private String getNameInternal() {
		StringBuilder name = new StringBuilder(getNamePrefix());
		name.append('-');
		if (getAddress() != null) {
			name.append(getAddress().getHostAddress());
			name.append('-');
		}
		int port = getPortWithOffset();
		if (port == 0) {
			// 使用自动绑定, 检查端口是否已知
			name.append("auto-");
			name.append(getNameIndex());
			port = getLocalPort();
			if (port != -1) {
				name.append('-');
				name.append(port);
			}
		} else {
			name.append(port);
		}
		return name.toString();
	}

	// ------------------------------------------------- 连接处理程序基类 -------------------------------------------------
	/**
	 *  连接处理器
	 * @param <S> - 协议处理器类型
	 */
	protected static class ConnectionHandler<S> implements AbstractEndpoint.Handler<S> {
		/** 协议处理器 */
		private final AbstractProtocol<S> proto;
		/** 协议处理器注册数 */
		private final AtomicLong registerCount = new AtomicLong(0);
		/** Processor 缓存 */
		private final Map<S,Processor> connections = new ConcurrentHashMap<>();
		/** 可重用的处理器实例 */
		private final RecycledProcessors recycledProcessors = new RecycledProcessors(this);

		public ConnectionHandler(AbstractProtocol<S> proto) {
			this.proto = proto;
		}

		protected AbstractProtocol<S> getProtocol() {
			return proto;
		}
		
		protected int getRegisterCount() {
			return registerCount.intValue();
		}

		protected Logger getLogger() {
			return getProtocol().getLogger();
		}

		@Override
		public void recycle() {
			recycledProcessors.clear();
		}


		@Override
		public SocketState process(SocketWrapperBase<S> wrapper, SocketEvent status, SelectionKey key) {
			if (getLogger().isDebugEnabled()) {
				getLogger().debug("AbstractProtocol.ConnectionHandler#process-套接字状态处理, status: {}, by SelectionKey: {}", status, key);
			}
			if (wrapper == null) {
				return SocketState.CLOSED;
			}

			S socket = wrapper.getSocketChannel();

			Processor processor = connections.get(socket);
			if (getLogger().isDebugEnabled()) {
				getLogger().debug("AbstractProtocol.ConnectionHandler#process-获取已缓存的Processor, Processor: {}, Socket: {}", processor, socket);
			}

			/*
			 * 超时在专用线程上计算, 然后进行调度。由于调度过程中的延迟, 可能不再需要超时。检查此处, 避免不必要的处理
			 */
			if (SocketEvent.TIMEOUT == status && (processor == null || !processor.isAsync() && !processor.isUpgrade() ||
					processor.isAsync() && !processor.checkAsyncTimeoutGeneration())) {
				return SocketState.OPEN;
			}

			if (processor != null) {
				// 确保没有触发异步超时
				getProtocol().removeWaitingProcessor(processor);
			} else if (status == SocketEvent.DISCONNECT || status == SocketEvent.ERROR) {
				// 端节点请求关闭, 并且不再有与此套接字关联的处理器.
				return SocketState.CLOSED;
			}

			ContainerThreadMarker.set();

			try {
				if (processor == null) {
					String negotiatedProtocol = wrapper.getNegotiatedProtocol();
					// OpenSSL通常返回null, 而JSSE通常在未协商协议时返回””
					if (negotiatedProtocol != null && negotiatedProtocol.length() > 0) {
						if (negotiatedProtocol.equals("http/1.1")) {
							// 显式协商默认协议。获取下面的处理器
						} else {
							// 如果无法协商协议, OpenSSL 1.0.2的ALPN回调不支持握手失败并出现错误。因此, 我们需要在这里中断连接。修复后, 用注释掉的块替换下面的代码.
							if (getLogger().isDebugEnabled()) {
								getLogger().debug("协商协议失败, by {}", negotiatedProtocol);
							}
							return SocketState.CLOSED;
						}
					}
				}
				if (processor == null) {
					 // 尝试获取之前回收的协议处理器对象
					processor = recycledProcessors.pop();
					if (getLogger().isDebugEnabled() && processor != null) {
						getLogger().debug("AbstractProtocol.ConnectionHandler#process-重用的空闲处理器队列末尾对象: {}", processor);
					}
				}
				if (processor == null) {
					processor = getProtocol().createProcessor();
					if (getLogger().isDebugEnabled()) {
						getLogger().debug("AbstractProtocol.ConnectionHandler#process-新Processor实例创建, Processor: {}, Socket: {}", processor, socket);
					}
				}

//				processor.setSslSupport(wrapper.getSslSupport(getProtocol().getClientCertProvider()));

				// 缓存关联映射
				connections.put(socket, processor);

				SocketState state = SocketState.CLOSED;
				state = processor.process(wrapper, status);
				
				if (state == SocketState.LONG) {
					// 在处理请求/响应的中间。保持与处理器关联的套接字。具体要求取决于长轮询的类型
					longPoll(wrapper, processor);
					if (processor.isAsync()) {
						getProtocol().addWaitingProcessor(processor);
					}
				} else if (state == SocketState.OPEN) {
					// 在请求之间保持活动状态。确定回收处理器。继续轮询下一个请求
					connections.remove(socket);
					release(processor);
					wrapper.registerReadInterest();
				} else if (state == SocketState.SENDFILE) {
					// 正在发送文件。如果失败, 插座将关闭。如果它工作, 则可以将套接字添加到轮询器（或等效程序）以等待更多数据, 或者如果还有任何管道请求, 则对其进行处理
				} else if (state == SocketState.UPGRADED) {
					// 如果这是非阻塞写入, 请不要将套接字添加回轮询器, 否则轮询器可能会触发多个读取事件, 从而导致连接器中的线程不足。write（）方法将在必要时将此套接字添加到轮询器
					if (status != SocketEvent.OPEN_WRITE) {
						longPoll(wrapper, processor);
						getProtocol().addWaitingProcessor(processor);
					}
				} else if (state == SocketState.SUSPENDED) {
					// 不要将套接字添加回轮询器。resumeProcessing（）方法将此套接字添加到轮询器
				} else {
					// 连接已关闭。可以回收处理器。处理升级的处理器需要在发布前进行额外清理
					connections.remove(socket);
					release(processor);
				}
				return state;
			} catch(java.net.SocketException e) {
				// SocketException是正常的
				getLogger().debug("Socket Exception", e);
			} catch (IOException e) {
				// 异常是正常的
				getLogger().debug("IOException", e);
//			} catch (ProtocolException e) {
//				// 协议异常通常意味着客户端发送的数据无效或不完整
//				getLogger().debug("Protocol Exception", e);
			} catch (OutOfMemoryError oome) {
				/**
				 * 未来的开发人员: 如果您发现任何其他罕见但非致命的异常, 请在此处捕获它们, 并如上所述登录
				 * 在这里尝试并处理这个问题, 让Tomcat有机会关闭连接, 并防止客户端等待超时。
				 * 最坏的情况是, 它是不可恢复的, 记录日志的尝试将触发另一个OOME
				 */
				getLogger().error("abstractConnectionHandler.oome", oome);
			} catch (Throwable e) {
				ExceptionUtils.handleThrowable(e);
				// 任何其他异常或错误都是奇怪的。在这里, 我们以“错误”级别记录它, 因此它甚至会显示在不太详细的日志上
				getLogger().error("abstractConnectionHandler.error", e);
			} finally {
				ContainerThreadMarker.clear();
			}

			// 确保从当前连接列表中删除套接字/处理器
			connections.remove(socket);
			release(processor);
			return SocketState.CLOSED;
		}


		@Override
		public Set<S> getOpenSockets() {
			return connections.keySet();
		}

		protected void longPoll(SocketWrapperBase<?> socket, Processor processor) {
            if (!processor.isAsync()) {
            	// 目前仅与HTTP一起使用
                socket.registerReadInterest();
            }
        }
		
		/**
		 * 预计在不再需要处理器时由处理程序使用。
		 *
		 * @param processor - 正在释放的 {@link Processor} (与socket关联)
		 */
		private void release(Processor processor) {
			if (processor != null) {
				getLogger().debug("重置Processor[" + processor + "]，添加缓存");
				processor.recycle();
				recycledProcessors.push(processor);
			}
		}


		/**
		 * 应由 {@link Endpoint } 用于在套接字关闭、错误等情况下释放资源。
		 */
		@Override
		public void release(SocketWrapperBase<S> socketWrapper) {
			S socket = socketWrapper.getSocketChannel();
			Processor processor = connections.remove(socket);
			release(processor);
		}

		@Override
		public final void pause() {
			/*
			 * 通知与当前连接关联的所有处理器端点正在暂停。 大多数情况下不用在意。 如果是处理多路复用流的情况可能希望采取行动。 例如, HTTP/2 可能希望停止接受新的流。
			 * 
			 * 请注意, 即使端点恢复, （当前）也没有 API 可以通知处理器。
			 */
			for (Processor processor : connections.values()) {
				processor.pause();
			}
		}
	}

	/**
	 * 可重用（空闲）处理器操作类
	 */
	protected static class RecycledProcessors extends SynchronizedStack<Processor> {
		private final transient ConnectionHandler<?> handler;
		protected final AtomicInteger size = new AtomicInteger(0);

		public RecycledProcessors(ConnectionHandler<?> handler) {
			this.handler = handler;
		}

		@Override
		public boolean push(Processor processor) {
			int cacheSize = handler.getProtocol().getProcessorCache();
			boolean offer = cacheSize == -1 ? true : size.get() < cacheSize;
			// 避免过度增长缓存或在停止后添加
			boolean result = false;
			if (offer) {
				result = super.push(processor);
				if (result) { // 添加操作则递增1
					size.incrementAndGet();
				}
			}
			
			return result;
		}

		@Override
		public Processor pop() {
			Processor result = super.pop();
			if (result != null) {
				size.decrementAndGet();
			}
			return result;
		}

		@Override
		public synchronized void clear() {
			Processor next = pop();
			while (next != null) {
				next = pop();
			}
			super.clear();
			size.set(0);
		}
	}
}
