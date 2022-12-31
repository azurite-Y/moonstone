package org.zy.moonStone.core.webResources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @dateTime 2022年9月2日;
 * @author zy(azurite-Y);
 * @description
 */
public class ClasspathURLStreamHandler extends URLStreamHandler {

	/**
	 * 打开与URL参数引用的对象的连接。此方法应由子类重写。
	 * 
	 * 如果对于处理程序的协议(如HTTP或JAR)，存在属于以下包或其子包之一的公共专用URLConnection子类：
	 * java.lang、java.io、java.util、java.net，则返回的连接将属于该子类。
	 * 例如，对于HTTP，将返回HttpURLConnection；对于JAR，将返回JarURLConnection。
	 * 
	 * @param u - 连接到的 URL
	 * @return URL 的 URLConnection 对象
	 * 
	 * @exception IOException - 如果在打开连接时发生 I/O 错误
	 */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        String path = u.getPath();

        // 线程上下文类加载器优先
        URL classpathUrl = Thread.currentThread().getContextClassLoader().getResource(path);
        if (classpathUrl == null) {
            // 这个类的类加载器如果不喜欢 tccl
            classpathUrl = ClasspathURLStreamHandler.class.getResource(path);
        }

        if (classpathUrl == null) {
            throw new FileNotFoundException("not Found URI, by uri: " + u);
        }

        return classpathUrl.openConnection();
    }
}
