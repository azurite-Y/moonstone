package org.zy.moonStone.core.interfaces.connector;


import org.omg.CORBA.Request;
import org.zy.moonStone.core.util.net.SocketWrapperBase;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description
 */
public interface UpgradeProtocol {
	/**
     * @param isSSLEnabled - 这是否适用于配置为支持 TLS 的连接器。 某些协议（例如 HTTP/2）仅支持通过非安全连接进行 HTT 升级。
     * @return 客户端通过HTTP/1.1升级请求来请求升级到该协议的名称，或者如果不支持通过HTTP/1.1升级请求进行升级，则为null。
     */
    public String getHttpUpgradeName(boolean isSSLEnabled);

    /**
     * @return 此协议的IANA注册表中列出的字节序列，如果不支持通过ALPN升级，则为null。
     */
    public byte[] getAlpnIdentifier();

    /**
     * @return IANA 注册中列出的协议名称当且仅当 getAlpnIdentifier() 返回此名称的 UTF-8 编码。 
     * 如果 getAlpnIdentifier() 返回某个其他字节序列，则此方法返回空字符串。 如果不支持通过 ALPN 升级，则返回 null。
     */
    public String getAlpnName();

    /**
     * @param socketWrapper - 需要处理器的连接的 socketWrapper
     * @param adapter - 提供对标准Engine/Host/Context/Wrapper 处理链的访问的适配器实例
     * @return 使用此协议处理连接的处理器实例
     */
    public Processor getProcessor(SocketWrapperBase<?> socketWrapper, Adapter adapter);


    /**
     * @param socketWrapper
     * @param adapter - 用于配置新升级处理程序的适配器
     * @param request - 触发升级的请求的副本(可能是不完整的)
     * @return 此协议的HTTP升级处理程序的实例
     */
    public InternalHttpUpgradeHandler getInternalUpgradeHandler(SocketWrapperBase<?> socketWrapper, Adapter adapter, Request request);


    /**
     * 允许实现检查请求，并根据发现的结果接受或拒绝请求
     *
     * @param request - 包含此协议升级报头的请求
     * @return 如果请求被接受，则为True，否则为false
     */
    public boolean accept(Request request);
}
