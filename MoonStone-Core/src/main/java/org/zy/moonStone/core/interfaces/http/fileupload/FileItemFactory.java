package org.zy.moonStone.core.interfaces.http.fileupload;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description 用于创建 {@link FileItem} 实例的工厂接口。在默认文件上传实现提供的配置之上，工厂可以提供它们自己的自定义配置。
 */
public interface FileItemFactory {
	/**
	 * 根据提供的参数和任何本地工厂配置创建一个新的 {@link FileItem} 实例。
     *
     * @param fieldName - 表单字段的名称
     * @param contentType - 表单字段的内容类型
     * @param isFormField - 如果这是一个普通表单字段，则为 <code>true</code> ;否则 <code>false</code> 。
     * @param fileName - 上载文件的名称(如果有的话)，由浏览器或其他客户端提供。
     *
     * @return 新创建的 {@link FileItem}
     */
    FileItem createItem(String fieldName, String contentType, boolean isFormField, String fileName);
}
