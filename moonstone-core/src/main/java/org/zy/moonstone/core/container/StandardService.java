package org.zy.moonstone.core.container;

import java.util.ArrayList;

import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.connector.Connector;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.connector.Executor;
import org.zy.moonstone.core.interfaces.container.Engine;
import org.zy.moonstone.core.interfaces.container.Server;
import org.zy.moonstone.core.interfaces.container.Service;
import org.zy.moonstone.core.mapper.Mapper;
import org.zy.moonstone.core.mapper.MapperListener;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description 服务接口的标准实现。关联的容器通常是引擎的实例, 但这不是必需的
 */
public class StandardService  extends LifecycleBase implements Service{
	/**
	 * 服务名
	 */
	private String name = null;

	/**
	 * 拥有此服务的服务器（如果有）.
	 */
	private Server server = null;

	/**
	 * 与此服务关联的连接器集.
	 */
	protected Connector connectors[] = new Connector[0];

	private final Object connectorsLock = new Object();

	/**
	 * 本服务持有的执行器集合.
	 */
	protected final ArrayList<Executor> executors = new ArrayList<>();

	private Engine engine = null;

	/**
	 * Mapper.
	 */
	protected final Mapper mapper = new Mapper();

	/**
	 * Mapper listener.
	 */
	protected final MapperListener mapperListener = new MapperListener(this);

	private ClassLoader parentClassLoader = null;
	
	@Override
	public Mapper getMapper() {
		return mapper;
	}
	
	@Override
	public Engine getContainer() {
		return engine;
	}

	@Override
	public void setContainer(Engine engine) {
		Engine oldEngine = this.engine;
		if (oldEngine != null) {
			oldEngine.setService(null);
		}
		this.engine = engine;
		if (this.engine != null) {
			this.engine.setService(this);
		}
		if (getState().isAvailable()) { // 若当前服务状态可用
			if (this.engine != null) {
				try {
					this.engine.start();
				} catch (LifecycleException e) {
					logger.error("Engine启动失败.", e);
				}
			}
			// 重新启动MapperListener以获取新引擎.
			try {
				mapperListener.stop();
			} catch (LifecycleException e) {
				logger.error("MapperListener停止失败." , e);
			}
			try {
				mapperListener.start();
			} catch (LifecycleException e) {
				logger.error("MapperListener启动失败." , e);
			}
			if (oldEngine != null) {
				try {
					oldEngine.stop();
				} catch (LifecycleException e) {
					logger.error("Engine停止失败.", e);
				}
			}
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public Server getServer() {
		return this.server;
	}

	@Override
	public void setServer(Server server) {
		this.server = server;
	}


	@Override
	public void addConnector(Connector connector) {
		synchronized (connectorsLock) {
			connector.setService(this);
			Connector results[] = new Connector[connectors.length + 1];
			System.arraycopy(connectors, 0, results, 0, connectors.length);
			results[connectors.length] = connector;
			connectors = results;
		}

		try {
			if (getState().isAvailable()) {
				connector.start();
			}
		} catch (LifecycleException e) {
			throw new IllegalArgumentException("连接器启动失败", e);
		}
	}

	@Override
	public Connector[] findConnectors() {
		return connectors;
	}

	@Override
	public void removeConnector(Connector connector) {
		synchronized (connectorsLock) {
			int j = -1;
			for (int i = 0; i < connectors.length; i++) { // 迭代查找要删除的连接器在连接器数组中的下标
				if (connector == connectors[i]) {
					j = i;
					break;
				}
			}

			if (j < 0) return;

			if (connectors[j].getState().isAvailable()) { // 若连接器是可用状态则停止
				try {
					connectors[j].stop();
				} catch (LifecycleException e) {
					logger.error("连接器停止失败", e);
				}
			}
			connector.setService(null);

			int k = 0;
			Connector results[] = new Connector[connectors.length - 1];
			for (int i = 0; i < connectors.length; i++) {
				if (i != j)
					results[k++] = connectors[i];
			}
			connectors = results;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("StandardService[");
		sb.append(getName());
		sb.append("]");
		return sb.toString();
	}

	@Override
	public void addExecutor(Executor ex) {
		synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (getState().isAvailable()) {
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        logger.error("执行器启动失败", x);
                    }
                }
            }
        }
	}

	@Override
	public Executor[] findExecutors() {
		synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
	}

	@Override
	public Executor getExecutor(String executorName) {
		synchronized (executors) {
            for (Executor executor: executors) {
                if (executorName.equals(executor.getName()))
                    return executor;
            }
        }
        return null;
	}

	@Override
	public void removeExecutor(Executor ex) {
		synchronized (executors) {
            if ( executors.remove(ex) && getState().isAvailable() ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    logger.error("执行器停止异常", e);
                }
            }
        }
	}

	@Override
	protected void initInternal() throws LifecycleException {
        if (engine != null) {
            engine.init();
        }

        // 初始化所有执行器
        for (Executor executor : findExecutors()) {
            executor.init();
        }

        // 初始化 MapperListener
        mapperListener.init();

        // 初始化定义的所有连接器
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.init();
            }
        }
	}

	@Override
	protected void startInternal() throws LifecycleException {
		if(logger.isInfoEnabled()) {
			logger.info("Service Start, by name: {}", this.name);
		}
        setState(LifecycleState.STARTING);

        // 启动定义的容器
        if (engine != null) {
            synchronized (engine) {
                engine.start();
            }
        }

        synchronized (executors) {
            for (Executor executor: executors) {
                executor.start();
            }
        }

        mapperListener.start();

        // 启动定义的第二个容器
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (connector.getState() != LifecycleState.FAILED) { // 不启动已经启动失败的容器
                    connector.start();
                }
            }
        }
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		// 第一步暂停连接器
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                connector.pause();
                connector.getProtocolHandler().closeServerSocketGraceful();
            }
        }

        if(logger.isInfoEnabled()) {
        	logger.info("关闭服务, by name：{}", this.name);
        }
        setState(LifecycleState.STOPPING);

        // 停止定义的第二个容器
        if (engine != null) {
            synchronized (engine) {
                engine.stop();
            }
        }
        
        synchronized (executors) {
            for (Executor executor: executors) {
                executor.stop();
            }
        }

        // 现在停止所有连接器
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (!LifecycleState.STARTED.equals(connector.getState())) { // 轮空未启动完的连接器
                    continue;
                }
                connector.stop();
            }
        }

        // 如果服务器启动失败, mapperListener将不会启动
        if (mapperListener.getState() != LifecycleState.INITIALIZED) {
            mapperListener.stop();
        }
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		mapperListener.destroy();

        // 销毁定义的所有连接器
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.destroy();
            }
        }

        // 销毁所有的执行器
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        if (engine != null) {
            engine.destroy();
        }
	}
	
	/**
     * @Return 此组件的父类l类加载器
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (server != null) {
            return server.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * 为此 Service 设置父类加载器
     *
     * @param parent - 新的父级类加载器
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        this.parentClassLoader = parent;
    }
}
