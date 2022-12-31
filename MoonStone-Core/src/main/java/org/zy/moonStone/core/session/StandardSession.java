package org.zy.moonStone.core.session;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.interfaces.container.ContainerListener;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.security.SecurityUtil;
import org.zy.moonStone.core.session.interfaces.Manager;
import org.zy.moonStone.core.session.interfaces.Session;
import org.zy.moonStone.core.session.interfaces.SessionEvent;
import org.zy.moonStone.core.session.interfaces.SessionListener;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年8月10日;
 * @author zy(azurite-Y);
 * @description Session接口的标准实现。这个对象是可序列化的，因此它可以存储在持久存储中，或者转移到不同的JVM以支持分布式会话。
 *              <p>
 *              注意:这个类的实例代表了会话的内部(Session)和应用层(HttpSession)视图。
 *              但是，因为类本身没有声明为公共的，所以
 *              <code>org.zy.moonStone.core.session </code>包外的Java逻辑不能将这个实例的httpsession视图转换回Session视图。
 *              <p>
 *              注意:如果你向这个类添加字段，你必须确保你在读/写对象方法中携带它们，这样这个类才会被正确序列化。
 */
@SuppressWarnings("deprecation")
public class StandardSession implements HttpSession, Session, Serializable {
	private static final long serialVersionUID = 6104316552568428979L;

	protected static final boolean STRICT_SERVLET_COMPLIANCE;

	protected static final boolean ACTIVITY_CHECK;

	protected static final boolean LAST_ACCESS_AT_START;

	static {
		STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

		String activityCheck = System.getProperty("org.zy.moonStone.core.session.StandardSession.ACTIVITY_CHECK");
		if (activityCheck == null) {
			ACTIVITY_CHECK = STRICT_SERVLET_COMPLIANCE;
		} else {
			ACTIVITY_CHECK = Boolean.parseBoolean(activityCheck);
		}

		String lastAccessAtStart = System.getProperty("org.zy.moonStone.core.session.StandardSession.LAST_ACCESS_AT_START");
		if (lastAccessAtStart == null) {
			LAST_ACCESS_AT_START = STRICT_SERVLET_COMPLIANCE;
		} else {
			LAST_ACCESS_AT_START = Boolean.parseBoolean(lastAccessAtStart);
		}
	}

	protected static final String EMPTY_ARRAY[] = new String[0];

	/** 与此会话关联的用户数据属性的集合 */
	protected ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

	/** 用于对缓存的主体进行身份验证的身份验证类型(如果有的话)。注意:该值不包含在该对象的序列化版本中。 */
	protected transient String authType = null;

	/** 此会话创建的时间，以毫秒为单位，自格林尼治标准时间1970年1月1日零点开始。 */
	protected long creationTime = 0L;

	/**
	 * 目前可能正在处理一个会话过期，因此设置此值以绕过某些 {@link IllegalStateException } 测试。注意:该值不包含在该节点的序列化版本中。
	 */
	protected transient volatile boolean expiring = false;

	/** 与此会话关联的 facade。注意:该值不包含在该对象的序列化版本中。 */
	protected transient StandardSessionFacade facade = null;

	/** 此会话的会话标识符。 */
	protected String id = null;

	/** 此会话的最后一次访问时间 */
	protected volatile long lastAccessedTime = creationTime;

	/** 此会话的会话事件监听器 */
	protected transient ArrayList<SessionListener> listeners = new ArrayList<>();

	/** 与此会话关联的管理器 */
	protected transient Manager manager = null;

	/** 在 servlet 容器可能使此会话无效之前，客户端请求之间的最大时间间隔（以秒为单位）。 负时间表示会话永远不会超时  */
	protected volatile int maxInactiveInterval = -1;

	/** 指示此会话是否是新的标志 */
	protected volatile boolean isNew = false;

	/** 指示此会话是否有效的标志 */
	protected volatile boolean isValid = false;

	/** 由组件和事件监听器与此会话关联的内部 note。注意:该对象不会跨会话序列化保存和存储！*/
	protected transient Map<String, Object> notes = new Hashtable<>();

	/** 与此会话关联的经过身份验证的主体(如果有)。注意:该对象不跨会话序列化保存和恢复！ */
	protected transient Principal principal = null;

	/** 此组件的属性更改支持。注意:该值不包含在该对象的序列化版本中。 */
	protected final transient PropertyChangeSupport support = new PropertyChangeSupport(this);

	/** 此会话的当前访问时间 */
	protected volatile long thisAccessedTime = creationTime;

	/** 此会话的访问次数 */
	protected transient AtomicInteger accessCount = null;

	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 构造一个与指定Manager关联的新Session。
	 * 
	 * @param manager - 与此会话关联的管理器
	 */
	public StandardSession(Manager manager) {
		this.manager = manager;

		// 初始化访问数
		if (ACTIVITY_CHECK) {
			accessCount = new AtomicInteger();
		}
	}
	
    @Override
    public String getAuthType() {
        return this.authType;
    }

    /**
     * 设置用于验证缓存的 Principal 的身份验证类型（如果有的话）
     *
     * @param authType - 新的缓存身份验证类型
     */
    @Override
    public void setAuthType(String authType) {
        this.authType = authType;
    }

    /**
     * 设置此会话的创建时间。当重用现有的Session实例时，Manager会调用这个方法。
     *
     * @param time - 新的创建时间
     */
    @Override
    public void setCreationTime(long time) {
        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;
    }

    /**
     * @return 此会话的会话标识符（session id）
     */
    @Override
    public String getId() {
        return this.id;
    }

    /**
     * @return 此会话的会话标识符（session id）
     */
    @Override
    public String getIdInternal() {
        return this.id;
    }

    /**
     * 设置此会话的会话标识符
     *
     * @param id - 新的会话标识符
     */
    @Override
    public void setId(String id) {
        setId(id, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setId(String id, boolean notify) {
        if ((this.id != null) && (manager != null)) manager.remove(this);

        this.id = id;

        if (manager != null)
            manager.add(this);

        if (notify) {
            tellNew();
        }
    }

    /**
     * 通知监听器关于新会话的信息
     */
    public void tellNew() {
        // 通知感兴趣的会话事件监听器
        fireSessionEvent(Session.SESSION_CREATED_EVENT, null);

        // 通知感兴趣的应用程序事件监听器
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationLifecycleListeners();
        if (listeners != null && listeners.length > 0) {
            HttpSessionEvent event = new HttpSessionEvent(getSession());
            for (Object o : listeners) {
                if (!(o instanceof HttpSessionListener)) continue;
                
                HttpSessionListener listener = (HttpSessionListener) o;
                try {
                    context.fireContainerEvent("beforeSessionCreated", listener);
                    listener.sessionCreated(event);
                    context.fireContainerEvent("afterSessionCreated", listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    try {
                        context.fireContainerEvent("afterSessionCreated", listener);
                    } catch (Exception e) {
                        // Ignore
                    }
                    manager.getContext().getLogger().error("ContainerEvent-NewSessionCreated", t);
                }
            }
        }

    }

    /**
     * 通知侦听器更改会话 ID已更改
     *
     * @param newId - 新的 session ID
     * @param oldId - 旧的 session ID
     * @param notifySessionListeners - 任何关联的会话监听器是否应该通知会话ID已经更改?
     * @param notifyContainerListeners 是否应该通知任何关联的 {@link ContainerListener }会话ID已经更改?
     */
    @Override
    public void tellChangedSessionId(String newId, String oldId, boolean notifySessionListeners, boolean notifyContainerListeners) {
        Context context = manager.getContext();
         // 通知 ContainerListeners
        if (notifyContainerListeners) {
            context.fireContainerEvent(Context.CHANGE_SESSION_ID_EVENT, new String[] {oldId, newId});
        }

        // 通知 HttpSessionIdListener
        if (notifySessionListeners) {
            Object listeners[] = context.getApplicationEventListeners();
            if (listeners != null && listeners.length > 0) {
                HttpSessionEvent event = new HttpSessionEvent(getSession());

                for(Object listener : listeners) {
                    if (!(listener instanceof HttpSessionIdListener)) continue;

                    HttpSessionIdListener idListener = (HttpSessionIdListener)listener;
                    try {
                        idListener.sessionIdChanged(event, oldId);
                    } catch (Throwable t) {
                        manager.getContext().getLogger().error("HttpSessionEvent-SessionIdChanged", t);
                    }
                }
            }
        }
    }

    /**
     * 返回客户端上次发送与此会话关联的请求的时间，以自1970GMT 1月1日午夜以来的毫秒数表示。
     * 应用程序执行的操作，如获取或设置与会话关联的值，不会影响访问时间。请求启动时，此操作会更新。
     */
    @Override
    public long getThisAccessedTime() {
    	isValidInternal("当前 Session 已失效，无法获取当前访问时间");
        return this.thisAccessedTime;
    }

	/**
	 * 返回上次客户端访问时间，而不进行会话失效检查
	 * 
	 * @see #getThisAccessedTime()
	 */
	@Override
	public long getThisAccessedTimeInternal() {
		return this.thisAccessedTime;
	}

	/**
	 * @return 客户端上次发送与此会话关联的请求的时间，以自1970年1月1日午夜(格林威治标准时间)以来的毫秒数表示。
	 * 应用程序执行的操作(如获取或设置与会话关联的值)不会影响访问时间。每当请求完成时，这个请求就会被更新。
	 */
	@Override
	public long getLastAccessedTime() {
    	isValidInternal("当前 Session 已失效，无法获取上次访问时间");
		return this.lastAccessedTime;
	}

	/**
	 * @see #getLastAccessedTime()
	 */
	@Override
	public long getLastAccessedTimeInternal() {
		return this.lastAccessedTime;
	}

	@Override
	public long getIdleTime() {
    	isValidInternal("当前 Session 已失效，无法获取自上次客户端访问时间开始的空闲时间");
		return getIdleTimeInternal();
	}

	/**
	 * @see #getIdleTime()
	 */
	@Override
	public long getIdleTimeInternal() {
		long timeNow = System.currentTimeMillis();
		long timeIdle;
		if (LAST_ACCESS_AT_START) {
			timeIdle = timeNow - lastAccessedTime;
		} else {
			timeIdle = timeNow - thisAccessedTime;
		}
		return timeIdle;
	}

	@Override
	public Manager getManager() {
		return this.manager;
	}

	@Override
	public void setManager(Manager manager) {
		this.manager = manager;
	}

	/**
	 * 返回在Servlet容器使会话无效之前客户端请求之间的最大时间间隔(秒)。负时间表示会话永远不应超时。
	 */
	@Override
	public int getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	/**
	 * 设置在 servlet 容器使会话无效之前客户端请求之间的最大时间间隔（以秒为单位）。 零或负时间表示会话不应超时。
	 *
	 * @param interval - 新的最大区间
	 */
	@Override
	public void setMaxInactiveInterval(int interval) {
		this.maxInactiveInterval = interval;
	}

	/**
	 * 设置此会话的 <code>isNew</code> 标志
	 */
	@Override
	public void setNew(boolean isNew) {
		this.isNew = isNew;
	}

	/**
	 * 返回与此 Session 关联的经过身份验证的 Principal。
	 *  这为 Authenticator 提供了一种缓存先前经过身份验证的 Principal 的方法，并避免对每个请求进行潜在的昂贵 <code>Realm.authenticate()</code> 调用。 
	 *  如果没有当前关联的 Principal，则返回 <code>null</code>。
	 */
	@Override
	public Principal getPrincipal() {
		return this.principal;
	}

	/**
	 * 设置与此 Session 关联的经过身份验证的 Principal。 
	 * 这为 Authenticator 提供了一种缓存先前经过身份验证的 Principal 的方法，并避免对每个请求进行潜在的昂贵 <code>Realm.authenticate()</code> 调用。
	 *
	 * @param principal - 新的Principal，如果没有则为 <code>null</code>
	 */
	@Override
	public void setPrincipal(Principal principal) {
		this.principal = principal;
	}

	/**
	 * 
	 * 返回此对象为其外观的 HttpSession
	 * Return the <code>HttpSession</code> for which this object is the facade.
	 */
	@Override
	public HttpSession getSession() {
		if (facade == null) {
			if (SecurityUtil.isPackageProtectionEnabled()) {
				facade = AccessController.doPrivileged(new PrivilegedNewSessionFacade(this));
			} else {
				facade = new StandardSessionFacade(this);
			}
		}
		return facade;
	}

	@Override
	public boolean isValid() {
		if (!this.isValid) {
			return false;
		}

		if (this.expiring) {
			return true;
		}

		if (ACTIVITY_CHECK && accessCount.get() > 0) {
			return true;
		}

		if (maxInactiveInterval > 0) {
			int timeIdle = (int) (getIdleTimeInternal() / 1000L);
			if (timeIdle >= maxInactiveInterval) {
				expire(true);
			}
		}

		return this.isValid;
	}

	@Override
	public void setValid(boolean isValid) {
		this.isValid = isValid;
	}

    // -------------------------------------------------------------------------------------
    // Session 公共方法
    // -------------------------------------------------------------------------------------
	@Override
	public void access() {
		this.thisAccessedTime = System.currentTimeMillis();

		if (ACTIVITY_CHECK) {
			accessCount.incrementAndGet();
		}
	}

	@Override
	public void endAccess() {
		isNew = false;

		/** servlet规范要求忽略lastAccessedTime中的请求处理时间 */
		if (LAST_ACCESS_AT_START) {
			this.lastAccessedTime = this.thisAccessedTime;
			this.thisAccessedTime = System.currentTimeMillis();
		} else {
			this.thisAccessedTime = System.currentTimeMillis();
			this.lastAccessedTime = this.thisAccessedTime;
		}

		if (ACTIVITY_CHECK) {
			accessCount.decrementAndGet();
		}
	}

	@Override
	public void addSessionListener(SessionListener listener) {
		listeners.add(listener);
	}

	@Override
	public void expire() {
		expire(true);
	}

	/**
	 * 执行使会话失效所需的内部处理，如果会话已经过期，则不会触发异常。
	 *
	 * @param notify - 是否通知监听器当前 Session 失效？
	 */
	public void expire(boolean notify) {
		// 检查会话是否已经失效。 此时不要检查过期，因为在 isValid 为 false 之前 expire() 方法不应返回
		if (!isValid) return;

		synchronized (this) {
			// 双重检查
			if (expiring || !isValid)
				return;

			if (manager == null)
				return;

			// 将此会话标记为“已过期”
			expiring = true;

			// 通知感兴趣的应用程序事件监听器
			Context context = manager.getContext();

			// 对expire()的调用可能没有被webapp触发。确保webapp的类加载器在调用监听器时被设置
			if (notify) {
                ClassLoader oldContextClassLoader = null;
                try {
                    oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
                    
					Object listeners[] = context.getApplicationLifecycleListeners();
					if (listeners != null && listeners.length > 0) {
						HttpSessionEvent event = new HttpSessionEvent(getSession());
						for (int i = 0; i < listeners.length; i++) {
							int j = (listeners.length - 1) - i;
							if (!(listeners[j] instanceof HttpSessionListener))
								continue;
							HttpSessionListener listener = (HttpSessionListener) listeners[j];
							try {
								context.fireContainerEvent("beforeSessionDestroyed", listener);
								listener.sessionDestroyed(event);
								context.fireContainerEvent("afterSessionDestroyed", listener);
							} catch (Throwable t) {
								ExceptionUtils.handleThrowable(t);
								try {
									context.fireContainerEvent("afterSessionDestroyed", listener);
								} catch (Exception e) {
									// Ignore
								}
		                        manager.getContext().getLogger().error("HttpSessionEvent-SessionDestroyed", t);
							}
						}
					}
				} finally {
					context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
				}
			}

			if (ACTIVITY_CHECK) {
				accessCount.set(0);
			}

			manager.remove(this, true);

			// 通知感兴趣的会话事件监听器
			if (notify) {
				fireSessionEvent(Session.SESSION_DESTROYED_EVENT, null);
			}

			// 完成 Session 失效
			setValid(false);
			expiring = false;

			// 解除与此会话关联的所有对象的绑定
			String keys[] = keys();
            ClassLoader oldContextClassLoader = null;
            try {
                oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
				for (String key : keys) {
					removeAttributeInternal(key, notify);
				}
			} finally {
				context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
			}
		}

	}

	/**
	 * 进行持久化此会话所需的内部处理
	 */
	public void passivate() {
		// 通知感兴趣的会话事件监听器
		fireSessionEvent(Session.SESSION_PASSIVATED_EVENT, null);

		// 通知 ActivationListeners
		HttpSessionEvent event = null;
		String keys[] = keys();
		for (String key : keys) {
			Object attribute = attributes.get(key);
			if (attribute instanceof HttpSessionActivationListener) {
				if (event == null)
					event = new HttpSessionEvent(getSession());
				try {
					((HttpSessionActivationListener) attribute).sessionWillPassivate(event);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error("HttpSessionEvent-SessionPassivate", t);
				}
			}
		}
	}

	/**
	 * 执行激活此会话所需的内部处理
	 */
	public void activate() {
		// 初始化访问计数
		if (ACTIVITY_CHECK) {
			accessCount = new AtomicInteger();
		}

		// 通知感兴趣的会话事件监听器
		fireSessionEvent(Session.SESSION_ACTIVATED_EVENT, null);

		// 通知 ActivationListeners
		HttpSessionEvent event = null;
		String keys[] = keys();
		for (String key : keys) {
			Object attribute = attributes.get(key);
			if (attribute instanceof HttpSessionActivationListener) {
				if (event == null)
					event = new HttpSessionEvent(getSession());
				try {
					((HttpSessionActivationListener) attribute).sessionDidActivate(event);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error("HttpSessionEvent-SessionDidActivate", t);
				}
			}
		}
	}

	@Override
	public Object getNote(String name) {
		return notes.get(name);
	}

	@Override
	public Iterator<String> getNoteNames() {
		return notes.keySet().iterator();
	}

	@Override
	public void recycle() {
		// 重置与此 Session 关联的实例变量
		attributes.clear();
		setAuthType(null);
		creationTime = 0L;
		expiring = false;
		id = null;
		lastAccessedTime = 0L;
		maxInactiveInterval = -1;
		notes.clear();
		setPrincipal(null);
		isNew = false;
		isValid = false;
		manager = null;
	}

	@Override
	public void removeNote(String name) {
		notes.remove(name);
	}

	@Override
	public void removeSessionListener(SessionListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void setNote(String name, Object value) {
		notes.put(name, value);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("StandardSession[");
		sb.append(id);
		sb.append("]");
		return sb.toString();
	}

	// -------------------------------------------------------------------------------------
	// Session 包方法
	// -------------------------------------------------------------------------------------
	/**
	 * 从指定的对象输入流中读取此会话对象内容的序列化版本，而无需对 StandardSession 本身进行序列化。
	 *
	 * @param stream - 要读取的对象输入流
	 *
	 * @exception ClassNotFoundException - 如果指定了未知类
	 * @exception IOException - 如果发生输入/输出错误
	 */
	public void readObjectData(ObjectInputStream stream) throws ClassNotFoundException, IOException {
		doReadObject(stream);
	}

	/**
	 * 将此会话对象的内容的序列化版本写入指定的对象输出流，而不要求标准会话本身已被序列化。
	 *
	 * @param stream - 要写入的对象输出流
	 * @exception IOException - 如果发生输入/输出错误
	 */
	public void writeObjectData(ObjectOutputStream stream) throws IOException {
		doWriteObject(stream);
	}

	// -------------------------------------------------------------------------------------
	// HttpSession 属性
	// -------------------------------------------------------------------------------------
	/**
	 * 返回创建此会话的时间，从格林威治标准时间 1970 年 1 月 1 日午夜开始的毫秒数
	 *
	 * @exception IllegalStateException - 一个长的指定创建此会话的时间，表示自 1970 年 1 月 1 日格林威治标准时间以来的毫秒数
	 */
	@Override
	public long getCreationTime() {
    	isValidInternal("当前 Session 已失效，无法获取 Session 创建时间");
    	
		return this.creationTime;
	}

	/**
	 * 返回创建此会话的时间，以毫秒为单位，自格林威治标准时间 1970 年 1 月 1 日午夜开始，绕过会话验证检查。
	 */
	@Override
	public long getCreationTimeInternal() {
		return this.creationTime;
	}

	/**
	 * 返回此会话所属的 ServletContext
	 */
	@Override
	public ServletContext getServletContext() {
		if (manager == null) {
			return null;
		}
		Context context = manager.getContext();
		return context.getServletContext();
	}

	// -------------------------------------------------------------------------------------
	// HttpSession 公共方法
	// -------------------------------------------------------------------------------------
	/**
	 * 在此会话中返回与指定名称绑定的对象，如果没有对象与该名称绑定，则返回 <code>null</code>
	 *
	 * @param name - 要返回的属性名称
	 * @return 具有指定名称的对象
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public Object getAttribute(String name) {
    	isValidInternal("当前 Session 已失效，无法访问指定属性");
		return name == null ? null : attributes.get(name);
	}

	/**
	 * 返回包含绑定到此会话的对象名称的字符串对象的枚举
	 *
	 * @return 字符串对象的枚举，指定绑定到此会话的所有对象的名称
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public Enumeration<String> getAttributeNames() {
    	isValidInternal("当前 Session 已失效，无法获取内部属性名集合");

		Set<String> names = new HashSet<>(attributes.keySet());
		return Collections.enumeration(names);
	}

	/**
	 * 在此会话中返回与指定名称绑定的对象，如果没有对象与该名称绑定，则返回 null。
	 *
	 * @param name - 要返回的值的名称
	 * @return 具有指定名称的对象
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 * @deprecated 从 2.2 版开始，此方法由 <code>getAttribute()</code> 代替
	 */
	@Override
	@Deprecated
	public Object getValue(String name) {
		return getAttribute(name);
	}

	/**
	 * 返回绑定到此会话的对象的名称集。 如果没有这样的对象，则返回一个长度为零的数组。
	 *
	 * @return 字符串对象数组，指定绑定到此会话的所有对象的名称
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 * @deprecated - 从版本 2.2 开始，此方法由 <code>getAttributeNames()</code> 代替
	 *             
	 */
	@Override
	@Deprecated
	public String[] getValueNames() {
    	isValidInternal("当前 Session 已失效，无法获取内部属性名集合");

		return keys();
	}

	/**
	 * 使此会话无效，然后解除绑定到它的任何对象。
	 *
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public void invalidate() {
    	isValidInternal("当前 Session 已失效，无法获取内部属性名集合");

		// 导致此会话过期
		expire();
	}

	/**
	 * 如果客户端还不知道会话，或者客户端选择不加入会话，则返回 true。 
	 * 例如，如果服务器仅使用基于 cookie 的会话，而客户端已禁用 cookie，则每个请求都会有一个新会话。
	 *
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public boolean isNew() {
    	isValidInternal("当前 Session 已失效");

		return this.isNew;
	}

	/**
	 * 使用指定的名称将对象绑定到此会话。 如果同名对象已绑定到此会话，则替换该对象。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用 <code>valueBound()</code>。
	 *
	 * @param name - 对象绑定的名称不能为空
	 * @param value - 要绑定的对象，不能为空
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 * @deprecated - 从版本 2.2 开始，此方法由 <code>setAttribute()</code> 代替
	 */
	@Override
	@Deprecated
	public void putValue(String name, Object value) {
		setAttribute(name, value);
	}

	/**
	 * 从此会话中移除与指定名称绑定的对象。 如果会话没有与此名称绑定的对象，则此方法不执行任何操作。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用 <code>valueUnbound()</code>。
	 *
	 * @param name - 要从此会话中删除的对象的名称
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 * @deprecated - 从版本 2.2 开始，此方法由 <code>removeAttribute()</code> 代替
	 */
	@Override
	@Deprecated
	public void removeValue(String name) {
		removeAttribute(name);
	}

	/**
	 * 从此会话中移除与指定名称绑定的对象。 如果会话没有与此名称绑定的对象，则此方法不执行任何操作。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用 <code>valueUnbound()</code>。
	 *
	 * @param name - 要从此会话中删除的对象的名称
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public void removeAttribute(String name) {
		removeAttribute(name, true);
	}

	/**
	 * 从此会话中移除与指定名称绑定的对象。 如果会话没有与此名称绑定的对象，则此方法不执行任何操作。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用 <code>valueUnbound()</code>。
	 *
	 * @param name - 要从此会话中删除的对象的名称
	 * @param notify -  是否通知感兴趣的监听器指定删除正在被删除
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	public void removeAttribute(String name, boolean notify) {
    	isValidInternal("当前 Session 已失效，无法获取内部属性名集合");

		removeAttributeInternal(name, notify);
	}

	/**
	 * 从此会话中移除与指定名称绑定的对象。 如果会话没有与此名称绑定的对象，则此方法不执行任何操作。
	 * <p>
	 * 此方法执行后，如果对象实现了 <code>HttpSessionBindingListener</code>，则容器会在对象上调用 <code>valueUnbound()</code>。
	 * 
	 * @param name - 要从此会话中删除的对象的名称
	 * @param notify - 应该通知感兴趣的监听器这个属性正在被删除吗？
	 */
	protected void removeAttributeInternal(String name, boolean notify) {
		// 避免 NPE
		if (name == null)
			return;
	
		// 从集合中删除此属性
		Object value = attributes.remove(name);
	
		// 是否需要 valueUnbound() 和 attributeRemoved() 通知
		if (!notify || (value == null)) {
			return;
		}
	
		// 必要时调用 valueUnbound() 方法
		HttpSessionBindingEvent event = null;
		if (value instanceof HttpSessionBindingListener) {
			event = new HttpSessionBindingEvent(getSession(), name, value);
			((HttpSessionBindingListener) value).valueUnbound(event);
		}
	
		// 通知感兴趣的应用程序事件监听器
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null)
			return;
		
		for (Object o : listeners) {
			if (!(o instanceof HttpSessionAttributeListener)) {
				continue;
			}
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) o;
			try {
				context.fireContainerEvent("beforeSessionAttributeRemoved", listener);
				if (event == null) {
					event = new HttpSessionBindingEvent(getSession(), name, value);
				}
				listener.attributeRemoved(event);
				context.fireContainerEvent("afterSessionAttributeRemoved", listener);
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				try {
					context.fireContainerEvent("afterSessionAttributeRemoved", listener);
				} catch (Exception e) {
					// Ignore
				}
				manager.getContext().getLogger().error("HttpSessionBindingEvent-SessionAttributeRemoved", t);
			}
		}
	}

	/**
	 * 使用指定的名称将对象绑定到此会话。 如果同名对象已绑定到此会话，则替换该对象。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用<code>valueBound()</code>。
	 *
	 * @param name  - 绑定对象的名称，不能为空
	 * @param value - 要绑定的对象，不能为空
	 * @exception IllegalArgumentException - 如果尝试在标记为可分发的环境中添加不可序列化的对象。
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	@Override
	public void setAttribute(String name, Object value) {
		setAttribute(name, value, true);
	}

	/**
	 * 使用指定的名称将对象绑定到此会话。 如果同名对象已绑定到此会话，则替换该对象。
	 * <p>
	 * 此方法执行后，如果对象实现了<code>HttpSessionBindingListener</code>，则容器会在对象上调用<code>valueBound()</code>。
	 * 
	 * @param name  - 绑定对象的名称，不能为空
	 * @param value - 要绑定的对象，不能为空
	 * @exception IllegalArgumentException - 如果尝试在标记为可分发的环境中添加不可序列化的对象。
	 * @exception IllegalStateException - 如果在无效会话上调用此方法
	 */
	public void setAttribute(String name, Object value, boolean notify) {
		// 名称不能为空
		if (name == null) {
			throw new IllegalArgumentException("绑定对象的名称，不能为空");
		}

		// Null 值 则与 removeAttribute() 相同的 
		if (value == null) {
			removeAttribute(name);
			return;
		}

		// 验证当前状态
    	isValidInternal("当前 Session 已失效，无法设置指定属性值，by Session ID：" + getIdInternal());

		Context context = manager.getContext();

		if (context.getDistributable() && !isAttributeDistributable(name, value) && !exclude(name, value)) {
			throw new IllegalArgumentException("尝试在标记为可分发的环境中添加不可序列化的对象，by name：" + name);
		}
		// 使用新值构造事件
		HttpSessionBindingEvent event = null;

		// 如果需要，调用 valueBound() 方法
		if (notify && value instanceof HttpSessionBindingListener) {
			// 如果替换为相同的值，则不要调用任何通知，除非配置为这样做
			Object oldValue = attributes.get(name);
			if (value != oldValue || manager.getNotifyBindingListenerOnUnchangedValue()) {
				event = new HttpSessionBindingEvent(getSession(), name, value);
				try {
					((HttpSessionBindingListener) value).valueBound(event);
				} catch (Throwable t) {
                    manager.getContext().getLogger().error ("HttpSessionBindingEvent-AttributeValueBound", t);
				}
			}
		}

		// 替换或添加此属性
		Object unbound = attributes.put(name, value);

		// 必要时调用 valueUnbound() 方法
		if (notify && unbound instanceof HttpSessionBindingListener) {
			// 如果替换为相同的值，则不要调用任何通知，除非配置为这样做
			if (unbound != value || manager.getNotifyBindingListenerOnUnchangedValue()) {
				try {
					((HttpSessionBindingListener) unbound).valueUnbound(new HttpSessionBindingEvent(getSession(), name));
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
					manager.getContext().getLogger().error("HttpSessionBindingEvent-AttributeValueUnbound", t);
				}
			}
		}

		if (!notify) {
			return;
		}

		// 通知感兴趣的应用程序事件监听器，session 属性值发生了更换
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null) {
			return;
		}
		for (Object o : listeners) {
			if (!(o instanceof HttpSessionAttributeListener)) {
				continue;
			}
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) o;
			try {
				if (unbound != null) {
					if (unbound != value || manager.getNotifyAttributeListenerOnUnchangedValue()) {
						context.fireContainerEvent("beforeSessionAttributeReplaced", listener);
						if (event == null) {
							event = new HttpSessionBindingEvent(getSession(), name, unbound);
						}
						listener.attributeReplaced(event);
						context.fireContainerEvent("afterSessionAttributeReplaced", listener);
					}
				} else {
					context.fireContainerEvent("beforeSessionAttributeAdded", listener);
					if (event == null) {
						event = new HttpSessionBindingEvent(getSession(), name, value);
					}
					listener.attributeAdded(event);
					context.fireContainerEvent("afterSessionAttributeAdded", listener);
				}
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				try {
					if (unbound != null) {
						if (unbound != value || manager.getNotifyAttributeListenerOnUnchangedValue()) {
							context.fireContainerEvent("afterSessionAttributeReplaced", listener);
						}
						manager.getContext().getLogger().error("ContainerEvent-SessionAttributeReplaced", t);
					} else {
						context.fireContainerEvent("afterSessionAttributeAdded", listener);
						manager.getContext().getLogger().error("ContainerEvent-SessionAttributeAdded", t);
					}
				} catch (Exception e) {
					// Ignore
				}
			}
		}
	}

	// -------------------------------------------------------------------------------------
	// HttpSession 保护方法
	// -------------------------------------------------------------------------------------
	/**
	 * @return 此会话的 <code>isValid</code> 标志，不进行任何过期检查。
	 */
	protected boolean isValidInternal() {
		return this.isValid;
	}

	/**
	 * 验证 Session 是否已失效，若失效则抛出 {@link IllegalStateException }
	 * @param throwMsg - 包含详细错误消息的字符串
	 */
	protected void isValidInternal(String throwMsg) {
		if ( !isValidInternal() ) throw new IllegalStateException(throwMsg);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * 这个实现只是检查可序列化的值。 子类可能使用其他不基于序列化的分发技术，并且可以覆盖此检查。
	 */
	@Override
	public boolean isAttributeDistributable(String name, Object value) {
		return value instanceof Serializable;
	}

	/**
	 * 从指定的对象输入流中读取此会话对象的序列化版本。
	 * <p>
	 * <b>实现说明</b>: 此方法不会恢复先前已关联 Manager的引用，必须显式设置。
	 *
	 * @param stream - 要读取的输入流
	 * @exception ClassNotFoundException - 如果指定了未知类
	 * @exception IOException - 如果发生输入/输出错误
	 */
	protected void doReadObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
		// 反序列化实例变量( Manager 除外)
		creationTime = ((Long) stream.readObject()).longValue();
		lastAccessedTime = ((Long) stream.readObject()).longValue();
		maxInactiveInterval = ((Integer) stream.readObject()).intValue();
		isNew = ((Boolean) stream.readObject()).booleanValue();
		isValid = ((Boolean) stream.readObject()).booleanValue();
		thisAccessedTime = ((Long) stream.readObject()).longValue();
		id = (String) stream.readObject();
		if (manager.getContext().getLogger().isDebugEnabled())
			manager.getContext().getLogger().debug("readObject() loading session " + id);

		authType = null;  // Transient（可能稍后设置）
		principal = null; // Transient（可能稍后设置）

		// 下一个读取的对象可以是属性数（整数）或会话的 authType 后跟一个 Principal 对象（不是整数）
		Object nextObject = stream.readObject();
		if (!(nextObject instanceof Integer)) {
			setAuthType((String) nextObject);
			try {
				setPrincipal((Principal) stream.readObject());
			} catch (ClassNotFoundException | ObjectStreamException e) {
				String msg = "Principal 不可序列化";
				if (manager.getContext().getLogger().isDebugEnabled()) {
					manager.getContext().getLogger().debug(msg, e);
				} else {
					manager.getContext().getLogger().warn(msg);
				}
				throw e;
			}
			// 之后，读取的下一个对象应该是属性数(Integer)
			nextObject = stream.readObject();
		}

		// 反序列化属性计数和属性值
		if (attributes == null)
			attributes = new ConcurrentHashMap<>();
		
		int n = ((Integer) nextObject).intValue();
		boolean isValidSave = isValid;
		isValid = true;
		for (int i = 0; i < n; i++) {
			String name = (String) stream.readObject(); // String 支持序列化，无需担心抛出异常
			final Object value;
			try {
				value = stream.readObject();
			} catch (WriteAbortedException wae) {
				if (wae.getCause() instanceof NotSerializableException) {
					String msg = name + "不支持序列化，by Session ID：" + id;
					if (manager.getContext().getLogger().isDebugEnabled()) {
						manager.getContext().getLogger().debug(msg, wae);
					} else {
						manager.getContext().getLogger().warn(msg);
					}
					// 跳过不可序列化的属性
					continue;
				}
				throw wae;
			}
			
			if (manager.getContext().getLogger().isDebugEnabled())
				manager.getContext().getLogger().debug("加载属性 name '" + name + "' ，value '" + value + "'");
			
			// 跳过不可序列化的属性
			if (exclude(name, value)) {
				continue;
			}
			
			// ConcurentHashMap不允许使用空键或空值
			if (null != value)
				attributes.put(name, value);
		}
		isValid = isValidSave;

		if (listeners == null) {
			listeners = new ArrayList<>();
		}

		if (notes == null) {
			notes = new Hashtable<>();
		}
	}

	/**
	 * 将此会话对象的序列化版本写入指定的对象输出流。
	 * <p>
	 * <b>实现说明</b>：所属 Manager 将不会存储在此会话的序列化表示中。调用ReadObject()后，必须显式设置关联的 Manager 。
	 * <p>
	 * <b>实现说明</b>：任何不可序列化的属性都将从会话解除绑定，如果它实现了HttpSessionBindingListener，则会执行相应的操作。
	 * 如果不需要任何此类属性，需确保关联的Manager的可分发属性设置为 true。
	 *
	 * @param stream - 要写入的输出流
	 * @exception IOException - 如果发生输入/输出错误
	 */
	protected void doWriteObject(ObjectOutputStream stream) throws IOException {
		// Write the scalar instance variables (except Manager)
		stream.writeObject(Long.valueOf(creationTime));
		stream.writeObject(Long.valueOf(lastAccessedTime));
		stream.writeObject(Integer.valueOf(maxInactiveInterval));
		stream.writeObject(Boolean.valueOf(isNew));
		stream.writeObject(Boolean.valueOf(isValid));
		stream.writeObject(Long.valueOf(thisAccessedTime));
		stream.writeObject(id);
		if (manager.getContext().getLogger().isDebugEnabled())
			manager.getContext().getLogger().debug("writeObject() 存储 Session：" + id);

		// 	收集身份验证信息(如果已配置)
		String sessionAuthType = null;
		Principal sessionPrincipal = null;
		if (getPersistAuthentication()) {
			sessionAuthType = getAuthType();
			sessionPrincipal = getPrincipal();
			if (!(sessionPrincipal instanceof Serializable)) {
				sessionPrincipal = null;
				manager.getContext().getLogger().warn("Principal 不支持序列化，by Session ID：" + id);
			}
		}

		// 写入身份验证类型（可能为空值）
		stream.writeObject(sessionAuthType);
		try {
			stream.writeObject(sessionPrincipal);
		} catch (NotSerializableException e) {
			manager.getContext().getLogger().warn("Principal 不支持序列化，by Session ID：" + id, e);
		}

		// 累积可序列化和不可序列化属性的名称
		String keys[] = keys();
		List<String> saveNames = new ArrayList<>();
		List<Object> saveValues = new ArrayList<>();
		for (String key : keys) {
			Object value = attributes.get(key);
			if (value == null) {
				continue;
			} else if (isAttributeDistributable(key, value) && !exclude(key, value)) {
				saveNames.add(key);
				saveValues.add(value);
			} else {
				removeAttributeInternal(key, true);
			}
		}

		// 序列化属性计数和可序列化属性
		int n = saveNames.size();
		stream.writeObject(Integer.valueOf(n));
		for (int i = 0; i < n; i++) {
			stream.writeObject(saveNames.get(i)); // 保存会话属性名
			try {
				stream.writeObject(saveValues.get(i)); // 保存会话属性值
				
				if (manager.getContext().getLogger().isDebugEnabled())
					manager.getContext().getLogger().debug("存储属性，name：[" + saveNames.get(i) + "]，value：[" + saveValues.get(i) + "]");
			} catch (NotSerializableException e) {
				manager.getContext().getLogger().warn("属性[" + saveNames.get(i) + "]不能序列化，by Session ID：" + id, e);
			}
		}
	}

	/**
	 * 是否要持久化认证信息？
	 *
	 * @return 如果要保留身份验证信息，则为 {@code true}；否则为{@code false}
	 */
	private boolean getPersistAuthentication() {
		if (manager instanceof ManagerBase) {
			return ((ManagerBase) manager).getPersistAuthentication();
		}
		return false;
	}

	/**
	 * 是否应该排除给定的会话属性？ 此实现检查：
	 * <ul>
	 * <li>{@link SessionConstants#excludedAttributeNames}</li>
	 * <li>{@link Manager#willAttributeDistribute(String, Object)}</li>
	 * </ul>
	 * 注意：此方法故意不检查 {@link #isAttributeDistributable(String, Object)} ，它保持独立以支持 {@link #setAttribute(String, Object, boolean)} 中所需的检查
	 *
	 * @param name - 属性名称
	 * @param value - 属性值
	 *
	 * @return 如果属性应该从分发中排除，则为 {@code true}，否则为 {@code false}
	 */
	protected boolean exclude(String name, Object value) {
		if (SessionConstants.excludedAttributeNames.contains(name)) {
			return true;
		}

		Manager manager = getManager();
		if (manager == null) {
			// 在集群中复制新会话时，Manager 可能为空，避免NPE。
			return false;
		}

		return !manager.willAttributeDistribute(name, value);
	}

	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
	/**
	 * 通知所有会话事件侦听器此会话已发生特定事件。默认实现使用调用线程同步执行此通知。
	 *
	 * @param type - 事件类型
	 * @param data - 事件数据
	 */
	public void fireSessionEvent(String type, Object data) {
		if (listeners.size() < 1) return;
		
		SessionEvent event = new SessionEvent(this, type, data);
		SessionListener list[] = new SessionListener[0];
		synchronized (listeners) {
			list = listeners.toArray(list);
		}

		for (SessionListener sessionListener : list) {
			sessionListener.sessionEvent(event);
		}
	}

	/**
	 * @return 所有当前定义的会话属性的名称作为字符串数组。 如果没有定义的属性，则返回一个长度为零的数组。
	 */
	protected String[] keys() {
		return attributes.keySet().toArray(EMPTY_ARRAY);
	}

	private static class PrivilegedNewSessionFacade implements PrivilegedAction<StandardSessionFacade> {
		private final HttpSession session;

		public PrivilegedNewSessionFacade(HttpSession session) {
			this.session = session;
		}

		@Override
		public StandardSessionFacade run() {
			return new StandardSessionFacade(session);
		}
	}

	@Override
	public HttpSessionContext getSessionContext() {
		return null;
	}
}
	
