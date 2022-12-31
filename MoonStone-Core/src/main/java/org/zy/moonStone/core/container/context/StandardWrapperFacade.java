package org.zy.moonStone.core.container.context;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.zy.moonStone.core.container.StandardWrapper;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description
 */
public final class StandardWrapperFacade implements ServletConfig {
	/**
	 * 包装config.
	 */
	private final ServletConfig config;

	/**
	 * 包装context (镜像).
	 */
	private ServletContext context = null;

	/**
	 * 围绕StandardWrapper创建一个新的镜像.
	 * @param config - 相关的包装
	 */
	public StandardWrapperFacade(StandardWrapper config) {
		super();
		this.config = config;
	}

	/**
	 * 返回这个servlet实例的名称。
	 */
	@Override
	public String getServletName() {
		return config.getServletName();
	}

	/**
	 * 返回调用者所在的ServletContext的引用
	 * @return 一个ServletContext对象，被调用者用来与它的servlet容器交互
	 */
	@Override
	public ServletContext getServletContext() {
		if (context == null) {
			updateServletContext();
		}
		return context;
	}
	
	/**
	 * 更新 ServletContext 对象。再重启上下文之后会重新实例化一个 ServletContext。调用从方法方便感知此对象
	 */
	public void updateServletContext() {
		context = config.getServletContext();
		if (context instanceof ApplicationContext) {
			context = ((ApplicationContext) context).getFacade();
		}
	}
	
	/**
	 * 获取具有给定名称的初始化参数的值
	 * @param name - 要获取其值的初始化参数的名称
	 * @return 一个包含初始化参数值的字符串，如果初始化参数不存在，则为空
	 */
	@Override
	public String getInitParameter(String name) {
		return config.getInitParameter(name);
	}

	/**
	 * 返回servlet初始化参数的名称为字符串对象的枚举，如果servlet没有初始化参数，则返回空的枚举
	 * @return 一个字符串对象的枚举，包含servlet初始化参数的名称
	 */
	@Override
	public Enumeration<String> getInitParameterNames() {
		return config.getInitParameterNames();
	}
}
