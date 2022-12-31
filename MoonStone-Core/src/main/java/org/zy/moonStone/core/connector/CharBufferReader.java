package org.zy.moonStone.core.connector;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.nio.CharBuffer;

import org.zy.moonStone.core.util.net.interfaces.ServletReader;

/**
 * @dateTime 2022年7月10日;
 * @author zy(azurite-Y);
 * @description 此类为请求正文的读取处理字符流，支持 mark() 操作。为了满足重写方法的限制故而使用 {@link BufferedReader } 子类包装 {@link CharArrayReader }。
 * <p>
 * 为了不使 <code>BufferedReader</code> 重新拷贝字符数组，所以在此子类额外覆写了与 <code>BufferedReader</code> 类填充缓冲区方法(<code>fill()</code>)有关的调用方法
 */ 
public class CharBufferReader extends  BufferedReader implements ServletReader {
	private CharArrayReader charArrayReader = null;

	private boolean finished;
	
	/**
	 * 相对于当前调用 <code>read()</code> 返回字符的上一个字符
	 */
	private char lastChar = 0;
	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 创建一个使用指定大小的输入缓冲区的缓冲字符输入流
	 * @param charBuffer - 缓冲区
	 * @param len - 缓冲区大小
	 * @apiNote 使用 {@link CharArrayReader }来进行字符数组数据的相关底层操作
	 */
	public CharBufferReader(CharBuffer charBuffer, int len) {
		super( new CharArrayReader(charBuffer.array()), len);
		
		charArrayReader = (CharArrayReader)super.lock;
	}


	@Override
	public int read() throws IOException {
		int read = charArrayReader.read();
		finished = read == -1 ? true : finished;
		return read;
	}
	
	
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		return charArrayReader.read(cbuf, off, len);
	}
	
	
	/**
	 * 如果可以在不阻塞的情况下读取数据，则返回 true，否则返回 false。
	 * 
	 * @return 如果可以不阻塞地获取数据，则返回 true，否则返回 false。
	 */
	@Override
	public boolean ready() throws IOException {
		return charArrayReader.ready();
	}
	

	/**
	 * 跳过字符
	 * 
	 * @param skipCount - 要跳过的字符数
	 * @return 实际跳过的字符数
	 * @throws IOException 如果发生 I/O 错误
	 */
	@Override
	public long skip(long skipCount) throws IOException  {
		/*
		 * 传入跳过数小于0则跳过0；
		 * 如果剩余待读数大于指定跳过数则跳过指定数；
		 * 如果剩余待读数小于指定跳过数则跳过剩余待度数；
		 */
		return charArrayReader.skip(skipCount);
	}
	
	
	/**
	 * 读取一行文本。 一行被视为由换行符 ('\n')、回车符 ('\r') 或紧跟换行符的回车符中的任何一个终止。
	 * 
	 * @return 包含行内容的字符串，不包括任何行终止字符，如果已到达流的末尾，则为 null
	 * @throws IOException 如果发生 I/O 错误
	 */
	@Override
	public String readLine() throws IOException {
		int readData;
		
		StringBuilder builder = new StringBuilder();
		while(true) {
			readData =charArrayReader.read();

			if (-1 == readData) {
				break;
			}
			
			if (lastChar == '\n' && readData == '\r') {
				// 跳過当前的‘\r’
				lastChar = 0;
			} else if (readData == '\n') {
				lastChar = (char)readData;
				break;
			} else if ( readData == '\r' ) {
				lastChar = 0;
				break;
			} else {
				builder.append((char)readData);
			}
		}
		
		return builder.toString();
	}
	
	
	/**
	 * 标记流中的当前位置。 对 reset() 的后续调用将尝试将流重新定位到该点。
	 * 
	 * @param readAheadLimit - 在保留标记的同时限制可以读取的字符数。 
	 * 在读取字符达超过（在读取完待读字符之后的读取下一字符视为超过此限值）此限制后将无法重置流。
	 * 
	 * @throws IOException - 如果发生 I/O 错误
	 * @throws IllegalArgumentException - 如果限制值大于输入缓冲区的待读字符数
	 */
	@Override
	public void mark(int readAheadLimit) throws IOException {
		charArrayReader.mark(readAheadLimit);
	}
	
	
	/**
	 * 将流重置为最新标记。
	 * 
	 * @throws IOException - 如果流从未被标记，或者标记已失效
	 */
	@Override
	public void reset() throws IOException {
		charArrayReader.reset();
	}

	
	/**
     * 防止克隆 facade
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    
    
    /**
     * 已读取流中的所有数据时返回 true，否则返回 false
     * 
     * @return 当此特定请求的所有数据都已读取时为 true，否则返回 false。
     */
	@Override
	public boolean isFinished() {
		return finished;
	}

}
