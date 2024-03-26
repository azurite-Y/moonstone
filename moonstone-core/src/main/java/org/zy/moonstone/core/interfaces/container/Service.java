package org.zy.moonstone.core.interfaces.container;


import org.zy.moonstone.core.connector.Connector;
import org.zy.moonstone.core.interfaces.connector.Executor;
import org.zy.moonstone.core.mapper.Mapper;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * 一个服务是一组一个或多个连接器，它们共享一个容器来处理它们的传入请求。例如，这种安排允许非SSL和SSL连接器共享相同的web应用程序。
 * <p>
 * 一个给定的JVM可以包含任意数量的Service实例;然而，它们彼此是完全独立的，并且只共享系统类路径上的基本JVM工具和类
 */
public interface Service extends Lifecycle {
	// --------------------------------------------------------- 属性---------------------------------------------------------

    /**
     * @return 引擎，处理与此服务关联的所有连接器的请求.
     */
    public Engine getContainer();

    /**
     * 设置处理与此服务关联的所有连接器请求的引擎.
     *
     * @param engine 新的Engine
     */
    public void setContainer(Engine engine);

    /**
     * @return 服务的名称.
     */
    public String getName();

    /**
     * 设置此服务的名称.
     *
     * @param name  - 新服务的名称
     */
    public void setName(String name);

    /**
     * @return 与我们相关的服务器(如果有的话).
     */
    public Server getServer();

    /**
     * 设置与我们关联的服务器(如果有的话).
     *
     * @param server 拥有此服务的服务器
     */
    public void setServer(Server server);

    /**
     * @return 此组件的父类装入器。如果没有设置，返回getServer()-Server.getParentClassLoader()。
     * 如果没有设置服务器，则返回系统类装入器.
     */
    public ClassLoader getParentClassLoader();

    /**
     * 设置此服务的父类装入器.
     *
     * @param parent - 新的父类装入器
     */
    public void setParentClassLoader(ClassLoader parent);


	// --------------------------------------------------------- 公共方法---------------------------------------------------------
    /**
     * 向已定义的连接器集添加一个新的连接器，并将其与服务的容器相关联.
     *
     * @param connector - 要添加的连接器
     */
    public void addConnector(Connector connector);

    /**
     * 查找并返回与此服务关联的连接器集.
     *
     * @return 关联的连接器集
     */
    public Connector[] findConnectors();

    /**
     * 从与此服务关联的集合中删除指定的连接器。被移除的连接器也将与我们的容器解除关联.
     *
     * @param connector - 待拆卸的连接器
     */
    public void removeConnector(Connector connector);

    /**
     * 向服务添加命名执行程序
     */
    public void addExecutor(Executor ex);

    /**
     * 检索所有执行器
     */
    public Executor[] findExecutors();

    /**
     * 按名称检索执行程序，如果没有找到则为空
     */
    public Executor getExecutor(String executorName);

    /**
     * 从服务中移除执行器
     */
    public void removeExecutor(Executor ex);

    /**
     * @return 与此服务相关联的映射器.
     */
    Mapper getMapper();
}
