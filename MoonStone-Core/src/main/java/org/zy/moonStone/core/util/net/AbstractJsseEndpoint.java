package org.zy.moonStone.core.util.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NetworkChannel;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description 套接字数据安全传输基础扩展
 * @param <S> - 与此端点相关的套接字包装器使用的类型。可能和U一样。
 * @param <U> - 这个端点使用的底层套接字的类型。可能和S一样。
 */
public abstract class AbstractJsseEndpoint<S,U> extends AbstractEndpoint<S,U> {
	protected abstract NetworkChannel getServerSocket();

	@Override
	public void unbind() throws Exception {
		// TODO

//		for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
//			for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
//				certificate.setSslContext(null);
//			}
//		}
	}

//	@Override
//	protected void createSSLContext(SSLHostConfig sslHostConfig) throws Exception {
//		// TODO 自动生成的方法存根
//	}
	
//	@Override
//	public boolean isAlpnSupported() {
//		// TODO 自动生成的方法存根
//		return false;
//	}

    protected void initialiseSsl() throws Exception {
		// TODO

//        if (isSSLEnabled()) {
//            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());
//
//            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
//                createSSLContext(sslHostConfig);
//            }
//
//            // Validate default SSLHostConfigName
//            if (sslHostConfigs.get(getDefaultSSLHostConfigName()) == null) {
//                throw new IllegalArgumentException(String.format("无 SslHostConfig, by defaultSSLHostConfigName: %s, endpointName: %s" , getDefaultSSLHostConfigName(), getName()));
//            }
//
//        }
    }

	@Override
	protected final InetSocketAddress getLocalAddress() throws IOException {
		NetworkChannel serverSock = getServerSocket();
		if (serverSock == null) {
			return null;
		}
		SocketAddress sa = serverSock.getLocalAddress();
		if (sa instanceof InetSocketAddress) {
			return (InetSocketAddress) sa;
		}
		return null;
	}

}
