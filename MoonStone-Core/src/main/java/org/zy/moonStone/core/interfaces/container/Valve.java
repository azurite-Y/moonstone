package org.zy.moonStone.core.interfaces.container;

import java.io.IOException;

import javax.servlet.ServletException;

import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.connector.HttpResponse;


/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 */
public interface Valve {
	// ----------------------------------------------------- 屬性 -----------------------------------------------------
	/**
	 * @return 管道中的下一个阀门(如果有的话).
	 */
	Valve getNext();


	/**
	 * 在包含该Valve的管道中设置下一个Valve.
	 *
	 * @param valve - 下一个新的阀门，如果没有则为空
	 */
	void setNext(Valve valve);


	// ----------------------------------------------------- 公共方法 -----------------------------------------------------
	/**
	 * 执行周期性任务，如重新加载等。此方法将在该容器的类加载上下文中调用。意外throwables将被捕获并记录.
	 */
	void backgroundProcess();


	/**
	 * <p>按照本阀门的要求执行请求处理.</p>
	 *
	 * 单个阀门可以按照指定的顺序执行以下操作:
	 * <ul>
	 * <li>检查和/或修改指定的请求和响应的属性.
	 * <li>检查指定请求的属性，完全生成相应的响应，并将控制权返回给调用者.
	 * <li>检查指定的请求和响应的属性，包装其中一个或两个对象，以补充它们的功能，并传递它们.
	 * <li>如果相应的响应没有生成(并且控制没有返回)，通过执行getNext().invoke()调用管道中的下一个Valve(如果有的话).
	 * <li>检查，但不修改，结果响应的属性(由随后调用的阀门或容器创建).
	 * </ul>
	 *
	 * <p>Valve不能做以下任何事情:</p>
	 * <ul>
	 * <li>更改已经被用于指导处理控制的请求流的请求属性(例如，在标准实现中，尝试更改请求应该从连接到主机或上下文的管道发送到的虚拟主机).
	 * <li>创建一个完整的响应，并将此请求和响应传递给管道中的下一个Valve.
	 * <li>从与请求相关的输入流中消耗字节数，除非它完全生成了响应，或者在传递请求之前包装了请求.
	 * <li>在getNext().invoke()方法返回后，修改响应中包含的HTTP报头.
	 * <li>在getNext().invoke()方法返回后，对与指定的Response关联的输出流执行任何操作.
	 * </ul>
	 * @param httpRequest - 要处理的servlet请求
	 * @param httpResponse - 要创建的servlet响应
	 * @exception IOException - 如果输入/输出错误发生，或者由随后调用的阀门、过滤器或Servlet抛出
	 * @exception ServletException - 如果发生servlet错误，或者被随后调用的阀门、过滤器或servlet抛出
	 */
	void invoke(HttpRequest request, HttpResponse response) throws IOException, ServletException;

	boolean isAsyncSupported();
}
