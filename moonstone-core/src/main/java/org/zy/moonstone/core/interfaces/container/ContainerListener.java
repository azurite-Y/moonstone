package org.zy.moonstone.core.interfaces.container;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description 为重要容器生成的事件定义侦听器的接口。请注意，“容器启动”和“容器停止”事件通常是生命周期事件，而不是容器事件
 */
public interface ContainerListener {
	/**
     * 确认指定事件的发生.
     *
     * @param event 已经发生的容器事件
     */
    public void containerEvent(ContainerEvent event);
}
