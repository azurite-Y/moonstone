package org.zy.moonStone.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.zy.moonStone.core.interfaces.InstanceManager;

/**
 * @dateTime 2022年9月22日;
 * @author zy(azurite-Y);
 * @description
 */
public final class InstanceManagerBindings {
    private static final Map<ClassLoader, InstanceManager> bindings = new ConcurrentHashMap<>();

    public static final void bind(ClassLoader classLoader, InstanceManager instanceManager) {
        bindings.put(classLoader, instanceManager);
    }
    public static final void unbind(ClassLoader classLoader) {
        bindings.remove(classLoader);
    }
    public static final InstanceManager get(ClassLoader classLoader) {
        return bindings.get(classLoader);
    }
}
