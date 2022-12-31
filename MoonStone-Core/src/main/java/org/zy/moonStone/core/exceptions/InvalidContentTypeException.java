package org.zy.moonStone.core.exceptions;

/**
 * @dateTime 2022年11月23日;
 * @author zy(azurite-Y);
 * @description
 */
public class InvalidContentTypeException extends FileUploadException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 4007870944369042975L;

	public InvalidContentTypeException() {
        super();
    }

    public InvalidContentTypeException(String message) {
        super(message);
    }

    public InvalidContentTypeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
