package org.zy.moonstone.core.util.buf;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description ByteBuffer 的简单包装器，它记住缓冲区是否已切换读模式
 */
public class ByteBufferHolder {
	private final ByteBuffer buf;
	private final AtomicBoolean flipped;

	public ByteBufferHolder(ByteBuffer buf, boolean flipped) {
		this.buf = buf;
		this.flipped = new AtomicBoolean(flipped);
	}


	public ByteBuffer getBuf() {
		return buf;
	}


	public boolean isFlipped() {
		return flipped.get();
	}


	public boolean flip() {
		if (flipped.compareAndSet(false, true)) {
			buf.flip();
			return true;
		} else {
			return false;
		}
	}
}
