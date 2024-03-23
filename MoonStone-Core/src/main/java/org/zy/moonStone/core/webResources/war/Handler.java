package org.zy.moonstone.core.webResources.war;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @dateTime 2022年9月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class Handler extends URLStreamHandler {

	/**
	 * 打开与 URL 参数引用的对象的连接。此方法应被子类覆盖。
	 * 
	 * 如果对于处理程序的协议（例如 HTTP 或 JAR），存在属于以下包之一或其子包之一的公共专用 URLConnection 子类：
	 * java.lang、java.io、java.util、java.net，返回的连接 将属于该子类。 例如，对于 HTTP 将返回一个 HttpURLConnection. 对于 JAR 将返回一个 JarURLConnection。
	 * 
	 * @param u - 它连接到的URL
	 * @return URL 的 URLConnection 对象
	 * @exception IOException - 如果在打开连接时发生 I/O 错误
	 */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new WarURLConnection(u);
    }

    /**
     * 将 URL 参数的字段设置为指定的值。只有从 URLStreamHandler 派生的类才能使用此方法设置 URL 字段的值。
     * 
     * @param u - 要修改的 URL
     * @param protocol - 协议名称
     * @param host - URL 的远程主机值
     * @param port - 远程机器上的端口
     * @param authority - URL 的权限部分
     * @param userInfo - URL 的 userInfo 部分
     * @param path - URL 的路径组件
     * @param query - URL 的查询部分
     * @param ref - 引用
     * 
     * @exception SecurityException - 如果该URL的协议处理程序与此不同
     */
    @Override
    protected void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path, String query, String ref) {
        if (path.startsWith("file:") && !path.startsWith("file:/")) {
            /*
             * 解决安全策略文件中的 URL 问题。
             * 
             * 在 Windows 上，在策略文件中使用 ${moonstone.[home|base]} 会导致代码库 URL 的格式为 file:C:/... 而它们应该是 file:/C:/...
             * 
             * 对于 file: 和 jar: URL，JRE 对此进行了补偿。 它不会为 war:file:... URL 补偿这一点。 因此，在这里这样做
             */
            path = "file:/" + path.substring(5);
        }
        super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    }
}
