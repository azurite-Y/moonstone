package org.zy.moonStone.core.exceptions;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 为指示与生命周期相关的问题而引发的通用异常。此类异常通常应被视为对包含此组件的应用程序的操作是致命的
 */
public final class LifecycleException extends Exception {
	private static final long serialVersionUID = 4847884627337008808L;

	/**
     * 在没有其他信息的情况下构造新的LifecycleException.
     */
    public LifecycleException() {
        super();
    }


    /**
     * 为指定的消息构造新的LifecycleException.
     * @param message - 描述此异常的消息
     */
    public LifecycleException(String message) {
        super(message);
    }


    /**
     * 为指定的throwable构造新的LifecycleException.
     * @param throwable - 导致此异常的Throwable
     */
    public LifecycleException(Throwable throwable) {
        super(throwable);
    }


    /**
     * 为指定的messageand throwable构造新的LifecycleException.
     *
     * @param message - 描述此异常的消息
     * @param throwable - 导致此异常的Throwable
     */
    public LifecycleException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
