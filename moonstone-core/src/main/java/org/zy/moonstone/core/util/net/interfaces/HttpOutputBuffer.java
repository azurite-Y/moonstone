package org.zy.moonstone.core.util.net.interfaces;

import java.io.IOException;

/**
 * @dateTime 2022年7月26日;
 * @author zy(azurite-Y);
 * @description
 */
public interface HttpOutputBuffer extends OutputBuffer {
	/**
	 * 完成当前响应的写入。在这个方法的执行过程中使用 {@link #doWrite(java.nio.ByteBuffer)} 写入额外字节字节是可以接受的。
     *
     * @throws IOException - 如果在写入客户端时发生I/O错误
     */
    public void end() throws IOException;

    /**
     * 将任何未写入的数据刷新到客户端。
     *
     * @throws IOException - 如果在写入客户端时发生I/O错误
     */
    public void flush() throws IOException;
}
