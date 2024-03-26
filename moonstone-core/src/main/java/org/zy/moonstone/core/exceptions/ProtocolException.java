package org.zy.moonstone.core.exceptions;

/**
 * @dateTime 2022年12月3日;
 * @author zy(azurite-Y);
 * @description
 */
public class ProtocolException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7085793274201494315L;

	public ProtocolException() {
        super();
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(Throwable cause) {
        super(cause);
    }
}
