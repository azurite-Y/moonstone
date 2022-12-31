package org.zy.moonStone.core.security;

import java.security.PrivilegedAction;

/**
 * @dateTime 2022年9月22日;
 * @author zy(azurite-Y);
 * @description
 */
public class PrivilegedContextClassLoaderSetter implements PrivilegedAction<Void> {
    private final ClassLoader cl;
    private final Thread t;

    public PrivilegedContextClassLoaderSetter(ClassLoader cl) {
        this(Thread.currentThread(), cl);
    }

    public PrivilegedContextClassLoaderSetter(Thread t, ClassLoader cl) {
        this.t = t;
        this.cl = cl;
    }

    @Override
    public Void run() {
        t.setContextClassLoader(cl);
        return null;
    }
}
