package org.zy.moonstone.core.interfaces.connector;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @dateTime 2022年1月7日;
 * @author zy(azurite-Y);
 * @description 抽象协议实现，包括线程等。这是协议要实现的主要接口。
 */
public interface ProtocolHandler {
	/**
	 * 返回与协议处理程序关联的适配器.
	 */
	Adapter getAdapter();


	/**
	 * 适配器，用于调用连接器.
	 *
	 * @param adapter - 要关联的适配器
	 */
	void setAdapter(Adapter adapter);


	/**
	 * 执行器提供对底层线程池的访问.
	 *
	 * @return 用于处理请求的执行者
	 */
	Executor getExecutor();


	/**
	 * 设置连接器将使用的可选执行器.
	 */
	void setExecutor(Executor executor);


	/**
	 * 获取协议处理程序应使用的实用程序执行器.
	 */
	ScheduledExecutorService getUtilityExecutor();


	/**
	 * 设置协议处理程序应使用的实用程序执行器.
	 */
	void setUtilityExecutor(ScheduledExecutorService utilityExecutor);


	/**
	 * 初始化协议
	 *
	 * @throws Exception - 如果协议处理程序未能初始化
	 */
	void init() throws Exception;


	/**
	 * 启动协议
	 *
	 * @throws Exception - 如果协议处理程序无法启动
	 */
	void start() throws Exception;


	/**
	 * 暂停协议（可选）.
	 *
	 * @throws Exception - 如果协议处理程序未能暂停
	 */
	void pause() throws Exception;


	/**
	 * 恢复协议（可选）.
	 *
	 * @throws Exception - 如果协议处理程序无法恢复
	 */
	void resume() throws Exception;


	/**
	 * 停止协议.
	 *
	 * @throws Exception - 如果协议处理程序未能停止
	 */
	void stop() throws Exception;


	/**
	 * 销毁协议（可选）.
	 *
	 * @throws Exception - 如果协议处理程序未能销毁
	 */
	void destroy() throws Exception;


	/**
	 * 如果服务器套接字绑定在{@link #start()}上（而不是在{@link #init()}上），请关闭服务器套接字（以防止进一步连接），但不要执行任何进一步的关闭操作.
	 */
	void closeServerSocketGraceful();


	/**
	 * 需要APR/本机库
	 *
	 * @return 如果此协议处理程序需要APR/本机库，则为true，否则为false
	 */
	boolean isAprRequired();


	/**
	 * 此ProtocolHandler支持sendfile吗？
	 *
	 * @return 如果该协议处理程序支持sendfile，则为true，否则为false
	 */
	boolean isSendfileSupported();


	/**
	 * 为虚拟主机添加新的SSL配置.
	 */
//	void addSslHostConfig(SSLHostConfig sslHostConfig);


	/**
	 * 查找将被SNI使用的所有配置的SSL虚拟主机配置。
	 */
//	SSLHostConfig[] findSslHostConfigs();


	/**
	 * 为HTTP/1.1升级或ALPN添加一个新的协议。
	 * @param upgradeProtocol - 该协议
	 */
	void addUpgradeProtocol(org.zy.moonstone.core.interfaces.connector.UpgradeProtocol upgradeProtocol);

	/**
	 * 返回所有配置的升级协议。
	 * @return 该协议
	 */
	org.zy.moonstone.core.interfaces.connector.UpgradeProtocol[] findUpgradeProtocols();
}
