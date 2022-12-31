package org.zy.moonStone.core.util.net;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.collections.SynchronizedStack;
import org.zy.moonStone.core.util.http.FastHttpDateFormat;
import org.zy.moonStone.core.util.net.NioChannel.ClosedNioChannel;
import org.zy.moonStone.core.util.net.NioEndpoint.Poller;
import org.zy.moonStone.core.util.net.NioEndpoint.SendfileData;
import org.zy.moonStone.core.util.net.interfaces.SSLSupport;

import io.netty.buffer.ByteBuf;

/**
 * @dateTime 2022年1月24日;
 * @author zy(azurite-Y);
 * @description
 */
public class NioSocketWrapper extends SocketWrapperBase<NioChannel> {
	private final NioSelectorPool pool;
	private final SynchronizedStack<NioChannel> nioChannels;
	private final Poller poller;

	private int interestOps = 0;
	/** 线程读锁 */
	private CountDownLatch readLatch = null;
	/** 线程写锁 */
	private CountDownLatch writeLatch = null;
	private volatile SendfileData sendfileData = null;
	private volatile long lastRead = System.currentTimeMillis();
	private volatile long lastWrite = lastRead;

	
	/**
	 * 源自客户端的输缓冲字节入流
	 */
	@Deprecated
	 private BufferedInputStream bis;

	public NioSocketWrapper(NioChannel channel, NioEndpoint endpoint) {
		super(channel, endpoint);
		pool = endpoint.getSelectorPool();
		nioChannels = endpoint.getNioChannels();
		poller = endpoint.getPoller();
		socketBufferHandler = channel.getBufHandler();
	}

	public Poller getPoller() {
		return poller;
	}

	public int interestOps() {
		return interestOps;
	}

	public int interestOps(int ops) {
		this.interestOps = ops;
		return ops;
	}

	public CountDownLatch getReadLatch() {
		return readLatch;
	}

	public CountDownLatch getWriteLatch() {
		return writeLatch;
	}

	protected CountDownLatch resetLatch(CountDownLatch latch) {
		if (latch == null || latch.getCount() == 0) {
			return null;
		} else {
			throw new IllegalStateException("重置的锁需从零开始计数");
		}
	}

	public void resetReadLatch() {
		readLatch = resetLatch(readLatch);
	}

	public void resetWriteLatch() {
		writeLatch = resetLatch(writeLatch);
	}

	protected CountDownLatch startLatch(CountDownLatch latch, int cnt) {
		if (latch == null || latch.getCount() == 0) {
			/*
			 * 导致当前线程等待，直到闩锁倒计时为零，除非线程被中断，或指定的等待时间已过。
			 * 
			 * 如果当前计数为零，则该方法立即返回true。
			 */
			return new CountDownLatch(cnt);
		} else {
			throw new IllegalStateException("重置的锁需从零开始计数");
		}
	}

	public void startReadLatch(int cnt) {
		readLatch = startLatch(readLatch, cnt);
	}

	public void startWriteLatch(int cnt) {
		writeLatch = startLatch(writeLatch, cnt);
	}

	protected void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit) throws InterruptedException {
		if (latch == null) {
			throw new IllegalStateException("等待的锁不能为null");
		}
		// Note: 如果闩锁超时，返回值将被忽略，而调用堆栈上部的逻辑将触发 SocketTimeoutException
		/**
		 * CountDownLatch.await(…): 使当前线程等待直到锁存器倒计时为零，除非线程被中断，或者指定的等待时间已过。
		 *
		 * 如果当前计数为零，则此方法立即返回值 true。
		 *
		 * 如果当前计数大于零，则当前线程出于线程调度目的而被禁用并处于休眠状态，直到发生以下三种情况之一： (1) 由于调用countDown 方法，计数达到零。
		 * (2) 其他一些线程中断当前线程。 (3) 指定的等待时间已过。
		 *
		 * 如果计数达到零，则该方法返回值 true。
		 *
		 * 如果当前线程发送以下三种情况之一，则抛出 InterruptedException 并清除当前线程的中断状态。 (1) 在进入此方法时设置其中断状态
		 * (2) 在等待时被打断
		 *
		 * 如果经过指定的等待时间，则返回 false 值。如果时间小于或等于零，则该方法根本不会等待。 --- CountDownLatch
		 * 在Selector（轮询器）有事件触发处理之后是调用其countDown()方法递减一。
		 *
		 * selectorTimeout：1000
		 */
		latch.await(timeout, unit);
	}

	public void awaitReadLatch(long timeout, TimeUnit unit) throws InterruptedException {
		awaitLatch(readLatch, timeout, unit);
	}

	public void awaitWriteLatch(long timeout, TimeUnit unit) throws InterruptedException {
		awaitLatch(writeLatch, timeout, unit);
	}

	public void setSendfileData(SendfileData sf) {
		this.sendfileData = sf;
	}

	public SendfileData getSendfileData() {
		return this.sendfileData;
	}

	public void updateLastWrite() {
		lastWrite = System.currentTimeMillis();
	}

	public long getLastWrite() {
		return lastWrite;
	}

	public void updateLastRead() {
		lastRead = System.currentTimeMillis();
	}

	public long getLastRead() {
		return lastRead;
	}

	@Override
	protected void doClose() {
		if (logger.isDebugEnabled()) {
			logger.debug("[" + getEndpoint() + "]调用.断开Socket连接([" + this + "])");
		}
		try {
			synchronized (getSocketChannel()) {
				getEndpoint().countDownConnection();
				if (getSocketChannel().isOpen()) {
					getSocketChannel().close(true);
				}
				if (getEndpoint().running && !getEndpoint().paused) {
					if (nioChannels == null || !nioChannels.push(getSocketChannel())) {
						getSocketChannel().free();
					}
				}
			}
		} catch (Throwable e) {
			ExceptionUtils.handleThrowable(e);
			if (logger.isDebugEnabled()) {
				logger.error("通道关闭失败", e);
			}
		} finally {
			socketBufferHandler = SocketBufferHandler.EMPTY;
			nonBlockingWriteBuffer.clear();
			reset(NioChannel.CLOSED_NIO_CHANNEL);
		}
		try {
			SendfileData data = getSendfileData();
			if (data != null && data.fileChannel != null && data.fileChannel.isOpen()) {
				data.fileChannel.close();
			}
		} catch (Throwable e) {
			ExceptionUtils.handleThrowable(e);
			if (logger.isDebugEnabled()) {
				logger.error("文件通道关闭错误", e);
			}
		}
	}

	
	// -------------------------------------------------------------------------------------
	// Read 实现废弃代码
	// -------------------------------------------------------------------------------------
	/**
	 * @return 源自客户端的输缓冲字节入流
	 * @throws IOException
	 */
	public BufferedInputStream getInputStream() throws IOException {
		if (bis == null) {
			NioChannel nioChannel = getSocketChannel();
			if (nioChannel instanceof ClosedNioChannel) {
				throw new ClosedChannelException();
			}
			Socket socket = nioChannel.getSocket();
			bis = new BufferedInputStream(socket.getInputStream());
		}
		return bis;
	}
	
	private int fillReadBuffer(boolean block, ByteBuf to) throws IOException {
		if (block) {
			nonBlockingRead(to);
		} else {
			int len;
			byte[] array = new byte[1024 * 2];
			while (-1 != (len = bis.read(array))) {
				to.writeBytes(array, 0, len);
			}
		}
		return to.writerIndex();
	}

	@Deprecated
	public byte readByte() throws IOException {
		getInputStream();
		return (byte) bis.read();
	}

	@Deprecated
	public int read(boolean block, ByteBuf to) throws IOException {
		int readSize = fillReadBuffer(block, to);

		if (logger.isDebugEnabled()) {
			logger.debug("Socket: [" + this + "], 从套接字读取: [" + readSize + "]");
		}

		return readSize;
	}
	
	@Deprecated
	public int read(byte b[], int off, int len) throws IOException {
		if (bis == null)
			getInputStream();

		return bis.read(b, off, len);
	}

	/**
	 * 非阻塞是读取全部的请求数据。<br/>
	 * 一次读取之后若缓冲字节数组未使用完，则代表输入流中当前已无待读字节
	 * 
	 * @param client
	 * @param to     - 存储数据的缓冲区对象
	 * @throws IOException
	 */
	@Deprecated
	private void nonBlockingRead(ByteBuf to) throws IOException {
		byte[] array = new byte[2048];
		boolean reading = true;
		int len = 0;
		while (reading) {
			len = bis.read(array);

			// 一次读取之后若缓冲字节数组未使用完，则代表输入流中当前已无待读字节
			if (len < array.length) {
				reading = false;
			}
			to.writeBytes(array, 0, len);
		}
	}
	

	@Override
	public void registerReadInterest() {
		getPoller().add(this, SelectionKey.OP_READ);
	}


	@Override
	public void registerWriteInterest() {
		getPoller().add(this, SelectionKey.OP_WRITE);
	}

	@Override
	public SendfileDataBase createSendfileData(String filename, long pos, long length) {
		return new SendfileData(filename, pos, length);
	}

	@Override
	public SendfileState processSendfile(SendfileDataBase sendfileData) {
		setSendfileData((SendfileData) sendfileData);
		SelectionKey key = getSocketChannel().getIOChannel().keyFor(getPoller().getSelector());
		return getPoller().processSendfile(key, this, true);
	}

	@Override
	protected void populateRemoteAddr() {
		SocketChannel sc = getSocketChannel().getIOChannel();
		if (sc != null) {
			InetAddress inetAddr = sc.socket().getInetAddress();
			if (inetAddr != null) {
				remoteAddr = inetAddr.getHostAddress();
			}
		}
	}

	@Override
	protected void populateRemoteHost() {
		SocketChannel sc = getSocketChannel().getIOChannel();
		if (sc != null) {
			InetAddress inetAddr = sc.socket().getInetAddress();
			if (inetAddr != null) {
				remoteHost = inetAddr.getHostName();
				if (remoteAddr == null) {
					remoteAddr = inetAddr.getHostAddress();
				}
			}
		}
	}

	@Override
	protected void populateRemotePort() {
		SocketChannel sc = getSocketChannel().getIOChannel();
		if (sc != null) {
			remotePort = sc.socket().getPort();
		}
	}

	@Override
	protected void populateLocalName() {
		SocketChannel sc = getSocketChannel().getIOChannel();
		if (sc != null) {
			InetAddress inetAddr = sc.socket().getLocalAddress();
			if (inetAddr != null) {
				localName = inetAddr.getHostName();
			}
		}
	}

	@Override
	protected void populateLocalAddr() {
		Socket socket = getSocketChannel().getSocket();
		if (socket != null) {
			InetAddress inetAddr = socket.getLocalAddress();
			if (inetAddr != null) {
				localAddr = inetAddr.getHostAddress();
			}
		}
	}

	@Override
	protected void populateLocalPort() {
		Socket socket = getSocketChannel().getSocket();
		if (socket != null) {
			localPort = socket.getLocalPort();
		}
	}

	/**
	 * @param clientCertProvider - 忽略的实现
	 */
	@Override
	public SSLSupport getSslSupport(String clientCertProvider) {
		// TODO

		// if (getSocketChannel() instanceof SecureNioChannel) {
		// SecureNioChannel ch = (SecureNioChannel) getSocketChannel();
		// SSLEngine sslEngine = ch.getSslEngine();
		// if (sslEngine != null) {
		// SSLSession session = sslEngine.getSession();
		// return ((NioEndpoint)
		// getEndpoint()).getSslImplementation().getSSLSupport(session);
		// }
		// }
		return null;
	}

	@Override
	public void doClientAuth(SSLSupport sslSupport) throws IOException {
		// TODO

		// SecureNioChannel sslChannel = (SecureNioChannel) getSocketChannel();
		// SSLEngine engine = sslChannel.getSslEngine();
		// if (!engine.getNeedClientAuth()) {
		// // Need to re-negotiate SSL connection
		// engine.setNeedClientAuth(true);
		// sslChannel.rehandshake(getEndpoint().getConnectionTimeout());
		// ((JSSESupport) sslSupport).setSession(engine.getSession());
		// }
	}



	@Override
	protected void doWrite(boolean block, ByteBuffer from) throws IOException {
		NioChannel nioChannel = getSocketChannel();
		if (nioChannel instanceof ClosedNioChannel) {
			throw new ClosedChannelException();
		}
		if (block) {
			long writeTimeout = getWriteTimeout();
			Selector selector = null;
			try {
				selector = pool.get();
			} catch (IOException x) {
			} // Ignore
			try {
				pool.write(from, nioChannel, selector, writeTimeout);
				if (block) {
					// 确保被刷新了
					do {
						if (nioChannel.flush(true, selector, writeTimeout)) {
							break;
						}
					} while (true);
				}
			} finally {
				if (selector != null) {
					pool.put(selector);
				}
			}
			/*
			 * 如果缓冲区中还有数据，套接字将被注册，以便在堆栈的上部进行写操作。 这是为了确保套接字只注册写入一次，因为容器和用户代码都可以触发写入注册。
			 */
		} else {
			if (nioChannel.write(from) == -1) {
				throw new EOFException();
			}
		}
		updateLastWrite();
	}

	 @Override
	protected int doRead(boolean block, ByteBuffer from) throws IOException {
		NioChannel nioChannel = getSocketChannel();
		if (nioChannel instanceof ClosedNioChannel) {
			throw new ClosedChannelException();
		}
		int nRead;
		if (block) {
			Selector selector = null;
			try {
				selector = pool.get();
			} catch (IOException x) {
			} // Ignore
			try {
				nRead = pool.read(from, nioChannel, selector, getReadTimeout());
			} finally {
				if (selector != null) {
					pool.put(selector);
				}
			}
			/*
			 * 如果缓冲区中还有数据，套接字将被注册，以便在堆栈的上部进行写操作。 这是为了确保套接字只注册写入一次，因为容器和用户代码都可以触发写入注册。
			 */
		} else {
			if ((nRead = nioChannel.read(from)) == -1) {
				throw new EOFException();
			}
		}
		return nRead;

	}

	@Override
	public int read(boolean block, ByteBuffer to) throws IOException {
		int nRead = populateReadBuffer(to);
		if (nRead > 0) {
			return nRead;
			/*
			 * 由于自上次填充缓冲区后可能已到达更多字节，因此此时可以选择执行非阻塞读取。
			 * 然而，如果读取返回流结尾，则正确处理这种情况会增加复杂性。因此，目前人们倾向于简单。
			 */
		}

		// 套接字读取缓冲区容量为socket.appReadBufSize
		int limit = socketBufferHandler.getReadBuffer().capacity();
		if (to.remaining() >= limit) {
			to.limit(to.position() + limit);
			nRead = doRead(block, to);
			if (nRead > 0 && logger.isDebugEnabled()) {
				logger.debug("直接从 Socket 读取: [" + nRead + "]bit");
			}
			updateLastRead();
		} else {
			// 尽可能填满读取缓冲区
			nRead = doRead(block);
			if (nRead > 0 && logger.isDebugEnabled()) {
				logger.debug("Read data into Socket [" + nRead + "]bit to socketBuffer, Socket: [" + this + "]");
			}
			updateLastRead();

			// 用刚读取的数据尽可能多地填充剩余的字节数组
			if (nRead > 0) {
				nRead = populateReadBuffer(to);
			}
		}
		return nRead;
	}

	@Override
	public int read(boolean block, byte[] b, int off, int len) throws IOException {
		int nRead = populateReadBuffer(b, off, len);
		if (nRead > 0) {
			return nRead;
			/*
			 * 由于自上次填充缓冲区后可能已到达更多字节，因此此时可以选择执行非阻塞读取。
			 * 然而，如果读取返回流结尾，则正确处理这种情况会增加复杂性。因此，目前人们倾向于简单。
			 */
		}

		// 尽可能填满读取缓冲区
		nRead = doRead(block);
		updateLastRead();

		// 用刚读取的数据尽可能多地填充剩余的字节数组
		if (nRead > 0) {
			socketBufferHandler.configureReadBufferForRead();
			nRead = Math.min(nRead, len);
			socketBufferHandler.getReadBuffer().get(b, off, nRead);
		}
		return nRead;
	}

	protected int populateReadBuffer(byte[] b, int off, int len) {
		socketBufferHandler.configureReadBufferForRead();
		ByteBuffer readBuffer = socketBufferHandler.getReadBuffer();
		int remaining = readBuffer.remaining();

		// 读取缓冲区中是否有足够的数据来满足此请求？将读取缓冲区中的数据复制到字节数组
		if (remaining > 0) {
			remaining = Math.min(remaining, len);
			readBuffer.get(b, off, remaining);

			if (remaining > 0 && logger.isDebugEnabled()) {
				logger.debug("Read from socketBuffer: [" + remaining + "]bit");
			}
		}
		return remaining;
	}

	protected int populateReadBuffer(ByteBuffer to) {
		// 读取缓冲区中是否有足够的数据来满足此请求？将读取缓冲区中的数据复制到字节数组
		socketBufferHandler.configureReadBufferForRead();
		int nRead = transfer(socketBufferHandler.getReadBuffer(), to);

		if (logger.isDebugEnabled()) {
			logger.debug("Read from socketBuffer: [" + nRead + "]bit");
		}
		return nRead;
	}

	@Override
	public String toString() {
		return "NioSocketWrapper [interestOps=" + interestOps + ", sendfileData=" + sendfileData + ", lastRead="
				+ FastHttpDateFormat.formatDayTime(lastRead) + ", lastWrite=" + FastHttpDateFormat.formatDayTime(lastWrite) + "]";
	}
}
