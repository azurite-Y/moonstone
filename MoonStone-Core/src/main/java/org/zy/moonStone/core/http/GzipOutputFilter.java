package org.zy.moonStone.core.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.util.net.interfaces.HttpOutputBuffer;

/**
 * @dateTime 2022年12月6日;
 * @author zy(azurite-Y);
 * @description
 */
public class GzipOutputFilter implements OutputFilter {
	protected static final Logger logger = LoggerFactory.getLogger(GzipOutputFilter.class);

	/**
	 * 调用链中的下一个 {@link HttpOutputBuffer }
	 */
	protected HttpOutputBuffer httpOutputBuffer;

	/**
	 * 压缩数据输出流
	 */
	protected GZIPOutputStream compressionStream = null;

	/**
	 * 假内部输出流，接收 {@link #compressionStream} 的数据，并将数据写入到 {@link #httpOutputBuffer }
	 */
	protected final OutputStream fakeOutputStream = new FakeOutputStream();


	@Override
	public int doWrite(ByteBuffer chunk) throws IOException {
		if (compressionStream == null) {
			compressionStream = new GZIPOutputStream(fakeOutputStream, true);
		}
		int len = chunk.remaining();
		if (logger.isDebugEnabled()) {
			logger.debug("Gzip Compress. original: {}", len);
		}
		
		if (chunk.hasArray()) {
			compressionStream.write(chunk.array(), chunk.arrayOffset() + chunk.position(), len);
		} else {
			byte[] bytes = new byte[len];
			chunk.put(bytes);
			compressionStream.write(bytes, 0, len);
		}
		return len;
	}

	@Override
	public void end() throws IOException {
		if (compressionStream == null) {
			compressionStream = new GZIPOutputStream(fakeOutputStream, true);
		}
		compressionStream.finish();
		compressionStream.close();
		httpOutputBuffer.end();
	}

	@Override
	public void flush() throws IOException {
		if (compressionStream != null) {
            try {
                if (logger.isDebugEnabled()) {
                	logger.debug("刷新压缩流");
                }
                compressionStream.flush();
            } catch (IOException e) {
                if (logger.isDebugEnabled()) {
                	logger.debug("在刷新gzip过滤器时忽略异常", e);
                }
            }
        }
        httpOutputBuffer.flush();
	}
	
	@Override
	public long getBytesWritten() {
		return httpOutputBuffer.getBytesWritten();
	}

	@Override
	public void setResponse(Response response) {
		// 在此过滤器中不需要来自响应的参数
	}

	@Override
	public void recycle() {
        compressionStream = null;
	}

	@Override
	public void setHttpOutputBuffer(HttpOutputBuffer buffer) {
		this.httpOutputBuffer = buffer;
	}

	protected class FakeOutputStream extends OutputStream {
		protected final ByteBuffer outputChunk = ByteBuffer.allocate(1);
		protected long writeByteLength = 0;
		
		@Override
		public void write(int b) throws IOException {
			// 不应该用于良好的性能，但是需兼容 Sun JDK 1.4.0
			outputChunk.put(0, (byte) (b & 0xff));
			writeByteLength += httpOutputBuffer.doWrite(outputChunk);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			writeByteLength += httpOutputBuffer.doWrite(ByteBuffer.wrap(b, off, len));
		}

		@Override
		public void flush() throws IOException {
			if (logger.isDebugEnabled()) {
				logger.debug("Gzip Compress. compressional: {}", writeByteLength);
			}
		}

		@Override
		public void close() throws IOException {}
	}
}
