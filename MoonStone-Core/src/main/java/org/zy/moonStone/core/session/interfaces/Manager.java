package org.zy.moonStone.core.session.interfaces;

import java.beans.PropertyChangeListener;
import java.io.IOException;

import org.zy.moonStone.core.interfaces.container.Context;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * Manager管理与特定上下文相关联的会话池。不同的Manager实现可能支持增值特性，比如会话数据的持久存储，以及为可分布的web应用程序迁移会话
 * <p>为了让Manager实现成功操作实现重载的Context实现，它必须遵守以下约束:</p>
 * <ul>
 * <li>必须实现生命周期，以便上下文可以指示需要重启.
 * <li>必须允许在同一个Manager实例中调用stop()之后再调用start().
 * </ul>
 */
public interface Manager {
	// ------------------------------------------------------------- 属性 -------------------------------------------------------------
	/**
	 * 获取与此Manager相关联的上下文.
	 *
	 * @return 关联的上下文
	 */
	public Context getContext();

	/**
	 * 设置与此Manager相关联的上下文。在第一次使用Manager之前，上下文必须设置为一个非空值。
	 * 允许在首次使用前多次调用此方法。一旦使用了Manager，这个方法就不能用来更改与Manager关联的Context(包括设置空值).
	 *
	 * @param context - 新关联的上下文
	 */
	public void setContext(Context context);

	/**
	 * @return 会话id生成器
	 */
	public SessionIdGenerator getSessionIdGenerator();

	/**
	 * 设置会话id生成器
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator);

	/**
	 * 返回此管理器创建的会话总数.
	 *
	 * @return Total - 此管理器创建的会话数.
	 */
	public long getSessionCounter();

	/**
	 * 设置此管理器创建的会话总数.
	 */
	public void setSessionCounter(long sessionCounter);

	/**
	 * 获取同时处于活动状态的最大会话数.
	 *
	 * @return 同时处于活动状态的最大会话数
	 */
	public int getMaxActive();

	/**
	 * 设置在同一时间活动的最大会话数.
	 *
	 * @param maxActive - 同时处于活动状态的最大会话数
	 */
	public void setMaxActive(int maxActive);

	/**
	 * 获取当前活动会话的数量.
	 *
	 * @return 当前活动会话数
	 */
	public int getActiveSessions();

	/**
	 * 获取已过期的会话数.
	 *
	 * @return 会话过期的次数
	 */
	public long getExpiredSessions();

	/**
	 * 设置会话过期的数量.
	 *
	 * @param expiredSessions - 会话过期的次数
	 */
	public void setExpiredSessions(long expiredSessions);

	/**
	 * 获取由于达到活动会话的最大数目而未创建的会话数.
	 *
	 * @return 被拒绝的会话数
	 */
	public int getRejectedSessions();

	/**
	 * 获取过期会话的最长存活时间(以秒为单位).
	 *
	 * @return 过期会话存活的最长时间(以秒为单位).
	 */
	public int getSessionMaxAliveTime();

	/**
	 * 设置过期会话的最长存活时间(以秒为单位).
	 *
	 * @param sessionMaxAliveTime - 过期会话存活的最长时间(以秒计)
	 */
	public void setSessionMaxAliveTime(int sessionMaxAliveTime);

	/**
	 * 获取过期会话的平均存活时间(以秒为单位)。这可能是基于样本数据.
	 *
	 * @return 过期会话的平均存活时间(以秒为单位)
	 */
	public int getSessionAverageAliveTime();

	/**
	 * 获取当前会话创建速率(以会话每分钟为单位)。这可能是基于样本数据.
	 *
	 * @return  当前会话创建速率(以会话/分钟为单位)
	 */
	public int getSessionCreateRate();

	/**
	 * 获取会话过期的当前速率(以会话每分钟为单位)。这可能是基于样本数据
	 *
	 * @return  当前会话过期速率(以会话每分钟为单位)
	 */
	public int getSessionExpireRate();

	// ------------------------------------------------------------- 公共方法 -------------------------------------------------------------
	/**
	 * 将此会话添加到此管理器的活动会话集.
	 *
	 * @param session 要添加的会话
	 */
	public void add(Session session);

	/**
	 * 	向此组件添加PropertyChangeListener.
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener);

	/**
	 * 修改当前会话的会话ID为随机生成的新会话ID.
	 *
	 * @param session   要更改会话ID的会话
	 */
	public void changeSessionId(Session session);

	/**
     * 将当前会话的会话 ID 更改为随机生成的新会话 ID。
     *
     * @param session - 要更改其会话 ID 的会话
     * @return 新的会话 ID
     */
    public default String rotateSessionId(Session session) {
        String newSessionId = null;
        // 假设新的 Id 是重复的，直到证明它不是。 重复的可能性极低，但当前的 ManagerBase 代码可以防止重复，因此此默认方法也是如此。
        boolean duplicate = true;
        do {
            newSessionId = getSessionIdGenerator().generateSessionId();
            try {
                if (findSession(newSessionId) == null) {
                    duplicate = false;
                }
            } catch (IOException ioe) {
                // IOE 表示 ID 重复，因此继续循环
            }
        } while (duplicate);
        changeSessionId(session, newSessionId);
        return newSessionId;
    }
	
	/**
	 * 修改当前会话的session ID为指定的session ID.
	 *
	 * @param session - 要更改会话ID的会话
	 * @param newId - 新会话ID
	 */
	public void changeSessionId(Session session, String newId);

	/**
	 * 从回收的会话中获取一个会话，或者创建一个新的空会话。持久化会话管理器不需要创建会话数据，因为它从Store中读取.
	 *
	 * @return 空Session对象
	 */
	public Session createEmptySession();

	/**
	 * 根据此Manager的属性指定的默认设置，构造并返回一个新的会话对象。指定的sessionid将作为会话id。如果由于任何原因不能创建新的会话，则返回null.
	 *
	 * @param sessionId - 会话id应该被用来创建新的会话;如果为空，sessionid将由该方法分配，并通过返回的会话的getId()方法可用.
	 * @exception IllegalStateException - 如果一个新的会话由于任何原因不能被实例化
	 * @return 一个空的Session对象，带有给定的ID或者一个新创建的Session ID(如果没有指定)
	 */
	public Session createSession(String sessionId);

	/**
	 * 返回与这个Manager关联的活动会话，带有指定的会话id(如果有);否则返回null.
	 *
	 * @param id - 要返回的会话id
	 * @exception IllegalStateException - 如果一个新的会话由于任何原因不能被实例化
	 * @exception IOException - 如果在处理此请求时发生输入/输出错误
	 * @return 如果没有找到请求ID的会话，则返回null
	 */
	public Session findSession(String id) throws IOException;

	/**
	 * 返回与该管理器关联的活动会话集。如果这个Manager没有活动的会话，则返回一个零长度的数组.
	 *
	 * @return 由该管理器管理的所有当前活动的会话
	 */
	public Session[] findSessions();

	/**
	 * 将之前卸载的任何当前活动会话加载到适当的持久性机制（如果有）。 如果不支持持久化，则此方法返回而不执行任何操作。
	 *
	 * @exception ClassNotFoundException - 如果在重载期间无法找到序列化的类
	 * @exception IOException - 如果输入/输出错误
	 */
	public void load() throws ClassNotFoundException, IOException;

	/**
	 * 将此会话从该管理器的活动会话中删除.
	 */
	public void remove(Session session);

	/**
	 * 将此会话从该管理器的活动会话中删除.
	 *
	 * @param session - 需删除的session
	 * @param update - 是否应该更新过期统计信息
	 */
	public void remove(Session session, boolean update);

	/**
	 * 在适当的持久化机制中保存当前活动的会话(如果有的话)。如果不支持持久化，该方法将不做任何操作返回.
	 *
	 * @exception IOException - 如果输入/输出错误
	 */
	public void unload() throws IOException;

	/**
     * 该方法将由上下文/容器周期性地调用，并允许管理器实现执行周期性任务的方法，例如即将到期的会话等。
     */
    public void backgroundProcess();

    /**
     * Manager会分发给定的会话属性吗？Manager实现可能提供额外的配置选项来控制哪些属性是可分发的。
     *
     * @param name  - 属性名称
     * @param value - 属性值
     *
     * @return 如果Manager分发给定的属性，则为{@code true}，否则为{@code false}
     */
    public boolean willAttributeDistribute(String name, Object value);

    /**
     * 当已经存在于会话中的属性以相同的名称再次添加时，该属性实现了 {@link javax.servlet.http.HttpSessionBindingListener}，
     * 是否应该在 {@link javax.servlet.http.HttpSessionBindingListener#valueUnbound(javax.servlet.http.HttpSessionBindingEvent)} 
     * 之后调用 {@link javax.servlet.http.HttpSessionBindingListener#valueBound(javax.servlet.http.HttpSessionBindingEvent)} ？
     * <p>
     * 默认值是 {@code false}.
     *
     * @return 如果监听器将被通知，则为 {@code true}，如果没有，则为 {@code false}
     */
    public default boolean getNotifyBindingListenerOnUnchangedValue() {
        return false;
    }

    /**
     * 配置是否在调用 {@link javax.servlet.http.HttpSessionBindingListener#valueUnbound(javax.servlet.http.HttpSessionBindingEvent)}，
     * 之后调用 {@link javax.servlet.http.HttpSessionBindingListener#valueBound(javax.servlet.http.HttpSessionBindingEvent)} 
     * 当属性已经存在会话中时以相同的名称再次添加，并且该属性实现 javax.servlet.http.HttpSessionBindingListener。
     *
     * @param notifyBindingListenerOnUnchangedValue - {@code true} 会调用监听器，{@code false} 不会
     */
    public void setNotifyBindingListenerOnUnchangedValue(boolean notifyBindingListenerOnUnchangedValue);

    /**
     * 当会话中已经存在的属性再次以相同名称添加并且为会话配置了{@link javax.servlet.http.HttpSessionAttributeListener} 时，应调用
     * {@link javax.servlet.http.HttpSessionAttributeListener#attributeReplaced(javax.servlet.http.HttpSessionBindingEvent)} 吗？
     * <p>
     * 默认值是 {@code true}.
     *
     * @return 如果将通知侦听器，则为 {@code true}，如果不通知，则为 {@code false}
     */
    public default boolean getNotifyAttributeListenerOnUnchangedValue() {
        return true;
    }

    /**
     * 配置
     * {@link javax.servlet.http.HttpSessionAttributeListener#attributeReplaced(javax.servlet.http.HttpSessionBindingEvent)}
     * 是否以相同名称再次添加会话中已存在的属性并为会话配置 {@link javax.servlet.http.HttpSessionAttributeListener}。
     *
     * @param notifyAttributeListenerOnUnchangedValue - {@code true} 将调用监听器，{@code false } 不会
     */
    public void setNotifyAttributeListenerOnUnchangedValue(boolean notifyAttributeListenerOnUnchangedValue);
}
