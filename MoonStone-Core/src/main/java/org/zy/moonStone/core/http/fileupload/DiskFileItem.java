package org.zy.moonStone.core.http.fileupload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.zy.moonStone.core.exceptions.FileUploadException;
import org.zy.moonStone.core.exceptions.InvalidFileNameException;
import org.zy.moonStone.core.interfaces.http.fileupload.FileItem;
import org.zy.moonStone.core.interfaces.http.fileupload.FileItemHeaders;
import org.zy.moonStone.core.util.http.parser.HttpParser;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description
 * FileItem接口的默认实现。在从FileUpload实例中检索该类的实例后，您可以使用get()一次性请求文件的所有内容，
 * 或者使用getInputStream()请求InputStream并处理文件，而不尝试将其加载到内存中，这对于大型文件来说可能很方便。
 * <p>
 * 为文件项创建的临时文件应该稍后删除。
 * 
 * @see FileUpload.parseRequest(RequestContext)
 */
public class DiskFileItem implements FileItem {
	/**
	 * 当发送方没有提供显式的字符参数时使用的默认内容字符集。
	 * 当通过HTTP接收时，“text”类型的媒体子类型被定义为具有一个默认的字符集值“ISO-8859-1”。
	 */
	public static final String DEFAULT_CHARSET = "ISO-8859-1";

	/**
	 * 在惟一文件名生成中使用的UID
	 */
	private static final String UID = UUID.randomUUID().toString().replace('-', '_');

	/**
	 * 在惟一标识符生成中使用的计数器。
	 */
	private static final AtomicInteger COUNTER = new AtomicInteger(0);

	/**
	 * 浏览器提供的表单字段的名称。
	 */
	private String fieldName;

	/**
	 * 由浏览器传递的内容类型，如果没有定义则为空。
	 */
	private final String contentType;

	/**
	 * 该项是否为简单表单字段。
	 */
	private boolean isFormField;

	/**
	 * 用户文件系统中的原始文件名。
	 */
	private final String fileName;

	/**
	 * 项的大小，以字节为单位。这用于在文件项从其原始位置移动时缓存其大小。
	 */
	private long size = -1;

	/**
	 * 高于该阈值的上传将存储在磁盘上。
	 */
	private final int sizeThreshold;

	/**
	 * 如果存储在磁盘上，则存储上传文件的目录。
	 */
	private final File repository;

	/**
	 * 文件的缓存内容。
	 */
	private byte[] cachedContent;

	/**
	 * 上传文件的输出流
	 */
	private transient DeferredFileOutputStream dfos; 

	/**
	 * 要使用的临时文件。
	 */
	private transient File tempFile;

	/**
	 * 表单项头
	 */
	private FileItemHeaders headers;

	/**
	 * 当发送方没有提供显式的字符参数时使用的默认内容字符集。
	 */
	private String defaultCharset = DEFAULT_CHARSET;


	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 构造一个新的 <code>DiskFileItem</code> 实例
	 *
	 * @param fieldName - 表单字段的名称
	 * @param contentType - 浏览器传递的内容类型或null(如果未指定)。
	 * @param isFormField - 该项是否为普通表单字段，而不是文件上传。
	 * @param fileName - 用户文件系统中的原始文件名，如果未指定则为空。
	 * @param sizeThreshold - 阈值(以字节为单位)，低于该阈值的项将保存在内存中，高于该阈值的项将作为文件存储。
	 * @param repository - 数据存储库是在项目大小超过阈值时将在其中创建文件的目录。
	 */
	public DiskFileItem(String fieldName, String contentType, boolean isFormField, String fileName, int sizeThreshold, File repository) {
		this.fieldName = fieldName;
		this.contentType = contentType;
		this.isFormField = isFormField;
		this.fileName = fileName;
		this.sizeThreshold = sizeThreshold;
		this.repository = repository;
	}


	// -------------------------------------------------------------------------------------
	// javax.activation.DataSource Methods
	// -------------------------------------------------------------------------------------
	/**
	 * 返回一个可用于检索文件内容的InputStream。
	 *
	 * @return 一个可用于检索文件内容的InputStream
	 * @throws IOException - 如果发生错误
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		if (!isInMemory()) {
			return new FileInputStream(dfos.getFile());
		}

		if (cachedContent == null) {
			cachedContent = dfos.getData();
		}
		return new ByteArrayInputStream(cachedContent);
	}

	
	/**
	 * 返回由代理传递的内容类型，如果未定义则返回null。
	 *
	 * @return 由代理传递的内容类型，如果未定义则为空。
	 */
	@Override
	public String getContentType() {
		return contentType;
	}

	
	/**
	 * @return 由代理传递的内容字符集，如果没有定义则为null。
	 */
	public String getCharSet() {
		Map<String, String> params = HttpParser.parseSeparator(getContentType(), ";", "=");
		return params.get("charset");
	}

	
	/**
	 *
	 * @return 客户端文件系统中的原始文件名。
	 * @throws InvalidFileNameException - 文件名包含NUL字符，这可能是安全攻击的指示。
	 * 如果打算无论如何都使用文件名，请捕获异常并使用{@link InvalidFileNameException#getName()}.
	 */
	@Override
	public String getName() {
		return Streams.checkFileName(fileName);
	}


	// -------------------------------------------------------------------------------------
	// FileItem methods
	// -------------------------------------------------------------------------------------
	/**
	 * 提供是否从内存读取文件内容的提示。
	 *
	 * @return 如果文件内容将从存储器中读取，则为 <code>true</code>；否则为  <code>false</code>。
	 */
	@Override
	public boolean isInMemory() {
		if (cachedContent != null) {
			return true;
		}
		return dfos.isInMemory();
	}

	
	/**
	 * 返回文件的大小
	 *
	 * @return 文件的大小，以字节为单位。
	 */
	@Override
	public long getSize() {
		if (size >= 0) {
			return size;
		} else if (cachedContent != null) {
			return cachedContent.length;
		} else if (dfos.isInMemory()) {
			return dfos.getData().length;
		} else {
			return dfos.getFile().length();
		}
	}

	
	/**
	 * 以字节数组的形式返回文件的内容。如果文件的内容尚未缓存在内存中，它们将从磁盘存储中加载并缓存。
	 *
	 * @return 文件的内容是一个字节数组，如果数据无法读取则为null
	 */
	@Override
	public byte[] get() {
		if (isInMemory()) {
			if (cachedContent == null && dfos != null) {
				cachedContent = dfos.getData();
			}
			return cachedContent;
		}

		byte[] fileData = new byte[(int) getSize()];
		InputStream fis = null;

		try {
			fis = new FileInputStream(dfos.getFile());
			IOUtils.readFully(fis, fileData);
		} catch (IOException e) {
			fileData = null;
		} finally {
			IOUtils.closeQuietly(fis);
		}

		return fileData;
	}

	
	@Override
	public String getString(final String charset) throws UnsupportedEncodingException {
		return new String(get(), charset);
	}

	
	@Override
	public String getString() {
		byte[] rawdata = get();
		String charset = getCharSet();
		if (charset == null) {
			charset = defaultCharset;
		}
		try {
			return new String(rawdata, charset);
		} catch (UnsupportedEncodingException e) {
			return new String(rawdata);
		}
	}

	
	/**
	 * 一种将上传的项写入磁盘的方便方法。
	 * 客户端代码不关心项目是否存储在内存中，还是存储在磁盘上的临时位置。他们只是想把上传的条目写入一个文件。
	 * <p>
	 * 这个实现首先尝试将上传的项重命名为指定的目标文件，如果该项最初被写入磁盘。否则，数据将被复制到指定的文件。
	 * <p>
	 * 此方法只保证工作一次，即第一次为特定项调用它。
	 * 这是因为，如果该方法重命名了一个临时文件，那么该文件在以后将无法再被复制或重命名。
	 */
	@Override
	public void write(File file) throws Exception {
		if (isInMemory()) {
			FileOutputStream fout = null;
			try {
				fout = new FileOutputStream(file);
				fout.write(get());
				fout.close();
			} finally {
				IOUtils.closeQuietly(fout);
			}
		} else {
			File outputFile = getStoreLocation();
			if (outputFile != null) {
				// 保存文件长度
				size = outputFile.length();
				// 上传的文件被存储在磁盘上的临时位置，因此将其移动到所需的文件
				if (file.exists()) {
					if (!file.delete()) {
						throw new FileUploadException("无法将上传的文件写入磁盘!");
					}
				}
				if (!outputFile.renameTo(file)) {
					BufferedInputStream in = null;
					BufferedOutputStream out = null;
					try {
						in = new BufferedInputStream(new FileInputStream(outputFile));
						out = new BufferedOutputStream(new FileOutputStream(file));
						IOUtils.copy(in, out);
						out.close();
					} finally {
						IOUtils.closeQuietly(in);
						IOUtils.closeQuietly(out);
					}
				}
			} else {
				// 不管出于什么原因，无法将文件写入磁盘
				throw new FileUploadException("无法将上传的文件写入磁盘!");
			}
		}
	}
	

	@Override
	public void delete() {
		cachedContent = null;
		File outputFile = getStoreLocation();
		if (outputFile != null && !isInMemory() && outputFile.exists()) {
			outputFile.delete();
		}
	}
	

	@Override
	public String getFieldName() {
		return fieldName;
	}

	
	@Override
	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	
	/**
	 * @see #setFormField(boolean)
	 */
	@Override
	public boolean isFormField() {
		return isFormField;
	}

	
	/**
	 * @see #isFormField()
	 */
	@Override
	public void setFormField(boolean state) {
		isFormField = state;
	}

	
	@Override
	public OutputStream getOutputStream() throws IOException {
		if (dfos == null) {
			File outputFile = getTempFile();
			dfos = new DeferredFileOutputStream(sizeThreshold, outputFile);
		}
		return dfos;
	}

	
	// -------------------------------------------------------------------------------------
	// 公开 methods
	// -------------------------------------------------------------------------------------
	/**
	 * 返回 {@link java.io.File} 对象，获取 <code>FileItem</code> 数据在磁盘上的临时位置。
	 * 注意，对于数据存储在内存中的fileitem，此方法将返回null。
	 * 在处理大文件时，如果源位置和目标位置位于同一个逻辑卷中，
	 * 则可以使用 {@link java.io.File#renameTo(java.io.File)} 将文件移动到新的位置而不复制数据。
	 *
	 * @return 数据文件，如果数据存储在内存中则为 <code>null</code>。
	 */
	public File getStoreLocation() {
		if (dfos == null) {
			return null;
		}
		if (isInMemory()) {
			return null;
		}
		return dfos.getFile();
	}

	
	// -------------------------------------------------------------------------------------
	// 保护 methods
	// -------------------------------------------------------------------------------------
	/**
	 * 从临时存储中删除文件内容。
	 */
	@Override
	protected void finalize() {
		if (dfos == null || dfos.isInMemory()) {
			return;
		}
		File outputFile = dfos.getFile();

		if (outputFile != null && outputFile.exists()) {
			outputFile.delete();
		}
	}

	
	/**
	 * 创建并返回一个 {@link java.io.File File}，该File表示在配置的存储库路径中唯一命名的临时文件。
	 * 文件的生存期与FileItem实例的生存期绑定;当实例被垃圾回收时，该文件将被删除。
	 * <p>
	 * <b>Note: 覆盖此方法的子类必须确保每次返回相同的File</b>
	 *
	 * @return 用于临时存储的 {@link java.io.File File}
	 */
	protected File getTempFile() {
		if (tempFile == null) {
			File tempDir = repository;
			if (tempDir == null) {
				tempDir = new File(System.getProperty("java.io.tmpdir"));
			}

			String tempFileName = null;
			if (isFormField) {
				tempFileName = String.format("upload_%s_%s.tmp", UID, getUniqueId());
			} else {
				tempFileName = String.format("upload_tmp_%s_%s_%s", UID, getUniqueId(), this.fileName);
			}

			tempFile = new File(tempDir, tempFileName);
		}
		return tempFile;
	}


	@Override
	public FileItemHeaders getHeaders() {
		return headers;
	}
	

	@Override
	public void setHeaders(FileItemHeaders pHeaders) {
		headers = pHeaders;
	}

	
	/**
	 * 当发送方没有提供显式的字符集参数时，返回使用的默认字符集。
	 * @return 默认字符集
	 */
	public String getDefaultCharset() {
		return defaultCharset;
	}

	
	/**
	 * 设置默认字符集，以便在发送方没有提供显式字符集参数时使用。
	 * @param charset - 默认字符集
	 */
	public void setDefaultCharset(String charset) {
		defaultCharset = charset;
	}

	
	// -------------------------------------------------------------------------------------
	// 私有 methods
	// -------------------------------------------------------------------------------------
	/**
	 * 返回一个标识符，该标识符在用于加载此类的类加载器中是唯一的，但不具有随机的外观。
	 *
	 * @return 具有非随机外观的实例标识符的字符串。
	 */
	private static String getUniqueId() {
		final int limit = 100000000;
		int current = COUNTER.getAndIncrement();
		String id = Integer.toString(current);

		// 如果获得了超过ID数值超过1亿，那么您将开始获得超过8个字符的ID。
		if (current < limit) {
			id = ("00000000" + id).substring(id.length());
		}
		return id;
	}

	
	@Override
	public String toString() {
        return String.format("name=%s, StoreLocation=%s, size=%s bytes, isFormField=%s, FieldName=%s, defaultCharset=%s",
                getName(), getStoreLocation(), Long.valueOf(getSize()),
                Boolean.valueOf(isFormField()), getFieldName(), defaultCharset);
	}
}
