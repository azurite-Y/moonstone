package org.zy.moonStone.core.webResources;

import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.interfaces.webResources.WebResourceSet;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示基于JAR文件的 {@link WebResourceSet }
 */
public class JarResourceSet extends AbstractSingleArchiveResourceSet {

    public JarResourceSet() {}


    /**
     * 基于JAR文件创建新的 {@link WebResourceSet }
     *
     * @param root - 这个新 {@link WebResourceSet } 将被添加到的 {@link WebResourceRoot }
     * @param webAppMount - Web应用程序内的路径，该 {@link WebResourceSet } 将在该路径上挂载。
     * @param base - 文件系统上JAR文件的绝对路径，资源将从该文件系统获得。
     * @param internalPath - 这个新的 {@link WebResourceSet } 中的路径，资源将从这里提供。例如，对于资源JAR，这将是"META-INF/resources"
     *
     * @throws IllegalArgumentException - 如果 webAppMount 或 internalPath 无效（有效路径必须以“/”开头）
     */
    public JarResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath) throws IllegalArgumentException {
        super(root, webAppMount, base, internalPath);
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry, String webAppPath, Manifest manifest) {
        return new JarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}
