package org.zy.moonStone.core.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import org.zy.moonStone.core.util.buf.ByteBufferHolder;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description 
 * 为写入提供一组可扩展的缓冲区。 非阻塞写入可以是任何大小，并且可能无法立即写入或完全包含在用于执行对下一层的写入的缓冲区中。
 *  此类提供缓冲功能以允许此类写入立即返回，并且还允许根据需要重新使用/回收用户提供的缓冲区。
 */
public class WriteBuffer {
	private final int bufferSize;

    private final LinkedBlockingDeque<ByteBufferHolder> buffers = new LinkedBlockingDeque<>();

    public WriteBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    void clear() {
        buffers.clear();
    }

    void add(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = getByteBufferHolder(length);
        holder.getBuf().put(buf, offset, length);
    }


    public void add(ByteBuffer from) {
        ByteBufferHolder holder = getByteBufferHolder(from.remaining());
        holder.getBuf().put(from);
    }


    private ByteBufferHolder getByteBufferHolder(int capacity) {
        ByteBufferHolder holder = buffers.peekLast();
        if (holder == null || holder.isFlipped() || holder.getBuf().remaining() < capacity) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferSize, capacity));
            holder = new ByteBufferHolder(buffer, false);
            buffers.add(holder);
        }
        return holder;
    }


    public boolean isEmpty() {
        return buffers.isEmpty();
    }


    /**
     * 从当前的 WriteBuffer 创建一个 ByteBuffers 数组，在该数组前面加上提供的 ByteBuffers
     *
     * @param prefixes - 添加到数组开头的附加字节缓冲区
     * @return 当前 WriteBuffer 中的 ByteBuffers 数组，前缀为提供的 ByteBuffers
     */
    ByteBuffer[] toArray(ByteBuffer... prefixes) {
        List<ByteBuffer> result = new ArrayList<>();
        for (ByteBuffer prefix : prefixes) {
            if (prefix.hasRemaining()) {
                result.add(prefix);
            }
        }
        for (ByteBufferHolder buffer : buffers) {
            buffer.flip();
            result.add(buffer.getBuf());
        }
        buffers.clear();
        return result.toArray(new ByteBuffer[result.size()]);
    }


    public boolean write(SocketWrapperBase<?> socketWrapper, boolean blocking) throws IOException {
        Iterator<ByteBufferHolder> bufIter = buffers.iterator();
        boolean dataLeft = false;
        while (!dataLeft && bufIter.hasNext()) {
            ByteBufferHolder buffer = bufIter.next();
            buffer.flip();
            if (blocking) {
                socketWrapper.writeBlocking(buffer.getBuf());
            } else {
                socketWrapper.writeNonBlockingInternal(buffer.getBuf());
            }
            if (buffer.getBuf().remaining() == 0) {
                bufIter.remove();
            } else {
                dataLeft = true;
            }
        }
        return dataLeft;
    }


    public boolean write(Sink sink, boolean blocking) throws IOException {
        Iterator<ByteBufferHolder> bufIter = buffers.iterator();
        boolean dataLeft = false;
        while (!dataLeft && bufIter.hasNext()) {
            ByteBufferHolder buffer = bufIter.next();
            buffer.flip();
            dataLeft = sink.writeFromBuffer(buffer.getBuf(), blocking);
            if (!dataLeft) {
                bufIter.remove();
            }
        }
        return dataLeft;
    }


    /**
     * Interface implemented by clients of the WriteBuffer to enable data to be
     * written back out from the httpOutputBuffer.
     */
    public interface Sink {
        boolean writeFromBuffer(ByteBuffer buffer, boolean block) throws IOException;
    }
}
