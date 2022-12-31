package org.zy.moonStone.core.filter;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.connector.HttpRequest;
import org.zy.moonStone.core.container.context.StandardContext;
import org.zy.moonStone.core.interfaces.container.Wrapper;

/**
 * @dateTime 2022年11月17日;
 * @author zy(azurite-Y);
 * @description 用于创建和缓存过滤器以及创建过滤器链的工厂
 */
public final class ApplicationFilterFactory {
    private ApplicationFilterFactory() {}
    
    
    /**
     * 构造一个FilterChain实现，它将包装指定的servlet实例的执行
     *
     * @param request - 正在处理的servlet请求
     * @param wrapper - 管理servlet实例的包装器
     * @param servlet - 要包装的servlet实例
     *
     * @return 配置的FilterChain实例，如果不执行则为空
     */
    public static ApplicationFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {
        if (servlet == null)
            return null;

        // 创建并初始化过滤器链对象
        ApplicationFilterChain filterChain = null;
        if (request instanceof HttpRequest) {
        	HttpRequest req = (HttpRequest) request;
        	
            if (Globals.IS_SECURITY_ENABLED) {
                // Security: 不回收
                filterChain = new ApplicationFilterChain();
            } else {
                filterChain = (ApplicationFilterChain) req.getFilterChain();
                if (filterChain == null) {
                    filterChain = new ApplicationFilterChain();
                    req.setFilterChain(filterChain);
                }
            }
        } else {
            // 请求正在使用的调度程序
            filterChain = new ApplicationFilterChain();
        }

        filterChain.setServlet(servlet);
        filterChain.setServletSupportsAsync(wrapper.isAsyncSupported());

        // 获取此上下文的过滤器映射
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // 如果没有过滤器映射，就算完成了
        if ((filterMaps == null) || (filterMaps.length == 0))
            return filterChain;

        // 获取匹配过滤器映射所需的信息
        DispatcherType dispatcher = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);

        String requestPath = null;
        Object attribute = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null){
            requestPath = attribute.toString();
        }

        String servletName = wrapper.getName();

        // 将相关的路径映射过滤器添加到此过滤器链中
        for (FilterMap filterMap : filterMaps) {
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMap, requestPath))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // 其次添加与servlet名称匹配的过滤器
        for (FilterMap filterMap : filterMaps) {
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMap, servletName))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // Return the completed filter chain
        return filterChain;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 如果与上下文相关的请求路径与指定的筛选器映射的要求匹配，则返回 <code>true</code>;否则，返回 <code>false</code>
     *
     * @param filterMap - 正在检查过滤器映射
     * @param requestPath - 此请求的上下文相对请求路径
     */
    private static boolean matchFiltersURL(FilterMap filterMap, String requestPath) {
        // 检查特定的“*”特殊URL模式，它也匹配已命名的分派
        if (filterMap.getMatchAllUrlPatterns())
            return true;

        if (requestPath == null)
            return false;

        // 匹配上下文相对请求路径
        String[] testPaths = filterMap.getURLPatterns();

        for (String testPath : testPaths) {
            if (matchFiltersURL(testPath, requestPath)) {
                return true;
            }
        }

        // No match
        return false;
    }


    /**
     * 如果与上下文相关的请求路径与指定的筛选器映射的要求匹配，则返回 <code>true</code>;否则，返回 <code>false</code>。
     *
     * @param testPath - 正在检查URL映射
     * @param requestPath - 此请求的上下文相对请求路径
     */
    private static boolean matchFiltersURL(String testPath, String requestPath) {
        if (testPath == null)
            return false;

        // Case 1 - 精确匹配
        if (testPath.equals(requestPath))
            return true;

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return true;
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0, testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - 扩展匹配
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) && (period != requestPath.length() - 1) && ((requestPath.length() - period) == (testPath.length() - 1))) {
                return testPath.regionMatches(2, requestPath, period + 1, testPath.length() - 2);
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - 与选择过滤器无关

    }


    /**
     * 如果指定的servlet名称符合指定过滤器映射的要求，则返回<code>true</code>；否则返回<code>false</code>。
     *
     * @param filterMap - 正在检查过滤器映射
     * @param servletName - 正在检查Servlet名称
     */
    private static boolean matchFiltersServlet(FilterMap filterMap, String servletName) {
        if (servletName == null) {
            return false;
        } else if (filterMap.getMatchAllServletNames()) { // 检查特定的“*”特殊servlet名称
            return true;
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (String name : servletNames) {
                if (servletName.equals(name)) {
                    return true;
                }
            }
            return false;
        }

    }


    /**
     * 如果调度程序类型与FilterMap中指定的调度程序类型匹配，则返回true的便利方法
     */
    private static boolean matchDispatcher(FilterMap filterMap, DispatcherType type) {
        switch (type) {
            case FORWARD :
                if ((filterMap.getDispatcherMapping() & FilterMap.FORWARD) != 0) {
                    return true;
                }
                break;
            case INCLUDE :
                if ((filterMap.getDispatcherMapping() & FilterMap.INCLUDE) != 0) {
                    return true;
                }
                break;
            case REQUEST :
                if ((filterMap.getDispatcherMapping() & FilterMap.REQUEST) != 0) {
                    return true;
                }
                break;
            case ERROR :
                if ((filterMap.getDispatcherMapping() & FilterMap.ERROR) != 0) {
                    return true;
                }
                break;
            case ASYNC :
                if ((filterMap.getDispatcherMapping() & FilterMap.ASYNC) != 0) {
                    return true;
                }
                break;
        }
        return false;
    }
}
