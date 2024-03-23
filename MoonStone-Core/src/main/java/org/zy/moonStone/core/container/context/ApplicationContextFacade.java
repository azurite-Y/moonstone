package org.zy.moonstone.core.container.context;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.zy.moonstone.core.security.SecurityUtil;
import org.zy.moonstone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description Facade对象，它将内部ApplicationContext对象与web应用程序屏蔽。
 */
public class ApplicationContextFacade implements ServletContext {
	// ---------------------------------------------------------- 
	// 属性 
	// ----------------------------------------------------------
	/**
	 * 包装应用程序上下文
	 */
	private final ApplicationContext context;
	
    /** 用于反射的缓存类对象 */
    private final Map<String,Class<?>[]> classCache;

    /** 缓存方法对象 */
    private final Map<String,Method> objectCache;

    
	// ---------------------------------------------------------- 
    // 构造器 
    // ----------------------------------------------------------
	/**
	 * 构造这个类的一个新实例，与指定的Context实例相关联。
	 *
	 * @param context - 关联的Context实例
	 */
	public ApplicationContextFacade(ApplicationContext context) {
		super();
		this.context = context;
		
        classCache = new HashMap<>();
        objectCache = new ConcurrentHashMap<>();
        initClassCache();
	}

	private void initClassCache(){
        Class<?>[] clazz = new Class[]{String.class};
        classCache.put("getContext", clazz);
        classCache.put("getMimeType", clazz);
        classCache.put("getResourcePaths", clazz);
        classCache.put("getResource", clazz);
        classCache.put("getResourceAsStream", clazz);
        classCache.put("getRequestDispatcher", clazz);
        classCache.put("getNamedDispatcher", clazz);
        classCache.put("getServlet", clazz);
        classCache.put("setInitParameter", new Class[]{String.class, String.class});
        classCache.put("createServlet", new Class[]{Class.class});
        classCache.put("addServlet", new Class[]{String.class, String.class});
        classCache.put("createFilter", new Class[]{Class.class});
        classCache.put("addFilter", new Class[]{String.class, String.class});
        classCache.put("createListener", new Class[]{Class.class});
        classCache.put("addListener", clazz);
        classCache.put("getFilterRegistration", clazz);
        classCache.put("getServletRegistration", clazz);
        classCache.put("getInitParameter", clazz);
        classCache.put("setAttribute", new Class[]{String.class, Object.class});
        classCache.put("removeAttribute", clazz);
        classCache.put("getRealPath", clazz);
        classCache.put("getAttribute", clazz);
        classCache.put("log", clazz);
        classCache.put("setSessionTrackingModes", new Class[]{Set.class} );
    }
	
	
	// ------------------------------------------------- 
	// ServletContext 方法 
	// -------------------------------------------------
	@Override
	public ServletContext getContext(String uripath) {
		ServletContext theContext = context.getContext(uripath);
		if ((theContext != null) && (theContext instanceof ApplicationContext)){
			theContext = ((ApplicationContext)theContext).getFacade();
		}
		return theContext;
	}

	@Override
	public String getMimeType(String file) {
		return context.getMimeType(file);
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		return context.getResourcePaths(path);
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		return context.getResource(path);
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		return context.getResourceAsStream(path);
	}

	@Override
	public RequestDispatcher getRequestDispatcher(final String path) {
		if (SecurityUtil.isPackageProtectionEnabled()) {
			return (RequestDispatcher) doPrivileged("getRequestDispatcher", new Object[] { path });
		} else {
			return context.getRequestDispatcher(path);
		}
	}

	@Override
	public RequestDispatcher getNamedDispatcher(String name) {
		return context.getNamedDispatcher(name);
	}

	@Override
	@Deprecated
	public Servlet getServlet(String name) throws ServletException {
		return context.getServlet(name);
	}

	@Override
	@Deprecated
	public Enumeration<Servlet> getServlets() {
		return context.getServlets();
	}

	@Override
	@Deprecated
	public Enumeration<String> getServletNames() {
		return context.getServletNames();
	}

	@Override
	public void log(String msg) {
		context.log(msg);
	}

	@Override
	@Deprecated
	public void log(Exception exception, String msg) {
		context.log(exception, msg);
	}

	@Override
	public void log(String message, Throwable throwable) {
		context.log(message, throwable);
	}

	@Override
	public String getRealPath(String path) {
		return context.getRealPath(path);
	}

	@Override
	public String getServerInfo() {
		return context.getServerInfo();
	}

	@Override
	public String getInitParameter(String name) {
		return context.getInitParameter(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return context.getInitParameterNames();
	}

	@Override
	public Object getAttribute(String name) {
		return context.getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return context.getAttributeNames();
	}

	@Override
	public void setAttribute(String name, Object object) {
		context.setAttribute(name, object);
	}

	@Override
	public void removeAttribute(String name) {
		context.removeAttribute(name);
	}

	@Override
	public String getServletContextName() {
		return context.getServletContextName();
	}

	@Override
	public String getContextPath() {
		return context.getContextPath();
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, String className) {
		return context.addFilter(filterName, className);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		return context.addFilter(filterName, filter);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		return context.addFilter(filterName, filterClass);
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
		return context.createFilter(c);
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName) {
		return context.getFilterRegistration(filterName);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, String className) {
		return context.addServlet(servletName, className);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
		return context.addServlet(servletName, servlet);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		return context.addServlet(servletName, servletClass);
	}

	@Override
	public Dynamic addJspFile(String jspName, String jspFile) {
		return context.addJspFile(jspName, jspFile);
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
		return context.createServlet(c);
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName) {
		return context.getServletRegistration(servletName);
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		return context.getDefaultSessionTrackingModes();
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		return context.getEffectiveSessionTrackingModes();
	}

	@Override
	public SessionCookieConfig getSessionCookieConfig() {
		return context.getSessionCookieConfig();
	}

	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		context.setSessionTrackingModes(sessionTrackingModes);
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		return context.setInitParameter(name, value);
	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass) {
		context.addListener(listenerClass);
	}

	@Override
	public void addListener(String className) {
		context.addListener(className);
	}

	@Override
	public <T extends EventListener> void addListener(T t) {
		context.addListener(t);
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
		return context.createListener(c);
	}

	@Override
	public void declareRoles(String... roleNames) {
		context.declareRoles(roleNames);
	}

	@Override
	public ClassLoader getClassLoader() {
		return context.getClassLoader();
	}

	@Override
	public int getEffectiveMajorVersion() {
		return context.getEffectiveMajorVersion();
	}


	@Override
	public int getEffectiveMinorVersion() {
		return context.getEffectiveMinorVersion();
	}

	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		return context.getFilterRegistrations();
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		return context.getJspConfigDescriptor();
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		return context.getServletRegistrations();
	}

	@Override
	public String getVirtualServerName() {
		return context.getVirtualServerName();
	}

	@Override
	public int getSessionTimeout() {
		return context.getSessionTimeout();
	}

	@Override
	public void setSessionTimeout(int sessionTimeout) {
		context.setSessionTimeout(sessionTimeout);
	}

	@Override
	public String getRequestCharacterEncoding() {
		return context.getRequestCharacterEncoding();
	}

	@Override
	public void setRequestCharacterEncoding(String encoding) {
		context.setRequestCharacterEncoding(encoding);
	}

	@Override
	public String getResponseCharacterEncoding() {
		return context.getResponseCharacterEncoding();
	}

	@Override
	public void setResponseCharacterEncoding(String encoding) {
		context.setResponseCharacterEncoding(encoding);
	}

	/**
	 * 返回此Servlet容器支持的Servlet API的主要版本。所有符合版本4.0的实现都必须使用此方法返回整数4。
	 */
	@Override
    public int getMajorVersion() {
        return context.getMajorVersion();
    }

	/**
	 * 返回此 servlet 容器支持的 Servlet API 的次要版本。 所有符合 4.0 版本的实现都必须让这个方法返回整数 0。
	 */
    @Override
    public int getMinorVersion() {
        return context.getMinorVersion();
    }
    
    
    /**
     * 使用反射来调用请求的方法。缓存方法对象以加快进程
     * 
     * @param methodName - 回调的方法
     * @param params 传递给被调用方法的参数
     */
    private Object doPrivileged(final String methodName, final Object[] params) {
        try{
            return invokeMethod(context, methodName, params);
        }catch(Throwable t){
            ExceptionUtils.handleThrowable(t);
            throw new RuntimeException(t.getMessage(), t);
        }
    }


    /**
     * 使用反射来调用请求的方法。缓存方法对象以加快进程
     * 
     * @param appContext - 方法将被调用的ApplicationContext对象
     * @param methodName - 回调的方法
     * @param params - 传递给被调用方法的参数
     */
    private Object invokeMethod(ApplicationContext appContext, final String methodName, Object[] params) throws Throwable{
        try{
            Method method = objectCache.get(methodName);
            if (method == null){
                method = appContext.getClass().getMethod(methodName, classCache.get(methodName));
                objectCache.put(methodName, method);
            }

            return executeMethod(method,appContext,params);
        } catch (Exception ex){
            handleException(ex);
            return null;
        } finally {
            params = null;
        }
    }

    /**
     * 使用反射来调用请求的方法。缓存方法对象以加快进程
     * @param methodName - 要调用的方法
     * @param clazz - 方法所在的类
     * @param params - 传递给被调用方法的参数
     */
    @SuppressWarnings("unused")
	private Object doPrivileged(final String methodName, final Class<?>[] clazz, Object[] params) {
        try{
            Method method = context.getClass().getMethod(methodName, clazz);
            return executeMethod(method,context,params);
        } catch (Exception ex){
            try {
                handleException(ex);
            } catch (Throwable t){
                ExceptionUtils.handleThrowable(t);
                throw new RuntimeException(t.getMessage());
            }
            return null;
        } finally {
            params = null;
        }
    }


    /**
     * 执行指定的 <code>ApplicationContext</code> 的方法
     * 
     * @param method - 要调用的方法对象
     * @param context - 方法将被调用的ApplicationContext对象
     * @param params - 传递给被调用方法的参数
     */
    private Object executeMethod(final Method method, final ApplicationContext context, final Object[] params) 
    		throws PrivilegedActionException, IllegalAccessException, InvocationTargetException {
        if (SecurityUtil.isPackageProtectionEnabled()){
           return AccessController.doPrivileged( new PrivilegedExecuteMethod(method, context,  params) );
        } else {
            return method.invoke(context, params);
        }
    }


    /**
     * 抛出真正的异常
     * 
     * @param ex - 当前异常
     */
    private void handleException(Exception ex) throws Throwable {
        Throwable realException;

        if (ex instanceof PrivilegedActionException) {
            ex = ((PrivilegedActionException) ex).getException();
        }

        if (ex instanceof InvocationTargetException) {
            realException = ex.getCause();
            if (realException == null) {
                realException = ex;
            }
        } else {
            realException = ex;
        }

        throw realException;
    }


	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
    private static class PrivilegedExecuteMethod implements PrivilegedExceptionAction<Object> {
        private final Method method;
        private final ApplicationContext context;
        private final Object[] params;

        public PrivilegedExecuteMethod(Method method, ApplicationContext context, Object[] params) {
            this.method = method;
            this.context = context;
            this.params = params;
        }

        @Override
        public Object run() throws Exception {
            return method.invoke(context, params);
        }
    }
}
