package org.zy.moonstone.core.session;

import org.slf4j.Logger;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.loader.Loader;
import org.zy.moonstone.core.security.SecurityUtil;
import org.zy.moonstone.core.session.interfaces.Session;
import org.zy.moonstone.core.util.CustomObjectInputStream;
import org.zy.moonstone.core.util.ExceptionUtils;

import javax.servlet.ServletContext;
import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;


/**
 * @dateTime 2022年8月8日;
 * @author zy(azurite-Y);
 * @description
 * Manager 接口的标准实现，它在此组件重新启动时提供简单的会话持久性（例如，当整个服务器关闭并重新启动时，或者当重新加载特定的 Web 应用程序时
 * <b>实现说明</b>：会话存储和重新加载的正确行为取决于在正确时间对此类的 start() 和 stop() 方法的外部调用。
 */
public class StandardManager extends ManagerBase {
    /** 此 Manager 实现的描述性名称（用于日志记录） */
    protected static final String name = "StandardManager";

    /**
     * 停止时保存活动会话的磁盘文件的路径名，启动时加载这些会话的磁盘文件的路径名。
     * 空值表示不需要持久化。如果此路径名是相对的，它将针对临时工作目录进行解析由上下文提供，可通过 <code>javax.servlet.context.tempdir</code> 上下文属性获得。
     */
    protected String pathname = "SESSIONS.ser";
	
    
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return 会话持久性路径名（如果有）
     */
    public String getPathname() {
        return pathname;
    }

    /**
     * 将会话持久性路径名设置为指定值。 如果需要无持久性支持，请将路径名设置为 <code>null</code>。
     *
     * @param pathname - 新会话持久性路径名
     */
    public void setPathname(String pathname) {
        this.pathname = pathname;
    }
    
    
	// -------------------------------------------------------------------------------------
	// 安全类
	// -------------------------------------------------------------------------------------
    private class PrivilegedDoLoad implements PrivilegedExceptionAction<Void> {
        PrivilegedDoLoad() {}

        @Override
        public Void run() throws Exception{
           doLoad();
           return null;
        }
    }

    private class PrivilegedDoUnload implements PrivilegedExceptionAction<Void> {
        PrivilegedDoUnload() {}

        @Override
        public Void run() throws Exception{
            doUnload();
            return null;
        }

    }

	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    @Override
    public void load() throws ClassNotFoundException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoLoad() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException)exception;
                } else if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("load() 未报告的异常 ", exception);
                }
            }
        } else {
            doLoad();
        }
    }

    /**
     * 将之前卸载的任何当前活动会话加载到适当的持久化机制（如果有）。 如果不支持持久性，则此方法返回而不执行任何操作。
     *
     * @exception ClassNotFoundException - 如果在重新加载期间找不到序列化类
     * @exception IOException - 如果发生输入/输出错误
     */
    protected void doLoad() throws ClassNotFoundException, IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("开始加载持久化会话");
        }

        // 初始化内部数据结构
        sessions.clear();

        // 打开指定路径名的输入流（如果有）
        File file = file();
        if (file == null) {
            return;
        } else if (!file.exists()) {
        	if (logger.isDebugEnabled()) {
                logger.debug("未找到持久化数据文件. by file: {}", file.getAbsoluteFile());
            }
        	return ;
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("standardManager.loading path: {}", pathname);
        }
        
		Loader loader = null;
        ClassLoader classLoader = null;
        Logger logger = null;
        try (
        		FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                BufferedInputStream bis = new BufferedInputStream(fis)) {
            Context c = getContext();
            loader = c.getLoader();
            logger = c.getLogger();
            if (loader != null) {
                classLoader = loader.getClassLoader();
            }
            if (classLoader == null) {
                classLoader = getClass().getClassLoader();
            }

            // 加载以前卸载的活跃会话
            synchronized (sessions) {
                try (ObjectInputStream ois = new CustomObjectInputStream(bis, classLoader, logger, getSessionAttributeValueClassNamePattern(), getWarnOnSessionAttributeFilterFailure())) {
                    
                	Integer count = (Integer) ois.readObject();
                    int n = count.intValue();
                    if (logger.isDebugEnabled())
                        logger.debug("需加载会话数: {} ",  n);
                    
                    for (int i = 0; i < n; i++) {
                        StandardSession session = getNewSession();
                        // 单个 Session 反序列化
                        session.readObjectData(ois);
                        session.setManager(this);
                        sessions.put(session.getIdInternal(), session);
                        session.activate();
                        if (!session.isValidInternal()) {
                            // 如果会话已经无效，则使会话过期以防止内存泄漏。
                            session.setValid(true);
                            session.expire();
                        }
                        sessionCounter++;
                    }
                } finally {
                    // 删除永久存储文件
                    if (file.exists()) {
                        if (!file.delete()) {
                            logger.warn("删除持久化文件失败. by file: {}", file.getAbsoluteFile());
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("未找到持久化数据文件. by file: {}", file.getAbsoluteFile());
            }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("加载持久化会话完成");
        }
    }


    @Override
    public void unload() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                AccessController.doPrivileged(new PrivilegedDoUnload());
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("unLoad() 未报告的异常", exception);
                }
            }
        } else {
            doUnload();
        }
    }


    /**
     * 将任何当前活动的会话保存在相应的持久性机制中(如果有的话)。如果不支持持久性，则此方法无需执行任何操作。
     *
     * @exception IOException - 如果发生输入/输出错误
     */
    protected void doUnload() throws IOException {
        if (sessions.isEmpty()) {
            logger.debug("无需持久化保存任何 Session");
            return;
        }

        // 打开指向指定路径名的输出流(如果有的话)
        File file = file();
        if (file == null) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("会话持久化路径：{}", pathname);
        }

        // 记录已持久化的会话
        List<StandardSession> list = new ArrayList<>();

        try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            synchronized (sessions) {
                if (logger.isDebugEnabled()) {
                    logger.debug("需持久化会话数：{} ",  sessions.size());
                }
                // 写下持久化会话的数量，然后是详细信息
                oos.writeObject(Integer.valueOf(sessions.size()));
                for (Session s : sessions.values()) {
                    StandardSession session = (StandardSession) s;
                    list.add(session);
                    session.passivate();
                    session.writeObjectData(oos);
                }
            }
        }

        // 失效已持久化的会话
        if (logger.isDebugEnabled()) {
            logger.debug("需失效的持久化会话数：{} ",  list.size());
        }
        for (StandardSession session : list) {
            try {
                session.expire(false);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                session.recycle();
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("完成: 持久化会话保存");
        }
    }


    /**
     * 启动当前组件
     *
     * @exception LifecycleException - 如果此组件检测到阻止此组件被使用的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        if (logger.isDebugEnabled()) {
            logger.debug("StandardManager Starting");
        }
        
        super.startInternal();

        // 加载已持久化的会话（如果有）
        try {
            load();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            logger.error("standardManager.managerLoad", t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * 停止此组件
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        if (logger.isDebugEnabled()) {
            logger.debug("StandardManager Stopping.");
        }

        setState(LifecycleState.STOPPING);

        // 写出会话
        try {
            unload();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            logger.error("会话管理器持久化会话异常", t);
        }

        // 失效当前所有活动的会话
        Session sessions[] = findSessions();
        for (Session session : sessions) {
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                // 如果对Session对象的引用保存在某个共享字段中，则应对内存泄漏采取措施
                session.recycle();
            }
        }

        // 如果重新启动，需要一个新的随机数生成器
        super.stopInternal();
    }

	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    /**
     * 返回一个 File 对象，表示我们的持久性文件的路径名（如果有）。
     * @return the file
     */
    protected File file() {
        if (pathname == null || pathname.length() == 0) {
            return null;
        }
        File file = new File(pathname);
        if (!file.isAbsolute()) { // 如果此路径名是相对的，它将针对临时工作目录进行解析由上下文提供，可通过 javax.servlet.context.tempdir 上下文属性获得
            Context context = getContext();
            ServletContext servletContext = context.getServletContext();
            File tempdir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            if (tempdir != null) {
                file = new File(tempdir, pathname);
            }
        }
        return file;
    }
}
