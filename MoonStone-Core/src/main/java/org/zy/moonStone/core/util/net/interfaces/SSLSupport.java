package org.zy.moonStone.core.util.net.interfaces;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * @dateTime 2022年1月13日;
 * @author zy(azurite-Y);
 * @description 定义与SSL会话交互的接口
 */
public interface SSLSupport {
	/**
     * 密码套件的请求属性键
     */
    public static final String CIPHER_SUITE_KEY = "javax.servlet.request.cipher_suite";

    /**
     * 密钥尺寸的请求属性键
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * 客户端证书链的请求属性键
     */
    public static final String CERTIFICATE_KEY = "javax.servlet.request.X509Certificate";

    /**
     * 会话 id 的请求属性键。这是对 Servlet 规范的扩展
     */
    public static final String SESSION_ID_KEY = "javax.servlet.request.ssl_session_id";

    /**
     * 会话管理器的请求属性键。这是对 Servlet 规范的扩展。
     */
    public static final String SESSION_MGR = "javax.servlet.request.ssl_session_mgr";

    /**
     * 请求属性键，在此键下记录了创建SSL套接字的协议，例如TLSv1或TLSv1.2等。
     */
    public static final String PROTOCOL_VERSION_KEY = "org.zy.moonStone.core.util.net.secure_protocol_version";

    /**
     * 此连接上使用的密码套件
     *
     * @return SSL/TLS 实现返回的密码套件的名称
     * @throws IOException - 如果在尝试获取密码套件时发生错误
     */
    public String getCipherSuite() throws IOException;

    /**
     * 客户端证书链（如果有）。
     *
     * @return 由客户端首先提供对等端证书的证书链，然后是任何证书颁发机构的证书链
     * @throws IOException - 如果在尝试获取证书链时发生错误
     */
    public X509Certificate[] getPeerCertificateChain() throws IOException;

    /**
     * 获取密钥大小。我们应该放在这里的内容在 Servlet 规范（S 4.7）中定义不明确。 这里至少有 4 个潜在值：
     * (a) 加密密钥的大小
     * (b) MAC 密钥的大小
     * (c) 密钥交换密钥的大小
     * (d) 使用的签名密钥的大小
     * 不幸的是，所有这些值都是无意义的。
     *
     * @return 当前密码套件的有效密钥大小
     * @throws IOException - 如果在尝试获取密钥大小时发生错误
     */
    public Integer getKeySize() throws IOException;

    /**
     * 当前会话Id
     *
     * @return 当前SSL/TLS会话ID
     * @throws IOException - 如果在尝试获取会话ID时发生错误
     */
    public String getSessionId() throws IOException;

    /**
     * @return 指示SSL套接字是如何创建的协议字符串。TLSv1或TLSv1.2等等。
     * @throws IOException - 如果试图从套接字获取协议信息时发生错误
     */
    public String getProtocol() throws IOException;
}
