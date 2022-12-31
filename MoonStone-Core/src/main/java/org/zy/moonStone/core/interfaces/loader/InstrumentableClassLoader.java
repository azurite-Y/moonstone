package org.zy.moonStone.core.interfaces.loader;

import java.lang.instrument.ClassFileTransformer;

/**
 * @dateTime 2022年8月22日;
 * @author zy(azurite-Y);
 * @description
 * 
 * 指定一个能够用 ClassFileTransformers 修饰的类加载器。 这些转换器可以检测（或编织）通过这个类加载器加载的类的字节码来改变它们的行为。 
 * 目前只有 org.apache.catalina.loader.WebappClassLoaderBase 实现了这个接口。 这允许与 Web 应用程序捆绑在一起的 Web 应用程序框架或 JPA 提供程序在必要时检测 Web 应用程序类。
 * <p>
 * 应该始终针对此接口的方法进行编程（无论是使用反射还是其他方式）。 WebappClassLoaderBase 中的方法受默认安全管理器保护（如果正在使用）。
 */
public interface InstrumentableClassLoader {

	/**
	 * 将指定的类文件转换器添加到此类加载器。 然后，在调用此方法后，转换器将能够检测由此类加载器加载的任何类的字节码。
     *
     * @param transformer - 添加到类加载器的转换器
     * @throws IllegalArgumentException - 如果 {@literal transformer} 为 null。
     */
    void addTransformer(ClassFileTransformer transformer);

    /**
     * 从这个类加载器中移除指定的类文件转换器。在调用此方法之后，它将不再能够修改任何类加载器加载的类的字节码。
     * 但是，任何已经被这个转换器修改过的类都将保持转换状态。
     *
     * @param transformer - 要移除的 transformer
     */
    void removeTransformer(ClassFileTransformer transformer);

    /**
     * 返回没有任何类文件转换器的此类加载器的副本。这是Java持久性API提供程序经常使用的一个工具，用于在没有任何插装的情况下检查实体类，
     * 而这在 {@link ClassFileTransformer} 的 {@link ClassFileTransformer#transform(ClassLoader, String, Class,java.security.ProtectionDomain, byte[]) transform} 方法的上下文中是不能保证的。
     * <p>
     * 返回的类装入器的资源缓存将被清除，因此已经插装的类将不会被保留或返回。
     *
     * @return 这个类装入器的无转换器副本
     */
    ClassLoader copyWithoutTransformers();
}
