package org.zy.moonstone.core.interfaces.container;

import java.io.File;

import org.slf4j.Logger;
import org.zy.moonstone.core.interfaces.Cluster;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 
 * Container是一个对象，它可以执行从客户端接收到的请求，并根据这些请求返回响应。
 * 一个容器也可以通过实现pipeline接口，选择支持一个阀门的管道，按照运行时配置的顺序处理请求。
 * 
 * 容器有不同的概念层次。以下是一些常见的例子:
 * <ul>
 * <li>Engine	 - —整个Servlet Engine的表示，很可能包含一个或多个子容器，要么是主机上下文实现，要么是其他自定义组。
 * <li>Host		 - 表示一个包含许多上下文的虚拟主机。
 * <li>Context	 - —单个ServletContext的表示，它通常包含一个或多个被支持的servlet的包装器。
 * <li>Wrapper - 单个servlet定义的表示(如果servlet本身实现了SingleThreadModel，则可能支持多个servlet实例)。
 * </ul>
 * 一个给定的 moonstone 部署不需要包含上述所有级别的容器。
 * 例如，嵌入在网络设备(如路由器)中的管理应用程序可能只包含一个Context和几个Wrapper，如果应用程序相对较小，甚至可能只包含一个Wrapper。
 * 因此，容器的实现需要被设计成在给定部署中没有父容器的情况下能够正确操作。
 * <p>
 * 一个容器也可以与许多支持组件相关联，这些支持组件提供的功能可以被共享(通过将其附加到父容器)或单独定制。以下支持组件目前已被认可:
 * <ul>
 * <li>Loader - 类加载器，用于将新的Java类集成到运行 moonstone 的JVM中
 * <li>Logger - 实现了 ServletContext 接口的log()方法签名
 * <li>Manager - 与该容器关联的会话池的Manager
 * </ul>
 */
public interface Container extends Lifecycle {
	// ----------------------------------------------------- 常量 -----------------------------------------------------
	/**
	 * 当子容器被addChild()添加时发送的ContainerEvent事件类型
	 */
	public static final String ADD_CHILD_EVENT = "addChild";


	/**
	 * 当一个Valve被addValve()添加时发送的ContainerEvent事件类型，如果这个容器支持管道
	 */
	public static final String ADD_VALVE_EVENT = "addValve";


	/**
	 * 当子容器被removeChild()移除时发送的ContainerEvent事件类型.
	 */
	public static final String REMOVE_CHILD_EVENT = "removeChild";


	/**
	 * 如果容器支持管道，则当removeValve()移除时发送的ContainerEvent事件类型.
	 */
	public static final String REMOVE_VALVE_EVENT = "removeValve";
	
	
    /**
     * <code>addAlias()</code> 添加新别名时发送的 ContainerEvent 事件类型
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * 当通过 <code>removeAlias()</code> 删除旧别名时发送的 ContainerEvent 事件类型
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";
    
    
    /**
     * 用于添加 wrapper 的容器事件
     */
    public static final String ADD_MAPPING_EVENT = "addMapping";
    
    
    /**
     * 用于移除 wrapper 的容器事件
     */
    public static final String REMOVE_MAPPING_EVENT = "removeMapping";
    
    
    /**
     * 用于添加 welcome 文件的容器事件
     */
    public static final String ADD_WELCOME_FILE_EVENT = "addWelcomeFile";
    
    
    /**
     * 用于移除 Welcome 文件的容器事件
     */
    public static final String REMOVE_WELCOME_FILE_EVENT = "removeWelcomeFile";

    
    /**
     * 用于清除多个 Welcome 文件的容器事件
     */
    public static final String  CLEAR_WELCOME_FILES_EVENT = "clearWelcomeFiles";

    
    /**
     * 用于更改会话 ID 的容器事件
     */
    public static final String CHANGE_SESSION_ID_EVENT = "changeSessionId";

	// ----------------------------------------------------- 屬性 -----------------------------------------------------
	/**
	 * 获取应将此容器的事件记录到的日志.
	 *
	 * @return 与此容器相关联的Logger。如果没有关联的Logger，返回与父容器关联的Logger(如果有);否则返回null.
	 */
	public Logger getLogger();


	/**
	 * 返回管理与此容器关联的阀门的Pipeline对象.
	 */
	public Pipeline getPipeline();


	/**
	 * 获取此容器的Cluster.
	 *
	 * @return 与此容器关联的集群。如果没有关联的集群，返回与父容器关联的集群(如果有);否则返回null.
	 */
	public Cluster getCluster();


	/**
	 * 设置与此容器关联的集群.
	 *
	 * @param cluster - 与该容器关联的集群.
	 */
	public void setCluster(Cluster cluster);


	/**
	 * 获取此容器上的backgroundProcess方法调用与其子容器之间的延迟。
	 * 如果子容器的延迟值为正(这意味着它们正在使用自己的线程)，则不会被调用。
	 * 将此设置为正值将导致触发线程。在等待指定的时间后，线程将对该容器和所有具有非正延迟值的子容器调用backgroundProcess()方法.
	 *
	 * @return 这个容器和它的子容器调用backgroundProcess方法之间的延迟。非正值表示后台处理将由父进程管理.
	 */
	public int getBackgroundProcessorDelay();


	/**
	 * 设置此容器上的execute方法调用与其子容器之间的延迟.
	 *
	 * @param delay - 调用backgroundprocess方法之间的延迟(以秒为单位)
	 */
	public void setBackgroundProcessorDelay(int delay);


	/**
	 * 返回一个名称字符串来描述这个容器。在属于特定父容器的子容器集合中，容器名称必须是唯一的.
	 *
	 * @return 该容器的可读名称.
	 */
	public String getName();


	/**
	 * 设置一个名称字符串来描述这个容器。在属于特定父容器的子容器集合中，容器名称必须是唯一的.
	 *
	 * @param name - 此容器的新名称
	 * @exception IllegalStateException - 如果这个容器已经被添加到一个父容器的子容器中(之后的名称不能被更改)
	 */
	public void setName(String name);


	/**
	 * 获取父容器.
	 *
	 * @return 如果存在子容器，则返回该容器的子容器。如果没有定义父节点，则返回null.
	 */
	public Container getParent();


	/**
	 * 设置该容器作为子容器添加到的父容器。这个容器可以通过抛出异常来拒绝连接到指定的容器.
	 *
	 * @param container - 容器，该容器将作为子容器添加到其中
	 * @exception IllegalArgumentException - 如果该容器拒绝连接到指定的容器
	 */
	public void setParent(Container container);


	/**
	 * 获取父类装入器.
	 *
	 * @return 此组件的父类装入器。如果没有设置，则返回getParent().getparentclassloader()。如果没有设置父类，则返回系统类装入器.
	 */
	public ClassLoader getParentClassLoader();


	/**
	 * 设置此组件的父类装入器。对于context，这个调用只有在Loader被配置之前才有意义，并且指定的值(如果非空)应该作为参数传递给类加载器构造函数.
	 *
	 * @param parent - 新的父类装入器
	 */
	public void setParentClassLoader(ClassLoader parent);

	/**
	 * 找到配置资源所在的配置路径.
	 * @param container
	 * @param resourceName - 资源文件名
	 * @return 配置路径
	 */
	public static String getConfigPath(Container container, String resourceName) {
		StringBuffer result = new StringBuffer();
		Container host = null;
		Container engine = null;
		while (container != null) {
			if (container instanceof Host) {
				host = container;
			} else if (container instanceof Engine) {
				engine = container;
			}
			container = container.getParent();
		}
		if (host == null) {
			result.append("conf/");
			if (engine != null) {
				result.append(engine.getName()).append('/');
			}
		} else {
		}
		result.append(resourceName);
		return result.toString();
	}


	/**
	 * 返回该容器所属的服务.
	 * @param container - 从容器开始
	 * @return 服务，如果没有找到则为空
	 */
	public static Service getService(Container container) {
		while (container != null && !(container instanceof Engine)) {
			container = container.getParent();
		}
		if (container == null) {
			return null;
		}
		return ((Engine) container).getService();
	}

	// ----------------------------------------------------- 公共方法 -----------------------------------------------------
	/**
	 * 执行一个周期性的任务，比如重新加载等。这个方法会在这个容器的类加载上下文中被调用。 Unexpectedthrowables 将被捕获和记录。
	 */
	public void backgroundProcess();


	/**
	 * 如果支持，将一个新的子容器添加到与该容器关联的子容器中。
	 * 在将这个Container添加到子集合之前，必须调用子集合的setParent()方法，并使用thisContainer作为参数。
	 * 如果这个容器选择不附加到指定的容器，这个方法可能会抛出一个IllegalArgumentException，在这种情况下它不会被添加
	 *
	 * @param child - 要添加的新子容器
	 * @exception IllegalArgumentException - 如果这个异常是由子容器的setParent()方法抛出的
	 * @exception IllegalArgumentException - 如果新的子容器的名称与现有的子容器的名称不同
	 * @exception IllegalStateException - 如果这个容器不支持子容器
	 */
	public void addChild(Container child);


	/**
	 * 向该组件添加容器事件监听器.
	 */
	public void addContainerListener(ContainerListener listener);


	/**
	 * 按名称获取一个子容器.
	 *
	 * @param name - 要检索的子容器的名称
	 * @return 返回与该容器关联的子容器，指定名称(如果有);否则,返回空.
	 */
	public Container findChild(String name);

	/**
	 * 获取与此容器关联的子容器.
	 *
	 * @return 包含此容器的所有子容器的数组。如果这个容器没有子容器，则返回一个零长度的数组.
	 */
	public Container[] findChildren();


	/**
	 * 获取与此容器关联的容器监听器.
	 *
	 * @return 包含与此容器关联的容器监听器的数组。如果这个容器没有注册的容器监听器，则返回一个零长度的数组.
	 */
	public ContainerListener[] findContainerListeners();


	/**
	 * 从这个parentContainer关联中移除一个已经存在的子Container.
	 *
	 * @param child - 要移除的现有子容器
	 */
	public void removeChild(Container child);


	/**
	 * 从该组件中移除容器事件监听器.
	 *
	 * @param listener - 要删除的监听器
	 */
	public void removeContainerListener(ContainerListener listener);


	/**
	 * 通知所有容器事件监听器该容器发生了特定的事件。默认实现使用调用线程同步执行此通知.
	 * @param type - 事件类型
	 * @param data - 事件数据
	 */
	public void fireContainerEvent(String type, Object data);


	/**
	 * 记录一个发送到这个容器的请求/响应，但是在处理链中被更早地处理，这样请求/响应仍然出现在正确的访问日志中.
	 * @param request - 请求(与响应关联)到日志
	 * @param httpResponse - 到日志的响应(与请求关联)
	 * @param time - 处理请求/响应所花费的时间(以毫秒为单位)(如果不知道就使用0
	 * @param useDefault - 指示应将请求/响应记录在引擎的默认访问日志中的标志
	 */
//	public void logAccess(Request request, Response httpResponse, long time, boolean useDefault);


	/**
	 * 获取AccessLog，用于记录目标为该容器的请求/响应。
	 * 当请求/响应在处理链的早期被处理(和被拒绝)时，通常使用这种方法，以便请求/响应仍然出现在正确的访问日志中.
	 * @return 用于发送到该容器的请求/响应的AccessLog
	 */
//	public AccessLog getAccessLog();


	/**
	 * 获取用于启动和停止与此容器关联的任何子容器的可用线程数。这允许并行处理对子进程的启动/停止调用.
	 *
	 * @return 用于启动/停止与该容器关联的子容器的当前配置的线程数
	 */
	public int getStartStopThreads();

	/**
	 * 设置用于启动和停止与此容器关联的任何子容器的可用线程数。这允许并行处理对子进程的启动/停止调用.
	 * @param startStopThreads - 要使用的新线程数
	 */
	public void setStartStopThreads(int startStopThreads);

	
	/**
	 * 获取 WEB_APPLICATION_BASE 的位置.
	 */
	public File getMoonBase();

	
	/**
	 * 获取 WEB_APPLICATION_HOME 的位置
	 */
	public File getMoonHome();
}
