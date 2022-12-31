package org.zy.moonStone.core.container;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.exceptions.MultiThrowable;
import org.zy.moonStone.core.interfaces.Cluster;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.ContainerEvent;
import org.zy.moonStone.core.interfaces.container.ContainerListener;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Lifecycle;
import org.zy.moonStone.core.interfaces.container.Pipeline;
import org.zy.moonStone.core.interfaces.container.Server;
import org.zy.moonStone.core.interfaces.container.Valve;
import org.zy.moonStone.core.interfaces.loader.Loader;
import org.zy.moonStone.core.threads.InlineExecutorService;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class ContainerBase extends LifecycleBase implements Container {
	/**
	 * 属于这个容器的子容器，key为容器名
	 */
	protected final HashMap<String, Container> children = new HashMap<>();

	/**
	 * 此组件的处理器延迟.
	 */
	protected int backgroundProcessorDelay = -1;

	protected ScheduledFuture<?> backgroundProcessorFuture;
	protected ScheduledFuture<?> monitorFuture;

	/**
	 * 此container的容器事件监听器.
	 */
	protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * 与此容器关联的集群.
	 */
	protected Cluster cluster = null;

	private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();

	/**
	 * 容器名.
	 */
	protected String name = null;

	/**
	 * 父容器，该容器是其子容器.
	 */
	protected Container parent = null;

    /**
     * 安装Loader时要配置的父类加载器
     */
    protected ClassLoader parentClassLoader = null;
	
	/**
	 * 与此容器关联的Pipeline对象.
	 */
	protected final Pipeline pipeline = new StandardPipeline(this);

	/**
	 * 添加子容器是是否自启动
	 */
	protected boolean startChildren = true;

	/**
	 * 用于处理与此容器关联的任何子容器的启动和停止事件的可用线程数.
	 */
	private int startStopThreads = 1;

	/** 启动和停止子容器的线程执行器 */
	protected ExecutorService startStopExecutor;

	@Override
	public int getStartStopThreads() {
		return startStopThreads;
	}

	@Override
	public void setStartStopThreads(int startStopThreads) {
		int oldStartStopThreads = this.startStopThreads;
		this.startStopThreads = startStopThreads;

		// 使用本地副本来确保线程安全
		if (oldStartStopThreads != startStopThreads && startStopExecutor != null) {
			reconfigureStartStopExecutor(getStartStopThreads());
		}
	}

	@Override
	public int getBackgroundProcessorDelay() {
		return backgroundProcessorDelay;
	}

	@Override
	public void setBackgroundProcessorDelay(int delay) {
		backgroundProcessorDelay = delay;
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	@Override
	public Cluster getCluster() {
		Lock readLock = clusterLock.readLock();
		readLock.lock();
		try {
			if (cluster != null) return cluster;
			if (parent != null) return parent.getCluster();
			return null;
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public void setCluster(Cluster cluster) {
		Cluster oldCluster = null;
		Lock writeLock = clusterLock.writeLock();
		writeLock.lock();
		try {
			// 如有必要，更改组件
			oldCluster = this.cluster;
			if (oldCluster == cluster) return;

			this.cluster = cluster;

			// 如有必要，停止旧组件
			if (getState().isAvailable() && (oldCluster != null) && (oldCluster instanceof Lifecycle)) {
				try {
					((Lifecycle) oldCluster).stop();
				} catch (LifecycleException e) {
					logger.error("组件停止异常", e);
				}
			}

			// 如有必要，启动新组件
			if (cluster != null) cluster.setContainer(this);

			if (getState().isAvailable() && (cluster != null) && (cluster instanceof Lifecycle)) {
				try {
					((Lifecycle) cluster).start();
				} catch (LifecycleException e) {
					logger.error("集群组件启动异常", e);
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		if (name == null) {
			throw new IllegalArgumentException("组件名不能为空");
		}
		this.name = name;
	}

	/**
	 *
	 * @return true则自启动添加的子容器
	 */
	public boolean getStartChildren() {
		return startChildren;
	}

	/**
	 * 设置添加的子容器是是否自启动
	 *
	 */
	public void setStartChildren(boolean startChildren) {
		this.startChildren = startChildren;
	}

	@Override
	public Container getParent() {
		return parent;
	}

	@Override
	public void setParent(Container container) {
		this.parent = container;
	}

	/**
	 * 返回此 Web 应用程序的父类加载器（如果有）。此调用仅在配置了加载器后才有意义。
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (parent != null) {
            return parent.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }
    
    /**
     * 为此Web应用程序设置父级加载程序（如果有）。此调用仅在配置加载程序<strong>之前</strong>才有意义，
     * 并且应将指定的值（如果非null）作为参数，则应向类加载程序构造函数进行参数。
     *
     * @param parent - 新的父级类加载程序
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        this.parentClassLoader = parent;
    }
	
	@Override
	public Pipeline getPipeline() {
		return this.pipeline;
	}

    @Override
    public void backgroundProcess() {
        if (!getState().isAvailable())
            return;

//        Cluster cluster = getClusterInternal();
//        if (cluster != null) {
//            try {
//                cluster.backgroundProcess();
//            } catch (Exception e) {
//                logger.warn("ContainerBase#backgroundProcess() 方法调用异常, by cluster" + cluster, e);
//            }
//        }
//        Realm realm = getRealmInternal();
//        if (realm != null) {
//            try {
//                realm.backgroundProcess();
//            } catch (Exception e) {
//                logger.warn("ContainerBase#backgroundProcess() 方法调用异常, by realm" + realm, e);
//            }
//        }
        
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                logger.warn("ContainerBase#backgroundProcess() 方法调用异常, by valve" + current, e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }
    
    
	// -------------------------------------------------------------------------------------
	// 容器方法
	// -------------------------------------------------------------------------------------
	@Override
	public void addChild(Container child) {
		if (logger.isDebugEnabled()) {
			logger.debug("自容器[{}]追加[{}]" , this, child);
		}

		String childName = child.getName();
		synchronized(children) {
			if (children.get(child.getName()) != null)
				throw new IllegalArgumentException(String.format("容器名冲突，by name：%s", childName));

			child.setParent(this);
			children.put(child.getName(), child);
		}

		fireContainerEvent(ADD_CHILD_EVENT, child);

		try {
			if ((getState().isAvailable() || LifecycleState.STARTING_PREP.equals(getState())) && startChildren) {
				child.start();
			}
		} catch (LifecycleException e) {
			throw new IllegalStateException(String.format("子容器名冲突，by name：%s", childName), e);
		}
	}

	@Override
	public void addContainerListener(ContainerListener listener) {
		listeners.add(listener);
	}

	@Override
	public Container findChild(String name) {
		if (name == null) {
			return null;
		}
		synchronized (children) {
			return children.get(name);
		}
	}

	@Override
	public Container[] findChildren() {
		synchronized (children) {
			Container results[] = new Container[children.size()];
			return children.values().toArray(results);
		}
	}

	@Override
	public ContainerListener[] findContainerListeners() {
		ContainerListener[] results = new ContainerListener[0];
		return listeners.toArray(results);
	}

	@Override
	public void removeChild(Container child) {
		if (child == null) {
			return;
		}
		try {
			if (child.getState().isAvailable()) {
				child.stop();
			}
		} catch (LifecycleException e) {
			logger.error("子容器停止异常", e);
		}

		boolean destroy = false;
		try {
			// 轮空可能已经销毁的子容器
			if (!LifecycleState.DESTROYING.equals(child.getState())) {
				child.destroy();
				destroy = true;
			}
		} catch (LifecycleException e) {
			logger.error("子容器销毁异常", e);
		}

		if (!destroy) {
			fireContainerEvent(REMOVE_CHILD_EVENT, child);
		}

		synchronized(children) {
			if (children.get(child.getName()) == null) return;
			children.remove(child.getName());
		}
	}

	@Override
	public void removeContainerListener(ContainerListener listener) {
		listeners.remove(listener);
	}

	@Override
	protected void initInternal() throws LifecycleException {
		reconfigureStartStopExecutor(getStartStopThreads());
	}

	private void reconfigureStartStopExecutor(int threads) {
		if (threads == 1) {
			if (!(startStopExecutor instanceof InlineExecutorService)) {
				startStopExecutor = new InlineExecutorService();
			}
		} else {
			// 将实用程序的执行委托给服务
			Server server = Container.getService(this).getServer();
			server.setUtilityThreads(threads);
			startStopExecutor = server.getUtilityExecutor();
		}
	}


	/**
	 * 启动该组件并实现 {@link LifecycleBase.startInternal() }的要求
	 *
	 * @exception LifecycleException - 如果此组件检测到阻止使用此组件的致命错误
	 */
	@Override
	protected synchronized void startInternal() throws LifecycleException {
//		Cluster cluster = getClusterInternal();
//		if (cluster instanceof Lifecycle) {
//			((Lifecycle) cluster).start();
//		}
//		Realm realm = getRealmInternal();
//		if (realm instanceof Lifecycle) {
//			((Lifecycle) realm).start();
//		}

		Container children[] = findChildren();
		List<Future<Void>> results = new ArrayList<>();
		for (int i = 0; i < children.length; i++) {
			results.add(startStopExecutor.submit(new StartChild(children[i])));
		}

		MultiThrowable multiThrowable = null;

		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (Throwable e) {
				logger.error("线程停止失败", e);
				if (multiThrowable == null) {
					multiThrowable = new MultiThrowable();
				}
				multiThrowable.add(e);
			}
		}
		if (multiThrowable != null) {
			throw new LifecycleException("线程停止失败", multiThrowable.getThrowable());
		}

		if (pipeline instanceof Lifecycle) {
			((Lifecycle) pipeline).start();
		}

		setState(LifecycleState.STARTING);
	}

	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		if (monitorFuture != null) {
			monitorFuture.cancel(true);
			monitorFuture = null;
		}
		threadStop();

		setState(LifecycleState.STOPPING);

		if (pipeline instanceof Lifecycle && ((Lifecycle) pipeline).getState().isAvailable()) {
			((Lifecycle) pipeline).stop();
		}

		Container children[] = findChildren();
		List<Future<Void>> results = new ArrayList<>();
		for (int i = 0; i < children.length; i++) {
			results.add(startStopExecutor.submit(new StopChild(children[i])));
		}

		boolean fail = false;
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (Exception e) {
				logger.error("线程停止失败", e);
				fail = true;
			}
		}
		if (fail) {
			throw new LifecycleException("线程停止失败");
		}

//		Realm realm = getRealmInternal();
//		if (realm instanceof Lifecycle) {
//			((Lifecycle) realm).stop();
//		}
//		Cluster cluster = getClusterInternal();
//		if (cluster instanceof Lifecycle) {
//			((Lifecycle) cluster).stop();
//		}
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
//		Realm realm = getRealmInternal();
//		if (realm instanceof Lifecycle) {
//			((Lifecycle) realm).destroy();
//		}
//		Cluster cluster = getClusterInternal();
//		if (cluster instanceof Lifecycle) {
//			((Lifecycle) cluster).destroy();
//		}

		if (pipeline instanceof Lifecycle) {
			((Lifecycle) pipeline).destroy();
		}

		for (Container child : findChildren()) {
			removeChild(child);
		}

		if (parent != null) {
			parent.removeChild(this);
		}

		if (startStopExecutor != null) {
			startStopExecutor.shutdownNow();
		}
	}
	
	/**
     * 向容器添加Valve.
     *
     * @exception IllegalArgumentException - 如果该容器拒绝接受指定的阀门
     * @exception IllegalStateException - 如果指定的阀门已与其他容器关联
     */
    public synchronized void addValve(Valve valve) {
        pipeline.addValve(valve);
    }
    
    @Override
    public File getMoonBase() {
        if (parent == null) {
            return null;
        }
        return parent.getMoonBase();
    }

    @Override
    public File getMoonHome() {
        if (parent == null) {
            return null;
        }
        return parent.getMoonHome();
    }
    
    @Override
    public void fireContainerEvent(String type, Object data) {
        if (listeners.size() < 1)
            return;

        ContainerEvent event = new ContainerEvent(this, type, data);
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// Background Thread
	// -------------------------------------------------------------------------------------
    /**
     * 启动将定期检查会话超时的后台线程
     */
    protected void threadStart() {
        if (backgroundProcessorDelay > 0 && (getState().isAvailable() || LifecycleState.STARTING_PREP.equals(getState()))
        		&& (backgroundProcessorFuture == null || backgroundProcessorFuture.isDone())) {
            if (backgroundProcessorFuture != null && backgroundProcessorFuture.isDone()) {
                // 执行计划任务时出错
                try {
                    backgroundProcessorFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("containerBase#backgroundProcess() 方法调度错误", e);
                }
            }
            backgroundProcessorFuture = Container.getService(this).getServer().getUtilityExecutor()
            		/*
                     * 创建并执行一个周期性操作，该操作在给定的初始延迟之后首先启用，然后在一次执行的终止和下一次执行的开始之间具有给定的延迟。
                     * 如果任务的任何执行遇到异常，则后续执行将被抑制。否则，任务将只能通过取消或终止执行程序来终止.
                     * [10秒后开始执行定时任务，以10秒为间隔执行]
                     *
                     * @param command - 要执行的任务
                     * @param initialDelay - 延迟第一次执行的时间
                     * @param delay - 从一次执行的结束到下一次执行的开始之间的延迟
                     * @param unit - initialDelay和period参数的时间单位
                     * @return 表示待完成任务的ScheduledFuture，其get()方法将在取消时抛出异常
                     */
                    .scheduleWithFixedDelay(new ContainerBackgroundProcessor(), backgroundProcessorDelay, backgroundProcessorDelay, TimeUnit.SECONDS);
        }
    }

    /**
     * 停止定期检查会话超时的后台线程
     */
    protected void threadStop() {
        if (backgroundProcessorFuture != null) {
            backgroundProcessorFuture.cancel(true);
            backgroundProcessorFuture = null;
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    private static class StartChild implements Callable<Void> {
        private Container child;

        public StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    private static class StopChild implements Callable<Void> {
        private Container child;

        public StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }
    
    /**
     * 私有Runnable类，用于在固定延迟后调用此容器及其子容器的 backgroundProcess 方法
     */
    protected class ContainerBackgroundProcessor implements Runnable {
        @Override
        public void run() {
            processChildren(ContainerBase.this);
        }

        protected void processChildren(Container container) {
            ClassLoader originalClassLoader = null;

            try {
                if (container instanceof Context) {
                    Loader loader = ((Context) container).getLoader();
                    // 对于失败的上下文实例，加载器将为空
                    if (loader == null) {
                        return;
                    }

                    // 确保在Web应用程序的类加载器下执行上下文和包装的后台处理
                    originalClassLoader = ((Context) container).bind(false, null);
                }
                container.backgroundProcess();
                // 当前只有StandardContext需要调用backgroundProcess()，故不再处理子类StandardWrapper的backgroundProcess()方法
//                Container[] children = container.findChildren();
//                for (int i = 0; i < children.length; i++) {
//                    if (children[i].getBackgroundProcessorDelay() <= 0) {
//                        processChildren(children[i]);
//                    }
//                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                logger.error("containerBase#backgroundProcess() 方法调度错误", t);
            } finally {
                if (container instanceof Context) {
                    ((Context) container).unbind(false, originalClassLoader);
                }
            }
        }
    }
}
