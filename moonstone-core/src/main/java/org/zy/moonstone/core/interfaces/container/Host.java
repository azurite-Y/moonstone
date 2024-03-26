package org.zy.moonstone.core.interfaces.container;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description
 * 主机是一个容器，在catalina servlet引擎中表示一个虚拟主机
 * <p>
 * 通常，当部署连接到web服务器(如Apache)的Catalina时，使用主机，因为连接器将利用web服务器的设施来确定哪个上下文(或者甚至是哪个包装器)应该被用来处理这个请求。
 * 连接到主机上的父容器通常是一个引擎，但也可能是一些其他的实现，或者如果没有必要的话可以省略。
 * 附加到Host的子容器通常是Context的实现(表示一个单独的servlet上下文)
 */
public interface Host extends Container {
    // ----------------------------------------------------- 常量 -----------------------------------------------------
    /**
     * 当一个新的别名被addAlias()添加时发送的ContainerEvent事件类型.
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * 当旧的别名被removeAlias()移除时发送的ContainerEvent事件类型.
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    // ------------------------------------------------------------- 属性 -----------------------------------------------------
    /**
     * @return 该主机的默认配置路径.
     */
    public File getConfigBaseFile();

    /**
     * @return 这个主机的应用程序根目录。这可以是绝对路径名、相对路径名或URL.
     */
    public String getAppBase();


    /**
     * @return 主机的appBase的绝对文件。如果可能，该文件将是规范的.
     */
    public File getAppBaseFile();


    /**
     * 设置此主机的应用程序根目录。这可以是绝对路径名、相对路径名或URL
     *
     * @param appBase - 新的应用程序根目录
     */
    public void setAppBase(String appBase);


    /**
     * @return 自动部署标志的值。如果为true，则表示应发现并自动动态部署此主机的子Web应用程序.
     */
    public boolean getAutoDeploy();


    /**
     * 设置此主机的自动部署标志值.
     *
     * @param autoDeploy - 新的自动部署标志
     */
    public void setAutoDeploy(boolean autoDeploy);


    /**
     * @return 新web应用程序的上下文配置类的Java类名.
     */
    public String getConfigClass();


    /**
     * 为新的web应用程序设置上下文配置类的Java类名.
     *
     * @param configClass - 新的上下文配置类
     */
    public void setConfigClass(String configClass);


    /**
     * @return 启动时部署标志的值。如果为true，则表示应查找并自动部署此主机的子Web应用程序.
     */
    public boolean getDeployOnStartup();


    /**
     * 为此主机设置启动时部署标志值.
     *
     * @param deployOnStartup - 新的启动时部署标志
     */
    public void setDeployOnStartup(boolean deployOnStartup);


    /**
     * @return 这个正则表达式定义了在主机的appBase中会被自动部署过程忽略的文件和目录.
     */
    public String getDeployIgnore();


    /**
     * @return 编译后的正则表达式，它定义了主机appBase中会被自动部署过程忽略的文件和目录.
     */
    public Pattern getDeployIgnorePattern();


    /**
     * 设置正则表达式，它定义了在主机的appBase中会被自动部署过程忽略的文件和目录.
     *
     * @param deployIgnore - 匹配文件名的正则表达式
     */
    public void setDeployIgnore(String deployIgnore);


    /**
     * @return 用于启动和停止上下文的执行程序。这主要用于部署上下文的组件，这些上下文需要以多线程的方式完成.
     */
    public ExecutorService getStartStopExecutor();


    /**
     * 如果Host将尝试为appBase创建目录，则返回true，除非它们已经存在.
     * @return 如果主机将尝试创建目录，则为true
     */
    public boolean getCreateDirs();


    /**
     * 主机是否应该尝试创建xmlBase和appbaseon启动目录.
     *
     * @param createDirs 此标志的新值
     */
    public void setCreateDirs(boolean createDirs);


    /**
     * @return true被配置为自动取消使用并行部署部署的旧版本应用程序的部署。只有{@link #getAutoDeploy()}也返回true时才会生效
     */
    public boolean getUndeployOldVersions();


    /**
     * 如果主机应该自动取消使用并行部署部署的旧版本的应用程序，则设置为true。这只需要{@link #getAutoDeploy()}返回true
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);


    // ------------------------------------------------------------- 公共方法 -----------------------------------------------------
    /**
     * 添加一个应该映射到同一个主机的别名.
     */
    public void addAlias(String alias);


    /**
     * @return 此主机的别名集。如果没有定义，则返回一个长度为零的数组.
     */
    public String[] findAliases();


    /**
     * 从该主机的别名中删除指定的别名.
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias);
}
