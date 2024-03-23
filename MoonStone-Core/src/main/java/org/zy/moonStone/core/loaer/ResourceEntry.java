package org.zy.moonstone.core.loaer;

/**
 * @dateTime 2022年8月23日;
 * @author zy(azurite-Y);
 * @description 资源条目
 */
public class ResourceEntry {
	/**
     * 加载此资源时原始文件的“最后修改”时间，以自纪元起的毫秒为单位
     */
    public long lastModified = -1;

    /**
     * Loaded class.
     */
    public volatile Class<?> loadedClass = null;
}
