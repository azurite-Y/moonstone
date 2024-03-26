package org.zy.moonstone.core.interfaces.webResources;

import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import org.zy.moonstone.core.interfaces.container.Lifecycle;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description 表示作为 Web 应用程序一部分的一组资源。 示例包括目录结构、资源 JAR 和 WAR 文件。
 */
public interface WebResourceSet extends Lifecycle {
	/**
     * 获取表示给定路径上资源的对象。 请注意，该路径上的资源可能不存在。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return 表示给定路径上的资源的对象
     */
    WebResource getResource(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的名称列表。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return 资源列表。 如果路径不引用目录，则将返回零长度数组。
     */
    String[] list(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的 Web 应用程序路径名集。 表示目录的路径将以“/”字符结尾。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return 资源集。 如果路径不引用目录，则将返回一个空集。
     */
    Set<String> listWebAppPaths(String path);
    
    /**
     * 在给定路径创建一个新目录
     *
     * @param path - 要创建的新资源相对于 Web 应用程序根目录的路径。 它必须以“/”开头。
     * @return 如果目录已创建，则为 true，否则为 false
     */
    boolean mkdir(String path);

    /**
     * 使用提供的InputStream 在请求的路径创建一个新资源
     *
     * @param path - 用于新资源的路径。 它相对于 Web 应用程序的根目录，并且必须以“/”开头。
     * @param is - 将为新资源提供内容的 InputStream
     * @param overwrite - 如果为 true 并且资源已经存在，它将被覆盖。 如果为 false 并且资源已经存在，则写入将失败。
     *
     * @return 当且仅当写入新的资源时为 <code>true</code>
     */
    boolean write(String path, InputStream is, boolean overwrite);

    void setRoot(WebResourceRoot root);

    /**
     * 此资源集返回的资源是否应该只包含在显式查找类加载器资源的结果中, 即这些资源应该从显式查找静态(非类加载器)资源的查找中排除
     *
     * @return 如果这些资源应仅用于类加载器资源查找，则为 true，否则为 false
     */
    boolean getClassLoaderOnly();

    void setClassLoaderOnly(boolean classLoaderOnly);

    /**
     * 当查找显式查找静态（非类加载器）资源时，此资源集返回的资源是否应该仅包含在任何结果中。
     * 即这些资源是否应该从明确查找类加载器资源的查找中排除。
     *
     * @return 如果这些资源只能用于静态（非类加载器）资源查找，则为 true，否则为 false
     */
    boolean getStaticOnly();

    void setStaticOnly(boolean staticOnly);

    /**
     * 获取这组资源的基本 URL。 它的用途之一是在安全管理器下运行时授予对资源的读取权限。
     *
     * @return 这组资源的基本 URL
     */
    URL getBaseUrl();

    /**
     * 配置这组资源是否为只读
     *
     * @param readOnly - 如果这组资源应配置为只读，则为 true
     * @throws IllegalArgumentException - 如果尝试配置硬编码为只读可写的 {@link WebResourceSet}
     */
    void setReadOnly(boolean readOnly);

    /**
     * 获取这组资源的只读设置的当前值
     *
     * @return 如果这组资源配置为只读，则为 true，否则为 false
     */
    boolean isReadOnly();

    /**
     * 实现可能会缓存一些信息以提高性能。 此方法触发对这些资源的清理
     */
    void gc();
}
