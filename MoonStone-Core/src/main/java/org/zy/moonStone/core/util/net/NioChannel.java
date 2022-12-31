package org.zy.moonStone.core.util.net;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description 端点使用的 {@link SocketChannel } 包装器的基类。这样，SSL套接字通道的逻辑与非SSL通道的逻辑保持一致，确保不需要为任何异常情况编写代码。
 */
public class NioChannel implements ByteChannel, ScatteringByteChannel, GatheringByteChannel {
	protected final SocketBufferHandler bufHandler;
	protected SocketChannel socketChannel = null;
	protected Socket socket = null; 
	protected NioSocketWrapper socketWrapper = null;

	public NioChannel(SocketBufferHandler bufHandler) {
		this.bufHandler = bufHandler;
	}

	/**
	 * 重置通道指定属性
	 *
	 * @param channel - 套接字通道
	 * @param socket - socket - 套接字
	 * @param socketWrapper - 套接字包装器
	 * @throws IOException - 如果在重置通道时遇到问题
	 */
	public void reset(SocketChannel channel, Socket socket, NioSocketWrapper socketWrapper) throws IOException {
		this.socketChannel = channel;
		this.socket = socket;
		this.socketWrapper = socketWrapper;
		bufHandler.reset();
	}

	NioSocketWrapper getSocketWrapper() {
		return socketWrapper;
	}

	/**
	 * 释放通道内存
	 */
	public void free() {
		bufHandler.free();
	}

	/**
	 * 如果网络缓冲区已清除且为空，则返回true
	 *
	 * @param block - 未使用的。可在重写时使用
	 * @param s - 未使用的。可在重写时使用
	 * @param timeout - 未使用的。可在重写时使用
	 * @return Always - 始终返回true，因为常规通道中没有网络缓冲区
	 * @throws IOException - 不适用于非安全通道
	 */
	public boolean flush(boolean block, Selector s, long timeout) throws IOException {
		return true;
	}

	/**
	 * 关闭此频道
	 *
	 * @throws IOException - 如果发生I/O错误
	 */
	@Override
	public void close() throws IOException {
		socketChannel.close();
	}

	/**
	 * 关闭连接
	 *
	 * @param force - 是否应强制关闭基础套接字
	 * @throws IOException - 如果关闭安全通道失败
	 */
	public void close(boolean force) throws IOException {
		if (isOpen() || force) {
			close();
		}
	}

	/**
	 * 判断此通道是否打开
	 *
	 * @return 当且仅当此通道打开时为true
	 */
	@Override
	public boolean isOpen() {
		return socketChannel.isOpen();
	}

	/**
	 * 从给定缓冲区将字节序列写入此通道
	 *
	 * @param src - 要从中检索字节的缓冲区
	 * @return 写入的字节数，可能为零
	 * @throws IOException - 如果发生其他I/O错误
	 */
	@Override
	public int write(ByteBuffer src) throws IOException {
		checkInterruptStatus();
		return socketChannel.write(src);
	}

	@Override
	public long write(ByteBuffer[] srcs) throws IOException {
		return write(srcs, 0, srcs.length);
	}

	@Override
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		checkInterruptStatus();
		return socketChannel.write(srcs, offset, length);
	}

	/**
	 * 从该通道将字节序列读入给定的缓冲区
	 *
	 * @param dst - 要传输字节的缓冲区
	 * @return 读取的字节数，可能为零，如果通道已到达流尾，则为 -1
	 * @throws IOException - 如果发生其他一些 I/O 错误
	 */
	@Override
	public int read(ByteBuffer dst) throws IOException  {
		return socketChannel.read(dst);
	}

	@Override
	public long read(ByteBuffer[] dsts) throws IOException {
		return read(dsts, 0, dsts.length);
	}

	@Override
	public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
		return socketChannel.read(dsts, offset, length);
	}

	public SocketBufferHandler getBufHandler() {
		return bufHandler;
	}

	/**
	 * @return 本次连接的 {@link SocketChannel } 实例
	 */
	public SocketChannel getIOChannel() {
		return socketChannel;
	}
	
	/**
	 * @return 本次连接的 {@link Socket } 实例
	 */
	public Socket getSocket() {
		return socket;
	}

	public boolean isClosing() {
		return false;
	}

	public boolean isHandshakeComplete() {
		return true;
	}

	/**
	 * 执行 SSL 握手，因此对于非安全实现来说是无操作的
	 *
	 * @param read - 在非安全实现中未使用
	 * @param write - 在非安全实现中未使用
	 * @return 总是返回零
	 * @throws IOException - 从不用于非安全通道
	 */
	public int handshake(boolean read, boolean write) throws IOException {
		return 0;
	}

	@Override
	public String toString() {
		return super.toString() + ":" + socketChannel.toString();
	}

	/**
	 * 出栈剩余
	 * @return
	 */
	public int getOutboundRemaining() {
		return 0;
	}

	/**
	 * 如果缓冲区写入数据，则返回 true
	 *
	 * @return 对于非安全通道，始终返回 false
	 * @throws IOException - 从不用于非安全通道
	 */
	public boolean flushOutbound() throws IOException {
		return false;
	}

	/**
	 * 此方法应用于在尝试写入之前检查中断状态。
	 * 
	 * 如果线程被中断并且中断没有被清除，那么写入套接字的尝试将失败。 
	 * 发生这种情况时，套接字会从轮询器中移除，而不会选择套接字。 
	 * 这会导致 NIO 的连接限制泄漏，因为端点希望即使在错误条件下也会选择套接字。
	 * 
	 * @throws IOException - 如果当前线程被中断
	 */
	protected void checkInterruptStatus() throws IOException {
		if (Thread.interrupted()) {
			throw new IOException("当前线程被中断，by name：" + Thread.currentThread().getName());
		}
	}

	static final NioChannel CLOSED_NIO_CHANNEL = new ClosedNioChannel();
	public static class ClosedNioChannel extends NioChannel {
		public ClosedNioChannel() {
			super(SocketBufferHandler.EMPTY);
		}
		
		@Override
		public void close() throws IOException {
		}
		
		@Override
		public boolean isOpen() {
			return false;
		}
		
		@Override
		public void reset(SocketChannel channel, Socket socket, NioSocketWrapper socketWrapper) throws IOException {
		}
		
		@Override
		public void free() {
		}
		
		@Override
		public int read(ByteBuffer dst) throws IOException {
			return -1;
		}
		
		@Override
		public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
			return -1L;
		}
		
		@Override
		public int write(ByteBuffer src) throws IOException {
			checkInterruptStatus();
			return -1;
		}
		
		@Override
		public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
			return -1L;
		}
		
		@Override
		public String toString() {
			return "Closed NioChannel";
		}
	}
}
