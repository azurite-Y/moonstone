package org.zy.moonstone.core.util;

/**
 * @dateTime 2022年5月23日;
 * @author zy(azurite-Y);
 * @description
 */
public enum ErrorState {
	/**
	 * 不处于错误状态
	 */
	NONE(false, 0, true, true),

	/**
	 * 当前请求/响应处于错误状态，虽然完成当前响应是安全的，但继续使用现有连接是不安全的，一旦响应完成，就必须关闭现有连接。
	 * 对于多路复用协议，当当前请求/响应完成但连接可能继续时，通道必须关闭。
	 */
	CLOSE_CLEAN(true, 1, true, true),

	/**
	 * 当前请求/响应处于错误状态，继续使用它们不安全。对于多路复用协议(如HTTP/2)，流/通道必须立即关闭，但连接可以继续。
	 * 对于非多路复用协议(AJP, HTTP/1.x)，当前连接必须关闭。
	 */
	CLOSE_NOW(true, 2, false, true),

	/**
	 * 检测到影响底层网络连接的错误。 继续使用必须立即关闭的网络连接是不安全的。 对于多路复用协议（例如 HTTP/2），这会影响所有多路复用通道。
	 */
	CLOSE_CONNECTION_NOW(true, 3, false, false);

	/** 错误状态 */
	private final boolean error;
	/** 严重性 */
	private final int severity;
	/** 允许io */
	private final boolean ioAllowed;
	/** 连接允许 */
	private final boolean connectionIoAllowed;

	private ErrorState(boolean error, int severity, boolean ioAllowed, boolean connectionIoAllowed) {
		this.error = error;
		this.severity = severity;
		this.ioAllowed = ioAllowed;
		this.connectionIoAllowed = connectionIoAllowed;
	}

	public boolean isError() {
		return error;
	}

	/**
	 * 将此 ErrorState 与提供的 ErrorState 进行比较并返回最严重的.
	 * @param input - 与此比较的错误状态
	 * @return 提供的错误状态和这个错误状态中最严重的错误状态
	 */
	public ErrorState getMostSevere(ErrorState input) {
		if (input.severity > this.severity) {
			return input;
		} else {
			return this;
		}
	}

	public boolean isIoAllowed() {
		return ioAllowed;
	}

	public boolean isConnectionIoAllowed() {
		return connectionIoAllowed;
	}
}
