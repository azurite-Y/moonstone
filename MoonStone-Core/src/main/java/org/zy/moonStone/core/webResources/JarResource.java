package org.zy.moonStone.core.webResources;

import java.util.jar.JarEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 表示位于JAR中的单个资源(文件或目录)。
 */
public class JarResource extends AbstractSingleArchiveResource {

    private static final Logger logger = LoggerFactory.getLogger(JarResource.class);


    public JarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String baseUrl, JarEntry jarEntry) {
        super(archiveResourceSet, webAppPath, "jar:" + baseUrl + "!/", jarEntry, baseUrl);
    }


    @Override
    protected Logger getLogger() {
        return logger;
    }
}
