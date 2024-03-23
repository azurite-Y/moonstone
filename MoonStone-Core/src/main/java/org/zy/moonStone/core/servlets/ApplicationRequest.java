package org.zy.moonstone.core.servlets;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * @dateTime 2022年11月16日;
 * @author zy(azurite-Y);
 * @description
 * 将应用程序请求对象(它可能是传递给servlet的原始请求，也可能是基于{@link ServletRequestWrapper }类)转换回内部的 {@link HttpRequest}。
 * <p>
 * <strong>WARNING</strong>: 
 * 由于Java缺乏对多重继承的支持，ApplicationRequest中的所有逻辑在{@link ApplicationHttpRequest}中都是重复的。确保在进行更改时保持这两个类同步!
 */
class ApplicationRequest extends ServletRequestWrapper {
	/**
     * 请求分派器专用的属性名称集
     */
    protected static final String specials[] ={ RequestDispatcher.INCLUDE_REQUEST_URI,
      RequestDispatcher.INCLUDE_CONTEXT_PATH,
      RequestDispatcher.INCLUDE_SERVLET_PATH,
      RequestDispatcher.INCLUDE_PATH_INFO,
      RequestDispatcher.INCLUDE_QUERY_STRING,
      RequestDispatcher.INCLUDE_MAPPING,
      RequestDispatcher.FORWARD_REQUEST_URI,
      RequestDispatcher.FORWARD_CONTEXT_PATH,
      RequestDispatcher.FORWARD_SERVLET_PATH,
      RequestDispatcher.FORWARD_PATH_INFO,
      RequestDispatcher.FORWARD_QUERY_STRING,
      RequestDispatcher.FORWARD_MAPPING};

    /**
     * 此请求的请求属性。这是从包装的请求开始初始化的，但是允许更新。
     */
    protected final HashMap<String, Object> attributes = new HashMap<>();
    
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 围绕指定的servlet请求构造一个新的包装请求
     *
     * @param request - 正在包装的servlet请求
     */
    public ApplicationRequest(ServletRequest request) {
        super(request);
        setRequest(request);
    }


	// -------------------------------------------------------------------------------------
	// ServletRequestWrapper 实现方法
	// -------------------------------------------------------------------------------------
    /**
     * 重写包装请求的 <code>getAttribute()</code> 方法
     *
     * @param name - 要检索的属性的名称
     */
    @Override
    public Object getAttribute(String name) {
        synchronized (attributes) {
            return attributes.get(name);
        }
    }


    /**
     * 重写包装请求的 <code>getAttributeNames()</code> 方法
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        synchronized (attributes) {
            return Collections.enumeration(attributes.keySet());
        }
    }


    /**
     * 重写包装请求的 <code>removeAttribute()</code> 方法。
     *
     * @param name - 要移除的属性名
     */
    @Override
    public void removeAttribute(String name) {
        synchronized (attributes) {
            attributes.remove(name);
            if (!isSpecial(name))
                getRequest().removeAttribute(name);
        }
    }


    /**
     * 重写包装请求的 <code>setAttribute()</code> 方法。
     *
     * @param name - 要设置属性的名称
     * @param value - 要设置属性的值
     */
    @Override
    public void setAttribute(String name, Object value) {
        synchronized (attributes) {
            attributes.put(name, value);
            if (!isSpecial(name))
                getRequest().setAttribute(name, value);
        }
    }


    /**
     * 设置要包装的请求
     *
     * @param request - 新的包装请求
     */
    @Override
    public void setRequest(ServletRequest request) {
        super.setRequest(request);

        // 初始化此请求的属性
        synchronized (attributes) {
            attributes.clear();
            Enumeration<String> names = request.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Object value = request.getAttribute(name);
                attributes.put(name, value);
            }
        }
    }


	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    /**
     * 这个属性名是只为包含的servlet添加的特殊属性之一吗？
     *
     * @param name - 要测试的属性名
     */
    protected boolean isSpecial(String name) {
        for (String special : specials) {
            if (special.equals(name))
                return true;
        }
        return false;
    }
}
