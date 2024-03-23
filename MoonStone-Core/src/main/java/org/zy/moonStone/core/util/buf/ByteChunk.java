package org.zy.moonstone.core.util.buf;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @dateTime 2022年5月25日;
 * @author zy(azurite-Y);
 * @description 类用于表示一个字节块，以及操作字节[]。缓冲区可以修改并用于输入和输出。
 * 有 2 种模式：块可以与接收器相关联 - ByteInputChannel 或 ByteOutputChannel，它将 当缓冲区为空（输入）或填充（输出）时使用。 
 * 对于输出，它也可以增长。 此操作模式通过调用 setLimit() 或 allocate(initial, limit) 来选择，limit != -1。定义了各种搜索和追加字节的方法，与String和stringbuffer类似。
 * 它允许直接在接收到的字节上处理 http 请求头，而无需转换为字符和字符串，直到需要字符串。 此外，字符集是稍后从请求头或代码中确定的
 * 
 */
public class ByteChunk extends AbstractChunk {
	private static final long serialVersionUID = -9202729002174466620L;

	/**
	 * 用于转换为字符串的默认编码
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	private transient Charset charset;

	private byte[] buff;

	@SuppressWarnings("unused")
	private transient ByteInputChannel in = null;
	private transient ByteOutputChannel out = null;

	/**
	 * 创建一个新的、未初始化的 ByteChunk 对象。
	 */
	public ByteChunk() {}

	public ByteChunk(int initial) {
		allocate(initial, -1);
	}

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
		oos.writeUTF(getCharset().name());
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
		this.charset = Charset.forName(ois.readUTF());
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	@Override
	public void recycle() {
		super.recycle();
		charset = null;
	}

	public void allocate(int initial, int limit) {
		if (buff == null || buff.length < initial) {
			buff = new byte[initial];
		}
		setLimit(limit);
		start = 0;
		end = 0;
		isSet = true;
		hasHashCode = false;
	}

	/**
	 * 将缓冲区设置为指定的字节子数组
	 *
	 * @param b - Ascii 字节
	 * @param off - 字节的起始偏移量
	 * @param len - 字节的长度
	 */
	public void setBytes(byte[] b, int off, int len) {
		buff = b;
		start = off;
		end = start + len;
		isSet = true;
		hasHashCode = false;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}
	public Charset getCharset() {
		if (charset == null) {
			charset = DEFAULT_CHARSET;
		}
		return charset;
	}

	public byte[] getBytes() {
		return getBuffer();
	}

	public byte[] getBuffer() {
		return buff;
	}

	/**
	 * 当缓冲区为空时，从输入通道读取数据
	 * @param in - 输入通道
	 */
	public void setByteInputChannel(ByteInputChannel in) {
		this.in = in;
	}

	/**
	 * 当缓冲区已满时，将数据写入输出通道。 也用于追加大量数据时。 如果未设置，缓冲区将增长到限制。
	 * @param out - 输出通道
	 */
	public void setByteOutputChannel(ByteOutputChannel out) {
		this.out = out;
	}

	public void append(byte b) throws IOException {
		makeSpace(1);
		int limit = getLimitInternal();

		// 无法腾出空间
		if (end >= limit) {
			flushBuffer();
		}
		buff[end++] = b;
	}

	public void append(ByteChunk src) throws IOException {
		append(src.getBytes(), src.getStart(), src.getLength());
	}

	/**
	 * 添加数据到缓冲区
	 * @param src - 字节数组
	 * @param off - 偏移量
	 * @param len - 字节数组长度
	 * @throws IOException - 向输出通道写入溢出数据失败
	 */
	public void append(byte src[], int off, int len) throws IOException {
		// 能否增长到极限
		makeSpace(len);
		int bufLimit = getLimitInternal();

		// 对常见情况进行优化。如果缓冲区是空的，并且源数据将填满缓冲区中的所有空间，那么可以直接将其写入输出，从而避免额外的拷贝
		if (len == bufLimit && end == start && out != null) {
			out.realWriteBytes(src, off, len);
			return;
		}

		// 如果低于上限，则写入缓冲区
		if (len <= bufLimit - end) {
			System.arraycopy(src, off, buff, end, len);
			end += len;
			return;
		}

		// 缓冲区已经达到（或大于）限制，先写入部分数据，之后刷新到通道中，然后继续写入部分数据。
		int avail = bufLimit - end;
		System.arraycopy(src, off, buff, end, avail); // 
		end += avail;

		flushBuffer();

		// 剩余需继续写入字节数
		int remain = len - avail;

		while (remain > (bufLimit - end)) { // 分片写入通道
			out.realWriteBytes(src, (off + len) - remain, bufLimit - end);
			remain = remain - (bufLimit - end);
		}

		// 最后一片数据写入缓冲区
		System.arraycopy(src, (off + len) - remain, buff, end, remain);
		end += remain;
	}


	/**
	 * 添加数据到缓冲区
	 * @param from - 带有数据的 ByteBuffer
	 * @throws IOException - 将溢出数据写入输出通道失败
	 */
	public void append(ByteBuffer from) throws IOException {
		int len = from.remaining();

		// will grow, up to limit
		makeSpace(len);
		int limit = getLimitInternal();

		// 针对常见情况进行优化。 如果缓冲区是空的，并且源将填满缓冲区中的所有空间，不妨直接将其写入输出，并避免额外的拷贝
		if (len == limit && end == start && out != null) {
			out.realWriteBytes(from);
			from.position(from.limit());
			return;
		}
		// 如果低于上限，则写入缓冲区
		if (len <= limit - end) {
			// makeSpace方法会将缓冲区增加到极限，所以有空间直接写入
			from.get(buff, end, len);
			end += len;
			return;
		}

		// 缓冲区已经达到（或大于）限制，先写入部分数据，之后刷新到通道中，然后继续写入部分数据。
		int avail = limit - end;
		from.get(buff, end, avail);
		end += avail;

		flushBuffer();

		int fromLimit = from.limit();
		// 剩余需继续写入字节数
		int remain = len - avail;
		avail = limit - end;
		while (remain >= avail) { // 分片写入通道
			from.limit(from.position() + avail);
			out.realWriteBytes(from);
			from.position(from.limit());
			remain = remain - avail;
		}

		from.limit(fromLimit);
		// 最后一片数据写入缓冲区
		from.get(buff, end, remain);
		end += remain;
	}

	/**
	 * 为 len 个字节腾出空间。 如果 len 很小，则分配一个保留空间。永远不要超过限制或  {@link AbstractChunk#ARRAY_MAX_SIZE}。
	 * @param count The size
	 */
	public void makeSpace(int count) {
		byte[] tmp = null;

		int bufLimit = getLimitInternal();

		long newSize;
		// 所需写入字节数
		long desiredSize = end + count;

		// 不能超过极限
		if (desiredSize > bufLimit) {
			desiredSize = bufLimit;
		}

		if (buff == null) {
			if (desiredSize < 256) {
				desiredSize = 256; // 采取最低限度
			}
			buff = new byte[(int) desiredSize];
		}

		// 当前缓冲区已可容纳
		if (desiredSize <= buff.length) {
			return;
		}
		
		// 调整缓冲区尺寸大小，以选取合适的值
		if (desiredSize < 2L * buff.length) {
			newSize = buff.length * 2L;
		} else {
			newSize = buff.length * 2L + count;
		}

		if (newSize > bufLimit) {
			newSize = bufLimit;
		}
		tmp = new byte[(int) newSize];

		// 数据迁移
		System.arraycopy(buff, start, tmp, 0, end - start);
		buff = tmp;
		tmp = null;
		end = end - start;
		start = 0;
	}

	/**
	 * 将缓冲区数据全部写入输出通道
	 *
	 * @throws IOException- 将溢出数据写入输出通道失败
	 */
	public void flushBuffer() throws IOException {
		// assert out!=null
		if (out == null) {
			throw new IOException(String.format("数据溢出，输出通为空。写入数据量：%s，limit：%s", Integer.valueOf(buff.length), Integer.valueOf(getLimit())));
		}
		out.realWriteBytes(buff, start, end - start);
		end = start;
	}

	/**
	 * @return 内部数据块的String表现形式
	 */
	public String toStringInternal() {
		if (charset == null) {
			charset = DEFAULT_CHARSET;
		}
		CharBuffer cb = charset.decode(ByteBuffer.wrap(buff, start, end - start));
		return new String(cb.array(), cb.arrayOffset(), cb.length());
	}

	public long getLong() {
		return Long.valueOf( new String(buff, start, end - start).trim() );
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ByteChunk) {
			return equals((ByteChunk) obj);
		}
		return false;
	}

	/**
	 * 将消息字节与指定的 String 对象进行比较
	 * @param s - 要比较的字符串
	 * @return 如果比较成功，则为 true，否则为 false
	 */
	public boolean equals(String s) {
		byte[] b = buff;
		int len = end - start;
		if (b == null || len != s.length()) {
			return false;
		}
		int off = start;
		for (int i = 0; i < len; i++) {
			if (b[off++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 将消息字节与指定的 String 对象忽略大小写进行比较。
	 * @param s - 要比较的字符串
	 * @return 如果比较成功，则为 true，否则为 false
	 */
	public boolean equalsIgnoreCase(String s) {
		byte[] b = buff;
		int len = end - start;
		if (b == null || len != s.length()) {
			return false;
		}
		int off = start;
		for (int i = 0; i < len; i++) {
			if (Ascii.toLower(b[off++]) != Ascii.toLower(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public boolean equals(ByteChunk bb) {
		return equals(bb.getBytes(), bb.getStart(), bb.getLength());
	}

	public boolean equals(byte b2[], int off2, int len2) {
		byte b1[] = buff;
		if (b1 == null && b2 == null) {
			return true;
		}

		int len = end - start;
		if (len != len2 || b1 == null || b2 == null) {
			return false;
		}

		int off1 = start;

		while (len-- > 0) {
			if (b1[off1++] != b2[off2++]) {
				return false;
			}
		}
		return true;
	}

	public boolean equals(CharChunk cc) {
		return equals(cc.getChars(), cc.getStart(), cc.getLength());
	}

	public boolean equals(char c2[], int off2, int len2) {
		// 仅适用于与 ASCII/UTF 兼容的编码
		byte b1[] = buff;
		if (c2 == null && b1 == null) {
			return true;
		}

		if (b1 == null || c2 == null || end - start != len2) {
			return false;
		}
		int off1 = start;
		int len = end - start;

		while (len-- > 0) {
			if ((char) b1[off1++] != c2[off2++]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 如果以区分大小写的方式测试缓冲区以指定的字符串开始，则返回true
	 * @param s - the string
	 * @param pos - 位置
	 * @return 如果开始匹配，则为True
	 */
	public boolean startsWith(String s, int pos) {
		byte[] b = buff;
		int len = s.length();
		if (b == null || len + pos > end - start) {
			return false;
		}
		int off = start + pos;
		for (int i = 0; i < len; i++) {
			if (b[off++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 如果缓冲区以不区分大小写的方式测试时以指定的字符串开始，则返回true。
	 * @param s the string
	 * @param pos The position
	 * @return 如果开始匹配，则为True
	 */
	public boolean startsWithIgnoreCase(String s, int pos) {
		byte[] b = buff;
		int len = s.length();
		if (b == null || len + pos > end - start) {
			return false;
		}
		int off = start + pos;
		for (int i = 0; i < len; i++) {
			if (Ascii.toLower(b[off++]) != Ascii.toLower(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected int getBufferElement(int index) {
		return buff[index];
	}

	/**
	 * 返回ByteChunk中从指定字节开始的给定字符的第一个实例。如果未找到，则返回-1。<br>
	 * 注意:这只适用于0-127的字符。
	 *
	 * @param c The character
	 * @param starting - 开始位置
	 * @return 字符出现的第一个实例的位置，如果没有找到该字符，则为-1。
	 */
	public int indexOf(char c, int starting) {
		int ret = indexOf(buff, start + starting, end, c);
		return (ret >= start) ? ret - start : -1;
	}

	/**
	 * 返回给定字节数组中给定字符在指定的开始和结束之间的第一个实例。 <br>
	 * 注意:这只适用于0-127的字符。
	 *
	 * @param bytes - 要搜索的数组
	 * @param start - 数组中开始搜索的点
	 * @param end - 在数组中停止搜索的点
	 * @param s - 要搜索的字符
	 * @return 字符出现的第一个实例的位置，如果没有找到字符，则为-1。
	 */
	public static int indexOf(byte bytes[], int start, int end, char s) {
		int offset = start;

		while (offset < end) {
			byte b = bytes[offset];
			if (b == s) {
				return offset;
			}
			offset++;
		}
		return -1;
	}

	/**
	 * 返回字节数组中指定起始和结束之间的给定字节的第一个实例。
	 *
	 * @param bytes - 要搜索的字节数组
	 * @param start - 从字节数组中开始搜索的点
	 * @param end - 在字节数组中停止搜索的点
	 * @param b - 要搜索的字节
	 * @return 字节出现的第一个实例的位置，如果没有找到字节，则为-1
	 */
	public static int findByte(byte bytes[], int start, int end, byte b) {
		int offset = start;
		while (offset < end) {
			if (bytes[offset] == b) {
				return offset;
			}
			offset++;
		}
		return -1;
	}

	/**
	 * 返回字节数组中指定起始和结束位置之间的任何给定字节的第一个实例。
	 * @param bytes - 要搜索的字节数组
	 * @param start - 从字节数组中开始搜索的点
	 * @param end - 在字节数组中停止搜索的点
	 * @param b - 要搜索的字节数组
	 * @return 字节出现的第一个实例的位置，如果没有找到字节，则为-1
	 */
	public static int findBytes(byte bytes[], int start, int end, byte b[]) {
		int blen = b.length;
		int offset = start;
		while (offset < end) {
			for (int i = 0; i < blen; i++) {
				if (bytes[offset] == b[i]) {
					return offset;
				}
			}
			offset++;
		}
		return -1;
	}

	/**
	 * 将指定的 String 转换为字节数组。 这仅适用于 Ascii，UTF 字符将被截断。
	 *
	 * @param value - 转换为字节数组
	 * @return 字节数组
	 */
	public static final byte[] convertToBytes(String value) {
		byte[] result = new byte[value.length()];
		for (int i = 0; i < value.length(); i++) {
			result[i] = (byte) value.charAt(i);
		}
		return result;
	}

	@Override
	protected String getString() {
		return isNull() ? null : new String(buff, start, end, getCharset()).trim();
	}
	
	// ----------------------------------------------------- 接口定义 -----------------------------------------------------

	/**
	 * 输入接口，缓冲区为空时使用。同java.nio.channels.ReadableByteChannel
	 */
	public static interface ByteInputChannel {
		/**
		 * 读取新字节
		 *
		 * @return 读取的字节数
		 * @throws IOException 如果在读取过程中发生 I/O 错误
		 */
		public int realReadBytes() throws IOException;
	}

	/**
	 * 当需要更多空间时，要么增加缓冲区（达到限制），要么将其发送到通道。与 java.nio.channel.WritableByteChannel 相同。
	 */
	public static interface ByteOutputChannel {
		/**
		 * 发送字节（通常是内部转换缓冲区）。 如果缓冲区已满，则预期为 8k 输出。
		 *
		 * @param buf - 将被写入的字节
		 * @param off - 字节数组中的偏移量
		 * @param len - 将被写入的长度
		 * @throws IOException - 如果在写入字节时发生 I/O
		 */
		public void realWriteBytes(byte buf[], int off, int len) throws IOException;

		/**
		 * 发送字节（通常是内部转换缓冲区）。 如果缓冲区已满，则预期为 8k 输出。
		 *
		 * @param from - 将被写入的字节
		 * @throws IOException - 如果在写入字节时发生 I/O
		 */
		public void realWriteBytes(ByteBuffer from) throws IOException;
	}
}
