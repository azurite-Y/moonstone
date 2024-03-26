package org.zy.moonstone.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.util.ExceptionUtils;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @dateTime 2022年6月29日;
 * @author zy(azurite-Y);
 * @description 该实用程序类将 Subject 与当前的  {@linkplain AccessControlContext } 相关联。 <br/>
 * 当使用 {@linkplain SecurityManager  } 时，容器将始终将被调用线程与仅包含请求的 Servlet/Filter 的主体的 {@linkplain AccessControlContext } 相关联。此类使用反射来调用方法。
 */
public final class SecurityUtil {
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtil.class);

	private static final boolean packageDefinitionEnabled = (System.getProperty("package.definition") == null && System.getProperty("package.access")  == null) ? false : true;
	
    /**
     * 缓存为其创建方法的每个类
     */
    private static final Map<Class<?>,Method[]> classCache = new ConcurrentHashMap<>();
	
    /*
     *  注意索引重叠
     *  
     *  A Servlet uses "init", "service", "event", "destroy".
     *  A Filter uses "doFilter", "doFilterEvent", "destroy".
     */
    private static final int INIT= 0;
    
    private static final int SERVICE = 1;
    private static final int DOFILTER = 1;
    
    private static final int EVENT = 2;
    private static final int DOFILTEREVENT = 2;
    
    private static final int DESTROY = 3;

    private static final String INIT_METHOD = "init";
    
    private static final String DOFILTER_METHOD = "doFilter";
    private static final String SERVICE_METHOD = "service";
    
    private static final String EVENT_METHOD = "event";
    private static final String DOFILTEREVENT_METHOD = "doFilterEvent";
    
    private static final String DESTROY_METHOD = "destroy";
    
    
	/**
	 * 仅当启用了安全性并且启用了包保护机制时才返回 <code>SecurityManager</code>
     * @return 如果启用了包级别保护，则为 <code>true</code>
     */
    public static boolean isPackageProtectionEnabled(){
        if (packageDefinitionEnabled && Globals.IS_SECURITY_ENABLED){
            return true;
        }
        return false;
    }
    
    
    /**
     * 作为特定<code>Subject</code>执行工作。在这里，工作将被授予空<code>Subject</code>。
     *
     * @param methodName - 应用安全限制的方法
     * @param targetObject - 该方法将被调用的过滤器
     * @param targetParameterTypes - 用于实例化方法对象的类数组
     * @param targetParameterValues - 对象数组包含运行时参数实例。
     * @param principal - 安全权限应用到的<code>Principal</code>
     * @throws Exception - 发生了执行错误
     */
    public static void doAsPrivilege(final String methodName, final Filter targetObject, final Class<?>[] targetParameterTypes,
    		final Object[] targetParameterValues, Principal principal) throws Exception {
        Method method = null;
        Method[] methodsCache = classCache.get(Filter.class);
        if(methodsCache == null) {
            method = createMethodAndCacheIt(null, Filter.class, methodName, targetParameterTypes);
        } else {
            method = findMethod(methodsCache, methodName);
            if (method == null) {
                method = createMethodAndCacheIt(methodsCache, Filter.class, methodName, targetParameterTypes);
            }
        }

        execute(method, targetObject, targetParameterValues, principal);
    }
    
    
    /**
     * 作为特定<code>Subject</code>执行工作。在这里，工作将被授予空<code>Subject</code>。
     *
     * @param methodName - 应用安全限制的方法
     * @param targetObject - 该方法将被调用的Servlet
     * @param targetParameterTypes - 用于实例化方法对象的类数组
     * @param targetArguments - 对象数组包含运行时参数实例。
     * @param principal - 安全权限应用到的<code>Principal</code>
     * @throws Exception - 发生了执行错误
     */
    public static void doAsPrivilege(final String methodName, final Servlet targetObject, final Class<?>[] targetParameterTypes, 
    		final Object[] targetArguments, Principal principal) throws Exception {
        Method method = null;
        Method[] methodsCache = classCache.get(Servlet.class);
        if(methodsCache == null) {
            method = createMethodAndCacheIt(null, Servlet.class, methodName, targetParameterTypes);
        } else {
            method = findMethod(methodsCache, methodName);
            if (method == null) {
                method = createMethodAndCacheIt(methodsCache, Servlet.class, methodName, targetParameterTypes);
            }
        }

        execute(method, targetObject, targetArguments, principal);
    }
    
    /**
     * 创建方法并将其缓存以供进一步重用
     * @param methodsCache - 用于存储方法实例的缓存
     * @param targetType - 将在其上调用方法的类
     * @param methodName - 应用安全限制的方法
     * @param parameterTypes - 用于实例化方法对象的类数组
     * @return 方法实例
     * @throws Exception - 发生了执行错误
     */
    private static Method createMethodAndCacheIt(Method[] methodsCache, Class<?> targetType, String methodName, Class<?>[] parameterTypes) throws Exception {
        if (methodsCache == null) {
            methodsCache = new Method[4];
        }

        Method method = targetType.getMethod(methodName, parameterTypes);

        if (methodName.equals(INIT_METHOD)){
            methodsCache[INIT] = method;
        } else if (methodName.equals(DESTROY_METHOD)){
            methodsCache[DESTROY] = method;
        } else if (methodName.equals(SERVICE_METHOD)){
            methodsCache[SERVICE] = method;
        } else if (methodName.equals(DOFILTER_METHOD)){
            methodsCache[DOFILTER] = method;
        } else if (methodName.equals(EVENT_METHOD)){
            methodsCache[EVENT] = method;
        } else if (methodName.equals(DOFILTEREVENT_METHOD)){
            methodsCache[DOFILTEREVENT] = method;
        }

        classCache.put(targetType, methodsCache);
        return method;
    }
    
    
    /**
     * 查找存储在缓存中的方法
     * 
     * @param methodsCache - 用于存储方法实例的缓存
     * @param methodName - 应用安全限制的方法
     * @return 方法实例，如果尚未创建则为空
     */
    private static Method findMethod(Method[] methodsCache, String methodName){
        if (methodName.equals(INIT_METHOD)){
            return methodsCache[INIT];
        } else if (methodName.equals(DESTROY_METHOD)){
            return methodsCache[DESTROY];
        } else if (methodName.equals(SERVICE_METHOD)){
            return methodsCache[SERVICE];
        } else if (methodName.equals(DOFILTER_METHOD)){
            return methodsCache[DOFILTER];
        } else if (methodName.equals(EVENT_METHOD)){
            return methodsCache[EVENT];
        } else if (methodName.equals(DOFILTEREVENT_METHOD)){
            return methodsCache[DOFILTEREVENT];
        }
        return null;
    }
    
    
    /**
     * 作为一个特定的<code>Subject</code>执行工作。在这里，工作将被授予一个空<code>Subject</code>。
     *
     * @param method - 应用安全限制的方法
     * @param targetObject - 方法将被调用的<code>Servlet</code>
     * @param targetArguments - 对象数组包含运行时参数实例
     * @param principal - 安全权限应用到的<code>Principal</code>
     * @throws Exception - 发生了执行错误
     */
    private static void execute(final Method method, final Object targetObject, final Object[] targetArguments, Principal principal) throws Exception {
        try{
            Subject subject = null;
            PrivilegedExceptionAction<Void> pea = new PrivilegedExceptionAction<Void>(){
            	@Override
            	public Void run() throws Exception{
            		method.invoke(targetObject, targetArguments);
            		return null;
            	}
            };

            // 第一个参数总是请求对象
            if (targetArguments != null && targetArguments[0] instanceof HttpServletRequest){
                HttpServletRequest request = (HttpServletRequest)targetArguments[0];

                boolean hasSubject = false;
                HttpSession session = request.getSession(false);
                if (session != null){
                    subject = (Subject)session.getAttribute(Globals.SUBJECT_ATTR);
                    hasSubject = (subject != null);
                }

                if (subject == null){
                    subject = new Subject();

                    if (principal != null){
                        subject.getPrincipals().add(principal);
                    }
                }

                if (session != null && !hasSubject) {
                    session.setAttribute(Globals.SUBJECT_ATTR, subject);
                }
            }

            Subject.doAsPrivileged(subject, pea, null);
        } catch( PrivilegedActionException pe) {
            Throwable e;
            if (pe.getException() instanceof InvocationTargetException) {
                e = pe.getException().getCause();
                ExceptionUtils.handleThrowable(e);
            } else {
                e = pe;
            }

            if (logger.isDebugEnabled()){
                logger.debug("SecurityUtil.doAsPrivilege", e);
            }

            if (e instanceof UnavailableException)
                throw (UnavailableException) e;
            else if (e instanceof ServletException)
                throw (ServletException) e;
            else if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new ServletException(e.getMessage(), e);
        }
    }
    
}
