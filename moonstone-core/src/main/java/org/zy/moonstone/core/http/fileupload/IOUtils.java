package org.zy.moonstone.core.http.fileupload;

import java.io.*;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description
 * 
 * 常规IO流操作实用程序。
 * <p>
 * 此类为输入/输出操作提供静态实用程序方法。
 * <ul>
 * <li>closeQuietly - 这些方法关闭流，忽略null和异常
 * <li>toXxx/read - 这些方法从流中读取数据
 * <li>write - 这些方法将数据写入流
 * <li>copy - 这些方法将所有数据从一个流复制到另一个流
 * <li>contentEquals - 这些方法比较两个流的内容
 * </ul>
 * <p>
 * 字节到字符方法和字符到字节方法涉及转换步骤。在每种情况下都提供了两种方法，一种使用平台默认编码，另一种允许您指定编码。
 * 鼓励您始终指定编码，因为依赖平台默认值可能会导致意外的结果，例如从开发转移到生产时。
 * <p>
 * 此类中读取流的所有方法都在内部缓冲。这意味着没有理由使用 <code>BufferedInputStream</code> 或 <code>BufferedReader</code> 。
 * 默认的缓冲区大小4K在测试中被证明是有效的。
 * <p>
 * 只要可能，此类中的方法不会刷新或关闭流。这是为了避免对流的来源和进一步使用做出不可移植的假设。
 */
public class IOUtils {
	/**
	 * 表示文件（或流）的结尾。
     */
    public static final int EOF = -1;


    /**
     * 用于复制大型（InputStream、OutputStream）的默认缓冲区大小（4096）
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    
    
	/**
	 * 无条件关闭 <code>Closeable</code>
     * <p>
     * 相当于 {@link Closeable#close()} ，只是任何异常都会被忽略。这通常用于finally块。
     * <p>
     * Example code:
     * </p>
     * <pre>
     * Closeable closeable = null;
     * try {
     *     closeable = new FileReader(&quot;foo.txt&quot;);
     *     // process closeable
     *     closeable.close();
     * } catch (Exception e) {
     *     // error handling
     * } finally {
     *     IOUtils.closeQuietly(closeable);
     * }
     * </pre>
     * <p>
     * Closing all streams:
     * </p>
     * <pre>
     * try {
     *     return IOUtils.copy(inputStream, outputStream);
     * } finally {
     *     IOUtils.closeQuietly(inputStream);
     *     IOUtils.closeQuietly(outputStream);
     * }
     * </pre>
     *
     * @param closeable the objects to close, may be null or already closed
     * @since 2.0
     */
    public static void closeQuietly(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// copy from InputStream
	// -------------------------------------------------------------------------------------
    /**
     * 将字节从InputStream复制到OutputStream。
     * <p>
     * 该方法在内部缓冲输入，因此不需要使用BufferedInputStream。
     * <p>
     * 大的流(超过2GB)在拷贝完成后将返回一个字节拷贝值-1，因为正确的字节数不能作为int返回。
     * 对于较大的流，使用copyLarge(InputStream, OutputStream)方法。
     * 
     * @param input - 要读取的InputStream
     * @param output - 要写入的OutputStream
     * @return 复制的字节数，如果 &gt; Integer.MAX_VALUE，则为-1
     * 
     * @throws NullPointerException - 如果输入或输出为空
     * @throws IOException - 如果发生 I/O 错误
     * @since 1.1
     */
    public static int copy(final InputStream input, final OutputStream output) throws IOException {
        final long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }
    

    /**
     * 将字节从大(超过2GB)的InputStream复制到OutputStream。
     * <p>
     * 该方法在内部缓冲输入，因此不需要使用BufferedInputStream。
     * <p>
     * 缓冲区大小由 {@link #DEFAULT_BUFFER_SIZE} 给出。
     *
     * @param input - 要读取的InputStream
     * @param output - 要写入的OutputStream
     * @return 复制的字节数，如果 &gt; Integer.MAX_VALUE，则为-1
     * 
     * @throws NullPointerException - 如果输入或输出为空
     * @throws IOException - 如果发生 I/O 错误
     */
    public static long copyLarge(final InputStream input, final OutputStream output) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
    

    /**
     * 从输入流读取字节。这个实现保证了它在放弃之前将读取尽可能多的字节;对于 {@link InputStream} 的子类来说，情况可能并不总是这样。
     *
     * @param input - 从哪里读取输入
     * @param buffer - 目标
     * @param offset - 初始偏移量到缓冲区
     * @param length - 要读取的长度，必须 &gt;= 0
     * @return 实际读取长度;如果达到EOF，是否会少于要求
     * 
     * @throws IOException - 如果发生读错误
     */
    public static int read(final InputStream input, final byte[] buffer, final int offset, final int length) throws IOException {
        if (length < 0) {
            throw new IllegalArgumentException("length 不能为负值: " + length);
        }
        int remaining = length;
        while (remaining > 0) {
            final int location = length - remaining;
            final int count = input.read(buffer, offset + location, remaining);
            if (EOF == count) { // EOF
                break;
            }
            remaining -= count;
        }
        return length - remaining;
    }

    
    /**
     * 读取所请求的字节数，如果没有足够的字节则失败。
     * <p>
     * 这允许 {@link InputStream#read(byte[], int, int)} 可能读不到要求的字节数(很可能是因为到达EOF)。
     *
     * @param input - 从哪里读取输入
     * @param buffer - 目标
     *
     * @throws IOException - 如果读取文件时出现问题
     * @throws IllegalArgumentException - 如果长度是负的
     * @throws EOFException - 如果读取的字节数不正确
     */
    public static void readFully(final InputStream input, final byte[] buffer) throws IOException {
        readFully(input, buffer, 0, buffer.length);
    }
    
    
    /**
     * 读取所请求的字节数，如果没有足够的字节则失败。
     * <p>
     * 这允许 {@link InputStream#read(byte[], int, int)} 可能读不到要求的字节数(很可能是因为到达EOF)。
     *
     * @param input - 从哪里读取输入
     * @param buffer - 目标
     * @param offset - 缓冲区的初始偏移量
     * @param length - 要读取的长度，必须 &gt;= 0
     *
     * @throws IOException - 如果读取文件时出现问题
     * @throws IllegalArgumentException - 如果长度是负的
     * @throws EOFException - 如果读取的字节数不正确
     */
    public static void readFully(final InputStream input, final byte[] buffer, final int offset, final int length) throws IOException {
        final int actual = read(input, buffer, offset, length);
        if (actual != length) {
            throw new EOFException("目标读取长度: " + length + " 实际: " + actual);
        }
    }
}
