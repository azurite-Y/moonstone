package org.zy.moonStone.core.util.net;

import java.nio.ByteBuffer;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description 套接字缓冲区操作类，解耦写入和刷新操作
 */
public class SocketBufferHandler {
	 static SocketBufferHandler EMPTY = new SocketBufferHandler(0, 0, false) ;
		 
//	private volatile ByteBuf ReadBuffer;
	private final boolean direct;
    private volatile boolean readBufferConfiguredForWrite = true;
    private volatile ByteBuffer readBuffer;

    private volatile boolean writeBufferConfiguredForWrite = true;
    private volatile ByteBuffer writeBuffer;
	
	public SocketBufferHandler(int initialCapacity, int maxCapacity, boolean direct) {
		this.direct = direct;
		// 创建ByteBuf对象，并指定初始容量和最大容量
		if (direct) {
//			ReadBuffer = ByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
			readBuffer = ByteBuffer.allocateDirect(maxCapacity);
            writeBuffer = ByteBuffer.allocateDirect(maxCapacity);
		} else {
//			ReadBuffer = ByteBufAllocator.DEFAULT.buffer(initialCapacity, maxCapacity);
			readBuffer = ByteBuffer.allocate(maxCapacity);
            writeBuffer = ByteBuffer.allocate(maxCapacity);
		}
	}

	public ByteBuffer getReadBuffer() {
		return readBuffer;
	}

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }
	
	public void reset() {
		readBuffer.clear();
        writeBuffer.clear();
        writeBufferConfiguredForWrite = true;
	}


	/**
	 * 释放缓冲区内存
	 */
	public void free() {
		if (direct) {
			readBuffer = null;
			writeBuffer = null;
		}
	}

	
	public void configureReadBufferForWrite() {
        setReadBufferConfiguredForWrite(true);
    }

    public void configureReadBufferForRead() {
        setReadBufferConfiguredForWrite(false);
    }

    private void setReadBufferConfiguredForWrite(boolean readBufferConFiguredForWrite) {
        // NO-OP 如果缓冲区已处于正确状态
        if (this.readBufferConfiguredForWrite != readBufferConFiguredForWrite) {
            if (readBufferConFiguredForWrite) {
                // Switching to write
                int remaining = readBuffer.remaining();
                if (remaining == 0) {
                    readBuffer.clear();
                } else {
                    readBuffer.compact();
                    
                }
                // 设置为写状态时从当前 readBuffer.position() 处开始写
            } else {
                // Switching to read
                readBuffer.flip();
            }
            this.readBufferConfiguredForWrite = readBufferConFiguredForWrite;
        }
    }
    
    public boolean isReadBufferEmpty() {
        if (readBufferConfiguredForWrite) {
            return readBuffer.position() == 0;
        } else {
            return readBuffer.remaining() == 0;
        }
    }
    
    
    
	public void configureWriteBufferForWrite() {
        setWriteBufferConfiguredForWrite(true);
    }

    public void configureWriteBufferForRead() {
        setWriteBufferConfiguredForWrite(false);
    }

    private void setWriteBufferConfiguredForWrite(boolean writeBufferConfiguredForWrite) {
        if (this.writeBufferConfiguredForWrite != writeBufferConfiguredForWrite) {
            if (writeBufferConfiguredForWrite) {
                // Switching to write
                int remaining = writeBuffer.remaining();
                if (remaining == 0) {
                    writeBuffer.clear();
                } else {
                    writeBuffer.compact();
                    writeBuffer.position(remaining);
                    writeBuffer.limit(writeBuffer.capacity());
                }
            } else {
                // Switching to read
                writeBuffer.flip();
            }
            this.writeBufferConfiguredForWrite = writeBufferConfiguredForWrite;
        }
    }
    
    /**
     * 
     * @return true代表 写入缓冲区内还有数据未读取
     */
    public boolean isWriteBufferWritable() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.hasRemaining();
        } else {
            return writeBuffer.remaining() == 0;
        }
    }
    
    /**
     * @return true 代表写入缓冲区为空
     */
    public boolean isWriteBufferEmpty() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.position() == 0;
        } else {
            return writeBuffer.remaining() == 0;
        }
    }
}
