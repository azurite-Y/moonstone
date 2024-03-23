package org.zy.moonstone.core.interfaces.container;

import org.zy.moonstone.core.LifecycleEvent;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 接口，用于为实现Lifecycle接口的组件生成的重要事件（包括“componentstart”和“component stop”）定义侦听器。关联状态更改发生后，将触发侦听器
 */
public interface LifecycleListener {
	/**
     * 确认指定事件的发生.
     * @param event - 已发生的生命周期事件
     */
    public void lifecycleEvent(LifecycleEvent event);
}
