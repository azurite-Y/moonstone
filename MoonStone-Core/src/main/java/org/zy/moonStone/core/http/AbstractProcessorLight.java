package org.zy.moonStone.core.http;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.zy.moonStone.core.interfaces.connector.Processor;
import org.zy.moonStone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonStone.core.util.net.DispatchType;
import org.zy.moonStone.core.util.net.SocketEvent;
import org.zy.moonStone.core.util.net.SocketWrapperBase;

/**
 * @dateTime 2022年5月20日;
 * @author zy(azurite-Y);
 * @description 这是一个轻量级的抽象处理器实现，旨在作为从轻量级升级处理器到HTTP处理器的所有处理器实现的基础
 */
public abstract class AbstractProcessorLight implements Processor {
	private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();

    @Override
    public SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status) throws IOException {
        SocketState state = SocketState.CLOSED;
        Iterator<DispatchType> dispatches = null;
        do {
            if (dispatches != null) {
                DispatchType nextDispatch = dispatches.next();
                state = dispatch(nextDispatch.getSocketStatus());
            } else if (status == SocketEvent.DISCONNECT) {
                // 在这里什么都不做，只是等待它被回收
            } else if (isAsync() || isUpgrade() || state == SocketState.ASYNC_END) {
                state = dispatch(status);
                if (state == SocketState.OPEN) {
                	/**
                	 * 可能有管道数据要读取。 如果现在未处理数据，则执行将退出此循环并调用 release() ，这将回收处理器（和输入缓冲区）以删除任何管道数据。 
                	 * 为避免这种情况，需立即处理。
                	 */
                    state = service(socketWrapper);
                }
            } else if (status == SocketEvent.OPEN_WRITE) {
                // 异步后可能发生的额外写入事件，忽略
                state = SocketState.LONG;
            } else if (status == SocketEvent.OPEN_READ) {
                state = service(socketWrapper);
            } else if (status == SocketEvent.CONNECT_FAIL) {
                logAccess(socketWrapper);
            } else {
                // 如果传入的SocketEvent与处理器的当前状态不一致，则默认关闭套接字
                state = SocketState.CLOSED;
            }

            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Socket: [" + socketWrapper +"], Status in: [" + status + "], State out: [" + state + "]");
            }

            if (state != SocketState.CLOSED && isAsync()) {
                state = asyncPostProcess();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("AbstractProcessorLight#process-Socket: [" + socketWrapper +"], 异步后处理后的状态: [" + state + "]");
                }
            }

            if (dispatches == null || !dispatches.hasNext()) {
                // 仅当存在要处理的调度时，才返回非空迭代器.
                dispatches = getIteratorAndClearDispatches();
            }
        } while (state == SocketState.ASYNC_END || dispatches != null && state != SocketState.CLOSED);
        return state;
    }

    public void addDispatch(DispatchType dispatchType) {
        synchronized (dispatches) {
            dispatches.add(dispatchType);
        }
    }

    public Iterator<DispatchType> getIteratorAndClearDispatches() {
        // 注意：AbstractProtocol 中的逻辑依赖于此方法，如果迭代器非空，则仅返回非空值。 即它永远不应该返回一个空的迭代器。
        Iterator<DispatchType> result;
        synchronized (dispatches) {
            // 同步，因为迭代器的生成和分派的清除需要是一个原子操作。
            result = dispatches.iterator();
            if (result.hasNext()) {
                dispatches.clear();
            } else {
                result = null;
            }
        }
        return result;
    }

    protected void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }

    /**
     * 为失败的连接尝试添加一个条目到访问日志.
     * @param socketWrapper - 与进程的连接
     * @throws IOException - 如果在处理请求的过程中发生 I/O 错误
     */
    protected void logAccess(SocketWrapperBase<?> socketWrapper) throws IOException {}

    /**
     * 为“标准”HTTP请求提供服务。对于新请求和部分读取了HTTP请求行或HTTP标头的请求，都会调用此方法。
     * 一旦完全读取了头，在有新的HTTP请求要处理之前，不会再次调用此方法。
     * 请注意，在处理过程中，请求类型可能会发生更改，这可能会导致一个或多个调度调用{@link #dispatch(SocketEvent)}。请求可能是管道式的
     * @param socketWrapper - 与进程的连接
     * @return 此方法返回时调用方应将套接字置于的状态
     * @throws IOException - 如果在处理请求期间发生I/O错误
     */
    protected abstract SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException;

    /**
     * 处理不再处于标准HTTP模式的正在进行的请求。目前的使用包括Servlet 3.0异步和HTTP升级连接.
     *
     * @param status - 要处理的事件
     * @return 此方法返回时调用方应将套接字置于的状态
     * @throws IOException - 如果在处理请求期间发生I/O错误
     */
    protected abstract SocketState dispatch(SocketEvent status) throws IOException;

    /**
     * 异步后处理请求
     * @return 此方法返回时调用方应将套接字置于的状态
     */
    protected abstract SocketState asyncPostProcess();

    protected abstract Logger getLogger();
}
