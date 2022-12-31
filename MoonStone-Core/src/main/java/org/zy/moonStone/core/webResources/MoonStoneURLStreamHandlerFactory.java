package org.zy.moonStone.core.webResources;

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.zy.moonStone.core.webResources.war.Handler;

/**
 * @dateTime 2022年9月2日;
 * @author zy(azurite-Y);
 * @description
 */
public class MoonStoneURLStreamHandlerFactory implements URLStreamHandlerFactory {
	private static final String WAR_PROTOCOL = "war";
	private static final String CLASSPATH_PROTOCOL = "classpath";

	/** 当前 {@link MoonStoneURLStreamHandlerFactory}  是否已注册到 Java 虚拟机 */
	private final boolean registered;
	
	/** 单利实例 */
	private static volatile MoonStoneURLStreamHandlerFactory instance = null;

	/** 应用程序定义的流处理程序工厂的工厂列表 */
	private final List<URLStreamHandlerFactory> userFactories = new CopyOnWriteArrayList<>();
	
	
	/**
	 * 获取对单例实例的引用。建议调用者在使用返回的实例之前检查 {@link #isRegistered()} 的值。
	 *
	 * @return 对单例实例的引用
	 */
	public static MoonStoneURLStreamHandlerFactory getInstance() {
		getInstanceInternal(true);
		return instance;
	}

	/**
	 * 获取对单例实例的引用。并指定布尔值决定是否注册到 Java 虚拟机
	 * 
	 * @param register - 是否注册到 Java 虚拟机
	 * @return 对单例实例的引用
	 */
	private static MoonStoneURLStreamHandlerFactory getInstanceInternal(boolean register) {
		// 双重检查锁
		if (instance == null) {
			synchronized (MoonStoneURLStreamHandlerFactory.class) {
				if (instance == null) {
					instance = new MoonStoneURLStreamHandlerFactory(register);
				}
			}
		}
		return instance;
	}

	/**
	 * 向 JVM 注册这个工厂。 可以多次调用。 该实现确保注册只发生一次。
	 *
	 * @return 如果工厂已在 JVM 中注册或由于此调用而成功注册，则为 <code>true</code>。 如果工厂在此调用之前被禁用，则为 <code>false</code>。
	 */
	public static boolean register() {
		return getInstanceInternal(true).isRegistered();
	}


	/**
	 * 阻止此 factory 向 JVM 注册。 可以多次调用。
	 *
	 * @return 如果工厂已被禁用或由于此调用而被成功禁用，则为 <code>true</code>。 如果工厂在此调用之前已经注册，则返回 <code>false</code>。
	 */
	public static boolean disable() {
		return !getInstanceInternal(false).isRegistered();
	}


	/**
	 * 释放对已使用提供的类加载器加载的任何用户提供的工厂的引用。 在 Web 应用程序停止期间调用以防止内存泄漏。
	 *
	 * @param classLoader - 要释放的类加载器
	 */
	public static void release(ClassLoader classLoader) {
		if (instance == null) {
			return;
		}
		List<URLStreamHandlerFactory> factories = instance.userFactories;
		for (URLStreamHandlerFactory factory : factories) {
			ClassLoader factoryLoader = factory.getClass().getClassLoader();
			while (factoryLoader != null) {
				if (classLoader.equals(factoryLoader)) {
					// 实现说明：userFactories 是一个 CopyOnWriteArrayList，因此使用 List.remove() 而不是通常的 Iterator.remove() 删除元素
					factories.remove(factory);
					break;
				}
				factoryLoader = factoryLoader.getParent();
			}
		}
	}

	/**
	 * 隐藏默认构造函数，以确保该工厂只有一个实例（单例模式）
	 * 
	 * @param register - 是否注册到 Java 虚拟机
	 */
	private MoonStoneURLStreamHandlerFactory(boolean register) {
		this.registered = register;
		if (register) {
			/**
			 * 设置应用程序的 URLStreamHandlerFactory。此方法在给定的 Java 虚拟机中最多可以调用一次。
			 * 
			 * URLStreamHandlerFactory 实例用于从协议名称构造流协议处理程序。
			 * 
			 * 如果有安全管理器，此方法首先调用安全管理器的 checkSetFactory 方法以确保操作被允许。这可能会导致 SecurityException。
			 * 
			 * @param fac - 所需的工厂
			 * 
			 * @exception Error -  如果应用程序已经设置了工厂
			 * @excepton SecurityException - 如果一个安全管理器存在并且它的checkSetFactory方法不允许该操作
			 */
			URL.setURLStreamHandlerFactory(this);
		}
	}

	public boolean isRegistered() {
		return registered;
	}

	/**
	 * 由于 JVM 只允许单次调用 {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)}  并且Tomcat需要注册一个处理程序，
	 * 所以提供一种机制来允许应用程序注册自己的处理程序。
	 * 
	 * @param factory - 用户提供了要添加到已经注册 URLStreamHandlerFactory 的 factory
	 */
	public void addUserFactory(URLStreamHandlerFactory factory) {
		userFactories.add(factory);
	}

	/**
	 * 使用指定的协议创建一个新的 URLStreamHandler 实例。
	 * 
	 * @param protocol - 协议（“ftp”、“http”、“nntp”等）。
	 * @return 特定协议的 URLStreamHandler。
	 */
	@Override
	public URLStreamHandler createURLStreamHandler(String protocol) {
		// MoonStone 的处理程序始终优先，因此应用程序无法覆盖它
		if (WAR_PROTOCOL.equals(protocol)) {
			return new Handler();
		} else if (CLASSPATH_PROTOCOL.equals(protocol)) {
			return new ClasspathURLStreamHandler();
		}

		// 应用程序处理程序
		for (URLStreamHandlerFactory factory : userFactories) {
			URLStreamHandler handler = factory.createURLStreamHandler(protocol);
			if (handler != null) {
				return handler;
			}
		}

		// 未知协议
		return null;
	}
}
