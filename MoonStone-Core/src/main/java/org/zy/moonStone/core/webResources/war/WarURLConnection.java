package org.zy.moonStone.core.webResources.war;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;

import org.zy.moonStone.core.util.buf.UriUtil;

/**
 * @dateTime 2022年9月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class WarURLConnection extends URLConnection {
    private final URLConnection wrappedJarUrlConnection;
    private boolean connected;

    protected WarURLConnection(URL url) throws IOException {
        super(url);
        URL innerJarUrl = UriUtil.warToJar(url);
        wrappedJarUrlConnection = innerJarUrl.openConnection();
    }

    /**
     * 如果此类连接尚未建立，则打开指向此 URL 引用的资源的通信链接。
     * 
     * 如果connect方法在连接已经打开的情况下被调用(通过connected字段的值为true表示)，调用将被忽略。
     * 
     * URLConnection对象经历两个阶段:首先它们被重新创建，然后它们被连接。在创建之后，在连接之前，可以指定各种选项(例如: doInput和UseCaches)。
     * 连接后，试图设置它们是一个错误。依赖于被连接的操作，如getContentLength，在必要时将隐式执行连接。
     * 
     * @exception IOException - 如果在打开连接时发生I/O错误
     */
    @Override
    public void connect() throws IOException {
        if (!connected) {
            wrappedJarUrlConnection.connect();
            connected = true;
        }
    }

    /**
     * 返回从此打开的连接读取的输入流。如果在数据可供读取之前读取超时到期，则从返回的输入流读取时会引发 SocketTimeoutException。
     * 
     * @return 从此打开连接读取的输入流。
     * @exception IOException - 如果在创建输入流时发生I/O错误
     */
    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return wrappedJarUrlConnection.getInputStream();
    }

    /**
     * 返回一个权限对象，该权限对象表示建立此对象所表示的连接所需的权限。如果不需要进行连接的权限，则此方法返回null。
     * 默认情况下，该方法返回java.security.AllPermission。子类应该覆盖此方法并返回最能代表连接URL所需权限的权限。
     * 例如，一个表示file: URL的urlconnection会返回一个java.io.FilePermission对象。
     * 
     * 返回的权限可能取决于连接的状态。例如，连接前的权限可能与连接后的权限不同。
     * 例如，HTTPsever(例如foo.com)可能会将连接重定向到一个不同的主机(例如bar.com)。
     * 在连接之前，连接返回的权限将代表连接到foo.com所需的权限，而连接后返回的权限将代表到bar.com。
     * 
     * 权限通常用于两个目的:保护通过URLConnections获得的对象的缓存，以及检查接收方了解特定URL的权利。
     * 在第一种情况下，许可应该在获得对象之后获得。例如，在http连接中，这将表示连接到最终从其获取数据的主机的权限。
     * 在第二种情况下，应该在连接之前获得并测试权限。
     */
    @Override
    public Permission getPermission() throws IOException {
        return wrappedJarUrlConnection.getPermission();
    }

    /**
     * 返回最后一次修改日期 的header值。结果是自1970年1月1日格林威治标准时间以来的毫秒数。
     * 
     * @return 这个URLConnection引用的资源最后一次修改的日期，如果不知道，则为0。
     */
    @Override
    public long getLastModified() {
        return wrappedJarUrlConnection.getLastModified();
    }

    /**
     * 返回内容长度 header 字段的值。
     * 
     * 注意:getContentLengthLong()应该优先于此方法，因为它返回一个long代替，因此更易移植。
     */
    @Override
    public int getContentLength() {
        return wrappedJarUrlConnection.getContentLength();
    }

    /**
     * 此连接的URL引用的资源的内容长度，如果内容长度未知，或者如果内容长度大于Integer.MAX_VALUE，则为-1。
     * 
     * @return 此连接的URL引用的资源的内容长度，如果内容长度未知，则为-1。
     */
    @Override
    public long getContentLengthLong() {
        return wrappedJarUrlConnection.getContentLengthLong();
    }
}
