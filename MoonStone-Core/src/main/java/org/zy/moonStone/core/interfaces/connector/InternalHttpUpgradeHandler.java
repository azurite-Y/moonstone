package org.zy.moonstone.core.interfaces.connector;

import org.zy.moonstone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonstone.core.util.net.SocketEvent;
import org.zy.moonstone.core.util.net.SocketWrapperBase;
import org.zy.moonstone.core.util.net.interfaces.SSLSupport;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description 特定于moonstone的接口，由处理程序实现。处理程序需要直接访问moonstone的I/O层，而不是通过Servlet API
 */
public interface InternalHttpUpgradeHandler {
	SocketState upgradeDispatch(SocketEvent status);

    void timeoutAsync(long now);

    void setSocketWrapper(SocketWrapperBase<?> wrapper);

    void setSslSupport(SSLSupport sslSupport);

    void pause();

    default boolean hasAsyncIO() {
        return false;
    }
}
