package org.zy.moonstone.core.interfaces.http.fileupload;

import java.util.Iterator;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description 该类支持访问在 <code>multipart/form-data</code> POST请求中收到的文件或表单项的头属性。
 */
public interface FileItemHeaders {
	/**
	 * 以String形式返回指定表单头的值。
	 * 
	 * 如果该部件不包含指定名称的表头，则此方法返回null。如果有多个具有相同名称的标头，此方法将返回项中的第一个标头。头名不区分大小写。
	 * 
     * @param name - 指定头名称的字符串
     */
    String getHeader(String name);

    /**
     * <p>
     * 作为String对象的迭代器返回指定项表单头的所有值。
     * <p>
     * 如果不包含指定名称的任何表单头，此方法将返回一个空的  <code>Iterator</code>。头名称不区分大小写。
     *
     * @param name - 指定表单头名称的字符串
     * @return 包含指定表单头值的迭代器。如果没有该名称的任何头文件，则返回一个空的 <code>Iterator</code>
     */
    Iterator<String> getHeaders(String name);

    /**
     * <p>
     * 返回所有表单头名称的迭代器
     * </p>
     *
     * @return 包含此表单项提供的所有表单头名称的迭代器。如果表单内没有任何头，则返回一个空的 <code>Iterator</code> 
     */
    Iterator<String> getHeaderNames();

    
    /**
     * 将表单头值添加到此实例的方法
     *
     * @param name - 表单头名称
     * @param value - 表单头值
     */
	void addHeader(String name, String value);
}
