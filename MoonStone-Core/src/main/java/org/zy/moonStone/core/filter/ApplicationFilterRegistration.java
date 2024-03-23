package org.zy.moonstone.core.filter;

import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.util.ParameterMap;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.*;

/**
 * @dateTime 2022年4月13日;
 * @author zy(azurite-Y);
 * @description
 */
public class ApplicationFilterRegistration implements FilterRegistration.Dynamic {
	private final FilterDef filterDef;
	private final Context context;

	public ApplicationFilterRegistration(FilterDef filterDef, Context context) {
		this.filterDef = filterDef;
		this.context = context;

	}

	/**
	 * 为由此 FilterRegistration 表示的过滤器添加具有给定 servlet 名称和调度程序类型的过滤器映射。
	 * <p>
	 * 过滤器映射按照它们添加的顺序进行匹配。
	 * <p>
	 * 根据 isMatchAfter 参数的值，将在获得 thisFilterRegistration 的 ServletContext 的任何声明的过滤器映射之后或之前考虑给定的过滤器映射。
	 * <p>
	 * 如果多次调用此方法，则每次连续调用都会增加前者的效果。
	 * 
	 * @param dispatcherTypes - 过滤器映射的调度程序类型，如果要使用默认 DispatcherType.REQUEST，则为 null
	 * @param isMatchAfter - 如果给定的过滤器映射应该在任何声明的过滤器映射之后匹配，则为 true；如果它应该在获得此 FilterRegistration 的 ServletContext 的任何声明的过滤器映射之前匹配，则为 false
	 * @param servletNames - 过滤器映射的 servlet 名称
	 */
	@Override
	public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {
		FilterMap filterMap = new FilterMap();

		filterMap.setFilterName(filterDef.getFilterName());

		if (dispatcherTypes != null) {
			for (DispatcherType dispatcherType : dispatcherTypes) {
				filterMap.setDispatcher(dispatcherType.name());
			}
		}

		if (servletNames != null) {
			for (String servletName : servletNames) {
				filterMap.addServletName(servletName);
			}

			if (isMatchAfter) {
				context.addFilterMap(filterMap);
			} else {
				context.addFilterMapBefore(filterMap);
			}
		}

	}

	/**
	 * 获取此 FilterRegistration 表示的 Filter 当前可用的 servlet 名称映射。
	 * <p>
	 * 如果允许，对返回的 Collection 的任何更改都不得影响此 FilterRegistration。
	 * @return 由此 FilterRegistration 表示的过滤器的当前可用 servlet 名称映射的集合（可能为空）
	 */
	@Override
	public Collection<String> getServletNameMappings() {
		Collection<String> result = new HashSet<>();

		FilterMap[] filterMaps = context.findFilterMaps();

		for (FilterMap filterMap : filterMaps) {
			if (filterMap.getFilterName().equals(filterDef.getFilterName())) {
				for (String servletName : filterMap.getServletNames()) {
					result.add(servletName);
				}
			}
		}
		return result;
	}

	/**
	 * 为由此 FilterRegistration 表示的过滤器添加具有给定 url 模式和调度程序类型的过滤器映射。
	 * <p>
	 * 过滤器映射按照它们添加的顺序进行匹配。
	 * <p>
	 * 根据 isMatchAfter 参数的值，给定的过滤器映射将在获得此 FilterRegistration 的 ServletContext 的任何声明的过滤器映射之后或之前考虑。
	 * 
	 * @param dispatcherTypes - 过滤器映射的调度程序类型，如果要使用默认 DispatcherType.REQUEST，则为 null
	 * @param isMatchAfter - 如果给定的过滤器映射应该在任何声明的过滤器映射之后匹配，则为 true；如果它应该在获得此 FilterRegistration 的 ServletContext 的任何声明的过滤器映射之前匹配，则为 false
	 * @param urlPatterns - 过滤器映射的 url 模式
	 */
	@Override
	public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {
		FilterMap filterMap = new FilterMap();

		filterMap.setFilterName(filterDef.getFilterName());

		if (dispatcherTypes != null) {
			for (DispatcherType dispatcherType : dispatcherTypes) {
				filterMap.setDispatcher(dispatcherType.name());
			}
		}

		if (urlPatterns != null) {
			for (String urlPattern : urlPatterns) {
				filterMap.addURLPattern(urlPattern);
			}

			if (isMatchAfter) {
				context.addFilterMap(filterMap);
			} else {
				context.addFilterMapBefore(filterMap);
			}
		}
	}

	/**
	 * 获取此 FilterRegistration 表示的 Filter 当前可用的 URL 模式映射。
	 * <p>
	 * 如果允许，对返回的 Collection 的任何更改都不得影响此 FilterRegistration。
	 * @return 一个（可能为空）由此 FilterRegistration 表示的过滤器的当前可用 URL 模式映射的集合
	 */
	@Override
	public Collection<String> getUrlPatternMappings() {
		Collection<String> result = new HashSet<>();

		FilterMap[] filterMaps = context.findFilterMaps();

		for (FilterMap filterMap : filterMaps) {
			if (filterMap.getFilterName().equals(filterDef.getFilterName())) {
				for (String urlPattern : filterMap.getURLPatterns()) {
					result.add(urlPattern);
				}
			}
		}
		return result;
	}

	/**
	 * 获取由 thisRegistration 表示的 Servlet 或 Filter 的名称。
	 * @return 此注册所代表的 Servlet 或过滤器的名称
	 */
	@Override
	public String getName() {
		return filterDef.getFilterName();
	}

	/**
	 * 获取由此注册表示的 Servlet 或过滤器的完全限定类名
	 * @return 此注册表示的 Servlet 或过滤器的完全限定类名，如果此注册是初步的，则为 null
	 */
	@Override
	public String getClassName() {
		return filterDef.getFilterClass();
	}

	/**
	 * 使用此注册所代表的Servlet或筛选器上的给定名称和值设置初始化参数
	 * @param name - 初始化参数名
	 * @param value - 初始化参数值
	 * @return 如果更新成功，则为 true，即此注册表示的 Servletor 过滤器不存在具有给定名称的初始化参数，否则为 false
	 */
	@Override
	public boolean setInitParameter(String name, String value) {
		if (name == null || value == null) {
			throw new IllegalArgumentException("初始化参数不能为null");
		}
		if (getInitParameter(name) != null) {
			return false;
		}

		filterDef.addInitParameter(name, value);

		return true;
	}

	/**
	 * 获取具有给定名称的初始化参数的值，该名称将用于初始化由此注册对象表示的 Servlet 或过滤器。
	 * @param name - 请求其值的初始化参数的名称
	 * @return 具有给定名称的初始化参数的值，如果不存在具有给定名称的初始化参数，则返回 null
	 */
	@Override
	public String getInitParameter(String name) {
		return filterDef.getParameterMap().get(name);
	}

	/**
	 * 在此注册表示的 Servlet 或过滤器上设置给定的初始化参数。
	 * <p>
	 * 给定的初始化参数映射按值处理，即对于映射中包含的每个初始化参数，此方法调用 setInitParameter(String, String)。
	 * 如果该方法对给定映射中的任何初始化参数返回 false，则不进行更新 将被执行，并返回false。 
	 * 同样，如果映射包含具有空名称或值的初始化参数，则不会执行任何更新，并且会抛出 IllegalArgumentException。
	 * <p>
	 * 返回的集合不受注册对象的支持，因此返回集合中的更改不会反映在注册对象中，反之亦然。
	 * 
	 * @return （可能为空的）初始化参数名称集冲突
	 */
	@Override
	public Set<String> setInitParameters(Map<String, String> initParameters) {
		Set<String> conflicts = new HashSet<>();

		for (Map.Entry<String, String> entry : initParameters.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				throw new IllegalArgumentException(String.format("初始化参数不能为null，by name: %s", entry.getKey()));
			}
			if (getInitParameter(entry.getKey()) != null) {
				conflicts.add(entry.getKey());
			}
		}

		for (Map.Entry<String, String> entry : initParameters.entrySet()) {
			setInitParameter(entry.getKey(), entry.getValue());
		}

		return conflicts;
	}

	/**
	 * 获取一个不可变（可能为空）的 Map，其中包含当前可用的初始化参数，这些参数将用于初始化由此 Registration 对象表示的 Servlet 或 Filter。
	 * 
	 * @return 包含当前可用初始化参数的映射，这些参数将用于初始化此 Registration 对象表示的 Servlet 或 Filter
	 */
	@Override
	public Map<String, String> getInitParameters() {
		ParameterMap<String,String> result = new ParameterMap<>();
		result.putAll(filterDef.getParameterMap());
		result.setLocked(true);
		return result;
	}

	/**
	 * 将此 dynamicRegistration 表示的 Servlet 或 Filter 配置为是否支持异步操作。
	 * <p>
	 * 默认情况下，servlet 和过滤器不支持异步操作。
	 * <p>
	 * 对此方法的调用会覆盖任何先前的设置。
	 * 
	 * @param isAsyncSupported - 如果此动态注册所代表的 Servlet 或 Filter 支持异步操作，则为 true，否则为 false
	 */
	@Override
	public void setAsyncSupported(boolean isAsyncSupported) {
		filterDef.setAsyncSupported(isAsyncSupported);
	}
}
