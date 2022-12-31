package org.zy.moonStone.core.connector;

import java.io.IOException;
import java.io.Writer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.exceptions.ClientAbortException;
import org.zy.moonStone.core.exceptions.CloseNowException;
import org.zy.moonStone.core.http.Response;
import org.zy.moonStone.core.util.http.ActionCode;

/**
 * @dateTime 2022年7月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class OutputBuffer extends Writer {
	/** 默认缓冲区大小 */
	public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
	
    private final int defaultBufferSize;

    /** 字节缓冲区  */
    private ByteBuffer byteBuffer;

    /** 字符缓冲区  */
    private CharBuffer charBuffer;
    
    /** 输出缓冲区的状态，初始态 */
    private boolean initial = true;

    /** 写入的字节数  */
    private long bytesWritten = 0;

    /** 写入的字符数 */
    private long charsWritten = 0;

    /** 指示输出缓冲区是否关闭的标志 */
    private volatile boolean closed = false;

    /** 在下一个操作中执行刷新 */
    private boolean doFlush = false;

    /** 原初响应对象 */
    private Response response;

    /** 挂起的标志 */
    private volatile boolean suspended = false;
    
    /**
     * 是否直接使用字节缓冲区。若为true则写入的数据将按字节存储
     */
    private boolean useByteBuffer;
    
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 创建具有指定初始大小的缓冲区
     *
     * @param size - 输出缓冲区尺寸
     * @param useByteBuffer - 是否直接使用字节缓冲区。若为true则写入的数据将按字节存储
     */
    public OutputBuffer(int size) {
        this.defaultBufferSize = size;
        
        byteBuffer = ByteBuffer.allocate(size);
        clear(byteBuffer);
        
        charBuffer = CharBuffer.allocate(size);
        clear(charBuffer);
    }
    
    
	// -------------------------------------------------------------------------------------
	// getter、setter
	// -------------------------------------------------------------------------------------
    /**
     * 设置关联的原初响应对象
     *
     * @param response - 关联的原初响应对象
     */
    public void setResponse(Response response) {
        this.response = response;
    }

    public void setWriteListener(WriteListener listener) {
        response.setWriteListener(listener);
    }
    
	/**
	 * 是否直接使用字节缓冲区
	 * 
	 * @return true则写入的数据将按字节存储
	 */
	public boolean isUseByteBuffer() {
		return useByteBuffer;
	}

	public void setUseByteBuffer(boolean useByteBuffer) {
		this.useByteBuffer = useByteBuffer;
	}


	/**
     * 响应输出是否暂停？
     *
     */
    public boolean isSuspended() {
        return this.suspended;
    }

    /**
     * 设置暂停标志
     *
     * @param suspended - 新的暂停标志
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    /**
     * 响应输出是否已关闭
     */
    public boolean isClosed() {
        return this.closed;
    }
    
    public int getBufferSize() {
        return defaultBufferSize;
    }
    public void setBufferSize(int size) {
        if (size > byteBuffer.capacity()) {
            byteBuffer = ByteBuffer.allocate(size);
            clear(byteBuffer);
        }
    }
    
    /**
     * @return 写入的字节数 
     */
	public long getBytesWritten() {
		return bytesWritten;
	}

	/**
	 * @return 写入的字符数 
	 */
	public long getCharsWritten() {
		return charsWritten;
	}


	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    /**
     * 回收输出缓冲区
     */
    public void recycle() {
        initial = true;
        bytesWritten = 0;
        charsWritten = 0;

        if (byteBuffer.capacity() > 16 * defaultBufferSize) {
            // 丢弃太大的缓冲区
            byteBuffer = ByteBuffer.allocate(defaultBufferSize);
        }
        clear(byteBuffer);
        
        clear(charBuffer);
        
        closed = false;
        suspended = false;
        doFlush = false;
    }
    
    public void reset() {
    	clear(byteBuffer);
        clear(charBuffer);

        bytesWritten = 0;
        charsWritten = 0;
        initial = true;
    }
    
//    public boolean isBlocking() {
//        return response.getWriteListener() == null;
//    }
//    
//	public boolean isReady() {
//		return response.isReady();
//	}
//	
//    public void checkRegisterForWrite() {
//        response.checkRegisterForWrite();
//    }
    
    public long getContentWritten() {
        return bytesWritten;
    }
    
    /**
     * 是否使用了这个缓冲区?
     *
     * @return 如果自上次调用 {@link #recycle()} 以来没有向缓冲区添加字符或字节，则为 true if no chars or bytes have been added to the httpOutputBuffer since the
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }
	// -------------------------------------------------------------------------------------
	// 实现方法
	// -------------------------------------------------------------------------------------
    /**
     * 将数据写入缓冲区，可以是单个字节或者字符，但这取决于缓冲区创建时指定的 {@link #useByteBuffer} 属性。
     * <ul>
     * <li><b>useByteBuffer：true</b> - <br/>将指定字节写入此输出流。 写入的一般约定是将一个字节写入输出流。 要写入的字节是参数 b 的低八位。 b 的高 24 位被忽略。</li>
     * <li><b>useByteBuffer：false</b> - <br/>写入单个字符。 要写入的字符包含在给定整数值的低 16 位中； 16 个高位被忽略。</li>
     * </ul>
     * 
     * @param data - 写入缓冲区数据
     * @throws IOException - 如果发生 I/O 错误
     */
    @Override
    public void write(int data) throws IOException {
    	if (suspended) {
            return;
        }

        if (isFull(byteBuffer)) {
            flushByteBuffer();
        }
        
        if (this.useByteBuffer) {
        	   if (isFull(byteBuffer)) {
                   flushByteBuffer();
               }
        	
        	transfer((byte)data, byteBuffer);
        	this.bytesWritten++;
        } else {
        	   if (isFull(charBuffer)) {
                   flushCharBuffer();
               }
        	
        	transfer((char)data, charBuffer);
            charsWritten++;
        }
    }

    /**
     * 将指定字节数组中的 b.length 个字节写入此输出流。 write(b) 的一般约定是它应该与调用 write(b, 0, b.length) 具有完全相同的效果
     * 
     */
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }
    
    /**
     * 将指定字节数组中的 len 个字节从偏移量 off 处开始写入此输出流。 
     * write(b, off, len) 的一般约定是数组 b 中的某些字节按顺序写入输出流； 元素 b[off] 是写入的第一个字节， b[off+len-1] 是此操作写入的最后一个字节。
     * 
     * @param b - 源数据
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @throws IOException - 如果发生 I/O 错误。 特别是，如果输出流关闭，则会引发 IOException。
     */
    public void write(byte b[], int off, int len) throws IOException {
        if (suspended) {
            return;
        }
    	
    	if (closed) {
            return;
        }

        append(b, off, len);
        bytesWritten += len;

        // 如果从 flush() 中调用，则立即刷新剩余字节
        if (doFlush) {
            flushByteBuffer();
        }
    }
    
	/**
	 * 写入一个字符数组
	 * 
	 * @param cbuf - 要写入的字符数组
     * @throws IOException - 如果发生 I/O 错误
	 */
	@Override
	public void write(char[] cbuf) throws IOException {
        write(cbuf, 0, cbuf.length);
	}
    
    /**
     * 写入字符数组的一部分
     * 
     * @param b - 源数据
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @throws IOException - 如果发生 I/O 错误
     */
	@Override
	public void write(char c[], int off, int len) throws IOException {
        if (suspended) {
            return;
        }

        append(c, off, len);
        charsWritten += len;
	}
	
	/**
	 * 写入一个字符串
	 * 
	 * @param str - 写入的字符串
	 * @throws IOException - 如果发生 I/O 错误
	 */
	@Override
	public void write(String str) throws IOException {
        if (str == null) {
            str = "null";
        }
		write(str, 0, str.length());
	}
	
	/**
	 * 写入字符串的一部分
	 * 
	 * @param str - 部分写入的字符串
	 * @param off - 开始写入字符的偏移量
	 * @param len - 要写入的字符数
	 */
	@Override
	public void write(String str, int off, int len) throws IOException {
		if (suspended) {
            return;
        }

        if (str == null) {
            throw new NullPointerException("str");
        }

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) { // 循环写入缓冲区并刷新到预期目标流之后
            int n = transfer(str, sOff, sEnd - sOff, charBuffer);
            sOff += n;
            if (isFull(charBuffer)) {
                flushCharBuffer();
            }
        }

        charsWritten += len;
	}
	
    public void write(ByteBuffer from) throws IOException {
        if (suspended) {
            return;
        }
        

        if (closed) {
            return;
        }

        append(from);
        bytesWritten += from.remaining();

        // 如果从 flush() 中调用，则立即刷新剩余字节
        if (doFlush) {
            flushByteBuffer();
        }
    }
	
	/**
	 * 关闭输出缓冲区。 如果响应尚未提交，这将尝试计算响应大小。
	 * 
	 * @throws IOException - 发生了基础 IOException
	 */
	@Override
	public void close() throws IOException {
        if (closed) {
            return;
        }
        if (suspended) {
            return;
        }

        // 如果有字符，现在将它们全部刷新到字节缓冲区，因为字节用于计算内容长度（当然，如果所有内容都适合字节缓冲区）
        if ( !useByteBuffer && charBuffer.remaining() > 0) {
            flushCharBuffer();
        }

        if ((!response.isCommitted()) && (response.getContentLengthLong() == -1) && !response.getRequest().method().equals("HEAD")) {
            /**
             * 如果这没有导致响应的提交，则可以计算最终的内容长度。 仅当这不是 HEAD 请求时才这样做。
             * 因为在这种情况下，不应写入任何正文，并且在此处设置零值将导致在响应上显式设置内容长度为零。
             */
            if (!response.isCommitted()) {
            	response.setContentLength(this.getBytesWritten());
            }
        }

        if (response.getStatus() == HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
            doFlush(true);
        } else {
            doFlush(false);
        }
        closed = true;

        response.action(ActionCode.CLOSE, null);		
	}
	
    /**
     * 缓冲区中包含的字节或字符
     * <p>
     * 刷新流。 如果流已将来自各种 write() 方法的任何字符保存在缓冲区中，则立即将它们写入其预期目标。 
     * 然后，如果该目的地是另一个字符或字节流，则刷新它。 因此，一次 flush() 调用将刷新 Writers 和 OutputStreams 链中的所有缓冲区。
     * <p>
     * 如果此流的预期目的地是底层操作系统提供的抽象，例如文件，则刷新流仅保证先前写入流的字节被传递给操作系统进行写入； 它不保证它们实际上被写入了物理设备，例如磁盘驱动器。
     * 
     * @throws IOException - 如果发生 I/O 错误
     */
	@Override
	public void flush() throws IOException {
//        doFlush(true);
	}


	// -------------------------------------------------------------------------------------
	// 基础方法
	// -------------------------------------------------------------------------------------
    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }
    
    /**
     * 缓冲区是否已满
     * @param httpOutputBuffer
     * @return true则代表缓冲区已满
     */
    private boolean isFull(Buffer buffer) {
        return buffer.limit() == buffer.capacity();
    }

    /**
     * 切换到读模式
     * @param httpOutputBuffer
     */
    private void toReadMode(Buffer buffer) {
        buffer.limit(buffer.position()).reset();
    }

    /**
     * 切换到写模式
     * @param httpOutputBuffer
     */
    private void toWriteMode(Buffer buffer) {
        buffer.mark().position(buffer.limit()).limit(buffer.capacity());
    }
    
    /**
     * 转移一个字节到字节缓冲区中
     * @param b - 转移字节
     * @param to - 转移字节缓冲区
     */
    private void transfer(byte b, ByteBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }

    /**
     * 转移一个字符到字符缓冲区中
     * @param b - 转移字符
     * @param to - 转移字符缓冲区
     */
    private void transfer(char c, CharBuffer to) {
        toWriteMode(to);
        to.put(c);
        toReadMode(to);
    }

    /**
     * 转移源数据数组中的一段连续区间中的字节到字节缓冲区中
     * 
     * @param buf - 源数据数组
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @param to - 转入字节的缓冲区
     * @return 转移数据量
     */
    private int transfer(byte[] buf, int off, int len, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    /**
     * 转移源数据数组中的一段连续区间中的字符到字符缓冲区中
     * 
     * @param buf - 源数据数组
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @param to - 目标字节缓冲区
     * @return 转移数据量
     */
    private int transfer(char[] buf, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    /**
     * 转移字符串中的一段连续区间中的字符到字符缓冲区中
     * @param s - 源数据字符串
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @param to - 目标字符缓冲区
     * @return 转移数据量
     */
    private int transfer(String s, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(s, off, off + max);
        }
        toReadMode(to);
        return max;
    }

    /**
     * 字节缓冲区之间的数据转移
     * 
     * @param from - 源数据字节缓冲区
     * @param to - 目标字节缓冲区
     */
    private void transfer(ByteBuffer from, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(from.remaining(), to.remaining());
        if (max > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + max);
            to.put(from);
            from.limit(fromLimit);
        }
        toReadMode(to);
    }
    
    /**
     * 添加数据到缓冲区
     * 
     * @param src - Byte 数组
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @throws IOException - 将溢出数据写入输出通道失败
     */
    public void append(byte src[], int off, int len) throws IOException {
        if (byteBuffer.remaining() == 0) { // 缓冲区无写入空间
            appendByteArray(src, off, len);
        } else {
        	// 缓冲区还有写入空间则写满缓冲区，之后再尝试写入剩余数据
            int n = transfer(src, off, len, byteBuffer);
            len = len - n;
            off = off + n;
            if (isFull(byteBuffer)) {
                flushByteBuffer();
                appendByteArray(src, off, len);
            }
        }
    }
    
    private void appendByteArray(byte src[], int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        int limit = byteBuffer.capacity();
        while (len >= limit) {
            realWriteBytes(ByteBuffer.wrap(src, off, limit));
            len = len - limit;
            off = off + limit;
        }

        if (len > 0) { // 缓冲次级流写剩下的数据
            transfer(src, off, len, byteBuffer);
        }
    }
    
    /**
     * 添加数据到缓冲区
     * @param src - Char数组
     * @param off - 数据中的起始偏移量
     * @param len - 要写入的字节数
     * @throws IOException - 将溢出数据写入输出通道失败
     */
    public void append(char src[], int off, int len) throws IOException {
        // 缓冲区可以容纳得下写入数据则直接追加到缓冲区中
        if(len <= charBuffer.capacity() - charBuffer.limit()) {
            transfer(src, off, len, charBuffer);
            return;
        }

        /*
         * 优化:
         * 1.如果写入数据使用2次缓冲区就可容纳，你们就写入2次缓冲区，只是第一次写入数据更多。
         * 2.如果写入数据使用2次缓冲区都不能容下则刷新缓冲区之后直接将数据写入预期目标。
         */
        if(len + charBuffer.limit() < 2 * charBuffer.capacity()) {
			/*
			 * 如果请求长度超过输出缓冲区的大小，则刷新输出缓冲区，然后直接写入数据。 我们无法避免两次写入，但我们可以在第二次写入更多
			 */
            int n = transfer(src, off, len, charBuffer);

            flushCharBuffer();

            transfer(src, off + n, len - n, charBuffer);
        } else {
            flushCharBuffer();

            realWriteChars(CharBuffer.wrap(src, off, len));
        }
    }
    
    public void append(ByteBuffer from) throws IOException {
        if (byteBuffer.remaining() == 0) { // 缓冲区无写入空间
            appendByteBuffer(from);
        } else {
            transfer(from, byteBuffer);
            if (isFull(byteBuffer)) {
                flushByteBuffer();
                appendByteBuffer(from);
            }
        }
    }
    
    private void appendByteBuffer(ByteBuffer from) throws IOException {
        if (from.remaining() == 0) {
            return;
        }

        int limit = byteBuffer.capacity();
        int fromLimit = from.limit();
        
        /*
         * 若待读数据量超过当前缓冲区尺寸则直接写入数据到预期目标流。
         * 若待读数据量未超过当前缓冲区尺寸则写入缓冲区。
         */
        while (from.remaining() >= limit) {
            from.limit(from.position() + limit);
            realWriteBytes(from.slice());
            from.position(from.limit());
            from.limit(fromLimit);
        }

        if (from.remaining() > 0) {
            transfer(from, byteBuffer);
        }
    }
    
    /**
     * 刷新字节缓冲区数据到底层输出流
     * @throws IOException
     */
    private void flushByteBuffer() throws IOException {
        realWriteBytes(byteBuffer.slice());
        clear(byteBuffer);
    }

    /**
     * 刷新字符缓冲区数据到底层输出流
     * @throws IOException
     */
    private void flushCharBuffer() throws IOException {
        realWriteChars(charBuffer.slice());
        clear(charBuffer);
    }
    
    /**
     * 将字符转换为字节，然后将数据发送到客户端。
     *
     * @param from - 要写入响应的字符缓冲区
     * @throws IOException - 发生了基础 IOException
     */
    public void realWriteChars(CharBuffer from) throws IOException {
    	if (from.limit() > 0) {
        	byteBuffer = response.getCharset().encode(from);
        	bytesWritten = byteBuffer.limit();
    	}
    	flushByteBuffer();
    }
    
    /**
     * 将缓冲区数据发送到客户端输出，检查响应状态并调用正确的拦截器。
     *
     * @param buf - 要写入响应的 ByteBuffer
     * @throws IOException - 发生了基础 IOException
     */
    public void realWriteBytes(ByteBuffer buf) throws IOException {
        if (closed) {
            return;
        }
        if (response == null) {
            return;
        }

        // 如果有内容可写
        if (buf.remaining() > 0) {
            try {
            	response.doWrite(buf);
            } catch (CloseNowException e) {
                /*
                 * 捕获这个子类，因为它需要特定的处理。 抛出此异常的示例：
                 * HTTP/2 流超时，阻止此响应的进一步输出
                 */
                closed = true;
                throw e;
            } catch (IOException e) {
                // 写入时的 IOException 几乎总是由于远程客户端中止请求。 包装它，以便错误调度程序可以更好地处理它。
                throw new ClientAbortException(e);
            }
        }
    }
    
    /**
     * 刷新缓冲区中包含的字节或字符
     *
     * @param realFlush - 如果这也应该导致真正的网络刷新，则为<code>true</code>
     * @throws IOException - 发生了基础 IOException
     */
    protected void doFlush(boolean realFlush) throws IOException {
        if (suspended) {
            return;
        }

        try {
            doFlush = true;
            if (initial) {
                response.sendHeaders();
                initial = false;
            }
            if (charBuffer.remaining() > 0) {
                flushCharBuffer();
            }
            if (byteBuffer.remaining() > 0) {
                flushByteBuffer();
            }
        } finally {
            doFlush = false;
        }

        if (realFlush) {
            response.action(ActionCode.CLIENT_FLUSH, null);
            // 如果之前发生了一些异常，或者这里发生了一些 IOE，请使用 IOE 通知 servlet
            if (response.isExceptionPresent()) {
                throw new ClientAbortException(response.getErrorException());
            }
        }

    }
}
