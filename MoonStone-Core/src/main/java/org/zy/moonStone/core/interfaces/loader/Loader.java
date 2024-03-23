package org.zy.moonstone.core.interfaces.loader;

import org.zy.moonstone.core.interfaces.container.Context;

/**
 * @dateTime 2022年8月22日;
 * @author zy(azurite-Y);
 * @description
 * Loader 表示 Java ClassLoader 实现，Container 可以使用该实现来加载类文件(在与 Loader 关联的存储库中),  
 * 这些文件被设计为根据请求重新加载, 以及一种检测底层存储库中是否发生更改的机制。
 * 
 * 为了让 Loader 实现与实现重新加载的 Context 实现一起成功运行，它必须遵守以下约束：
 * <ul>
 * <li>必须实现生命周期，以便上下文可以指示需要新的<code>ClassLoader</code>.</li>
 * <li><code>start()</code> 方法必须无条件地创建一个新的ClassLoader 实现.</li>
 * <li><code>stop()</code> 方法必须丢弃它对先前使用的ClassLoader 的引用，以便类加载器、由它加载的所有类以及这些类的所有对象都可以进行垃圾收集.</li>
 * <li>必须允许在同一个Loader 实例上调用<code>stop()</code> 之后调用<code>start()</code>.</li>
 * <li>根据实现选择的策略，当检测到由该类加载器加载的一个或多个类文件发生变化时，必须调用所属上下文的 <code>Context.reload()</code> 方法.</li>
 * </ul>
 */
public interface Loader {
	/**
     * 执行周期性任务，如重新加载等。这个方法将在容器的类加载上下文中调用。 意料之外的异常将被捕获并记录。
     */
    public void backgroundProcess();


    /**
     * @return 此 Container 要使用的 Java 类加载器
     */
    public ClassLoader getClassLoader();


    /**
     * @return 与此 Loader 关联的 Context
     */
    public Context getContext();


    /**
     * 设置与此 Loader 关联的 Context
     *
     * @param context - 关联的 Context
     */
    public void setContext(Context context);


    /**
     * @return 用于配置 ClassLoader 的“遵循标准委托模型”标志
     */
    public boolean getDelegate();


    /**
     * 用于配置 ClassLoader 的“遵循标准委托模型”标志。
     *
     * @param delegate - 新的标识
     */
    public void setDelegate(boolean delegate);


    /**
     * 向该组件添加 {@link PropertyChangeListener }
     *
     * @param listener - 添加的监听器
     */
//    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 移除当前组件的 {@link PropertyChangeListener }
     *
     * @param listener - 移除的监听器
     */
//    public void removePropertyChangeListener(PropertyChangeListener listener);
    
    
    /**
     * 是否修改了与此 Loader 关联的内部存储库，从而应该重新加载加载的类？
     * 
     * @return 当存储库被修改时为 <code>true</code>，否则为 <code>false</code> 
     */
    public boolean modified();

}
