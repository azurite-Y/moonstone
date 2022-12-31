package org.zy.moonStone.core.interfaces;

/**
 * @dateTime 2022年2月22日;
 * @author zy(azurite-Y);
 * @description
 */
public interface InstanceManager {
	Object newInstance(Class<?> clazz) throws ReflectiveOperationException;

	Object newInstance(String className) throws ReflectiveOperationException;
	
	Object newInstance(String fqcn, ClassLoader classLoader) throws ReflectiveOperationException;
	
    default Object prepareInstance(Object o) {
        return o;
    }
    
    /**
     * 由使用 InstanceManager 的组件定期调用，以执行可能需要的任何定期维护
     */
    default void backgroundProcess() {
    }
}
