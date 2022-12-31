package org.zy.moonStone.core.interfaces.container;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import org.zy.moonStone.core.startup.Moon;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 
 * 服务器元素表示整个MoonStone servlet容器。它的属性表示servlet容器作为一个整体的特性。服务器可以包含一个或多个服务以及顶级命名资源集。
 * <p>
 * 通常，此接口的实现也将实现生命周期，这样当调用start（）和stop（）方法时，所有定义的服务也将启动或停止。
 * </p>
 * 在这两者之间，实现必须在port属性指定的端口号上打开服务器套接字。接受连接后，将读取第一行并与指定的关机命令进行比较。如果命令匹配，则启动服务器关闭
 */
public interface Server extends Lifecycle {
	// --------------------------------------------------------- 属性---------------------------------------------------------
	/**
	 * @return 侦听关机命令的端口号.
	 *
	 * @see #getPortOffset()
	 * @see #getPortWithOffset()
	 */
	public int getPort();


	/**
	 * 设置侦听关机命令的端口号.
	 * 
	 * @see #setPortOffset(int)
	 */
	public void setPort(int port);

	/**
	 * 获取用于关闭命令的端口偏移数。例如，如果端口为8005，端口偏移量为1000，则服务器在9005侦听,
	 *
	 * @return 端口偏移量
	 */
	public int getPortOffset();

	/**
	 * 设置用于关闭命令的服务器端口的偏移量。例如，如果端口为8005，并且将portOffset设置为1000，则连接器将侦听9005.
	 *
	 * @param portOffset - 设置端口偏移量
	 */
	public void setPortOffset(int portOffset);

	/**
	 * 获取服务器侦听关机命令的实际端口。如果未设置端口偏移量，则返回端口。如果设置端口偏移量，则返回端口偏移量+端口.
	 */
	public int getPortWithOffset();

	/**
	 * @return 侦听关机命令的地址.
	 */
	public String getAddress();


	/**
	 * 设置侦听关机命令的地址.
	 */
	public void setAddress(String address);


	/**
	 * @return 正在等待的关机命令字符串.
	 */
	public String getShutdown();


	/**
	 * 设置我们正在等待的关机命令.
	 *
	 * @param shutdown - 新的关机命令
	 */
	public void setShutdown(String shutdown);


	/**
	 * @return 此组件的父类装入器。如果没有设置，返回getMoon()-Moon.getParentClassLoader()。
	 * 如果没有设置Moon，则返回系统类装入器
	 */
	public ClassLoader getParentClassLoader();


	/**
	 * 设置此服务器的父类装入器.
	 *
	 * @param parent - 新的父类装入器
	 */
	public void setParentClassLoader(ClassLoader parent);

	
	/**
	 * @return 外部Moon启动/关闭组件(如果存在).
	 */
	public Moon getMoon();

	
	/**
	 * 设置外部Catalina启动/关闭组件(如果存在).
	 *
	 * @param moon - 外部Catalina组件
	 */
	public void setMoon(Moon moon);


	/**
	 * @return 配置的基本(实例)目录。注意，home和base可能相同(默认情况下也是)。
	 * 如果没有设置这个值，将使用 {@link #getCatalinaHome()}  返回的值
	 */
	public File getMoonBase();

	/**
	 * 设置配置的基本(实例)目录。注意，home和base可能相同(默认情况下也是).
	 *
	 * @param catalinaBase - 配置的基本目录
	 */
	public void setMoonBase(File moonBase);


	/**
	 * @return 配置的主(二进制)目录。注意，home和base可能相同(默认情况下也是).
	 */
	public File getMoonHome();

	/**
	 * 设置配置的主(二进制)目录。注意，home和base可能相同(默认情况下也是).
	 *
	 * @param moonHome - 配置的主目录
	 */
	public void setMoonHome(File moonHome);


	/**
	 * 获取实用程序线程数.
	 * @return 线程数
	 */
	public int getUtilityThreads();


	/**
	 * 设置实用程序线程计数.
	 * @param utilityThreads - 新线程数
	 */
	public void setUtilityThreads(int utilityThreads);


	// --------------------------------------------------------- 公共方法---------------------------------------------------------
	/**
	 * 向已定义的服务集添加一个新的服务.
	 *
	 * @param service - 待添加的服务
	 */
	public void addService(Service service);


	/**
	 * 等待，直到收到正确的关闭命令，然后返回.
	 */
	public void await();


	/**
	 * 查找指定的服务
	 *
	 * @param name - 要返回的服务的名称
	 * @return 指定的服务，如果不存在则为空.
	 */
	public Service findService(String name);


	/**
	 * @return 本服务器中定义的一组服务.
	 */
	public Service[] findServices();


	/**
	 * 从与此服务器相关联的集合中删除指定的服务
	 *
	 * @param service - 需要删除的服务
	 */
	public void removeService(Service service);

	/**
	 * @return 服务管理的线程执行器.
	 */
	public ScheduledExecutorService getUtilityExecutor();
}
