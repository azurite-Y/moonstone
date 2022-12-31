package org.zy.moonStone.core.filter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;

/**
 * @dateTime 2022年4月2日;
 * @author zy(azurite-Y);
 * @description 表示 Web 应用程序的过滤器定义
 */
public class FilterDef implements Serializable{
	private static final long serialVersionUID = -6558390954109724759L;

	/**
	 * 过滤器的说明.
	 */
	private String description = null;

	/**
	 * 过滤器的显示名称.
	 */
	private String displayName = null;

	/**
	 * 与此定义关联的过滤器实例
	 */
	private transient Filter filter = null;

	/**
	 * 实现此过滤器的 Java 类的完全限定名称.
	 */
	private String filterClass = null;

	/**
	 * 实现此过滤器的 Java 类的完全限定名称.
	 */
	private String filterName = null;

	/**
	 * 与此过滤器关联的图标.
	 */
	private String largeIcon = null;

	/**
	 * 与此过滤器关联的小图标.
	 */
	private String smallIcon = null;

	/**
	 * 此过滤器的初始化参数集，以参数名称为键.
	 */
	private final Map<String, String> parameters = new HashMap<>();

	/**
	 * 异步支持
	 */
	private boolean asyncSupported = false;

	// --------------------------------------------------------- 公共方法 ---------------------------------------------------------
	/**
	 * 将初始化参数添加到与此过滤器关联的参数集.
	 *
	 * @param name - 初始化参数名称
	 * @param value - 初始化参数值
	 */
	public void addInitParameter(String name, String value) {
		if (parameters.containsKey(name)) {
			return;
		}
		parameters.put(name, value);

	}

	// --------------------------------------------------------- getter、setter ---------------------------------------------------------
	public String getDescription() {
		return this.description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getDisplayName() {
		return this.displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public Filter getFilter() {
		return filter;
	}
	public void setFilter(Filter filter) {
		this.filter = filter;
	}
	public String getFilterClass() {
		return this.filterClass;
	}
	public void setFilterClass(String filterClass) {
		this.filterClass = filterClass;
	}
	public String getFilterName() {
		return this.filterName;
	}
	public void setFilterName(String filterName) {
		if (filterName == null || filterName.equals("")) {
			throw new IllegalArgumentException(String.format("无效的filterName，by filterName：%", filterName));
		}
		this.filterName = filterName;
	}
	public String getLargeIcon() {
		return this.largeIcon;
	}
	public void setLargeIcon(String largeIcon) {
		this.largeIcon = largeIcon;
	}
	public Map<String, String> getParameterMap() {
		return this.parameters;
	}
	public String getSmallIcon() {
		return this.smallIcon;
	}
	public void setSmallIcon(String smallIcon) {
		this.smallIcon = smallIcon;
	}
	public boolean getAsyncSupported() {
		return asyncSupported;
	}
	public void setAsyncSupported(boolean asyncSupported) {
		this.asyncSupported = asyncSupported;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("FilterDef[");
		sb.append("filterName=");
		sb.append(this.filterName);
		sb.append(", filterClass=");
		sb.append(this.filterClass);
		sb.append("]");
		return sb.toString();
	}
}
