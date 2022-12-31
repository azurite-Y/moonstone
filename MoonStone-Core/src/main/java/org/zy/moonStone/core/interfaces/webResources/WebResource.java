package org.zy.moonStone.core.interfaces.webResources;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description 表示 Web 应用程序中的文件或目录。 它大量借鉴了 java.io.File。
 */
public interface WebResource {
	/**
	 * 返回此抽象路径名表示的文件最后一次修改的时间。
	 * <p>
	 * 如果需要区分 I/O 异常与返回 0L 的情况，或者同时需要同一文件的多个属性，或者需要最后访问时间或创建时间的情况，则可以使用 Files.readAttributes 方法。
	 * 
     */
    long getLastModified();

    /**
     * @return RFC 2616 指定的 HTTP Last-Modified 标头的正确格式的此资源的最后修改时间。
     */
    String getLastModifiedHttp();

    /**
     * 测试此抽象路径名表示的文件或目录是否存在。
     * @return 当且仅当此抽象路径名表示的文件或目录存在时才为true； 否则为false
     */
    boolean exists();

    /**
     * 指示应用程序是否需要此资源才能正确扫描文件结构，但该资源在主 Web 资源集中或任何其他 Web 资源集中均不存在。 
     * 例如，如果一个外部目录被映射到一个空的 web 应用程序中的 /WEB-INF/lib，那么 /WEB-INF 将被表示为一个虚拟资源。
     *
     * @return 是虚拟资源则为<code>true</code>
     */
    boolean isVirtual();
    
	/**
	 * 测试此抽象路径名表示的文件是否为目录。
	 * 
	 * 如果需要区分I/O异常与文件不是目录的情况，或者同时需要同一文件的几个属性，则可以使用Files.ReadAttributes方法。
	 * 
	 * @return 当且仅当此抽象路径名表示的文件存在并且是目录时为 true；否则为 false
	 */
    boolean isDirectory();

    /**
     * 测试此抽象路径名表示的文件是否为普通文件。 如果文件不是目录，并且满足其他系统相关标准，则该文件是正常的。 Java 应用程序创建的任何非目录文件都保证是普通文件。
     * <p>
     * 如果需要区分I/O异常和文件不是普通文件的情况，或者同时需要同一文件的多个属性，则可以使用Files.readAttributes方法。
     */
    boolean isFile();

    /**
     * 删除此抽象路径名表示的文件或目录。 如果此路径名表示一个目录，则该目录必须为空才能被删除。
     * <p>
     * 请注意，{@link java.nio.file.Files }类定义了 delete 方法以在无法删除文件时抛出 IOException。 这对于错误报告和诊断无法删除文件的原因很有用。
     * 
     * @return 当且仅当文件或目录被成功删除时为true； 否则为false
     */
    boolean delete();

    /**
     * @return 此抽象路径名表示的文件或目录的名称。 这只是路径名的名称序列中的最后一个名称。 如果路径名的名称序列为空，则返回空字符串。
     */
    String getName();

    /**
     * @return {@link java.io.File#length()}.
     * 返回此抽象路径名表示的文件的长度。如果此路径名表示目录，则返回值未指定。
     * <p>
     * 如果需要区分 I/O 异常和返回 0L 的情况，或者同时需要同一文件的多个属性，则可以使用 Files.readAttributes 方法。
     * 
     * @return 此抽象路径名表示的文件的长度（以字节为单位），如果文件不存在，则为 0L。 某些操作系统可能会为路径名返回 0L，表示系统相关的实体，例如设备或管道。
     */
    long getContentLength();

    /**
     * 返回此抽象路径名的规范路径名字符串。
     * <p>
     * 规范路径名是绝对且唯一的。规范形式的精确定义是系统相关的。如果需要，此方法首先将此路径名转换为绝对形式，就像调用 getAbsolutePath 方法一样，然后以系统相关的方式将其映射到其唯一形式。
     * 这通常涉及删除冗余名称，例如“.”。和路径名中的“..”，解析符号链接（在 UNIX 平台上），并将驱动器号转换为标准大小写（在 Microsoft Windows 平台上）。
     * <p>
     * 每个表示现有文件或目录的路径名都具有唯一的规范形式。每个表示不存在的文件或目录的路径名也具有唯一的规范形式。不存在的文件或目录的路径名的规范形式可能与创建文件或目录后相同路径名的规范形式不同。
     * 类似地，现有文件或目录的路径名的规范形式可能与删除文件或目录后相同路径名的规范形式不同。
     * @return 表示与此抽象路径名相同的文件或目录的规范路径名字符串。
     */
    String getCanonicalPath();

    /**
     * 测试应用程序是否可以读取此抽象路径名表示的文件。 在某些平台上，可以使用允许它读取标记为不可读的文件的特殊权限启动 Java 虚拟机。 因此，即使文件没有读取权限，此方法也可能返回 true。
     * 
     * @return 当且仅当此抽象路径名指定的文件存在并且应用程序可以读取时，才为true； 否则为false。
     */
    boolean canRead();

    /**
     * @return 此资源相对于 Web 应用程序根的路径。 资源是目录，返回值会以'/'结尾。
     */
    String getWebappPath();

    /**
     * 如果可用（当前不支持）返回强 ETag，否则返回根据内容长度计算并最后修改的弱 ETag。
     *
     * @return  此资源的 ETag
     */
    String getETag();

    /**
     * 设置此资源的 MIME 类型。
     *
     * @param mimeType - 将与资源关联的 mime 类型
     */
    void setMimeType(String mimeType);

    /**
     * @return 此资源的 MIME 类型
     */
    String getMimeType();

    /**
     * 根据该资源的内容获取一个 InputStream。
     *
     * @return  InputStream 基于此资源的内容，如果资源不存在或不代表文件，则为 null
     */
    InputStream getInputStream();

    /**
     * @return 此资源的二进制内容，如果它在 byte[] 中不可用，则返回 null，例如，它太大了。
     */
    byte[] getContent();

    /**
     * @return 创建文件的时间。 如果不可用，将返回 {@link #getLastModified()} 的结果。
     */
    long getCreation();

    /**
     * @return 访问资源的 URL，如果没有此类可用 URL 或资源不存在，则返回 null。
     */
    URL getURL();

    /**
     * @return 此资源的二进制内容，如果它在 byte[] 中不可用，则返回 null，例如，它太大了。
     */
    URL getCodeBase();

    /**
     * @return 对此 WebResource 所在的 WebResourceRoot 的引用。
     */
    WebResourceRoot getWebResourceRoot();

    /**
     * @return 用于签署此资源以验证它的证书，如果没有则为 null。
     *
     * @see java.util.jar.JarEntry#getCertificates()
     */
    Certificate[] getCertificates();

    /**
     * @return 与此资源关联的清单，如果没有则为 null。
     *
     * @see java.util.jar.JarFile#getManifest()
     */
    Manifest getManifest();
}
