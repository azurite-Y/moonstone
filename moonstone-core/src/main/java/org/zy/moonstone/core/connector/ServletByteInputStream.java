package org.zy.moonstone.core.connector;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.zy.moonstone.core.http.Request;
import org.zy.moonstone.core.security.SecurityUtil;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.net.ContainerThreadMarker;

import io.netty.buffer.ByteBuf;

/**
 * @dateTime 2022年7月10日;
 * @author zy(azurite-Y);
 * @description 此类为请求正文的延时读取处理字节流
 */
public class ServletByteInputStream extends ServletInputStream {
	/** 请求行数据的延迟提供者 */
	private Supplier<Byte> deferredSupplier = null;
	
	/** 已读取数 */
	private int readCount = 0;
	
	private int limit;
	
	private int contentLength;
	
	/** 标识当前是否已是安全控制环境 */
	private AtomicBoolean privileged = new AtomicBoolean();
	
	private final Request request; 
	
	public ServletByteInputStream(Request request) {
		super();
		this.request = request;
		this.deferredSupplier = request.getRequestBodySupplier();
		this.contentLength = request.getContentLength();
		this.limit = this.contentLength - 1;
	}

	/**
     * 防止克隆 facade
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
	
    /**
     * 已读取流中的所有数据时返回 true，否则返回 false
     * 
     * @return 当此特定请求的所有数据都已读取时为 true，否则返回 false。
     */
	@Override
	public boolean isFinished() {
		return this.readCount == limit;
	}

	/**
	 * 如果可以在不阻塞的情况下读取数据，则返回 true，否则返回 false。
	 * 
	 * @return 如果可以不阻塞地获取数据，则返回 true，否则返回 false。
	 */
	@Override
	public boolean isReady() {
		return true;
	}
	
	/**
	 * 指示 ServletInputStream 在可以读取时调用提供的 ReadListener
	 * @param readListener - 可以读取时应通知的 ReadListener
	 */
	@Override
	public void setReadListener(ReadListener readListener) {
		request.setReadListener(readListener);

		request.action(ActionCode.DISPATCH_READ, null);
		if (!ContainerThreadMarker.isContainerThread()) {
			// 不是在容器线程上，所以需要执行分派
			request.action(ActionCode.DISPATCH_EXECUTE, null);
		}
	}

	/**
	 * 返回可以从此输入流中读取（或跳过）的字节数的估计值，而不会被此输入流的方法的下一次调用阻塞。 下一次调用可能是同一个线程或另一个线程。 
	 * 单次读取或跳过这么多字节不会阻塞，但可能会读取或跳过更少的字节。
	 * <p>
	 * 请注意，虽然 InputStream 的某些实现会返回流中的总字节数，但许多不会。 使用此方法的返回值来分配用于保存此流中所有数据的缓冲区是不正确的。
	 * <p>
	 * 如果此输入流已通过调用 close() 方法关闭，则此方法的子类的实现可以选择抛出 IOException。
	 * <p>
	 * InputStream 类的可用方法总是返回 0。
	 * 
	 * @return 可以在没有阻塞的情况下从此输入流中读取（或跳过）的字节数的估计值，或者当它到达输入流的末尾时为 0
	 */
	@Override
	public int available() throws IOException {
		 if (SecurityUtil.isPackageProtectionEnabled()) {
	            try {
	                Integer result = AccessController.doPrivileged(new PrivilegedAvailable());
	                return result.intValue();
	            } catch (PrivilegedActionException pae) {
	                Exception e = pae.getException();
	                if (e instanceof IOException) {
	                    throw (IOException) e;
	                } else {
	                    throw new RuntimeException(e.getMessage(), e);
	                }
	            }
	        } else {
	            return basicAvailable();
	        }
	}
	
	public int basicAvailable() {
		return this.contentLength - readCount;
	}
	
	/**
	 * 从输入流中读取数据的下一个字节。 值字节作为 0 到 255 范围内的 int 返回。如果由于已到达流的末尾而没有可用的字节，则返回值 -1。 
	 * 此方法会阻塞，直到输入数据可用、检测到流的结尾或引发异常。
	 * 
	 * @return 数据的下一个字节，如果到达流的末尾，则为 -1
	 * @throws IOException - 如果发生 I/O 错误
	 */
	@Override
	public int read() throws IOException {
		if (SecurityUtil.isPackageProtectionEnabled() && !privileged.get()) {
            try {
                Integer result = AccessController.doPrivileged(new PrivilegedRead());
                return result.intValue();
            } catch (PrivilegedActionException pae) {
                Exception e = pae.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        } else {
            return basicRead();
        }
	}
	
	private int basicRead() {
		if (readCount < request.getContentLength()) {
			readCount++;
			return this.deferredSupplier.get();
		} else {
			return -1;
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                Integer result = AccessController.doPrivileged(new PrivilegedReadArray(b, off, len));
                // 设置安全控制标识，使read()方法不必进由安全控制
                privileged.compareAndSet(false, true);
                
                return result.intValue();
            } catch (PrivilegedActionException pae) {
                Exception e = pae.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new RuntimeException(e.getMessage(), e);
                }
            } finally { // 为下一次可能的调用而重置
            	privileged.compareAndSet(true, false);
            }
        } else {
        	// 首先读取一个字节数据判断是否为-1，是则直接返回-1，反之则继续读取之后的数据，直到填充满指定的存储数组或无待读数据结束并返回已读取字节数
            return basicRead(b, off, len);
        }
	}
	
	private int basicRead(byte[] b, int off, int len) throws IOException {
		return super.read(b, off, len);
	}
	
	/**
     * 将字节从缓冲区传输到指定的 ByteBuf
     *
     * @param b - 要写入字节的 ByteBuf
     * @return 一个整数，指定读取的实际字节数，如果到达流的末尾，则为 -1
     * @throws IOException if an input or output exception has occurred
     */
    public int read(final ByteBuf b) throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                Integer result = AccessController.doPrivileged(new PrivilegedReadBuffer(b));
                return result.intValue();
            } catch (PrivilegedActionException pae) {
                Exception e = pae.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        } else {
            return basicRead(b);
        }
    }
	
    private int basicRead(final ByteBuf byteBuf) {
        int n = Math.min(limit - readCount, byteBuf.capacity());
        for (int i = 0; i < n; i++) {
        	byteBuf.writeByte(this.basicRead());
		}
        return n;
    }

    private class PrivilegedAvailable implements PrivilegedExceptionAction<Integer> {
        @Override
        public Integer run() throws IOException {
            return Integer.valueOf(basicAvailable());
        }
    }

    
	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
    private class PrivilegedRead implements PrivilegedExceptionAction<Integer> {
        @Override
        public Integer run() throws IOException {
            Integer integer = Integer.valueOf(basicRead());
            return integer;
        }
    }


    private class PrivilegedReadArray implements PrivilegedExceptionAction<Integer> {
        private final byte[] buf;
        private final int off;
        private final int len;

        public PrivilegedReadArray(byte[] buf, int off, int len) {
            this.buf = buf;
            this.off = off;
            this.len = len;
        }

        @Override
        public Integer run() throws IOException {
            Integer integer = Integer.valueOf(basicRead(buf, off, len));
            return integer;
        }
    }


    private class PrivilegedReadBuffer implements PrivilegedExceptionAction<Integer> {
        private final ByteBuf byteBuf;

        public PrivilegedReadBuffer(ByteBuf byteBuf) {
            this.byteBuf = byteBuf;
        }

        @Override
        public Integer run() throws IOException {
            Integer integer = Integer.valueOf(basicRead(byteBuf));
            return integer;
        }
    }
}
