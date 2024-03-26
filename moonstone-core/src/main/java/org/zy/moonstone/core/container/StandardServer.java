package org.zy.moonstone.core.container;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.AccessControlException;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Server;
import org.zy.moonstone.core.interfaces.container.Service;
import org.zy.moonstone.core.startup.Moon;
import org.zy.moonstone.core.threads.ScheduledThreadPoolExecutorWrapper;
import org.zy.moonstone.core.threads.TaskThreadFactory;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description 服务器接口的标准实现, 可在部署和启动Catalina时使用(但不是必需的)
 */
public final class StandardServer  extends LifecycleBase implements Server{
	/**
	 * 等待shutdown命令的端口号.
	 */
	private int port = 8005;

	/**
	 * 端口偏移量
	 */
	private int portOffset = 0;

	/**
	 * 等待shutdown命令的地址.
	 */
	private String address = "localhost";

	/**
	 * 仅当shutdown命令字符串长度大于1024时使用的随机数生成器
	 */
	private Random random = null;

	/**
	 * 与此服务器相关联的服务集.
	 */
	private Service services[] = new Service[0];

	private final Object servicesLock = new Object();

	/**
	 * 关机命令字符串.
	 */
	private String shutdown = "SHUTDOWN";

	private volatile boolean stopAwait = false;

	private Moon moon = null;

    /**
     * 安装Loader时要配置的父类加载器
     */
    protected ClassLoader parentClassLoader = null;
	
	/**
	 * 阻塞主线程的线程
	 */
	private volatile Thread awaitThread = null;

	/**
	 * 用于等待shutdown命令的服务器套接字.
	 */
	private volatile ServerSocket awaitSocket = null;

	private File moonHome = null;

	private File moonBase = null;

	/**
	 * 可用来处理此服务中实用程序任务的线程数.
	 */
	protected int utilityThreads = 2;

	/**
	 * 实用程序线程守护标志.
	 */
	protected boolean utilityThreadsAsDaemon = false;

	/**
	 * 具有调度能力的实用程序执行器.
	 */
	private ScheduledThreadPoolExecutor utilityExecutor = null;

	/**
	 * 实用程序执行器包装.
	 */
	private ScheduledExecutorService utilityExecutorWrapper = null;

	/**
	 * 周期性生命周期事件的控制器.
	 */
//	private ScheduledFuture<?> periodicLifecycleEventFuture = null;

//	private ScheduledFuture<?> monitorFuture;

	/**
	 * 生命周期事件周期（以秒为单位）.
	 */
//	protected int periodicEventDelay = 10;


	@Override
	public int getPort() {
		return this.port;
	}

	@Override
	public void setPort(int port) {
		this.port = port;		
	}

	@Override
	public int getPortOffset() {
		return portOffset;
	}

	@Override
	public void setPortOffset(int portOffset) {
		if (portOffset < 0) {
			throw new IllegalArgumentException("无效的端口偏移量, by portOffset：" + portOffset);
		}
		this.portOffset = portOffset;		
	}

	@Override
	public int getPortWithOffset() {
		int port = getPort();
		if (port > 0) {
			return port + getPortOffset();
		} else {
			return port;
		}
	}

	@Override
	public String getAddress() {
		return this.address;
	}

	@Override
	public void setAddress(String address) {
		this.address = address;
	}

	@Override
	public String getShutdown() {
		return this.shutdown;
	}

	@Override
	public void setShutdown(String shutdown) {
		this.shutdown = shutdown;
	}

	@Override
	public Moon getMoon() {
		return moon;
	}

	@Override
	public void setMoon(Moon moon) {
		this.moon = moon;
	}

	@Override
	public File getMoonBase() {
		if (moonBase != null) {
			return moonBase;
		}

		moonBase = getMoonHome();
		return moonBase;
	}

	@Override
	public void setMoonBase(File moonBase) {
		this.moonBase = moonBase;
	}

	@Override
	public File getMoonHome() {
		return moonHome;
	}

	@Override
	public void setMoonHome(File moonHome) {
		this.moonHome = moonHome;
	}

    private int getUtilityThreadsInternal(int utilityThreads) {
        int result = utilityThreads;
        if (result <= 0) {
        	/**
        	 * Runtime.getRuntime().availableProcessors()：
        	 * 返回JVM可用的最大处理器数量, 恒大于1
        	 */
            result = Runtime.getRuntime().availableProcessors() + result;
            if (result < 2) {
                result = 2;
            }
        }
        return result;
    }
	
	@Override
	public int getUtilityThreads() {
		return utilityThreads;
	}

	@Override
	public void setUtilityThreads(int utilityThreads) {
		// 使用本地副本以确保线程安全
        int oldUtilityThreads = this.utilityThreads;
        int utilityThreadsInternal = getUtilityThreadsInternal(utilityThreads);
        if ( utilityThreadsInternal < getUtilityThreadsInternal(oldUtilityThreads)) {
            return;
        }
        this.utilityThreads = utilityThreads;
        if (oldUtilityThreads != utilityThreads && utilityExecutor != null) {
            reconfigureUtilityExecutor(utilityThreadsInternal);
        }
	}

	private synchronized void reconfigureUtilityExecutor(int threads) {
        if (utilityExecutor != null) {
            utilityExecutor.setCorePoolSize(threads);
        } else {
        	/*
        	 * ScheduledThreadPoolExecutor(corePoolSize, threadFactory)
        	 * corePoolSize - 池中要保留的线程数
        	 * threadFactory - 当执行器创建一个新线程时使用的工厂
        	 */
            ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(threads,
            		new TaskThreadFactory("moonstone-Utility-", utilityThreadsAsDaemon, Thread.MIN_PRIORITY));
            /*
             * 设置线程在终止之前可以保持空闲的时间限制。
             * 如果池中的线程数超过了当前的核心线程数, 则在等待这段时间而不处理任务后, 多余的线程将被终止。
             * 这将覆盖构造函数中设置的任何值
             * 
             * @param time - 等待的时间。时间值为0将导致在执行任务后立即终止多余的线程。
             * @param unit - 时间参数的时间单位
             */
            scheduledThreadPoolExecutor.setKeepAliveTime(10, TimeUnit.SECONDS);
            /*
             * 设置在取消时是否应立即从工作队列中删除已取消任务的策略。默认情况下, 此值为false
             * 
             * @param value - 如果为真, 取消时删除, 否则不删除
             */
            scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
            /*
             * 设置是否执行现有的延迟任务的策略, 即使该执行器已关闭。
             * 在这种情况下, 这些任务只会在shutdown Now时终止, 或者在已经关闭时将策略设置为false后终止。该值默认为true。
             * 
             * @param value - 如果为真, 关闭后执行, 否则不执行
             */
            scheduledThreadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            utilityExecutor = scheduledThreadPoolExecutor;
            utilityExecutorWrapper = new ScheduledThreadPoolExecutorWrapper(utilityExecutor);
        }
    }
	
	@Override
	public void addService(Service service) {
		service.setServer(this);

        synchronized (servicesLock) {
        	// 追加Service对象到服务集中
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;

            if (getState().isAvailable()) {
                try {
                    service.start();
                } catch (LifecycleException e) {}
            }
        }
        // 向感兴趣的侦听器报告此属性更改
//        support.firePropertyChange("service", null, service);
	}

	public void stopAwait() {
        stopAwait=true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

	@Override
	public void await() {
		int portWithOffset = getPortWithOffset();
        /*
         * 若未设置监听的shutdown命令的端口号, 默认值为-1
         * 那么就在其他线程未将 stopAwait 置为true之前一直暂停当前主线程
         */
        if (portWithOffset == -1) { 
            try {
                awaitThread = Thread.currentThread();
                while(!stopAwait) {
                    try {
                        Thread.sleep( 10000 );
                    } catch( InterruptedException ex ) {}
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        // 若代码走到这, 那么就意味着设置了监听shutdown命令的端口号, 那么就创建对应服务器套接字等待连接
        try {
            awaitSocket = new ServerSocket(portWithOffset, 1, InetAddress.getByName(address));
        } catch (IOException e) {
            logger.error("打开socket时发生I/O错误, by address：[{}], port：[{}]" , address , portWithOffset, e);
            return;
        }

        try {
            awaitThread = Thread.currentThread();
            // 循环等待连接和有效的命令
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // 等待下一个连接
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                    	/*
                    	 * 阻塞式等待连接。监听连接请求, 并阻塞直到接收请求, 建立连接
                    	 */
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000);  // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        logger.warn("读取请求输入流超时：{}", Long.valueOf(System.currentTimeMillis() - acceptStartTime), ste);
                        continue;
                    } catch (AccessControlException ace) {
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            // socket.close()终止等待
                            break;
                        }
                        logger.error("连接异常", e);
                        break;
                    }

                    // 从套接字读取一组字符
                    int expected = 1024; // 切断, 避免Dos攻击
                    while (expected < shutdown.length()) {
                        if (random == null)
                            random = new Random();
                        expected += (random.nextInt() % 1024);
                    }
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            logger.warn("连接读取异常", e);
                            ch = -1;
                        }
                        // 控制字符 或 EOF (-1) 终止循环
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // 现在关闭socket
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {}
                }

                // 匹配命令字符串
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    logger.info("通过端口关闭服务器...");
                    break;
                } else {
                	logger.warn("无效的关机命令, by command：[{}]", command.toString());
                }
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // Close the server socket and return
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
	}

	@Override
	public Service findService(String name) {
		if (name == null) {
            return null;
        }
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                if (name.equals(services[i].getName())) {
                    return services[i];
                }
            }
        }
        return null;
	}

	@Override
	public Service[] findServices() {
        return services;
	}

	@Override
	public void removeService(Service service) {
		synchronized (servicesLock) {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            
            if (j < 0) return;
            
            try {
                services[j].stop();
            } catch (LifecycleException e) {} // 抑制生命周期异常
            
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j) results[k++] = services[i];
            }
            services = results;

//            support.firePropertyChange("service", service, null);
        }
	}
	
	@Override
	public ScheduledExecutorService getUtilityExecutor() {
		return utilityExecutorWrapper;
	}

	@Override
	protected void initInternal() throws LifecycleException {
        // 初始化实用程序执行器
        reconfigureUtilityExecutor(getUtilityThreadsInternal(utilityThreads));
        
        // 初始化定义的服务
        for (int i = 0; i < services.length; i++) {
            services[i].init();
        }
	}

	@Override
	protected void startInternal() throws LifecycleException {
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        setState(LifecycleState.STARTING);

        // 启动默认服务
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                services[i].start();
            }
        }
	}

	@Override
	protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        // 事件发布
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        for (Service service : services) {
            service.stop();
        }

        stopAwait();
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		// 销毁默认的服务
        for (int i = 0; i < services.length; i++) {
            services[i].destroy();
        }
        
        if (utilityExecutor != null) {
            utilityExecutor.shutdownNow();
            utilityExecutor = null;
        }
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("StandardServer[");
		sb.append(getPort());
		sb.append("]");
		return sb.toString();
	}

    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (moon != null) {
            return moon.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * 为此 Server 设置父类加载器
     *
     * @param parent - 新的父级类加载器
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        this.parentClassLoader = parent;
    }
}
