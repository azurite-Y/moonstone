package org.zy.moonstone.core;

import java.util.EventObject;

import org.zy.moonstone.core.interfaces.container.Lifecycle;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 用于通知侦听器实现生命周期接口的组件上的重大更改的常规事件
 */
public class LifecycleEvent extends EventObject {
	private static final long serialVersionUID = 3356166543869732077L;

	/**
     * 使用指定的参数构造新的LifecycleEvent.
     *
     * @param lifecycle - 发生此事件的组件
     * @param type - 事件类型（必需）
     * @param data - 事件数据（如有）
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.type = type;
        this.data = data;
    }

    /**
     * 与此事件关联的事件数据.
     */
    private final Object data;


    /**
     * 此实例表示的事件类型.
     */
    private final String type;

    /**
     * @return 此事件的事件数据.
     */
    public Object getData() {
        return data;
    }

    /**
     * @return 发生此事件的生命周期.
     */
    public Lifecycle getLifecycle() {
        return (Lifecycle) getSource();
    }

    /**
     * @return 此事件的事件类型.
     */
    public String getType() {
        return this.type;
    }
}
