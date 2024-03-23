package org.zy.moonstone.core.webResources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.util.ResourceSet;
import org.zy.moonstone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractArchiveResourceSet extends AbstractResourceSet {
	/** war/jar 文件路径 URL */
	private URL baseUrl;
	/** war/jar 文件 URL 路径 */
    private String baseUrlString;

    /** jar 文件资源 */
    private JarFile archive = null;
    /** jar 文件树映射 */
    protected Map<String,JarEntry> archiveEntries = null;
    protected final Object archiveLock = new Object();
    /** archiveEntries 属性使用计数 */
    private long archiveUseCount = 0;


    protected final void setBaseUrl(URL baseUrl) {
        this.baseUrl = baseUrl;
        if (baseUrl == null) {
            this.baseUrlString = null;
        } else {
            this.baseUrlString = baseUrl.toString();
        }
    }

    
    @Override
    public final URL getBaseUrl() {
        return baseUrl;
    }

    protected final String getBaseUrlString() {
        return baseUrlString;
    }

    @Override
    public final String[] list(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ArrayList<String> result = new ArrayList<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar = getInternalPath() + path.substring(webAppMount.length());
            // 总是去掉前导的“/”来获取 JAR 路径
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            for (String name : getArchiveEntries(false).keySet()) {
                if (name.length() > pathInJar.length() && name.startsWith(pathInJar)) {
                	// 获得 jar 包名称
                    if (name.charAt(name.length() - 1) == '/') {
                        name = name.substring(pathInJar.length(), name.length() - 1);
                    } else {
                        name = name.substring(pathInJar.length());
                    }
                    if (name.length() == 0) {
                        continue;
                    }
                    if (name.charAt(0) == '/') {
                        name = name.substring(1);
                    }
                    if (name.length() > 0 && name.lastIndexOf('/') == -1) {
                        result.add(name);
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) { // 若 path 包含 webAppMount
            	// 获得 path 字符之后出现 "/" 字符的索引
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                	// 截取 path 之后的字符
                    return new String[] {webAppMount.substring(path.length())};
                } else {
                	// 截取 path 字符之后和 '/' 之前的字符
                    return new String[] {webAppMount.substring(path.length(), i)};
                }
            }
        }
        return result.toArray(new String[0]);
    }

    @Override
    public final Set<String> listWebAppPaths(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar = getInternalPath() + path.substring(webAppMount.length());
            // 总是去掉前导的 '/' 以获取 JAR 路径并确保它以 '/' 结尾
            if (pathInJar.length() > 0) {
                if (pathInJar.charAt(pathInJar.length() - 1) != '/') {
                    pathInJar = pathInJar.substring(1) + '/';
                }
                if (pathInJar.charAt(0) == '/') {
                    pathInJar = pathInJar.substring(1);
                }
            }

            for (String name : getArchiveEntries(false).keySet()) {
                if (name.length() > pathInJar.length() && name.startsWith(pathInJar)) {
                    int nextSlash = name.indexOf('/', pathInJar.length());
                    if (nextSlash != -1 && nextSlash != name.length() - 1) {
                        name = name.substring(0, nextSlash + 1);
                    }
                    result.add(webAppMount + '/' + name.substring(getInternalPath().length()));
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    result.add(webAppMount + "/");
                } else {
                    result.add(webAppMount.substring(0, i + 1));
                }
            }
        }
        result.setLocked(true);
        return result;
    }

    @Override
    public final WebResource getResource(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        WebResourceRoot root = getRoot();

        /*
         * 实现注意事项
         * 
         * 传入此方法的path参数总是以'/'开头。
         * 
         * 传入此方法的路径参数可以以'/'结尾，也可以不以'/'结尾。无论名称是否以'/'结尾，JarFile.getEntry()将返回一个匹配的目录条目。
         * 但是，如果请求条目时没有使用“/”，后续调用JarEntry.isDirectory()将返回false。
         * 
         * jar文件中的路径从不以“/”开头。在任何JarFile.getEntry()调用之前，需要删除开头的'/'。
         * 
         * 如果JAR挂载在web应用程序根目录下，那么对挂载点以外的请求返回一个空资源。
         */
        if (path.startsWith(webAppMount)) {
            String pathInJar = getInternalPath() + path.substring(webAppMount.length(), path.length());
            // 始终去掉前导“/”以获得JAR路径
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            if (pathInJar.equals("")) {
                // 特殊情况：这是目录资源，因此路径必须以 / 结尾
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                return new JarResourceRoot(root, new File(getBase()), baseUrlString, path);
            } else {
                JarEntry jarEntry = null;
                if (isMultiRelease()) {
                    // 调用支持多版本的 JarFile.getJarEntry()
                    jarEntry = getArchiveEntry(pathInJar);
                } else {
                    Map<String,JarEntry> jarEntries = getArchiveEntries(true);
                    if (!(pathInJar.charAt(pathInJar.length() - 1) == '/')) {
                        if (jarEntries == null) {
                            jarEntry = getArchiveEntry(pathInJar + '/');
                        } else {
                        	/**
                        	 * 返回指定键映射到的值，如果此映射不包含该键的映射，则返回 null。
                        	 * 
                        	 * 更正式地说，如果此映射包含从键 k 到值 v 的映射，使得 (key==null ? k==null :key.equals(k))，则此方法返回 v； 否则返回null。 （最多可以有一个这样的映射。）
                        	 * 
                        	 * 如果此映射允许空值，则返回值 null 不一定表示映射不包含键的映射； map 也可能将键显式映射为 null。 containsKey 操作可用于区分这两种情况。
                        	 * 
                        	 * @param key: 要返回其关联值的键
                        	 * @return 指定键映射到的值，如果此映射不包含该键的映射，则为 null
                        	 * @exception ClassCastException - 如果键的类型不适合此 Map（可选）
                        	 * @exception NullPointerException - 如果指定的键为空并且此映射不允许空键（可选）
                        	 */
                            jarEntry = jarEntries.get(pathInJar + '/'); // 在此假定 pathInJar 路径指向一个目录
                        }
                        if (jarEntry != null) {
                            path = path + '/';
                        }
                    }
                    if (jarEntry == null) {
                        if (jarEntries == null) {
                            jarEntry = getArchiveEntry(pathInJar);
                        } else {
                            jarEntry = jarEntries.get(pathInJar);
                        }
                    }
                }
                if (jarEntry == null) {
                    return new EmptyResource(root, path);
                } else {
                    return createArchiveResource(jarEntry, path, getManifest());
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }


    /**
     * 获取Jar文件中的条目映射。可能返回null，在这种情况下，应该使用 {@link #getArchiveEntry(String)}
     *
     * @param single 此请求是否支持单个查找？如果 false，将始终返回 Map。如果为 true，则实现可以将其用作确定最佳响应方式的提示。
     * @return 如果应该使用 {@link #getArchiveEntry(String)}，则归档条目映射到它们的名称或 null。
     */
    protected abstract Map<String,JarEntry> getArchiveEntries(boolean single);


    /**
     * 出于性能原因，应始终首先调用 {@link #getArchiveEntries(boolean)}，如果返回，则在映射中查找归档条目。仅当该调用返回 null 时，才应使用此方法。
     *
     * @param pathInArchive - 所需条目在存档中的路径
     * @return 指定的存档条目; 如果不存在, 则返回NULL
     */
    protected abstract JarEntry getArchiveEntry(String pathInArchive);

    @Override
    public final boolean mkdir(String path) {
        checkPath(path);

        return false;
    }

    @Override
    public final boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException("DirResourceSet 输入流写入为 NULL");
        }

        return false;
    }
    
    /**
     * 这个 JarFile 是多版本 JAR 文件吗.
     * @return 如果它是一个多版本 JAR 文件并且被配置为这样则返回 true
     */
    protected abstract boolean isMultiRelease();

    /**
     * 
     * @param jarEntry
     * @param webAppPath
     * @param manifest
     * @return
     */
    protected abstract WebResource createArchiveResource(JarEntry jarEntry, String webAppPath, Manifest manifest);

    @Override
    public final boolean isReadOnly() {
        return true;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            // 这是硬编码的默认值 - 忽略调用
            return;
        }

        throw new IllegalArgumentException("设置 readOnly 为 false");
    }

    /**
     * 
     * @return 当前资源的 JarFIle 实例
     * @throws IOException 如果创建 JarFile 实例时发生 I/O 错误
     */
    protected JarFile openJarFile() throws IOException  {
        synchronized (archiveLock) {
            if (archive == null) {
                archive = JreCompat.getInstance().jarFileNewInstance(getBase());
            }
            archiveUseCount++;
            return archive;
        }
    }

    protected void closeJarFile() {
        synchronized (archiveLock) {
            archiveUseCount--;
        }
    }

    @Override
    public void gc() {
        synchronized (archiveLock) {
            if (archive != null && archiveUseCount == 0) {
                try {
                    archive.close();
                } catch (IOException e) {
                    // Log at least WARN
                }
                archive = null;
                archiveEntries = null;
            }
        }
    }
}
