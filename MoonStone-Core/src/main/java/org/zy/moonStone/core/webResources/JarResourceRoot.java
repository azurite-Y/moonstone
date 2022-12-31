package org.zy.moonStone.core.webResources;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;

/**
 * @dateTime 2022年8月30日;
 * @author zy(azurite-Y);
 * @description
 */
public class JarResourceRoot extends AbstractResource {
    private static final Logger logger = LoggerFactory.getLogger(JarResourceRoot.class);
    
    /** 资源对象 */
    private final File base;
    /** 资源路径 */
    private final String baseUrl;
    /** 文件名 */
    private final String name;

    public JarResourceRoot(WebResourceRoot root, File base, String baseUrl, String webAppPath) {
        super(root, webAppPath);
        // 在继续之前验证webAppPath
        if (!webAppPath.endsWith("/")) {
            throw new IllegalArgumentException("无效的 WebAppPath, by webAppPath: " + webAppPath);
        }
        this.base = base;
        this.baseUrl = "jar:" + baseUrl;
        // 从webAppPath中提取名称，去掉末尾的'/'字符
        String resourceName = webAppPath.substring(0, webAppPath.length() - 1);
        
        int i = resourceName.lastIndexOf('/');
        if (i > -1) {
            resourceName = resourceName.substring(i + 1);
        }
        name = resourceName;
    }

    @Override
    public long getLastModified() {
        return base.lastModified();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    protected InputStream doGetInputStream() {
        return null;
    }

    @Override
    public byte[] getContent() {
        return null;
    }

    @Override
    public long getCreation() {
        return base.lastModified();
    }

    @Override
    public URL getURL() {
        String url = baseUrl + "!/";
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("获取访问资源的 URL失败, by url: " + url, e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        try {
            return new URL(baseUrl);
        } catch (MalformedURLException e) {
            if (logger.isDebugEnabled()) {
            	logger.debug("获取此资源的二进制内容失败, by path:" + baseUrl, e);
            }
            return null;
        }
    }
    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return null;
    }
}
