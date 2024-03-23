package org.zy.moonstone.core.exceptions;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description
 * 如果文件名无效，则抛出此异常。如果文件名包含NUL字符，则该文件名无效。
 * 攻击者可能会利用这一点来绕过安全检查:例如，恶意用户可能会上传名为“foo.exe\0.png”的文件。
 * 这个文件名可能通过安全检查(例如检查扩展名“。png”)，而根据底层的C库，它可能创建一个名为“foo.exe”的文件，因为null字符是C中的字符串结束符。
 */
public class InvalidFileNameException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5685665688988135028L;
	
	/**
     * 导致异常的文件名
     */
    private final String name;

    /**
     * 创建一个新的实例
     *
     * @param pName  - 导致异常的文件名
     * @param pMessage - 可读的错误消息
     */
    public InvalidFileNameException(String pName, String pMessage) {
        super(pMessage);
        name = pName;
    }

    /**
     * @return 导致异常的文件名
     */
    public String getName() {
        return name;
    }
}
