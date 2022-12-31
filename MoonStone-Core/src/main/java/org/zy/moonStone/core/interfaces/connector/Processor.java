package org.zy.moonStone.core.interfaces.connector;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.zy.moonStone.core.http.Request;
import org.zy.moonStone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonStone.core.util.net.SocketEvent;
import org.zy.moonStone.core.util.net.SocketWrapperBase;
import org.zy.moonStone.core.util.net.UpgradeToken;
import org.zy.moonStone.core.util.net.interfaces.SSLSupport;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description 所有协议处理器的通用接口
 */
public interface Processor {
	/**
     * 处理连接。 每当发生允许对当前未处理的连接继续处理的事件（例如，更多数据到达）时，都会调用此方法。
     *
     * @param socketWrapper - 与进程的连接
     * @param status - 触发此附加处理的连接状态
     * @return 此方法返回时调用者应将套接字置于的状态
     * @throws IOException - 处理请求时发生I/O错误
     */
    SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status) throws IOException;

    /**
     * 生成升级token
     *
     * @return 一个升级token，封装了处理升级请求所需的信息
     * @throws IllegalStateException - 如果在一个不支持升级的处理器上调用
     */
    UpgradeToken getUpgradeToken();

    /**
     * @return 如果处理器当前正在处理升级请求，则为 true，否则为 false
     */
    boolean isUpgrade();
    boolean isAsync();

    /**
     * 检查这个处理器，看看超时是否已经过期，如果是这种情况，则处理一个超时。
     * <p>
     * Note: 此方法的名称起源于 Servlet 3.0 异步处理，但随着时间的推移演变为表示独立于套接字读/写超时触发的超时。
     *
     * @param now - 时间（由System.currentTimeMillis（）返回）用作确定超时是否已过期的当前时间。如果为负，则超时将始终被视为qit已过期。
     */
    void timeoutAsync(long now);

    /**
     * @return 与此处理器关联的原初请求
     */
    Request getRequest();

    /**
     * 回收处理器，为下一个请求做好准备，这个请求可能在同一个连接上，也可能在不同的连接上
     */
    void recycle();

    /**
     * 设置此HTTP连接的SSL信息
     *
     * @param sslSupport - 用于此连接的 SSL 支持对象
     */
    void setSslSupport(SSLSupport sslSupport);

    /**
     * 允许在升级过程中检索其他输入
     *
     * @return 剩余字节
     * @throws IllegalStateException - 如果在不支持升级的处理器上调用它
     */
    ByteBuffer getLeftoverInput();

    /**
     * 通知处理器底层的I/O层已经停止接受新的连接。
     * 这主要是为了使使用多路连接的处理器能够防止更多的“流”被添加到现有的多路连接中。
     */
    void pause();

    /**
     * 检查异步生成(每个异步周期都会增加AsyncStateMachine的生成)是否与最近的异步超时触发时的生成相同。这是为了避免不必要的处理
     *
     * @return 如果自触发同步超时后异步生成未更改，则为true
     */
    boolean checkAsyncTimeoutGeneration();
}
