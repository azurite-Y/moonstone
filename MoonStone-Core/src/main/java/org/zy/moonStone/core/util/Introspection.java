package org.zy.moonstone.core.util;

import org.slf4j.Logger;
import org.zy.moonstone.core.interfaces.container.Context;

/**
 * @dateTime 2022年9月28日;
 * @author zy(azurite-Y);
 * @description
 */
public class Introspection {
	
	/**
	 * 尝试使用给定容器的类加载器加载类。如果无法加载该类，则会在容器的日志中写入调试级别日志消息，并返回null。
	 * 
	 * @param context - 将用于尝试加载类的此上下文类加载器
	 * @param className - 类名
	 * @return 加载的类，如果加载失败则为<code>null</code>
	 */
	public static Class<?> loadClass(Context context, String className) {
		ClassLoader cl = context.getLoader().getClassLoader();
		Logger logger = context.getLogger();
		Class<?> clazz = null;
		try {
			clazz = cl.loadClass(className);
		} catch (ClassNotFoundException | NoClassDefFoundError | ClassFormatError e) {
			logger.debug("加载类失败, by class: " + className, e);
		} catch (Throwable t) {
			ExceptionUtils.handleThrowable(t);
			logger.debug("加载类失败, by class: " + className, t);
		}
		return clazz;
	}
}
