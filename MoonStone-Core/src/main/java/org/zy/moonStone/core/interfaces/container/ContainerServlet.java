package org.zy.moonStone.core.interfaces.container;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description
 * 一个ContainerServlet是一个可以访问Catalina内部功能的servlet，它是从 Moon 类加载器而不是web应用程序类加载器加载的。
 * 每当这个servlet的新实例投入服务时，容器必须调用属性setter方法。
 */
public interface ContainerServlet {
	/**
     * 获取与此Servlet相关联的包装器.
     *
     * @return 与此Servlet相关联的包装器.
     */
    public Wrapper getWrapper();


    /**
     * 设置与此Servlet相关联的Wrapper.
     *
     * @param wrapper - 新的关联包装器
     */
    public void setWrapper(Wrapper wrapper);
}
