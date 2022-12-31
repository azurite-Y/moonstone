package org.zy.moonStone.core.container;

import java.io.File;
import java.util.Locale;

import org.zy.moonStone.core.container.valves.StandardEngineValve;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Engine;
import org.zy.moonStone.core.interfaces.container.Host;
import org.zy.moonStone.core.interfaces.container.Server;
import org.zy.moonStone.core.interfaces.container.Service;
import org.zy.moonStone.core.util.ServerInfo;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardEngine extends ContainerBase implements Engine {
	/**
	 * 在请求中未指定服务器主机或未知主机时使用的主机名.
	 */
	private String defaultHost = null;

	/**
	 * 拥有这个 Engine 的服务(如果有的话)。
	 */
	private Service service = null;

	/**
	 * 这个MoonStone实例的JVM Route ID。所有路由ID在集群中必须是唯一的.
	 */
	private String jvmRouteId;


	/**
	 * 使用默认的基本Valve创建一个新的StandardEngine组件.
	 */
	public StandardEngine() {
		super();
		pipeline.setBasic(new StandardEngineValve());
		// 使用系统属性jvmRoute设置jmvRoute，以区分集群环境下的各个容器
		try {
			setJvmRoute(System.getProperty("jvmRoute"));
		} catch(Exception ex) {
			logger.warn("设置jvmRoute属性失败", ex);
		}
		// 默认情况下，引擎将保持重新加载线程
		backgroundProcessorDelay = 10;
	}

	@Override
	public String getDefaultHost() {
		return defaultHost;
	}

	@Override
	public void setDefaultHost(String defaultHost) {
		if (defaultHost == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = defaultHost.toLowerCase(Locale.ENGLISH);
        }
        if (getState().isAvailable()) {
            service.getMapper().setDefaultHostName(defaultHost);
        }
	}

	@Override
	public String getJvmRoute() {
		return jvmRouteId;
	}

	@Override
	public void setJvmRoute(String jvmRouteId) {
		this.jvmRouteId = jvmRouteId;
	}

	@Override
	public Service getService() {
		return service;
	}

	@Override
	public void setService(Service service) {
		this.service = service;
	}
	
	@Override
	protected synchronized void startInternal() throws LifecycleException {
		if (logger.isInfoEnabled()) {
			logger.info("StandardEngine Start. {}", ServerInfo.getServerInfo());
		}

		super.startInternal();
	}
	
    /**
     * 仅当建议的子容器是 Host 的实现时，才添加子容器
     *
     * @param child - 要添加的子容器
     */
    @Override
    public void addChild(Container child) {
        if (!(child instanceof Host))
            throw new IllegalArgumentException("StandardEngine 不能添加非 Host 实现的容器, by child: " + child.getName());
        super.addChild(child);
    }


    /**
     * 禁止任何尝试为此 Container 设置父级，因为 Engine 应该位于 Container 层次结构的顶部。
     *
     * @param container - 建议的父容器
     */
    @Override
    public void setParent(Container container) {
        throw new IllegalArgumentException("StandardEngine 不能设置父级容器");
    }
    
    /**
     * @return 此组件的父类l类加载器
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (service != null) {
            return service.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public File getMoonBase() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getMoonBase();
                if (base != null) {
                    return base;
                }
            }
        }
        // 回退
        return super.getMoonBase();
    }

    @Override
    public File getMoonHome() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getMoonHome();
                if (base != null) {
                    return base;
                }
            }
        }
        return super.getMoonHome();
    }
}
