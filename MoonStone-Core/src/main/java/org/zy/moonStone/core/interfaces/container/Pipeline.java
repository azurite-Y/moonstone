package org.zy.moonStone.core.interfaces.container;

import java.util.Set;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * 接口，描述了当调用invoke()方法时应该按顺序执行的一组阀门。
 * 这要求阀门在管道中的某处(通常是最后一个)必须处理请求并创建相应的响应，而不是试图将请求传递下去。
 * <p>
 * 通常每个容器都有一个单独的Pipeline实例。容器的正常请求处理功能通常封装在容器特定的阀门中，它应该总是在管道的末端执行。
 * 为了实现这一点，提供了setBasic()方法来设置总是最后执行的Valve实例。在基本的阀门被执行之前，其他阀门将按照它们被添加的顺序执行。
 */
public interface Pipeline extends Contained {
	/**
	 * @return 这个阀门实例已经被区分为这个管道的基本阀门(如果有的话).
	 */
	public Valve getBasic();


	/**
	 * 设置已经被区分为这个管道的basicValve实例(如果有的话)。
	 * 在设置基本的Valve之前，如果实现包含，Valve的setContainer()将被调用，并以其所属的Container作为参数。
	 * 如果这个阀门选择不与这个容器关联，这个方法可能抛出一个illegalargumentexception，或者如果它已经与另一个容器关联，则抛出一个IllegalStateException
	 *
	 * @param valve - 要区分为基本valve的valve
	 */
	public void setBasic(Valve valve);


	/**
	 * 在与此容器关联的管道末端添加一个新的Valve。
	 * 在添加Valve之前，如果Valve的setContainer()方法实现了Contained，那么它将以所属Container作为参数被调用。
	 * 如果这个阀门选择不与这个容器关联，这个方法可能抛出一个IllegalArgumentException，或者如果它已经与另一个容器关联，则抛出一个illegalstateexception
	 * </p>
	 * 实现注意:实现预期会触发容器。如果调用成功，为关联容器添加一个ADD_VALVE_EVENT
	 * 
	 * @param valve 增加的Valve
	 * @exception IllegalArgumentException - 如果该容器拒绝接受指定的Valve
	 * @exception IllegalArgumentException - 如果指定的阀门拒绝与此容器相关联
	 * @exception IllegalStateException - 如果指定的阀门已经与一个不同的容器相关联
	 */
	public void addValve(Valve valve);


	/**
	 * @return 与此容器相关联的管道中的阀门集，包括基本阀门(如果有)。如果没有这样的阀门，则返回一个零长度阵列。.
	 */
	public Valve[] getValves();


	/**
	 * 从与此容器相关的管道上移除指定的阀门(如果找到的话);否则,什么都不做。
	 * 如果Valve被找到并移除，如果它实现了Contained，那么它的setContainer(null)方法将被调用。.
	 * <p>
	 * 实现注意:实现预期会触发容器。如果调用成功，则为关联容器REMOVE_VALVE_EVENT
	 */
	public void removeValve(Valve valve);


	/**
	 * 获取首位Valve，若为null则返回基础的Valve
	 * @return 这个阀门实例已经被区分为这个管道的基本阀门(如果有的话).
	 */
	public Valve getFirst();


	/**
	 * 如果该管道中的所有阀门都支持异步，则返回true，否则返回false
	 * @return 如果该管道中的所有阀门都支持异步，则为True，否则为false
	 */
	public boolean isAsyncSupported();


	/**
	 * 标识该管道中不支持异步的阀门(如果有).
	 *
	 * @param result 此管道中不支持async的每个Valve的完全限定类名添加到的集合
	 */
	public void findNonAsyncValves(Set<String> result);
}
