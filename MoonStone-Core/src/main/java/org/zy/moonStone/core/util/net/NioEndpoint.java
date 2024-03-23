package org.zy.moonstone.core.util.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.collections.SynchronizedQueue;
import org.zy.moonstone.core.util.collections.SynchronizedStack;
import org.zy.moonstone.core.util.net.AbstractEndpoint.Handler.SocketState;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.*;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class NioEndpoint extends AbstractJsseEndpoint<NioChannel,SocketChannel> {
	// ------------------------------------------------------------- 属性 -------------------------------------------------------------
	protected static final Logger logger = LoggerFactory.getLogger(NioEndpoint.class);

	private NioSelectorPool selectorPool = new NioSelectorPool();

	// 256
	public static final int OP_REGISTER = 0x100; 

	/**
	 * 服务器套接字通道
	 */
	private volatile ServerSocketChannel serverSocketChannel = null;

	/** 用于等待轮询器停止的停止锁存器 */
	private volatile CountDownLatch stopLatch = null;

	/**
	 * 已重置的 {@link PollerEvent 轮询事件 } 缓存
	 */
	private SynchronizedStack<PollerEvent> eventCache;

	/**
	 * Bytebuffer缓存, 每个通道保存一组缓冲区（两个, 除了SSL持有四个）
	 */
	private SynchronizedStack<NioChannel> nioChannels;

	/**
	 * 是否使用 System.inheritableChannel 从标准输入/标准输出获取通道
	 */
	private boolean useInheritedChannel = false;

	/**
	 * 轮询器线程的优先级
	 */
	private int pollerThreadPriority = Thread.NORM_PRIORITY;

	private long selectorTimeout = 1000;

	/**
	 * 套接字轮询器
	 */
	private Poller poller = null;



	// ------------------------------------------------------ getter、setter ------------------------------------------------------
	public void setUseInheritedChannel(boolean useInheritedChannel) { this.useInheritedChannel = useInheritedChannel; }
	public boolean getUseInheritedChannel() { return useInheritedChannel; }

	public void setPollerThreadPriority(int pollerThreadPriority) { this.pollerThreadPriority = pollerThreadPriority; }
	public int getPollerThreadPriority() { return pollerThreadPriority; }

	public void setSelectorTimeout(long timeout) { this.selectorTimeout = timeout;}
	public long getSelectorTimeout() { return this.selectorTimeout; }

	public void setSelectorPool(NioSelectorPool selectorPool) { this.selectorPool = selectorPool; }

	protected Poller getPoller() { return poller; }

	protected NioSelectorPool getSelectorPool() { return selectorPool; }

	protected SynchronizedStack<NioChannel> getNioChannels() { return nioChannels; }

	protected CountDownLatch getStopLatch() { return stopLatch; }

	protected void setStopLatch(CountDownLatch stopLatch) { this.stopLatch = stopLatch; }

	// ------------------------------------------------------ 保护方法 ----------------------------------------------------
	/**
	 * 处理指定的连接
	 * @param socketChannel - 套接字通道
	 * @return 如果正确配置了套接字并且可以继续处理, 则为 true, 如果需要立即关闭套接字, 则为 false
	 */
	@Override
	protected boolean setSocketOptions(SocketChannel socketChannel) {
		// 处理连接
		try {
			// 禁用阻塞, 将使用轮询
			socketChannel.configureBlocking(false);
			Socket socket = socketChannel.socket();
			socketProperties.setProperties(socket);

			NioChannel nioChannel = null;
			if (nioChannels != null) {
				nioChannel = nioChannels.pop();
			}
			if (nioChannel == null) {
				SocketBufferHandler bufhandler = new SocketBufferHandler(socketProperties.getInitialCapacity(), socketProperties.getMaxCapacity(), socketProperties.getDirectBuffer());
//				if (isSSLEnabled()) {
//					nioChannel = new SecureNioChannel(bufhandler, selectorPool, this);
//				} else {
//					nioChannel = new NioChannel(bufhandler);
//				}
				
				nioChannel = new NioChannel(bufhandler);
			}
			NioSocketWrapper socketWrapper = new NioSocketWrapper(nioChannel, this);
			nioChannel.reset(socketChannel, socket, socketWrapper);
			socketWrapper.setReadTimeout(getConnectionTimeout());
			socketWrapper.setWriteTimeout(getConnectionTimeout());
			socketWrapper.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
//			socketWrapper.setSecure(isSSLEnabled());
			poller.register(nioChannel, socketWrapper);
			return true;
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			try {
				logger.error("套接字选项错误", t);
			} catch (Throwable tt) {
				ExceptionUtils.handleThrowable(tt);
			}
		}
		// 反馈需关闭套接字
		return false;
	}

	@Override
	protected void closeSocket(SocketChannel socket) {
		countDownConnection();
		try {
			socket.close();
		} catch (IOException ioe) {
			if (logger.isDebugEnabled()) {
				logger.debug("服务器套接字关闭异常", ioe);
			}
		}
	}

	@Override
	protected ServerSocketChannel getServerSocket() {
		return serverSocketChannel;
	}

	@Override
	protected SocketChannel serverSocketAccept() throws Exception {
		return serverSocketChannel.accept();
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	protected SocketProcessorBase<NioChannel> createSocketProcessor(SocketWrapperBase<NioChannel> socketWrapper, SocketEvent event) {
		return new SocketProcessor(socketWrapper, event);
	}

	// ----------------------------------------------------- 公共方法 -----------------------------------------------------
	@Override
	public boolean getDeferAccept() {
		// 不支持
		return false;
	}

	/**
	 * 保持活动的套接字数
	 *
	 * @return 当前处于保持活动状态等待套接字上接收下一个请求的套接字数
	 */
	public int getKeepAliveCount() {
		if (poller == null) {
			return 0;
		} else {
			return poller.getKeyCount();
		}
	}

	// --------------------------------------------------- 公共生命周期方法 ---------------------------------------------------
	/**
	 * 初始化端点
	 */
	@Override
	public void bind() throws Exception {
		initServerSocket();

		setStopLatch(new CountDownLatch(1));

		// 如果需要, 初始化SSL
		initialiseSsl();

		selectorPool.open(getName());
	}

	protected void initServerSocket() throws Exception {
		if (!getUseInheritedChannel()) {
			serverSocketChannel = ServerSocketChannel.open();
			socketProperties.setProperties(serverSocketChannel.socket());
			InetSocketAddress addr = new InetSocketAddress(getAddress(), getPortWithOffset());
			serverSocketChannel.socket().bind(addr,getAcceptCount());
		} else {
			// 检索操作系统提供的通道
			Channel ic = System.inheritedChannel();
			if (ic instanceof ServerSocketChannel) {
				serverSocketChannel = (ServerSocketChannel) ic;
			}
			if (serverSocketChannel == null) {
				throw new IllegalArgumentException("服务器套接字初始化异常");
			}
		}
		// 设置为阻塞模式
		serverSocketChannel.configureBlocking(true);
	}


	/**
	 * 启动NIO端点, 创建acceptor、poller线程
	 */
	@Override
	public void startInternal() throws Exception {
		if (!running) {
			running = true;
			paused = false;

			//  {@link SocketProcessor } 对象缓存的尺寸
			if (socketProperties.getProcessorCache() != 0) {
				processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE, socketProperties.getProcessorCache());
			}
			// {@link PollerEvent } 对象缓存的尺寸
			if (socketProperties.getEventCache() != 0) {
				eventCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE, socketProperties.getEventCache());
			}
			// Endpoint的NioChannel池大小
			if (socketProperties.getBufferPool() != 0) {
				nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE, socketProperties.getBufferPool());
			}

			if (getExecutor() == null) {
				createExecutor();
			}

			initializeConnectionLatch();

			// 启动轮询线程
			poller = new Poller();
			String pollerThreadName = getName() + "-Poller";
			Thread pollerThread = new Thread(poller, pollerThreadName);
			pollerThread.setPriority(threadPriority);
			pollerThread.setDaemon(true);
			pollerThread.start();

			startAcceptorThread();
		}
	}


	/**
	 * 停止端点。 这将导致所有处理线程停止
	 */
	@Override
	public void stopInternal() {
		if (!paused) {
			pause();
		}
		if (running) {
			running = false;
			if (poller != null) {
				poller.destroy();
				poller = null;
			}
			try {
				/**
				 * 使当前线程等待，直到锁存器倒数为零，除非线程中断或指定的等待时间过去。
				 * 
				 * 如果当前计数为零，则此方法立即返回值为true。
				 * 
				 * 如果当前计数大于零，则出于线程调度的目的，当前线程将被禁用，并在以下三种情况之一发生之前一直处于休眠状态：
				 * 由于countDown方法的调用，计数达到零；或其他一些线程中断当前线程；或经过指定的等待时间。
				 * 
				 * 如果计数为零，则该方法返回值为true。
				 * 
				 * 如果当前线程：在进入该方法时设置其中断状态；或等待时中断，则抛出InterruptedException，并清除当前线程的中断状态。
				 * 如果经过了指定的等待时间，则返回值false。如果时间小于或等于零，则该方法根本不会等待。
				 * 
				 * @param timeout - 等待的最长时间
				 * @param unit - 超时参数的时间单位
				 * @return 如果计数达到零，则为true；如果在计数达到零之前经过了等待时间，则为false
				 * @exception InterruptedException - 如果当前线程在等待时中断
				 */
				if (!getStopLatch().await(selectorTimeout + 100, TimeUnit.MILLISECONDS)) {
					logger.warn("停止计时器等待失败");
				}
			} catch (InterruptedException e) {
				logger.warn("停止计时器等待被中断. msg: {}", e.getMessage());
			}
			shutdownExecutor();
			if (eventCache != null) {
				eventCache.clear();
				eventCache = null;
			}
			if (nioChannels != null) {
				nioChannels.clear();
				nioChannels = null;
			}
			if (processorCache != null) {
				processorCache.clear();
				processorCache = null;
			}
		}
	}


	/**
	 * 释放 NIO 内存池, 并关闭服务器套接字
	 */
	@Override
	public void unbind() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("为 [{}] 发起销毁", new InetSocketAddress(getAddress(),getPortWithOffset()));
		}
		if (running) {
			stop();
		}
		doCloseServerSocket();
		destroySsl();
		super.unbind();
		if (getHandler() != null ) {
			getHandler().recycle();
		}
		selectorPool.close();
		if (logger.isDebugEnabled()) {
			logger.debug("销毁完成, by [{}] ", new InetSocketAddress(getAddress(), getPortWithOffset()));
		}
	}


	@Override
	protected void doCloseServerSocket() throws IOException {
		if (!getUseInheritedChannel() && serverSocketChannel != null) {
			// 关闭服务器套接字
			serverSocketChannel.close();
		}
		serverSocketChannel = null;
	}

	
	// ----------------------------------------------------- 轮询内部类 -----------------------------------------------------
	/**
	 * PollerEvent, 用于轮询事件的可缓存对象, 以避免 GC
	 */
	public static class PollerEvent {
		private NioChannel socket;
		private int interestOps;

		public PollerEvent(NioChannel ch, int intOps) {
			reset(ch, intOps);
		}

		/**
		 * 覆盖重置复用的轮询器事件
		 * @param ch
		 * @param intOps
		 */
		public void reset(NioChannel ch, int intOps) {
			socket = ch;
			interestOps = intOps;
		}

		public void reset() {
			reset(null, 0);
		}

        public NioChannel getSocket() {
            return socket;
        }
        
        public int getInterestOps() {
			return interestOps;
		}
		
		public void run() {
			if (interestOps == OP_REGISTER) {
				try {
					/**
					 * 将Socket通道注册到选择器上, 并指定监听读事件。后将SocketWrapper注册为附件
					 * 
					 * 向给定选择器注册此通道, 并返回选择键。该方法首先验证该通道是否打开, 以及给定的初始兴趣集是否有效。
					 * 
					 * 如果该通道已向给定选择器注册, 则在将其兴趣集设置为给定值后返回表示该注册的选择键。
					 * 
					 * 否则, 该通道还没有向给定的选择器注册, 因此在持有适当的锁的同时调用选择器的注册方法。得到的密钥在返回之前被添加到该通道的密钥集。
					 * 
					 * @param sel - 要向其注册此通道的选择器
					 * @param ops - 结果密钥的兴趣集
					 * @param att - 所产生的密钥的附件;可能为空
					 */
					Selector pollerSelector = socket.getSocketWrapper().getPoller().getSelector();
					socket.getIOChannel().register(pollerSelector, SelectionKey.OP_READ, socket.getSocketWrapper());
					if (logger.isDebugEnabled()) {
						logger.debug("Register SelectionKey. by Poller: {}, key: OP_READ", pollerSelector);
					}
					
				} catch (Exception x) {
					logger.error("Socket通道注册失败", x);
				}
			} else {
				// 检索SocketChannel在Selector中注册的SelectionKey
				final SelectionKey key = socket.getIOChannel().keyFor(socket.getSocketWrapper().getPoller().getSelector());
				try {
					if (key == null) {
						try {
							/*
							 * key被取消（例如由于 socket 关闭）并在处理时从selector 中删除。 此时倒计时连接, 因为当套接字关闭时它不会倒计时。 
							 */
							socket.socketWrapper.close();
						} catch (Exception ignore) {}
					} else {
						// 提取附件对象
						final NioSocketWrapper socketWrapper = (NioSocketWrapper) key.attachment();
						if (socketWrapper != null) {
							// 开始注册key, 重置公平计数器
							int ops = key.interestOps() | interestOps;
							socketWrapper.interestOps(ops);
							key.interestOps(ops);
						} else {
							socket.getSocketWrapper().getPoller().cancelledKey(key, socket.getSocketWrapper());
						}
					}
				} catch (CancelledKeyException ckx) {
					try {
						socket.getSocketWrapper().getPoller().cancelledKey(key, socket.getSocketWrapper());
					} catch (Exception ignore) {}
				}
			}
		}

		@Override
		public String toString() {
			return "Poller event: socket [" + socket + "], socketWrapper [" + socket.getSocketWrapper() +
					"], interestOps [" + interestOps + "]";
		}
	}

	/**
	 * 轮询器, 负责监听客户端连接和注册对应事件
	 */
	public class Poller implements Runnable {
		private Selector selector;
		/**
		 *  轮询器事件
		 */
		private final SynchronizedQueue<PollerEvent> events = new SynchronizedQueue<>();

		private volatile boolean close = false;
		// 优化过期处理
		private long nextExpiration = 0;

		private AtomicLong wakeupCounter = new AtomicLong(0);

		/** 保存当前选择器上已经“准备就绪”的事件数 */
		private volatile int keyCount = 0;

		/**
		 * 创建一个Poll对象, 并获得一个 Selector 对象
		 * @throws IOException
		 */
		public Poller() throws IOException {
			this.selector = Selector.open();
		}

		public int getKeyCount() { return keyCount; }

		public Selector getSelector() { return selector; }

		/**
		 * 销毁轮询器
		 */
		protected void destroy() {
			// 在执行任何操作之前等待轮询时间, 以便轮询器线程退出, 否则并行关闭仍在轮询器中的套接字可能会导致问题
			close = true;
			selector.wakeup();
		}

		/**
		 * 添加轮询器事件
		 * @param event
		 */
		private void addEvent(PollerEvent event) {
			events.offer(event);
			if (wakeupCounter.incrementAndGet() == 0) {
				selector.wakeup();
			}
		}

		/**
		 * 将指定的套接字和关联池添加到轮询器。套接字将被添加到一个临时数组中, 并在与轮询时间相等的最长时间后首先轮询（不过, 在大多数情况下, 延迟会低得多）。
		 *
		 * @param socketWrapper - 添加的轮询器
		 * @param interestOps - 向轮询器注册此套接字的操作
		 */
		public void add(NioSocketWrapper socketWrapper, int interestOps) {
			PollerEvent r = null;
			if (eventCache != null) {
				r = eventCache.pop();
			}
			if (r == null) {
				r = new PollerEvent(socketWrapper.getSocketChannel(), interestOps);
			} else {
				r.reset(socketWrapper.getSocketChannel(), interestOps);
			}
			addEvent(r);
			if (close) {
				processSocket(socketWrapper, SocketEvent.STOP, false);
			}
		}

		/**
		 * 处理轮询器的事件队列中的事件
		 *
		 * @return 如果处理了某些事件, 则为true；如果队列为空, 则为false
		 */
		public boolean events() {
			boolean result = false;

            PollerEvent pe = null;
            for (int i = 0, size = events.size(); i < size && (pe = events.poll()) != null; i++ ) {
                result = true;
                NioChannel channel = pe.getSocket();
                NioSocketWrapper socketWrapper = channel.getSocketWrapper();
                int interestOps = pe.getInterestOps();
                if (interestOps == OP_REGISTER) {
                    try {
                    	/**
    					 * 将Socket通道注册到选择器上, 并指定监听读事件。后将SocketWrapper注册为附件
    					 * 
    					 * 向给定选择器注册此通道, 并返回选择键。该方法首先验证该通道是否打开, 以及给定的初始兴趣集是否有效。
    					 * 
    					 * 如果该通道已向给定选择器注册, 则在将其兴趣集设置为给定值后返回表示该注册的选择键。
    					 * 
    					 * 否则, 该通道还没有向给定的选择器注册, 因此在持有适当的锁的同时调用选择器的注册方法。得到的密钥在返回之前被添加到该通道的密钥集。
    					 * 
    					 * @param sel - 要向其注册此通道的选择器
    					 * @param ops - 结果密钥的兴趣集
    					 * @param att - 所产生的密钥的附件;可能为空
    					 */
                        channel.getIOChannel().register(getSelector(), SelectionKey.OP_READ, socketWrapper);
                    } catch (Exception x) {
                        logger.error("Endpoint#events-OP_READ注册失败", x);
                    }
                } else {
                    final SelectionKey key = channel.getIOChannel().keyFor(getSelector());
                    if (key == null) {
                        // key在处理过程中已被取消(例如，由于套接字关闭)并从选择器中移除。此时对连接进行倒计时，因为在套接字关闭时不会进行倒计时
                        socketWrapper.close();
                    } else {
                        final NioSocketWrapper attachment = (NioSocketWrapper) key.attachment();
                        if (attachment != null) {
                            // 开始注册密钥，重置公平计数器
                            try {
                                int ops = key.interestOps() | interestOps;
                                attachment.interestOps(ops);
                                key.interestOps(ops);
                            } catch (CancelledKeyException ckx) {
                                cancelledKey(key, socketWrapper);
                            }
                        } else {
                            cancelledKey(key, attachment);
                        }
                    }
                }
                if (running && !paused && eventCache != null) {
                    pe.reset();
                    eventCache.push(pe);
                }
            }

            return result;
		}
		
		/**
		 * 处理轮询器的事件队列中的事件
		 *
		 * @return 如果处理了某些事件, 则为true；如果队列为空, 则为false
		 */
		public boolean events2() {
			boolean result = false;

			PollerEvent pollerEvent = null;
			// 从 缓存(events) 中返回可用的PollerEvent
			for (int i = 0, size = events.size(); i < size && (pollerEvent = events.poll()) != null; i++ ) {
				result = true;
				try {
					pollerEvent.run();
					pollerEvent.reset();
					if (running && !paused && eventCache != null) {
						eventCache.push(pollerEvent);
					}
				} catch ( Throwable x ) {
					logger.error("轮询事件异常", x);
				}
			}

			return result;
		}

		/**
		 * 向轮询器注册新创建的套接字
		 *
		 * @param nioChannel - 新创建的套接字
		 * @param socketWrapper
		 */
		public void register(final NioChannel nioChannel, final NioSocketWrapper socketWrapper) {
			// 注册读事件
			socketWrapper.interestOps(SelectionKey.OP_READ);

			PollerEvent r = null;
			if (eventCache != null) {
				r = eventCache.pop();
			}
			if (r == null) {
				r = new PollerEvent(nioChannel, OP_REGISTER);
			} else {
				r.reset(nioChannel, OP_REGISTER);
			}
			addEvent(r);
		}

		/**
		 * 取消 SelectionKey 和注册的附件
		 * @param sk
		 * @param socketWrapper
		 */
		public void cancelledKey(SelectionKey sk, SocketWrapperBase<NioChannel> socketWrapper) {
			try {
				if (socketWrapper != null) {
					socketWrapper.close();
				}
				if (sk != null) {
					/*
					 * 将给定对象附加到此键。
					 * 附加的对象可以稍后通过附加方法检索。一次只能附加一个对象；调用此方法会导致丢弃任何先前的附件。可以通过附加 null 来丢弃当前附件。
					 */
					sk.attach(null);
					if (sk.isValid()) {
						sk.cancel();
					}
					// 通过 SocketChannel 也可以获得 SelectionKey 。 如果上面的块中没有关闭, 现在关闭
					if (sk.channel().isOpen()) {
						sk.channel().close();
					}
				}
			} catch (Throwable e) {
				ExceptionUtils.handleThrowable(e);
				if (logger.isDebugEnabled()) {
					logger.error("通道关闭失败", e);
				}
			}
		}

		/**
		 * 将套接字添加到轮询器的后台线程检查轮询器是否有触发事件, 并在事件发生时将关联的套接字交给适当的处理器。
		 */
		@Override
		public void run() {
			// 循环直到 destroy() 被调用
			while (true) {
				boolean hasEvents = false;

				try {
					if (!close) {
						hasEvents = events();
						if (wakeupCounter.getAndSet(-1) > 0) {
							// 触发此逻辑意味着还有其他事情需要处理, 所以在此做非阻塞选择
							keyCount = selector.selectNow();
						} else {
							keyCount = selector.select(selectorTimeout);
						}
						wakeupCounter.set(0);
					}
					if (close) {
						events();
						timeout(0, false);
						try {
							selector.close();
						} catch (IOException ioe) {
							logger.error("选择器关闭失败", ioe);
						}
						break;
					}
				} catch (Throwable x) {
					ExceptionUtils.handleThrowable(x);
					logger.error("选择器循环错误", x);
					continue;
				}
				
				if (keyCount == 0) {
					hasEvents = (hasEvents | events());
				}

				Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
				// 遍历就绪键的集合并调度任何活动事件
				while (iterator != null && iterator.hasNext()) {
					SelectionKey sk = iterator.next();
					NioSocketWrapper socketWrapper = (NioSocketWrapper) sk.attachment();
					// 如果另一个线程调用了cancelledKey(), 附件可能为空
					if (socketWrapper == null) {
						// 取消选择键 SelectionKey
						iterator.remove();
					} else {
						// 取消选择键 SelectionKey
						iterator.remove();
						processKey(sk, socketWrapper);
					}
				}

				// 进程超时
				timeout(keyCount,hasEvents);
			}

			getStopLatch().countDown();
		}

		/**
		 * SelectionKey 处理
		 * @param sk - SelectionKey 实例
		 * @param socketWrapper
		 */
		protected void processKey(SelectionKey sk, NioSocketWrapper socketWrapper) {
			try {
				if (close) {
					cancelledKey(sk, socketWrapper);
				} else if (sk.isValid() && socketWrapper != null) {
					if (sk.isReadable() || sk.isWritable()) {
						if (socketWrapper.getSendfileData() != null) {
							processSendfile(sk, socketWrapper, false);
						} else {
							unreg(sk, socketWrapper, sk.readyOps());
							boolean closeSocket = false;
							// 读先于写
							if (sk.isReadable()) {
								if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
									closeSocket = true;
								}
							}
							if (!closeSocket && sk.isWritable()) {
								if (!processSocket(socketWrapper, SocketEvent.OPEN_WRITE, true)) {
									closeSocket = true;
								}
							}
							if (closeSocket) {
								cancelledKey(sk, socketWrapper);
							}
						}
					}
				} else {
					// 无效 key
					cancelledKey(sk, socketWrapper);
				}
			} catch (CancelledKeyException ckx) {
				cancelledKey(sk, socketWrapper);
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				logger.error("key 处理错误", t);
			}
		}

		public SendfileState processSendfile(SelectionKey sk, NioSocketWrapper socketWrapper, boolean calledByProcessor) {
			NioChannel sc = null;
			try {
				unreg(sk, socketWrapper, sk.readyOps());
				SendfileData sendFileData = socketWrapper.getSendfileData();

				if (logger.isTraceEnabled()) {
					logger.trace("Processing send file for: " + sendFileData.fileName);
				}

				if (sendFileData.fileChannel == null) {
					// 设置文件通道
					File f = new File(sendFileData.fileName);
					@SuppressWarnings("resource") // 通道关闭时关闭
					FileInputStream fis = new FileInputStream(f);
					sendFileData.fileChannel = fis.getChannel();
				}

				// 配置输出通道
				sc = socketWrapper.getSocketChannel();
				// TLS/SSL 通道略有不同
//				WritableByteChannel writableByteChannel = ((sc instanceof SecureNioChannel) ? sc : sc.getIOChannel());
				SocketChannel socketChannel = sc.getIOChannel();
				
				// 缓冲区中还有数据
				if (sc.getOutboundRemaining() > 0) {
					if (sc.flushOutbound()) {
						socketWrapper.updateLastWrite();
					}
				} else {
					/**
					 * 将字节从该通道的文件传输到给定的可写字节通道
					 */
//					long written = sendFileData.fileChannel.transferTo(sendFileData.pos, sendFileData.length, writableByteChannel);
					long written = sendFileData.fileChannel.transferTo(sendFileData.pos, sendFileData.length, socketChannel);
					if (written > 0) {
						sendFileData.pos += written;
						sendFileData.length -= written;
						socketWrapper.updateLastWrite();
					} else {
						// 无法传输任何字节异常, 检查长度是否设置正确
						if (sendFileData.fileChannel.size() <= sendFileData.pos) {
							throw new IOException(String.format("指定传输文件通道内无法传输任何字节, by 指定传输字节数: [%s], 文件通道内字节数: [%s], 传输文件: %s", sendFileData.pos, written, sendFileData.fileName));
						}
					}
				}
				if (sendFileData.length <= 0 && sc.getOutboundRemaining()<=0) { // 如果文件已发送完成
					if (logger.isDebugEnabled()) {
						logger.debug("NioEndpoint.Poller#processSendfile-Send file complete for: " + sendFileData.fileName);
					}
					socketWrapper.setSendfileData(null);
					try {
						// 关闭文件通道
						sendFileData.fileChannel.close();
					} catch (Exception ignore) {}
					
					// 对于来自轮询器外部的调用, 如果sendfile完成, 则调用者负责为相应事件注册套接字
					if (!calledByProcessor) {
						switch (sendFileData.keepAliveState) {
							case NONE: {
								if (logger.isDebugEnabled()) {
									logger.debug("发送文件连接在发送文件完成后正在关闭");
								}
								poller.cancelledKey(sk, socketWrapper);
								break;
							}
							case PIPELINED: {
								if (logger.isDebugEnabled()) {
									logger.debug("连接保持活动状态, 处理管道数据");
								}
								if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
									poller.cancelledKey(sk, socketWrapper);
								}
								break;
							}
							case OPEN: {
								if (logger.isDebugEnabled()) {
									logger.debug("连接保持活动状态, 重新注册 OP_READ");
								}
								regKey(sk, socketWrapper, SelectionKey.OP_READ);
								break;
							}
						}
					}
					return SendfileState.DONE;
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("注册OP_WRITE事件以为了继续发送剩余文件数据: " + sendFileData.fileName);
					}
					if (calledByProcessor) {
						add(socketWrapper, SelectionKey.OP_WRITE);
					} else {
						regKey(sk, socketWrapper, SelectionKey.OP_WRITE);
					}
					return SendfileState.PENDING;
				}
			} catch (IOException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("无法完成发送文件请求:", e);
				}
				if (!calledByProcessor && sc != null) {
					poller.cancelledKey(sk, socketWrapper);
				}
				return SendfileState.ERROR;
			} catch (Throwable t) {
				logger.error("发送文件错误", t);
				if (!calledByProcessor && sc != null) {
					poller.cancelledKey(sk, socketWrapper);
				}
				return SendfileState.ERROR;
			}
		}

		protected void unreg(SelectionKey sk, NioSocketWrapper socketWrapper, int readyOps) {
			/*
			 * 这是必须的, 这样就不会有多个线程干扰套接字
			 * 
			 * “～”符号代表按位取反, , “&”代表按位与
			 * sk.interestOps() & (~readyOps): 代表着如果原sk.interestOps()中有readyOps则删除readyOps, 没有则保持不变
			 */
			regKey(sk, socketWrapper, sk.interestOps() & (~readyOps));
		}

		protected void regKey(SelectionKey sk, NioSocketWrapper socketWrapper, int intops) {
			/*
			 * 将此键的兴趣集设置为给定值。
			 * 可以随时调用此方法。它是否阻塞以及阻塞时间取决于实现。
			 * 
			 * @param ops - 新的兴趣集
			 */
			sk.interestOps(intops);
			socketWrapper.interestOps(intops);
		}

		/**
		 * 在 Poller 的每个循环上都会调用此方法。 不要在轮询器的每个循环上处理超时, 因为这会产生过多的负载, 并且超时可以等待几秒钟。 
		 * 但是, 如果以下任何一项为真, 请执行处理超时: 
		 * <ul>
		 * <li>在等待通道准备就绪阻塞超时（表明没有太多负载）</li>
		 * <li>轮询器设置的 {@code nextExpiration } 时间已过</li>
		 * <li>服务器套接字正在关闭</li>
		 * </ul>
		 * @param keyCount - 当前“准备就绪”的SelectionKey 数
		 * @param hasEvents - 是否还有剩余事件需处理？
		 */
		protected void timeout(int keyCount, boolean hasEvents) {
			long now = System.currentTimeMillis();
			if (nextExpiration > 0 && (keyCount > 0 || hasEvents) && (now < nextExpiration) && !close) {
				return;
			}
			int keycount = 0;
			try {
				for (SelectionKey key : selector.keys()) {
					keycount++;
					try {
						NioSocketWrapper socketWrapper = (NioSocketWrapper) key.attachment();
						if (socketWrapper == null) {
							// 不支持任何没有附件的key
							cancelledKey(key, null);
						} else if (close) {
							key.interestOps(0);
							// 避免重复的停止调用
							socketWrapper.interestOps(0);
							cancelledKey(key, socketWrapper);
						
						/*
						 * ( value & x ) == x: 意图判断value中是否包含x, 若包含则为true, 反之则为false
						 */
						} else if ((socketWrapper.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ ||
								(socketWrapper.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
							boolean isTimedOut = false;
							boolean readTimeout = false;
							boolean writeTimeout = false;
							// 检查读取超时
							if ((socketWrapper.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
								long delta = now - socketWrapper.getLastRead();
								long timeout = socketWrapper.getReadTimeout();
								
//								if (timeout > 0 && delta > timeout) readTimeout = true;
								readTimeout = timeout > 0 && delta > timeout;
							}
							// 检查写入超时
							if (!isTimedOut && (socketWrapper.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
								long delta = now - socketWrapper.getLastWrite();
								long timeout = socketWrapper.getWriteTimeout();
								
//								if (timeout > 0 && delta > timeout) writeTimeout = true;
								writeTimeout = timeout > 0 && delta > timeout;
							}
							
							if (readTimeout || writeTimeout) {
								if (logger.isDebugEnabled()) {
									StringBuilder builder = new StringBuilder();
									builder.append("Socket");
									if (readTimeout) {
										builder.append("读");
									}
									if (writeTimeout) {
										builder.append("写");
									}
									builder.append("超时处理, SelectionKey: {}, by SocketWrapper: {}");
									logger.debug(builder.toString(), key, socketWrapper);
								}
								
                                key.interestOps(0);
                                // 避免重复的超时调用
                                socketWrapper.interestOps(0);
                                socketWrapper.setError(new SocketTimeoutException());
                                
                                if (!processSocket(socketWrapper, SocketEvent.ERROR, true)) {
                                	logger.debug("Socket连接超时... by socketWrapper: {}", socketWrapper);
                                    cancelledKey(key, socketWrapper);
                                }
                            }
						}
					} catch (CancelledKeyException ckx) {
						cancelledKey(key, (NioSocketWrapper) key.attachment());
					}
				}
			} catch (ConcurrentModificationException cme) {
				logger.warn("超时处理异常", cme);
			}
			
			// 仅用于记录目的
			long prevExp = nextExpiration;
			nextExpiration = System.currentTimeMillis() + socketProperties.getTimeoutInterval();
			if (logger.isTraceEnabled()) {
				logger.trace("timeout completed: keys processed=" + keycount +
						"; now=" + now + "; nextExpiration=" + prevExp +
						"; keyCount=" + keyCount + "; hasEvents=" + hasEvents +
						"; eval=" + ((now < prevExp) && (keyCount>0 || hasEvents) && (!close) ));
			}

		}
	}

	/**
	 * 发送文件数据类
	 */
	public static class SendfileData extends SendfileDataBase {
		public SendfileData(String filename, long pos, long length) {
			super(filename, pos, length);
		}

		protected volatile FileChannel fileChannel;
	}

	/**
	 * 使用给定的SocketChannel包装器和SocketEvent 创建一个Socket处理器。
	 * 此类相当于 Worker, 但只会在外部 Executor 线程池中使用
	 */
	protected class SocketProcessor extends SocketProcessorBase<NioChannel> {
		public SocketProcessor(SocketWrapperBase<NioChannel> socketWrapper, SocketEvent event) {
			super(socketWrapper, event);
		}

		@Override
		protected void doRun() {
			NioChannel socket = socketWrapper.getSocketChannel();
			SelectionKey key = socket.getIOChannel().keyFor(socket.getSocketWrapper().getPoller().getSelector());
			
			if (logger.isDebugEnabled()) {
				logger.debug("SocketChannelHashCode: {}, SelectionKey: {}", socket.getIOChannel().hashCode(), key);
			}
			Poller poller = NioEndpoint.this.poller;
			if (poller == null) {
				socketWrapper.close();
				return;
			}

			try {
				int handshake = -1;

				try {
					if (key != null) {
						if (socket.isHandshakeComplete()) {
							// 无需 TLS 握手。让处理程序处理这个套接字/事件组合
							handshake = 0;
						} else if (event == SocketEvent.STOP || event == SocketEvent.DISCONNECT || event == SocketEvent.ERROR) {
							// 无法完成 TLS 握手。 将其视为握手失败
							handshake = -1;
						} else {
							// 执行 SSL 握手
							handshake = socket.handshake(key.isReadable(), key.isWritable());
							/*
							 * 握手进程对套接字进行读写操作。因此, 一旦握手完成, status可能是OPEN_WRITE。
							 * 但是, 握手发生在套接字打开时, 所以在握手完成后, 状态必须总是为OPEN_READ。
							 * 总是设置它是可以的, 因为它只在握手完成时使用。
							 */
							event = SocketEvent.OPEN_READ;
						}
					}
				} catch (IOException x) {
					handshake = -1;
					if (logger.isDebugEnabled())  logger.debug("SSL握手时出错",x);
				} catch (CancelledKeyException ckx) {
					handshake = -1;
				}
				if (handshake == 0) {
					SocketState state = SocketState.OPEN;
					// 处理来自这个套接字的请求
					if (event == null) {
						state = getHandler().process(socketWrapper, SocketEvent.OPEN_READ, key);
					} else {
						state = getHandler().process(socketWrapper, event, key);
					}
					if (state == SocketState.CLOSED) {
						poller.cancelledKey(key, socketWrapper);
					}
				} else if (handshake == -1 ) {
					getHandler().process(socketWrapper, SocketEvent.CONNECT_FAIL, key);
					poller.cancelledKey(key, socketWrapper);
				} else if (handshake == SelectionKey.OP_READ){
					socketWrapper.registerReadInterest();
				} else if (handshake == SelectionKey.OP_WRITE){
					socketWrapper.registerWriteInterest();
				}
			} catch (CancelledKeyException cx) {
				poller.cancelledKey(key, socketWrapper);
			} catch (VirtualMachineError vme) {
				ExceptionUtils.handleThrowable(vme);
			} catch (Throwable t) {
				logger.error("断点处理失败", t);
				poller.cancelledKey(key, socketWrapper);
			} finally {
				socketWrapper = null;
				event = null;
				// 返回缓存
				if (running && !paused && processorCache != null) {
					processorCache.push(this);
				}
			}
		}
	}


}
