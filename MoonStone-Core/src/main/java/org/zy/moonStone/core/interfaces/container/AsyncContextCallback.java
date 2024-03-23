package org.zy.moonstone.core.interfaces.container;

import javax.servlet.AsyncContext;

/**
 * @dateTime 2022年12月4日;
 * @author zy(azurite-Y);
 * @description 为连接器提供与 {@link AsyncContext } 通信的机制。它是以这种方式实现的。
 */
public interface AsyncContextCallback {
	public void fireOnComplete();

    /**
     * 报告与此异步请求关联的web应用程序是否可用。
     *
     * @return 如果关联的web应用程序可用，则为{@code true}，否则为{@code false}
     */
    public boolean isAvailable();
}
