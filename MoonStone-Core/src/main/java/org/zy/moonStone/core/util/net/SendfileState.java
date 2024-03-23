package org.zy.moonstone.core.util.net;

/**
 * @dateTime 2022年1月13日;
 * @author zy(azurite-Y);
 * @description
 */
public enum SendfileState {
	/**
     * 文件的发送已开始但尚未完成。 Sendfile 仍在使用套接字
     */
    PENDING,

    /**
     * 文件已全部发送完毕。Sendfile不再使用套接字
     */
    DONE,

    /**
     * 出了点问题。文件可能已发送，也可能尚未发送。socketis处于未知状态。
     */
    ERROR
}
