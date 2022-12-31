package org.zy.moonStone.core.interfaces.http.fileupload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.zy.moonStone.core.exceptions.InvalidFileNameException;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description 该类表示在 <code>multipart/form-data</code> POST请求中接收到的文件或表单项。
 * <p>
 * 在从 FileUpload 实例中检索该类的实例之后，可以使用get()一次性请求文件的所有内容，
 * 或者使用getInputStream()请求InputStream并处理该文件，而不尝试将其加载到内存中，这对于大型文件来说可能很方便。
 * 
 * @see FileUpload#parseRequest(RequestContext))
 */
public interface FileItem extends FileItemHeadersSupport {
	
	// -------------------------------------------------------------------------------------
	// javax.activation.DataSource methods
	// -------------------------------------------------------------------------------------    
    /**
     * 返回一个可用于检索文件内容的 {@link java.io.InputStream InputStream}
     *
     * @return 一个可以用来检索文件内容的 {@link java.io.InputStream InputStream}。
     * @throws IOException - 如果发生错误
     */
    InputStream getInputStream() throws IOException;

    
    /**
     * 返回浏览器传递的内容类型，如果未定义则返回null。
     *
     * @return 由浏览器传递的内容类型，如果未定义则为空。 
     */
    String getContentType();

    
    /**
     * 返回浏览器(或其他客户端软件)提供的客户端文件系统中的原始文件名。在大多数情况下，这将是基文件名，没有路径信息。
     * 然而，一些客户端，如Opera浏览器，确实包含路径信息。
     *
     * @return 客户端文件系统中的原始文件名。
     * @throws InvalidFileNameException - 文件名包含NUL字符，这可能是安全攻击的指示。
     * 如果打算无论如何都使用文件名，请捕获异常并使用invalidfilenameexception#getName()。
     */
    String getName();

    
	// -------------------------------------------------------------------------------------
	// FileItem methods
	// -------------------------------------------------------------------------------------    
    /**
     * 提供是否从内存读取文件内容的提示
     *
     * @return 如果将从内存读取文件内容，则为 <code>true</code>;否则 <code>false</code>。
     */
    boolean isInMemory();

    
    /**
     * 返回文件项的大小
     *
     * @return 文件项的大小，以字节为单位。
     */
    long getSize();

    
    /**
     * 以字节数组的形式返回文件项的内容
     *
     * @return 作为字节数组的文件项内容
     */
    byte[] get();

    
    /**
     * 使用指定的编码将文件项的内容作为字符串返回。该方法使用 {@link #get()} 检索项的内容。
     *
     * @param encoding - 要使用的字符编码
     * @return 作为字符串的表单值内容
     * @throws UnsupportedEncodingException - 若指定的字符集编码不支持
     */
    String getString(String encoding) throws UnsupportedEncodingException;

    
    /**
     * 使用默认字符编码，以字符串形式返回文件项的内容。此方法使用 {@link #get()} 检索项的内容。
     *
     * @return 作为字符串的表单值内容
     */
    String getString();

    
    /**
     * 一种将上传的项写入磁盘的方便方法。
     * 客户端代码不关心项目是否存储在内存中，还是存储在磁盘上的临时位置。他们只是想把上传的条目写入一个文件。
     * <p>
     * 如果对同一项多次调用此方法，则不能保证成功。
     * 这允许一个特定的实现使用，例如，文件重命名，在可能的情况下，而不是复制所有的底层数据，从而获得显著的性能优势。
     *
     * @param file - 上传的需存储文件
     * @throws Exception - 如果发生错误
     */
    void write(File file) throws Exception;

    
    /**
     * 删除文件项的底层存储，包括删除任何相关的临时磁盘文件。
     * 尽管在垃圾回收 <code>FileItem</code> 实例时，该存储将被自动删除，但可以使用此方法确保在更早的时间完成此操作，从而保留系统资源。
     */
    void delete();

    
    /**
     * 返回与此文件项对应的<code>multipart/form-data</code> 表单字段的名称。
     *
     * @return 表单字段的名称
     */
    String getFieldName();

    
    /**
     * 设置用于引用此文件项的字段名
     *
     * @return 表单字段的名称
     */
    void setFieldName(String name);

    
    /**
     * 确定 <code>FileItem</code> 实例是否表示简单表单字段
     *
     * @return 如果实例表示一个简单的表单字段，则为 <code>true</code>;如果表示上传的文件，则为 <code>false</code>。
     */
    boolean isFormField();

    
    /**
     * 指定 <code>FileItem</code> 实例是否表示简单表单字段
     *
     * @param state - 如果实例表示一个简单的表单字段，则为 <code>true</code>;如果表示上传的文件，则为 <code>false</code>。
     */
    void setFormField(boolean state);

    
    /**
     * 返回一个可用于存储文件内容的 {@link java.io.OutputStream OutputStream}
     *
     * @return 可用于存储文件内容的 {@link java.io.OutputStream OutputStream}
     * @throws IOException - 如果发生错误
     */
    OutputStream getOutputStream() throws IOException;
}
