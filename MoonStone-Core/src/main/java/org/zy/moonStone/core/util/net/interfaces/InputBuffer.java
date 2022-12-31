package org.zy.moonStone.core.util.net.interfaces;

import java.io.IOException;

import org.zy.moonStone.core.interfaces.connector.ApplicationBufferHandler;

/**
 * @dateTime 2022年5月30日;
 * @author zy(azurite-Y);
 * @description 此类仅在协议实现中供内部使用。 应该使用 Request.doRead() 读取所有内容。
 * 
 */
public interface InputBuffer {
	 /**
     * 从输入流中读取ApplicationBufferHandler 提供的ByteBuffer。<br/>
     * 重要提示：当前模型假定协议将“拥有” ByteBuffer 并返回指向它的指针。
     *
     * @param handler - 提供缓冲区以将数据读入的应用程序缓冲区处理程序。
     * @return 已添加到缓冲区的字节数或 -1 表示流结束
     * @throws IOException - 如果从输入流读取时发生 I/O 错误
     */
	@Deprecated
    default  int doRead(ApplicationBufferHandler handler) throws IOException {return 0;};
}
