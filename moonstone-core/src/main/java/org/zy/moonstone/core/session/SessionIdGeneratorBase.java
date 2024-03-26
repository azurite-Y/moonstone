package org.zy.moonstone.core.session;

import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.session.interfaces.SessionIdGenerator;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @dateTime 2022年8月9日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class SessionIdGeneratorBase extends LifecycleBase implements SessionIdGenerator {
	
	/**
     * 创建会话标识符时使用的随机数生成器对象队列。如果在需要随机数生成器时队列为空，则会创建一个新的随机数生成器对象。
     * 之所以这样设计，是因为随机数生成器使用了同步来使其线程安全，而同步使单个对象的使用变慢
     */
    private final Queue<SecureRandom> randoms = new ConcurrentLinkedQueue<>();

    /** 随机数生成器全限定类名 */
    private String secureRandomClass = null;

    /** 随机数生成算法 */
    private String secureRandomAlgorithm = "SHA1PRNG";

    /** 随机数生成器提供程序全限定类名 */
    private String secureRandomProvider = null;

    /** 在集群中的节点标识符。默认为空字符串。 */
    private String jvmRoute = "";

    /** 会话ID的字节数。默认为16 */
    private int sessionIdLength = 16;
	
    /**
     * 获取用于生成会话id的 {@link SecureRandom} 实现的类名
     *
     * @return 完全限定类名。{@code null} 表示使用JRE提供的 {@link SecureRandom} 实现
     */
    public String getSecureRandomClass() {
        return secureRandomClass;
    }

    /**
     * 指定要使用的非默认 {@link SecureRandom} 实现。实现必须是自定型的，并且具有零参数构造函数。如果未指定，将生成 {@link SecureRandom} 的实例。
     * 
     * @param secureRandomClass - 完全限定的类名
     */
    public void setSecureRandomClass(String secureRandomClass) {
        this.secureRandomClass = secureRandomClass;
    }

    /**
     * 获取用于创建生成新会话ID的 {@link SecureRandom} 实例的算法的名称。
     *
     * @return 算法的名称。 {@code null} 或空字符串表示将使用平台默认设置
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }

    /**
     * 指定用于创建用于生成会话 ID 的 {@link SecureRandom} 实例的非默认算法。 如果没有指定算法，则使用 SHA1PRNG。 
     * 要使用平台默认值（可能是 SHA1PRNG），请指定 {@code null}  或空字符串。 
     * 如果指定了无效算法和/或提供程序，则将使用此 {@link SessionIdGenerator} 实现的默认值创建 SecureRandom 实例。 
     * 如果失败，将使用平台默认值创建 {@link SecureRandom} 实例。
     *
     * @param secureRandomAlgorithm - 算法名称
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    /**
     * 获取用于创建生成新会话 ID 的  {@link SecureRandom} 实例的提供程序的名称。
     *
     * @return 提供者的名称。 {@code null} 或空字符串表示将使用平台默认值
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }

    /**
     * 指定用于创建 {@link SecureRandom} 实例的非默认提供程序，这些实例用于生成会话 ID。 如果未指定提供程序，则使用平台默认值。 
     * 要使用平台默认值，请指定 null 或空字符串。 如果指定了无效的算法和/或提供程序，则将使用此 {@link SessionIdGenerator} 实现的默认值创建 SecureRandom 实例。 
     * 如果失败，将使用平台默认值创建 {@link SecureRandom} 实例。
     *
     * @param secureRandomProvider - 提供者的名称
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }

    /**
     * 返回与该节点关联的节点标识符，该标识符将包含在生成的会话 ID 中。
     */
    @Override
    public String getJvmRoute() {
        return jvmRoute;
    }

    /**
     * 指定与此节点关联的节点标识符，该标识符将包含在生成的会话 ID 中
     *
     * @param jvmRoute  - 节点标识符
     */
    @Override
    public void setJvmRoute(String jvmRoute) {
        this.jvmRoute = jvmRoute;
    }

    /**
     * 返回会话 ID 的字节数
     */
    @Override
    public int getSessionIdLength() {
        return sessionIdLength;
    }

    /**
     * 指定会话 ID 的字节数
     *
     * @param sessionIdLength - 字节数
     */
    @Override
    public void setSessionIdLength(int sessionIdLength) {
        this.sessionIdLength = sessionIdLength;
    }

    /**
     * 生成并返回一个新的会话标识符
     */
    @Override
    public String generateSessionId() {
        return generateSessionId(jvmRoute);
    }

    protected void getRandomBytes(byte bytes[]) {
        SecureRandom random = randoms.poll();
        if (random == null) {
            random = createSecureRandom();
        }
        random.nextBytes(bytes);
        randoms.add(random);
    }


    /**
     * 创建一个新的随机数生成器实例，我们应该使用它来生成会话标识符。
     */
    private SecureRandom createSecureRandom() {
        SecureRandom result = null;

        long t1 = System.currentTimeMillis();
        if (secureRandomClass != null) {
            try {
            	// 构造新的随机数生成器并为其设定种子
                Class<?> clazz = Class.forName(secureRandomClass);
                result = (SecureRandom) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                logger.error("随机数生成器 实例化失败，by Class：" + secureRandomClass, e);
            }
        }

        boolean error = false;
        if (result == null) {
            // 没有安全随机类或创建失败。使用 SecureRandom
            try {
                if (secureRandomProvider != null && secureRandomProvider.length() > 0) { // 有提供程序则优先从提供程序获得实例
                    result = SecureRandom.getInstance(secureRandomAlgorithm, secureRandomProvider);
                } else if (secureRandomAlgorithm != null && secureRandomAlgorithm.length() > 0) {
                    result = SecureRandom.getInstance(secureRandomAlgorithm);
                }
            } catch (NoSuchAlgorithmException e) {
                error = true;
                logger.error("指定的加密算法在当前环境中不可用，by algorithm：" + secureRandomAlgorithm, e);
            } catch (NoSuchProviderException e) {
                error = true;
                logger.error("指定的安全提供程序在当前环境中不可用，by algorithm：" + secureRandomProvider, e);
            }
        }

        if (result == null && error) {
            // 无效的 provider / algorithm
            try {
                result = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
            	logger.error("指定的加密算法在当前环境中不可用，by algorithm：" + secureRandomAlgorithm, e);
            }
        }

        if (result == null) {
            // 使用平台默认值
            result = new SecureRandom();
        }

        // 强制为其设定种子
        result.nextInt();

        long t2 = System.currentTimeMillis();
        if ((t2 - t1) > 100) {
            logger.warn("创建随机数生成器耗时：" + Long.valueOf(t2 - t1) + "，使用算法：result.getAlgorithm()");
        }
        return result;
    }


    @Override
    protected void initInternal() throws LifecycleException {}


    @Override
    protected void startInternal() throws LifecycleException {
        // 确保已初始化SecureRandom
        generateSessionId();
        setState(LifecycleState.STARTING);
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        randoms.clear();
    }

    @Override
    protected void destroyInternal() throws LifecycleException {}


}
