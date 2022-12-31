package org.zy.moonStone.core.util;


import org.zy.moonStone.core.interfaces.InstanceManager;

/**
 * @dateTime 2022年2月22日;
 * @author zy(azurite-Y);
 * @description
 */
public class SimpleInstanceManager implements InstanceManager {
    @Override
    public Object newInstance(Class<?> clazz) throws ReflectiveOperationException, SecurityException {
        return prepareInstance(clazz.getConstructor().newInstance());
    }

    @Override
    public Object newInstance(String className) throws ReflectiveOperationException, SecurityException  {
//        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
        Class<?> clazz = Class.forName(className);
        return prepareInstance(clazz.getConstructor().newInstance());
    }

    @Override
    public Object newInstance(String fqcn, ClassLoader classLoader) throws ReflectiveOperationException, SecurityException  {
        Class<?> clazz = classLoader.loadClass(fqcn);
        return prepareInstance(clazz.getConstructor().newInstance());
    }
}
