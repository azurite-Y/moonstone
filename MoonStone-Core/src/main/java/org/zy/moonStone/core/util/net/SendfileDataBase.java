package org.zy.moonStone.core.util.net;

/**
 * @dateTime 2022年1月13日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class SendfileDataBase {
	/**
	 * 当前请求是否在保持活动连接上处理？ 
	 * 这决定了套接字是否在发送文件完成后关闭，或者处理是否继续连接上的下一个请求或等待下一个请求到达。
	 */
	public SendfileKeepAliveState keepAliveState = SendfileKeepAliveState.NONE;

	/**
	 * 包含要写入套接字的数据的文件的完整路径
	 */
	public final String fileName;

	/**
	 * 文件中要写入套接字的下一个字节的位置。这被初始化为起点，然后随着文件的写入而更新
	 */
	public long pos;

	/**
	 * 从文件中要写入的剩余字节数（从当前位置开始。这被初始化为终点 - 起点，然后在写入文件时更新。
	 */
	public long length;

	public SendfileDataBase(String filename, long pos, long length) {
		this.fileName = filename;
		this.pos = pos;
		this.length = length;
	}
}
