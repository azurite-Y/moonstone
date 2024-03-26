package org.zy.moonstone.core.session.interfaces;

import java.util.EventObject;

/**
 * @dateTime 2021年12月30日;
 * @author zy(azurite-Y);
 * @description 通知侦听器会话上的重大更改的一般事件
 */
public final class SessionEvent extends EventObject {
	private static final long serialVersionUID = -3860423006625982215L;

	/**
     * 与此事件关联的事件数据.
     */
    private final Object data;


    /**
     * 发生此事件的会话.
     */
    private final Session session;


    /**
     * 此实例表示的事件类型.
     */
    private final String type;


    /**
     * 用指定的参数构造一个新的SessionEvent.
     *
     * @param session - 发生此事件的会话
     * @param type - 事件类型
     * @param data - 事件数据
     */
    public SessionEvent(Session session, String type, Object data) {
        super(session);
        this.session = session;
        this.type = type;
        this.data = data;
    }


    /**
     * @return 该事件的事件数据.
     */
    public Object getData() {
        return this.data;
    }


    /**
     * @return 发生此事件的会话.
     */
    public Session getSession() {
        return this.session;
    }


    /**
     * @return 事件类型t.
     */
    public String getType() {
        return this.type;
    }


    @Override
    public String toString() {
        return "SessionEvent['" + getSession() + "','" + getType() + "']";
    }
}
