package org.zy.moonstone.core.webResources;

import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.interfaces.webResources.WebResourceSet;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示基于WAR文件的 {@link WebResourceSet }
 */
public class WarResourceSet extends AbstractSingleArchiveResourceSet {
    public WarResourceSet() {}


    /**
     * 基于 WAR 文件创建一个新的 {@link WebResourceSet}
     *
     * @param root -  这个新的 {@link WebResourceSet } 将被添加到的 WebResourceRoot
     * @param webAppMount - 这个 {@link WebResourceSet } 将挂载在web应用程序中的路径。
     * @param base - 将从中提供资源的文件系统上的WAR文件的绝对路径
     *
     * @throws IllegalArgumentException - 如果webAppmount无效(有效路径必须以 "/" 开头)
     */
    public WarResourceSet(WebResourceRoot root, String webAppMount, String base) throws IllegalArgumentException {
        super(root, webAppMount, base, "/");
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry, String webAppPath, Manifest manifest) {
        return new WarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}
