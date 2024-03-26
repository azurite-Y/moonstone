package org.zy.moonstone.core.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleEvent;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.*;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * @dateTime 2022年8月15日;
 * @author zy(azurite-Y);
 * @description
 */
public class MapperListener extends LifecycleBase implements ContainerListener, LifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(MapperListener.class);

	/** 关联的 Mapper */
    private final Mapper mapper;

    /**
     * 关联的 service
     */
    private final Service service;


	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 创建 MapperListener
     *
     * @param service - 此监听器关联的服务
     */
    public MapperListener(Service service) {
        this.service = service;
        this.mapper = service.getMapper();
    }
    
	
	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    public void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);

        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }

        findDefaultHost();

        addListeners(engine);

        Container[] conHosts = engine.findChildren();
        for (Container conHost : conHosts) {
            Host host = (Host) conHost;
            if (!LifecycleState.NEW.equals(host.getState())) {
                // 注册 Host 将注册 Context 和 Wrappers
                registerHost(host);
            }
        }
    }

    @Override
    public void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);

        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }
        removeListeners(engine);
    }

	@Override
	public void lifecycleEvent(LifecycleEvent event) {
		if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            Object obj = event.getSource();
            if (obj instanceof Wrapper) {
                Wrapper w = (Wrapper) obj;
                //  仅当上下文已启动时。 如果没有，那么稍后它将有自己的“after_start”事件
                if (w.getParent().getState().isAvailable()) {
                    registerWrapper(w);
                }
            } else if (obj instanceof Context) {
                Context c = (Context) obj;
                // 仅当主机已启动时。 如果没有，那么稍后它将有自己的“after_start”事件。
                if (c.getParent().getState().isAvailable()) {
                    registerContext(c);
                }
            } else if (obj instanceof Host) {
                registerHost((Host) obj);
            }
        } else if (event.getType().equals(Lifecycle.BEFORE_STOP_EVENT)) {
            Object obj = event.getSource();
            if (obj instanceof Wrapper) {
                unregisterWrapper((Wrapper) obj);
            } else if (obj instanceof Context) {
                unregisterContext((Context) obj);
            } else if (obj instanceof Host) {
                unregisterHost((Host) obj);
            }
        }
	}
	
	@Override
	protected void initInternal() throws LifecycleException {}

	@Override
	protected void destroyInternal() throws LifecycleException {}

	// -------------------------------------------------------------------------------------
	// 容器监听器方法
	// -------------------------------------------------------------------------------------
	@Override
	public void containerEvent(ContainerEvent event) {
		if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
            Container child = (Container) event.getData();
            addListeners(child);
            // 如果 child 已启动，那么生命周期侦听器注册 child 为时已晚，因此请在此处注册
            if (child.getState().isAvailable()) {
                if (child instanceof Host) {
                    registerHost((Host) child);
                } else if (child instanceof Context) {
                    registerContext((Context) child);
                } else if (child instanceof Wrapper) {
                    // 仅当上下文已启动时。 如果没有，那么稍后它会有自己的“after_start”生命周期事件
                    if (child.getParent().getState().isAvailable()) {
                        registerWrapper((Wrapper) child);
                    }
                }
            }
        } else if (Container.REMOVE_CHILD_EVENT.equals(event.getType())) {
            Container child = (Container) event.getData();
            removeListeners(child);
            // 无需注销 - 生命周期侦听器将在孩子停止时处理此问题
        } else if (Host.ADD_ALIAS_EVENT.equals(event.getType())) {
            // 处理动态添加 Host 别名
            mapper.addHostAlias(((Host) event.getSource()).getName(), event.getData().toString());
        } else if (Host.REMOVE_ALIAS_EVENT.equals(event.getType())) {
            // 处理动态删除 Host 别名
            mapper.removeHostAlias(event.getData().toString());
        } else if (Wrapper.ADD_MAPPING_EVENT.equals(event.getType())) {
            // 处理动态添加 Wrapper
            Wrapper wrapper = (Wrapper) event.getSource();
            Context context = (Context) wrapper.getParent();
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            String version = context.getWebappVersion();
            String hostName = context.getParent().getName();
            String wrapperName = wrapper.getName();
            String mapping = (String) event.getData();
            mapper.addWrapper(hostName, contextPath, version, mapping, wrapper, context.isResourceOnlyServlet(wrapperName));
        } else if (Wrapper.REMOVE_MAPPING_EVENT.equals(event.getType())) {
            // 处理动态删除 Wrapper
            Wrapper wrapper = (Wrapper) event.getSource();

            Context context = (Context) wrapper.getParent();
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            String version = context.getWebappVersion();
            String hostName = context.getParent().getName();

            String mapping = (String) event.getData();

            mapper.removeWrapper(hostName, contextPath, version, mapping);
        } else if (Context.ADD_WELCOME_FILE_EVENT.equals(event.getType())) {
            // 处理动态添加 Welcome 文件
            Context context = (Context) event.getSource();

            String hostName = context.getParent().getName();

            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            mapper.addWelcomeFile(hostName, contextPath, context.getWebappVersion(), welcomeFile);
        } else if (Context.REMOVE_WELCOME_FILE_EVENT.equals(event.getType())) {
            // 处理动态删除 Welcome 文件
            Context context = (Context) event.getSource();

            String hostName = context.getParent().getName();

            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            mapper.removeWelcomeFile(hostName, contextPath, context.getWebappVersion(), welcomeFile);
        } else if (Context.CLEAR_WELCOME_FILES_EVENT.equals(event.getType())) {
            // 处理动态清除 Welcome 文件
            Context context = (Context) event.getSource();

            String hostName = context.getParent().getName();

            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            mapper.clearWelcomeFiles(hostName, contextPath, context.getWebappVersion());
        }
	}
	
	/**
	 * 将此 MapperListener 添加到指定容器及其所有子容器中
     */
    private void addListeners(Container container) {
        container.addContainerListener(this);
        container.addLifecycleListener(this);
        for (Container child : container.findChildren()) {
            addListeners(child);
        }
    }

    /**
     * 从指定容器及其所有子容器中删除此 MapperListener
     * Remove this mapper from the container and all child containers
     */
    private void removeListeners(Container container) {
        container.removeContainerListener(this);
        container.removeLifecycleListener(this);
        for (Container child : container.findChildren()) {
            removeListeners(child);
        }
    }
    
    /**
     * 根据 Engine 中配置的默认 Host 名称遍历其自容器中的Host，并将对应的 Host 保存到 Mapper中
     */
    private void findDefaultHost() {
        Engine engine = service.getContainer();
        String defaultHost = engine.getDefaultHost();

        boolean found = false;

        if (defaultHost != null && defaultHost.length() > 0) {
            Container[] containers = engine.findChildren();

            for (Container container : containers) {
                Host host = (Host) container;
                if (defaultHost.equalsIgnoreCase(host.getName())) {
                    found = true;
                    break;
                }

                String[] aliases = host.findAliases();
                for (String alias : aliases) {
                    if (defaultHost.equalsIgnoreCase(alias)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (found) {
            mapper.setDefaultHostName(defaultHost);
        } else {
            logger.error("默认Host未知, by serviceName: [{}]", service.getName());
        }
    }
    
    /**
     * 注册 Host
     */
    private void registerHost(Host host) {
        String[] aliases = host.findAliases();
        mapper.addHost(host.getName(), aliases, host);

        for (Container container : host.findChildren()) {
            if (container.getState().isAvailable()) {
                registerContext((Context) container);
            }
        }

        // 默认 Host 可能已更改
        findDefaultHost();

        if(logger.isDebugEnabled()) {
            logger.debug("Host注册, by hostName: [{}], serviceName: [{}]", host.getName(), service.getName());
        }
    }
    
    /**
     * 注销 Host
     */
    private void unregisterHost(Host host) {
        String hostname = host.getName();

        mapper.removeHost(hostname);

        // 默认 Host 可能已更改
        findDefaultHost();

        if(logger.isDebugEnabled()) {
        	logger.debug("Host注销, by hostName: [{}], serviceName: [{}]", hostname, service.getName());
        }
    }
    
    /**
     * 注册 Context
     */
    private void registerContext(Context context) {
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        Host host = (Host)context.getParent();

        WebResourceRoot resources = context.getResources();
        String[] welcomeFiles = context.findWelcomeFiles();
        List<WrapperMappingInfo> wrappers = new ArrayList<>();

        for (Container container : context.findChildren()) {
            prepareWrapperMappingInfo(context, (Wrapper) container, wrappers);

            if(logger.isDebugEnabled()) {
                logger.debug("Wrapper注册, by wrapperName: [{}], contextPath: [{}], hostName: [{}]", container.getName(), contextPath, host.getName());
            }
        }

        mapper.addContextVersion(host.getName(), host, contextPath, context.getWebappVersion(), context, welcomeFiles, resources, wrappers);

        if(logger.isDebugEnabled()) {
        	logger.debug("Context注册, by contextPath: [{}], contextName: [{}], hostName: [{}]", contextPath, context.getName(), host.getName());
//        			contextPath == "" ? "''": contextPath, context.getName() == "" ? "''": context.getName(), host.getName());
        	
        }
    }


    /**
     * 注销 Context
     */
    private void unregisterContext(Context context) {
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        String hostName = context.getParent().getName();

        if (context.getPaused()) {
            if (logger.isDebugEnabled()) {
            	logger.debug("Context暂停, by contextPath: [{}], hostName: [{}]", contextPath, hostName);
            }

            mapper.removeContextVersion(context, hostName, contextPath, context.getWebappVersion());
        } else {
            if (logger.isDebugEnabled()) {
            	logger.debug("Context注销, by contextPath: [{}], hostName: [{}]", contextPath, hostName);
            }

            mapper.removeContextVersion(context, hostName, contextPath, context.getWebappVersion());
        }
    }


    /**
     * 注册 wrapper
     */
    private void registerWrapper(Wrapper wrapper) {
        Context context = (Context) wrapper.getParent();
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        String version = context.getWebappVersion();
        String hostName = context.getParent().getName();
        
        List<WrapperMappingInfo> wrappers = new ArrayList<>();
        prepareWrapperMappingInfo(context, wrapper, wrappers);
        
        mapper.addWrappers(hostName, contextPath, version, wrappers);

        if(logger.isDebugEnabled()) {
            logger.debug("Wrapper注册, by wrapperName: [{}], contextPath: [{}], Service: [{}]", wrapper.getName(), contextPath, service);
        }
    }
    
    /**
     * 注销 wrapper
     */
    private void unregisterWrapper(Wrapper wrapper) {
        Context context = ((Context) wrapper.getParent());
        String contextPath = context.getPath();

        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        String version = context.getWebappVersion();
        String hostName = context.getParent().getName();

        String[] mappings = wrapper.findMappings();
        for (String mapping : mappings) {
            mapper.removeWrapper(hostName, contextPath, version,  mapping);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("Wrapper注销, by wrapperName: [{}], contextPath: [{}], Service: [{}]", wrapper.getName(), contextPath, service);
        }
    }
    
    /**
     * 使用此上下文中此 wrapper 的映射注册信息填充 <code>wrappers</code> 列表
     */
    private void prepareWrapperMappingInfo(Context context, Wrapper wrapper, List<WrapperMappingInfo> wrappers) {
        String wrapperName = wrapper.getName();
        boolean resourceOnly = context.isResourceOnlyServlet(wrapperName);
        String[] mappings = wrapper.findMappings();
        for (String mapping : mappings) {
            wrappers.add(new WrapperMappingInfo(mapping, wrapper, resourceOnly));
        }
    }
}
