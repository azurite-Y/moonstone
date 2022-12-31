package org.zy.moonStone.core.http.fileupload;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description
 * 当指定字节数的数据写入时触发事件的输出流。例如，该事件可用于在达到最大值时抛出异常，或在超过阈值时切换底层流类型。
 * <p>
 * 这个类覆盖所有OutputStream方法。然而，这些覆盖最终会调用底层输出流实现中的相应方法。
 * <p>
 * 注意:这个实现可能会在实际达到阈值之前触发事件，因为当一个挂起的写操作将导致超过阈值时触发。
 */
public abstract class ThresholdingOutputStream extends OutputStream {
	/**
	 * 事件被触发的阈值
	 */
	protected final int threshold;

	/**
	 * 写入输出流的字节数
	 */
	private long written;

	/**
	 * 是否超过了配置的阈值
	 */
	private boolean thresholdExceeded;


	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 构造该类的实例，该实例将在指定的阈值处触发事件。
	 *
	 * @param threshold - 触发事件的字节数。
	 */
	public ThresholdingOutputStream(final int threshold) {
		this.threshold = threshold;
	}


	// -------------------------------------------------------------------------------------
	// OutputStream methods
	// -------------------------------------------------------------------------------------
	/**
	 * 将指定的字节流写入此输出流。
	 *
	 * @param b - 要写入的字节。
	 * @throws IOException - 如果发生错误。
	 */
	@Override
	public void write(final int b) throws IOException {
		checkThreshold(1);
		getStream().write(b);
		written++;
	}


	/**
	 * 将 <code>b.length</code> 字节从指定的字节数组写入此输出流。
	 *
	 * @param b - 要写入的字节数组。
	 * @throws IOException - 如果发生错误。
	 */
	@Override
	public void write(final byte b[]) throws IOException {
		checkThreshold(b.length);
		getStream().write(b);
		written += b.length;
	}


	/**
	 * 将指定字节数组中以<code>off</code>开始的 <code>len</code> 字节写入此输出流。
	 *
	 * @param b - 将从中写入数据的字节数组。
	 * @param off - 字节数组中的起始偏移量。
	 * @param len - 写入的字节数。
	 *
	 * @throws IOException - 如果发生错误。
	 */
	@Override
	public void write(final byte b[], final int off, final int len) throws IOException {
		checkThreshold(len);
		getStream().write(b, off, len);
		written += len;
	}


	/**
	 * 刷新此输出流并强制写入所有缓冲的输出字节。
	 *
	 * @throws IOException - 如果发生错误。
	 */
	@Override
	public void flush() throws IOException {
		getStream().flush();
	}


	/**
	 * 关闭此输出流并释放与此流相关的任何系统资源。
	 *
	 * @throws IOException - 如果发生错误。
	 */
	@Override
	public void close() throws IOException {
		try {
			flush();
		} catch (final IOException ignored) {
			// ignore
		}
		getStream().close();
	}


	// -------------------------------------------------------------------------------------
	// public methods
	// -------------------------------------------------------------------------------------
	/**
	 * 确定是否已超过此输出流的配置阈值。
	 *
	 * @return 如果已达到阈值则为true;否则false。
	 */
	public boolean isThresholdExceeded() {
		return written > threshold;
	}


	// -------------------------------------------------------------------------------------
	// protected methods
	// -------------------------------------------------------------------------------------
	/**
	 * 检查写入指定的字节数是否会导致超过配置的阈值。如果是，则触发一个事件，以允许具体实现对此采取行动。
	 *
	 * @param count - 即将写入底层输出流的字节数。
	 * @throws IOException - 如果发生错误。
	 */
	protected void checkThreshold(final int count) throws IOException {
		if (!thresholdExceeded && written + count > threshold) {
			thresholdExceeded = true;
			thresholdReached();
		}
	}


	// -------------------------------------------------------------------------------------
	// Abstract methods
	// -------------------------------------------------------------------------------------
	/**
	 * 返回底层输出流，该类中的相应 <code>OutputStream</code> 方法最终将委托给该输出流。
	 *
	 * @return 底层输出流。
	 * @throws IOException - 如果发生错误。
	 */
	protected abstract OutputStream getStream() throws IOException;


	/**
	 * 指示已达到配置的阈值，子类应该对该事件采取任何必要的操作。这可能包括更改底层输出流。
	 *
	 * @throws IOException - 如果发生错误。
	 */
	protected abstract void thresholdReached() throws IOException;
}
