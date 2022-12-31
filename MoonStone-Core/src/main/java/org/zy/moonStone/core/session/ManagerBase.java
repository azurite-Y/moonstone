package org.zy.moonStone.core.session;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.exceptions.TooManyActiveSessionsException;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Engine;
import org.zy.moonStone.core.interfaces.container.Lifecycle;
import org.zy.moonStone.core.session.interfaces.Manager;
import org.zy.moonStone.core.session.interfaces.Session;
import org.zy.moonStone.core.session.interfaces.SessionIdGenerator;
import org.zy.moonStone.core.util.ToStringUtil;
import org.zy.moonStone.core.util.http.FastHttpDateFormat;

/**
 * @dateTime 2022年8月8日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class ManagerBase extends LifecycleBase implements Manager {
	// -------------------------------------------------------------------------------------
	// 实例变量
	// -------------------------------------------------------------------------------------
    /** 与此 Manager 关联的 Context */
    private Context context;

    /** 此 Manager 实现的描述性名称（用于日志记录） */
    private static final String name = "ManagerBase";

    /**
     * 生成会话标识符时要使用的安全随机数生成器类的 Java 类名。 
     * 随机数生成器类必须是自播种并具有零参数构造函数。
     * 如果未指定，将生成 {@link java.security.SecureRandom} 的实例。
     */
    protected String secureRandomClass = null;

    /**
     * 用于创建用于生成会话 ID 的 {@link java.security.SecureRandom} 实例的算法名称。如果未指定算法，则使用 SHA1PRNG。 
     * 要使用平台默认值（可能是 SHA1PRNG），请指定空字符串。
     * 如果指定了无效算法和/或提供程序，则将使用默认值创建 SecureRandom 实例。 如果失败，将使用平台默认值创建 SecureRandom 实例。
     */
    protected String secureRandomAlgorithm = "SHA1PRNG";

    /**
     * 用于创建用于生成会话id的 {@link java.security.SecureRandom} 实例的提供程序的名称。如果不指定SHA1PRNG算法，则使用缺省值。
     * 如果指定了一个无效的算法和/或提供程序，SecureRandom实例将使用默认值创建。
     * 如果失败，将使用平台默认值创建SecureRandominstances。
     */
    protected String secureRandomProvider = null;

    /** 会话ID生成器 */
    protected SessionIdGenerator sessionIdGenerator = null;
    /** 会话ID生成器 Class对象 */
    protected Class<? extends SessionIdGenerator> sessionIdGeneratorClass = null;

    /** 过期会话存活的最长时间(以秒为单位) */
    protected volatile int sessionMaxAliveTime;
    /** sessionMaxAliveTime 更新锁 */
    private final Object sessionMaxAliveTimeUpdateLock = new Object();

    /** 计时统计缓存大小 */
    protected static final int TIMING_STATS_CACHE_SIZE = 100;

    
    protected final Deque<SessionTiming> sessionCreationTiming = new LinkedList<>();

    
    protected final Deque<SessionTiming> sessionExpirationTiming = new LinkedList<>();

    /** 已过期的会话数 */
    protected final AtomicLong expiredSessions = new AtomicLong(0);

    /** 此 Manager 的当前活动会话集，以会话标识符为键 [ sessionId: Session ]。 */
    protected Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 此 Manager 创建的会话数 */
    protected long sessionCounter=0;

    /** 最大活跃会话数 */
    protected volatile int maxActive=0;

    /** maxActive 更新锁 */
    private final Object maxActiveUpdateLock = new Object();

    /** 允许的最大活跃会话数，或 -1 表示无限制 */
    protected int maxActiveSessions = -1;

    /** 由于 maxActiveSessions 而失败的会话创建数 */
    protected int rejectedSessions = 0;

    /** 重复会话 id 的数量，理论上不应该 >0 */
    protected volatile int duplicates=0;

    /** 会话到期期间的处理时间 */
    protected long processingTime = 0;

    /** 后台处理的迭代次数 */
    private int count = 0;

    /** 会话到期的频率，以及相关的 Manager 操作。Manager 操作将针对指定数量的后台进程调用执行一次（即，数量越低，检查发生的频率越高）。*/
    protected int processExpiresFrequency = 6;

    /** 此组件的属性更改支持 */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);
    
    /** 会话属性名称样式，在会话被持久保存和复制之前，应该忽略的会话属性 */
    private Pattern sessionAttributeNamePattern;

    /** 会话属性值类名称样式，在会话被持久保存和复制之前，应该忽略的会话属性值 */
    private Pattern sessionAttributeValueClassNamePattern;

    /** warn会话属性过滤失败 */
    private boolean warnOnSessionAttributeFilterFailure;

    /** 在未更改的值上通知绑定侦听器 */
    private boolean notifyBindingListenerOnUnchangedValue;

    /** 在未更改的值上通知属性侦听器 */
    private boolean notifyAttributeListenerOnUnchangedValue = true;

    /** 确定此管理器管理的会话是否应保留（序列化）身份验证信息。 */
    private boolean persistAuthentication = false;
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    public ManagerBase() {
        if (Globals.IS_SECURITY_ENABLED) {
            // 默认分发/持久性工作所需的最小集加上字符串加上可序列化主体和字符串[]（身份验证持久性所需）
            setSessionAttributeValueClassNameFilter(
                    "java\\.lang\\.(?:Boolean|Integer|Long|Number|String)"
                    + "|org\\.apache\\.catalina\\.realm\\.GenericPrincipal\\$SerializablePrincipal"
                    + "|\\[Ljava.lang.String;");
            setWarnOnSessionAttributeFilterFailure(true);
        }
    }
    
    // -------------------------------------------------------------------------------------
    // getter、setter
    // -------------------------------------------------------------------------------------
    @Override
    public boolean getNotifyAttributeListenerOnUnchangedValue() {
        return notifyAttributeListenerOnUnchangedValue;
    }

    @Override
    public void setNotifyAttributeListenerOnUnchangedValue(boolean notifyAttributeListenerOnUnchangedValue) {
        this.notifyAttributeListenerOnUnchangedValue = notifyAttributeListenerOnUnchangedValue;
    }

    @Override
    public boolean getNotifyBindingListenerOnUnchangedValue() {
        return notifyBindingListenerOnUnchangedValue;
    }

    @Override
    public void setNotifyBindingListenerOnUnchangedValue(boolean notifyBindingListenerOnUnchangedValue) {
        this.notifyBindingListenerOnUnchangedValue = notifyBindingListenerOnUnchangedValue;
    }

    /**
     * 根据属性名获取会话属性过滤的正则表达式。正则表达式是固定的，所以它必须匹配整个名称
     *
     * @return 当前用于过滤属性名称的正则表达式。Null表示不应用过滤器。如果指定的是空字符串，那么没有名字会匹配过滤器，所有的属性都会被阻塞。
     */
    public String getSessionAttributeNameFilter() {
        if (sessionAttributeNamePattern == null) {
            return null;
        }
        return sessionAttributeNamePattern.toString();
    }

    /**
     * 设置正则表达式用于根据属性名称过滤会话属性。正则表达式是固定的，所以它必须匹配整个名称。
     *
     * @param sessionAttributeNameFilter - 用于根据属性名称过滤会话属性的正则表达式。
     * 如果不需要过滤，则使用null。如果指定了一个空字符串，那么名称将匹配过滤器，所有属性将被阻塞。
     *
     * @throws PatternSyntaxException - 如果表达式无效
     */
    public void setSessionAttributeNameFilter(String sessionAttributeNameFilter) throws PatternSyntaxException {
        if (sessionAttributeNameFilter == null || sessionAttributeNameFilter.length() == 0) {
            sessionAttributeNamePattern = null;
        } else {
            sessionAttributeNamePattern = Pattern.compile(sessionAttributeNameFilter);
        }
    }

    /**
     * 提供 {@link #getSessionAttributeNameFilter()} 作为预编译的正则表达式模式
     *
     * @return 在会话被持久保存和复制之前，应该忽略的会话属性。 {@code null} 表示没有应用过滤器。
     */
    protected Pattern getSessionAttributeNamePattern() {
        return sessionAttributeNamePattern;
    }

    /**
     * 根据值的实现类获取用于过滤会话属性的正则表达式。正则表达式是固定的，并且必须与完全限定的类名匹配
     *
     * @return 当前用于过滤类名的正则表达式。 {@code null} 表示未应用过滤器。 如果指定了空字符串，则没有名称与过滤器匹配，所有属性都将被阻止。
     */
    public String getSessionAttributeValueClassNameFilter() {
        if (sessionAttributeValueClassNamePattern == null) {
            return null;
        }
        return sessionAttributeValueClassNamePattern.toString();
    }

    /**
     * 提供 {@link #getSessionAttributeValueClassNameFilter()} 作为预编译的正则表达式模式。
     *
     * @return 用于根据值的实现类名称过滤会话属性的预编译模式。 null 表示未应用过滤器。
     */
    protected Pattern getSessionAttributeValueClassNamePattern() {
        return sessionAttributeValueClassNamePattern;
    }

    /**
     * 设置正则表达式以用于过滤用于会话属性的类。 正则表达式是固定的，并且必须与完全限定的类名匹配。
     *
     * @param sessionAttributeValueClassNameFilter - 用于根据类名过滤会话属性的正则表达式。
     * 如果不需要过滤，请使用 {@code null }。 如果指定了空字符串，则没有名称与过滤器匹配，并且所有属性都将被阻止。
     *
     * @throws PatternSyntaxException - 如果表达式无效
     */
    public void setSessionAttributeValueClassNameFilter(String sessionAttributeValueClassNameFilter) throws PatternSyntaxException {
        if (sessionAttributeValueClassNameFilter == null || sessionAttributeValueClassNameFilter.length() == 0) {
            sessionAttributeValueClassNamePattern = null;
        } else {
            sessionAttributeValueClassNamePattern = Pattern.compile(sessionAttributeValueClassNameFilter);
        }
    }

    /**
     * 如果会话属性未持久化/复制/恢复，是否应生成警告级别日志消息。
     *
     * @return 如果应生成警告级别日志消息，则为 {@code true}
     */
    public boolean getWarnOnSessionAttributeFilterFailure() {
        return warnOnSessionAttributeFilterFailure;
    }

    /**
     * 配置如果会话属性未持久化/复制/恢复，是否应生成警告级别日志消息。
     *
     * @param warnOnSessionAttributeFilterFailure - 如果应生成警告级别消息，则为{@code true}
     */
    public void setWarnOnSessionAttributeFilterFailure(boolean warnOnSessionAttributeFilterFailure) {
        this.warnOnSessionAttributeFilterFailure = warnOnSessionAttributeFilterFailure;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        if (this.context == context) {
            return;
        }
        if (!getState().equals(LifecycleState.NEW)) {
            throw new IllegalStateException("关联的 Context 生命周期不是初始态");
        }
        this.context = context;
    }

    /**
     * @return 实现类的名称
     */
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public SessionIdGenerator getSessionIdGenerator() {
        if (sessionIdGenerator != null) {
            return sessionIdGenerator;
        } else if (sessionIdGeneratorClass != null) {
            try {
                sessionIdGenerator = sessionIdGeneratorClass.getConstructor().newInstance();
                return sessionIdGenerator;
            } catch(ReflectiveOperationException ex) {
                // Ignore
            }
        }
        return null;
    }

    @Override
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
        sessionIdGeneratorClass = sessionIdGenerator.getClass();
    }

    /**
     * @return 此 Manager 实现的描述性短名称。
     */
    public String getName() {
        return name;
    }

    /**
     * @return 安全随机数生成器类名
     */
    public String getSecureRandomClass() {
        return this.secureRandomClass;
    }

    /**
     * 设置安全随机数生成器类名
     *
     * @param secureRandomClass - 新的安全随机数生成器类名
     */
    public void setSecureRandomClass(String secureRandomClass) {
        this.secureRandomClass = secureRandomClass;
    }

    /**
     * @return 安全随机数生成器算法名称
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }

    /**
     * 设置安全随机数生成器算法名称
     *
     * @param secureRandomAlgorithm - 新的安全随机数生成器算法名称
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    /**
     * @return 安全随机数生成器提供程序名称。
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }

    /**
     * 设置安全随机数生成器提供程序名称
     *
     * @param secureRandomProvider - 新的安全随机数生成器提供者名称
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }

    @Override
    public int getRejectedSessions() {
        return rejectedSessions;
    }

    @Override
    public long getExpiredSessions() {
        return expiredSessions.get();
    }

    @Override
    public void setExpiredSessions(long expiredSessions) {
        this.expiredSessions.set(expiredSessions);
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * @return Manager 检查会话到期的频率
     */
    public int getProcessExpiresFrequency() {
        return this.processExpiresFrequency;
    }

    /**
     * 设置 Manager 检查会话到期的频率
     *
     * @param processExpiresFrequency - 新的检查会话到期的频率
     */
    public void setProcessExpiresFrequency(int processExpiresFrequency) {
        if (processExpiresFrequency <= 0) {
            return;
        }
        this.processExpiresFrequency = processExpiresFrequency;
    }

    /**
     * 返回此管理器管理的会话是否应保留身份验证信息。
     *
     * @return {@code true}, 由该管理器管理的会话将持久化认证信息; {@code false} 反之
     */
    public boolean getPersistAuthentication() {
        return this.persistAuthentication;
    }

    /**
     * 设置此管理器管理的会话是否应保留身份验证信息
     *
     * @param persistAuthentication - 如果为 {@code true} ，则由该管理器管理的会话将保留身份验证信息
     */
    public void setPersistAuthentication(boolean persistAuthentication) {
        this.persistAuthentication = persistAuthentication;
    }
    
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    @Override
    public void backgroundProcess() {
        count = (count + 1) % processExpiresFrequency;
        if (count == 0) processExpires();
    }
    
    /**
     * 使所有已过期的会话无效.
     */
    public void processExpires() {
        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0 ;

        boolean log = false;
        if (logger.isDebugEnabled() && sessions.length > 0) {
        	log = true;
        	logger.debug(getName() + " 在 [" + FastHttpDateFormat.formatDayTime( timeNow ) + "] 会话计数 '" + sessions.length +"' 处开始过期会话." );
        }
        
        for (Session session : sessions) {
            if (session != null && !session.isValid()) { // 调用 isValid方法会自动处理失效Session
                expireHere++;
            }
        }
        long timeEnd = System.currentTimeMillis();
        if(log && logger.isDebugEnabled()) {
        	logger.debug(getName() + " 结束过期会话, 处理时间: '" + (timeEnd - timeNow)/1000 + "s'，过期会话: " + expireHere);
        }
        
        processingTime += ( timeEnd - timeNow );
    }
    
    @Override
    protected void initInternal() throws LifecycleException {
        if (context == null) {
            throw new LifecycleException("关联的 Context 不能为null");
        }
    }

    @Override
    protected void startInternal() throws LifecycleException {
        // 通过填充空值确保时间统计缓存的大小正确
        while (sessionCreationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionCreationTiming.add(null);
        }
        while (sessionExpirationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionExpirationTiming.add(null);
        }

        /* 如果未明确配置，则创建 StandardSessionIdGenerator */
        SessionIdGenerator sessionIdGenerator = getSessionIdGenerator();
        if (sessionIdGenerator == null) {
            sessionIdGenerator = new StandardSessionIdGenerator();
            setSessionIdGenerator(sessionIdGenerator);
        }

        sessionIdGenerator.setJvmRoute(getJvmRoute());
        if (sessionIdGenerator instanceof SessionIdGeneratorBase) {
            SessionIdGeneratorBase sig = (SessionIdGeneratorBase)sessionIdGenerator;
            sig.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
            sig.setSecureRandomClass(getSecureRandomClass());
            sig.setSecureRandomProvider(getSecureRandomProvider());
        }

        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).start();
        } else {
            // 强制初始化随机数生成器
            if (logger.isDebugEnabled()) logger.debug("强制随机数初始化开始");
            
            sessionIdGenerator.generateSessionId();
            
            if (logger.isDebugEnabled()) logger.debug("强制随机数初始化完成");
        }
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).stop();
        }
    }

    @Override
    public void add(Session session) {
        sessions.put(session.getIdInternal(), session);
        int size = getActiveSessions();
        if( size > maxActive ) { // 更新最大会话活跃数
            synchronized(maxActiveUpdateLock) {
                if( size > maxActive ) {
                    maxActive = size;
                }
            }
        }
    }
    
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
    
    @Override
    public Session createSession(String sessionId) {
        if ((maxActiveSessions >= 0) && (getActiveSessions() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new TooManyActiveSessionsException("创建 Session 失败，已达到最大活跃会话数", maxActiveSessions);
        }

        // 回收或创建 Session 实例
        Session session = createEmptySession();

        // 初始化新会话的属性并返回
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        // 设置会话失效时间
        session.setMaxInactiveInterval(getContext().getSessionTimeout() * 60);
        String id = sessionId;
        if (id == null) {
            id = generateSessionId();
        }
        session.setId(id);
        sessionCounter++;

        SessionTiming timing = new SessionTiming(session.getCreationTime(), 0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.add(timing);
            sessionCreationTiming.poll();
        }
        return session;
    }

    @Override
    public Session createEmptySession() {
        return getNewSession();
    }

    @Override
    public Session findSession(String id) throws IOException {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }

    @Override
    public Session[] findSessions() {
        return sessions.values().toArray(new Session[0]);
    }

    @Override
    public void remove(Session session) {
        remove(session, false);
    }

    @Override
    public void remove(Session session, boolean update) {
        // 如果会话已过期 - 而不是因为它被持久化而被从管理器中删除 - 更新过期的统计信息
        if (update) {
            long timeNow = System.currentTimeMillis();
            int timeAlive = (int) (timeNow - session.getCreationTimeInternal())/1000;
            // 记录过期会话存活的最长时间
            updateSessionMaxAliveTime(timeAlive);
       
            // 更新过期会话计数
            expiredSessions.incrementAndGet();
            
            SessionTiming timing = new SessionTiming(timeNow, timeAlive);
            synchronized (sessionExpirationTiming) {
                sessionExpirationTiming.add(timing);
                sessionExpirationTiming.poll();
            }
        }

        if (session.getIdInternal() != null) {
            sessions.remove(session.getIdInternal());
        }
    }
    
    @Override
    public void changeSessionId(Session session) {
        rotateSessionId(session);
    }

    @Override
    public String rotateSessionId(Session session) {
        String newId = generateSessionId();
        changeSessionId(session, newId, true, true);
        return newId;
    }

    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true, true);
    }

    protected void changeSessionId(Session session, String newId, boolean notifySessionListeners, boolean notifyContainerListeners) {
        String oldId = session.getIdInternal();
        session.setId(newId, false);
        session.tellChangedSessionId(newId, oldId, notifySessionListeners, notifyContainerListeners);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 如果出现以下情况，此实现将从分发中排除会话属性:
     * <ul>
     * 		<li>属性名称与 {@link #getSessionAttributeNameFilter()} 匹配 </li>
     * </ul>
     */
    @Override
    public boolean willAttributeDistribute(String name, Object value) {
        Pattern sessionAttributeNamePattern = getSessionAttributeNamePattern();
        if (sessionAttributeNamePattern != null) {
            if (!sessionAttributeNamePattern.matcher(name).matches()) {
                if (getWarnOnSessionAttributeFilterFailure() || logger.isDebugEnabled()) {
                    String msg = "属性名称与 sessionAttributeNamePattern 匹配，by name：" + name;
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        logger.warn(msg);
                    } else {
                        logger.debug(msg);
                    }
                }
                return false;
            }
        }

        Pattern sessionAttributeValueClassNamePattern = getSessionAttributeValueClassNamePattern();
        if (value != null && sessionAttributeValueClassNamePattern != null) {
            if (!sessionAttributeValueClassNamePattern.matcher( value.getClass().getName()).matches() ) {
                if (getWarnOnSessionAttributeFilterFailure() || logger.isDebugEnabled()) {
                	String msg = "属性名称与 sessionAttributeValueClassNamePattern 匹配，by name：" + name;
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        logger.warn(msg);
                    } else {
                        logger.debug(msg);
                    }
                }
                return false;
            }
        }

        return true;
    }
    
    /**
     * @return 从关联的上下文递归获得的父类容器 Engine
     */
    public Engine getEngine() {
        Engine e = null;
        // 从关联的上下文递归获得其父类容器 Engine
        for (Container c = getContext(); e == null && c != null ; c = c.getParent()) {
            if (c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e;
    }

    /**
     * @return 从关联的上下文递归获得的父类容器 Engine 中获得 JvmRoute
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }
    
    // -------------------------------------------------------------------------------------
    // 保护方法
    // -------------------------------------------------------------------------------------
    /**
     * @return 关联此管理器的新 Session
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }

    /**
     * 生成并返回新的会话标识符
     * @return 一个新的未重复的 session id
     */
    protected String generateSessionId() {
        String result = null;
        do {
            if (result != null) {
                // 不是线程安全的，但如果多个增量中的一个丢失了，那也不是什么大问题，因为有任何重复是一个大得多的问题
                duplicates++;
            }
            result = sessionIdGenerator.generateSessionId();
        } while (sessions.containsKey(result));
        return result;
    }
    
    // -------------------------------------------------------------------------------------
    // 包方法
    // -------------------------------------------------------------------------------------
    @Override
    public void setSessionCounter(long sessionCounter) {
        this.sessionCounter = sessionCounter;
    }

    @Override
    public long getSessionCounter() {
        return sessionCounter;
    }

    /**
     * 随机源生成的重复会话 ID 的数量。任何大于 0 的都表示有问题。
     *
     * @return 重复的次数
     */
    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    @Override
    public int getActiveSessions() {
        return sessions.size();
    }

    @Override
    public int getMaxActive() {
        return maxActive;
    }

    @Override
    public void setMaxActive(int maxActive) {
        synchronized (maxActiveUpdateLock) {
            this.maxActive = maxActive;
        }
    }

    /**
     * @return 允许的最大活动会话数，或-1表示无限大。
     */
    public int getMaxActiveSessions() {
        return this.maxActiveSessions;
    }

    /**
     * 设置允许的活动会话的最大数量，或-1表示无限制。
     *
     * @param max - 新的最大会话数
     */
    public void setMaxActiveSessions(int max) {
        this.maxActiveSessions = max;
    }

    @Override
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }

    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        synchronized (sessionMaxAliveTimeUpdateLock) {
            this.sessionMaxAliveTime = sessionMaxAliveTime;
        }
    }

    /**
     * 如果候选值大于当前值，更新 sessionMaxAliveTime 属性。
     *
     * @param sessionAliveTime - 候选值(单位为秒)
     */
    public void updateSessionMaxAliveTime(int sessionAliveTime) {
        if (sessionAliveTime > this.sessionMaxAliveTime) {
            synchronized (sessionMaxAliveTimeUpdateLock) {
                if (sessionAliveTime > this.sessionMaxAliveTime) {
                    this.sessionMaxAliveTime = sessionAliveTime;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 基于最近100个即将过期的会话。如果过期的会话少于100个，则使用所有可用的数据。
     */
    @Override
    public int getSessionAverageAliveTime() {
        // 复制当前的数据
        List<SessionTiming> copy;
        synchronized (sessionExpirationTiming) {
            copy = new ArrayList<>(sessionExpirationTiming);
        }

        // Init
        int counter = 0;
        int result = 0;

        // 计算平均
        for (SessionTiming timing : copy) {
            if (timing != null) {
                int timeAlive = timing.getDuration();
                counter++;
                // 小心不要溢出来——可能没必要
                result = (result * ((counter - 1)/counter)) + (timeAlive/counter);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}<p>
     * 基于之前创建的100个会话的创建时间。如果创建的会话少于100个，则使用所有可用数据。
     */
    @Override
    public int getSessionCreateRate() {
        // 复制当前的数据
        List<SessionTiming> copy;
        synchronized (sessionCreationTiming) {
            copy = new ArrayList<>(sessionCreationTiming);
        }

        return calculateRate(copy);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 基于之前创建的100个会话的创建时间。如果创建的会话少于100个，则使用所有可用数据。
     *
     * @return 会话过期的当前速率(以每分钟会话数为单位)
     */
    @Override
    public int getSessionExpireRate() {
        // 复制当前的数据
        List<SessionTiming> copy;
        synchronized (sessionExpirationTiming) {
            copy = new ArrayList<>(sessionExpirationTiming);
        }

        return calculateRate(copy);
    }

    private static int calculateRate(List<SessionTiming> sessionTiming) {
        // Init
        long now = System.currentTimeMillis();
        long oldest = now;
        int counter = 0;
        int result = 0;

        // 计算速率
        for (SessionTiming timing : sessionTiming) {
            if (timing != null) {
                counter++;
                if (timing.getTimestamp() < oldest) {
                    oldest = timing.getTimestamp();
                }
            }
        }
        if (counter > 0) {
            if (oldest < now) {
                result = (1000*60*counter)/(int) (now - oldest);
            } else {
                // 总比零报告要好
                result = Integer.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * 用于调试
     *
     * @return 以空格分隔的当前活动的所有会话id列表
     */
    public String listSessionIds() {
        StringBuilder sb = new StringBuilder();
        for (String s : sessions.keySet()) {
            sb.append(s).append(" ");
        }
        return sb.toString();
    }

    /**
     * 用于调试
     *
     * @param sessionId - 感兴趣的会话ID
     * @param key - 要获取的属性的键
     * @return 如果找到指定会话的属性值，则为空
     */
    public String getSessionAttribute( String sessionId, String key ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
                logger.info("未找到 session id，by {}", sessionId);
            }
            return null;
        }
        Object o=s.getSession().getAttribute(key);
        if( o==null ) return null;
        return o.toString();
    }

    /**
     * 返回给定会话id的会话信息。
     * <p>
     * 会话信息被组织为HashMap，将会话属性名称的String 值作为 Map的key。
     *
     * @param sessionId - Session id
     * @return HashMap将会话属性名称映射到它们值的字符串表示形式，如果不存在具有指定id的会话，或如果会话没有任何属性，则为null
     */
    public HashMap<String, String> getSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return null;
        }

        Enumeration<String> ee = s.getSession().getAttributeNames();
        if (ee == null || !ee.hasMoreElements()) {
            return null;
        }

        HashMap<String, String> map = new HashMap<>();
        while (ee.hasMoreElements()) {
            String attrName = ee.nextElement();
            map.put(attrName, getSessionAttribute(sessionId, attrName));
        }

        return map;
    }

    /**
     * 过期会话
     * 
     * @param sessionId
     */
    public void expireSession( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return;
        }
        s.expire();
    }

    /**
     * 客户端最后一次发送与此会话相关的请求的时间。您的应用程序所采取的操作，例如获取或设置与会话相关的值，不会影响访问时间。每当请求开始时，这个函数就会更新.
     * 
     * @param sessionId
     * @return
     */
    public long getThisAccessedTimestamp( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return -1;
        }
        return s.getThisAccessedTime();
    }

    /**
     * 客户端最后一次发送与此会话相关的请求的时间。您的应用程序所采取的操作，例如获取或设置与会话相关的值，不会影响访问时间。每当请求开始时，这个函数就会更新.
     * 
     * @param sessionId
     * @return
     */
    public String getThisAccessedTime( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return "";
        }
        return new Date(s.getThisAccessedTime()).toString();
    }

    /**
     * 客户端最后一次发送与此会话相关的请求的时间。您的应用程序所采取的操作，例如获取或设置与会话相关的值，不会影响访问时间。每当一个请求完成时，它就会更新.
     * 
     * @param sessionId
     * @return
     */
    public long getLastAccessedTimestamp( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return -1;
        }
        return s.getLastAccessedTime();
    }

    public String getLastAccessedTime( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }

    public String getCreationTime( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return "";
        }
        return new Date(s.getCreationTime()).toString();
    }

    public long getCreationTimestamp( String sessionId ) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (logger.isInfoEnabled()) {
            	logger.info("未找到 session id，by {}", sessionId);
            }
            return -1;
        }
        return s.getCreationTime();
    }

	@Override
	protected void destroyInternal() throws LifecycleException {}

    @Override
    public String toString() {
        return ToStringUtil.toString(this, context);
    }
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    protected static final class SessionTiming {
    	/** 时间戳 */
        private final long timestamp;
        /** 持续时间 */
        private final int duration;

        public SessionTiming(long timestamp, int duration) {
            this.timestamp = timestamp;
            this.duration = duration;
        }

        /**
         * @return 与这条计时信息相关的时间戳，以毫秒为单位。
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return 与这条计时信息相关的持续时间（以秒为单位）。
         */
        public int getDuration() {
            return duration;
        }
    }
}
