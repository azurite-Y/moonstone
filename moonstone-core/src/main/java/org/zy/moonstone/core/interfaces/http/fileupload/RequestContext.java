package org.zy.moonstone.core.interfaces.http.fileupload;

import java.util.function.Supplier;

/**
 * @dateTime 2022年11月22日;
 * @author zy(azurite-Y);
 * @description 抽象对文件上传所需的请求信息的访问。这个接口应该为FileUpload处理的每一种类型的请求实现，比如servlet和portlet。
 */
public interface RequestContext {
	/**
     * 检索请求的字符编码
     * 
     * @return 请求的字符编码
     */
    String getCharacterEncoding();

    /**
     * 检索请求的内容类型
     *
     * @return 请求的内容类型
     */
    String getContentType();

	/**
	 * @return 请求行数据的延迟提供者
	 */
	Supplier<Byte> getRequestBodySupplier();
    
    /**
     * 检索请求的输入流
     *
     * @return 请求的输入流
     *
     * @throws IOException - 如果出现问题
     */
//    InputStream getInputStream() throws IOException;
    
	/**
	 * 检索请求的内容长度
     *
     * @return 请求的内容长度
     */
    long getContentLength();
    
    /**
     * @return POST 请求体数据边界字节数组 
     */
    byte[] getBoundaryArray();
}
