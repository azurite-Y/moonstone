package org.zy.moonstone.core.util.http;

/**
 * @dateTime 2022年5月23日;
 * @author zy(azurite-Y);
 * @description 动作挂钩。 Actions 表示 coyote servlet 容器用来请求对连接器进行操作的回调机制。在 ActionCode 中定义了一些标准操作，但是允许自定义操作。
 * param对象可用于传递和返回与操作相关的信息。此接口通常由ProtocolHandlers实现，参数通常是请求或响应对象。
 */
public interface ActionHook {
	/**
     * 向连接器发送一个操作
     * @param actionCode -  Type of the action
     * @param param - 操作参数
     */
    void action(ActionCode actionCode, Object param);
}
