package org.zy.moonstone.core.exceptions;

/**
 * @dateTime 2022年11月23日;
 * @author zy(azurite-Y);
 * @description
 */
public class SizeException extends FileUploadException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8555347532955015594L;

    private final long actual;

    private final long permitted;

    protected SizeException(String message, long actual, long permitted) {
        super(message);
        this.actual = actual;
        this.permitted = permitted;
    }

    public long getActualSize() {
        return actual;
    }

    public long getPermittedSize() {
        return permitted;
    }
}
