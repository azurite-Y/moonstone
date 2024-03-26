package org.zy.moonstone.core.util.net;

/**
 * @dateTime 2022年1月13日;
 * @author zy(azurite-Y);
 * @description
 */
public enum SendfileKeepAliveState {
	/**
     * Keep-alive 未使用。 写入响应后可以关闭套接字
     */
    NONE,

    /**
     * Keep alive正在使用中，输入缓冲区中有流水线数据，待当前响应写入后立即读取
     */
    PIPELINED,

    /**
     * Keep-alive 正在使用中。 应将套接字添加到轮询器（或等效）以在当前响应被写入后立即等待更多数据。
     */
    OPEN
}
