package org.zy.moonStone.core.http.fileupload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.http.Request;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description 
 * 一种输出流，它将数据保留在内存中，直到达到指定的阈值，然后才将数据提交到磁盘。如果流在达到阈值之前关闭，则数据根本不会写入磁盘。
 * <p>
 * 若事先不知道要上传的文件的大小。如果文件很小，希望将其存储在内存中(为了效率)，但如果文件很大，希望将其存储到文件中(以避免内存问题)。
 */
public class DeferredFileOutputStream extends ThresholdingOutputStream {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);

	/**
	 * 在达到阈值之前数据将被写入的输出流。
	 */
	private ByteArrayOutputStream memoryOutputStream;

	/**
	 * 在任何给定时间将写入数据的输出流。这将始终是内存 <code>OutputStream</code> 或磁盘 <code>OutputStream</code> 之一。
	 */
	private OutputStream currentOutputStream;

	/**
	 * 如果超过阈值，输出将指向的文件。
	 */
	private File outputFile;

	/**
	 * 临时文件前缀。
	 */
	private final String prefix;

	/**
	 * 临时文件后缀
	 */
	private final String suffix;

	/**
	 * 用于临时文件的目录。
	 */
	private final File directory;

	/** 基础输出流数据是否已写入磁盘 */
	private boolean reached;
	
	// -------------------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------------------
	/**
	 * 构造该类的实例，该实例将在指定的阈值处触发事件，并将数据保存到超过该阈值的文件中。
	 * 初始缓冲区大小将默认为1024字节，这是ByteArrayOutputStream的默认缓冲区大小。
	 *
	 * @param threshold - 触发事件的字节数。
	 * @param outputFile - 数据保存超过阈值的文件。
	 */
	public DeferredFileOutputStream(final int threshold, final File outputFile) {
		this(threshold, outputFile, null, null, null, ByteArrayOutputStream.DEFAULT_SIZE);
	}

	/**
	 * 构造该类的实例，该实例将在指定的阈值处触发事件，并将数据保存到超过该阈值的文件中。
	 *
	 * @param threshold - 触发事件的字节数。
	 * @param outputFile - 数据保存超过阈值的文件
	 * @param prefix - 用于临时文件的前缀
	 * @param suffix - 用于临时文件的后缀            
	 * @param directory - 临时文件目录
	 * @param initialBufferSize - 内存缓冲区的初始大小
	 */
	private DeferredFileOutputStream(final int threshold, final File outputFile, final String prefix,
			final String suffix, final File directory, final int initialBufferSize) {
		super(threshold);
		this.outputFile = outputFile;
		this.prefix = prefix;
		this.suffix = suffix;
		this.directory = directory;

		memoryOutputStream = new ByteArrayOutputStream(initialBufferSize);
		currentOutputStream = memoryOutputStream;
	}


	// -------------------------------------------------------------------------------------
	// ThresholdingOutputStream methods
	// -------------------------------------------------------------------------------------
	/**
	 * 返回当前输出流。这可能是基于内存的，也可能是基于磁盘的，具体取决于相对于阈值的当前状态。
	 *
	 * @return 底层输出流
	 *
	 * @throws IOException - 如果发生错误
	 */
	@Override
	protected OutputStream getStream() throws IOException {
		return currentOutputStream;
	}

	/**
	 * 将基础输出流从基于内存的流切换到磁盘支持的流。
	 * 在这一点上意识到写入的数据太多，无法保存在内存中，因此选择切换到基于磁盘的存储。
	 *
	 * @throws IOException - 如果发生错误
	 */
	@Override
	protected void thresholdReached() throws IOException {
		if (prefix != null) {
			outputFile = File.createTempFile(prefix, suffix, directory);
		}
		FileUtils.forceMkdirParent(outputFile);
		final FileOutputStream fos = new FileOutputStream(outputFile, false);
		try {
			memoryOutputStream.writeTo(fos);
		} catch (final IOException e) {
			fos.close();
			throw e;
		}
		reached = true;
		if (logger.isDebugEnabled()) {
			logger.debug("提交文件数据量超过阈值, 缓存到磁盘中. threshold: {}, outputFile: {}", threshold, outputFile.getPath());
		}
		// 切换输出流
		currentOutputStream = fos;
		memoryOutputStream = null;
	}


	// -------------------------------------------------------------------------------------
	// Public methods
	// -------------------------------------------------------------------------------------
	/**
	 * 确定此输出流的数据是否已保留在内存中
	 *
	 * @return 如果数据在内存中可用，则为 {@code true}；否则为 {@code false}
	 */
	public boolean isInMemory() {
		return !isThresholdExceeded();
	}

	/**
	 * 假设数据已保留在内存中，则将此输出流的数据作为字节数组返回。如果数据已写入磁盘，则此方法返回null。
	 *
	 * @return 此输出流的数据，如果没有可用的数据，则为空。
	 */
	public byte[] getData() {
		if (memoryOutputStream != null) {
			return memoryOutputStream.toByteArray();
		}
		return null;
	}

	/**
	 * 返回构造函数中指定的输出文件或创建的临时文件或null。
	 * <p>
	 * 如果使用了指定文件的构造函数，则即使尚未达到阈值，它也会返回相同的输出文件。
	 * <p>
	 * 如果使用指定临时文件前缀/后缀的构造函数，则返回达到阈值后创建的临时文件。如果未达到阈值，则返回null。
	 *
	 * @return 此输出流的文件，如果不存在此类文件，则为{@code null}。
	 */
	public File getFile() {
		return outputFile;
	}

	/**
	 * 关闭底层输出流，并将其标记为已关闭
	 *
	 * @throws IOException - 如果发生错误
	 */
	@Override
	public void close() throws IOException {
		super.close();
	}
	
	@Override
	public String toString() {
		String temp = null;
		if (reached) {
			try {
				temp = String.format(" [数据超限，写入磁盘位置: %s]", getFile().getCanonicalPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			temp = String.format(" [数据寥寥，内存存储字符: %s]", new String(getData()));
		}
		return temp;
	}
}
