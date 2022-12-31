package org.zy.moonStone.core.security;

import java.security.PrivilegedAction;

/**
 * @dateTime 2022年9月22日;
 * @author zy(azurite-Y);
 * @description
 */
public class PrivilegedContextClassLoaderGetter implements PrivilegedAction<ClassLoader> {
    @Override
    public ClassLoader run() {
        return Thread.currentThread().getContextClassLoader();
    }
}
