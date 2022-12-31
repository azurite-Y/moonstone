package org.zy.moonStone.core.webResources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractSingleArchiveResource extends AbstractArchiveResource {

    protected AbstractSingleArchiveResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String baseUrl, JarEntry jarEntry, String codeBaseUrl) {
        super(archiveResourceSet, webAppPath, baseUrl, jarEntry, codeBaseUrl);
    }


    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile jarFile = null;
        try {
            jarFile = getArchiveResourceSet().openJarFile();
            // 需要创建一个新的 JarEntry 以便可以读取证书
            JarEntry jarEntry = jarFile.getJarEntry(getResource().getName());
            InputStream is = jarFile.getInputStream(jarEntry);
            return new JarInputStreamWrapper(jarEntry, is);
        } catch (IOException e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug(String.format("获取读取 jar 内容的 InputStream失败, by name: %s, url: %s", getResource().getName(), getBaseUrl()), e);
            }
            if (jarFile != null) {
                getArchiveResourceSet().closeJarFile();
            }
            return null;
        }
    }
}
