package org.zy.moonstone.core.interfaces.connector;

import io.netty.buffer.ByteBuf;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description 回调接口能够在缓冲区溢出异常发生时扩展缓冲区或替换缓冲区
 */
public interface ApplicationBufferHandler {
	public void setByteBuf(ByteBuf buffer);

    public ByteBuf getByteBuf();
}
