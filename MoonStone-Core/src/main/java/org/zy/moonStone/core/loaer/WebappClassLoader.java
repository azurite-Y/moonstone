package org.zy.moonstone.core.loaer;

import org.zy.moonstone.core.exceptions.LifecycleException;

/**
 * @dateTime 2022年8月25日;
 * @author zy(azurite-Y);
 * @description
 */
public class WebappClassLoader extends WebappClassLoaderBase {
	public WebappClassLoader() {
        super();
    }
    public WebappClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public WebappClassLoader copyWithoutTransformers() {
        WebappClassLoader result = new WebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }

    /**
     * 这个类加载器没有并行能力，所以锁定类加载器而不是每个类的锁。
     * 
     * @param className - 待加载类的名称
     * @return 类加载操作的锁
     */
    @Override
    protected Object getClassLoadingLock(String className) {
        return this;
    }
}
