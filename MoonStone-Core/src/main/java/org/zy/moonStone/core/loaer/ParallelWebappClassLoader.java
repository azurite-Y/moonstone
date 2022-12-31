package org.zy.moonStone.core.loaer;

import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年8月25日;
 * @author zy(azurite-Y);
 * @description
 */
public class ParallelWebappClassLoader extends WebappClassLoaderBase {

    static {
        if (!JreCompat.isGraalAvailable()) {
        	/**
        	 * ClassLoader.registerAsParallelCapable():
        	 * 
        	 * 将调用者注册为具有并行能力。当且仅当满足以下所有条件时，注册成功：
        	 *   1.没有创建调用者的实例
        	 *   2.调用者的所有超类（对象类除外）都注册为具有并行能力
        	 * 
        	 * 请注意，一旦类加载器注册为具有并行能力，就无法将其更改回来。
        	 * 
        	 * @return 如果调用者成功注册了并行能力，则为 true，否则为 false。
        	 * 
        	 * NOTE: 如果注册了ClassLoader为并行加载，则loadClass的时候，锁的粒度是className，否则锁的粒度是ClassLoader实例本身this
        	 */
            if (!ClassLoader.registerAsParallelCapable()) {
            	LoggerFactory.getLogger(ParallelWebappClassLoader.class).warn("ParallelWebappClassLoader 注册失败");
            }
        }
    }

    public ParallelWebappClassLoader() {
        super();
    }
    public ParallelWebappClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public ParallelWebappClassLoader copyWithoutTransformers() {
        ParallelWebappClassLoader result = new ParallelWebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }
}
