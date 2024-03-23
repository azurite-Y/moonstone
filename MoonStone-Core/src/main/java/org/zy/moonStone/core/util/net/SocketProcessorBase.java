package org.zy.moonstone.core.util.net;

import java.util.Objects;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description Socket处理器基础抽象类
 */
public abstract class SocketProcessorBase<S> implements Runnable {
	protected SocketWrapperBase<S> socketWrapper;
    protected SocketEvent event;

    public SocketProcessorBase(SocketWrapperBase<S> socketWrapper, SocketEvent event) {
        reset(socketWrapper, event);
    }


    public void reset(SocketWrapperBase<S> socketWrapper, SocketEvent event) {
        Objects.requireNonNull(event);
        this.socketWrapper = socketWrapper;
        this.event = event;
    }


    @Override
    public final void run() {
        synchronized (socketWrapper) {
            /*
             * 可能会同时触发读取和写入处理。上面的同步确保处理不会并行发生。
             * 下面的测试确保如果要处理的第一个事件导致套接字被关闭，则不处理后续事件。
             */
            if (socketWrapper.isClosed()) {
                return;
            }
            doRun();
        }
    }


    protected abstract void doRun();
}
