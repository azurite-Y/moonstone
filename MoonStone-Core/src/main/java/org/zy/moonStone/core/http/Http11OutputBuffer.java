package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.exceptions.CloseNowException;
import org.zy.moonstone.core.exceptions.HeadersTooLargeException;
import org.zy.moonstone.core.util.buf.ByteChunk;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.net.SocketWrapperBase;
import org.zy.moonstone.core.util.net.interfaces.HttpOutputBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @dateTime 2022年7月20日;
 * @author zy(azurite-Y);
 * @description
 */
public class Http11OutputBuffer implements HttpOutputBuffer {
	private static final Logger logger = LoggerFactory.getLogger(Http11OutputBuffer.class);

	/**
	 * 提供对底层套接字访问的包装器
	 */
	private SocketWrapperBase<?> socketWrapper;
	
    /**
     * 当前请求写入客户端的字节数
     */
    protected long byteCount = 0;
	
    /**
     * 底层输出缓冲区
     */
    protected HttpOutputBuffer socketOutputBuffer;
    
    /**
     * 用于缓存响应头字节的缓冲区
     */
    protected final ByteBuffer headerBuffer;
    
    /**
     * 用于处理响应体的筛选器数组
     */
    protected OutputFilter[] filterLibrary;

    /**
     * 当前请求的使用的筛选器
     */
    protected OutputFilter[] activeFilters;

    /**
     * 最后一个活动筛选器的索引。
     */
    protected int lastActiveFilter;
    
	/**
	 * 关联的原初响应对象
	 */
	private final Response response;
	
	/** 记录HTTP响应行和响应头数据 */
	private StringBuilder responeHeaderBuilder = new StringBuilder();
	
    /**
     * 完成的标志
     */
    protected boolean responseFinished;
    
	/**
	 * 根据给定的参数实例化一个 {@code Http11InputBuffer } 对象
	 * @param response
	 */
	public Http11OutputBuffer(Response response, int maxHttpHeaderSize) {
		this.response = response;
		
        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;
		
		socketOutputBuffer = new SocketOutputBuffer();
        headerBuffer = ByteBuffer.allocate(maxHttpHeaderSize);
	}
	
	@Override
	public int doWrite(ByteBuffer chunk) throws IOException {
		if (!response.isCommitted()) {
            // 向连接器发送提交请求。 然后，连接器应验证标头，发送它们（使用 sendHeaders）并相应地设置过滤器。
            response.action(ActionCode.COMMIT, null);
        }

		if (lastActiveFilter == -1) {
			return socketOutputBuffer.doWrite(chunk);
		} else {
			return activeFilters[lastActiveFilter].doWrite(chunk);
		}
	}

	@Override
	public long getBytesWritten() {
        if (lastActiveFilter == -1) {
            return socketOutputBuffer.getBytesWritten();
        } else {
            return activeFilters[lastActiveFilter].getBytesWritten();
        }
	}

	@Override
	public void end() throws IOException {
        if (responseFinished) {
            return;
        }
        
        if (lastActiveFilter == -1) {
        	socketOutputBuffer.end();
        } else {
            activeFilters[lastActiveFilter].end();
        }
        responseFinished = true;
	}

	@Override
	public void flush() throws IOException {
        if (lastActiveFilter == -1) {
        	socketOutputBuffer.flush();
        } else {
            activeFilters[lastActiveFilter].flush();
        }
	}

	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
	/**
	 * 回收输出缓冲区。 这应该在关闭连接时调用
	 */
    public void recycle() {
        nextRequest();
        socketWrapper = null;
    }

    /**
     * 如果在写响应头时发生错误，则重置响应头缓冲区，以便写入错误响应。
     */
    void resetHeaderBuffer() {
        headerBuffer.position(0).limit(headerBuffer.capacity());
    }
    
    public void init(SocketWrapperBase<?> socketWrapper) {
	    this.socketWrapper = socketWrapper;
	}

	/**
     * 结束当前HTTP请求的处理。
     * 注意：当前请求的所有字节应该已经被消耗掉了。 此方法仅重置所有指针，以便准备好解析下一个 HTTP 请求。
     */
    public void nextRequest() {
        response.recycle();
        responseFinished = false;
        byteCount = 0;
        lastActiveFilter = -1;
        responeHeaderBuilder.delete(0, responeHeaderBuilder.length());
    }
    
    public void sendAck() throws IOException {
        if (!response.isCommitted()) {
            socketWrapper.write(isBlocking(), Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
            if (flushBuffer(true)) {
                throw new IOException("ACK 消息写入失败");
            }
        }
    }
    
    /**
     * 发送响应状态行
     */
    public void sendStatus() {
        // 写入协议名称
        write(Constants.HTTP_11_BYTES);
        headerBuffer.put(Constants.SP);

        // 写入状态码
        int status = response.getStatus();
        switch (status) {
        case 200:
            write(Constants._200_BYTES);
            break;
        case 400:
            write(Constants._400_BYTES);
            break;
        case 404:
            write(Constants._404_BYTES);
            break;
        default:
            write(status);
        }

        headerBuffer.put(Constants.SP);
//        headerBuffer.put("ok".getBytes());
        // 原因短语(reason phrase) 是可选的，但它前面的空格不是。跳过发送原因短语。客户端应该忽略它(RFC 7230)，它只会浪费字节。
        headerBuffer.put(Constants.CR).put(Constants.LF);
        
        
        if (logger.isDebugEnabled()) {
        	responeHeaderBuilder.append(Constants.HTTP_11).append(" ").append(status).append(" ").append(Constants.CRLF);
        }
    }

    /**
     * 写入响应头
     *
     * @param name - 响应头名
     * @param value - 响应头值
     */
    public void sendHeader(MessageBytes name, MessageBytes value) {
        write(name);
        headerBuffer.put(Constants.COLON).put(Constants.SP);
        write(value);
        headerBuffer.put(Constants.CR).put(Constants.LF);
        
        if (logger.isDebugEnabled()) {
        	responeHeaderBuilder.append(name.getString()).append(": ").append(value.getString()).append(Constants.CRLF);
        }
    }
    
    /**
     * 响应头结束符（空行）
     */
    public void endHeaders() {
        headerBuffer.put(Constants.CR).put(Constants.LF);
    }
    
    /**
     * 该方法将把指定字节缓冲区的内容写入输出流，而不进行过滤。该方法用于编写响应标头。
     *
     * @param b - 写入数据
     */
    public void write(byte[] b) {
        checkLengthBeforeWrite(b.length);

        // 将字节块写入输出缓冲区
        headerBuffer.put(b);
    }
	
    /**
     * Get filters.
     *
     * @return 包含所有可能筛选器的当前筛选器库
     */
    public OutputFilter[] getFilters() {
        return filterLibrary;
    }
    
    /**
     * 将输出过滤器添加到当前响应的活动过滤器。
     * <p>
     * 过滤器不必出现在getFilters()中。
     * <p>
     * 一个过滤器只能添加到一个响应中一次。如果筛选器已经添加到此响应中，则此方法将是无操作的。
     * 
     * @param filter - 添加的Filter
     */
    public void addActiveFilter(OutputFilter filter) {
        if (lastActiveFilter == -1) {
            filter.setHttpOutputBuffer(this.socketOutputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setHttpOutputBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setResponse(response);
    }
    
    /**
     * 将 OutputFilter 添加到过滤器库。请注意，调用此方法会将 {@link #activeFilters 当前活动的筛选器 } 重置为None。
     *
     * @param filter - 添加的过滤器
     */
    public void addFilter(OutputFilter filter) {
        OutputFilter[] newFilterLibrary = Arrays.copyOf(filterLibrary, filterLibrary.length + 1);
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new OutputFilter[filterLibrary.length];
    }
    
    // -------------------------------------------------------------------------------------
	// protected methods
	// -------------------------------------------------------------------------------------
    /**
     * 写入任何剩余的缓冲数据
     *
     * @param block - 这个方法是否应该阻塞直到缓冲区为空
     * @return 如果数据保留在缓冲区中（只能在非阻塞模式下发生），则为 <code>true</code>，否则为 <code>false</code>
     * @throws IOException - 写入数据时出错
     */
    protected boolean flushBuffer(boolean block) throws IOException  {
        return socketWrapper.flush(block);
    }

    /**
     * 标准 Servlet 阻塞 IO 是否用于输出？
     * 
     * @return 如果这是阻塞 IO，则为 <code>true</code>
     */
    protected final boolean isBlocking() {
        return response.getWriteListener() == null;
    }
	
    /**
     * 提交响应
     *
     * @throws IOException - 发生了底层I/O错误
     */
    protected void commit() throws IOException {
        response.setCommitted(true);

        if (headerBuffer.position() > 0) {
            // 发送响应头缓冲区
            headerBuffer.flip();
            try {
                SocketWrapperBase<?> socketWrapper = this.socketWrapper;
                if (socketWrapper != null) {
                    socketWrapper.write(isBlocking(), headerBuffer);
                    
            		if ( logger.isDebugEnabled() ) {
            			logger.debug("Respone Header[{}]: \n{}", this.response.getRequest().decodedURI(), responeHeaderBuilder.toString().trim());
            		}
                } else {
                    throw new CloseNowException("写入失败");
                }
            } finally {
            	// 重置为写入状态
                headerBuffer.position(0).limit(headerBuffer.capacity());
            }
        }
    }
    
	// -------------------------------------------------------------------------------------
	// private methods
	// -------------------------------------------------------------------------------------
    /**
     * 检查缓冲区中是否有足够的空间写入所需的字节数。
     */
    private void checkLengthBeforeWrite(int length) {
        // "+ 4": 为CR/LF/COLON/SP字符保留空间，这些字符在写入操作后直接放入缓冲区。
        if (headerBuffer.position() + length + 4 > headerBuffer.capacity()) {
            throw new HeadersTooLargeException("响应头数据太大.");
        }
    }
    
	/**
	 * 此方法将把指定的消息字节缓冲区的内容写入输出流，而不进行过滤。该方法用于编写响应标头。
	 *
	 * @param mb - 待写入数据
	 */
	private void write(MessageBytes mb) {
		if (mb.getType() != MessageBytes.T_BYTES) {
	        mb.toBytes();
	        ByteChunk bc = mb.getByteChunk();
	        // 需要过滤掉除Tab以外的ctl。ISO-8859-1和UTF-8值是可以的。使用其他编码的字符串可能被损坏。
	        byte[] buffer = bc.getBuffer();
	        for (int i = bc.getOffset(); i < bc.getLength(); i++) {
	            // 这些值是无符号的。0到31是ctl，因此它们被过滤(除了TAB为9)。127是一个控制(DEL)。128 ~ 255都可以。将它们转换为有符号的结果是-128 = -1。
	            if ((buffer[i] > -1 && buffer[i] <= 31 && buffer[i] != 9) || buffer[i] == 127) {
	                buffer[i] = ' ';
	            }
	        }
	    }
		
		if (mb.getType() != MessageBytes.T_BYTES) {
	        mb.toBytes();
	        ByteChunk bc = mb.getByteChunk();
	        byte[] buffer = bc.getBuffer();
	        for (int i = bc.getOffset(); i < bc.getLength(); i++) {
	            // byte values are signed i.e. -128 to 127
	            // 
	            if ((buffer[i] > -1 && buffer[i] <= 31 && buffer[i] != 9) || buffer[i] == 127) {
	                buffer[i] = ' ';
	            }
	        }
	    }
	    write(mb.getByteChunk().getBuffer());
	}

	/**
	 * 此方法将将指定的整数写入输出流。此方法用于写入响应头。
	 *
	 * @param value - 要写入的数据
	 */
	private void write(int value) {
	    String s = Integer.toString(value);
	    int len = s.length();
	    checkLengthBeforeWrite(len);
	    for (int i = 0; i < len; i++) {
	        char c = s.charAt (i);
	        headerBuffer.put((byte) c);
	    }
	}

	/**
     * 此类是一个输出缓冲区，它将数据写入套接字
     */
    protected class SocketOutputBuffer implements HttpOutputBuffer {
        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            try {
                int len = chunk.remaining();
                SocketWrapperBase<?> socketWrapper = Http11OutputBuffer.this.socketWrapper;
                if (socketWrapper != null) {
                    socketWrapper.write(isBlocking(), chunk);
                } else {
                    throw new CloseNowException("写入失败");
                }
                // 减去已写入的数据量
                len -= chunk.remaining();
                byteCount += len;
                return len;
            } catch (IOException ioe) {
                response.action(ActionCode.CLOSE_NOW, ioe);
                // 重新抛出
                throw ioe;
            }
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }

        @Override
        public void end() throws IOException {
            socketWrapper.flush(true);
        }

        @Override
        public void flush() throws IOException {
            socketWrapper.flush(isBlocking());
        }
    }
}
