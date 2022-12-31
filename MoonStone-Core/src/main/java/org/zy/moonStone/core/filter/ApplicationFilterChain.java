package org.zy.moonStone.core.filter;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.security.SecurityUtil;
import org.zy.moonStone.core.servlets.ApplicationDispatcher;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年10月5日;
 * @author zy(azurite-Y);
 * @description FilterChain的实现，用于管理针对特定请求的一组过滤器的执行。当一组已定义的过滤器全部被执行时，对doFilter()的下一次调用将执行servlet的service()方法本身。
 */
public final class ApplicationFilterChain implements FilterChain {
	private static final ThreadLocal<ServletRequest> lastServicedRequest;
    private static final ThreadLocal<ServletResponse> lastServicedResponse;

    static {
        if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
            lastServicedRequest = new ThreadLocal<>();
            lastServicedResponse = new ThreadLocal<>();
        } else {
            lastServicedRequest = null;
            lastServicedResponse = null;
        }
    }

    /** 增量 */
    public static final int INCREMENT = 10;

    /** Filters */
    private ApplicationFilterConfig[] filters = new ApplicationFilterConfig[0];

    /** 用于维持过滤器链中的当前位置的整数 */
    private int pos = 0;

    /** 给出链中当前过滤器数量的整数 */
    private int n = 0;

    /** 要由该链执行的Servlet实例 */
    private Servlet servlet = null;

    /** 相关的servlet实例是否支持异步处理? */
    private boolean servletSupportsAsync = false;

    /** 打开SecurityManager并调用 <code>doFilter</code> 时使用的静态类数组 */
    private static final Class<?>[] classType = new Class[]{ ServletRequest.class, ServletResponse.class, FilterChain.class };

    /** 打开SecurityManager并调用<code>service</code>时使用的静态类数组 */
    private static final Class<?>[] classTypeUsedInService = new Class[]{ ServletRequest.class, ServletResponse.class };
	
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
		if( Globals.IS_SECURITY_ENABLED ) {
            final ServletRequest req = request;
            final ServletResponse res = response;
            try {
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<Void>() {
                        @Override
                        public Void run()
                            throws ServletException, IOException {
                            internalDoFilter(req,res);
                            return null;
                        }
                    }
                );
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                else if (e instanceof IOException)
                    throw (IOException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new ServletException(e.getMessage(), e);
            }
        } else {
            internalDoFilter(request,response);
        }		
	}

	
	private void internalDoFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
		if (pos < n) {
			ApplicationFilterConfig filterConfig = filters[pos++];
			try {
				Filter filter = filterConfig.getFilter();

				if (request.isAsyncSupported() && !( filterConfig.getFilterDef().getAsyncSupported()) ) {
					request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR, Boolean.FALSE);
				}
				if( Globals.IS_SECURITY_ENABLED ) {
					final ServletRequest req = request;
					final ServletResponse res = response;
					/**
					 * 返回一个java.security.Principal对象，其中包含当前经过身份验证用户的名称。如果用户没有经过身份验证，该方法返回null。
					 * 
					 * @return 包含发出此请求的用户名的 java.security.Principal; 如果用户没有经过身份验证，则为空
					 */
					Principal principal = ((HttpServletRequest) req).getUserPrincipal();

					Object[] args = new Object[]{req, res, this};
					SecurityUtil.doAsPrivilege ("doFilter", filter, classType, args, principal);
				} else {
					filter.doFilter(request, response, this);
				}
			} catch (IOException | ServletException | RuntimeException e) {
				throw e;
			} catch (Throwable e) {
				e = ExceptionUtils.unwrapInvocationTargetException(e);
				ExceptionUtils.handleThrowable(e);
				throw new ServletException("filterChain filter调度异常", e);
			}
			return;
		}

		// 脱离FIlter调用链后调用Servlet实例
		try {
			if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
				lastServicedRequest.set(request);
				lastServicedResponse.set(response);
			}

			if (request.isAsyncSupported() && !servletSupportsAsync) {
				request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR, Boolean.FALSE);
			}
			// 从此处使用可能已包装的请求
			if ((request instanceof HttpServletRequest) && (response instanceof HttpServletResponse) && Globals.IS_SECURITY_ENABLED ) {
				final ServletRequest req = request;
				final ServletResponse res = response;
				Principal principal = ((HttpServletRequest) req).getUserPrincipal();
				Object[] args = new Object[]{req, res};
				SecurityUtil.doAsPrivilege("service", servlet, classTypeUsedInService, args, principal);
			} else {
				servlet.service(request, response);
			}
		} catch (IOException | ServletException | RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			e = ExceptionUtils.unwrapInvocationTargetException(e);
			ExceptionUtils.handleThrowable(e);
			throw new ServletException("filterChain servlet调度异常", e);
		} finally {
			if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
				lastServicedRequest.set(null);
				lastServicedResponse.set(null);
			}
		}
	}
	
	
    /**
     * 释放对该链执行的过滤器和包装器的引用
     */
    public void release() {
        for (int i = 0; i < n; i++) {
            filters[i] = null;
        }
        n = 0;
        pos = 0;
        servlet = null;
        servletSupportsAsync = false;
    }
    
	
	/**
	 * 从当前线程传递给servlet的最后一个请求
     *
     * @return 要服务的最后一个请求
     */
    public static ServletRequest getLastServicedRequest() {
        return lastServicedRequest.get();
    }


    /**
     * 从当前线程传递给servlet的最后一个响应
     *
     * @return 要服务的最后一个响应
     */
    public static ServletResponse getLastServicedResponse() {
        return lastServicedResponse.get();
    }
    
    
    /**
     * 设置将在此链的末尾执行的servlet
     *
     * @param servlet - 要执行的servlet的包装器
     */
    void setServlet(Servlet servlet) {
        this.servlet = servlet;
    }


    void setServletSupportsAsync(boolean servletSupportsAsync) {
        this.servletSupportsAsync = servletSupportsAsync;
    }
    
    
    /**
     * 向将在此链中执行的过滤器集添加一个过滤器
     *
     * @param filterConfig - 要执行的servlet的FilterConfig
     */
    void addFilter(ApplicationFilterConfig filterConfig) {
        // 防止同一过滤器被添加多次
        for(ApplicationFilterConfig filter:filters)
            if(filter==filterConfig)
                return;

        if (n == filters.length) {
            ApplicationFilterConfig[] newFilters = new ApplicationFilterConfig[n + INCREMENT];
            System.arraycopy(filters, 0, newFilters, 0, n);
            filters = newFilters;
        }
        filters[n++] = filterConfig;
    }
    
    /**
     * 标识此过滤器链中不支持异步的过滤器(如果有的话)。
     *
     * @param result - 在此FilterChain中，不支持async的每个filter的全限定类名将被添加到该Set中
     */
    public void findNonAsyncFilters(Set<String> result) {
        for (int i = 0; i < n ; i++) {
            ApplicationFilterConfig filter = filters[i];
            if ( !(filter.getFilterDef().getAsyncSupported()) ) {
                result.add(filter.getFilterClass());
            }
        }
    }
}
