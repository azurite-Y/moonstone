package org.zy.moonStone.core.util.net;

/**
 * @dateTime 2022年5月20日;
 * @author zy(azurite-Y);
 * @description 此枚举列出了请求处理可以触发的不同类型的分派。 在这种情况下，分派意味着使用给定的套接字状态重新处理这个请求。
 */
public enum DispatchType {
	/**
	 * 非阻塞读
	 */
	NON_BLOCKING_READ(SocketEvent.OPEN_READ),
	/**
	 * 非阻塞写
	 */
	NON_BLOCKING_WRITE(SocketEvent.OPEN_WRITE);

	private final SocketEvent status;

	private DispatchType(SocketEvent status) {
		this.status = status;
	}

	public SocketEvent getSocketStatus() {
		return status;
	}
}
