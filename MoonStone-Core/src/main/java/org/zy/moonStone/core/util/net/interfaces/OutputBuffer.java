package org.zy.moonStone.core.util.net.interfaces;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @dateTime 2022年5月30日;
 * @author zy(azurite-Y);
 * @description 输出缓冲区。此类由协议实现在内部使用。 所有来自更高级别代码的写入都应该通过 Response.doWrite() 发生。
 */
public interface OutputBuffer {
	/**
	 * 将给定的数据写入响应。
	 * @param chunk - 要写入的数据
	 * @return 写入的字节数可能少于输入块中的可用字节数
	 * @throws IOException - 发生基础 I/O 错误
	 */
	public int doWrite(ByteBuffer chunk) throws IOException;

	/**
	 * 写入底层套接字的字节数。 这包括分块、压缩等的影响。
	 * @return  Bytes - 为当前请求而写
	 */
	public long getBytesWritten();
}
