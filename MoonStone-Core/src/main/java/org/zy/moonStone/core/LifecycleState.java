package org.zy.moonStone.core;

import org.zy.moonStone.core.interfaces.container.Lifecycle;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 实现生命周期的组件的有效状态列表。有关状态转换图，请参见生命周期。
 */
public enum LifecycleState {
	/** 生命周期初始态 */
	NEW(false, null),
	/** 组件初始化之前 */
    INITIALIZING(false, Lifecycle.BEFORE_INIT_EVENT),
    /** 组件初始化之后 */
    INITIALIZED(false, Lifecycle.AFTER_INIT_EVENT),
    /** 组件启动前 */
    STARTING_PREP(false, Lifecycle.BEFORE_START_EVENT),
    /** 组件启动中 */
    STARTING(true, Lifecycle.START_EVENT),
    /** 组件启动后 */
    STARTED(true, Lifecycle.AFTER_START_EVENT),
    /** 组件停止前 */
    STOPPING_PREP(true, Lifecycle.BEFORE_STOP_EVENT),
    /** 组件停止中 */
    STOPPING(false, Lifecycle.STOP_EVENT),
    /** 组件停止后 */
    STOPPED(false, Lifecycle.AFTER_STOP_EVENT),
    /** 组件销毁前 */
    DESTROYING(false, Lifecycle.BEFORE_DESTROY_EVENT),
    /** 组件销毁后 */
    DESTROYED(false, Lifecycle.AFTER_DESTROY_EVENT),
    /** 异常 */
    FAILED(false, null);

	/**
	 * 可用状态
	 */
    private final boolean available;
    /**
     * 生命周期事件类型
     */
    private final String lifecycleEvent;
    /**
     * 生命周期事件描述
     */
//    private final String lifecycDescribe;

	private LifecycleState(boolean available, String lifecycleEvent/* , String lifecycDescribe */) {
        this.available = available;
        this.lifecycleEvent = lifecycleEvent;
//        this.lifecycDescribe = lifecycDescribe;
    }

    /**
     * 在这种状态下，组件可以调用除了属性getter /setter和生命周期方法之外的其他公共方法吗?对于处于以下状态的任何组件，它都返回true:
     * 
     * <ul>
     * <li>{@link #STARTING}</li>
     * <li>{@link #STARTED}</li>
     * <li>{@link #STOPPING_PREP}</li>
     * </ul>
     *
     * @return 如果组件可用，则为True，否则为false
     */
    public boolean isAvailable() {
        return available;
    }

    public String getLifecycleEvent() {
        return lifecycleEvent;
    }
    
    @Override
    public String toString() {
//    	return String.format("%s[available: %s, lifecycDescribe: %s]", super.toString(), available, lifecycDescribe);
    	return super.name();
    }
}
