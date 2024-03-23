package org.zy.moonstone.core.exceptions;

/**
 * @dateTime 2022年6月10日;
 * @author zy(azurite-Y);
 * @description 用于标记HTTP标头超出最大允许大小的特定错误条件的异常。
 */
public class HeadersTooLargeException extends IllegalStateException {
	private static final long serialVersionUID = -5630350842012435751L;

	public HeadersTooLargeException() {
	        super();
	    }

	    public HeadersTooLargeException(String message, Throwable cause) {
	        super(message, cause);
	    }

	    public HeadersTooLargeException(String s) {
	        super(s);
	    }

	    public HeadersTooLargeException(Throwable cause) {
	        super(cause);
	    }
}
