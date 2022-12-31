package org.zy.moonStone.core.session.interfaces;

import java.util.EventListener;

/**
 * @dateTime 2021年12月30日;
 * @author zy(azurite-Y);
 * @description 为重要的Session生成事件定义侦听器的接口
 */
public interface SessionListener extends EventListener {

    /**
     * 确认指定事件的发生.
     *
     * @param event - 发生的SessionEvent
     */
    public void sessionEvent(SessionEvent event);
}
