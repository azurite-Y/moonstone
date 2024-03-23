package org.zy.moonstone.core.loaer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.loader.Loader;
import org.zy.moonstone.core.util.ExceptionUtils;
import org.zy.moonstone.core.util.ToStringUtil;
import org.zy.moonstone.core.util.compat.JreCompat;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;

/**
 * @dateTime 2022年8月22日;
 * @author zy(azurite-Y);
 * @description 
 * Classloader实现专门用于以最有效的方式处理Web应用程序，所有资源访问都通过org.apache.Catalina.WebResourceRoot实现。
 * 该类加载器支持检测已修改的Java类，可用于实现自动重载支持。
 * <p>
 * 在调用start()之前，该类加载器通过其Context的Resources子级进行配置。当需要一个新类时，将首先查阅这些参考资料来查找该类。如果不存在，则将使用系统类加载器。
 */
public class WebappLoader extends LifecycleBase implements Loader {
	private static final Logger logger = LoggerFactory.getLogger(WebappLoader.class);

	/**
	 * 由此 Loader 组件管理的类加载器
	 */
	private WebappClassLoaderBase classLoader = null;

	/**
	 * 与此 Loader 关联的 Context
	 */
	private Context context = null;

	/**
	 * 将用于配置的 ClassLoader 的“遵循标准委托模型”标志。
	 */
	private boolean delegate = false;

	/**
	 * 要使用的 ClassLoader 实现的 Java 类名。这个类应该扩展 WebappClassLoaderBase，否则，必须使用不同的加载器实现。
	 */
	private String loaderClass = ParallelWebappClassLoader.class.getName();

	/**
	 * 将创建类加载器的父类加载器
	 */
	private ClassLoader parentClassLoader = null;

	/**
	 * 此 Loader 的可重新加载标志
	 */
	private boolean reloadable = false;

	/**
	 * 在加载器中设置的类路径
	 */
	private String classpath = null;

	
	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 构造一个新的 WebappLoader。 父类加载器将由 {@link Context#getParentClassLoader()} 定义。
     */
    public WebappLoader() {}
    
    
    
	// -------------------------------------------------------------------------------------
	// getter、setter 方法
	// -------------------------------------------------------------------------------------
	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public Context getContext() {
		return context;
	}

	@Override
	public void setContext(Context context) {
		if (this.context == context) {
			return;
		}

		if (getState().isAvailable()) {
			throw new IllegalStateException("当前组件已可用，不能再设置 Context");
		}
		
		this.context = context;
        if (this.context != null) {
            this.reloadable = this.context.getReloadable();
        }
	}
	
	@Override
	public boolean getDelegate() {
		return this.delegate;
	}

	@Override
	public void setDelegate(boolean delegate) {
		this.delegate = delegate;
	}

	/**
	 * @return - 此类加载器的类名
	 */
	public String getLoaderClass() {
		return this.loaderClass;
	}

	/**
	 * 设置此类加载器的类名
	 *
	 * @param loaderClass - 新的类加载器类名
	 */
	public void setLoaderClass(String loaderClass) {
		this.loaderClass = loaderClass;
	}
	
	/**
	 * 类路径
	 * 
	 * @return The classpath
	 */
	public String getClasspath() {
		return classpath;
	}
	
	
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
	public String[] getLoaderRepositories() {
		if (classLoader == null) {
			return new String[0];
		}
		URL[] urls = classLoader.getURLs();
		String[] result = new String[urls.length];
		for (int i = 0; i < urls.length; i++) {
			result[i] = urls[i].toExternalForm();
		}
		return result;
	}

	public String getLoaderRepositoriesString() {
		String repositories[] = getLoaderRepositories();
		StringBuilder sb = new StringBuilder();
		for (String repository : repositories) {
			sb.append(repository).append(":");
		}
		return sb.toString();
	}

	@Override
	public void backgroundProcess() {
		if (reloadable && modified()) {
			try {
				// 切换为
				Thread.currentThread().setContextClassLoader(WebappLoader.class.getClassLoader());
				if (context != null) {
					context.reload();
				}
			} finally {
				if (context != null && context.getLoader() != null) {
					Thread.currentThread().setContextClassLoader(context.getLoader().getClassLoader());
				}
			}
		}
	}

	@Override
	public boolean modified() {
		return classLoader != null ? classLoader.modified() : false;
	}

	@Override
	public String toString() {
		return ToStringUtil.toString(this, context);
	}


	// -------------------------------------------------------------------------------------
	// 私有方法
	// -------------------------------------------------------------------------------------
	/**
	 * 创建关联的类加载器
	 */
	private WebappClassLoaderBase createClassLoader() throws Exception {
		Class<?> clazz = Class.forName(loaderClass);
		WebappClassLoaderBase classLoader = null;

		if (parentClassLoader == null) {
			parentClassLoader = context.getParentClassLoader();
		} else {
			context.setParentClassLoader(parentClassLoader);
		}
		Class<?>[] argTypes = { ClassLoader.class };
		Object[] args = { parentClassLoader };
		Constructor<?> constr = clazz.getConstructor(argTypes);
		classLoader = (WebappClassLoaderBase) constr.newInstance(args);

		return classLoader;
	}

	/**
	 * 配置相关的类加载器权限
	 */
	private void setPermissions() {
		if (!Globals.IS_SECURITY_ENABLED)
			return;
		if (context == null)
			return;

		// 告诉类加载器上下文的根
		ServletContext servletContext = context.getServletContext();

		// 为工作目录分配权限
		File workDir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
		if (workDir != null) {
			try {
				String workDirPath = workDir.getCanonicalPath();
				classLoader.addPermission(new FilePermission(workDirPath, "read,write"));
				classLoader.addPermission(new FilePermission(workDirPath + File.separator + "-", "read,write,delete"));
			} catch (IOException e) {
				// Ignore
			}
		}

		for (URL url : context.getResources().getBaseUrls()) {
			classLoader.addPermission(url);
		}
	}

	/**
	 * 为类路径设置适当的上下文属性.
	 */
	private void setClassPath() {
		// 验证当前的状态信息
		if (context == null)
			return;
		ServletContext servletContext = context.getServletContext();
		if (servletContext == null)
			return;

		StringBuilder classpath = new StringBuilder();

		// 从类加载器链中组装类路径信息
		ClassLoader loader = getClassLoader();

		if (delegate && loader != null) {
			// 由于启用了委派，暂时跳过 webapp 加载器
			loader = loader.getParent();
		}

		while (loader != null) { // 构建父级类路径
			if (!buildClassPath(classpath, loader)) {
				break;
			}
			loader = loader.getParent();
		}

		if (delegate) {
			// 委托已启用，返回并添加 webapp 路径
			loader = getClassLoader();
			if (loader != null) {
				// 构建当前类加载器类路径
				buildClassPath(classpath, loader);
			}
		}

		this.classpath = classpath.toString();

		// 将组装的类路径存储为 servlet 上下文属性
		servletContext.setAttribute(Globals.CLASS_PATH_ATTR, this.classpath);
	}

	/**
	 * 根据指定的类加载器构建类加载路径
	 * 
	 * @param classpath - 构建的类加载路径
	 * @param loader - 当前类加载器的父级
	 * @return 若构建成功则返回true
	 * @throws UnsupportedEncodingException - 如果需要参考字符编码，但不支持命名字符编码
	 */
	private boolean buildClassPath(StringBuilder classpath, ClassLoader loader) {
		if (loader instanceof URLClassLoader) {
			// 返回类路径
			URL repositories[] = ((URLClassLoader) loader).getURLs();
			for (URL url : repositories) {
				String repository = url.toString();
				try {
					if (repository.startsWith("file://"))
						repository = URLDecoder.decode(repository.substring(7), "UTF-8");
					else if (repository.startsWith("file:"))
						repository = URLDecoder.decode(repository.substring(5), "UTF-8");
					else
						continue;
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				
				if (repository == null)
					continue;
				if (classpath.length() > 0)
					classpath.append(File.pathSeparator);
				classpath.append(repository);
			}
		} else if (loader == ClassLoader.getSystemClassLoader()) {
			// Java 9起。内部类装入器不再扩展 URLCLassLoader
			String cp = System.getProperty("java.class.path");
			if (cp != null && cp.length() > 0) {
				if (classpath.length() > 0) {
					classpath.append(File.pathSeparator);
				}
				classpath.append(cp);
			}
			return false;
		} else {
			// 忽略未知的 ClassLoader
			if (!JreCompat.isGraalAvailable()) {
				logger.info("WebappLoader 构建类路径失败, 未知的 ClassLoader, by class: {}", loader.getClass());
			}
			return false;
		}
		return true;
	}


	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	@Override
	protected void initInternal() throws LifecycleException {}

	/**
	 * @exception LifecycleException - 如果此组件检测到阻止使用此组件的致命错误
	 */
	@Override
	protected void startInternal() throws LifecycleException {
		if (logger.isDebugEnabled())
			logger.debug("WebappLoader Starting");

		if (context.getResources() == null) {
			logger.info("WebappLoader 设置的 WebResourceRoot 不能为 null");
			setState(LifecycleState.STARTING);
			return;
		}

		// 根据当前的存储库列表构建一个类加载器
		try {
			classLoader = createClassLoader();
			classLoader.setResources(context.getResources());
			classLoader.setDelegate(this.delegate);

			setContext(context);
			
			classLoader.start();

			// 配置存储库
			setClassPath();

			setPermissions();

		} catch (Throwable t) {
			t = ExceptionUtils.unwrapInvocationTargetException(t);
			ExceptionUtils.handleThrowable(t);
			throw new LifecycleException("WebappLoader StartError", t);
		}

		setState(LifecycleState.STARTING);
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		if (logger.isDebugEnabled())
			logger.debug("WebappLoader Stopping");

		setState(LifecycleState.STOPPING);

		// 根据需要删除 Context 属性
		ServletContext servletContext = context.getServletContext();
		servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);

		// 如果有的话，停止和销毁当前的类加载器
		if (classLoader != null) {
			try {
				classLoader.stop();
			} finally {
				classLoader.destroy();
			}
		}

		classLoader = null;
	}

	@Override
	protected void destroyInternal() throws LifecycleException {}
}
