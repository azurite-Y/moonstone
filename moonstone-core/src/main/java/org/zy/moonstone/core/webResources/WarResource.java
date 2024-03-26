package org.zy.moonstone.core.webResources;

import java.util.jar.JarEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.util.buf.UriUtil;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示位于 WAR 中的单个资源（文件或目录）
 */
public class WarResource extends AbstractSingleArchiveResource {
    private static final Logger logger = LoggerFactory.getLogger(WarResource.class);

    /**
     * 基于嵌套在WAR中的JAR文件创建新的 {@link WebResource}
     * 
     * @param archiveResourceSet - WarResourceSet 实例
     * @param webAppPath - 当前资源在web项目下路径
     * @param baseUrl - war 文件 url 路径
     * @param jarEntry - 封装此资源的 JarEntry 实例
     */
    public WarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String baseUrl, JarEntry jarEntry) {
        super(archiveResourceSet, webAppPath, "war:" + baseUrl + UriUtil.getWarSeparator(), jarEntry, baseUrl);
    }


    @Override
    protected Logger getLogger() {
        return logger;
    }
}
