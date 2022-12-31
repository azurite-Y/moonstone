package org.zy.moonStone.core.interfaces.container;

import java.util.EventObject;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description 用于通知侦听器容器上的重大更改的常规事件
 */
public final class ContainerEvent extends EventObject {
	private static final long serialVersionUID = -1387713993827188301L;


	/**
     * 与此事件关联的事件数据.
     */
    private final Object data;


    /**
     * 此实例表示的事件类型.
     */
    private final String type;


    /**
     * 用指定的参数构造一个新的ContainerEvent.
     *
     * @param container - 发生此事件的容器
     * @param type - 事件类型
     * @param data - 事件数据
     */
    public ContainerEvent(Container container, String type, Object data) {
        super(container);
        this.type = type;
        this.data = data;
    }


    /**
     *
     * @return 与此事件关联的数据（如果有）.
     */
    public Object getData() {
        return this.data;
    }


    /**
     *
     * @return 发生此事件的容器.
     */
    public Container getContainer() {
        return (Container) getSource();
    }


    /**
     *
     * @return 返回此事件的事件类型.
     */
    public String getType() {
        return this.type;
    }


    /**
     * 
     * @return 返回此事件的字符串表示形式
     */
    @Override
    public String toString() {
        return "ContainerEvent['" + getContainer() + "','" +
                getType() + "','" + getData() + "']";
    }
}
