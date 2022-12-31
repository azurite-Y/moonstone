package org.zy.moonStone.core.util.net.interfaces;

/**
 * @dateTime 2022年7月11日;
 * @author zy(azurite-Y);
 * @description Servlet 所使用字符输入流扩展接口
 */
public interface ServletReader {
    /**
     * 已读取流中的所有数据时返回 true，否则返回 false
     * 
     * @return 当此特定请求的所有数据都已读取时为 true，否则返回 false。
     */
	boolean isFinished();
}
