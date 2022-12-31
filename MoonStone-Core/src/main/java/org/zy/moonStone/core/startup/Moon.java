package org.zy.moonStone.core.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.container.Server;

/**
 * @dateTime 2021年12月30日;
 * @author zy(azurite-Y);
 * @description
 * 启动/关闭 Moon 的shell程序。可以识别以下命令行选项（未实现）:
 * <ul>
 * <li><b>-config {pathname}</b> - 设置要处理的配置文件的路径名。如果指定了相对路径，它将被解释为相对于“catalina.base”系统属性指定的目录路径名。[conf/server.xml]
 * <li><b>-help</b> - 显示使用信息</li>
 * <li><b>-nonaming</b> - 禁用命名支持</li>
 * <li><b>configtest</b - 尝试测试配置</li>
 * <li><b>start</b> - 启动 Moon 实例</li>
 * <li><b>stop</b> - 停止当前正在运行的 Moon 实例</li>
 * </ul>
 */
public class Moon {
    private static final Logger logger = LoggerFactory.getLogger(Moon.class);

    public static final String SERVER_XML = "conf/server.xml";

	/**
     * 使用等待的标识
     */
    protected boolean await = false;

    /**
     * 服务器配置文件的路径名
     */
    protected String configFile = SERVER_XML;

    // 应移动到嵌入式
    /**
     * 此服务器的共享扩展类加载器
     */
    protected ClassLoader parentClassLoader = Moon.class.getClassLoader();

    /**
     * 正在启动或停止的服务器组件
     */
    protected Server server = null;

    /**
     * 使用关机命令的标识
     */
    protected boolean useShutdownHook = true;

    /**
     * 关机线程
     */
    protected Thread shutdownHook = null;

    /**
     * 防止重复加载
     */
    protected boolean loaded = false;
    
	
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }
    
    /**
     * 设置共享扩展类加载器
     *
     * @param parentClassLoader - 共享扩展类加载器
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }
    
    public Server getServer() {
        return server;
    }
    
    /**
     * 启动新的服务器实例
     */
    public void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        long t1 = System.nanoTime();

//        initDirs();

        // Set configuration source
//        ConfigFileLoader.setSource(new CatalinaBaseConfigurationSource(Bootstrap.getCatalinaBaseFile(), getConfigFile()));
//        File file = configFile();

        getServer().setMoon(this);
//        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
//        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        // Stream redirection
//        initStreams();

        // Start the new server
        try {
            getServer().init();
        } catch (LifecycleException e) {
            if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
                throw new java.lang.Error(e);
            } else {
                logger.error("Moon 初始化错误", e);
            }
        }

        long t2 = System.nanoTime();
        if(logger.isInfoEnabled()) {
            logger.info("Moon 初始化错误", Long.valueOf((t2 - t1) / 1000000));
        }
    }
}
