package org.zy.moonstone.core.util.compat;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.net.URL;
import java.net.URLConnection;
import java.util.Deque;
import java.util.jar.JarFile;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description JRE兼容性的基本实现类，并提供了一个基于Java 8的实现。子类可以扩展这个类，并为以后的JRE版本提供替代实现
 */
public class JreCompat {
	private static final int RUNTIME_MAJOR_VERSION = 8;

    private static final JreCompat instance;
    private static final boolean graalAvailable;
    private static final boolean jre9Available;

    static {
        // 
        if (GraalCompat.isSupported()) {
            instance = new GraalCompat();
            graalAvailable = true;
            jre9Available = false;
        } else if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            graalAvailable = false;
            jre9Available = true;
        } else {
            instance = new JreCompat();
            graalAvailable = false;
            jre9Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    public static boolean isGraalAvailable() {
        return graalAvailable;
    }


    public static boolean isJre9Available() {
        return jre9Available;
    }


    // Java 9 方法的 Java 8 实现
    /**
     * 测试提供的异常是否是 java.lang.reflect.InaccessibleObjectException 的实例
     *
     * @param t - 测试的异常
     *
     * @return 如果异常是 InaccessibleObjectException 的实例，则为 true，否则为 false
     */
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        // Java 9 之前不存在异常
        return false;
    }


    /**
     * 设置服务器将为 ALPN 接受的应用程序协议
     *
     * @param sslParameters - 连接的 SSL 参数
     * @param protocols - 该连接允许的应用程序协议
     */
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        throw new UnsupportedOperationException("没有应用程序协议");
    }


    /**
     * 获取已为与给定 SSLEngine 关联的连接协商的应用程序协议
     *
     * @param sslEngine - 为其获取协商协议的 SSLEngine
     *
     * @return 协商协议的名称
     */
    public String getApplicationProtocol(SSLEngine sslEngine) {
        throw new UnsupportedOperationException("没有应用程序协议");
    }


    /**
     * 禁用 JAR URL 连接的缓存。 对于 Java 8 和更早版本，这也会禁用所有 URL 连接的缓存。
     *
     * @throws IOException - 如果无法创建虚拟 JAR URL 连接
     */
    public void disableCachingForJarUrlConnections() throws IOException {
        // 这个 JAR 不存在并不重要——只要 URL 格式正确
        URL url = new URL("jar:file://dummy.jar!/");
        URLConnection uConn = url.openConnection();
        uConn.setDefaultUseCaches(false);
    }


    /**
     * JVM 启动时获取模块路径上所有 JAR 的 URL，并将它们添加到提供的 Deque
     *
     * @param classPathUrlsToProcess - 模块应该添加到的双端队列
     */
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        // Java 8 不做任何操作。没有模块路径
    }


    /**
     * 创建一个新的 JarFile 实例。 在 Java 9 及更高版本上运行时，JarFile 将支持多版本 JAR。 
     * 虽然这并不严格要求包含在此包中，但它是作为一种方便的方法提供的。
     *
     * @param s - 要打开的 JAR 文件
     * @return A - 基于提供的路径的 JarFile 实例
     * @throws IOException 如果创建 JarFile 实例时发生 I/O 错误
     */
    public final JarFile jarFileNewInstance(String s) throws IOException {
        return jarFileNewInstance(new File(s));
    }


    /**
     * 创建一个新的 JarFile 实例。 在 Java 9 及更高版本上运行时，JarFile 将支持多版本 JAR
     *
     * @param f - 要打开的 JAR 文件
     * @return A - 基于提供的文件的 JarFile 实例
     * @throws IOException  - 如果创建 JarFile 实例时发生 I/O 错误
     */
    public JarFile jarFileNewInstance(File f) throws IOException {
        return new JarFile(f);
    }


    /**
     * 这个 JarFile 是多版本 JAR 文件吗.
     *
     * @param jarFile - 要测试的 JarFile
     * @return 如果它是一个多版本 JAR 文件并且被配置为这样则返回 true
     */
    public boolean jarFileIsMultiRelease(JarFile jarFile) {
        // Java 8 不支持多版本，所以默认为 false
        return false;
    }


    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }


    /**
     * 提供的实例上的可访问对象是否可访问（作为适当的模块导出的结果）？
     *
     * @param base - 要测试的具体实例
     * @param accessibleObject - 要测试的方法/字段/构造函数
     * @return 如果可以访问 AccessibleObject，则为 true，否则为 false
     */
    public boolean canAcccess(Object base, AccessibleObject accessibleObject) {
        // Java 8 不支持模块，所以默认为 true
        return true;
    }


    /**
     * 是否为导出包中的给定类
     *
     * @param type 要测试的类
     * @return 对于 Java 8 始终为 true。如果为 Java 9+ 导出封闭包，则为 true
     */
    public boolean isExported(Class<?> type) {
        return true;
    }
}
