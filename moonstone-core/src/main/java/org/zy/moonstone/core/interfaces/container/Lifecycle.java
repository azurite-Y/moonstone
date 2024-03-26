package org.zy.moonstone.core.interfaces.container;

import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 组件生命周期方法的通用接口。组件可以实现此接口（以及它们支持的功能的适当接口），以便提供一致的机制来启动和停止组件。支持Lifecycle的组件的有效状态转换
 */
public interface Lifecycle {
	/**
	 * “组件初始化之前”事件的LifecycleEvent类型.
	 */
	public static final String BEFORE_INIT_EVENT = "before_init";


	/**
	 * “组件初始化后”事件的LifecycleEvent类型.
	 */
	public static final String AFTER_INIT_EVENT = "after_init";


	/**
	 * “组件启动”事件的LifecycleEvent类型.
	 */
	public static final String START_EVENT = "start";


	/**
	 * “组件启动前”事件的LifecycleEvent类型.
	 */
	public static final String BEFORE_START_EVENT = "before_start";


	/**
	 * “组件启动后”事件的LifecycleEvent类型.
	 */
	public static final String AFTER_START_EVENT = "after_start";


	/**
	 * “组件停止”事件的LifecycleEvent类型.
	 */
	public static final String STOP_EVENT = "stop";


	/**
	 * “停止之前的组件”事件的LifecycleEvent类型.
	 */
	public static final String BEFORE_STOP_EVENT = "before_stop";


	/**
	 * “停止后的组件”事件的LifecycleEvent类型.
	 */
	public static final String AFTER_STOP_EVENT = "after_stop";


	/**
	 * “组件销毁后”事件的LifecycleEvent类型.
	 */
	public static final String AFTER_DESTROY_EVENT = "after_destroy";


	/**
	 * “组件销毁前”事件的LifecycleEvent类型.
	 */
	public static final String BEFORE_DESTROY_EVENT = "before_destroy";


	/**
	 * “周期性”事件的LifecycleEvent类型.
	 */
	public static final String PERIODIC_EVENT = "periodic";


	/**
	 * configure_start事件的LifecycleEvent类型。这些组件使用单独的组件来执行配置，并且需要在应该执行配置时发出信号
	 * --通常在{@link #BEFORE_START_EVENT}之后和{@link #START_EVENT}之前
	 */
	public static final String CONFIGURE_START_EVENT = "configure_start";


	/**
	 * configure_stop事件的LifecycleEvent类型。这些组件使用一个单独的组件来执行配置，并且需要在应该执行反配置时发出信号
	 * --通常在{@link #STOP_EVENT}之后和{@link #AFTER_STOP_EVENT}之前。
	 */
	public static final String CONFIGURE_STOP_EVENT = "configure_stop";


	/**
	 * 向该组件添加一个LifecycleEvent监听器.
	 * 
	 * @param listener - 添加的LifecycleEvent监听器
	 */
	public void addLifecycleListener(LifecycleListener listener);


	/**
	 * 获取与此生命周期关联的生命周期侦听器.
	 * 
	 * @return 包含与此生命周期关联的生命周期监听器的数组。如果此组件没有注册侦听器，则返回一个零长度的数组.
	 */
	public LifecycleListener[] findLifecycleListeners();


	/**
	 * 从该组件中移除LifecycleEvent监听器.
	 */
	public void removeLifecycleListener(LifecycleListener listener);


	/**
	 * 准备启动组件。此方法应执行创建对象后所需的任何初始化。以下的LifecycleEvents将按照以下顺序被触发:
	 * <ol>
	 *   <li>INIT_EVENT: 成功完成组件初始化.</li>
	 * </ol>
	 * 
	 * @exception LifecycleException - 如果该组件检测到致命错误，导致该组件无法使用
	 */
	public void init() throws LifecycleException;

	/**
	 * 准备好开始主动使用除属性getter /setter和此组件的生命周期方法之外的公共方法。
	 * 在使用组件的属性getter /setter和生命周期方法之外的任何公共方法之前，应该调用此方法。
	 * 以下的LifecycleEvents将按照以下顺序被触发::
	 * <ol>
	 *   <li>BEFORE_START_EVENT: 在方法的开头。就在此时，状态转换到{@link LifecycleState#STARTING_PREP}.</li>
	 *   <li>START_EVENT: 在方法期间，一旦可以安全调用任何子组件的start()。在这一点上，状态转换到LifecycleState。
	 *       并且，除了属性getter/setter和生命周期方法之外，还可以使用其他公共方法.</li>
	 *   <li>AFTER_START_EVENT: 在方法的末尾，在它返回之前。正是在这一点上，状态转换到{@link LifecycleState#STARTED}.</li>
	 * </ol>
	 *
	 * @exception LifecycleException - 如果该组件检测到致命错误，导致该组件无法使用
	 */
	public void start() throws LifecycleException;


	/**
	 * 优雅地终止除属性getter /setter和此组件的生命周期方法之外的公共方法的主动使用。
	 * 一旦STOP_EVENT被触发，除了属性getter /setter和生命周期方法之外的公共方法就不应该被使用。
	 * 以下的LifecycleEvents将按照以下顺序被触发:
	 * <ol>
	 *   <li>BEFORE_STOP_EVENT: 在方法的开头。此时状态转换到{@link LifecycleState#STOPPING_PREP}.</li>
	 *   <li>STOP_EVENT: 在方法执行期间，一旦可以安全调用任何子组件的stop()。
	 *       在这一点上，状态转换到LifecycleState。停止，并且除了属性getter /setter和生命周期方法之外的公共方法可能不再被使用.</li>
	 *   <li>AFTER_STOP_EVENT: 在方法的末尾，在它返回之前。正是在这一点上，状态转换到{@link LifecycleState#STOPPED}.</li>
	 * </ol>
	 *
	 * 注意，如果从LifecycleState过渡。如果失败，那么上面的三个事件将被触发，但是组件将直接从LifecycleState过渡。未能LifecycleState。停止,绕过LifecycleState。STOPPING_PREP
	 *
	 * @exception LifecycleException - 如果该组件检测到致命错误，导致该组件无法使用
	 */
	public void stop() throws LifecycleException;

	/**
	 * 准备丢弃对象。以下的LifecycleEvents将按照以下顺序被触发:
	 * <ol>
	 *   <li>DESTROY_EVENT: 成功完成组件销毁.</li>
	 * </ol>
	 *
	 * @exception LifecycleException - 如果该组件检测到致命错误，导致该组件无法使用
	 */
	public void destroy() throws LifecycleException;


	/**
	 * 获取源组件的当前状态.
	 * @return 源组件的当前状态.
	 */
	public LifecycleState getState();


	/**
	 * 获取当前组件状态的文本表示。这个字符串的格式可能会在不同的点发布中有所不同，不应该依赖于它来确定组件的状态。
	 * 要确定组件的状态，使用getState().
	 *
	 * @return 当前组件状态的名称.
	 */
	public String getStateName();


	/**
	 * 标记接口，用于指示该实例只能使用一次。在支持该接口的实例上调用stop()将在stop()完成后自动调用destroy().
	 */
	public interface SingleUse {
	}
}
