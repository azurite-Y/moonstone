package org.zy.moonStone.core.http.fileupload;

import java.io.File;

import org.zy.moonStone.core.interfaces.http.fileupload.FileItem;
import org.zy.moonStone.core.interfaces.http.fileupload.FileItemFactory;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description
 * 默认的 {@link FileItemFactory}。此实现创建 {@link FileItem} 实例，这些实例将内容保存在内存中（对于较小的文件），
 * 或保存在磁盘上的临时文件中（对于较大的项目）。内容将存储在磁盘上的大小阈值是可配置的，创建临时文件的目录也是可配置的。
 * <p>
 * 如果未另行配置，则默认配置值如下：
 * <ul>
 *   <li>大小阈值为10KB</li>
 *   <li>Repository是系统默认的临时目录，由<code>System.getProperty("java.io.tmpdir")</code>返回</li>
 * </ul>
 * <p>
 * 注意:在系统默认临时目录中创建的文件具有可预测的名称。这意味着对该目录具有写访问权限的本地攻击者可以执行TOUTOC攻击，
 * 用攻击者选择的文件替换任何上传的文件。这将取决于如何使用上传的文件，但可能是重要的。
 * 当在本地不受信任的用户环境中使用此实现时，必须使用setRepository(File)配置一个不可公开写入的存储库位置。
 * 在Servlet容器中，可以使用Servlet上下文属性javax. Servlet .context. tempdir确定的位置。
 * <p>
 * 为 {@link FileItem } 创建的临时文件应该稍后删除。
 */
public class DiskFileItemFactory implements FileItemFactory {
	/**
     * 超过该阈值的上载将存储在磁盘上
     */
    public static final int DEFAULT_SIZE_THRESHOLD = 10240;

    /**
     * 如果存储在磁盘上，则存储上传文件的目录
     */
    private File repository;

    /**
     * 高于该阈值的上传将存储在磁盘上
     */
    private int sizeThreshold = DEFAULT_SIZE_THRESHOLD;

    /**
     * 当发送方没有提供显式的字符集参数时使用的默认内容字符集
     */
    private String defaultCharset = DiskFileItem.DEFAULT_CHARSET;

    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 构造该类的未配置实例。生成的工厂可以通过调用适当的setter方法进行配置。
     */
    public DiskFileItemFactory() {
        this(DEFAULT_SIZE_THRESHOLD, null);
    }

    /**
     * 构造该类的预配置实例
     *
     * @param sizeThreshold - 阈值(以字节为单位)，低于该阈值的项将保留在内存中，高于该阈值的项将作为文件存储。
     * @param repository - 如果项的大小超过阈值，则数据存储库(将在其中创建文件的目录)将被删除。
     */
    public DiskFileItemFactory(int sizeThreshold, File repository) {
        this.sizeThreshold = sizeThreshold;
        this.repository = repository;
    }


    /**
     * 返回用于临时存储大于配置的大小阈值的文件的目录。
     *
     * @return 存放临时文件的目录
     *
     * @see #setRepository(java.io.File)
     */
    public File getRepository() {
        return repository;
    }

    /**
     * 设置用于临时存储大于配置的大小阈值的文件的目录。
     *
     * @param repository - 存放临时文件的目录
     *
     * @see #getRepository()
     */
    public void setRepository(File repository) {
        this.repository = repository;
    }

    /**
     * 返回文件直接写入磁盘的大小阈值。缺省值是10240字节。
     *
     * @return 大小阈值，以字节为单位。
     *
     * @see #setSizeThreshold(int)
     */
    public int getSizeThreshold() {
        return sizeThreshold;
    }

    /**
     * 设置文件直接写入磁盘的大小阈值。
     *
     * @param sizeThreshold - 大小阈值，以字节为单位。
     *
     * @see #getSizeThreshold()
     *
     */
    public void setSizeThreshold(int sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }


    /**
     * 根据提供的参数和本地工厂配置创建一个新的{@link DiskFileItem}实例
     *
     * @param fieldName - 表单字段的名称
     * @param contentType - 表单字段的内容类型
     * @param isFormField - 如果这是一个普通表单字段，则为 true;否则 false。
     * @param fileName - 上载文件的名称(如果有的话)，由浏览器或其他客户端提供。
     *
     * @return 新创建的 {@link FileItem }  
     */
    @Override
    public FileItem createItem(String fieldName, String contentType, boolean isFormField, String fileName) {
        DiskFileItem result = new DiskFileItem(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
        result.setDefaultCharset(defaultCharset);
        return result;
    }

    /**
     * 当发送方没有提供显式的字符集参数时，返回使用的默认字符集。
     * 
     * @return 默认字符集
     */
    public String getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * 设置默认字符集，以便在发送方没有提供显式字符集参数时使用。
     * @param pCharset - 要设置的默认字符集
     */
    public void setDefaultCharset(String pCharset) {
        defaultCharset = pCharset;
    }
}
