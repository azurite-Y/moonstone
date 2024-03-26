package org.zy.moonstone.core.http.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description
 * 该类实现了一个输出流，其中数据被写入字节数组。当数据写入缓冲区时，缓冲区会自动增长。
 * <p>
 * 可以使用toByteArray()和toString()检索数据。
 * <p>
 * 关闭ByteArrayOutputStream没有效果。该类中的方法可以在流关闭后调用，而不生成IOException。
 * <p>
 * 这是java.io. bytearrayoutputstream类的另一种实现。最初的实现在开始时只分配32个字节。
 * 由于这个类是为重载设计的，所以它的初始值为1024字节。与原来的相比，它没有重新分配整个内存块，而是分配了额外的缓冲区。
 * 这样就不需要垃圾回收缓冲区，内容也不需要复制到新的缓冲区。这个类被设计成与原始类完全相同的行为。
 * 唯一的例外是被忽略的已弃用的toString(int)方法。
 */
public class ByteArrayOutputStream extends OutputStream {
	static final int DEFAULT_SIZE = 1024;

    /** 一个单例空字节数组 */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /** 缓冲区的列表，它会增长而不会减少 */
    private final List<byte[]> buffers = new ArrayList<>();
    
    /** 当前缓冲区的索引 */
    private int currentBufferIndex;
    
    /** 所有已填充缓冲区中的字节总数 */
    private int filledBufferSum;
    
    /** 当前缓冲区 */
    private byte[] currentBuffer;
    
    /** 写入的字节总数 */
    private int count;

    
    /**
     * 创建一个新的字节数组输出流。缓冲区容量最初是1024字节，但如果需要，它的大小会增加。
     */
    public ByteArrayOutputStream() {
        this(DEFAULT_SIZE);
    }
    
    /**
     * 创建一个新的字节数组输出流，具有指定大小的缓冲区容量，以字节为单位。
     *
     * @param size - 初始大小
     * @throws IllegalArgumentException - 如果size是负数
     */
    public ByteArrayOutputStream(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("初始尺寸为负: " + size);
        }
        synchronized (this) {
            needNewBuffer(size);
        }
    }

    /**
     * 通过分配新缓冲区或回收现有缓冲区，使新的缓冲区可用
     *
     * @param newcount - 如果创建了缓冲区，则指定缓冲区的大小
     */
    private void needNewBuffer(final int newcount) {
        if (currentBufferIndex < buffers.size() - 1) {
            // 回收旧缓冲区
            filledBufferSum += currentBuffer.length;

            currentBufferIndex++;
            currentBuffer = buffers.get(currentBufferIndex);
        } else {
            // 创建新的缓冲区
            int newBufferSize;
            if (currentBuffer == null) {
                newBufferSize = newcount;
                filledBufferSum = 0;
            } else {
                newBufferSize = Math.max(currentBuffer.length << 1, newcount - filledBufferSum);
                filledBufferSum += currentBuffer.length;
            }

            currentBufferIndex++;
            currentBuffer = new byte[newBufferSize];
            buffers.add(currentBuffer);
        }
    }

    /**
     * 将字节写入字节数组
     * 
     * @param b - 要写入的字节
     * @param off - 开始偏移量
     * @param len - 写入的字节数
     */
    @Override
    public void write(final byte[] b, final int off, final int len) {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        synchronized (this) {
            final int newcount = count + len;
            int remaining = len;
            int inBufferPos = count - filledBufferSum;
            while (remaining > 0) {
                final int part = Math.min(remaining, currentBuffer.length - inBufferPos);
                System.arraycopy(b, off + len - remaining, currentBuffer, inBufferPos, part);
                remaining -= part;
                if (remaining > 0) {
                    needNewBuffer(newcount);
                    inBufferPos = 0;
                }
            }
            count = newcount;
        }
    }

    /**
     * 写一个字节到字节数组
     * 
     * @param b - 写入的字节
     */
    @Override
    public synchronized void write(final int b) {
        int inBufferPos = count - filledBufferSum;
        if (inBufferPos == currentBuffer.length) {
            needNewBuffer(count + 1);
            inBufferPos = 0;
        }
        currentBuffer[inBufferPos] = (byte) b;
        count++;
    }

    /**
     * 将指定输入流的全部内容写入此字节流。来自输入流的字节直接读入该流的内部缓冲区。
     *
     * @param in - 要从中读取的输入流
     * @return 从输入流读取(并写入此流)的总字节数
     * 
     * @throws IOException - 如果在读取输入流时发生I/O错误
     */
    public synchronized int write(final InputStream in) throws IOException {
        int readCount = 0;
        int inBufferPos = count - filledBufferSum;
        int n = in.read(currentBuffer, inBufferPos, currentBuffer.length - inBufferPos);
        while (n != -1) {
            readCount += n;
            inBufferPos += n;
            count += n;
            if (inBufferPos == currentBuffer.length) {
                needNewBuffer(currentBuffer.length);
                inBufferPos = 0;
            }
            n = in.read(currentBuffer, inBufferPos, currentBuffer.length - inBufferPos);
        }
        return readCount;
    }

    /**
     * 关闭 {@code ByteArrayOutputStream} 没有效果。该类中的方法可以在流关闭后调用，而不生成 {@code IOException}。
     *
     * @throws IOException - 永不 (此方法不应该声明此异常，但由于向后兼容，现在必须声明此异常)
     */
    @Override
    public void close() throws IOException {}

    /**
     * 将此字节流的全部内容写入指定的输出流。
     *
     * @param out - 要写入的输出流
     * @throws IOException - 如果I/O错误发生，例如流被关闭
     * 
     * @see java.io.ByteArrayOutputStream#writeTo(OutputStream)
     */
    public synchronized void writeTo(final OutputStream out) throws IOException {
        int remaining = count;
        for (final byte[] buf : buffers) {
            final int c = Math.min(buf.length, remaining);
            out.write(buf, 0, c);
            remaining -= c;
            if (remaining == 0) {
                break;
            }
        }
    }

    /**
     * 以字节数组的形式获取此字节流的当前内容。结果与此流无关。
     *
     * @return 此输出流的当前内容，作为字节数组
     * 
     * @see java.io.ByteArrayOutputStream#toByteArray()
     */
    public synchronized byte[] toByteArray() {
        int remaining = count;
        if (remaining == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        final byte newbuf[] = new byte[remaining];
        int pos = 0;
        for (final byte[] buf : buffers) {
            final int c = Math.min(buf.length, remaining);
            System.arraycopy(buf, 0, newbuf, pos, c);
            pos += c;
            remaining -= c;
            if (remaining == 0) {
                break;
            }
        }
        return newbuf;
    }
}
