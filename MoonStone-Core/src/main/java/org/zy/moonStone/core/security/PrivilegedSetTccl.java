package org.zy.moonstone.core.security;

import java.security.PrivilegedAction;

/**
 * @dateTime 2022年12月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class PrivilegedSetTccl implements PrivilegedAction<Void> {
	private final ClassLoader cl;
    private final Thread t;

    public PrivilegedSetTccl(ClassLoader cl) {
        this(Thread.currentThread(), cl);
    }

    public PrivilegedSetTccl(Thread t, ClassLoader cl) {
        this.t = t;
        this.cl = cl;
    }


    @Override
    public Void run() {
    	/**
    	 * 设置此线程的上下文ClassLoader。contextClassLoader可以在创建线程时设置，并允许线程的创建者通过getContextClassLoader为加载类和资源时在线程中运行的代码提供适当的类加载器。
    	 * 
    	 * 如果存在安全管理器，则使用RuntimePermission(“setContextClassLoader”)权限调用其checkPermission方法，以查看是否允许设置上下文ClassLoader。
    	 */
        t.setContextClassLoader(cl);
        return null;
    }
}
