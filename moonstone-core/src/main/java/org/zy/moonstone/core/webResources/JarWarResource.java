package org.zy.moonstone.core.webResources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.buf.UriUtil;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示位于 JAR 中的单个资源（文件或目录），而 JAR 又位于 WAR 文件中。
 */
public class JarWarResource extends AbstractArchiveResource {

    private static final Logger logger = LoggerFactory.getLogger(JarWarResource.class);

    private final String archivePath;

    public JarWarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String baseUrl, JarEntry jarEntry, String archivePath) {
        super(archiveResourceSet, webAppPath,"jar:war:" + baseUrl + UriUtil.getWarSeparator() + archivePath + "!/", jarEntry, "war:" + baseUrl + UriUtil.getWarSeparator() + archivePath);
        this.archivePath = archivePath;
    }

    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile warFile = null;
        JarInputStream jarIs = null;
        JarEntry entry = null;
        try {
            warFile = getArchiveResourceSet().openJarFile();
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream isInWar = warFile.getInputStream(jarFileInWar);

            jarIs = new JarInputStream(isInWar);
            entry = jarIs.getNextJarEntry();
            while (entry != null && !entry.getName().equals(getResource().getName())) {
                entry = jarIs.getNextJarEntry();
            }

            if (entry == null) {
                return null;
            }

            return new JarInputStreamWrapper(entry, jarIs);
        } catch (IOException e) {
            if (logger.isDebugEnabled()) {
            	logger.debug(String.format("获取读取 jar 内容的 InputStream失败, by name: %s, url: %s", getResource().getName(), getBaseUrl()), e);
            }
            entry = null;
            return null;
        } finally {
            if (entry == null) {
                if (jarIs != null) {
                    try {
                        jarIs.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (warFile != null) {
                    getArchiveResourceSet().closeJarFile();
                }
            }
        }
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }
}
