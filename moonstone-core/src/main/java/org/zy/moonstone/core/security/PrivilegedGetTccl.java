package org.zy.moonstone.core.security;

import java.security.PrivilegedAction;

/**
 * @dateTime 2022年12月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class PrivilegedGetTccl implements PrivilegedAction<ClassLoader> {
    @Override
    public ClassLoader run() {
    	/**
    	 * 返回此线程的上下文ClassLoader。上下文ClassLoader由线程的创建者提供，以在加载类和资源时在该线程中运行的代码使用。
    	 * 如果没有设置，默认是父线程的classloader上下文。原始线程的上下文ClassLoader通常设置为用于加载应用程序的类加载器。
    	 * 
    	 * 如果存在安全管理器，并且调用者的类加载器不是空的，并且与上下文类加载器不相同或不属于上下文类加载器的祖先，
    	 * 那么该方法调用安全管理器的checkpermission方法，并使用RuntimePermission("getClassLoader")权限来验证上下文类装入器的检索是否被允许。
    	 * 
    	 * @return 此线程的上下文ClassLoader，或指定系统类加载器(或引导类加载器)为空
    	 * @throw SecurityException - 如果当前线程无法获取上下文ClassLoader
    	 */
        return Thread.currentThread().getContextClassLoader();
    }
}
