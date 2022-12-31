package org.zy.moonStone.core.interfaces.container;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * 引擎是一个代表整个Catalina servlet引擎的容器
 * <p>
 * 通常，当部署连接到web服务器(如Apache)的Catalina时，你不会使用Engine，因为连接器将利用web服务器的设施来决定应该使用哪个上下文(或者甚至是哪个包装器)来处理这个请求。
 * 连接到Engine的子容器通常是Host(表示一个虚拟主机)或Context(表示单个servlet上下文)的实现，这取决于Engine的实现
 */
public interface Engine extends Container {
	 /**
     * @return 此 Engine 的默认主机名.
     */
    public String getDefaultHost();

    /**
     * 为这个Engine 设置默认主机名.
     */
    public void setDefaultHost(String defaultHost);

    /**
     * @return 此引擎的Jvm路由ID.
     */
    public String getJvmRoute();

    /**
     * 为此引擎设置Jvm路由ID.
     *
     * @param jvmRouteId - （新）JVM路由ID。群集中的每个引擎必须具有唯一的JVM路由ID.
     */
    public void setJvmRoute(String jvmRouteId);

    /**
     * @return 与我们关联的服务（如果有）.
     */
    public Service getService();

    /**
     * 设置与我们关联的服务（如果有）.
     *
     * @param service - 拥有此引擎的服务
     */
    public void setService(Service service);
}
