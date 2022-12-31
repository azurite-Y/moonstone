package org.zy.moonStone.core.interfaces.webResources;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Lifecycle;
import org.zy.moonStone.core.loaer.WebappClassLoaderBase;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description
 */
/**
 * 表示 Web 应用程序的完整资源集。 Web 应用程序的资源由多个 ResourceSets 组成，在查找 Resource 时，ResourceSets 按以下顺序处理：
 * <ol>
 * 		<li>Pre - 由 web 应用程序的 context.xml 中的 <PreResource> 元素定义的资源。将按照指定的顺序搜索资源。</li>
 * 		<li>Main - Web 应用程序的主要资源 - 即 WAR 或包含扩展 WAR 的目录</li>
 * 		<li>JARs - Servlet 规范定义的资源 JAR。 JAR 将按照它们添加到 ResourceRoot 的顺序进行搜索。</li>
 * 		<li>Post - 由 web 应用程序的 context.xml 中的 <PostResource> 元素定义的资源。将按照指定的顺序搜索资源。</li>
 * 		<li></li>
 * </ol>
 * 
 * 应注意以下约定：
 * <ul>
		<li>写入操作（包括删除）将仅应用于 mainResourceSet。如果其他资源集中的一个资源有效地使主资源集上的操作无效，则写入操作将失败。</li>
 * 		<li> ResourceSet 中的文件将隐藏 ResourceSet 中的同名目录（以及该目录的所有内容），该目录位于搜索顺序的后面。</li>
 * 		<li>只有主资源集可以定义 META-INF/context.xml，因为该文件定义了 Pre- 和 Post-Resources。</li>
 * 		<li>根据 Servlet 规范，资源 JAR 中的任何 META-INF 或 WEB-INF 目录都将被忽略。</li>
 * 		<li>Pre- 和 Post-Resources 可以定义 WEB-INF/lib 和 WEB-INF/classes，以便为 web 应用程序提供额外的库和/或类。</li>
 * </ul>
 * 
 * 此机制替换并扩展了早期版本中存在的以下功能：
 * <ul>
 * 		<li>别名 - 由 Post-Resources 取代，增加了对单个文件以及目录和 JAR 的支持。</li>
 * 		<li>虚拟 WebappLoader - 替换为映射到 WEB-INF/lib 和WEB-INF/classes 的Pre-和Post-Resources</li>
 * 		<li>虚拟 DirContext - 替换为前置和后置资源</li>
 * 		<li>外部存储库 - 替换为映射到 WEB-INF/lib 和 WEB-INF/classes 的 Pre- 和 Post-Resources</li>
 * 		<li>资源JAR - 相同的功能，但使用与所有其他附加资源相同的机制实现。</li>
 * </ul>
 * 
 */
public interface WebResourceRoot extends Lifecycle {
	/**
     * 获取表示给定路径上资源的对象。 请注意，该路径上的资源可能不存在。 
     * 如果路径不存在，则返回的 WebResource 将与主 WebResourceSet 相关联。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return  表示给定路径上的资源的对象
     */
    WebResource getResource(String path);

    /**
     * 获取表示给定路径上资源的对象。 请注意，该路径上的资源可能不存在。 如果路径不存在，则返回的 WebResource 将与主 WebResourceSet 相关联。 
     * 这将包括所有匹配项，即使该资源通常无法访问（例如，因为它被另一个资源覆盖）
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
     * @return  表示给定路径上的资源的对象数组
     */
    WebResource[] getResources(String path);
    
    /**
     * 获取表示给定路径中的类加载器资源的对象。 WEB-INF/classes 总是在搜索 WEB-INF/lib 中的 JAR 文件之前搜索。 
     * JAR 文件的搜索顺序将在对该方法的后续调用中保持一致，直到重新加载 Web 应用程序。 不保证 JAR 文件的搜索顺序可能是什么。
     *
     * @param path - 相关类加载器资源的路径相对于此 Web 应用程序的类加载器资源的根目录。
     * @return  表示给定路径上的类加载器资源的对象
     * @deprecated 作为老旧web项目的保留方法
     * @see
     */
//    WebResource getClassLoaderResource(String path);

    /**
     * 获取表示给定路径中的类加载器资源的对象。 请注意，该路径上的资源可能不存在。
     * 如果路径不存在，则返回的 WebResource 将与 mainWebResourceSet 关联。 
     * 这将包括所有匹配项，即使该资源通常无法访问（例如，因为它被另一个资源覆盖）
     *
     * @param path - 相关类加载器资源的路径相对于 Web 应用程序的类加载器资源的根目录。 它必须以“/”开头。
     * @return 表示给定路径上的类加载器资源的对象
     * @deprecated 作为老旧web项目的保留方法
     */
//    WebResource[] getClassLoaderResources(String path);

    
    /**
     * 获取表示给定路径中的类加载器资源对象。
     * 
     * @param path - 相关类加载器资源的路径相对于 Web 应用程序的类加载器资源的根目录。 它必须以“/”开头。
     * @return 表示给定路径上的类加载器资源的对象
     */
    WebResource getWebClassLoaderResource(String path);
    
    /**
     * 获取表示给定路径中的类加载器资源对象。
     * 
     * @param path - 相关类加载器资源的路径相对于 Web 应用程序的类加载器资源的根目录。 它必须以“/”开头。
     * @return 表示给定路径上的类加载器资源的对象集
     */
    WebResource[] getWebClassLoaderResources(String path);
    
    /**
     * 获取位于指定目录中的所有文件和目录的名称列表。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return 资源列表。 如果路径不引用目录，则将返回零长度数组。
     */
    String[] list(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的 Web 应用程序路径名集。 表示目录的路径将以“/”字符结尾
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头
     * @return 此资源集。如果路径不指向目录，则将返回 null
     */
    Set<String> listWebAppPaths(String path);

    /**
     * 获取指定目录下所有WebResources的列表。
     *
     * @param path - 相关资源相对于 Web 应用程序根的路径。 它必须以“/”开头。
     * @return 资源列表。 如果路径不引用目录，则将返回零长度数组。
     */
    WebResource[] listResources(String path);

    /**
     * 在给定路径创建一个新目录。
     *
     * @param path - 要创建的新资源相对于 Web 应用程序根目录的路径。 它必须以“/”开头。
     * @return  如果目录已创建，则为 true，否则为 false
     */
    boolean mkdir(String path);

    /**
     * 使用提供的 InputStream 在请求的路径上创建一个新资源。
     *
     * @param path - 用于新资源的路径。 它相对于 Web 应用程序的根目录，并且必须以“/”开头。
     * @param is - 将为新资源提供内容的 InputStream。
     * @param overwrite - 如果为 true 并且资源已经存在，它将被覆盖。 如果为 false 并且资源已经存在，则写入将失败。
     * @return 当且仅当新资源被写入时为true
     */
    boolean write(String path, InputStream is, boolean overwrite);

    /**
     * 基于提供的参数为此 {@link WebResourceRoot} 创建一个新的 {@link WebResourceSet}
     * 
     * @param type - 要创建的 {@link WebResourceSet} 的类型
     * @param webAppMount - 应在 Web 应用程序中发布资源的路径。 它必须以“/”开头
     * @param url - 资源的 URL（必须找到 JAR、文件或目录）
     * @param internalPath - 要在其中找到内容的资源中的路径。 它必须以“/”开头
     * @return 根据给定参数创建的WebResourceSet实例
     */
    void createWebResourceSet(ResourceSetType type, String webAppMount, URL url, String internalPath);

    /**
     * 基于提供的参数为此 {@link WebResourceRoot} 创建一个新的 {@link WebResourceSet}
     *
     * @param type - 要创建的 WebResourceSet 的类型
     * @param webAppMount - 应在 Web 应用程序中发布资源的路径。 它必须以“/”开头
     * @param base - 资源的位置
     * @param archivePath - 资源中的路径到要在其中找到内容的存档。 如果没有存档，那么这应该为空
     * @param internalPath - 存档中的路径（如果archivePath 为空，则为要在其中找到内容的资源。它必须以“/”开头
     * @return 根据给定参数创建的WebResourceSet实例
     */
    void createWebResourceSet(ResourceSetType type, String webAppMount, String base, String archivePath, String internalPath);

    /**
     * 将提供的 WebResourceSet 作为“Pre”资源添加到此 Web 应用程序。
     *
     * @param webResourceSet - 要使用的资源集
     */
    void addPreResources(WebResourceSet webResourceSet);

    /**
     * @return 配置到此 Web 应用程序的 WebResourceSet 列表作为“Pre”资源。
     */
    WebResourceSet[] getPreResources();

    /**
     * 将提供的 WebResourceSet 作为“Jar”资源添加到此 Web 应用程序。
     *
     * @param webResourceSet - 要使用的资源集
     */
    void addJarResources(WebResourceSet webResourceSet);

    /**
     * @return 配置到此 Web 应用程序的 WebResourceSet 列表作为“Jar”资源。
     */
    WebResourceSet[] getJarResources();

    /**
     * 将提供的 WebResourceSet 作为“Post”资源添加到此 Web 应用程序。
     *
     * @param webResourceSet - 要使用的资源集
     */
    void addPostResources(WebResourceSet webResourceSet);

    /**
     * @return 配置到此 Web 应用程序的 WebResourceSet 列表作为“Post”资源。
     */
    WebResourceSet[] getPostResources();

    /**
     * @return 与此 WebResourceRoot 关联的 Web 应用程序。
     */
    Context getContext();

    /**
     * 设置与此 WebResourceRoot 关联的 Web 应用程序。
     *
     * @param context - 关联的上下文
     */
    void setContext(Context context);

    /**
     * 配置此资源是否允许使用符号链接。
     *
     * @param allowLinking - 如果允许符号链接，则为 true。
     */
    void setAllowLinking(boolean allowLinking);

    /**
     * 确定此资源是否允许使用符号链接
     *
     * @return  如果允许符号链接，则为 <code>true</code>
     */
    boolean getAllowLinking();

    /**
     * 设置此 Web 应用程序是否允许缓存
     *
     * @param cachingAllowed - true 启用缓存，否则 false
     */
    void setCachingAllowed(boolean cachingAllowed);

    /**
     * @return 如果此 Web 应用程序允许缓存，则为 true
     */
    boolean isCachingAllowed();

    /**
     * 设置缓存条目的生存时间 (TTL)。
     *
     * @param ttl - 以毫秒为单位的 TTL
     */
    void setCacheTtl(long ttl);

    /**
     * 获取缓存条目的生存时间 (TTL)。
     *
     * @return  TTL - 以毫秒为单位
     */
    long getCacheTtl();

    /**
     * 设置缓存的最大允许大小
     *
     * @param cacheMaxSize - 最大缓存大小（以kb为单位）
     */
    void setCacheMaxSize(long cacheMaxSize);

    /**
     * 获取缓存的最大允许大小。
     *
     * @return  Maximum - 以kb为单位的缓存大小
     */
    long getCacheMaxSize();

    /**
     * 设置缓存中单个对象的最大允许大小。 请注意，以字节为单位的最大大小不得超过 Integer.MAX_VALUE。
     *
     * @param cacheObjectMaxSize - 单个缓存对象的最大大小（以kb为单位）
     */
    void setCacheObjectMaxSize(int cacheObjectMaxSize);

    /**
     * 获取缓存中单个对象的最大允许大小。 请注意，以字节为单位的最大大小不得超过 {@link Integer#MAX_VALUE}
     *
     * @return  Maximum - 单个缓存对象的大小（以kb为单位）
     */
    int getCacheObjectMaxSize();

    /**
     * 控制是否启用轨道锁定文件功能。 
     * 如果启用，所有对返回对象的方法的调用都会锁定文件并且需要关闭才能释放该锁定（例如 WebResource.getInputStream() 将执行许多额外的任务。
     * <ul>
     *   <li>调用方法时的堆栈跟踪将被记录并与返回的对象相关联</li>
     *   <li>将包装返回的对象，以便可以检测调用close()（或等效方法）以释放资源的点。一旦资源被释放，对象的跟踪将停止</li>
     *   <li>Web 应用程序关闭时所有剩余的锁定资源将被记录，然后关闭</li>
     * </ul>
     *
     * @param trackLockedFiles - {@code true} 启用它, {@code false} 禁用它
     */
    void setTrackLockedFiles(boolean trackLockedFiles);

    /**
     * 是否启用了跟踪锁定文件功能？
     *
     * @return 如果已启用，则为 {@code true}，否则为 {@code false}
     */
    boolean getTrackLockedFiles();

    /**
     * 此方法将由上下文定期调用，并允许实现一个执行周期性任务的方法，例如清除过期的缓存条目。
     */
    void backgroundProcess();

    /**
     * 将指定的资源添加到跟踪中，以便以后能够在停止时释放资源。
     * @param trackedResource - 将被跟踪的资源
     */
    void registerTrackedResource(TrackedWebResource trackedResource);

    /**
     * 一旦不再需要释放资源，就停止跟踪指定的资源
     * @param trackedResource - 被跟踪的资源
     */
    void deregisterTrackedResource(TrackedWebResource trackedResource);

    /**
     * @return 此根使用的所有 {@link WebResourceSet}s 的 {@link WebResourceSet#getBaseUrl()} 集。
     */
    List<URL> getBaseUrls();

    /**
     * 实现可能会缓存一些信息以提高性能。 此方法触发对这些资源的清理。
     */
    void gc();

    /**
     * 设置额外追加监视的资源到相关的类加载器
     * 
     * @param webappClassLoaderBase - 监视资源更改时间的类加载器
     */
    void additionalResourceMonitoring(WebappClassLoaderBase webappClassLoaderBase);
    
    enum ResourceSetType { PRE, RESOURCE_JAR, POST, CLASSES_JAR }
}
