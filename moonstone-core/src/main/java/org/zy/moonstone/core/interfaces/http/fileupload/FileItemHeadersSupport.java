package org.zy.moonstone.core.interfaces.http.fileupload;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description 该接口将指示 {@link FileItem } 或 {@link FileItemStream } 实现将接受 {@link FileItemHeaders} 封装该表单项的头
 */
public interface FileItemHeadersSupport {
	/**
	 * 返回此项中本地定义的表单头集合
     *
     * @return 该表单项的 {@link FileItemHeaders}
     */
    FileItemHeaders getHeaders();

    /**
     * 设置从项中读取的表单头。{@link FileItem} or {@link FileItemStream} 的实现应该实现这个接口，以便能够找到原始表单头。
     *
     * @param headers - 该项关联的 {@link FileItemHeaders} 实例
     */
    void setHeaders(FileItemHeaders headers);
}
