package org.zy.moonstone.core.http.fileupload;

import org.zy.moonstone.core.exceptions.InvalidFileNameException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description 用于处理流的实用程序类。
 */
public final class Streams {
    /**
     * 私有构造函数，以防止实例化。该类只有静态方法。
     */
    private Streams() {}

    /**
     * 使用于 {@link #copy(InputStream, OutputStream, boolean)} 的默认缓存尺寸
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * 将给定 {@link InputStream} 的内容复制到给定 {@link OutputStream} 的快捷方式
     * <pre>
     *   copy(pInputStream, pOutputStream, new byte[8192]);
     * </pre>
     *
     * @param inputStream - 正在读取的输入流。可以保证在流中调用 {@link InputStream#close()}。
     * @param outputStream - 数据应写入其中的输出流。可能为null，在这种情况下，输入的流内容被简单地丢弃。
     * @param closeOutputStream - true 保证在流上调用 {@link OutputStream#close()}。False表示最后只调用{@link OutputStream#flush()}。
     * @return 已复制的字节数。
     * 
     * @throws IOException - 如果发生I/O错误
     */
    public static long copy(InputStream inputStream, OutputStream outputStream, boolean closeOutputStream) throws IOException {
        return copy(inputStream, outputStream, closeOutputStream, new byte[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * 将给定 {@link InputStream} 的内容复制到给定 {@link OutputStream} 。
     *
     * @param inputStream - 正在读取的输入流。可以保证在流中调用 {@link InputStream#close()}。
     * @param outputStream - 数据应写入其中的输出流。可能为null，在这种情况下，输入的流内容被简单地丢弃。
     * @param closeOutputStream - true 保证在流上调用 {@link OutputStream#close()}。False表示最后只调用{@link OutputStream#flush()}。
     * @param buffer - 临时缓冲区，用于复制数据。
     * @return 已复制的字节数。
     * 
     * @throws IOException - 如果发生I/O错误
     */
    public static long copy(InputStream inputStream, OutputStream outputStream, boolean closeOutputStream, byte[] buffer) throws IOException {
        OutputStream out = outputStream;
        InputStream in = inputStream;
        try {
            long total = 0;
            for (;;) {
                int res = in.read(buffer);
                if (res == -1) {
                    break;
                }
                if (res > 0) {
                    total += res;
                    if (out != null) {
                        out.write(buffer, 0, res);
                    }
                }
            }
            if (out != null) {
                if (closeOutputStream) {
                    out.close();
                } else {
                    out.flush();
                }
                out = null;
            }
            in.close();
            in = null;
            return total;
        } finally {
            IOUtils.closeQuietly(in);
            if (closeOutputStream) {
                IOUtils.closeQuietly(out);
            }
        }
    }

    /**
     * 允许读取 {@link InputStream }的内容转换为字符串的便捷方法。平台的默认字符编码用于将字节转换为字符。
     *
     * @param inputStream - 要读取的输入流。
     * @return 作为字符串的流内容
     * 
     * @throws IOException - 如果发生I/O错误
     * @see #asString(InputStream, String)
     */
    public static String asString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inputStream, baos, true);
        return baos.toString();
    }

    /**
     * 允许读取 {@link InputStream }的内容转换为字符串的便捷方法，并指定字符编码
     *
     * @param inputStream - 要读取的输入流。
     * @param encoding - 字符编码，通常为“UTF-8”
     * @return 作为字符串的流内容
     * 
     *	@throws IOException - 如果发生I/O错误
     * @see #asString(InputStream)
     */
    public static String asString(InputStream inputStream, String encoding) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inputStream, baos, true);
        return baos.toString(encoding);
    }

    /**
     * 检查给定文件名在某种意义上是否有效，是否不包含任何NUL字符。
     * 如果文件名有效，则返回时不会进行任何修改。否则，将引发 {@link InvalidFileNameException}。
     *
     * @param fileName - 要检查的文件名
     * @return 未修改的文件名（如果有效）
     * @throws InvalidFileNameException - 发现文件名无效
     */
    public static String checkFileName(String fileName) {
        if (fileName != null  &&  fileName.indexOf('\u0000') != -1) {
            // pFileName.replace("\u0000", "\\0")
            final StringBuilder sb = new StringBuilder();
            for (int i = 0;  i < fileName.length();  i++) {
                char c = fileName.charAt(i);
                switch (c) {
                    case 0:
                        sb.append("\\0");
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }
            throw new InvalidFileNameException(fileName,"无效的文件名: " + sb);
        }
        return fileName;
    }
}
