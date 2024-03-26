package org.zy.moonstone.core.interfaces.container;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description
 */
public interface Wrapper extends Container {
    // ------------------------------------------------------------- 属性 -------------------------------------------------------------
	/**
     * 用于添加 Wrapper 的容器事件.
     */
    public static final String ADD_MAPPING_EVENT = "addMapping";

    /**
     * 用于删除 Wrapper 的容器事件.
     */
    public static final String REMOVE_MAPPING_EVENT = "removeMapping";

    /**
     * @return 此servlet的可用日期/时间，以毫秒为单位，从新纪元开始。
     * 如果此日期/时间在将来，对该servlet的任何请求都将返回 SC_SERVICE_Unavailable 错误。
     * 如果为零，则servlet当前可用。等于 Long.MAX_VALUE 被认为意味着永久不可用.
     */
    public long getAvailable();

    /**
     * 设置此servlet的可用日期/时间，以毫秒为单位，从新纪元开始。
     * 如果此日期/时间在将来，对该servlet的任何请求都将返回 SC_SERVICE_Unavailable 错误。
     * 如果为零，则servlet当前可用。等于 Long.MAX_VALUE 被认为意味着永久不可用.
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available);

    /**
     * @return 启动时加载顺序值（负值表示第一次调用时加载）.
     */
    public int getLoadOnStartup();

    /**
     * 设置启动时加载顺序值（负值表示第一次调用时加载）.
     *
     * @param value - 启动时的新负载值
     */
    public void setLoadOnStartup(int value);

    /**
     * @return 此servlet的运行方式标识.
     */
    public String getRunAs();

    /**
     * 设置此servlet的运行方式标识.
     *
     * @param runAs - 新运行方式标识值
     */
    public void setRunAs(String runAs);

    /**
     * @return 此servlet的完全限定servlet类名.
     */
    public String getServletClass();

    /**
     * 为此servlet设置完全限定的servlet类名.
     *
     * @param servletClass - Servlet类名
     */
    public void setServletClass(String servletClass);

    /**
     * 获取底层servlet支持的方法的名称。
     * 这与底层servlet处理的option请求方法的Allow响应头消息中包含的一组方法相同.
     *
     * @return 底层servlet支持的方法名数组
     * @throws ServletException - 如果目标servlet不能被加载
     */
    public String[] getServletMethods() throws ServletException;

    /**
     * @return 如果该Servlet当前不可用，则为true.
     */
    public boolean isUnavailable();

    /**
     * @return 关联的Servlet实例.
     */
    public Servlet getServlet();

    /**
     * 设置相关的Servlet实例
     *
     * @param servlet - 相关的Servlet
     */
    public void setServlet(Servlet servlet);

    // --------------------------------------------------------- 公共方法 ---------------------------------------------------------
    /**
     * 为这个servlet添加一个新的servlet初始化参数.
     *
     * @param name - 要添加的初始化参数的名称
     * @param value - 要添加的初始化参数的值 
     */
    public void addInitParameter(String name, String value);

    
    /**
     * 添加与Wrapper关联的映射.
     *
     * @param mapping - 新的包装器映射
     */
    public void addMapping(String mapping);


    /**
     * 分配这个Servlet的一个初始化的实例，该实例已准备好调用它的service()方法。
     *
     * @exception ServletException - 如果Servlet init()方法抛出异常
     * @exception ServletException - 如果加载错误发生
     * @return 一个新的Servlet实例
     */
    public Servlet allocate() throws ServletException;


    /**
     * 将这个先前分配的servlet返回到可用实例池中。如果这个servlet类没有实现SingleThreadModel，那么实际上不需要任何操作.
     *
     * @param servlet - 要返回的servlet
     * @exception ServletException - 如果发生重新分配错误
     */
    public void deallocate(Servlet servlet) throws ServletException;


    /**
     * 
     * @param name - 请求的初始化参数的名称
     * @return 指定的初始化参数名的值，如果有的话;否则返回null.
     */
    public String findInitParameter(String name);


    /**
     * @return 为这个servlet定义的所有初始化参数的名称
     */
    public String[] findInitParameters();


    /**
     * @return 与此包装器关联的映射.
     */
    public String[] findMappings();

    /**
     * 增加监视时使用的错误计数值.
     */
    public void incrementErrorCount();


    /**
     * 加载并初始化这个Servlet的一个实例(如果还没有初始化过的实例)。
     * 例如，它可以用来加载在部署描述符中标记为在服务器启动时加载的servlet.
     *
     * @exception ServletException - 如果Servlet init()方法抛出异常或发生其他加载问题
     */
    public void load() throws ServletException;


    /**
     * 从这个Servlet中删除指定的初始化参数.
     *
     * @param name - 要删除的初始化参数的名称
     */
    public void removeInitParameter(String name);


    /**
     * 移除与包装器关联的映射.
     *
     * @param mapping - 要删除的模式
     */
    public void removeMapping(String mapping);


    /**
     * 处理一个UnavailableException，在指定的时间内将这个Servlet标记为不可用.
     *
     * @param unavailable - 发生的异常，或将此Servlet标记为永久不可用
     */
    public void unavailable(UnavailableException unavailable);


    /**
     * 在为每个实例调用destroy()方法后，卸载这个servlet的所有初始化实例。
     * 例如，这可以在关闭整个servlet引擎之前使用，或者在从Loader的存储库中重新加载所有类之前使用.
     *
     * @exception ServletException - 如果卸载错误发生
     */
    public void unload() throws ServletException;


    /**
     * @return 相关Servlet的multi-part配置。如果没有定义multi-part配置，则返回null.
     */
    public MultipartConfigElement getMultipartConfigElement();


    /**
     * 为相关的Servlet设置multi-part配置。如果要清除multi-part配置，请将新值指定为null.
     *
     * @param multipartConfig - 与Servlet关联的配置
     */
    public void setMultipartConfigElement(MultipartConfigElement multipartConfig);

    /**
     * 相关的Servlet是否支持异步处理?默认值为假.
     *
     * @return 如果Servlet支持异步，则为true
     */
    public boolean isAsyncSupported();

    /**
     * 为相关的Servlet设置异步支持.
     *
     */
    public void setAsyncSupported(boolean asyncSupport);

    /**
     * 关联的Servlet启用了吗?默认值为true.
     *
     * @return 如果启用了Servlet，则为true
     */
    public boolean isEnabled();

    /**
     * 设置相关servlet的启用属性.
     *
     */
    public void setEnabled(boolean enabled);

    /**
     * Servlet可以被ServletContainerInitializer重写吗?
     *
     * @return 如果Servlet可以在ServletContainerInitializer中被重写，则为true
     */
    public boolean isOverridable();

    /**
     * 设置此Servlet的可重写属性.
     *
     */
    public void setOverridable(boolean overridable);
}
