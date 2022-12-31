package org.zy.moonStone.core.webResources;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.interfaces.webResources.WebResourceSet;
import org.zy.moonStone.core.util.buf.UriUtil;
import org.zy.moonStone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 基于单个(而不是嵌套)存档的 {@link WebResourceSet }的基类。
 */
public abstract class AbstractSingleArchiveResourceSet extends AbstractArchiveResourceSet {
    private volatile Boolean multiRelease;

    public AbstractSingleArchiveResourceSet() {}


    public AbstractSingleArchiveResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath) throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
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
    protected Map<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null && !single) {
                JarFile jarFile = null;
                archiveEntries = new HashMap<>();
                try {
                    jarFile = openJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        archiveEntries.put(entry.getName(), entry);
                    }
                } catch (IOException ioe) {
                    // 不应该发生
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (jarFile != null) {
                        closeJarFile();
                    }
                }
            }
            return archiveEntries;
        }
    }


    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        JarFile jarFile = null;
        try {
            jarFile = openJarFile();
            return jarFile.getJarEntry(pathInArchive);
        } catch (IOException ioe) {
            // 不应该发生
            throw new IllegalStateException(ioe);
        } finally {
            if (jarFile != null) {
                closeJarFile();
            }
        }
    }


    @Override
    protected boolean isMultiRelease() {
        if (multiRelease == null) {
            synchronized (archiveLock) {
                if (multiRelease == null) {
                    JarFile jarFile = null;
                    try {
                        jarFile = openJarFile();
                        multiRelease = Boolean.valueOf(JreCompat.getInstance().jarFileIsMultiRelease(jarFile));
                    } catch (IOException ioe) {
                        // 不应该发生
                        throw new IllegalStateException(ioe);
                    } finally {
                        if (jarFile != null) {
                            closeJarFile();
                        }
                    }
                }
            }
        }

        return multiRelease.booleanValue();
    }


	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    protected void initInternal() throws LifecycleException {
        try (JarFile jarFile = JreCompat.getInstance().jarFileNewInstance(getBase())) {
            setManifest(jarFile.getManifest());
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
