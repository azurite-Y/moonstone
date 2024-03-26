package org.zy.moonstone.core.webResources;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;

/**
 * @dateTime 2022年8月30日;
 * @author zy(azurite-Y);
 * @description 表示位于文件系统上的单个资源（文件或目录）
 */
public class FileResource extends AbstractResource {
	private static final Logger logger = LoggerFactory.getLogger(FileResource.class);

    private static final boolean PROPERTIES_NEED_CONVERT;
    
    private final File resource;
    /** 文件名 */
    private final String name;
    /** 是否仅读 */
    private final boolean readOnly;
    /** 清单文件 */
    private final Manifest manifest;
    /** 是否需要转换 */
    private final boolean needConvert;
    
    static {
        boolean isEBCDIC = false;
        try {
            String encoding = System.getProperty("file.encoding");
            if (encoding.contains("EBCDIC")) {
                isEBCDIC = true;
            }
        } catch (SecurityException e) {
            // Ignore
        }
        PROPERTIES_NEED_CONVERT = isEBCDIC;
    }

    public FileResource(WebResourceRoot root, String webAppPath, File resource, boolean readOnly, Manifest manifest) {
        super(root, webAppPath);
        this.resource = resource;

        if (webAppPath.charAt(webAppPath.length() - 1) == '/') {
            String realName = resource.getName() + '/';
            if (webAppPath.endsWith(realName)) {
                name = resource.getName();
            } else {
                // 这是一个挂载的ResourceSet的根目录。需要返回挂载的名字，不是真实的名字
                int endOfName = webAppPath.length() - 1;
                // 截取 webAppPath 最后一个 "/" 之后的字符串
                name = webAppPath.substring(webAppPath.lastIndexOf('/', endOfName - 1) + 1, endOfName);
            }
        } else {
            // 必须是文件
            name = resource.getName();
        }

        this.readOnly = readOnly;
        this.manifest = manifest;
        this.needConvert = PROPERTIES_NEED_CONVERT && name.endsWith(".properties");
    }

    @Override
    public long getLastModified() {
        return resource.lastModified();
    }

    @Override
    public boolean exists() {
        return resource.exists();
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return resource.isDirectory();
    }

    @Override
    public boolean isFile() {
        return resource.isFile();
    }

    @Override
    public boolean delete() {
        if (readOnly) {
            return false;
        }
        return resource.delete();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return getContentLengthInternal(needConvert);
    }

    private long getContentLengthInternal(boolean convert) {
        if (convert) {
            byte[] content = getContent();
            if (content == null) {
                return -1;
            } else {
                return content.length;
            }
        }

        if (isDirectory()) {
            return -1;
        }

        return resource.length();
    }

    @Override
    public String getCanonicalPath() {
        try {
            return resource.getCanonicalPath();
        } catch (IOException ioe) {
            if (logger.isDebugEnabled()) {
                logger.debug("FileResource 获得规范路径失败, path: " + resource.getPath(), ioe);
            }
            return null;
        }
    }

    @Override
    public boolean canRead() {
        return resource.canRead();
    }

    @Override
    protected InputStream doGetInputStream() {
        if (needConvert) {
            byte[] content = getContent();
            if (content == null) {
                return null;
            } else {
                return new ByteArrayInputStream(content);
            }
        }
        
        try {
            return new FileInputStream(resource);
        } catch (FileNotFoundException fnfe) {
            // 竞态条件(文件已被删除)-不是错误
            return null;
        }
    }

    @Override
    public final byte[] getContent() {
        // 当 needConvert 为 true 时，使用内部版本避免循环
        long len = getContentLengthInternal(false);

        if (len > Integer.MAX_VALUE) {
            // 无法创建这么大的数组
            throw new ArrayIndexOutOfBoundsException("获取内容太大, 无法创建这么大的数组, by path: " + getWebappPath() + ", length: " +  Long.valueOf(len));
        }

        if (len < 0) {
            // 内容在此不适用(例如目录)
            return null;
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (InputStream is = new FileInputStream(resource)) {
            while (pos < size) {
                int n = is.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
        } catch (IOException ioe) {
            if (logger.isDebugEnabled()) {
            	logger.debug("此资源的二进制内容失败, path: " + getWebappPath(), ioe);
            }
            return null;
        }

        if (needConvert) {
            // 通过 FileInputStream 读取使用 EBCDIC 编码的平台上的某些文件
            String str = new String(result);
            try {
                result = str.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                result = null;
            }
        }
        return result;
    }


    @Override
    public long getCreation() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(resource.toPath(), BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("获取文件的创建时间失败, by path: " + resource.getPath(), e);
            }
            return 0;
        }
    }

    @Override
    public URL getURL() {
        if (resource.exists()) {
            try {
                return resource.toURI().toURL();
            } catch (MalformedURLException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("获取访问资源的 URL失败,by path: " + resource.getPath(), e);
                }
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        if (getWebappPath().startsWith("/WEB-INF/classes/") && name.endsWith(".class")) {
            return getWebResourceRoot().getResource("/WEB-INF/classes/").getURL();
        } else {
            return getURL();
        }
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }
}
