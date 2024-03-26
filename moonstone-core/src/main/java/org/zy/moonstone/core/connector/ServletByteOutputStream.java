package org.zy.moonstone.core.connector;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;


/**
 * @dateTime 2022年7月20日;
 * @author zy(azurite-Y);
 * @description servlet 输出流
 */
public class ServletByteOutputStream extends ServletOutputStream {
	protected OutputBuffer outputBuffer;
	
	public ServletByteOutputStream(OutputBuffer outputBuffer) {
		this.outputBuffer = outputBuffer;
	}

	/**
     * 防止克隆 facade
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
	
    /**
     * 清理 facade
     */
    void clear() {
    	outputBuffer = null;
    }
    
	// -------------------------------------------------------------------------------------
	// OutputStream Methods
	// -------------------------------------------------------------------------------------
	@Override
	public void write(int b) throws IOException {
//		boolean nonBlocking = checkNonBlockingWrite();
        outputBuffer.write(b);
//        if (nonBlocking) {
//            checkRegisterForWrite();
//        }
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
//		boolean nonBlocking = checkNonBlockingWrite();
        outputBuffer.write(b, off, len);
//        if (nonBlocking) {
//            checkRegisterForWrite();
//        }
	}
	
	public void write(ByteBuffer from) throws IOException {
//        boolean nonBlocking = checkNonBlockingWrite();
        outputBuffer.write(from);
//        if (nonBlocking) {
//            checkRegisterForWrite();
//        }
    }
	
	@Override
	public void flush() throws IOException {
//		boolean nonBlocking = checkNonBlockingWrite();
        outputBuffer.flush();
//        if (nonBlocking) {
//            checkRegisterForWrite();
//        }
	}
	
    /**
     * 检查不允许的并发写操作。该节点没有状态信息，因此调用链是
     * CoyoteOutputStream->OutputBuffer->CoyoteResponse.
     *
     * @return 如果此OutputStream当前处于非阻塞模式，则为<code>true</code>
     */
//    private boolean checkNonBlockingWrite() {
//        boolean nonBlocking = !outputBuffer.isBlocking();
//        if (nonBlocking && !outputBuffer.isReady()) {
//            throw new IllegalStateException("OutputStream Notready");
//        }
//        return nonBlocking;
//    }

    /**
     * 检查套接字输出缓冲区（不是 servlet 输出缓冲区）中是否还有数据，如果有，则注册关联的套接字进行写入，以便清空缓冲区。 
     * 容器会处理这个问题。 就应用程序而言，正在进行非阻塞写入。 它不知道数据是缓冲在套接字缓冲区还是servlet缓冲区中。
     */
//    private void checkRegisterForWrite() {
//        outputBuffer.checkRegisterForWrite();
//    }
    
    @Override
    public void close() throws IOException {
        outputBuffer.close();
    }

    @Override
    public boolean isReady() {
        return true;
    }


    @Override
    public void setWriteListener(WriteListener listener) {
    	outputBuffer.setWriteListener(listener);
    }
}
