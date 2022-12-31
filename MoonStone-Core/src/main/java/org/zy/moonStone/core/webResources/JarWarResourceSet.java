package org.zy.moonStone.core.webResources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.interfaces.webResources.WebResourceSet;
import org.zy.moonStone.core.util.buf.UriUtil;
import org.zy.moonStone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示基于嵌套在打包的WAR文件中的JAR文件的 {@link WebResourceSet } 。这仅供 MoonStone 内部使用，因此不能通过配置创建。
 */
public class JarWarResourceSet extends AbstractArchiveResourceSet {
	/** 资源在 war/jar 包下的相对路径，即分隔符之后的路径 */
    private final String archivePath;

    
    /**
     * 基于嵌套在WAR中的JAR文件创建新的 {@link WebResourceSet}。
     *
     * @param root - 这个新 {@link WebResourceSet} 将被添加到的 {@link WebResourceRoot}。
     * @param webAppMount - Web应用程序内的路径，此 {@link WebResourceSet} 将在该路径上挂载。
     * @param base - JAR所在文件系统上的WAR文件的绝对路径。
     * @param archivePath - WAR文件中JAR文件所在的路径。
     * @param internalPath - 这个新 {@link WebResourceSet } 中的路径将从中提供资源。例如，对于资源JAR，这将是 "META-INF/Resources"
     * 
     * @throws IllegalArgumentException - 如果 webAppMount 或 internalPath 无效（有效路径必须以 '/' 开头）
     */
    public JarWarResourceSet(WebResourceRoot root, String webAppMount, String base, String archivePath, String internalPath) throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
        this.archivePath = archivePath;
        setInternalPath(internalPath);

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    
    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry, String webAppPath, Manifest manifest) {
        return new JarWarResource(this, webAppPath, getBaseUrlString(), jarEntry, archivePath);
    }

    /**
     * {@inheritDoc}
     * <p>
     * JarWar 无法针对单个资源进行优化，因此始终返回 Map。
     */
    @Override
    protected Map<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null) {
                JarFile warFile = null;
                InputStream jarFileIs = null;
                archiveEntries = new HashMap<>();
                boolean multiRelease = false;
                try {
                    warFile = openJarFile();
                    JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                    jarFileIs = warFile.getInputStream(jarFileInWar);

                    // 读取jar 文件项填充 archiveEntries
                    try (MoonStoneJarInputStream jarIs = new MoonStoneJarInputStream(jarFileIs)) {
                        JarEntry entry = jarIs.getNextJarEntry();
                        while (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                            entry = jarIs.getNextJarEntry();
                        }
                        Manifest m = jarIs.getManifest();
                        setManifest(m);
                        
                        if (m != null && JreCompat.isJre9Available()) {
                            String value = m.getMainAttributes().getValue("Multi-Release");
                            if (value != null) {
                                multiRelease = Boolean.parseBoolean(value);
                            }
                        }
                    }
                    if (multiRelease) {
                        processArchivesEntriesForMultiRelease();
                    }
                } catch (IOException ioe) {
                    // 不应该发生
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (warFile != null) {
                        closeJarFile();
                    }
                    if (jarFileIs != null) {
                        try {
                            jarFileIs.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }
            }
            return archiveEntries;
        }
    }

    /**
     * 待观察 
     */
    protected void processArchivesEntriesForMultiRelease() {
        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,VersionedJarEntry> versionedEntries = new HashMap<>();
        Iterator<Entry<String,JarEntry>> iter = archiveEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,JarEntry> entry = iter.next();
            String name = entry.getKey();
            if (name.startsWith("META-INF/versions/")) {
                // 删除多版本
                iter.remove();

                // 获取此版本化条目的基本名称和版本
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // 忽略针对比此运行时的目标版本更高版本的任何条目
                    if (version <= targetVersion) {
                        VersionedJarEntry versionedJarEntry = versionedEntries.get(baseName);
                        if (versionedJarEntry == null) {
                            // 找不到此名称的版本化条目。创建一个。
                            versionedEntries.put(baseName, new VersionedJarEntry(version, entry.getValue()));
                        } else {
                            // 忽略我们已经找到更高版本的任何条目
                            if (version > versionedJarEntry.getVersion()) {
                                // 替换针对较早版本的条目
                                versionedEntries.put(baseName, new VersionedJarEntry(version, entry.getValue()));
                            }
                        }
                    }
                }
            }
        }

        for (Entry<String,VersionedJarEntry> versionedJarEntry : versionedEntries.entrySet()) {
            archiveEntries.put(versionedJarEntry.getKey(), versionedJarEntry.getValue().getJarEntry());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 因为 getArchiveEntries(boolean) 总是返回一个 Map，所以永远不应该被调用。
     */
    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        throw new IllegalStateException("JarWarResourceSet 代码异常");
    }

    @Override
    protected boolean isMultiRelease() {
        // 这总是返回 false 否则超类将调用 getArchiveEntry(String)
        return false;
    }


	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    protected void initInternal() throws LifecycleException {
        try (JarFile warFile = new JarFile(getBase())) {
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream jarFileIs = warFile.getInputStream(jarFileInWar);

            try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                setManifest(jarIs.getManifest());
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }


    private static final class VersionedJarEntry {
        private final int version;
        private final JarEntry jarEntry;

        public VersionedJarEntry(int version, JarEntry jarEntry) {
            this.version = version;
            this.jarEntry = jarEntry;
        }

        public int getVersion() {
            return version;
        }

        public JarEntry getJarEntry() {
            return jarEntry;
        }
    }
}
