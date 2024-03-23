package org.zy.moonstone.core.util.net;

/**
 * @dateTime 2022年1月12日;
 * @author zy(azurite-Y);
 * @description 定义每个套接字发生的事件，这些事件需要容器进行进一步处理。这些事件通常由套接字实现触发，但也可能由容器触发
 */
public enum SocketEvent {
	/**
	 * 可读取的数据.
	 */
	OPEN_READ,

	/**
	 * 套接字已准备好被写入.
	 */
	OPEN_WRITE,

	/**
	 * 关联的Connector/Endpoint正在停止，需要干净利落地关闭连接/套接字.
	 */
	STOP,

	/**
	 * 超时发生，需要彻底关闭连接。目前这只被Servlet 3.0异步处理使用.
	 */
	TIMEOUT,

	/**
	 * 客户端已断开连接.
	 */
	DISCONNECT,

	/**
	 * 在非容器线程上发生了错误，处理需要返回到容器进行必要的清理。使用this的例子包括:
	 * <ul>
	 * <li>由NIO2发出完成处理程序失败的信号</li>
	 * <li>在Servlet 3.0异步处理期间，在非容器线程上发出I/O错误的信号.</li>
	 * </ul>
	 */
	ERROR,

	/**
	 * 客户端试图建立连接，但失败。使用这种方法的示例包括:
	 * <ul>
	 * <li>TLS握手失败</li>
	 * </ul>
	 */
	CONNECT_FAIL
}
