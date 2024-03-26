package org.zy.moonstone.core.interfaces.container;

import org.zy.moonstone.core.interfaces.loader.ThreadBindingListener;

/**
 * @dateTime 2022年9月21日;
 * @author zy(azurite-Y);
 * @description ThreadBindingListener
 */
public interface ContextBind {
	/**
	 * 将当前线程上下文类加载器更改为Web应用程序类加载器。如果未定义Web应用程序类加载器，或者如果当前线程已经在使用Web应用程序类加载器，则不会进行任何更改。
	 * 如果更改了类加载器并配置了{@link ThreadBindingListener }，则在进行更改后将调用 {@link ThreadBindingListener#bind() }
	 * 
     * @param usePrivilegedAction - 在获取当前线程上下文类加载器并设置新的加载器时，是否应该使用 {@link java.security.PrivilegedAction}
     * @param originalClassLoader
     * @return 如果类加载器已被该方法更改，它将返回调用该方法时正在使用的线程上下文类加载器。如果未进行任何更改，则此方法返回NULL。
     */
    ClassLoader bind(boolean usePrivilegedAction, ClassLoader originalClassLoader);

    /**
     * 将当前线程上下文类加载器恢复到调用 {@link #bind(boolean, ClassLoader)} 之前使用的原始类加载器。如果没有将原始类加载器传递给此方法，则不会进行任何更改。
     * 如果更改了类加载器并配置了 {@link ThreadBindingListener}，则在进行更改之前将调用 {@link ThreadBindingListener#unbind()}。
     * 
     * @param usePrivilegedAction - 在设置当前线程上下文类加载器时，是否应该使用 {@link java.security.PrivilegedAction}
     * @param originalClassLoader - 要恢复为线程上下文类加载器的类加载器
     */
    void unbind(boolean usePrivilegedAction, ClassLoader originalClassLoader);
}
