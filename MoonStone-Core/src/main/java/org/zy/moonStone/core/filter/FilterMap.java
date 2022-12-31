package org.zy.moonStone.core.filter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.DispatcherType;

/**
 * @dateTime 2022年5月9日;
 * @author zy(azurite-Y);
 * @description 表示 Web 应用程序的过滤器映射。 每个过滤器映射必须包含过滤器名称加上 URL 模式或 servlet 名称。
 */
public class FilterMap implements Serializable {
	private static final long serialVersionUID = -8928339134009740134L;

	   /**
	    * 此映射匹配特定请求时要执行的筛选器的名称
	    */
    public static final int ERROR = 1;
    public static final int FORWARD = 2;
    public static final int INCLUDE = 4;
    public static final int REQUEST = 8;
    public static final int ASYNC = 16;

    /** 表示没有设置任何内容。这将被视为等于一个REQUEST */
    private static final int NOT_SET = 0;

    private int dispatcherMapping = NOT_SET;
	
    private String filterName = null;

    /** 此映射匹配的 servlet 名称 */
    private String[] servletNames = new String[0];
    
    /** 指示此映射将匹配所有 url 模式的标志  */
    private boolean matchAllUrlPatterns = false;
    
    /** 指示此映射将匹配所有 servlet 名称的标志  */
    private boolean matchAllServletNames = false;
    
    /** 此映射匹配的 URL 模式 */
    private String[] urlPatterns = new String[0];
    
    public String getFilterName() {
        return this.filterName;
    }
    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    /**
     * 此方法将用于设置 FilterMap 的当前状态，表示应应用过滤器的状态。
     * @param dispatcherString - 应该匹配此过滤器的调度程序类型
     */
    public void setDispatcher(String dispatcherString) {
    	String dispatcher = dispatcherString.toUpperCase(Locale.ENGLISH);
    	
    	/*
    	 *  |= : 自高位与，有1为1无1为0 
    	 */
        if (dispatcher.equals(DispatcherType.FORWARD.name())) {
            // 将 FORWARD 应用于全局调度程序映射
            dispatcherMapping |= FORWARD;
        } else if (dispatcher.equals(DispatcherType.INCLUDE.name())) {
            // 将 INCLUDE 应用于全局调度程序映射
            dispatcherMapping |= INCLUDE;
        } else if (dispatcher.equals(DispatcherType.REQUEST.name())) {
            // 将 REQUEST 应用于全局调度程序映射
            dispatcherMapping |= REQUEST;
        }  else if (dispatcher.equals(DispatcherType.ERROR.name())) {
            // 将 ERROR 应用于全局调度程序映射
            dispatcherMapping |= ERROR;
        }  else if (dispatcher.equals(DispatcherType.ASYNC.name())) {
            // 将 ERROR 应用于全局调度程序映射
            dispatcherMapping |= ASYNC;
        }
	}
    public String[] getDispatcherNames() {
        List<String> result = new ArrayList<>();
        if ((dispatcherMapping & FORWARD) != 0) {
            result.add(DispatcherType.FORWARD.name());
        }
        if ((dispatcherMapping & INCLUDE) != 0) {
            result.add(DispatcherType.INCLUDE.name());
        }
        if ((dispatcherMapping & REQUEST) != 0) {
            result.add(DispatcherType.REQUEST.name());
        }
        if ((dispatcherMapping & ERROR) != 0) {
            result.add(DispatcherType.ERROR.name());
        }
        if ((dispatcherMapping & ASYNC) != 0) {
            result.add(DispatcherType.ASYNC.name());
        }
        return result.toArray(new String[result.size()]);
    }
    public int getDispatcherMapping() {
        // 缺少任何调度程序元素等效于 REQUEST 值
        if (dispatcherMapping == NOT_SET) return REQUEST;

        return dispatcherMapping;
    }
    
    public String[] getServletNames() {
        if (matchAllServletNames) {
            return new String[] {};
        } else {
            return this.servletNames;
        }
    }
    public void addServletName(String servletName) {
        if ("*".equals(servletName)) {
            this.matchAllServletNames = true;
        } else {
            String[] results = new String[servletNames.length + 1];
            System.arraycopy(servletNames, 0, results, 0, servletNames.length);
            results[servletNames.length] = servletName;
            servletNames = results;
        }
    }
    
    public boolean getMatchAllUrlPatterns() {
        return matchAllUrlPatterns;
    }
    
    public boolean getMatchAllServletNames() {
        return matchAllServletNames;
    }
    
    public String[] getURLPatterns() {
        if (matchAllUrlPatterns) {
            return new String[] {};
        } else {
            return this.urlPatterns;
        }
    }
    public void addURLPattern(String urlPattern) {
    	if ("*".equals(urlPattern)) {
            this.matchAllUrlPatterns = true;
        } else {
            String[] results = new String[urlPatterns.length + 1];
            System.arraycopy(urlPatterns, 0, results, 0, urlPatterns.length);
            results[urlPatterns.length] = urlPattern;
            urlPatterns = results;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FilterMap[");
        sb.append("filterName=");
        sb.append(this.filterName);
        for (int i = 0; i < servletNames.length; i++) {
            sb.append(", servletName=");
            sb.append(servletNames[i]);
        }
        for (int i = 0; i < urlPatterns.length; i++) {
            sb.append(", urlPattern=");
            sb.append(urlPatterns[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
