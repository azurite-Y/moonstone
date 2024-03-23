package org.zy.moonstone.core.util.net;

import org.zy.moonstone.core.Globals;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @dateTime 2022年1月24日;
 * @author zy(azurite-Y);
 * @description 线程安全的非阻塞选择器池
 */
public class NioSelectorPool {
	/** NIO选择器是否共享 */
	protected boolean shared = Globals.NIO_SELECTOR_SHARED;
	/** 是否启用 */
	protected boolean enabled = true;
	protected NioBlockingSelector blockingSelector;
	/** 共享选择器 */
	protected volatile Selector sharedSelector;
	/** 最大选择器数 */
	protected int maxSelectors = 200;
	/** 共享选择器数 */
	protected long sharedSelectorTimeout = 30000;
	/** 最大备用选择器数 */
	protected int maxSpareSelectors = -1;

	/** 在用的 {@link Selector } 计数器 */
	protected AtomicInteger active = new AtomicInteger(0);
	/** 备用的 {@link Selector } 计数器 */
	protected AtomicInteger spare = new AtomicInteger(0);
	/** {@link Selector } 队列 */
	protected ConcurrentLinkedQueue<Selector> selectors = new ConcurrentLinkedQueue<>();

	protected Selector getSharedSelector() throws IOException {
		if (shared && sharedSelector == null) {
			synchronized (NioSelectorPool.class) {
				if (sharedSelector == null) {
					sharedSelector = Selector.open();
				}
			}
		}
		return  sharedSelector;
	}

	public Selector get() throws IOException{
		if (shared) {
			return getSharedSelector();
		}
		if ((!enabled) || active.incrementAndGet() >= maxSelectors) {
			if (enabled) {
				active.decrementAndGet();
			}
			return null;
		}
		Selector s = null;
		try {
			s = selectors.size() > 0 ? selectors.poll() : null;
			if (s == null) {
				s = Selector.open();
			} else {
				spare.decrementAndGet();
			}
		} catch (NoSuchElementException x) {
			try {
				s = Selector.open();
			} catch (IOException iox) {}
		} finally {
			if (s == null) {
				active.decrementAndGet();// 找不到选择器
			}
		}
		return s;
	}

	public void put(Selector s) throws IOException {
		if (shared) {
			return;
		}
		if (enabled) {
			active.decrementAndGet();
		}
		if (enabled && (maxSpareSelectors == -1 || spare.get() < Math.min(maxSpareSelectors, maxSelectors))) {
			spare.incrementAndGet();
			// 追加到队列末尾
			selectors.offer(s);
		} else {
			s.close();
		}
	}

	/**
	 * 关闭选择器并释放所有资源的引用
	 * @throws IOException
	 */
	public void close() throws IOException {
		enabled = false;
		Selector s;
		while ((s = selectors.poll()) != null) {
			s.close();
		}
		spare.set(0);
		active.set(0);
		if (blockingSelector != null) {
			blockingSelector.close();
		}
		if (shared && getSharedSelector() != null) {
			getSharedSelector().close();
			sharedSelector = null;
		}
	}

	public void open(String name) throws IOException {
		enabled = true;
		getSharedSelector();
		if (shared) {
			blockingSelector = new NioBlockingSelector();
			blockingSelector.open(name, getSharedSelector());
		}
	}

	/**
	 * 使用字节缓冲区对要写入的数据执行写操作，并选择要阻塞的选择器(如果请求阻塞)。
	 * 如果选择器参数为空，并且阻塞被请求，那么它将执行一个繁忙的写操作，这将占用大量的CPU周期。
	 * @param buf - 在包含数据的缓冲区中，只要(buf.hasRemaining()==true)我们就会写入数据
	 * @param nioChannel - 要写入数据的套接字
	 * @param selector - 用于阻塞的选择器，如果为空，则会启动繁忙的写操作
	 * @param writeTimeout - 这个写操作的超时(以毫秒为单位)，-1表示没有超时
	 * @return 写入的字节数
	 * 
	 * @throws EOFException - 如果write返回-1
	 * @throws SocketTimeoutException - 如果写超时了
	 * @throws IOException - 如果一个IO异常发生在底层的套接字逻辑
	 */
	public int write(ByteBuffer buf, NioChannel nioChannel, Selector selector, long writeTimeout) throws IOException {
		if (shared) {
			return blockingSelector.write(buf, nioChannel, writeTimeout);
		}
		SelectionKey key = null;
		int written = 0;
		boolean timedout = false;
		int keycount = 1; // 假设我们可以写
		long time = System.currentTimeMillis(); // 启动超时计时器
		try {
			while ((!timedout) && buf.hasRemaining()) {
				int cnt = 0;
				if ( keycount > 0 ) { // 只有在注册写的时候才写
					cnt = nioChannel.write(buf); // 写入数据
					if (cnt == -1) {
						throw new EOFException();
					}

					written += cnt;
					if (cnt > 0) {
						time = System.currentTimeMillis(); // 重置超时计时器
						continue; // 成功写入，不用选择器再试一次
					}
				}
				if (selector != null) {
					// 将 OP_WRITE 注册到选择器
					if (key == null) {
						key = nioChannel.getIOChannel().register(selector, SelectionKey.OP_WRITE);
					} else {
						key.interestOps(SelectionKey.OP_WRITE);
					}
					if (writeTimeout == 0) {
						timedout = buf.hasRemaining();
					} else if (writeTimeout < 0) {
						keycount = selector.select();
					} else {
						keycount = selector.select(writeTimeout);
					}
				}
				if (writeTimeout > 0 && (selector == null || keycount == 0)) {
					timedout = (System.currentTimeMillis() - time) >= writeTimeout;
				}
			}
			if (timedout) {
				throw new SocketTimeoutException();
			}
		} finally {
			if (key != null) {
				key.cancel();
				if (selector != null) selector.selectNow();//removes the key from this selector
			}
		}
		return written;
	}

	/**
	 * 使用字节缓冲区对要读取的数据和要阻塞的选择器执行阻塞读取。如果选择器参数为空，那么它将执行一个繁忙的读取，这可能会占用大量的CPU周期。
	 * @param buf - ByteBuffer—包含数据的缓冲区，我们将读取数据，直到我们至少读取一个字节或超时
	 * @param socket - SocketChannel，写入数据的套接字
	 * @param selector - 用于阻塞的选择器，如果为null则启动忙读
	 * @param readTimeout - 此读取操作的超时时间（以毫秒为单位），-1 表示没有超时
	 * @return 读取的字节数
	 * @throws EOFException - 如果读取返回 -1
	 * @throws SocketTimeoutException - 如果读取超时
	 * @throws IOException - 如果底层套接字逻辑中发生 IO 异常
	 */
	public int read(ByteBuffer buf, NioChannel socket, Selector selector, long readTimeout) throws IOException {
		if (shared) {
			return blockingSelector.read(buf, socket, readTimeout);
		}
		SelectionKey key = null;
		int read = 0;
		boolean timedout = false;
		int keycount = 1; // 假设可以读
		long time = System.currentTimeMillis(); // 启动超时计时器
		try {
			while (!timedout) {
				int cnt = 0;
				if (keycount > 0) { // 仅在注册读时才读取
					cnt = socket.read(buf);
					if (cnt == -1) {
						if (read == 0) {
							read = -1;
						}
						break;
					}
					read += cnt;
					if (cnt > 0) continue;
					if (cnt == 0 && read > 0) {
						break; // 已读取完成
					}
				}
				if (selector != null) {// 执行阻塞读取
					// 将 OP_WRITE 注册到选择器
					if (key == null) {
						key = socket.getIOChannel().register(selector, SelectionKey.OP_READ);
					}
					else key.interestOps(SelectionKey.OP_READ);
					if (readTimeout == 0) {
						timedout = (read == 0);
					} else if (readTimeout < 0) {
						keycount = selector.select();
					} else {
						keycount = selector.select(readTimeout);
					}
				}
				if (readTimeout > 0 && (selector == null || keycount == 0) ) {
					timedout = (System.currentTimeMillis() - time) >= readTimeout;
				}
			}
			if (timedout) {
				throw new SocketTimeoutException();
			}
		} finally {
			if (key != null) {
				key.cancel();
				if (selector != null) {
					selector.selectNow(); // 从此选择器中删除键
				}
			}
		}
		return read;
	}

	public void setMaxSelectors(int maxSelectors) {
		this.maxSelectors = maxSelectors;
	}

	public void setMaxSpareSelectors(int maxSpareSelectors) {
		this.maxSpareSelectors = maxSpareSelectors;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setSharedSelectorTimeout(long sharedSelectorTimeout) {
		this.sharedSelectorTimeout = sharedSelectorTimeout;
	}

	public int getMaxSelectors() {
		return maxSelectors;
	}

	public int getMaxSpareSelectors() {
		return maxSpareSelectors;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getSharedSelectorTimeout() {
		return sharedSelectorTimeout;
	}

	public ConcurrentLinkedQueue<Selector> getSelectors() {
		return selectors;
	}

	public AtomicInteger getSpare() {
		return spare;
	}

	public boolean isShared() {
		return shared;
	}

	public void setShared(boolean shared) {
		this.shared = shared;
	}
}
