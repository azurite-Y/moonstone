package org.zy.moonStone.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 包含常用的I/O相关方法
 */
public class IOTools {
	// 4k
	protected static final int DEFAULT_BUFFER_SIZE=4*1024;
	
	/**
     * 从读取器读取输入，然后写入写入器，直到读取器不再有输入.
     *
     */
    public static void flow( Reader reader, Writer writer, char[] buf ) throws IOException {
        int numRead;
        while ( (numRead = reader.read(buf) ) >= 0) {
            writer.write(buf, 0, numRead);
        }
    }

    /**
     * 从读取器读取输入，然后写入写入器，直到读取器不再有输入.
     *
     * @see #flow( Reader, Writer, char[] )
     */
    public static void flow(Reader reader, Writer writer) throws IOException {
        char[] buf = new char[DEFAULT_BUFFER_SIZE];
        flow( reader, writer, buf );
    }


    /**
     * 使用默认大小(4kB)的新缓冲区从输入流读取输入并写入输出流，直到输入流没有更多的输入.
     *
     * @param is - 输入流要读取的输入流.
     * @param os - 输出流要写入的输出流.
     * @throws IOException - 如果复制过程中发生I/O错误
     */
    public static void flow(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int numRead;
        while ( (numRead = is.read(buf) ) >= 0 ) {
            if (os != null) {
                os.write(buf, 0, numRead);
            }
        }
    }
}
