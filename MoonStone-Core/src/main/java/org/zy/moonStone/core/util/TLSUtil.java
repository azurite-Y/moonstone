package org.zy.moonStone.core.util;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.util.net.interfaces.SSLSupport;

/**
 * @dateTime 2022年6月17日;
 * @author zy(azurite-Y);
 * @description
 */
public class TLSUtil {
	 /**
     * 确定是否使用命名请求属性将有关连接的 TLS 配置的信息传递给应用程序。 支持 Servlet 规范定义的标准请求属性和 MoonStone 特定属性。
     *
     * @param name - 要测试的属性名称
     * @return 如果该属性用于传递 TLS 配置信息，则为 {@code true}，否则为 {@code false}
     */
    public static boolean isTLSRequestAttribute(String name) {
        return Globals.CERTIFICATES_ATTR.equals(name) ||
                Globals.CIPHER_SUITE_ATTR.equals(name) ||
                Globals.KEY_SIZE_ATTR.equals(name)  ||
                Globals.SSL_SESSION_ID_ATTR.equals(name) ||
                Globals.SSL_SESSION_MGR_ATTR.equals(name) ||
                SSLSupport.PROTOCOL_VERSION_KEY.equals(name);
    }
}
