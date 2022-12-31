package org.zy.moonStone.core.connector;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @dateTime 2022年7月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class CharBufferWriter extends PrintWriter {
	// 系统相关的行分隔符字符数组
	private static final char[] LINE_SEP = System.lineSeparator().toCharArray();

	protected OutputBuffer outputBuffer;
	protected boolean error = false;

	public CharBufferWriter(OutputBuffer outputBuffer) {
		super(outputBuffer);
		this.outputBuffer = outputBuffer;
	}

	/**
	 * 防止克隆 facade
	 */
	@Override
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	/**
	 * 清理 facade
	 */
	void clear() {
		outputBuffer = null;
	}

	void recycle() {
		error = false;
	}

	// -------------------------------------------------------------------------------------
	// Writer Methods
	// -------------------------------------------------------------------------------------
	@Override
	public void flush() {
		if (error) {
			return;
		}

		try {
			outputBuffer.flush();
		} catch (IOException e) {
			error = true;
		}
	}

	@Override
	public void close() {
		// 不关闭PrintWriter - super()没有被调用，因此流可以被重用。关闭 outputBuffer
		try {
			outputBuffer.close();
		} catch (IOException ex) {
			// Ignore
		}
		error = false;
	}

	@Override
	public boolean checkError() {
		flush();
		return error;
	}

	@Override
	public void write(int c) {
		if (error) {
			return;
		}

		try {
			outputBuffer.write(c);
		} catch (IOException e) {
			error = true;
		}
	}

	@Override
	public void write(char buf[], int off, int len) {
		if (error) {
			return;
		}

		try {
			outputBuffer.write(buf, off, len);
		} catch (IOException e) {
			error = true;
		}

	}

	@Override
	public void write(String s, int off, int len) {
		if (error) {
			return;
		}

		try {
			outputBuffer.write(s, off, len);
		} catch (IOException e) {
			error = true;
		}
	}

	// -------------------------------------------------------------------------------------
	// PrintWriter Methods
	// -------------------------------------------------------------------------------------
	/**
	 * print 布尔值。由 <code>{@link java.lang.String#valueOf(boolean) }</code> 生成的字符串
	 * 根据平台的默认字符编码被转换为字节，并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(boolean b) {
		if (b) {
			write("true");
		} else {
			write("false");
		}
	}

	/**
	 * 输出一个字符。根据平台的默认字符编码，字符被转换为一个或多个字节，并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(char c) {
		write(c);
	}

	/**
	 * 输出一个整数。<code>{@link java.lang.String#valueOf(int) }</code>生成的字符串根据平台的默认字符编码被转换为字节，
	 * 并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(int i) {
		write(String.valueOf(i));
	}

	/**
	 * 输出长整数。<code>{@link java.lang.String#valueOf(long) }</code>生成的字符串根据平台的默认字符编码被转换为字节，
	 * 并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(long l) {
		write(String.valueOf(l));
	}

	/**
	 * 输出一个浮点数。<code>{@link java.lang.String#valueOf(float) }</code>生成的字符串根据平台的默认字符编码被转换为字节，
	 * 并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(float f) {
		write(String.valueOf(f));
	}

	/**
	 * 输出一个浮点数。<code>{@link java.lang.String#valueOf(float) }</code>生成的字符串根据平台的默认字符编码被转换为字节，
	 * 并且这些字节完全按照 <code>{@link #write(int)方法的方式写入。
	 */
	@Override
	public void print(double d) {
		write(String.valueOf(d));
	}

	/**
	 * 输出一个字符数组。根据平台的默认字符编码，字符被转换为字节，并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(char s[]) {
		write(s);
	}

	/**
	 * 输出一个字符串。如果参数为null，则打印字符串“null”。
	 * 否则，字符串的字符将根据平台的默认字符编码转换为字节，并且这些字节将完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(String s) {
		if (s == null) {
			s = "null";
		}
		write(s);
	}

	/**
	 * 输出一个对象。<code>{@link java.lang.String#valueOf(Object) }</code>方法生成的字符串根据平台的默认字符编码被转换为字节，
	 * 并且这些字节完全按照 <code>{@link #write(int) }</code>方法的方式写入。
	 */
	@Override
	public void print(Object obj) {
		write(String.valueOf(obj));
	}

	/**
	 * 通过写入行分隔符字符串来终止当前行。分隔符字符串由系统属性line定义。分隔符，并且不一定是一个换行符('\n')。
	 */
	@Override
	public void println() {
		write(LINE_SEP);
	}

	/**
	 * 输出一个布尔值，然后结束该行。这个方法的行为就好像它调用了 <code>{@link #print(boolean) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(boolean b) {
		print(b);
		println();
	}

	/**
	 * 输出一个字符，然后结束该行。这个方法的行为就好像它调用了 <code>{@link #print(char) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(char c) {
		print(c);
		println();
	}

	/**
	 * 输出一个整数，然后结束该行。这个方法的行为就像它调用 <code>{@link #print(int) }</code>，然后调用 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(int i) {
		print(i);
		println();
	}

	/**
	 * 输出一个长整数，然后终止该行。这个方法的行为就好像它调用了 <code>{@link #print(long) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(long l) {
		print(l);
		println();
	}

	/**
	 * 输出一个浮点数，然后终止该行。这个方法的行为就好像它调用了 <code>{@link #print(float) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(float f) {
		print(f);
		println();
	}

	/**
	 * 输出双精度浮点数，然后终止该行。这个方法的行为就好像它调用了 <code>{@link #print(double) }</code>，然后调用了 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(double d) {
		print(d);
		println();
	}

	/**
	 * 输出一个字符数组，然后结束该行。这个方法的行为就好像它调用了 <code>{@link #print(char[]) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(char c[]) {
		print(c);
		println();
	}

	/**
	 * 输出一个String，然后结束该行。这个方法的行为就好像它调用了 <code>{@link #print(String) }</code>，然后是 <code>{@link #println() }</code>。
	 */
	@Override
	public void println(String s) {
		print(s);
		println();
	}

	/**
	 * 输出一个Object，然后终止该行。这个方法该方法首先调用string.valueof (x)来获取打印对象的字符串值，
	 * 然后调用 <code>{@link #print(String)}</code> 和 <code>{@link #println()}</code> 。
	 */
	@Override
	public void println(Object o) {
		print(o);
		println();
	}
}
