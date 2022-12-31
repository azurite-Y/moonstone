package org.zy.moonStone.core.exceptions;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description
 */
public class FileUploadException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2353855534272983620L;

	public FileUploadException() {
        super();
    }

    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileUploadException(String message) {
        super(message);
    }

    public FileUploadException(Throwable cause) {
        super(cause);
    }
}
