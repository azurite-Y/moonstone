package org.zy.moonStone.core.container;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.MultipartConfig;

import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.container.context.StandardContext;
import org.zy.moonStone.core.container.context.StandardWrapperFacade;
import org.zy.moonStone.core.container.valves.StandardWrapperValve;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.InstanceManager;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.ContainerServlet;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月6日;
 * @author zy(azurite-Y);
 * @description 代表单个servlet定义的Wrapper接口的标准实现
 */
public class StandardWrapper extends ContainerBase implements ServletConfig, Wrapper {
	protected static final String[] DEFAULT_SERVLET_METHODS = new String[] {"GET", "HEAD", "POST" };

	/**
	 * 此 servlet 可用的日期和时间（自纪元以来的毫秒数），如果 servlet 可用，则为零。
	 * 如果此值等于 Long.MAX_VALUE ，则认为此 servlet 永久不可用。
	 */
	protected long available = 0L;
	
	/**
	 * 当前处于活动状态的Servlet实例计数(即使它们用于同一实例).
	 */
	protected final AtomicInteger countAllocated = new AtomicInteger(0);

	/**
	 * 与此 Wrapper 关联的镜像，Servlet对象持有的ServletConfig对象.
	 */
	protected final StandardWrapperFacade facade = new StandardWrapperFacade(this);

	/**
	 * 这个servlet的(单个)可能未初始化的实例.
	 */
	protected volatile Servlet instance = null;

	/**
	 * 指示此实例是否已初始化的标志
	 */
	protected volatile boolean instanceInitialized = false;

	/**
	 * 这个servlet的load-on-startup计数值(负值表示在第一次调用时加载)
	 */
	protected int loadOnStartup = -1;

	/**
	 * 与包装器关联的映射.
	 */
	protected final List<String> mappings = new ArrayList<>();

	/**
	 * 这个servlet的初始化参数，以参数名称为键值.
	 */
	protected Map<String, String> parameters = new HashMap<>();

	/**
	 * 这个servlet的run-as标识
	 */
	protected String runAs = null;

	/**
	 * 通知序列号
	 */
	protected long sequenceNumber = 0;

	/**
	 * 此servlet的完全限定servlet类名
	 */
	protected String servletClass = null;

	/**
	 * 是否加载servlet实例
	 */
	protected volatile boolean unloading = false;

	/**
	 * STM 实例的最大数量
	 */
	protected int maxInstances = 20;

	/**
	 * 当前为 STM servlet 加载的实例数
	 */
	protected int nInstances = 0;

	/**
	 * 包含 STM 实例的堆栈
	 */
	protected Stack<Servlet> instancePool = new Stack<>();

	/**
	 * servlet卸载的等待时间（毫秒）
	 */
	protected long unloadDelay = 2000;

    protected boolean swallowOutput = false;
	
	protected StandardWrapperValve standardWrapperValve;

	protected long loadTime=0;

	protected int classLoadTime=0;

	/**
	 * Multipart config
	 */
	protected MultipartConfigElement multipartConfigElement = null;

	/**
	 * 异步支持
	 */
	protected boolean asyncSupported = false;

	/**
	 * 是否启用
	 */
	protected boolean enabled = true;

	private boolean overridable = false;

	/**
	 * 当启用SecurityManager和Servlet时使用的静态类数组。调用初始化
	 */
	protected static Class<?>[] classType = new Class[]{ServletConfig.class};

	private final ReentrantReadWriteLock parametersLock = new ReentrantReadWriteLock();

	private final ReentrantReadWriteLock mappingsLock = new ReentrantReadWriteLock();

	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	/**
	 * 使用默认的基本阀门创建一个新的StandardWrapper组件.
	 */
	public StandardWrapper() {
		super();
		standardWrapperValve=new StandardWrapperValve();
		pipeline.setBasic(standardWrapperValve);
	}

	// -------------------------------------------------------------------------------------
	// Wrapper 方法
	// -------------------------------------------------------------------------------------
	@Override
	public boolean isOverridable() {
		return overridable;
	}

	@Override
	public void setOverridable(boolean overridable) {
		this.overridable = overridable;
	}

	@Override
	public long getAvailable() {
		return this.available;
	}

	@Override
	public void setAvailable(long available) {
		if (available > System.currentTimeMillis())
			this.available = available;
		else
			this.available = 0L;
	}

	@Override
	public int getLoadOnStartup() {
		return loadOnStartup < 0 ? Integer.MAX_VALUE : this.loadOnStartup;
	}

	@Override
	public void setLoadOnStartup(int value) {
		this.loadOnStartup = value;
	}

    /**
     * 设置启动时加载顺序值（负值表示第一次调用时加载）.
     *
     * @param value - 启动时的新负载值
     */
	public void setLoadOnStartupString(String value) {
		try {
			setLoadOnStartup(Integer.parseInt(value));
		} catch (NumberFormatException e) {
			setLoadOnStartup(0);
		}
	}

	/**
	 * @return 使用单线程模型servlet时将分配的最大实例数.
	 */
	public int getMaxInstances() {
		return this.maxInstances;
	}

	/**
	 * 设置使用单线程模型servlet时将分配的最大实例数.
	 * 
	 * @param maxInstances - 将分配的最大实例数
	 */
	public void setMaxInstances(int maxInstances) {
		this.maxInstances = maxInstances;
	}

	@Override
	public String getRunAs() {
		return this.runAs;
	}
	
	@Override
	public void setRunAs(String runAs) {
		this.runAs = runAs;
	}
	
	@Override
	public String getServletClass() {
		return this.servletClass;
	}
	
	@Override
	public void setServletClass(String servletClass) {
		this.servletClass = servletClass;
	}
	
	/**
	 * 设置此servlet的名称。这是普通容器的别名
	 *
	 */
	public void setServletName(String name) {
		setName(name);
	}

	@Override
	public boolean isUnavailable() {
		if (!isEnabled())
			return true;
		else if (available == 0L)
			return false;
		else if (available <= System.currentTimeMillis()) {
			available = 0L;
			return false;
		} else
			return true;
	}

	@Override
	public Servlet getServlet() {
		return instance;
	}

	@Override
	public void setServlet(Servlet servlet) {
		instance = servlet;
	}

	@Override
	public Servlet allocate() throws ServletException {
		// 如果当前正在卸载这个servlet，抛出一个异常
		if (unloading) {
			throw new ServletException("StandardWrapper当前正在卸载这个Servlet实例，by name：" + getName());
		}

		boolean newInstance = false;

		// 如果需要，加载并初始化我们的实例
		if (instance == null || !instanceInitialized) {
			synchronized (this) {
				if (instance == null) {
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("未分配servlet实例，by name：" + getName());
						}

						instance = loadServlet();
						newInstance = true;
					} catch (ServletException e) {
						throw e;
					} catch (Throwable e) {
						ExceptionUtils.handleThrowable(e);
						throw new ServletException("分配Servlet实例异常，by name：" + getName(), e);
					}
				}
				if (!instanceInitialized) {
					initServlet(instance);
				}
			}

			if (newInstance) {
				synchronized (instancePool) {
					instancePool.push(instance);
					nInstances++;

					// 以原子方式将当前值增加1
					countAllocated.incrementAndGet();
					return instance;
				}
			}
		}

		synchronized (instancePool) {
			while (countAllocated.get() >= nInstances) {
				// 如果可能，分配一个新实例，否则等待
				if (nInstances < maxInstances) {
					try {
						instancePool.push(loadServlet());
						nInstances++;
					} catch (ServletException e) {
						throw e;
					} catch (Throwable e) {
						ExceptionUtils.handleThrowable(e);
						throw new ServletException("standardWrapper分配Servlet异常", e);
					}
				} else {
					try {
						instancePool.wait();
					} catch (InterruptedException e) {
						// Ignore
					}
				}
			}
			countAllocated.incrementAndGet();
			return instancePool.pop();
		}
	}

	@Override
	public void deallocate(Servlet servlet) throws ServletException {
		// 解锁并释放此实例
		synchronized (instancePool) {
			countAllocated.decrementAndGet();
			instancePool.push(servlet);
			instancePool.notify();
		}
	}

	@Override
	public String findInitParameter(String name) {
		parametersLock.readLock().lock();
		try {
			return parameters.get(name);
		} finally {
			parametersLock.readLock().unlock();
		}
	}

	@Override
	public String[] findInitParameters() {
		parametersLock.readLock().lock();
		try {
			String results[] = new String[parameters.size()];
			return parameters.keySet().toArray(results);
		} finally {
			parametersLock.readLock().unlock();
		}
	}

	@Override
	public void load() throws ServletException {
		instance = loadServlet();

		if (!instanceInitialized) {
			initServlet(instance);
		}
	}

	/**
	 * 加载并初始化这个servlet的一个实例(如果还没有初始化过的实例)。
	 * 例如，它可以用来加载在部署描述符中标记为在服务器启动时加载的servlet。
	 * @return 加载的Servlet实例
	 * @throws ServletException - 一个Servlet加载错误
	 */
	public synchronized Servlet loadServlet() throws ServletException {
		// 如果已经有实例或实例池，则无需执行任何操作
		if (instance != null) return instance;

		Servlet servlet;
		try {
			long t1=System.currentTimeMillis();
			if (servletClass == null) {
				unavailable(null);
				throw new ServletException("未指定Servlet Class，by name：" + getName());
			}

			InstanceManager instanceManager = ((StandardContext)getParent()).getInstanceManager();
			try {
				servlet = (Servlet) instanceManager.newInstance(servletClass);
			} catch (ClassCastException e) {
				unavailable(null);
				throw new ServletException("指定的Servlet全限定类名无效，by class：" + servletClass, e);
			} catch (Throwable e) {
				e = ExceptionUtils.unwrapInvocationTargetException(e);
				ExceptionUtils.handleThrowable(e);
				unavailable(null);

				if(logger.isDebugEnabled()) {
					logger.debug("Servlet实例化异常，by class：" + servletClass, e);
				}
				throw new ServletException("指定的Servlet全限定类名无效，by class：" + servletClass, e);
			}

			if (multipartConfigElement == null) {
				MultipartConfig annotation = servlet.getClass().getAnnotation(MultipartConfig.class);
				if (annotation != null) {
					multipartConfigElement = new MultipartConfigElement(annotation);
				}
			}

			// ContainerServlet实例的特殊处理
			//注意：InstanceManager检查是否允许应用程序加载ContainerServlet
			if (servlet instanceof ContainerServlet) {
				((ContainerServlet) servlet).setWrapper(this);
			}

			classLoadTime=(int) (System.currentTimeMillis() -t1);

			initServlet(servlet);

			fireContainerEvent("load", this);

			loadTime=System.currentTimeMillis() -t1;
		} finally {}
		return servlet;
	}


	private synchronized void initServlet(Servlet servlet) throws ServletException {
		if (instanceInitialized ) return;

		// 调用这个servlet的初始化方法
		try {
			servlet.init(facade);
			instanceInitialized = true;
		} catch (UnavailableException f) {
			unavailable(f);
			throw f;
		} catch (ServletException f) {
			throw f;
		} catch (Throwable f) {
			ExceptionUtils.handleThrowable(f);
			getServletContext().log("Servlet初始化异常，by name：" + getName(), f);
		}
	}

	@Override
	public void removeInitParameter(String name) {
		parametersLock.writeLock().lock();
		try {
			parameters.remove(name);
		} finally {
			parametersLock.writeLock().unlock();
		}
		fireContainerEvent("removeInitParameter", name);
	}

	@Override
	public void unavailable(UnavailableException unavailable) {
		getServletContext().log("Servlet实例不可用，by name：" + getName());
		if (unavailable == null)
			setAvailable(Long.MAX_VALUE);
		else if (unavailable.isPermanent())
			setAvailable(Long.MAX_VALUE);
		else {
			int unavailableSeconds = unavailable.getUnavailableSeconds();
			if (unavailableSeconds <= 0) unavailableSeconds = 60;
			setAvailable(System.currentTimeMillis() + (unavailableSeconds * 1000L));
		}
	}

	@Override
	public void unload() throws ServletException {
		if (instance == null) return;
		unloading = true;

		if (countAllocated.get() > 0) {
			int nRetries = 0;
			long delay = unloadDelay / 20;
			while ((nRetries < 21) && (countAllocated.get() > 0)) {
				if ((nRetries % 10) == 0) {
					logger.info("Servlet实例卸载等待. count: [{}], name: [{}]", countAllocated.toString(), getName());
				}
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					// Ignore
				}
				nRetries++;
			}
		}

		if (instanceInitialized) {
			// 调用Servlet的destroy()方法
			try {
				instance.destroy();
			} catch (Throwable t) {
				t = ExceptionUtils.unwrapInvocationTargetException(t);
				ExceptionUtils.handleThrowable(t);
				// 初始化变量和发布事件
				instance = null;
				instancePool.clear();
				nInstances = 0;
				fireContainerEvent("unload", this);
				unloading = false;
				throw new ServletException("Servlet销毁异常，by name：" + getName(), t);
			} finally {}
		}

		// 注销已销毁的实例
		instance = null;
		instanceInitialized = false;

		if (instancePool != null) {
			try {
				while (!instancePool.isEmpty()) {
					Servlet s = instancePool.pop();
					s.destroy();
				}
			} catch (Throwable t) {
				t = ExceptionUtils.unwrapInvocationTargetException(t);
				ExceptionUtils.handleThrowable(t);
				instancePool = null;
				nInstances = 0;
				unloading = false;
				fireContainerEvent("unload", this);
				throw new ServletException("Servlet销毁异常，by name：" + getName(), t);
			}
			nInstances = 0;
		}

		unloading = false;
		fireContainerEvent("unload", this);
	}

	@Override
	public void incrementErrorCount() {
		this.standardWrapperValve.incrementErrorCount();
	}

	@Override
	public MultipartConfigElement getMultipartConfigElement() {
		return multipartConfigElement;
	}

	public int getRequestCount() {
        return standardWrapperValve.getRequestCount();
    }

    public int getErrorCount() {
        return standardWrapperValve.getErrorCount();
    }
	
	@Override
	public void setMultipartConfigElement(MultipartConfigElement multipartConfig) {
		this.multipartConfigElement = multipartConfig;
	}

	@Override
	public boolean isAsyncSupported() {
		return asyncSupported;
	}

	@Override
	public void setAsyncSupported(boolean asyncSupported) {
		this.asyncSupported = asyncSupported;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public String[] getServletMethods() throws ServletException {
		Set<String> allow = new HashSet<>();
		allow.add("OPTIONS");
		allow.add("TRACE");
		allow.add("GET");
		allow.add("HEAD");
		allow.add("POST");
		allow.add("PUT");
		allow.add("DELETE");
		return allow.toArray(new String[] {});
	}
	
	@Override
	public void addMapping(String mapping) {
		mappingsLock.writeLock().lock();
		try {
			mappings.add(mapping);
		} finally {
			mappingsLock.writeLock().unlock();
		}
	}
	
	@Override
	public String[] findMappings() {
		mappingsLock.readLock().lock();
		try {
			return mappings.toArray(new String[mappings.size()]);
		} finally {
			mappingsLock.readLock().unlock();
		}
	}
	
	@Override
	public void removeMapping(String mapping) {
		mappingsLock.writeLock().lock();
		try {
			mappings.remove(mapping);
		} finally {
			mappingsLock.writeLock().unlock();
		}
		if(parent.getState().equals(LifecycleState.STARTED)) 
			fireContainerEvent(Wrapper.REMOVE_MAPPING_EVENT, mapping);
	}
	
	// -------------------------------------------------------------------------------------
	// ServletConfig 方法
	// -------------------------------------------------------------------------------------
	@Override
	public String getServletName() {
		return getName();
	}

	@Override
	public ServletContext getServletContext() {
		if (parent == null)
			return null;
		else if (!(parent instanceof Context))
			return null;
		else
			return ((Context) parent).getServletContext();
	}

	@Override
	public String getInitParameter(String name) {
		return findInitParameter(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		parametersLock.readLock().lock();
		try {
			return Collections.enumeration(parameters.keySet());
		} finally {
			parametersLock.readLock().unlock();
		}
	}

	// -------------------------------------------------------------------------------------
	// Container 方法
	// -------------------------------------------------------------------------------------
	@Override
	public void setParent(Container container) {
		if ((container != null) && !(container instanceof Context)) {
			throw new IllegalArgumentException("设置的服容器不是Context的子类实现");

		}
		if (container instanceof StandardContext) {
			swallowOutput = ((StandardContext)container).getSwallowOutput();
			unloadDelay = ((StandardContext)container).getUnloadDelay();
		}
		super.setParent(container);
	}
	
	@Override
	public void addChild(Container child) {
		throw new IllegalStateException("此容器不能有子容器");
	}

	@Override
	public void addInitParameter(String name, String value) {
		parametersLock.writeLock().lock();
		try {
			parameters.put(name, value);
		} finally {
			parametersLock.writeLock().unlock();
		}
	}
	
	// startInternal() 方法执行父类逻辑
	@Override
	protected synchronized void startInternal() throws LifecycleException {
		// 在此重置可能的值，由 #stopInternal() 方法设置
		this.available = 0L;
		super.startInternal();
		this.facade.updateServletContext();
	}
	
	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		setAvailable(Long.MAX_VALUE);

		try {
			unload();
		} catch (ServletException e) {
			getServletContext().log("Servlet卸载异常，by name：" + getName(), e);
		}
		super.stopInternal();
	}
	
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    public long getProcessingTime() {
        return standardWrapperValve.getProcessingTime();
    }
    public long getMaxTime() {
        return standardWrapperValve.getMaxTime();
    }

    public long getMinTime() {
        return standardWrapperValve.getMinTime();
    }

    public long getLoadTime() {
        return loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    protected Method[] getAllDeclaredMethods(Class<?> c) {
        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods.length == 0) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods = new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0, parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length, thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }
    
    /**
     * 从servlet异常中提取根本 exception.
     *
     * @param e - servlet异常
     * @return servlet异常的根本 exception.
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // 额外积极的rootCause查找
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null)
                rootCause = rootCauseCheck;
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }
}
