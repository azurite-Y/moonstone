package org.zy.moonStone.core.exceptions;

import java.io.IOException;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 包装一个IOException，标识它是由远程客户端请求中止引起的
 */
public final class ClientAbortException extends IOException {
	private static final long serialVersionUID = 8793449705489336566L;

    public ClientAbortException() {
        super();
    }
    public ClientAbortException(String message) {
        super(message);
    }
    public ClientAbortException(Throwable throwable) {
        super(throwable);
    }
    public ClientAbortException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
