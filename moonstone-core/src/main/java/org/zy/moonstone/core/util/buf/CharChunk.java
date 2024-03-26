package org.zy.moonstone.core.util.buf;

import java.io.IOException;

/**
 * @dateTime 2022年5月25日;
 * @author zy(azurite-Y);
 * @description 操作字符块的实用程序
 */
public class CharChunk extends AbstractChunk implements CharSequence {
	private static final long serialVersionUID = -8927623711682275907L;

	private char[] buff;

	@SuppressWarnings("unused")
	private transient CharInputChannel in = null;
	private transient CharOutputChannel out = null;

	/**
	 * 创建一个新的、未初始化的CharChunk对象
	 */
	public CharChunk() {}

	public CharChunk(int initial) {
		allocate(initial, -1);
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public void allocate(int initial, int limit) {
		if (buff == null || buff.length < initial) {
			buff = new char[initial];
		}
		setLimit(limit);
		start = 0;
		end = 0;
		isSet = true;
		hasHashCode = false;
	}

	/**
	 * 将缓冲区设置为指定的字符数组
	 *
	 * @param c the characters
	 * @param off - 字符的起始偏移量
	 * @param len - 字符的长度
	 */
	public void setChars(char[] c, int off, int len) {
		buff = c;
		start = off;
		end = start + len;
		isSet = true;
		hasHashCode = false;
	}

	/**
	 * @return the httpOutputBuffer.
	 */
	public char[] getChars() {
		return getBuffer();
	}

	/**
	 * @return the httpOutputBuffer.
	 */
	public char[] getBuffer() {
		return buff;
	}

	/**
	 * 当缓冲区为空时，从输入通道读取数据
	 * @param in - 输入通道
	 */
	public void setCharInputChannel(CharInputChannel in) {
		this.in = in;
	}

	/**
	 * 当缓冲区满时，将数据写入输出通道。也用于添加大量数据时。如果不设置，缓冲区将增长到限制。
	 * @param out - 输出通道
	 */
	public void setCharOutputChannel(CharOutputChannel out) {
		this.out = out;
	}

	/**
	 * 追加指定字符到缓冲区
	 * @param b
	 * @throws IOException
	 */
	public void append(char b) throws IOException {
		makeSpace(1);
		int limit = getLimitInternal();

		// 无法腾出空间
		if (end >= limit) {
			flushBuffer();
		}
		buff[end++] = b;
	}

	/**
	 * 追加指定字符块到缓冲区
	 * @param src
	 * @throws IOException
	 */
	public void append(CharChunk src) throws IOException {
		append(src.getBuffer(), src.getOffset(), src.getLength());
	}

	/**
	 * 追加指定数据到缓冲区
	 * @param src - 字符数组
	 * @param off - 偏移量
	 * @param len - 字符数组长度
	 * @throws IOException - 向输出通道写入溢出数据失败
	 */
	public void append(char src[], int off, int len) throws IOException {
		makeSpace(len);
		int bufLimit = getLimitInternal();

		// 对常见情况进行优化。如果缓冲区是空的，并且源将填满缓冲区中的所有空间，那么可以直接将其写入输出，从而避免额外的拷贝
		if (len == bufLimit && end == start && out != null) {
			out.realWriteChars(src, off, len);
			return;
		}

		// 如果低于上限，则写入缓冲区
		if (len <= bufLimit - end) {
			System.arraycopy(src, off, buff, end, len);
			end += len;
			return;
		}

		/*
		 * 缓冲区已经达到(或大于)限制，刷新缓冲区后继续写入剩余部分。
		 */
		if (len + end < 2 * bufLimit) {
			/*
			 * 缓冲区足够写入两次数据，那么先写入部分数据，之后刷新到通道中，然后继续写入部分数据
			 */
			int avail = bufLimit - end;
			System.arraycopy(src, off, buff, end, avail);
			end += avail;

			flushBuffer();

			System.arraycopy(src, off + avail, buff, end, len - avail);
			end += len - avail;

		} else {
			// 刷新缓冲区并直接从源写入剩余的内容
			flushBuffer();
			out.realWriteChars(src, off, len);
		}
	}

	/**
	 * 添加字符串到缓冲区
	 * @param s The string
	 * @throws IOException - 向输出通道写入溢出数据失败
	 */
	public void append(String s) throws IOException {
		append(s, 0, s.length());
	}

	/**
	 * 添加字符串到缓冲区
	 * @param s - 字符串
	 * @param off - 偏移量
	 * @param len - 字符串长度
	 * @throws IOException - 向输出通道写入溢出数据失败
	 */
	public void append(String s, int off, int len) throws IOException {
		if (s == null) {
			return;
		}

		makeSpace(len);
		int limit = getLimitInternal();

		int sOff = off;
		int sEnd = off + len;
		while (sOff < sEnd) {
			int d = min(limit - end, sEnd - sOff);
			// 将此字符串中的字符复制到目标字符数组中
			s.getChars(sOff, sOff + d, buff, end);
			sOff += d;
			end += d;
			if (end >= limit) {
				flushBuffer();
			}
		}
	}

	/**
	 * 将缓冲区数据全部写入输出通道
	 * @throws IOException- 将溢出数据写入输出通道失败
	 */
	public void flushBuffer() throws IOException {
		// assert out!=null
		if (out == null) {
			throw new IOException(String.format("数据溢出，输出通为空。写入数据量：%s，limit：%s", Integer.valueOf(buff.length), Integer.valueOf(getLimit())));
		}
		out.realWriteChars(buff, start, end - start);
		end = start;
	}

	/**
	 * 为 len 个字符腾出空间。 如果 len 很小，则分配一个保留空间。永远不要超过限制或  {@link AbstractChunk#ARRAY_MAX_SIZE}。
	 * @param count The size
	 */
	public void makeSpace(int count) {
		char[] tmp = null;

		int bufLimit = getLimitInternal();

		long newSize;
		// 所需写入字符数
		long desiredSize = end + count;

		// 不能超过极限
		if (desiredSize > bufLimit) {
			desiredSize = bufLimit;
		}

		if (buff == null) {
			if (desiredSize < 256) {
				desiredSize = 256; // 采取最低限度
			}
			buff = new char[(int) desiredSize];
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
		tmp = new char[(int) newSize];

		// 数据迁移
		System.arraycopy(buff, 0, tmp, 0, end);
		buff = tmp;
		tmp = null;
	}

	@Override
	public String getString() {
		return isNull() ? null : new String(buff, start, end - start).trim();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CharChunk) {
			return equals((CharChunk) obj);
		}
		return false;
	}

	public boolean equals(String s) {
		char[] c = buff;
		int len = end - start;
		if (c == null || len != s.length()) {
			return false;
		}
		int off = start;
		for (int i = 0; i < len; i++) {
			if (c[off++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	public boolean equalsIgnoreCase(String s) {
		char[] c = buff;
		int len = end - start;
		if (c == null || len != s.length()) {
			return false;
		}
		int off = start;
		for (int i = 0; i < len; i++) {
			if (Ascii.toLower(c[off++]) != Ascii.toLower(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public boolean equals(CharChunk cc) {
		return equals(cc.getChars(), cc.getOffset(), cc.getLength());
	}

	public boolean equals(char b2[], int off2, int len2) {
		char b1[] = buff;
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

	/**
	 * @return 如果消息字节以指定字符串开始，则为True。
	 * @param s The string
	 */
	public boolean startsWith(String s) {
		char[] c = buff;
		int len = s.length();
		if (c == null || len > end - start) {
			return false;
		}
		int off = start;
		for (int i = 0; i < len; i++) {
			if (c[off++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}


	/**
	 * 如果缓冲区从指定的字符串开始，则返回true
	 * @param s the string
	 * @param pos The position
	 * @return 如果开始匹配，则为True
	 */
	public boolean startsWithIgnoreCase(String s, int pos) {
		char[] c = buff;
		int len = s.length();
		if (c == null || len + pos > end - start) {
			return false;
		}
		int off = start + pos;
		for (int i = 0; i < len; i++) {
			if (Ascii.toLower(c[off++]) != Ascii.toLower(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return 如果消息字节以指定字符串结束，则为True。
	 * @param s The string
	 */
	public boolean endsWith(String s) {
		char[] c = buff;
		int len = s.length();
		if (c == null || len > end - start) {
			return false;
		}
		int off = end - len;
		for (int i = 0; i < len; i++) {
			if (c[off++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected int getBufferElement(int index) {
		return buff[index];
	}

	public int indexOf(char c) {
		return indexOf(c, start);
	}

	/**
	 * 返回此 CharChunk 中给定字符的第一个实例，从指定字符开始。 如果未找到该字符，则返回 -1。<br>
	 * @param c The character
	 * @param starting - 搜索起始位置
	 * @return 字符的第一个实例的位置，如果未找到字符，则为 -1。
	 */
	public int indexOf(char c, int starting) {
		int ret = indexOf(buff, start + starting, end, c);
		return (ret >= start) ? ret - start : -1;
	}

	/**
	 * 在指定的开始和结束之间返回给定字符数组中给定字符的第一个实例。 <br>
	 * @param chars - 要搜索的数组
	 * @param start - 在数组中开始搜索的点
	 * @param end - 在数组中停止搜索的点
	 * @param s - 要搜索的字符
	 * @return 字符的第一个实例的位置，如果未找到字符，则为 -1。
	 */
	public static int indexOf(char chars[], int start, int end, char s) {
		int offset = start;

		while (offset < end) {
			char c = chars[offset];
			if (c == s) {
				return offset;
			}
			offset++;
		}
		return -1;
	}

	private int min(int a, int b) {
		if (a < b) {
			return a;
		}
		return b;
	}

	// ----------------------------------------------------- CharSequence 实现方法 -----------------------------------------------------
	@Override
	public char charAt(int index) {
		return buff[index + start];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		try {
			CharChunk result = (CharChunk) this.clone();
			result.setOffset(this.start + start);
			result.setEnd(this.start + end);
			return result;
		} catch (CloneNotSupportedException e) {
			// Cannot happen
			return null;
		}
	}

	@Override
	public int length() {
		return end - start;
	}


	// ----------------------------------------------------- 接口定义 -----------------------------------------------------
	/**
	 * 输入接口，当缓冲区为空时使用。
	 */
	public static interface CharInputChannel {
		/**
		 * 读取新的字符
		 * @return 读取的字符数
		 * @throws IOException - 如果在读取过程中发生 I/O 错误
		 */
		public int realReadChars() throws IOException;
	}

	/**
	 * 当需要更多空间时，要么增加缓冲区（达到限制），要么将其刷新到通道。
	 */
	public static interface CharOutputChannel {
		/**
		 * 发送字节（通常是内部转换缓冲区）。 如果缓冲区已满，则预期为 8k 输出。
		 *
		 * @param buf - 将要写入的字符
		 * @param off - 字符数组中的偏移量
		 * @param len - 将被写入的长度
		 * @throws IOException - 如果在写入字符时发生 I/O
		 */
		public void realWriteChars(char buf[], int off, int len) throws IOException;
	}
}
