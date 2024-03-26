package org.zy.moonstone.core.exceptions;

import java.io.IOException;

/**
 * @dateTime 2022年5月31日;
 * @author zy(azurite-Y);
 * @description 抛出这个异常是为了向Tomcat内部发出一个信号:发生了一个需要关闭连接的错误。
 * 对于多路复用协议，如HTTP/2，这意味着通道必须关闭，但连接可以继续。对于非多路协议，必须关闭连接。
 * 它对应于 ErrorState.CLOSE_NOW。
 */
public class CloseNowException extends IOException  {
	private static final long serialVersionUID = -1495393071605390218L;

	public CloseNowException() {
        super();
    }

    public CloseNowException(String message, Throwable cause) {
        super(message, cause);
    }

    public CloseNowException(String message) {
        super(message);
    }

    public CloseNowException(Throwable cause) {
        super(cause);
    }
}
