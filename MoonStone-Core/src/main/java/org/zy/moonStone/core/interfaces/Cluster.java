package org.zy.moonstone.core.interfaces;

import org.zy.moonstone.core.interfaces.container.Contained;
import org.zy.moonstone.core.session.interfaces.Manager;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * 一个集群作为本地主机的集群客户端/服务器工作。不同的集群实现可以用来支持不同的方式在集群内通信。
 * 一个集群实现负责在集群中建立一种通信的方式，并为“ClientApplications”提供在集群中发送信息时使用的clustersender和在集群中接收信息时使用的ClusterInfo
 */
public interface Cluster extends Contained {
	/**
	 * 返回当前配置为在其中运行此服务器的集群的名称.
	 *
	 * @return 与此服务器关联的集群的名称
	 */
	public String getClusterName();


	/**
	 * 设置要加入的集群的名称，如果没有具有此名称的集群，则创建一个.
	 *
	 * @param clusterName - 要加入的集群名称
	 */
	public void setClusterName(String clusterName);


	/**
	 * 创建一个新的管理器，它将使用这个集群来复制它的会话.
	 *
	 * @param name - 与管理器关联的应用程序的名称(键)
	 * @return 新创建的Manager实例
	 */
	public Manager createManager(String name);


	/**
	 * 向集群注册一个管理器。如果集群不负责创建管理器，那么容器至少会通知集群该管理器正在参与集群.
	 */
	public void registerManager(Manager manager);


	/**
	 * 从集群中移除管理器
	 */
	public void removeManager(Manager manager);


	/**
	 * 执行周期性任务，如重新加载等。此方法将在该容器的类加载上下文中调用。意外throwables将被捕获并记录.
	 */
	public void backgroundProcess();
}
