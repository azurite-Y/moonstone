package org.zy.moonstone.core.interfaces.container;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

/**
 * @dateTime 2022年12月4日;
 * @author zy(azurite-Y);
 * @description 抽象配置文件存储。允许使用常规配置样式嵌入 moonstone。这种抽象旨在非常简单，不包括资源列表，资源列表通常用于动态部署（通常在嵌入时不使用）以及资源写入。
 */
public interface ConfigurationSource {
	public static final ConfigurationSource DEFAULT = new ConfigurationSource() {
        protected final File userDir = new File(System.getProperty("user.dir"));
        protected final URI userDirUri = userDir.toURI();
        
        @Override
        public Resource getResource(String name) throws IOException {
            File f = new File(name);
            if (!f.isAbsolute()) {
                f = new File(userDir, name);
            }
            if (f.isFile()) {
                FileInputStream fis = new FileInputStream(f);
                return new Resource(fis, f.toURI());
            }
            URI uri = null;
            try {
                uri = getURI(name);
            } catch (IllegalArgumentException e) {
                throw new FileNotFoundException(name);
            }
            try {
                URL url = uri.toURL();
                return new Resource(url.openConnection().getInputStream(), uri);
            } catch (MalformedURLException e) {
                throw new FileNotFoundException(name);
            }
        }
        
        @Override
        public URI getURI(String name) {
            File f = new File(name);
            if (!f.isAbsolute()) {
                f = new File(userDir, name);
            }
            if (f.isFile()) {
                return f.toURI();
            }
            return userDirUri.resolve(name);
        }
    };

    /**
     * 表示资源：指向与其URI关联的资源的流。
     */
    public class Resource implements AutoCloseable {
        private final InputStream inputStream;
        private final URI uri;
        public Resource(InputStream inputStream, URI uri) {
            this.inputStream = inputStream;
            this.uri = uri;
        }
        public InputStream getInputStream() {
            return inputStream;
        }
        public URI getURI() {
            return uri;
        }
        public long getLastModified() throws MalformedURLException, IOException {
            URLConnection connection = null;
            try {
                connection = uri.toURL().openConnection();
                return connection.getLastModified();
            } finally {
                if (connection != null) {
                    connection.getInputStream().close();
                }
            }
        }
        
        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }


    /**
     * 根据conf路径获取资源
     * 
     * @param name - 资源名称
     * @return 将资源作为输入流
     * 
     * @throws IOException - 如果发生错误或资源不存在
     */
    public default Resource getConfResource(String name) throws IOException {
        String fullName = "conf/" + name;
        return getResource(fullName);
    }

    /**
     * 获取资源，而不是基于conf路径
     * 
     * @param name - 资源名称
     * @return 资源
     * @throws IOException - 如果发生错误或资源不存在
     */
    public Resource getResource(String name) throws IOException;

    /**
     * 获取给定资源的URI。与getResource不同，这也会将URI返回到不存在资源的位置
     * 
     * @param name - 资源名称
     * @return 表示资源位置的URI
     */
    public URI getURI(String name);
}
