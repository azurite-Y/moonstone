package org.zy.moonStone.core.session.interfaces;

import java.security.Principal;
import java.util.Iterator;

import javax.servlet.http.HttpSession;

import org.zy.moonStone.core.interfaces.container.ContainerListener;

/**
 * @dateTime 2021年12月30日;
 * @author zy(azurite-Y);
 * @description Session是 HttpSession 的 moonStone 内部镜像，用于维护web应用程序中特定用户请求之间的状态信息
 */
public interface Session {
    // ----------------------------------------------------- 常量 -----------------------------------------------------
    /**
     * 创建会话时的SessionEvent事件类型.
     */
    public static final String SESSION_CREATED_EVENT = "createSession";


    /**
     * 会话被销毁时的SessionEvent事件类型.
     */
    public static final String SESSION_DESTROYED_EVENT = "destroySession";


    /**
     * 激活会话时的SessionEvent事件类型.
     */
    public static final String SESSION_ACTIVATED_EVENT = "activateSession";


    /**
     * 会话被钝化时的SessionEvent事件类型
     */
    public static final String SESSION_PASSIVATED_EVENT = "passivateSession";


    // ------------------------------------------------------------- 属性 -------------------------------------------------------------
    /**
     * @return 用于验证缓存 Principal  (如果有的话)的身份验证类型.
     */
    public String getAuthType();


    /**
     * 设置用于验证我们的缓存 Principal 的身份验证类型(如果有的话).
     *
     * @param authType - 新的缓存身份验证类型
     */
    public void setAuthType(String authType);


    /**
     * @return 此会话的创建时间.
     */
    public long getCreationTime();


    /**
     * @return 该会话的创建时间，绕过会话有效性检查.
     */
    public long getCreationTimeInternal();


    /**
     * 设置此会话的创建时间。当一个现有的Session实例被重用时，这个方法被manager调用.
     *
     * @param time - 新的创建时间
     */
    public void setCreationTime(long time);


    /**
     * @return 此会话的会话标识符（session id）.
     */
    public String getId();


    /**
     * @return 此会话的会话标识符（session id）.
     */
    public String getIdInternal();


    /**
     * 设置此会话的会话标识符，并通知任何关联的侦听器一个新会话已经创建。
     *
     * @param id - 新的会话标识符
     */
    public void setId(String id);


    /**
     * 设置此会话的会话标识符，并通知任何关联的侦听器一个新的会话已经创建.
     *
     * @param id - 新的会话标识符
     * @param notify - 是否应该通知任何关联的侦听器一个新会话已经创建?
     */
    public void setId(String id, boolean notify);


    /**
     * @return 客户端最后一次发送与此会话相关的请求的时间。应用程序所采取的操作，例如获取或设置与会话相关的值，不会影响访问时间。每当请求开始时，这个函数就会更新.
     */
    public long getThisAccessedTime();

    /**
     * @return 未进行无效检查的最后一次客户端访问时间
     * @see #getThisAccessedTime()
     */
    public long getThisAccessedTimeInternal();

    /**
     * @return 客户端最后一次发送与此会话相关的请求的时间。
     * 您的应用程序所采取的操作，例如获取或设置与会话相关的值，不会影响访问时间。每当一个请求完成时，它就会更新.
     */
    public long getLastAccessedTime();

    /**
     * @return 返回上次客户端访问时间，而不进行无效检查
     * @see #getLastAccessedTime()
     */
    public long getLastAccessedTimeInternal();

    /**
     * @return 从上次客户端访问时间算起的空闲时间(以毫秒计).
     */
    public long getIdleTime();

    /**
     * @return 从上次客户端访问时间开始的空闲时间，没有进行无效检查
     * @see #getIdleTime()
     */
    public long getIdleTimeInternal();

    /**
     * @return 此会话关联的管理器
     */
    public Manager getManager();

    /**
     * 设置此会话关联的管理器
     */
    public void setManager(Manager manager);

    /**
     * @return 在servlet容器使会话失效之前，客户端请求之间的最大时间间隔，以秒为单位。负时间表示会话永远不会超时.
     */
    public int getMaxInactiveInterval();

    /**
     * 在servlet容器使会话失效之前，设置客户端请求之间的最大时间间隔，超时则会话失效，以秒为单位。负时间表示会话永远不会超时.
     *
     * @param interval - 新的最大间隔
     */
    public void setMaxInactiveInterval(int interval);

    /**
     * 为这个会话设置isNew标志.
     *
     * @param isNew - isNew标志的新值
     */
    public void setNew(boolean isNew);


    /**
     * @return 与此会话关联的经过身份验证的主体。
     * 这为Authenticator提供了一种缓存先前经过身份验证的Principal的方法，并避免了在每个请求上调用开销可能很大的Realm.authenticate()。
     * 如果当前没有关联的主体，则返回null.
     */
    public Principal getPrincipal();


    /**
     * 设置与此会话关联的经过身份验证的主体。
     * 这为Authenticator提供了一种缓存先前经过身份验证的Principal的方法，并避免了在每个请求上调用开销可能很大的Realm.authenticate().
     *
     * @param principal - 新的Principal，如果没有则为空
     */
    public void setPrincipal(Principal principal);


    /**
     * @return 此对象作为其镜像的HttpSession.
     */
    public HttpSession getSession();


    /**
     * 为此会话设置isValid标志.
     *
     * @param isValid - isValid标志的新值
     */
    public void setValid(boolean isValid);


    /**
     * @return 如果会话仍然有效，则为true
     */
    public boolean isValid();


    // --------------------------------------------------------- 公共方法 ---------------------------------------------------------
    /**
     * 更新此会话的访问时间信息。当特定会话的请求传入时，即使应用程序没有引用它，上下文也应该调用此方法
     */
    public void access();


    /**
     * 将会话事件侦听器添加到此组件.
     *
     * @param listener - 会话事件应该被通知的SessionListener实例
     */
    public void addSessionListener(SessionListener listener);


    /**
     * 结束会话访问.
     */
    public void endAccess();


    /**
     * 执行使会话无效所需的内部处理，如果会话已经过期，则不会触发异常.
     */
    public void expire();


    /**
     * 
     * @param name - 需要返回的备注的名称
     * @return 指定名称的对象绑定到会话的内部注释，如果不存在这样的绑定，则为空.
     */
    public Object getNote(String name);


    /**
     * @return 包含该会话中存在的所有notes绑定的String名称的迭代器.
     */
    public Iterator<String> getNoteNames();


    /**
     * 释放所有对象引用，初始化实例变量，为重用此对象做准备.
     */
    public void recycle();


    /**
     * 删除此会话的内部注释中绑定到指定名称的任何对象.
     *
     * @param name - 待删除的note名称
     */
    public void removeNote(String name);


    /**
     * 从该组件中删除会话事件监听器.
     *
     * @param listener - 删除会话侦听器，它将不再被注意
     */
    public void removeSessionListener(SessionListener listener);


    /**
     * 将一个对象绑定到与该会话关联的内部注释中的指定名称，替换该名称的任何现有绑定.
     *
     * @param name - 对象应该绑定到的名称
     * @param value - 对象要绑定到指定的名称
     */
    public void setNote(String name, Object value);


    /**
     * 通知侦听器更改会话ID.
     *
     * @param newId  新的 session ID
     * @param oldId  旧的 session ID
     * @param notifySessionListeners - 任何关联的 {@link SessionListener}s 应该通知会话ID已经更改吗?
     * @param notifyContainerListeners - 是否应该通知任何关联的 {@link ContainerListener }会话ID已被更改?
     */
    public void tellChangedSessionId(String newId, String oldId,
            boolean notifySessionListeners, boolean notifyContainerListeners);


    /**
     * 会话实现是否支持给定属性的分发?如果Manager被标记为可分发的，那么这个方法必须在将属性添加到会话之前检查它们，并且如果建议的属性不是可分发的，则抛出IllegalArgumentException.
     * <p>
     * 注意，Manager实现可能会进一步限制哪些属性是分布式的，但是Manager级别的限制不应该在HttpSession中触发 {@link HttpSession#setAttribute(String, Object)}
     *
     * @param name - 属性名称
     * @param value - 属性值
     *
     * @return 如果支持分发，则为True，否则为false
     */
    public boolean isAttributeDistributable(String name, Object value);
}
