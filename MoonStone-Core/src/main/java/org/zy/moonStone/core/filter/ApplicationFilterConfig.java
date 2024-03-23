package org.zy.moonstone.core.filter;

import org.zy.moonstone.core.interfaces.container.Context;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description
 */
public class ApplicationFilterConfig implements FilterConfig, Serializable {
//	private transient Logger logger = LoggerFactory.getLogger(ApplicationFilterConfig.class);

	private static final long serialVersionUID = -7957072751044520812L;

	/**
	 * 空字符串集合作为空枚举的基础
	 */
	private static final List<String> emptyString = Collections.emptyList();

	/**
	 * 关联的上下文
	 */
	private final transient Context context;

	/**
	 * 配置的过滤器
	 */
	private transient Filter filter = null;

	/**
	 * 关联过滤器的 FilterDef
	 */
	private final FilterDef filterDef;

	// ----------------------------------------------------------- 构造器 -----------------------------------------------------------
	/**
	 * 为指定的过滤器定义构造一个新的 ApplicationFilterConfig
	 *
	 * @param context - 所关联的上下文
	 * @param filterDef - 为其构造 FilterConfig 的过滤器定义
	 * @throws Exception 
	 *
	 */
	public ApplicationFilterConfig(Context context, FilterDef filterDef) throws Exception {
		super();

		this.context = context;
		this.filterDef = filterDef;
		// 如有必要，分配一个新的过滤器实例
		if (filterDef.getFilter() == null) {
			getFilter();
		} else {
			this.filter = filterDef.getFilter();
			filter.init(this);
		}
	}

	/**
	 * @return 过滤器对象实例
	 * @throws Exception 
	 */
	Filter getFilter() throws Exception {
		// 返回现有的过滤器实例（如果有）
		if (this.filter != null) return this.filter;

		String filterClass = filterDef.getFilterClass();
		this.filter = (Filter) context.getInstanceManager().newInstance(filterClass);

		filter.init(this);

		return this.filter;

	}

	/**
	 * @return 正在配置的过滤器的名称
	 */
	@Override
	public String getFilterName() {
		return filterDef.getFilterName();
	}

	/**
	 * @return 正在配置的过滤器的类
	 */
	public String getFilterClass() {
		return filterDef.getFilterClass();
	}

	/**
	 * 返回一个包含命名初始化参数值的字符串，如果参数不存在，则返回 null。
	 *
	 * @param name - 请求的初始化参数的名称
	 */
	@Override
	public String getInitParameter(String name) {
		Map<String,String> map = filterDef.getParameterMap();
		if (map == null) {
			return null;
		}
		return map.get(name);
	}

	/**
	 * @return 此过滤器的初始化参数名称的枚举
	 */
	@Override
	public Enumeration<String> getInitParameterNames() {
		Map<String,String> map = filterDef.getParameterMap();
		if (map == null) {
			return Collections.enumeration(emptyString);
		}
		return Collections.enumeration(map.keySet());
	}

	/**
	 * @return 对调用者在其中执行的 ServletContext 的引用
	 */
	@Override
	public ServletContext getServletContext() {
		return this.context.getServletContext();
	}

	public Map<String, String> getFilterInitParameterMap() {
		return Collections.unmodifiableMap(filterDef.getParameterMap());
	}

	/**
	 * 释放与此 FilterConfig 关联的 Filter 实例（如果有）。
	 */
	public void release() {
		if (this.filter != null) {
			filter.destroy();
		}
		this.filter = null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ApplicationFilterConfig[");
		sb.append("name=");
		sb.append(filterDef.getFilterName());
		sb.append(", filterClass=");
		sb.append(filterDef.getFilterClass());
		sb.append("]");
		return sb.toString();
	}
	
	
    /**
     * 返回为其配置的过滤器定义
     */
    public FilterDef getFilterDef() {
        return this.filterDef;
    }
}
