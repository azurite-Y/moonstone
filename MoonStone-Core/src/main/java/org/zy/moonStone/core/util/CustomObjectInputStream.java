package org.zy.moonStone.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;

/**
 * @dateTime 2022年8月12日;
 * @author zy(azurite-Y);
 * @description 从该 Web 应用程序的类加载器加载的 ObjectInputStream 的自定义子类。 这允许正确找到仅使用 Web应用程序定义的类。
 */
public class CustomObjectInputStream extends ObjectInputStream {
	private static final WeakHashMap<ClassLoader, Set<String>> reportedClassCache = new WeakHashMap<>();

	private final Logger logger;
	/** 将用来解析类的类加载器 */
	private final ClassLoader classLoader;
	/** 已报告的类 */
	private final Set<String> reportedClasses;

	/** 允许的类名模式 */
	private final Pattern allowedClassNamePattern;
	/** 允许的类名过滤器 */
	private final String allowedClassNameFilter;
	/** 失败时发出警告 */
	private final boolean warnOnFailure;

	/**
	 * 构造一个新的CustomObjectInputStream实例，而不对反序列化类进行任何筛选。
	 *
	 * @param stream - 将从中读取的输入流
	 * @param classLoader - 用于实例化对象的类加载器
	 * @exception IOException - 如果发生输入/输出错误
	 */
	public CustomObjectInputStream(InputStream stream, ClassLoader classLoader) throws IOException {
		this(stream, classLoader, null, null, false);
	}

	/**
	 * 使用反序列化类的过滤器构造一个新的 CustomObjectInputStream 实例。
	 *
	 * @param stream - 将从中读取的输入流
	 * @param classLoader - 用于实例化对象的类加载器
	 * @param logger - 用于报告任何问题的记录器。 如果 filterMode 不需要记录，它可能只为 null
	 * @param allowedClassNamePattern - 用于过滤反序列化类的正则表达式。 如果启用过滤，则完全限定的类名称必须与此模式匹配才能允许反序列化。
	 * @param warnOnFailure - 是否应该记录任何故障？
	 * @exception IOException - 如果发生输入/输出错误
	 */
	public CustomObjectInputStream(InputStream stream, ClassLoader classLoader, Logger logger, Pattern allowedClassNamePattern, boolean warnOnFailure) throws IOException {
		super(stream);
		if (logger == null && allowedClassNamePattern != null && warnOnFailure) {
			throw new IllegalArgumentException("日志对象不能为 null");
		}
		this.classLoader = classLoader;
		this.logger = logger;
		
		this.allowedClassNamePattern = allowedClassNamePattern;
		if (allowedClassNamePattern == null) {
			this.allowedClassNameFilter = null;
		} else {
			this.allowedClassNameFilter = allowedClassNamePattern.toString();
		}
		this.warnOnFailure = warnOnFailure;

		Set<String> reportedClasses;
		synchronized (reportedClassCache) {
			reportedClasses = reportedClassCache.get(classLoader);
		}
		if (reportedClasses == null) {
			reportedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
			Set<String> original;
			synchronized (reportedClassCache) {
				original = reportedClassCache.putIfAbsent(classLoader, reportedClasses);
			}
			if (original != null) {
				// 并发尝试创建新 Set。 确保所有线程都使用第一个成功添加的 Set。
				reportedClasses = original;
			}
		}
		this.reportedClasses = reportedClasses;
	}

	/**
	 * 使用分配给此上下文的类加载器加载指定流类描述的本地类等效项
	 *
	 * @param classDesc - 来自输入流的类描述
	 * @exception ClassNotFoundException - 如果找不到此类
	 * @exception IOException - 如果发生输入/输出错误
	 */
	@Override
	public Class<?> resolveClass(ObjectStreamClass classDesc) throws ClassNotFoundException, IOException {
		String name = classDesc.getName();
		if (allowedClassNamePattern != null) {
			boolean allowed = allowedClassNamePattern.matcher(name).matches();
			if (!allowed) {
				boolean doLog = warnOnFailure && reportedClasses.add(name);
				String msg = "与过滤反序列化类的正则表达式无法匹配，by pattern：'" + allowedClassNameFilter + "'，name：'" + name +"'";
				if (doLog) {
					logger.warn(msg);
				} else if (logger.isDebugEnabled()) {
					logger.debug(msg);
				}
				throw new InvalidClassException(msg);
			}
		}

		try {
			return Class.forName(name, false, classLoader);
		} catch (ClassNotFoundException e) {
			try {
				// 还可以尝试使用超类，因为它是基元类型
				return super.resolveClass(classDesc);
			} catch (ClassNotFoundException e2) {
				// 重新引发原始异常，因为它可以包含有关找不到类的原因的更多信息。BZ 48007
				throw e;
			}
		}
	}

	/**
	 * 返回实现代理类描述符中命名的接口的代理类。使用分配给此上下文的类加载器来实现这一点。
	 * 
	 * @param interfaces  - 在代理类描述符中反序列化的接口名称列表
	 * @return 指定接口的代理类
	 * @throws IOException - 底层 InputStream 抛出的任何异常
	 * @throws ClassNotFoundException - 如果找不到代理类或任何命名接口
	 */
	@Override
	protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
		Class<?>[] cinterfaces = new Class[interfaces.length];
		for (int i = 0; i < interfaces.length; i++) {
			cinterfaces[i] = classLoader.loadClass(interfaces[i]);
		}

		try {
			// @SuppressWarnings("deprecation") Java 9
			Class<?> proxyClass = Proxy.getProxyClass(classLoader, cinterfaces);
			return proxyClass;
		} catch (IllegalArgumentException e) {
			throw new ClassNotFoundException(null, e);
		}
	}
}
