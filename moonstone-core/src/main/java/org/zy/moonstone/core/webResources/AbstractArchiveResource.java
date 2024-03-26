package org.zy.moonstone.core.webResources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractArchiveResource extends AbstractResource {
	/** 关联的 WebResourceSet 实例 */
	private final AbstractArchiveResourceSet archiveResourceSet;
	/** war/jar 文件url路径 */
    private final String baseUrl;
    /** 封装此资源的 JarEntry 实例 */
    private final JarEntry resource;
    /** 原始的war文件url路径, {@link #baseUrl } 可能添加了某种前缀，如："war:" */
    private final String codeBaseUrl;
    /** 资源名称 */
    private final String name;
    /** 读取证书 */
    private boolean readCerts = false;
    private Certificate[] certificates;

    /**
     * 
     * @param archiveResourceSet - WarResourceSet 实例
     * @param webAppPath - 当前资源在web项目下路径
     * @param baseUrl - war 文件 url 路径
     * @param jarEntry - 封装此资源的 JarEntry 实例
     */
    protected AbstractArchiveResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String baseUrl, JarEntry jarEntry, String codeBaseUrl) {
        super(archiveResourceSet.getRoot(), webAppPath);
        this.archiveResourceSet = archiveResourceSet;
        this.baseUrl = baseUrl;
        this.resource = jarEntry;
        this.codeBaseUrl = codeBaseUrl;

        String resourceName = resource.getName();
        if (resourceName.charAt(resourceName.length() - 1) == '/') {
        	// 去除末尾"/"
            resourceName = resourceName.substring(0, resourceName.length() - 1);
        }
        String internalPath = archiveResourceSet.getInternalPath();
        if (internalPath.length() > 0 && resourceName.equals(internalPath.subSequence(1, internalPath.length()))) {
            name = "";
        } else {
            int index = resourceName.lastIndexOf('/');
            if (index == -1) {
                name = resourceName;
            } else {
            	// 截取字符串中最后 "/" 之后的字符
                name = resourceName.substring(index + 1);
            }
        }
    }

    protected AbstractArchiveResourceSet getArchiveResourceSet() {
        return archiveResourceSet;
    }

    protected final String getBase() {
        return archiveResourceSet.getBase();
    }

    protected final String getBaseUrl() {
        return baseUrl;
    }

    protected final JarEntry getResource() {
        return resource;
    }

    @Override
    public long getLastModified() {
        return resource.getTime();
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
        return resource.isDirectory();
    }

    @Override
    public boolean isFile() {
        return !resource.isDirectory();
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
        if (isDirectory()) {
            return -1;
        }
        return resource.getSize();
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
    public long getCreation() {
        return resource.getTime();
    }

    @Override
    public URL getURL() {
        String url = baseUrl + resource.getName();
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("获取资源的 url 失败, by url: " + url, e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        try {
            return new URL(codeBaseUrl);
        } catch (MalformedURLException e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("获取资源的原始 url 失败, by codeBaseUrl: " + codeBaseUrl, e);
            }
            return null;
        }
    }

    @Override
    public final byte[] getContent() {
        long len = getContentLength();

        if (len > Integer.MAX_VALUE) {
        	// 无法创建这么大的数组
            throw new ArrayIndexOutOfBoundsException("获取内容太大, 无法创建这么大的数组, by path: " + getWebappPath() + ", length: " +  Long.valueOf(len));
        }

        if (len < 0) {
            // 该内容在这里不适用（例如是一个目录）
            return null;
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (JarInputStreamWrapper jisw = getJarInputStreamWrapper()) {
            if (jisw == null) {
                return null;
            }
            while (pos < size) {
                int n = jisw.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
            // 读取数据流后，读取证书
            certificates = jisw.getCertificates();
            readCerts = true;
        } catch (IOException ioe) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("获取此资源的二进制内容失败，by webAppPath: " + getWebappPath(), ioe);
            }
            return null;
        }

        return result;
    }

    @Override
    public Certificate[] getCertificates() {
        if (!readCerts) {
            // 需先获取内容
            throw new IllegalStateException();
        }
        return certificates;
    }

    @Override
    public Manifest getManifest() {
        return archiveResourceSet.getManifest();
    }

    @Override
    protected final InputStream doGetInputStream() {
        if (isDirectory()) {
            return null;
        }
        return getJarInputStreamWrapper();
    }

    /**
     * 获取读取 jar 内容的 InputStream
     * @return 读取 jar 内容的 InputStream
     */
    protected abstract JarInputStreamWrapper getJarInputStreamWrapper();

    
    /**
     * 此包装器假定 InputStream 是从通过调用 getArchiveResourceSet().openJarFile() 获得的 JarFile 创建的。 
     * 如果不是这种情况，则 AbstractArchiveResourceSet 中的使用计数将中断，并且 JarFile 可能会意外关闭。
     */
    protected class JarInputStreamWrapper extends InputStream {
        private final JarEntry jarEntry;
        private final InputStream is;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public JarInputStreamWrapper(JarEntry jarEntry, InputStream is) {
            this.jarEntry = jarEntry;
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return is.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }

        @Override
        public int available() throws IOException {
            return is.available();
        }

        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                // 只能调用一次，否则使用计数将中断
                archiveResourceSet.closeJarFile();
            }
            is.close();
        }

        @Override
        public synchronized void mark(int readlimit) {
            is.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            is.reset();
        }

        @Override
        public boolean markSupported() {
            return is.markSupported();
        }

        public Certificate[] getCertificates() {
            return jarEntry.getCertificates();
        }
    }
}
