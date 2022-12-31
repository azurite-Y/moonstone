package org.zy.moonStone.core.servlets;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;

import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.ParameterMap;

/**
 * @dateTime 2022年9月29日;
 * @author zy(azurite-Y);
 * @description
 */
public class ApplicationServletRegistration implements ServletRegistration.Dynamic {
	private final Wrapper wrapper;
    private final Context context;
    private ServletSecurityElement constraint;

    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------    
    public ApplicationServletRegistration(Wrapper wrapper, Context context) {
        this.wrapper = wrapper;
        this.context = context;
    }
	
    
	// -------------------------------------------------------------------------------------
	// Registration Methods
	// -------------------------------------------------------------------------------------    
    /**
     * 获取由此registration表示的Servlet或Filter的名称
     * 
     * @return 这个注册表所代表的Servlet或Filter的名称
     */
	@Override
	public String getName() {
        return wrapper.getName();
	}
	
	/**
	 * 为这个ServletRegistration表示的servle添加一个使用给定URL模式的servlet映射。
	 * <P>
	 * 如果任何指定的URL模式已经映射到不同的Servlet，则不会执行更新。
	 * <P>
	 * 如果多次调用此方法，则每次后续调用都会增加前者的效果。
	 * <P>
	 * 返回的集合不是由ServletRegistration对象支持的，因此返回集合中的更改不会反映在servletregistration对象中，反之亦然。
	 * 
	 * @param urlPatterns - servlet映射的URL模式
	 * @return (可能为空)已经映射到不同Servlet的URL模式集
	 */
	@Override
	public Set<String> addMapping(String... urlPatterns) {
		if (urlPatterns == null) {
			return Collections.emptySet();
		}

		Set<String> conflicts = new HashSet<>();

		for (String urlPattern : urlPatterns) {
			String wrapperName = context.findServletMapping(urlPattern);
			if (wrapperName != null) {
				Wrapper wrapper = (Wrapper) context.findChild(wrapperName);
				if (wrapper.isOverridable()) {
					// 有些Wrappers可能会被覆盖，而不会产生冲突
					context.removeServletMapping(urlPattern);
				} else {
					conflicts.add(urlPattern);
				}
			}
		}

		if (!conflicts.isEmpty()) {
			return conflicts;
		}

		try {
			for (String urlPattern : urlPatterns) {
				context.addServletMappingDecoded(URLDecoder.decode(urlPattern, "UTF-8"), wrapper.getName());
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		if (constraint != null) {
			context.addServletSecurity(this, constraint);
		}

		return Collections.emptySet();
	}
	
	/**
	 * 获取由此注册表示的Servlet或Filter的完全限定类名
	 * 
	 * @return 由注册表示的Servlet或过滤器的完全限定类名，如果注册是初步的，则为null
	 */
	@Override
	public String getClassName() {
        return wrapper.getServletClass();
	}
    
	/**
	 * 在由此注册表示的Servlet或Filter上设置具有给定名称和值的初始化参数
	 * 
	 * @param name - 初始化参数名
	 * @param value - 初始化参数值
	 * @return 如果更新成功，即具有给定名称的初始化参数对于这个注册表示的Servlet或Filter不存在，则为true，否则为false
	 */
	@Override
	public boolean setInitParameter(String name, String value) {
		if (name == null || value == null) {
            throw new IllegalArgumentException(String.format("无初始化参数, by name: %s, value: %s",name, value));
        }
        if (getInitParameter(name) != null) {
            return false;
        }

        wrapper.addInitParameter(name, value);
        return true;
	}
	
	/**
	 * 获取具有给定名称的初始化参数的值，该参数将用于初始化此Registration对象表示的Servlet或Filter。
	 * 
	 * @param name - 请求其值的初始化参数的名称
	 * @return 带有给定名称的初始化参数的值，如果不存在具有给定名称的初始参数，则为null
	 */
	@Override
	public String getInitParameter(String name) {
		return wrapper.findInitParameter(name);
	}
	
	/**
	 * 在此注册表示的Servlet或筛选器上设置给定的初始化参数。
	 * <p>
	 * 给定的初始化参数映射是按值处理的，即，对于映射中包含的每个初始化参数，此方法调用setInitParameter(字符串，字符串)。
	 * 如果该方法将为给定映射中的任何初始化参数返回false，则不会执行更新，并且将返回false。
	 * 同样，如果映射包含名称或值为空的初始化参数，则不会执行任何更新，并且将抛出IllegalArgumentException异常。
	 * <p>
	 * 返回的集合不受注册对象的支持，因此返回集合中的更改不会反映在Registration对象中，反之亦然。
	 * 
	 * @param initParameters - 初始化参数
	 * @return (可能为空)冲突的初始化参数名称的集合
	 */
	@Override
	public Set<String> setInitParameters(Map<String, String> initParameters) {
		Set<String> conflicts = new HashSet<>();

        for (Map.Entry<String, String> entry : initParameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(String.format("无初始化参数, by name: %s, value: %s", entry.getKey(), entry.getValue()));
            }
            if (getInitParameter(entry.getKey()) != null) {
                conflicts.add(entry.getKey());
            }
        }

        // 必须添加一个单独的循环，因为如果有问题，规范根本不需要更新
        if (conflicts.isEmpty()) {
            for (Map.Entry<String, String> entry : initParameters.entrySet()) {
                setInitParameter(entry.getKey(), entry.getValue());
            }
        }

        return conflicts;
	}
	
	/**
	 * 获取一个不可变（可能为空）Map，其中包含当前可用的初始化参数，这些参数将用于初始化此Registration对象表示的Servlet或Filter。
	 * 
	 * @return 包含当前可用初始化参数的映射，这些参数将用于初始化此Registration对象表示的Servlet或Filter
	 */
	@Override
	public Map<String, String> getInitParameters() {
		ParameterMap<String,String> result = new ParameterMap<>();

        String[] parameterNames = wrapper.findInitParameters();

        for (String parameterName : parameterNames) {
            result.put(parameterName, wrapper.findInitParameter(parameterName));
        }

        result.setLocked(true);
        return result;
	}
	
	
	// -------------------------------------------------------------------------------------
	// ServletRegistration Methods
	// -------------------------------------------------------------------------------------    
	/**
	 * 获取此ServletRegistration表示的Servlet的当前可用映射。
	 * <p>
	 * 如果允许，对返回的集合的任何更改都不得影响此ServletRegistration。
	 * 
	 * @return 此 ServletRegistration 表示的Servlet的当前可用映射的集合(可能为空
	 */
	@Override
	public Collection<String> getMappings() {
		Set<String> result = new HashSet<>();
        String servletName = wrapper.getName();

        String[] urlPatterns = context.findServletMappings();
        for (String urlPattern : urlPatterns) {
            String name = context.findServletMapping(urlPattern);
            if (name.equals(servletName)) {
                result.add(urlPattern);
            }
        }
        return result;
	}
    
	/**
	 * 获取此ServletRegistration表示的Servlet的RunAs角色的名称。
	 * 
	 * @return RunAs角色的名称，如果Servlet被配置为作为调用者运行，则为空
	 */
	@Override
	public String getRunAsRole() {
        return wrapper.getRunAs();
	}
	
	
	// -------------------------------------------------------------------------------------
	// ServletRegistration.Dynamic Methods
	// -------------------------------------------------------------------------------------    
	/**
	 * 将此 ServletRegistration.Dynamic 表示的Servlet或筛选器配置为是否支持异步操作。
	 * <p>
	 * 默认情况下，servlet和过滤器不支持异步操作。
	 * <p>
	 * 对此方法的调用将覆盖以前的任何设置。
	 * 
	 * @param isAsyncSupported - 如果此动态注册表示的Servlet或筛选器支持异步操作，则为true，否则为false
	 */
	@Override
	public void setAsyncSupported(boolean asyncSupported) {
        wrapper.setAsyncSupported(asyncSupported);
	}

	/**
	 * 设置此动态ServletRegistration表示的Servlet的loadOnStartup优先级。
	 * <p>
	 * 大于或等于零的loadOnStartup值向容器指示Servlet的初始化优先级。
	 * 在这种情况下，容器必须在ServletContext的初始化阶段实例化和初始化Servlet，也就是说，
	 * 在它调用了ServletContextListener.contextInitialized方法中为ServletContex配置的所有ServletContactListener对象之后。
	 * <p>
	 * 如果loadOnStartup是一个负整数，则容器可以自由地实例化和初始化Servlet。
	 * <p>
	 * loadOnStartup的默认值为-1。
	 * <p>
	 * 对此方法的调用将覆盖以前的任何设置。
	 * 
	 * @param loadOnStartup - Servlet的初始化优先级
	 */
	@Override
	public void setLoadOnStartup(int loadOnStartup) {
        wrapper.setLoadOnStartup(loadOnStartup);
	}

	/**
	 * 设置要应用于为此ServletRegistration定义的映射的ServletSecurityElement。
	 * <p>
	 * 此方法适用于添加到此ServletRegistration的所有映射，直到从中获取它的ServletContext被初始化为止。
	 * <p>
	 * 如果此ServletRegistration的URL模式是通过可移植部署描述符建立的安全约束(security-constraint)的确切目标，则此方法不会更改该模式的安全约束，并且该模式将包含在返回值中。
	 * <p>
	 * 如果此ServletRegistration的URL模式是通过javax.servlet.annotation建立的安全约束的精确目标。
	 * ServletSecurity注释或之前对此方法的调用，则此方法将替换该模式的安全约束。
	 * <p>
	 * 如果此ServletRegistration的URL模式既不是通过javax.servlet.annotation建立的安全约束的确切目标。
	 * ServletSecurity注释或以前对此方法的调用，或者便携式部署描述符中安全约束的确切目标，则此方法通过参数ServletSecurityElement为该模式建立安全约束。
	 * <p>
	 * 返回的集合不受Dynamic对象支持，因此返回集合中的更改不会反映在Dynamic对象中，反之亦然。
	 * 
	 * @param constraint - 要应用于映射到此ServletRegistration的模式的ServletSecurityElement
	 * @return 已经是通过可移植部署描述符建立的安全约束的确切目标的URL模式集(可能是空的)。此方法对返回集合中包含的模式没有影响
	 */
	@Override
	public Set<String> setServletSecurity(ServletSecurityElement constraint) {
		if (constraint == null) {
            throw new IllegalArgumentException( String.format("设置的ServletSecurityElement不能为null, by 注册主体: %s, context: %s:", getName(), context.getName()) );
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
        	throw new IllegalArgumentException( String.format("Context 已不是启动前, by 注册主体: %s, context: %s:", getName(), context.getName()) );
        }

        this.constraint = constraint;
        return context.addServletSecurity(this, constraint);
	}

	/**
	 * 设置要应用于为此ServletRegistration定义的映射的MultipartConfigElement。如果多次调用此方法，则每次连续调用都会覆盖前者的效果。
	 * 
	 * @param multipartConfig - 要应用于映射到注册的模式的MultipartConfigElement
	 */
	@Override
	public void setMultipartConfig(MultipartConfigElement multipartConfig) {
		wrapper.setMultipartConfigElement(multipartConfig);
	}

	/**
	 * 设置此ServletRegistration的 RunAs 角色的名称。
	 * 
	 * @param roleName - RunAs 角色的名称
	 */
	@Override
	public void setRunAsRole(String roleName) {
        wrapper.setRunAs(roleName);
	}
}
