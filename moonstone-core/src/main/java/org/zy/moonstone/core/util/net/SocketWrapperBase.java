package org.zy.moonstone.core.util.net;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.net.interfaces.SSLSupport;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @dateTime 2022年1月13日;
 * @author zy(azurite-Y);
 * @description
 * @param <E> - 使用的 SocketChannel 包装器类型
 */
public abstract class SocketWrapperBase<E> {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	/** 端点使用的SocketChannel包装器 */
	private E socketChannel;

	/** NIo端点实例 */
	private final AbstractEndpoint<E,?> endpoint;

	protected final AtomicBoolean closed = new AtomicBoolean(false);

	// volatile是因为I/O和设置超时值发生在与检查超时的线程不同的线程上
	private volatile long readTimeout = -1;
	private volatile long writeTimeout = -1;

	private volatile int keepAliveLeft = 100;
	private volatile boolean upgraded = false;
	private boolean secure = false;
	/** 协商协议名称 */
	private String negotiatedProtocol = null;
	/*
	 * 以下缓存以提高速度/减少 GC
	 */
	/** 本地地址 */
	protected String localAddr = null;
	/** 本地主机名 */
	protected String localName = null;
	/** 本地端口 */
	protected int localPort = -1;
	/** 远程地址 */
	protected String remoteAddr = null;
	/** 远程主机名 */
	protected String remoteHost = null;
	/** 远程端口 */
	protected int remotePort = -1;
	/**
	 * 阻塞状态。如果在套接字级别设置了阻塞/非阻塞, 则使用它。 客户端负责通过提供的锁对该字段的线程安全使用。
	 */
//	private volatile boolean blockingStatus = true;
	/**
	 * 用于记录在非阻塞读/写期间发生的第一个 IOException, 由于堆栈中没有用户代码或适当的容器代码来处理它, 
	 * 因此无法有效地向上传播到堆栈。
	 */
	private volatile IOException error = null;

	/**
	 * 用于与套接字通信的缓冲区
	 */
	protected volatile SocketBufferHandler socketBufferHandler = null;

	/**
	 * 各个缓冲写入缓冲区的最大大小, 默认64K
	 */
	protected int bufferedWriteSize = 64 * 1024;

	/**
	 * 用于非阻塞写的额外缓冲区。非阻塞写需要立即返回, 即使数据不能立即写入, 但套接字缓冲区可能不够大, 无法容纳所有未写入的数据。
	 * 这个结构提供了一个额外的缓冲区来保存数据, 直到可以写入数据为止。
	 * 虽然Servlet API一次只允许一个非阻塞的写操作, 但由于缓冲和可能需要写HTTP报头, 这一层可能会看到多个写操作。
	 */
	protected final WriteBuffer nonBlockingWriteBuffer = new WriteBuffer(bufferedWriteSize);

	// 资源读取控制
	protected final Semaphore readPending;
	// 资源写入控制
	protected final Semaphore writePending;

	public SocketWrapperBase(E socket, AbstractEndpoint<E,?> endpoint) {
		this.socketChannel = socket;
		this.endpoint = endpoint;
		if (endpoint.getUseAsyncIO() || needSemaphores()) {
			readPending = new Semaphore(1);
			writePending = new Semaphore(1);
		} else {
			readPending = null;
			writePending = null;
		}
	}

	public E getSocketChannel() {
		return socketChannel;
	}

	protected void reset(E closedSocket) {
		socketChannel = closedSocket;
	}

	protected AbstractEndpoint<E,?> getEndpoint() {
		return endpoint;
	}

	/**
	 * 将处理过程传输到容器线程
	 *
	 * @param runnable - 容器线程上要处理的操作
	 * @throws RejectedExecutionException - 如果可运行文件不能执行
	 */
	public void execute(Runnable runnable) {
		Executor executor = endpoint.getExecutor();
		if (!endpoint.isRunning() || executor == null) {
			throw new RejectedExecutionException();
		}
		executor.execute(runnable);
	}

	public IOException getError() { return error; }
	public void setError(IOException error) {
		// 不是完全线程安全但足够好。 只需要确保一旦 this.error 为非空, 它就永远不能为空.
		if (this.error != null) {
			return;
		}
		this.error = error;
	}
	public void checkError() throws IOException {
		if (error != null) {
			throw error;
		}
	}

	public boolean isUpgraded() { return upgraded; }
	public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
	public boolean isSecure() { return secure; }
	public void setSecure(boolean secure) { this.secure = secure; }
	/** 获得协商协议名称 */
	public String getNegotiatedProtocol() { return negotiatedProtocol; }
	/** 设置协商协议名称 */
	public void setNegotiatedProtocol(String negotiatedProtocol) {
		this.negotiatedProtocol = negotiatedProtocol;
	}

	/**
	 * 设置读取超时。 零或更小的值将更改为-1
	 *
	 * @param readTimeout - 超时时间(毫秒)。值为-1表示无限制
	 */
	public void setReadTimeout(long readTimeout) {
		if (readTimeout > 0) {
			this.readTimeout = readTimeout;
		} else {
			this.readTimeout = -1;
		}
	}

	public long getReadTimeout() {
		return this.readTimeout;
	}

	/**
	 * 设置写入超时时间。0或更小的值将被更改为1
	 *
	 * @param writeTimeout - 超时时间(毫秒)。值为0或更小表示无限超时
	 */
	public void setWriteTimeout(long writeTimeout) {
		if (writeTimeout > 0) {
			this.writeTimeout = writeTimeout;
		} else {
			this.writeTimeout = -1;
		}
	}

	public long getWriteTimeout() {
		return this.writeTimeout;
	}

	public void setKeepAliveLeft(int keepAliveLeft) {
		this.keepAliveLeft = keepAliveLeft;
	}

	public int decrementKeepAlive() {
		return --keepAliveLeft;
	}

	public String getRemoteHost() {
		if (remoteHost == null) {
			populateRemoteHost();
		}
		return remoteHost;
	}
	/**
	 * 填充远程主机名 
	 */
	protected abstract void populateRemoteHost();

	public String getRemoteAddr() {
		if (remoteAddr == null) {
			populateRemoteAddr();
		}
		return remoteAddr;
	}
	/**
	 * 填充
	 */
	protected abstract void populateRemoteAddr();

	public int getRemotePort() {
		if (remotePort == -1) {
			populateRemotePort();
		}
		return remotePort;
	}
	/**
	 * 填充远程端口 
	 */
	protected abstract void populateRemotePort();

	public String getLocalName() {
		if (localName == null) {
			populateLocalName();
		}
		return localName;
	}
	/**
	 * 填充本地主机名 
	 */
	protected abstract void populateLocalName();

	public String getLocalAddr() {
		if (localAddr == null) {
			populateLocalAddr();
		}
		return localAddr;
	}
	/**
	 * 填充本地地址
	 */
	protected abstract void populateLocalAddr();

	public int getLocalPort() {
		if (localPort == -1) {
			populateLocalPort();
		}
		return localPort;
	}
	/**
	 * 填充本地端口
	 */
	protected abstract void populateLocalPort();

	public SocketBufferHandler getSocketBufferHandler() { return socketBufferHandler; }


	@Override
	public String toString() {
		return super.toString() + ":" + String.valueOf(socketChannel);
	}

	
	
	/**
	 * 读取数据到指定缓冲区中
	 * @param block - 是否阻塞读
	 * @param to - 写入的缓冲区对象
	 * @return 已读的字节数
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常
	 */
	@Deprecated
	public abstract int read(boolean block, ByteBuf to) throws IOException;
	
	/**
	 * 读取并返回一个IO流中的字节数据
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常
	 */
	@Deprecated
	public abstract byte readByte() throws IOException;
	
	
	/**
	 * 读取数据到指定的字节数组中
	 * @param b - 存储数据的字节数组
	 * @param off - 开始读取字节索引的偏移量
	 * @param len - 要读取的最大字节数
	 * @return 读取的字节数
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常
	 */
	public abstract int read(boolean block, byte b[], int off, int len) throws IOException;
		
	/**
	 * 读取数据到指定缓冲区中
	 * @param to - 写入的缓冲区对象
	 * @return 已读的字节数
	 * @throws IOException - 如果在底层套接字读取操作期间发生异常
	 */
	public abstract int read(boolean block, ByteBuffer to) throws IOException;
	
	
	/**
	 * 将套接字中的内容读取到缓冲区。 
	 * 对于阻塞读取, 是使用读取超时时间从套接字中读取数据到缓冲区。而不阻塞读则不应用读取超时参数
	 *
	 * @param block - 读是否应该阻塞?
	 * @throws IOException - 如果在读过程中出现超时等I/O错误
	 */
	protected int doRead(boolean block) throws IOException {
		socketBufferHandler.configureReadBufferForWrite();
		return doRead(block, socketBufferHandler.getReadBuffer());
	}

	protected abstract int doRead(boolean block, ByteBuffer from) throws IOException;

	/**
	 * 关闭套接字包装器
	 */
	public void close() {
		if (closed.compareAndSet(false, true)) {
			try {
				getEndpoint().getHandler().release(this);
			} catch (Throwable e) {
				ExceptionUtils.handleThrowable(e);
				if (logger.isDebugEnabled()) {
					logger.error("处理程序释放异常", e);
				}
			}
			doClose();
		}
	}

	/**
	 * 执行实际关闭。 关闭的 AtomicBoolean 保证每个包装器只调用一次
	 */
	protected abstract void doClose();

	/**
	 * @return 如果包装器已关闭, 则为 true
	 */
	public boolean isClosed() {
		return closed.get();
	}


	/**
	 * 将提供的数据写入套接字写缓冲区。
	 * 如果在写过程中, socket写缓冲区被填满, 则socket写缓冲区的内容将被写入网络, 该方法将重新开始填满socket写缓冲区。
	 * 根据要写入的数据的大小, 可能会有多个对网络的写操作
	 * <p>
	 * 非阻塞写操作必须立即返回, 保存要写入数据的字节数组必须立即可用以重用。可能无法向网络写入足够的数据来允许这种情况发生。
	 * 在这种情况下, 不能写入网络和不能由套接字缓冲区保存的数据被存储在非阻塞的写缓冲区中。
	 * <p>
	 * 注意:有一个实现假设, 在从非阻塞写切换到阻塞写之前, 任何保留在非阻塞写缓冲区中的数据将被写入网络。
	 *
	 * @param block - 如果应该使用阻塞写入, 则为True, 否则将使用非阻塞写入
	 * @param buf - 包含要写入的数据的字节数组
	 * @param off - 要写入数据的字节数组内的偏移量
	 * @param len - 要写入数据的长度
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	public final void write(boolean block, byte[] buf, int off, int len) throws IOException {
		if (len == 0 || buf == null) {
			return;
		}
		
		/*
		 * 虽然阻塞和非阻塞写入的实现非常相似, 但它们被分成了不同的方法:
		 * - 允许子类单独覆盖它们。例如, NIO2会覆盖非阻塞写, 但不会覆盖阻塞写。
		 * - 在不需要额外检查非阻塞写缓冲区使用的情况下, 略微提高阻塞写的效率。
		 */
		if (block) {
			writeBlocking(buf, off, len);
		} else {
			writeNonBlocking(buf, off, len);
		}
	}


	/**
	 * 将提供的数据写入套接字写缓冲区。如果在写过程中, socket写缓冲区被填满, 则socket写缓冲区的内容将被写入网络, 该方法将重新开始填满socket写缓冲区。
	 * 根据要写入的数据的大小, 可能会有多个对网络的写操作。
	 * <p>
	 * 非阻塞写入必须立即返回, 保存要写入数据的ByteBuffer必须立即可用以重用。可能无法向网络写入足够的数据来允许这种情况发生。
	 * 在这种情况下, 不能写入网络和不能由套接字缓冲区保存的数据被存储在非阻塞的写缓冲区中。
	 * <p>
	 * 注意:有一个实现假设, 在从非阻塞写切换到阻塞写之前, 任何保留在非阻塞写缓冲区中的数据将被写入网络。
	 *
	 * @param block - 如果应该使用阻塞写入, 则为True, 否则将使用非阻塞写入
	 * @param from - 包含要写入数据的ByteBuffer
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	public final void write(boolean block, ByteBuffer from) throws IOException {
		if (from == null || from.remaining() == 0) {
			return;
		}

		/*
		 * 虽然阻塞和非阻塞写入的实现非常相似, 但它们被分成了不同的方法:
		 * - 允许子类单独覆盖它们。例如, NIO2会覆盖非阻塞写, 但不会覆盖阻塞写。
		 * - 在不需要额外检查非阻塞写缓冲区使用的情况下, 略微提高阻塞写的效率。
		 */
		if (block) {
			writeBlocking(from);
		} else {
			writeNonBlocking(from);
		}
	}


	/**
	 * 将提供的数据写入套接字写缓冲区。如果写过程中socket写缓冲区被填满, 则socket写缓冲区的内容将以阻塞写的方式写入网络。
	 * 一旦阻塞的写操作完成, 这个方法就开始再次填充套接字写缓冲区。根据要写入数据的大小, 可能会有多次写入到网络中。
	 * 在此方法完成后, 套接字写缓冲区中总是会有剩余的空间。
	 *
	 * @param buf - 包含要写入的数据的字节数组
	 * @param off - 要写入数据的字节数组内的偏移量
	 * @param len - 要写入数据的长度
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	protected void writeBlocking(byte[] buf, int off, int len) throws IOException {
		socketBufferHandler.configureWriteBufferForWrite();
		int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
		while (socketBufferHandler.getWriteBuffer().remaining() == 0) {
			len = len - thisTime;
			off = off + thisTime;
			doWrite(true);
			socketBufferHandler.configureWriteBufferForWrite();
			thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
		}
	}


	/**
	 * 将提供的数据写入套接字写缓冲区。如果写过程中socket写缓冲区被填满, 则socket写缓冲区的内容将以阻塞写的方式写入网络。
	 * 一旦阻塞的写操作完成, 这个方法就开始再次填充套接字写缓冲区。根据要写入数据的大小, 可能会有多次写入到网络中。
	 * 在此方法完成后, 套接字写缓冲区中总是会有剩余的空间。
	 *
	 * @param from - 包含要写入数据的ByteBuffer
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	void writeBlocking(ByteBuffer from) throws IOException {
		if (socketBufferHandler.isWriteBufferEmpty()) {
			// 套接字写缓冲区为空。将所提供的缓冲区直接写入网络。
			writeBlockingDirect(from);
		} else {
			// 套接字写入缓冲区包含一些数据
			socketBufferHandler.configureWriteBufferForWrite();
			// 将尽可能多的数据放入写入缓冲区
			transfer(from, socketBufferHandler.getWriteBuffer());
			// 如果缓冲区现在已满, 请将其写入网络, 然后将剩余数据直接写入网络。
			if (!socketBufferHandler.isWriteBufferWritable()) { 
				doWrite(true);
				writeBlockingDirect(from);
			}
		}
	}


	/**
	 * 直接写入网络, 绕过套接字写入缓冲区
	 *
	 * @param from - 包含要写入的数据的字节缓冲区
	 * @throws IOException - 如果在写入过程中发生IO错误
	 */
	protected void writeBlockingDirect(ByteBuffer from) throws IOException {
		/*
		 * 套接字写入缓冲区容量为 socketChannel.appWriteBufSize。
		 * <p>
		 * 这仅在使用 TLS 时很重要。 对于非 TLS 连接, 应该可以在一次写入中写入 ByteBuffer 
		 */
		int limit = socketBufferHandler.getWriteBuffer().capacity();
		int fromLimit = from.limit();
		while (from.remaining() >= limit) {
			from.limit(from.position() + limit);
			doWrite(true, from);
			from.limit(fromLimit);
		}

		if (from.remaining() > 0) {
			socketBufferHandler.configureWriteBufferForWrite();
			transfer(from, socketBufferHandler.getWriteBuffer());
		}
	}


	/**
	 * 将数据传输到套接字写入缓冲区（如果缓冲区使用非阻塞写入填满, 则将该数据写入套接字）, 直到所有数据都已传输且套接字写入缓冲区中仍有空间, 
	 * 或者非阻塞写入将数据保留在套接字写入缓冲区中。写入不完整后, 任何剩余的要传输到套接字写入缓冲区的数据都将复制到套接字写入缓冲区。
	 * 如果剩余数据对于套接字写入缓冲区来说太大, 则将填充套接字写入缓冲区, 并将附加数据写入非阻塞写入缓冲区。
	 *
	 * @param buf - 包含要写入的数据的字节数组
	 * @param off - 要写入数据的字节数组内的偏移量
	 * @param len - 要写入数据的长度
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
		if (nonBlockingWriteBuffer.isEmpty() && socketBufferHandler.isWriteBufferWritable()) {
			socketBufferHandler.configureWriteBufferForWrite();
			int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
			len = len - thisTime;
			while (!socketBufferHandler.isWriteBufferWritable()) {
				off = off + thisTime;
				doWrite(false);
				if (len > 0 && socketBufferHandler.isWriteBufferWritable()) {
					socketBufferHandler.configureWriteBufferForWrite();
					thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
				} else {
					// 在最后一次非阻塞写入中没有写入任何数据。因此写入缓冲区仍然是满的。 这里没有别的事可做。 退出循环。
					break;
				}
				len = len - thisTime;
			}
		}

		if (len > 0) {
			// 剩余数据必须缓冲
			nonBlockingWriteBuffer.add(buf, off, len);
		}
	}


	/**
	 * 将数据传输到套接字写入缓冲区（如果缓冲区使用非阻塞写入填满, 则将该数据写入套接字）直到所有数据都已传输且套接字写入缓冲区中仍有空间, 
	 * 或者非阻塞写入将数据留在 套接字写入缓冲区。在不完整的写入之后, 任何剩余要传输到套接字写入缓冲区的数据都将被复制到套接字写入缓冲区。 
	 * 如果剩余数据对于套接字写入缓冲区来说太大, 则套接字写入缓冲区将被填满, 并将额外的数据写入非阻塞写入缓冲区
	 *
	 * @param from - 包含要写入数据的ByteBuffer
	 * @throws IOException - 如果在写过程中发生IO错误
	 */
	protected void writeNonBlocking(ByteBuffer from) throws IOException {
		if (nonBlockingWriteBuffer.isEmpty() && socketBufferHandler.isWriteBufferWritable()) {
			writeNonBlockingInternal(from);
		}

		if (from.remaining() > 0) {
			// 对剩余数据必须进行缓冲
			nonBlockingWriteBuffer.add(from);
		}
	}


	/**
	 * 单独的方法, 以便套接字写入缓冲区可以重新使用它将数据写入网络
	 *
	 * @param from - 包含要写入的数据的字节缓冲区
	 * @throws IOException - 如果在写入过程中发生IO错误
	 */
	void writeNonBlockingInternal(ByteBuffer from) throws IOException {
		if (socketBufferHandler.isWriteBufferEmpty()) {
			writeNonBlockingDirect(from);
		} else {
			socketBufferHandler.configureWriteBufferForWrite();
			transfer(from, socketBufferHandler.getWriteBuffer());
			if (!socketBufferHandler.isWriteBufferWritable()) {
				doWrite(false);
				if (socketBufferHandler.isWriteBufferWritable()) {
					writeNonBlockingDirect(from);
				}
			}
		}
	}


	protected void writeNonBlockingDirect(ByteBuffer from) throws IOException {
		/*
		 * 套接字写入缓冲区容量为 socketChannel.appWriteBufSize。
		 * NOTE: 这仅在使用 TLS 时很重要。 对于非 TLS 连接, 应该可以在一次写入中写入 ByteBuffer。
		 */
		int limit = socketBufferHandler.getWriteBuffer().capacity();
		int fromLimit = from.limit();
		while (from.remaining() >= limit) {
			int newLimit = from.position() + limit;
			from.limit(newLimit);
			doWrite(false, from);
			from.limit(fromLimit);
			if (from.position() != newLimit) {
				// 在最后一次非阻塞写入中没有写入全部数据。退出循环。
				return;
			}
		}

		if (from.remaining() > 0) {
			socketBufferHandler.configureWriteBufferForWrite();
			transfer(from, socketBufferHandler.getWriteBuffer());
		}
	}


	/**
	 * 从缓冲区中剩余的任何数据中写入尽可能多的数据
	 *
	 * @param block - 如果应该使用阻塞写入, 则为 true, 否则将使用非阻塞写入
	 * @return 如果在此方法完成后数据仍有待刷新, 则为 true, 否则为 false。 因此, 在阻塞模式下, 返回值应始终为 false
	 * @throws IOException - 如果在写入过程中发生IO错误
	 */
	public boolean flush(boolean block) throws IOException {
		boolean result = false;
		if (block) {
			// 阻塞刷新将始终清空缓冲区
			flushBlocking();
		} else {
			result = flushNonBlocking();
		}

		return result;
	}


	protected void flushBlocking() throws IOException {
		doWrite(true);

		if (!nonBlockingWriteBuffer.isEmpty()) {
			nonBlockingWriteBuffer.write(this, true);

			if (!socketBufferHandler.isWriteBufferEmpty()) {
				doWrite(true);
			}
		}
	}


	protected boolean flushNonBlocking() throws IOException {
		boolean dataLeft = !socketBufferHandler.isWriteBufferEmpty();

		if (dataLeft) {
			doWrite(false);
			dataLeft = !socketBufferHandler.isWriteBufferEmpty();
		}

		if (!dataLeft && !nonBlockingWriteBuffer.isEmpty()) {
			dataLeft = nonBlockingWriteBuffer.write(this, false);

			if (!dataLeft && !socketBufferHandler.isWriteBufferEmpty()) {
				doWrite(false);
				dataLeft = !socketBufferHandler.isWriteBufferEmpty();
			}
		}
		return dataLeft;
	}


	public void processSocket(SocketEvent socketStatus, boolean dispatch) {
		endpoint.processSocket(this, socketStatus, dispatch);
	}

	/**
	 * 将套接字写入缓冲区的内容写入套接字。 
	 * 对于阻塞写入, 要么写入缓冲区的全部内容, 要么抛出一个 IOException。 
	 * 不会发生部分阻塞写入。
	 *
	 * @param block - 写是否应该阻塞?
	 * @throws IOException - 如果在写过程中出现超时等I/O错误
	 */
	protected void doWrite(boolean block) throws IOException {
		socketBufferHandler.configureWriteBufferForRead();
		doWrite(block, socketBufferHandler.getWriteBuffer());
	}

	/**
	 * 将 ByteBuffer 的内容写入套接字。 
	 * 对于阻塞写入, 要么写入缓冲区的全部内容, 要么抛出 IOException。 不会发生部分阻塞写入。
	 *
	 * @param block - 写是否应该阻塞?
	 * @throws IOException - 如果在写过程中出现超时等I/O错误
	 * @param from - 包含要写入的数据的 ByteBuffer
	 * @throws IOException - 如果在写过程中出现超时等I/O错误
	 */
	protected abstract void doWrite(boolean block, ByteBuffer from) throws IOException;

	public abstract void registerReadInterest();

	public abstract void registerWriteInterest();

	public abstract SendfileDataBase createSendfileData(String filename, long pos, long length);

	/**
	 * 启动sendfile进程。如果sendfile进程在此调用期间未完成且未报告错误, 则调用程序将不会向轮询器（或等效程序）添加套接字。
	 * 这就是这个方法的职责
	 *
	 * @param sendfileData - 表示要发送的文件的数据
	 * @return 第一次写入后sendfile进程的状态
	 */
	public abstract SendfileState processSendfile(SendfileDataBase sendfileData);

	/**
	 * 要求客户端执行客户端证书身份验证（如果尚未准备好）
	 *
	 * @param sslSupport - 客户端身份验证后可能需要更新的连接当前使用的 SSL/TLS 支持实例
	 * @throws IOException - 如果需要身份验证, 那么客户端将有I/O, 如果出现错误, 将抛出此异常
	 */
	public abstract void doClientAuth(SSLSupport sslSupport) throws IOException;

	public abstract SSLSupport getSslSupport(String clientCertProvider);


	// ------------------------------------------------------ NIO2 风格的 API ------------------------------------------------------
	/** 阻塞模型 */
	public enum BlockingMode {
		/**
		 * 操作不会阻塞。如果存在挂起操作, 该操作将抛出挂起异常
		 */
		CLASSIC,
		/**
		 * 操作不会阻塞。如果有挂起的操作, 该操作将返回CompletionState.NOT_DONE.
		 */
		NON_BLOCK,
		/**
		 * 该操作将阻塞直到挂起的操作完成, 但不会在执行后阻塞。
		 */
		SEMI_BLOCK,
		/**
		 * 该操作将阻塞直到完成。
		 */
		BLOCK
	}

	/** 完成状态 */
	public enum CompletionState {
		/**
		 * 操作仍未完成
		 */
		PENDING,
		/**
		 * 操作挂起且非阻塞
		 */
		NOT_DONE,
		/**
		 * 操作以内联方式完成
		 */
		INLINE,
		/**
		 * 操作内联完成但失败
		 */
		ERROR,
		/**
		 * 操作完成, 但未内联
		 */
		DONE
	}

	public enum CompletionHandlerCall {
		/**
		 * 操作应该继续, 完成处理程序不应该被调用
		 */
		CONTINUE,
		/**
		 * 操作已经完成, 但是不应该调用完成处理程序
		 */
		NONE,
		/**
		 * 操作完成后, 应该调用完成处理程序
		 */
		DONE
	}

	public interface CompletionCheck {
		/**
		 * 确定对completionhandler进行什么调用(如果有的话)
		 *
		 * @param state - 操作的类型(完成或自io调用完成后内联完成)
		 * @param buffers - ByteBuffer[]已经传递给原始IO调用
		 * @param offset - 已经传递给原始IO调用的
		 * @param length - 已经传递给原始IO调用的
		 * @return 对完成处理程序的调用(如果有的话)
		 */
		public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length);
	}

	/**
	 * 这个工具CompletionCheck将导致写操作完全写入所有剩余的数据。如果操作内联完成, 则不会调用完成处理程序。
	 */
	public static final CompletionCheck COMPLETE_WRITE = new CompletionCheck() {
		@Override
		public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
				int offset, int length) {
			for (int i = 0; i < length; i++) {
				if (buffers[offset + i].hasRemaining()) {
					return CompletionHandlerCall.CONTINUE;
				}
			}
			return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE : CompletionHandlerCall.NONE;
		}
	};

	/**
	 * 这个工具CompletionCheck将导致写操作完全写入所有剩余的数据。然后将调用完成处理程序。
	 */
	public static final CompletionCheck COMPLETE_WRITE_WITH_COMPLETION = new CompletionCheck() {
		@Override
		public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
				int offset, int length) {
			for (int i = 0; i < length; i++) {
				if (buffers[offset + i].hasRemaining()) {
					return CompletionHandlerCall.CONTINUE;
				}
			}
			return CompletionHandlerCall.DONE;
		}
	};

	/**
	 * 这个工具CompletionCheck将导致在读取一些数据时调用完成处理程序。如果operationcompleted内联, 则不会调用完成处理程序。
	 */
	public static final CompletionCheck READ_DATA = new CompletionCheck() {
		@Override
		public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length) {
			return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE : CompletionHandlerCall.NONE;
		}
	};

	/**
	 * 这个工具CompletionCheck将导致在给定缓冲区满时调用完成处理程序。然后completionhandler将被调用。
	 */
	public static final CompletionCheck COMPLETE_READ_WITH_COMPLETION = COMPLETE_WRITE_WITH_COMPLETION;

	/**
	 * 这个工具CompletionCheck将导致在给定缓冲区满时调用完成处理程序。如果operationcompleted内联, 则不会调用完成处理程序。
	 */
	public static final CompletionCheck COMPLETE_READ = COMPLETE_WRITE;

	/**
	 * 内部状态跟踪矢量操作
	 */
	protected abstract class OperationState<A> implements Runnable {
		protected final boolean read;
		protected final ByteBuffer[] buffers;
		protected final int offset;
		protected final int length;
		protected final A attachment;
		protected final long timeout;
		protected final TimeUnit unit;
		protected final BlockingMode block;
		protected final CompletionCheck check;
		protected final CompletionHandler<Long, ? super A> handler;
		protected final Semaphore semaphore;
		protected final VectoredIOCompletionHandler<A> completion;
		
		protected OperationState(boolean read, ByteBuffer[] buffers, int offset, int length, BlockingMode block, long timeout, TimeUnit unit, A attachment,
				CompletionCheck check, CompletionHandler<Long, ? super A> handler, Semaphore semaphore, VectoredIOCompletionHandler<A> completion) {
			this.read = read;
			this.buffers = buffers;
			this.offset = offset;
			this.length = length;
			this.block = block;
			this.timeout = timeout;
			this.unit = unit;
			this.attachment = attachment;
			this.check = check;
			this.handler = handler;
			this.semaphore = semaphore;
			this.completion = completion;
		}
		protected volatile long nBytes = 0;
		protected volatile CompletionState state = CompletionState.PENDING;
		protected boolean completionDone = true;

		/**
		 * @return 如果操作仍然内联, 则为True, 如果操作在非原始调用方的线程上运行, 则为false
		 */
		protected abstract boolean isInline();

		/**
		 * 使用连接器执行程序处理该操作
		 * @return 如果操作被接受, 则为true, 如果executor拒绝执行, 则为false
		 */
		protected boolean process() {
			try {
				getEndpoint().getExecutor().execute(this);
				return true;
			} catch (RejectedExecutionException ree) {
				logger.warn("执行器异常, by SocketWrapper: " + SocketWrapperBase.this , ree);
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				// 这意味着我们在创建线程时遇到了OOM或类似情况, 或者池及其队列已满
				logger.error("断点处理失败", t);
			}
			return false;
		}

		/**
		 * 启动该操作, 这通常会调用run
		 */
		protected void start() {
			run();
		}

		/**
		 * 结束操作
		 */
		protected void end() {}

	}

	/**
	 * 矢量操作的完成处理程序。这将检查操作的完成情况, 然后继续或调用用户提供的完成处理程序
	 */
	protected class VectoredIOCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
		@Override
		public void completed(Long nBytes, OperationState<A> state) {
			if (nBytes.longValue() < 0) {
				failed(new EOFException(), state);
			} else {
				state.nBytes += nBytes.longValue();
				CompletionState currentState = state.isInline() ? CompletionState.INLINE : CompletionState.DONE;
				boolean complete = true;
				boolean completion = true;
				if (state.check != null) {
					CompletionHandlerCall call = state.check.callHandler(currentState, state.buffers, state.offset, state.length);
					if (call == CompletionHandlerCall.CONTINUE) {
						complete = false;
					} else if (call == CompletionHandlerCall.NONE) {
						completion = false;
					}
				}
				if (complete) {
					boolean notify = false;
					state.semaphore.release();
					if (state.block == BlockingMode.BLOCK && currentState != CompletionState.INLINE) {
						notify = true;
					} else {
						state.state = currentState;
					}
					state.end();
					if (completion && state.handler != null) {
						state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
					}
					synchronized (state) {
						state.completionDone = true;
						if (notify) {
							state.state = currentState;
							state.notify();
						}
					}
				} else {
					synchronized (state) {
						state.completionDone = true;
					}
					state.run();
				}
			}
		}
		@Override
		public void failed(Throwable exc, OperationState<A> state) {
			IOException ioe = null;
			if (exc instanceof InterruptedByTimeoutException) {
				ioe = new SocketTimeoutException();
				exc = ioe;
			} else if (exc instanceof IOException) {
				ioe = (IOException) exc;
			}
			setError(ioe);
			boolean notify = false;
			state.semaphore.release();
			if (state.block == BlockingMode.BLOCK) {
				notify = true;
			} else {
				state.state = state.isInline() ? CompletionState.ERROR : CompletionState.DONE;
			}
			state.end();
			if (state.handler != null) {
				state.handler.failed(exc, state.attachment);
			}
			synchronized (state) {
				state.completionDone = true;
				if (notify) {
					state.state = state.isInline() ? CompletionState.ERROR : CompletionState.DONE;
					state.notify();
				}
			}
		}
	}

	/**
	 * 允许使用NIO2风格的读/写
	 *
	 * @return 如果连接器已启用该功能, 则为true
	 */
	public boolean hasAsyncIO() {
		// 仅当启用异步IO时, 才会创建信号量
		return (readPending != null);
	}

	/**
	 * 允许指示连接器是否需要信号量
	 *
	 * @return 这个默认实现总是返回false
	 */
	public boolean needSemaphores() {
		return false;
	}

	/**
	 * 允许指示连接器是否支持每个操作超时
	 *
	 * @return 这个默认实现总是返回false
	 */
	public boolean hasPerOperationTimeout() {
		return false;
	}

	/**
	 * 允许检查异步读操作当前是否挂起
	 * @return 如果端点支持异步IO, 而读操作正在异步处理, 则为true
	 */
	public boolean isReadPending() {
		return false;
	}

	/**
	 * 允许检查异步写操作当前是否挂起
	 * @return 如果端点支持异步IO, 并且写入操作正在异步处理, 则为true
	 */
	public boolean isWritePending() {
		return false;
	}

	/**
	 * 散读。 一旦读取了一些数据或发生错误, 将调用完成处理程序。 
	 * 使用默认的 NIO2 行为: 一旦读取了一些数据, 就会调用完成处理程序, 即使读取已经内联完成。
	 *
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param handler - 在 IO 完成时调用
	 * @param dsts - 缓冲区
	 * @param <A> - 附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState read(long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Long, ? super A> handler, ByteBuffer... dsts) {
		if (dsts == null) {
			throw new IllegalArgumentException();
		}
		return read(dsts, 0, dsts.length, BlockingMode.CLASSIC, timeout, unit, attachment, null, handler);
	}

	/**
	 * 散读。 一旦读取了一些数据或发生错误, 将调用完成处理程序。 
	 * 如果提供了 CompletionCheck 对象, 则仅当 callHandler 方法返回 true 时才会调用完成处理程序。 
	 * 如果提供了 noCompletionCheck 对象, 则使用默认的 NIO2 行为: 一旦读取了一些数据, 就会调用完成处理程序, 即使读取已经内联完成。
	 *
	 * @param block - 是将用于此操作的阻塞模式
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param check - 用于 IO 操作完成
	 * @param handler - 在 IO 完成时调用
	 * @param dsts - 缓冲区
	 * @param <A> - 附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState read(BlockingMode block, long timeout,
			TimeUnit unit, A attachment, CompletionCheck check,
			CompletionHandler<Long, ? super A> handler, ByteBuffer... dsts) {
		if (dsts == null) {
			throw new IllegalArgumentException();
		}
		return read(dsts, 0, dsts.length, block, timeout, unit, attachment, check, handler);
	}

	/**
	 * 散读。 一旦读取了一些数据或发生错误, 将调用完成处理程序。
	 * 如果提供了 CompletionCheck 对象, 则仅当 callHandler 方法返回 true 时才会调用完成处理程序。 
	 * 如果提供了 noCompletionCheck 对象, 则使用默认的 NIO2 行为: 一旦读取了一些数据, 就会调用完成处理程序, 即使读取已经内联完成。
	 *
	 * @param dsts - 缓冲区
	 * @param offset - 缓冲区数组下标偏移量
	 * @param length - 缓冲区数组长度
	 * @param block - 是将用于此操作的阻塞模式
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param check - 用于 IO 操作完成
	 * @param handler - 在 IO 完成时调用
	 * @param <A> - 附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
			BlockingMode block, long timeout, TimeUnit unit, A attachment,
			CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
		return vectoredOperation(true, dsts, offset, length, block, timeout, unit, attachment, check, handler);
	}

	/**
	 * 聚写。 一旦写入一些数据或发生错误, 将调用完成处理程序。 
	 * 使用默认的 NIO2 行为: 完成处理程序将被调用, 即使写入不完整并且数据保留在缓冲区中, 或者如果写入内联完成。
	 *
	 * @param timeout - 写入超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param handler - 在 IO 完成时调用
	 * @param srcs 缓冲区
	 * @param <A> - 附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState write(long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Long, ? super A> handler, ByteBuffer... srcs) {
		if (srcs == null) {
			throw new IllegalArgumentException();
		}
		return write(srcs, 0, srcs.length, BlockingMode.CLASSIC, timeout, unit, attachment, null, handler);
	}

	/**
	 * 聚写。 一旦写入一些数据或发生错误, 将调用完成处理程序。 
	 * 如果提供了 CompletionCheck 对象, 则仅当 callHandler 方法返回 true 时才会调用完成处理程序。 
	 * 如果没有提供 CompletionCheck 对象, 则使用默认的 NIO2 行为: 完成处理程序将被调用, 即使写入不完整并且数据保留在缓冲区中, 或者如果写入内联完成。
	 *
	 * @param block - 是将用于此操作的阻塞模式
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param check - 用于 IO 操作完成
	 * @param handler - 在 IO 完成时调用
	 * @param srcs - 缓冲区
	 * @param <A>  附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState write(BlockingMode block, long timeout,
			TimeUnit unit, A attachment, CompletionCheck check,
			CompletionHandler<Long, ? super A> handler, ByteBuffer... srcs) {
		if (srcs == null) {
			throw new IllegalArgumentException();
		}
		return write(srcs, 0, srcs.length, block, timeout, unit, attachment, check, handler);
	}

	/**
	 * 聚写。 一旦写入一些数据或发生错误, 将调用完成处理程序。 
	 * 如果提供了 CompletionCheck 对象, 则仅当 callHandler 方法返回 true 时才会调用完成处理程序。 
	 * 如果没有提供 CompletionCheck 对象, 则使用默认的 NIO2 行为: 完成处理程序将被调用, 即使写入不完整并且数据保留在缓冲区中, 或者如果写入内联完成。
	 *
	 * @param srcs - 缓冲区
	 * @param offset - 缓冲区数组下标偏移量
	 * @param length - 缓冲区数组长度
	 * @param block - 是将用于此操作的阻塞模式
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param check - 用于 IO 操作完成
	 * @param handler - 在 IO 完成时调用
	 * @param <A>  附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	public final <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
			BlockingMode block, long timeout, TimeUnit unit, A attachment,
			CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
		return vectoredOperation(false, srcs, offset, length, block, timeout, unit, attachment, check, handler);
	}


	/**
	 * 矢量操作。一旦操作完成或发生错误, 完成处理程序将被调用。
	 * 如果提供了CompletionCheck对象, 完成处理程序只会在callHandler方法返回true时被调用。
	 * 如果没有提供CompletionCheck对象, 则使用默认的NIO2behavior:完成处理程序将被调用, 即使操作是不完整的, 或者操作是内联完成的。
	 *
	 * @param read - 如果操作是读操作, 则为true；如果操作是写操作, 则为false
	 * @param buffers - 缓冲区
	 * @param offset - 缓冲区数组下标偏移量
	 * @param length - 缓冲区数组长度
	 * @param block - 是将用于此操作的阻塞模式
	 * @param timeout - 读取的超时时间
	 * @param unit - 超时持续时间的单位
	 * @param attachment - 一个要附加到 I/O 操作的对象, 该对象将在调用完成处理程序时使用
	 * @param check - 用于 IO 操作完成
	 * @param handler - 在 IO 完成时调用
	 * @param <A> - 附件类型
	 * @return 完成状态（完成、内联完成或仍待处理）
	 */
	protected final <A> CompletionState vectoredOperation(boolean read, ByteBuffer[] buffers, int offset, int length,
			BlockingMode block, long timeout, TimeUnit unit, A attachment, CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
		/*
		IOException ioe = getError();
		 if (ioe != null) {

			handler.failed(ioe, attachment);
			return CompletionState.ERROR;
		}
		if (timeout == -1) {
			timeout = AbstractEndpoint.toTimeout(read ? getReadTimeout() : getWriteTimeout());
			unit = TimeUnit.MILLISECONDS;
		} else if (!hasPerOperationTimeout() && (unit.toMillis(timeout) != (read ? getReadTimeout() : getWriteTimeout()))) {
			if (read) {
				setReadTimeout(unit.toMillis(timeout));
			} else {
				setWriteTimeout(unit.toMillis(timeout));
			}
		}
		if (block == BlockingMode.BLOCK || block == BlockingMode.SEMI_BLOCK) {
			try {
				if (read ? !readPending.tryAcquire(timeout, unit) : !writePending.tryAcquire(timeout, unit)) {
					handler.failed(new SocketTimeoutException(), attachment);
					return CompletionState.ERROR;
				}
			} catch (InterruptedException e) {
				handler.failed(e, attachment);
				return CompletionState.ERROR;
			}
		} else {
			if (read ? !readPending.tryAcquire() : !writePending.tryAcquire()) {
				if (block == BlockingMode.NON_BLOCK) {
					return CompletionState.NOT_DONE;
				} else {
					handler.failed(read ? new ReadPendingException() : new WritePendingException(), attachment);
					return CompletionState.ERROR;
				}
			}
		}
		VectoredIOCompletionHandler<A> completion = new VectoredIOCompletionHandler<>();
		OperationState<A> state = new OperationState(read, buffers, offset, length, block, timeout, unit,
				attachment, check, handler, read ? readPending : writePending, completion);
		state.start();
		if (block == BlockingMode.BLOCK) {
			synchronized (state) {
				if (state.state == CompletionState.PENDING) {
					try {
						state.wait(unit.toMillis(timeout));
						if (state.state == CompletionState.PENDING) {
							return CompletionState.ERROR;
						}
					} catch (InterruptedException e) {
						completion.failed(new SocketTimeoutException(), state);
						return CompletionState.ERROR;
					}
				}
			}
		}
				 */
		return null;
	}

//	protected abstract <A> OperationState<A> newOperationState(boolean read,
//			ByteBuffer[] buffers, int offset, int length,
//			BlockingMode block, long timeout, TimeUnit unit, A attachment,
//			CompletionCheck check, CompletionHandler<Long, ? super A> handler,
//			Semaphore semaphore, VectoredIOCompletionHandler<A> completion);

	// --------------------------------------------------------- 工具方法 ---------------------------------------------------------

	protected static int transfer(byte[] from, int offset, int length, ByteBuffer to) {
		int max = Math.min(length, to.remaining());
		if (max > 0) {
			to.put(from, offset, max);
		}
		return max;
	}

	protected static int transfer(ByteBuffer from, ByteBuffer to) {
		int max = Math.min(from.remaining(), to.remaining());
		if (max > 0) {
			int fromLimit = from.limit();
			from.limit(from.position() + max);
			to.put(from);
			from.limit(fromLimit);
		}
		return max;
	}

}
