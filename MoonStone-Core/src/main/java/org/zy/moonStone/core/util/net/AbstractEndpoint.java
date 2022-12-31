package org.zy.moonStone.core.util.net;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.zy.moonStone.core.interfaces.connector.ProtocolHandler;
import org.zy.moonStone.core.threads.LimitLatch;
import org.zy.moonStone.core.threads.TaskQueue;
import org.zy.moonStone.core.threads.TaskThreadFactory;
import org.zy.moonStone.core.threads.ThreadPoolExecutor;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.collections.SynchronizedStack;
import org.zy.moonStone.core.util.net.Acceptor.AcceptorState;

/**
 * @dateTime 2022年1月12日;
 * @author zy(azurite-Y);
 * @description
 * @param <S> - 与此端点相关的套接字包装器使用的类型。可能和U一样。
 * @param <U> - 这个端点使用的底层套接字的类型。可能和S一样。
 */
public abstract class AbstractEndpoint<S,U> {
	/**
	 * 端点的运行标志.
	 */
	protected volatile boolean running = false;

	/**
	 * 当端点暂停时将被设置为true.
	 */
	protected volatile boolean paused = false;

	/**
	 * 是否使用内部执行器
	 */
	protected volatile boolean internalExecutor = true;

	/**
	 * 端点处理的连接数的计数器
	 */
	private volatile LimitLatch connectionLimitLatch = null;

	/**
	 * Socket 属性
	 */
	protected final SocketProperties socketProperties = new SocketProperties();

	/**
	 * 用于接受新连接并将其传递给工作线程的线程.
	 */
	protected Acceptor<U> acceptor;

	/**
	 * SocketProcessor对象的缓存, 在调用init方法时被实例化
	 */
	protected SynchronizedStack<SocketProcessorBase<S>> processorCache;

//	private String defaultSSLHostConfigName = SSLHostConfig.DEFAULT_SSL_HOST_NAME;

//	protected ConcurrentMap<String,SSLHostConfig> sslHostConfigs = new ConcurrentHashMap<>();

	/**
	 * 用户是否要求在可能的情况下使用发送文件?
	 */
	private boolean useSendfile = true;

	/**
	 * 端点停止时等待内部执行器（如果使用）终止的时间（毫秒）.默认值为5000（5秒）.
	 */
	private long executorTerminationTimeoutMillis = 5000;

	/**
	 * 接受线程计数
	 */
	protected int acceptorThreadCount = 1;

	/**
	 * 接受线程的优先级, 分配给线程默认优先级
	 */
	protected int acceptorThreadPriority = Thread.NORM_PRIORITY;

	/**
	 * 最大连接数
	 */
	private int maxConnections = 10000;

	/**
	 * 基于外部执行器的线程池
	 */
	private Executor executor = null;

	/**
	 * 用于实用程序任务的基于外部执行器的线程池
	 */
	private ScheduledExecutorService utilityExecutor = null;

	/**
	 * 服务器socket端口
	 */
	private int port = -1;

	/**
	 * 服务器socket端口偏移量
	 */
	private int portOffset = 0;

	/**
	 * 服务器套接字的地址
	 */
	private InetAddress address;

	/**
	 * 允许指定应该用于服务器套接字的最大连接数 (backlog)。缺省值是100
	 */
	private int acceptCount = 100;

	/**
	 * 控制端点何时绑定端口。True, 默认在{@link #init()}上绑定端口, 在{@link #destroy()}上取消绑定。
	 * 如果设置为false, 端口在{@link #start()}上绑定, 在{@link #stop()}上取消绑定。
	 */
	private boolean bindOnInit = true;

	private volatile BindState bindState = BindState.UNBOUND;

	/**
	 * Keepalive超时时间, 如果未设置, 则使用soTimeout
	 */
	private Integer keepAliveTimeout = null;

	/**
	 * SSL engine.
	 */
	private boolean SSLEnabled = false;

	/**
	 * 最小空闲线程
	 */
	private int minSpareThreads = 10;

	/**
	 * 工作线程的最大数量
	 */
	private int maxThreads = 200;

	/**
	 * 工作线程的优先级
	 */
	protected int threadPriority = Thread.NORM_PRIORITY;

	/**
	 * 最大保持活动请求数
	 */
	private int maxKeepAliveRequests=100;

	/**
	 * 线程池的名称, 将用于命名子线程
	 */
	private String name = "TP";

	/**
	 * 默认值为true-创建的线程将处于守护程序模式。如果设置为false, 则控制线程将不是守护进程, 并将保持进程的活动状态
	 */
	private boolean daemon = true;

	/**
	 * 公开异步IO功能
	 */
	private boolean useAsyncIO = true;

	/**
	 * 可转让协议
	 */
	protected final List<String> negotiableProtocols = new ArrayList<>();

	/**
	 * 处理接受的套接字
	 */
	private Handler<S> handler = null;

	/**
	 * 属性提供了一种将配置传递给子组件的方式, 而不用让{@link ProtocolHandler}知道这些子组件上可用的属性。
	 */
	protected HashMap<String, Object> attributes = new HashMap<>();


	protected abstract Logger getLogger();


	public void createExecutor() {
		internalExecutor = true;
		TaskQueue taskqueue = new TaskQueue();
		TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
		executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), 60, TimeUnit.SECONDS,taskqueue, tf);
		taskqueue.setParent( (ThreadPoolExecutor) executor);
	}

	public void shutdownExecutor() {
		Executor executor = this.executor;
		if (executor != null && internalExecutor) {
			this.executor = null;
			if (executor instanceof ThreadPoolExecutor) {
				// 关闭内部执行器
				ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
				tpe.shutdownNow();
				long timeout = getExecutorTerminationTimeoutMillis();
				if (timeout > 0) {
					try {
						tpe.awaitTermination(timeout, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						// Ignore
					}
					// 如果此执行程序在 shutdown 或 shutdownNow 之后正在终止但尚未完全终止, 则返回 true。 此方法可能对调试有用。
					if (tpe.isTerminating()) {
						getLogger().warn("执行器关闭, by name: {}", getName());
					}
				}
				TaskQueue queue = (TaskQueue) tpe.getQueue();
				queue.setParent(null);
			}
		}
	}

	/**
	 * 使用虚假连接解锁服务器套接字接受线程
	 */
	private void unlockAccept() {
		// 仅在必要时尝试解锁接受器
		if (acceptor == null || acceptor.getState() != AcceptorState.RUNNING) {
			return;
		}

		InetSocketAddress unlockAddress = null;
		InetSocketAddress localAddress = null;
		try {
			localAddress = getLocalAddress();
		} catch (IOException ioe) {
			getLogger().debug(String.format("获取本地地址失败, by name: {}", getName()), ioe);
		}
		if (localAddress == null) {
			getLogger().warn("无法获取本地地址, by name: {}", getName());
			return;
		}

		try {
			unlockAddress = getUnlockAddress(localAddress);

			try (java.net.Socket s = new java.net.Socket()) {
				int stmo = 2 * 1000;
				int utmo = 2 * 1000;
				if (getSocketProperties().getSoTimeout() > stmo)
					stmo = getSocketProperties().getSoTimeout();
				if (getSocketProperties().getUnlockTimeout() > utmo)
					utmo = getSocketProperties().getUnlockTimeout();
				s.setSoTimeout(stmo);
				s.setSoLinger(getSocketProperties().getSoLingerOn(),getSocketProperties().getSoLingerTime());
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("About to unlock socket for:" + unlockAddress);
				}
				s.connect(unlockAddress,utmo);
				if (getDeferAccept()) {
					/*
					 * 在延迟接受/接受过滤器的情况下, 我们需要发送数据来唤醒accept。
					 * 发送OPTIONS绕过BSD接受过滤器。Acceptor将丢弃它。
					 */
					OutputStreamWriter sw;

					sw = new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1");
					sw.write("OPTIONS * HTTP/1.0\r\n" + "User-Agent: MoonStone 唤醒连接\r\n\r\n");
					sw.flush();
				}
				if (getLogger().isDebugEnabled()) {
					getLogger().debug("Socket 解锁完成: {}", unlockAddress);
				}
			}
			// 等待高达1000ms的接受线程解锁
			long waitLeft = 1000;
			while (waitLeft > 0 && acceptor.getState() == AcceptorState.RUNNING) {
				Thread.sleep(5);
				waitLeft -= 5;
			}
		} catch(Throwable t) {
			ExceptionUtils.handleThrowable(t);
			if (getLogger().isDebugEnabled()) {
				getLogger().debug("解锁失败, by port: {}", getPortWithOffset(), t);
			}
		}
	}


	private static InetSocketAddress getUnlockAddress(InetSocketAddress localAddress) throws SocketException {
		if (localAddress.getAddress().isAnyLocalAddress()) {
			/*
			 * 需要一个与配置的绑定地址相同类型(IPv4或IPV6)的本地地址, 因为连接器可能被配置为不在类型之间映射。
			 */
			InetAddress loopbackUnlockAddress = null;
			InetAddress linkLocalUnlockAddress = null;

			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
				while (inetAddresses.hasMoreElements()) {
					InetAddress inetAddress = inetAddresses.nextElement();
					if (localAddress.getAddress().getClass().isAssignableFrom(inetAddress.getClass())) {
						if (inetAddress.isLoopbackAddress()) {
							if (loopbackUnlockAddress == null) {
								loopbackUnlockAddress = inetAddress;
							}
						} else if (inetAddress.isLinkLocalAddress()) {
							if (linkLocalUnlockAddress == null) {
								linkLocalUnlockAddress = inetAddress;
							}
						} else {
							// 默认使用非链接本地、非环回地址
							return new InetSocketAddress(inetAddress, localAddress.getPort());
						}
					}
				}
			}
			// 由于在某些平台（如OSX）上, 监听所有本地地址时不包括某些链路本地地址, 因此更倾向于环回而非链路本地地址。
			if (loopbackUnlockAddress != null) {
				return new InetSocketAddress(loopbackUnlockAddress, localAddress.getPort());
			}
			if (linkLocalUnlockAddress != null) {
				return new InetSocketAddress(linkLocalUnlockAddress, localAddress.getPort());
			}
			return new InetSocketAddress("localhost", localAddress.getPort());
		} else {
			return localAddress;
		}
	}

	// ------------------------------------------------- 请求处理方法 ----------------------------------------------
	/**
	 * 处理具有给定状态的给定SocketWrapper。用于触发处理, 就像轮询器（对于具有一个端点的端点）选择套接字一样
	 *
	 * @param socketWrapper - 要处理的套接字包装器
	 * @param event - 要处理的套接字事件
	 * @param dispatch - 是否应该在新的容器线程上执行处理
	 * @return 如果处理成功触发
	 */
	public boolean processSocket(SocketWrapperBase<S> socketWrapper, SocketEvent event, boolean dispatch) {
		try {
			if (socketWrapper == null) {
				return false;
			}
			SocketProcessorBase<S> sc = null;
			if (processorCache != null) {
				sc = processorCache.pop();
			}
			if (sc == null) {
				sc = createSocketProcessor(socketWrapper, event);
			} else {
				sc.reset(socketWrapper, event);
			}
			Executor executor = getExecutor();
			if (dispatch && executor != null) {
				executor.execute(sc);
			} else {
				sc.run();
			}
		} catch (RejectedExecutionException ree) {
			getLogger().warn("执行器异常, by socketWrapper: " + socketWrapper , ree);
			return false;
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			// 这意味着在创建线程时遇到了OOM或类似情况, 或者池及其队列已满
			getLogger().error("执行器异常", t);
			return false;
		}
		return true;
	}


	protected abstract SocketProcessorBase<S> createSocketProcessor(SocketWrapperBase<S> socketWrapper, SocketEvent event);

	// ----------------------------------------------- 生命周期方法 -----------------------------------------------
	/*
	 * NOTE: 除了确保在正确的位置调用bind/unbind之外, 在这个类中不需要维护状态或检查有效的转换。
	 * 预期调用代码将维护状态并防止无效的状态转换。
	 */
	public abstract void bind() throws Exception;
    public abstract void unbind() throws Exception;
    public abstract void startInternal() throws Exception;
    public abstract void stopInternal() throws Exception;
    
    /**
     * 
     * @throws Exception
     */
    private void bindWithCleanup() throws Exception {
        try {
            bind();
        } catch (Throwable t) {
            // 如果在绑定过程中出现问题, 确保清除打开的套接字等
            ExceptionUtils.handleThrowable(t);
            unbind();
            throw t;
        }
    }

    public final void init() throws Exception {
        if (bindOnInit) {
            bindWithCleanup();
            bindState = BindState.BOUND_ON_INIT;
        }
    }
	
    public final void start() throws Exception {
        if (bindState == BindState.UNBOUND) {
            bindWithCleanup();
            bindState = BindState.BOUND_ON_START;
        }
        startInternal();
    }

    /**
     * 启动 {@link Acceptor MoonStone接收器} 
     */
    protected void startAcceptorThread() {
        acceptor = new Acceptor<>(this);
        String threadName = getName() + "-Acceptor";
        acceptor.setThreadName(threadName);
        Thread t = new Thread(acceptor, threadName);
        t.setPriority(getAcceptorThreadPriority());
        t.setDaemon(getDaemon());
        t.start();
    }

    /**
     * 暂停端点, 这将停止它接受新连接并解锁接受器
     */
    public void pause() {
        if (running && !paused) {
            paused = true;
            releaseConnectionLatch();
            unlockAccept();
            getHandler().pause();
        }
    }

    /**
     * 恢复端点, 这将使它再次开始接受新的连接
     */
    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public final void stop() throws Exception {
        stopInternal();
        if (bindState == BindState.BOUND_ON_START || bindState == BindState.SOCKET_CLOSED_ON_STOP) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }

    public final void destroy() throws Exception {
        if (bindState == BindState.BOUND_ON_INIT) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }
    
    protected LimitLatch initializeConnectionLatch() {
        if (connectionLimitLatch==null) {
            connectionLimitLatch = new LimitLatch(getMaxConnections());
        }
        return maxConnections==-1 ? null : connectionLimitLatch ;
    }

    private void releaseConnectionLatch() {
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.releaseAll();
        connectionLimitLatch = null;
    }

    protected void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) return;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.countUpOrAwait();
    }

    protected long countDownConnection() {
        if (maxConnections==-1) return -1;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            long result = latch.countDown();
            if (result<0) {
                getLogger().warn("不正确的连接数:[{}]", result);
            }
            return result;
        } else return -1;
    }

    /**
     * 如果服务器套接字最初是在 {@link #start()} (而不是 {@link #init()} )上绑定的, 则关闭服务器套接字(以防止进一步的连接)。
     *
     * @see #getBindOnInit()
     */
    public final void closeServerSocketGraceful() {
        if (bindState == BindState.BOUND_ON_START) {
            bindState = BindState.SOCKET_CLOSED_ON_STOP;
            try {
                doCloseServerSocket();
            } catch (IOException ioe) {
                getLogger().warn( String.format("服务器Socket关闭失败, by name: %s", getName()) , ioe);
            }
        }
    }

    /**
     * 实际上关闭服务器套接字, 但不执行任何其他清理.
     *
     * @throws IOException - 如果发生错误, 关闭套接字
     */
    protected abstract void doCloseServerSocket() throws IOException;

    protected abstract U serverSocketAccept() throws Exception;

    protected abstract boolean setSocketOptions(U socket);

    /**
     * 当在配置接受的套接字、为套接字分配包装或试图调度它进行处理时发生错误, 必须立即关闭连接时, 请关闭套接字。
     * @param socket - 新接受的套接字
     */
    protected abstract void closeSocket(U socket);

    protected void destroySocket(U socket) {
        closeSocket(socket);
    }

	// ----------------------------------------------- getter、setter -----------------------------------------------
//	public String getDefaultSSLHostConfigName() {
//		return defaultSSLHostConfigName;
//	}
//	public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
//		this.defaultSSLHostConfigName = defaultSSLHostConfigName;
//	}

	public SocketProperties getSocketProperties() {
		return socketProperties;
	}

	public boolean getUseSendfile() {
		return useSendfile;
	}
	public void setUseSendfile(boolean useSendfile) {
		this.useSendfile = useSendfile;
	}

	public long getExecutorTerminationTimeoutMillis() {
		return executorTerminationTimeoutMillis;
	}
	public void setExecutorTerminationTimeoutMillis(long executorTerminationTimeoutMillis) {
		this.executorTerminationTimeoutMillis = executorTerminationTimeoutMillis;
	}

	public void setAcceptorThreadPriority(int acceptorThreadPriority) {
		this.acceptorThreadPriority = acceptorThreadPriority;
	}
	public int getAcceptorThreadPriority() { return acceptorThreadPriority; }

	public void setMaxConnections(int maxCon) {
		this.maxConnections = maxCon;
		LimitLatch latch = this.connectionLimitLatch;
		if (latch != null) {
			// 更新执行此操作的闩锁
			if (maxCon == -1) {
				releaseConnectionLatch();
			} else {
				latch.setLimit(maxCon);
			}
		} else if (maxCon > 0) {
			initializeConnectionLatch();
		}
	}
	public int  getMaxConnections() { return this.maxConnections; }

	/**
	 * 如果计算了连接数(当限制了最大连接数时), 返回由这个端点处理的当前连接数;如果没有计算, 返回-1。
	 *
	 * 在Acceptor尝试接受新的连接之前, 该计数由Acceptor递增。
	 * 直到达到限制, 因此计数不能增加, 这个值比正在服务的连接的实际计数多1(接受的计数)
	 */
	public long getConnectionCount() {
		LimitLatch latch = connectionLimitLatch;
		if (latch != null) {
			return latch.getCount();
		}
		return -1;
	}

	public void setExecutor(Executor executor) {
		this.executor = executor;
		this.internalExecutor = (executor == null);
	}
	public Executor getExecutor() { return executor; }

	public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
		this.utilityExecutor = utilityExecutor;
	}
	public ScheduledExecutorService getUtilityExecutor() {
		if (utilityExecutor == null) {
			getLogger().warn("UtilityExecutor不能为空");
			utilityExecutor = new ScheduledThreadPoolExecutor(1);
		}
		return utilityExecutor;
	}

	public int getPort() { return port; }
	public void setPort(int port ) { this.port=port; }

	public int getPortOffset() { return portOffset; }
	public void setPortOffset(int portOffset ) {
		if (portOffset < 0) {
			throw new IllegalArgumentException("无效的服务器Socket端口偏移量, by portOffset: " + Integer.valueOf(portOffset));
		}
		this.portOffset = portOffset;
	}

	public int getPortWithOffset() {
		// 零是特殊情况, 负值无效
		int port = getPort();
		if (port > 0) {
			return port + getPortOffset();
		}
		return port;
	}

	public final int getLocalPort() {
		try {
			InetSocketAddress localAddress = getLocalAddress();
			if (localAddress == null) {
				return -1;
			}
			return localAddress.getPort();
		} catch (IOException ioe) {
			return -1;
		}
	}

	public InetAddress getAddress() { return address; }
	public void setAddress(InetAddress address) { this.address = address; }

	/**
	 * 获取服务器套接字绑定到的网络地址。
	 * 这主要是为了在解锁serversocket时使用正确的地址, 因为如果没有指定地址, 它将删除所涉及的推测工作
	 *
	 * @return 服务器套接字正在侦听的网络地址, 如果服务器套接字当前未绑定, 则为空
	 * @throws IOException - 如果确定当前绑定套接字时出现问题
	 */
	protected abstract InetSocketAddress getLocalAddress() throws IOException;

	public void setAcceptCount(int acceptCount) { if (acceptCount > 0) this.acceptCount = acceptCount; }
	public int getAcceptCount() { return acceptCount; }

	public boolean getBindOnInit() { return bindOnInit; }
	public void setBindOnInit(boolean b) { this.bindOnInit = b; }

	public int getKeepAliveTimeout() {
		if (keepAliveTimeout == null) {
			return getConnectionTimeout();
		} else {
			return keepAliveTimeout.intValue();
		}
	}
	public void setKeepAliveTimeout(int keepAliveTimeout) {
		this.keepAliveTimeout = Integer.valueOf(keepAliveTimeout);
	}

	/**
	 * Socket TCP无延迟
	 *
	 * @return 此端点创建的套接字的当前TCP无延迟设置
	 */
	public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
	public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }

	/**
	 * Socket 存续时间.
	 *
	 * @return 此端点创建的套接字的当前套接字延迟时间
	 */
	public int getConnectionLinger() { return socketProperties.getSoLingerTime(); }
	public void setConnectionLinger(int connectionLinger) {
		socketProperties.setSoLingerTime(connectionLinger);
		socketProperties.setSoLingerOn(connectionLinger>=0);
	}

	/**
	 * Socket 超时时间
	 *
	 * @return 此端点创建的套接字的当前套接字超时时间
	 */
	public int getConnectionTimeout() { return socketProperties.getSoTimeout(); }
	public void setConnectionTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }

	public void setMinSpareThreads(int minSpareThreads) {
		this.minSpareThreads = minSpareThreads;
		Executor executor = this.executor;
		if (internalExecutor && executor instanceof java.util.concurrent.ThreadPoolExecutor) {
			// 内部执行器应该始终是j.u.c.ThreadPoolExecutor的一个实例, 但如果端点没有运行, 它可能为空。这种检查还可以避免各种线程问题
			((java.util.concurrent.ThreadPoolExecutor) executor).setCorePoolSize(minSpareThreads);
		}
	}
	public int getMinSpareThreads() {
		return Math.min(getMinSpareThreadsInternal(), getMaxThreads());
	}
	private int getMinSpareThreadsInternal() {
		if (internalExecutor) {
			return minSpareThreads;
		} else {
			return -1;
		}
	}

	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
		Executor executor = this.executor;
		if (internalExecutor && executor instanceof java.util.concurrent.ThreadPoolExecutor) {
			// 内部执行器应该始终是j.u.c.ThreadPoolExecutor的一个实例, 但如果端点没有运行, 它可能为空。这种检查还可以避免各种线程问题。
			((java.util.concurrent.ThreadPoolExecutor) executor).setMaximumPoolSize(maxThreads);
		}
	}
	public int getMaxThreads() {
		if (internalExecutor) {
			return maxThreads;
		} else {
			return -1;
		}
	}

	public void setThreadPriority(int threadPriority) {
		// 一旦执行者启动, 就无法更改此设置
		this.threadPriority = threadPriority;
	}
	public int getThreadPriority() {
		if (internalExecutor) {
			return threadPriority;
		} else {
			return -1;
		}
	}

	public int getMaxKeepAliveRequests() {
		return maxKeepAliveRequests;
	}
	public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
		this.maxKeepAliveRequests = maxKeepAliveRequests;
	}

	public void setName(String name) { this.name = name; }
	public String getName() { return name; }


    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }
	
	public void setUseAsyncIO(boolean useAsyncIO) { this.useAsyncIO = useAsyncIO; }
	public boolean getUseAsyncIO() { return useAsyncIO; }

    protected abstract boolean getDeferAccept();
	
	public void addNegotiatedProtocol(String negotiableProtocol) {
		negotiableProtocols.add(negotiableProtocol);
	}
	public boolean hasNegotiableProtocols() {
		return (negotiableProtocols.size() > 0);
	}

	public void setHandler(Handler<S> handler ) { this.handler = handler; }
	public Handler<S> getHandler() { return handler; }

	public void setAttribute(String name, Object value) {
		if (getLogger().isTraceEnabled()) {
			getLogger().trace("端节点属性设置, name: {}, value: {}", name, value);
		}
		attributes.put(name, value);
	}
	public Object getAttribute(String key) {
		Object value = attributes.get(key);
		if (getLogger().isTraceEnabled()) {
			getLogger().trace("端节点属性获取, name: {}, value: {}", name, value);
		}
		return value;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isPaused() {
		return paused;
	}


	// ------------------------------------------------------------- 静态 -------------------------------------------------------------
	public static long toTimeout(long timeout) {
		// 许多调用不能执行无限超时, 所以使用 Long.MAX_VALUE (如果timeout <= 0)
		return (timeout > 0) ? timeout : Long.MAX_VALUE;
	}

	// -------------------------------------------------------------------------------------
	// HTTPS 相关方法
	// -------------------------------------------------------------------------------------
	public boolean isSSLEnabled() { return SSLEnabled; }

	public void setSSLEnabled(boolean SSLEnabled) { this.SSLEnabled = SSLEnabled; }

	/**
	 * 标识端点是否支持ALPN。注意, 返回值为true意味着{@link #isSSLEnabled()}也将返回true
	 *
	 * @return 如果端点在其当前配置中支持ALPN, 则为true, 否则为false
	 */
//	public abstract boolean isAlpnSupported();
	
	/**
	 * 添加给定的SSL主机配置
	 *
	 * @param sslHostConfig - 要添加的配置
	 * @throws IllegalArgumentException - 如果主机名无效, 或者已经为该主机提供了配置
	 */
//	public void addSslHostConfig(SSLHostConfig sslHostConfig) throws IllegalArgumentException {
//		addSslHostConfig(sslHostConfig, false);
//	}

	/**
	 * 添加给定的SSL主机配置, 可选地替换给定主机的现有配置
	 *
	 * @param sslHostConfig - 要添加的配置
	 * @param replace - 如果允许对一个存在的配置进行真正的替换, 否则任何此类尝试的替换都会触发异常
	 * @throws IllegalArgumentException - 如果主机名无效或已经为该主机提供了配置, 则不允许替换
	 */
//	public void addSslHostConfig(SSLHostConfig sslHostConfig, boolean replace) throws IllegalArgumentException {
//		String key = sslHostConfig.getHostName();
//		if (StringUtils.isBlank(key)) {
//			throw new IllegalArgumentException("无效的SslHostName, 不可为空值或空串");
//		}
//		if (bindState != BindState.UNBOUND && bindState != BindState.SOCKET_CLOSED_ON_STOP && isSSLEnabled()) {
//			try {
//				createSSLContext(sslHostConfig);
//			} catch (Exception e) {
//				throw new IllegalArgumentException(e);
//			}
//		}
//		if (replace) {
//			SSLHostConfig previous = sslHostConfigs.put(key, sslHostConfig);
//			/*
//			 * 不要释放任何与替换的SSLHostConfig相关联的SSLContexts。
//			 * 它们可能仍然被现有的连接使用, 释放它们最多只能断开连接。让GC来清理。
//			 */
//		} else {
//			SSLHostConfig duplicate = sslHostConfigs.putIfAbsent(key, sslHostConfig);
//			if (duplicate != null) {
//				releaseSSLContext(sslHostConfig);
//				throw new IllegalArgumentException("重复的SslHostName, by key: "+ key);
//			}
//		}
//	}

	/**
	 * 删除给定主机名的SSL主机配置(如果存在这样的配置).
	 *
	 * @param hostName - 与要删除的SSL主机配置关联的主机名
	 * @return  删除的SSL主机配置(如果有的话)
	 */
//	public SSLHostConfig removeSslHostConfig(String hostName) {
//		if (hostName == null) {
//			return null;
//		}
//		// 主机名不区分大小写
//		if (hostName.equalsIgnoreCase(getDefaultSSLHostConfigName())) {
//			throw new IllegalArgumentException("不能删除默认的SslHostConfig, by hostName: " + hostName);
//		}
//		SSLHostConfig sslHostConfig = sslHostConfigs.remove(hostName);
//		return sslHostConfig;
//	}

	/**
	 * 重新读取SSL主机的配置文件, 并用更新的设置替换existingSSL配置。注意, 即使设置保持不变, 也会发生替换。
	 *
	 * @param hostName - 应该为其加载配置的SSL主机。这必须匹配当前的SSL主机
	 */
//	public void reloadSslHostConfig(String hostName) {
//		SSLHostConfig sslHostConfig = sslHostConfigs.get(hostName);
//		if (sslHostConfig == null) {
//			throw new IllegalArgumentException(("未知的SslHostName, by hostName" + hostName));
//		}
//		addSslHostConfig(sslHostConfig, true);
//	}

	/**
	 * 重新读取所有SSL主机的配置文件, 并用更新的设置替换现有的SSL配置。
	 * 注意, 即使设置保持不变, 替换也会发生。
	 */
//	public void reloadSslHostConfigs() {
//		for (String hostName : sslHostConfigs.keySet()) {
//			reloadSslHostConfig(hostName);
//		}
//	}

//	public SSLHostConfig[] findSslHostConfigs() {
//		return sslHostConfigs.values().toArray(new SSLHostConfig[0]);
//	}

	/**
	 * 为给定的SSLHostConfig创建SSLContext
	 *
	 * @param sslHostConfig - 应该为其创建SSLContext的SSLHostConfig
	 * @throws Exception - 如果不能为给定的SSLHostConfig创建SSLContext
	 */
//	protected abstract void createSSLContext(SSLHostConfig sslHostConfig) throws Exception;

	protected void destroySsl() throws Exception {
		// TODO
//		if (isSSLEnabled()) {
//			for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
//				releaseSSLContext(sslHostConfig);
//			}
//		}
	}

	/**
	 * 释放与SSLHostConfig关联的SSLContext(如果有的话)
	 *
	 * @param sslHostConfig - 应该为其释放SSLContext的SSLHostConfig
	 */
//	protected void releaseSSLContext(SSLHostConfig sslHostConfig) {
//		for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
//			if (certificate.getSslContext() != null) {
//				SSLContext sslContext = certificate.getSslContext();
//				if (sslContext != null) {
//					sslContext.destroy();
//				}
//			}
//		}
//	}

//	protected SSLHostConfig getSSLHostConfig(String sniHostName) {
//		SSLHostConfig result = null;
//
//		if (sniHostName != null) {
//			// 第一选择-直接比较
//			result = sslHostConfigs.get(sniHostName);
//			if (result != null) {
//				return result;
//			}
//			// 第二选择, 通配符匹配
//			int indexOfDot = sniHostName.indexOf('.');
//			if (indexOfDot > -1) {
//				result = sslHostConfigs.get("*" + sniHostName.substring(indexOfDot));
//			}
//		}
//
//		// 备用。使用默认的
//		if (result == null) {
//			result = sslHostConfigs.get(getDefaultSSLHostConfigName());
//		}
//		if (result == null) {
//			throw new IllegalStateException();
//		}
//		return result;
//	}
	
	
	/**
	 * 连接处理程序
	 * @param <S>
	 */
	public static interface Handler<S> {
		/**
		 * 要响应的不同类型的套接字状态.
		 */
		public enum SocketState {
			/**
			 * 打开套接字
			 */
			OPEN, 
			/**
			 * 关闭套接字
			 */
			CLOSED, 
			/**
			 * 长久
			 */
			LONG, 
			/**
			 * 异步结束
			 */
			ASYNC_END, 
			/**
			 * 发送文件
			 */
			SENDFILE, 
			/**
			 * 升级中
			 */
			UPGRADING, 
			/**
			 * 已升级
			 */
			UPGRADED, 
			/**
			 * 暂停
			 */
			SUSPENDED,
			/**
			 * 结束，之后不在注册任何选择器时间
			 */
			END
		}

		/**
		 * 用给定的当前状态处理提供的套接字.
		 *
		 * @param socket - 要处理的socket
		 * @param status - 当前socket状态
		 * @param key - 用于日志记录的
		 * @return socket处理后的状态 SelectionKey
		 */
		public SocketState process(SocketWrapperBase<S> socket, SocketEvent status, SelectionKey key);

		/**
		 * 获取当前打开的套接字.
		 *
		 * @return 处理程序正在跟踪当前打开连接的套接字
		 */
		public Set<S> getOpenSockets();

		/**
		 * 释放与给定SocketWrapper关联的任何资源.
		 *
		 * @param socketWrapper - 要释放资源的socketWrapper
		 */
		public void release(SocketWrapperBase<S> socketWrapper);

		/**
		 * 通知处理程序端点已停止接受任何新连接。
		 * 通常, 端点会在短时间内停止, 但也有可能会恢复端点, 因此处理程序不应假定随后会停止.
		 */
		public void pause();

		/**
		 * 回收与处理程序关联的资源.
		 */
		public void recycle();
	}

	protected enum BindState {
		UNBOUND, BOUND_ON_INIT, BOUND_ON_START, SOCKET_CLOSED_ON_STOP
	}
}
