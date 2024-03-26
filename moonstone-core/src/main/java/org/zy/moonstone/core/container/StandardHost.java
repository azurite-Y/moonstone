package org.zy.moonstone.core.container;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.zy.moonstone.core.LifecycleEvent;
import org.zy.moonstone.core.container.valves.StandardHostValve;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Container;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.container.Host;
import org.zy.moonstone.core.interfaces.container.Lifecycle;
import org.zy.moonstone.core.interfaces.container.LifecycleListener;
import org.zy.moonstone.core.interfaces.container.Valve;
import org.zy.moonstone.core.loaer.WebappClassLoaderBase;
import org.zy.moonstone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月2日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardHost extends ContainerBase implements Host {
	/**
     * 此主机的别名集.
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();

    /**
     * 此主机的应用程序根目录.
     */
    private String appBase = "webapps";
    
    private volatile File appBaseFile = null;

    /**
     * 主机的默认配置路径
     */
    private volatile File hostConfigBase = null;

    /**
     * 此主机的自动部署标志.
     */
    private boolean autoDeploy = true;

    /**
     * 已部署的web应用程序的默认上下文配置类的Java类名.
     */
    private String configClass = "org.zy.moonstone.core.container.StandardContext";

    /**
     * 已部署的web应用程序的默认上下文实现类的Java类名.
     */
    private String contextClass = "org.zy.moonstone.core.container.context.StandardContext";

    /**
     * 此主机的启动时部署标志.
     */
    private boolean deployOnStartup = true;

    /**
     * 已部署的web应用程序的默认错误报告器实现类的Java类名.
     */
    private String errorReportValveClass = "org.zy.moonstone.core.container.valves.ErrorReportValve";

    /**
     * 应用程序的基础工作目录.
     */
    private String workDir = null;

    /**
     * 是否在启动时创建appBase目录
     */
    private boolean createDirs = true;

    /**
     * 跟踪子web应用程序的类加载器，这样可以检测到内存泄漏.
     */
    private final Map<ClassLoader, String> childClassLoaders = new WeakHashMap<>();

    /**
     * {@link #appBase}中任何与此模式匹配的文件或目录都将被自动部署过程忽略(包括 {@link #deployOnStartup} 和 {@link #autoDeploy})
     */
    private Pattern deployIgnore = null;

    /** 取消部署旧版本*/
    private boolean undeployOldVersions = false;
    
    /** 如果Servlet启动失败则Ctx是否失败 */
    private boolean failCtxIfServletStartFails;

    
	// ------------------------------------------------------------- 
    // 构造器
	// -------------------------------------------------------------
	/**
	 * 使用默认的基本Valve创建一个新的StandardHost组件.
	 */
	public StandardHost() {
		super();
		pipeline.setBasic(new StandardHostValve());
	}
	
	// ------------------------------------------------------------- 
	// 属性
	// -------------------------------------------------------------
	@Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }

    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }

    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }

    @Override
	public boolean getDeployOnStartup() {
		return this.deployOnStartup;
	}
    
    @Override
    public String getAppBase() {
        return this.appBase;
    }

    @Override
    public File getAppBaseFile() {
        if (appBaseFile != null) {
            return appBaseFile;
        }

        File file = new File(getAppBase());

        if (!file.isAbsolute()) {
        	// 设置为绝对路径
            file = new File(getMoonBase(), file.getPath());
        }

        // 如果可能的话，使其规范化
        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
            // Ignore
        }

        this.appBaseFile = file;
        return file;
    }

    @Override
    public void setAppBase(String appBase) {
        if (StringUtils.isBlank(appBase)) {
            logger.warn("设置此主机的应用程序根目录不能为空串");
        }
        this.appBase = appBase;
        this.appBaseFile = null;
    }
    
    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }
    
    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }
    
    @Override
    public boolean getAutoDeploy() {
        return this.autoDeploy;
    }
    
    @Override
    public void setAutoDeploy(boolean autoDeploy) {
        this.autoDeploy = autoDeploy;
    }
    
    /**
     * @return 新web应用程序的Context实现类的Java类名.
     */
    public String getContextClass() {
        return this.contextClass;
    }
    
    /**
     * 为新的web应用程序设置Context实现类的Java类名.
     *
     * @param contextClass - 新的上下文实现类
     */
    public void setContextClass(String contextClass) {
        this.contextClass = contextClass;
    }
    
    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {
        this.deployOnStartup = deployOnStartup;
    }

    /**
     * @return 新的web应用程序的错误报告Valve类的Java类名.
     */
    public String getErrorReportValveClass() {
        return this.errorReportValveClass;
    }

    /**
     * 为新的web应用程序设置错误报告Valve类的Java类名.
     *
     */
    public void setErrorReportValveClass(String errorReportValveClass) {
        this.errorReportValveClass = errorReportValveClass;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("设置的容器名不能为null");

        this.name = name.toLowerCase(Locale.ENGLISH);
    }


    /**
     * @return 主机基础工作目录.
     */
    public String getWorkDir() {
        return workDir;
    }

    /**
     * 设置主机基础工作目录.
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    /**
     * @return 这个正则表达式定义了在主机的getAppBase中会被自动部署过程忽略的文件和目录.
     */
    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }

    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }

    @Override
    public void setDeployIgnore(String deployIgnore) {
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
    }


    /**
     * @return 如果webapp启动失败，Servlet启动失败，则为true
     */
    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }


    /**
     * 改变web应用程序启动时Servlet启动错误的行为.
     * @param failCtxIfServletStartFails - false表示忽略web应用程序启动时声明的servlet错误
     */
    public void setFailCtxIfServletStartFails( boolean failCtxIfServletStartFails) {
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
    }

    // --------------------------------------------------------- 公共方法 ---------------------------------------------------------
    @Override
    public void addAlias(String alias) {
        alias = alias.toLowerCase(Locale.ENGLISH);
        synchronized (aliasesLock) {
            // 跳过重复的别名
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias))
                    return;
            }
            // 将此别名添加到列表中
            String newAliases[] = Arrays.copyOf(aliases, aliases.length + 1);
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        fireContainerEvent(Context.ADD_ALIAS_EVENT, alias);
    }

    @Override
    public void addChild(Container child) {
        if (!(child instanceof Context)) throw new IllegalArgumentException("添加的子容器不是Context类型");

        child.addLifecycleListener(new MemoryLeakTrackingListener());
        
        super.addChild(child);
    }

    @Override
    public String[] findAliases() {
        synchronized (aliasesLock) {
            return this.aliases;
        }
    }

    @Override
    public void removeAlias(String alias) {
        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {
            // 确保该别名当前存在
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified alias
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;
        }
    }

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        String errorValveStr = getErrorReportValveClass();
        if ((errorValveStr != null) && (!errorValveStr.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValveStr.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    Valve valve = (Valve) Class.forName(errorValveStr).getConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                logger.error("无效的错误报告Valve类的Java类名，by name：" + errorValveStr, t);
            }
        }
        super.startInternal();
    }

    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    
    /**
     * 尝试识别具有类装入器内存泄漏的上下文。这通常在上下文重新加载时触发。注意:此方法试图强制进行完整的垃圾回收。在生产系统上使用时应格外小心.
     *
     * @return 可能泄漏的上下文的列表
     */
    public String[] findReloadedContextMemoryLeaks() {
        System.gc();
        
        List<String> result = new ArrayList<>();
        for (Map.Entry<ClassLoader, String> entry : childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).getState().isAvailable()) {
                    result.add(entry.getValue());
                }
            }
        }

        return result.toArray(new String[result.size()]);
    }
    
    /**
     * 为了确保不管{@link Context} 实现，每次上下文启动时都会保存一个类装入器的记录.
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(), context.getServletContext().getContextPath());
                }
            }
        }
    }

	@Override
	public File getConfigBaseFile() {
		// TODO 自动生成的方法存根
		return null;
	}

	@Override
	public String getConfigClass() {
		// TODO 自动生成的方法存根
		return null;
	}

	@Override
	public void setConfigClass(String configClass) {
		// TODO 自动生成的方法存根
		
	}
}
