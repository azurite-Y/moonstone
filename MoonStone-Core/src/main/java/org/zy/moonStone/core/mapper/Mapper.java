package org.zy.moonStone.core.mapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.MappingMatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.Constants;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Host;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.util.buf.CharChunk;
import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年8月16日;
 * @author zy(azurite-Y);
 * @description Mapper，它实现了 servlet API 映射规则（从 HTTP 规则派生而来）
 */
public class Mapper {
    private static final Logger logger = LoggerFactory.getLogger(Mapper.class);

    /** 包含虚拟 Host 定义的数组 */
    volatile MappedHost[] hosts = new MappedHost[0];

    /** 默认 Host 名称 */
    private volatile String defaultHostName = null;
    /** 默认 Host */
    private volatile MappedHost defaultHost = null;

    /** 从 Context 对象映射到 ContextVersion 以支持 RequestDispatcher 映射 */
    private final Map<Context, ContextVersion> contextObjectToContextVersionMap = new ConcurrentHashMap<>();
    
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    /**
     * 设置默认 Host.
     *
     * @param defaultHostName - 默认 Host 名称
     */
    public synchronized void setDefaultHostName(String defaultHostName) {
        this.defaultHostName = renameWildcardHost(defaultHostName);
        if (this.defaultHostName == null) {
            defaultHost = null;
        } else {
            defaultHost = exactFind(hosts, this.defaultHostName);
        }
    }
    
    /**
     * 将新 Host 添加到 Mapper
     *
     * @param name - 虚拟 Host 名称
     * @param aliases - 虚拟 Host 的别名
     * @param host - Host 对象
     */
    public synchronized void addHost(String name, String[] aliases, Host host) {
        name = renameWildcardHost(name);
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        // 真实的 Host
        MappedHost realHost = new MappedHost(name, host);
        if (insertMap(hosts, newHosts, realHost)) {
            hosts = newHosts;
            if (realHost.name.equals(defaultHostName)) {
                defaultHost = realHost;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Host注册成功, by name: [{}]", name);
            }
        } else { // 添加 Host 已存在
            MappedHost duplicate = hosts[find(hosts, name)];
            if (duplicate.object == host) {
                // Host 已经在 Mapper 中注册。 例如。 它可能是由 addContextVersion() 添加的
                if (logger.isDebugEnabled()) {
                    logger.debug("Host已注册, by name: [[{}]]", name);
                }
                realHost = duplicate;
            } else {
                logger.debug("同名Host已注册, by name: [{}]. reallHostName: [{}]", name, duplicate.getRealHostName());

                // 不要添加别名，因为 removeHost(hostName) 将无法删除它们
                return;
            }
        }
        
        // 添加别名
        List<MappedHost> newAliases = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            alias = renameWildcardHost(alias);
            MappedHost newAlias = new MappedHost(alias, realHost);
            if (addHostAliasImpl(newAlias)) {
                newAliases.add(newAlias);
            }
        }
        realHost.addAliases(newAliases);
    }


    /**
     * 从 Mapper 中移除 Host
     *
     * @param name - 虚拟 Host 名称
     */
    public synchronized void removeHost(String name) {
        name = renameWildcardHost(name);
        // 找到并删除旧 Host
        MappedHost host = exactFind(hosts, name);
        if (host == null || host.isAlias()) {
            return;
        }
        MappedHost[] newHosts = hosts.clone();
        // 删除真实 Host 及其所有别名
        int j = 0;
        for (int i = 0; i < newHosts.length; i++) {
            if (newHosts[i].getRealHost() != host) {
                newHosts[j++] = newHosts[i];
            }
        }
        hosts = Arrays.copyOf(newHosts, j);
    }

    /**
     * 为已有 Host 添加别名
     * 
     * @param name - Host 名称
     * @param alias - 添加的别名
     */
    public synchronized void addHostAlias(String name, String alias) {
        MappedHost realHost = exactFind(hosts, name);
        if (realHost == null) {
            // 不应该为不存在的 Host 添加别名，但只是以防万一..
            return;
        }
        alias = renameWildcardHost(alias);
        MappedHost newAlias = new MappedHost(alias, realHost);
        if (addHostAliasImpl(newAlias)) {
            realHost.addAlias(newAlias);
        }
    }

    private synchronized boolean addHostAliasImpl(MappedHost newAlias) {
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        if (insertMap(hosts, newHosts, newAlias)) {
            hosts = newHosts;
            if (newAlias.name.equals(defaultHostName)) {
                defaultHost = newAlias;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Host别名注册成功, by aliasName: [{}], reallHostName: [{}]", newAlias.name, newAlias.getRealHostName());
            }
            return true;
        } else {
            MappedHost duplicate = hosts[ find(hosts, newAlias.name) ];
            if (duplicate.getRealHost() == newAlias.getRealHost()) {
                // 同一 Host 的重复别名。一种无害的冗余。例如：
                //<Host name="localhost"><Alias>localhost</Alias></Host>
                if (logger.isDebugEnabled()) {
                    logger.debug("Host重复别名, by aliasName: [{}], reallHostName: [{}]", newAlias.name, newAlias.getRealHostName());
                }
                return false;
            }
            logger.debug("同别名Host已注册, by aliasName: [{}], aliasRealHostName:: [{}], reallHostName: [{}]", newAlias.name, newAlias.getRealHostName(), duplicate.getRealHostName());
            return false;
        }
    }

    /**
     * 移除 Host 别名
     * @param alias - 要移除的 Host 别名
     */
    public synchronized void removeHostAlias(String alias) {
        alias = renameWildcardHost(alias);
        // 查找和删除别名
        MappedHost hostMapping = exactFind(hosts, alias);
        if (hostMapping == null || !hostMapping.isAlias()) {
            return;
        }
        MappedHost[] newHosts = new MappedHost[hosts.length - 1];
        if (removeMap(hosts, newHosts, alias)) {
            hosts = newHosts;
            hostMapping.getRealHost().removeAlias(hostMapping);
        }
    }
    
    /**
     * 向现有 Host 添加新 Context
     * 
     * @param hostName - 此 Context 所属的虚拟 Host 名称
     * @param host - Host 对象
     * @param path -  Context 路径
     * @param version - Context 版本
     * @param context - Context 对象
     * @param welcomeResources - 为此 Context 定义的 Welcome 文件
     * @param resources - Context 的静态资源
     * @param wrappers - 有关 Wrapper 映射的信息
     */
    public void addContextVersion(String hostName, Host host, String path, String version, Context context, String[] welcomeResources, 
    		WebResourceRoot resources, Collection<WrapperMappingInfo> wrappers) {
        hostName = renameWildcardHost(hostName);

        MappedHost mappedHost  = exactFind(hosts, hostName);
        if (mappedHost == null) {
            addHost(hostName, new String[0], host);
            mappedHost = exactFind(hosts, hostName);
            if (mappedHost == null) {
                logger.error("添加Context无关联Host. by hostName: [{}]", hostName);
                return;
            }
        }
        if (mappedHost.isAlias()) {
            logger.error("添加Context关联别名Host. by aliasName: [{}]", hostName);
            return;
        }
        int slashCount = slashCount(path);
        synchronized (mappedHost) {
            ContextVersion newContextVersion = new ContextVersion(version, path, slashCount, context, resources, welcomeResources);
            if (wrappers != null) {
                addWrappers(newContextVersion, wrappers);
            }

            ContextList contextList = mappedHost.contextList;
            
            MappedContext mappedContext = exactFind(contextList.contexts, path);
            if (mappedContext == null) {
                mappedContext = new MappedContext(path, newContextVersion);
                ContextList newContextList = contextList.addContext(mappedContext, slashCount);
                if (newContextList != null) {
                	// 由新 mappedContext 替换旧值之后
                    updateContextList(mappedHost, newContextList);
                    contextObjectToContextVersionMap.put(context, newContextVersion);
                }
            } else {
            	// Context 路径已映射有 MappedContext
                ContextVersion[] contextVersions = mappedContext.versions;
                
                ContextVersion[] newContextVersions = new ContextVersion[contextVersions.length + 1];
                // 尝试追加新的 ContextVersion 到在已有 MappedContext
                if (insertMap(contextVersions, newContextVersions, newContextVersion)) {
                	// 由新 ContextVersion 替换旧值之后
                    mappedContext.versions = newContextVersions;
                    contextObjectToContextVersionMap.put(context, newContextVersion);
                } else {
                    // Context.reload() 后重新注册, 将 ContextVersion 替换为新的
                    int pos = find(contextVersions, version);
                    if (pos >= 0 && contextVersions[pos].name.equals(version)) {
                        contextVersions[pos] = newContextVersion;
                        contextObjectToContextVersionMap.put(context, newContextVersion);
                    }
                }
            }
        }
    }

    /**
     * 从现有 Host 中删除 Context
     *
     * @param ctxt - 实际 Context      
     * @param hostName - 此 Context 所属的虚拟 Host 名称
     * @param path - Context 路径
     * @param version - Context 版本
     */
    public void removeContextVersion(Context ctxt, String hostName, String path, String version) {
        hostName = renameWildcardHost(hostName);
        contextObjectToContextVersionMap.remove(ctxt);

        MappedHost host = exactFind(hosts, hostName);
        if (host == null || host.isAlias()) {
            return;
        }

        synchronized (host) {
            ContextList contextList = host.contextList;
            
            // 查找 Context 路径映射的 MappedContext
            MappedContext context = exactFind(contextList.contexts, path);
            if (context == null) {
                return;
            }

            ContextVersion[] contextVersions = context.versions;
            ContextVersion[] newContextVersions = new ContextVersion[contextVersions.length - 1];
            if (removeMap(contextVersions, newContextVersions, version)) {
                if (newContextVersions.length == 0) {
                    // 移除 context
                    ContextList newContextList = contextList.removeContext(path);
                    if (newContextList != null) {
                        updateContextList(host, newContextList);
                    }
                } else {
                    context.versions = newContextVersions;
                }
            }
        }
    }
    
    
    /**
     * 将 Wrapper 添加到给定的 Context
     * 
     * @param hostName - 此 Context 所属的虚拟 Host 名称
     * @param contextPath - Context 路径
     * @param version - Context 版本
     * @param path - Wrapper 映射
     * @param wrapper - Wrapper 对象
     * @param resourceOnly - 如果此包装器始终期望存在物理资源（例如 JSP），则为 true
     */
	public void addWrapper(String hostName, String contextPath, String version, String path, Wrapper wrapper, boolean resourceOnly) {
		hostName = renameWildcardHost(hostName);
		ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
		if (contextVersion == null) {
			return;
		}
		addWrapper(contextVersion, path, wrapper, resourceOnly);
	}
    
    /**
     * 将 Wrapper 添加到给定的 Context
     * 
     * @param hostName - 此 Context 所属的虚拟 Host 名称
     * @param contextPath - Context 路径
     * @param version - Context 版本
     * @param wrappers
     */
    public void addWrappers(String hostName, String contextPath, String version, Collection<WrapperMappingInfo> wrappers) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        addWrappers(contextVersion, wrappers);
    }
    
    /**
     * 将 Wrapper 添加到给定的 Context
     *
     * @param context - 要将 Wrapper 添加到的 Context
     * @param path - Wrapper 映射
     * @param wrapper - Wrapper 对象
     * @param resourceOnly - 如果此 Wrapper 始终期望存在物理资源（例如Html），则为 true
     */
    protected void addWrapper(ContextVersion context, String path, Wrapper wrapper, boolean resourceOnly) {
        synchronized (context) {
            if (path.endsWith("/*")) {
                // 通配符 wrapper
                String name = path.substring(0, path.length() - 2); // Context 路径截除末尾 “/*”
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper, resourceOnly);
                
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length + 1];
                
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                	// 由新 wrapper 替换旧值后，更新 Context 中的通配符 wrapper
                    context.wildcardWrappers = newWrappers;
                    int slashCount = slashCount(newWrapper.name);
                    if (slashCount > context.nesting) {
                        context.nesting = slashCount;
                    }
                }
            } else if (path.startsWith("*.")) {
                // 扩展 wrapper
                String name = path.substring(2); // Context 路径截除头部 “*.”
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper, resourceOnly);
                
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length + 1];
                
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                	// 由新 wrapper 替换旧值后，更新 Context 中的扩展 wrapper
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // 根路径上下文，设置默认 wrapper
                MappedWrapper newWrapper = new MappedWrapper("", wrapper, resourceOnly);
                context.defaultWrapper = newWrapper;
            } else {
                // 精准 wrapper
                final String name;
                if (path.length() == 0) {
                    // Context 根映射的特殊情况，它被视为精确匹配
                    name = "/";
                } else {
                    name = path;
                }
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper, resourceOnly);
                
                MappedWrapper[] oldWrappers = context.exactWrappers;
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length + 1];
                
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                	// 由新 wrapper 替换旧值后，更新 Context 中的精准 wrapper
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }
    
    /**
     * 从现有 Context 中删除 Wrapper
     *
     * @param hostName - 此 Wrapper 所属的虚拟 Host 名称
     * @param contextPath - 此 Wrapper 所属的 Context 路径
     * @param version - 此 Wrapper 所属的 Context 版本
     * @param path - Wrapper 映射
     */
    public void removeWrapper(String hostName, String contextPath, String version, String path) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, true);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        removeWrapper(contextVersion, path);
    }

    protected void removeWrapper(ContextVersion context, String path) {
        if (logger.isDebugEnabled()) {
            logger.debug("Wrapper移除, by contextName: [{}], contextPath: [{}]", context.name, path);
        }

        synchronized (context) {
            if (path.endsWith("/*")) {
                // 通配符 wrapper
                String name = path.substring(0, path.length() - 2);
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    // 重新计算 nesting
                    context.nesting = 0;
                    for (MappedWrapper newWrapper : newWrappers) {
                        int slashCount = slashCount(newWrapper.name);
                        if (slashCount > context.nesting) {
                            context.nesting = slashCount;
                        }
                    }
                    context.wildcardWrappers = newWrappers;
                }
            } else if (path.startsWith("*.")) {
                // 扩展 wrapper
                String name = path.substring(2);
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                context.defaultWrapper = null;
            } else {
                // Exact wrapper
                String name;
                if (path.length() == 0) {
                    // 上下文根映射的特殊情况，它被视为精确匹配
                    name = "/";
                } else {
                    name = path;
                }
                MappedWrapper[] oldWrappers = context.exactWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }

    /**
     * 将 Welcome 文件添加到给定的 Context
     *
     * @param hostName - 可以找到给定 Context 的 Host 名称
     * @param contextPath - 给定 Context 的路径
     * @param version - 给定 Context 的版本
     * @param welcomeFile - 要添加的 Welcome 文件
     */
    public void addWelcomeFile(String hostName, String contextPath, String version, String welcomeFile) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        int len = contextVersion.welcomeResources.length + 1;
        String[] newWelcomeResources = new String[len];
        System.arraycopy(contextVersion.welcomeResources, 0, newWelcomeResources, 0, len - 1);
        newWelcomeResources[len - 1] = welcomeFile;
        contextVersion.welcomeResources = newWelcomeResources;
    }

    /**
     * 删除给定的 Context 的 Welcome 文件
     *
     * @param hostName - 可以找到给定 Context 的 Host 名称
     * @param contextPath - 给定 Context 的路径
     * @param version - 给定 Context 的版本
     * @param welcomeFile - 要删除的 Welcome 文件
     */
    public void removeWelcomeFile(String hostName, String contextPath, String version, String welcomeFile) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        int match = -1;
        for (int i = 0; i < contextVersion.welcomeResources.length; i++) {
            if (welcomeFile.equals(contextVersion.welcomeResources[i])) {
                match = i;
                break;
            }
        }
        if (match > -1) {
            int len = contextVersion.welcomeResources.length - 1;
            String[] newWelcomeResources = new String[len];
            System.arraycopy(contextVersion.welcomeResources, 0, newWelcomeResources, 0, match);
            if (match < len) {
                System.arraycopy(contextVersion.welcomeResources, match + 1, newWelcomeResources, match, len - match);
            }
            contextVersion.welcomeResources = newWelcomeResources;
        }
    }

    /**
     * 清除给定 Context 的 Welcome 文件
     *
     * @param hostName - 可以找到给定 Context 的 Host 名称
     * @param contextPath - 给定 Context 的路径
     * @param version - 给定 Context 的版本
     */
    public void clearWelcomeFiles(String hostName, String contextPath, String version) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        contextVersion.welcomeResources = new String[0];
    }
    
    /**
     * 映射指定的主机名和 URI，改变给定的映射数据
     * 
     * @param host - 虚拟 Host 名称
     * @param uri - URI
     * @param version - 要映射的请求中包含的版本（如果有）
     * @param mappingData - 该结构将包含映射操作的结果
     * @throws IOException - 如果缓冲区太小而无法保存映射结果
     */
    public void map(MessageBytes host, MessageBytes uri, String version, MappingData mappingData) throws IOException {
        if (host.isNull()) {
            String defaultHostName = this.defaultHostName;
            if (defaultHostName == null) {
                return;
            }
            host.getCharChunk().append(defaultHostName);
        }
        host.toChars();
        uri.toChars();
        internalMap(host.getCharChunk(), uri.getCharChunk(), version, mappingData);
    }
    
    /**
     * 将指定的URI相对于上下文映射，改变给定的映射数据。
     *
     * @param context - 实际的环境
     * @param uri - URI
     * @param mappingData - 这个结构将包含映射操作的结果
     * @throws IOException - 如果缓冲区太小，无法容纳映射的结果
     */
    public void map(Context context, MessageBytes uri, MappingData mappingData) throws IOException {
        ContextVersion contextVersion = contextObjectToContextVersionMap.get(context);
        uri.toChars();
        CharChunk uricc = uri.getCharChunk();
        uricc.setLimit(-1);
        internalMapWrapper(contextVersion, uricc, mappingData);
    }
    
    
    // -------------------------------------------------------------------------------------
    // 私有方法
    // -------------------------------------------------------------------------------------
	/**
	 * 为了简化映射过程，通配符主机在内部采用“.apache.org”而不是“*.apache.org”的形式。 
	 * 然而，为了便于使用，外部形式仍然是“*.apache.org”。 
	 * 传入这个类的任何主机名都需要通过这个方法来重命名和通配符主机名从外部形式到内部形式。
	 */
	private static String renameWildcardHost(String hostName) {
	    if (hostName != null && hostName.startsWith("*.")) {
	        return hostName.substring(1);
	    } else {
	        return hostName;
	    }
	}

	/**
	 * 在 MapElement 的排序数组中查找给定名称的 MapElement 。 这将返回正在搜索的元素。 否则它将返回 <code>null</code>。
	 * 
	 * @param map - 查找数据数组
	 * @param name - 指定查找的数据
	 * 
	 * @param <T> - 方法返回值类型
	 * @param <E> - MapElement 元素封装的对象类型
	 * 
	 * @see #find(MapElement[], String)
	 */
	private static final <T, E extends MapElement<T>> E exactFind(E[] map, String name) {
	    int pos = find(map, name);
	    if (pos >= 0) {
	        E result = map[pos];
	        if (name.equals(result.name)) {
	            return result;
	        }
	    }
	    return null;
	}

	/**
	 * 在Map元素的排序数组中查找给定其名称的Map元素。
	 * 
	 * @param map - 查找数据数组
	 * @param name - 指定查找的数据
	 * @return 给定数组中相等项的索引，未找到则返回-1
	 * 
	 * @param <T> - MapElement 元素封装的对象类型
	 * @see #exactFind(MapElement[], String)
	 */
	private static final <T> int find(MapElement<T>[] map, String name) {
	    int len = map.length;
	
	    for (int i = 0; i < len; i++) {
			if (name.compareTo(map[i].name) == 0) {
				return i;
			}
		}
		return -1;
	}

    /**
     * 插入到已排序的MapElement数组的正确位置，并防止重复
     * 
     * @param oldMap - 查找数据数组
     * @param newMap - 存储修改之后的数据集数组
     * @param name - 指定查找的名称
     * @return true代表更换旧值成功，false代表读取容器中无插入数据
     * 
     * @param <T> -  MapElement 子类封装的对象类型
     */
    private static final <T> boolean insertMap(MapElement<T>[] oldMap, MapElement<T>[] newMap, MapElement<T> newElement) {
        int pos = find(oldMap, newElement.name);
        if ((pos != -1) && (newElement.name.equals(oldMap[pos].name))) {
            return false;
        }
        System.arraycopy(oldMap, 0, newMap, 0, pos + 1);
        newMap[pos + 1] = newElement;
        System.arraycopy(oldMap, pos + 1, newMap, pos + 2, oldMap.length - pos - 1);
        return true;
    }
    
    /**
     * 删除已排序的MapElement数组中指定名称的的 MapElement 元素，并保存数据到新数组中
     * 
     * @param oldMap - 查找数据数组
     * @param newMap - 存储修改之后的数据集数组
     * @param name - 指定查找的名称
     * @return true代表删除旧值成功，false代表未在指定查找数组中找到目标数据
     * 
     * @param <T> -  MapElement 子类封装的对象类型
     */
    private static final <T> boolean removeMap(MapElement<T>[] oldMap, MapElement<T>[] newMap, String name) {
        int pos = find(oldMap, name);
        if ((pos != -1) && (name.equals( oldMap[pos].name ))) {
            System.arraycopy(oldMap, 0, newMap, 0, pos);
            System.arraycopy(oldMap, pos + 1, newMap, pos, oldMap.length - pos - 1);
            return true;
        }
        return false;
    }
    
    /**
     * 
     * @param hostName - 可以找到给定 Context 的 Host 名称
     * @param contextPath - 给定 Context 的路径
     * @param version - 给定 Context 的版本
     * @param silent - 是否压制此方法 error 级日志
     * @return
     */
    private ContextVersion findContextVersion(String hostName, String contextPath, String version, boolean silent) {
        MappedHost host = exactFind(hosts, hostName);
        if (  host == null || host.isAlias() ) {
        	if ( !silent ) {
        		logger.error(host == null ? "未找到指定 Host" : "指定 Host 名称为别名" + ", by hostName: " + hostName);
        	}
        	return null;
        }
        MappedContext context = exactFind(host.contextList.contexts, contextPath);
        if (context == null) {
            if (!silent) {
            	logger.error("未找到指定 Context, by contexPath: [{}]", contextPath);
            }
            return null;
        }
        ContextVersion contextVersion = exactFind(context.versions, version);
        if (contextVersion == null) {
            if (!silent) {
            	logger.error("未找到指定 Context 的 ContextVersion , by contexPath: [{}], version: [{}]", contextPath, version);
            }
            return null;
        }
        return contextVersion;
    }
    
    /**
     * 返回给定字符串中的左斜杆("/") 计数
     */
    private static final int slashCount(String name) {
        int pos = -1;
        int count = 0;
        while ((pos = name.indexOf('/', pos + 1)) != -1) {
            count++;
        }
        return count;
    }
    
    /**
     * 将 <code>realHost</code> 中的 {@link MappedHost#contextList} 字段及其所有别名替换为新值。
     */
	private void updateContextList(MappedHost realHost, ContextList newContextList) {
        realHost.contextList = newContextList;
        for (MappedHost alias : realHost.getAliases()) {
            alias.contextList = newContextList;
        }
    }
	
	/**
	 * 将 Wrapper 添加到给定的 Context
     *
     * @param contextVersion - 添加 Wrapper 的 Context
     * @param wrappers - 有关 Wrapper 映射的信息
     */
    private void addWrappers(ContextVersion contextVersion, Collection<WrapperMappingInfo> wrappers) {
        for (WrapperMappingInfo wrapper : wrappers) {
            addWrapper(contextVersion, wrapper.getMapping(), wrapper.getWrapper(), wrapper.isResourceOnly());
        }
    }
    
    /**
     * 映射指定的 URI
     * 
     * @param host - 虚拟 Host 名称
     * @param uri - URI
     * @param version - 要映射的请求中包含的版本（如果有）
     * @param mappingData - 该结构将包含映射操作的结果
     * @throws IOException - 如果缓冲区太小而无法保存映射结果
     */
    private final void internalMap(CharChunk host, CharChunk uri, String version, MappingData mappingData) throws IOException {
        if (mappingData.host != null) {
            throw new AssertionError();
        }

        // 根据 HostName 查找已注册的 MappedHost
        MappedHost[] hosts = this.hosts;
        MappedHost mappedHost = exactFindIgnoreCase(hosts, host);
        if (mappedHost == null) {
            // 注意：在内部，Mapper 不使用通配符主机上的前导 *。
            int firstDot = host.indexOf('.');
            if (firstDot > -1) {
                int offset = host.getOffset();
                try {
                    host.setOffset(firstDot + offset);
                    mappedHost = exactFindIgnoreCase(hosts, host);
                } finally {
                    // 绝对确保这被重置
                    host.setOffset(offset);
                }
            }
            if (mappedHost == null) {
                mappedHost = defaultHost;
                if (mappedHost == null) {
                    return;
                }
            }
        }
        mappingData.host = mappedHost.object;

        if (uri.isNull()) {
            // 没有 uri 无法映射 Context 或 Wrapper
            return;
        }

        uri.setLimit(-1);

        // Context mapping
        ContextList contextList = mappedHost.contextList;
        MappedContext[] contexts = contextList.contexts;
        
        MappedContext context = null;
        context = exactFind(contexts, uri);

        if (context == null && contextList.defaultMappedContext() == null) {
            return;
        }
        context = contextList.defaultMappedContext();

//        mappingData.contextPath.setString(context.name);

        ContextVersion contextVersion = null;
        ContextVersion[] contextVersions = context.versions;
        final int versionCount = contextVersions.length;
        if (versionCount > 1) {
            Context[] contextObjects = new Context[contextVersions.length];
            for (int i = 0; i < contextObjects.length; i++) {
                contextObjects[i] = contextVersions[i].object;
            }
            mappingData.contexts = contextObjects;
            if (version != null) {
                contextVersion = exactFind(contextVersions, version);
            }
        }
        if (contextVersion == null) {
            /*
             * 返回最新版本
             * 已知版本数组至少包含一个元素
             */
            contextVersion = contextVersions[versionCount - 1];
        }
        mappingData.context = contextVersion.object;
        mappingData.contextSlashCount = contextVersion.slashCount;

        // Wrapper mapping
        if (!contextVersion.isPaused()) {
            internalMapWrapper(contextVersion, uri, mappingData);
        }
    }
    
	/**
	 * 在 MapElement的排序数组中查找给定名称的 MapElement。 这将返回正在搜索的元素。 否则它将返回 <code>null</code>。
	 * 
	 * @param map  - 查找数据数组
	 * @param name - 指定查找的数据
	 * 
	 * @see #findIgnoreCase(MapElement[], CharChunk)
	 */
    private static final <T, E extends MapElement<T>> E exactFindIgnoreCase(E[] map, CharChunk name) {
        int pos = findIgnoreCase(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equalsIgnoreCase(result.name)) {
                return result;
            }
        }
        return null;
    }
    
	/**
	 * 在Map元素的排序数组中查找给定其名称的Map元素。
	 * 
	 * @param map  - 查找数据数组
	 * @param name - 指定查找的数据
	 * @return 给定数组中相等项的索引，未找到则返回-1
	 * 
	 * @param <T> - MapElement 元素封装的对象类型
	 */
    private static final <T> int findIgnoreCase(MapElement<T>[] map, CharChunk name) {
    	 char[] nameChars = name.getBuffer();
    	 
    	 char[] elementCharrs;
    	 // 标识查询数组中数据的大小写，true代表此字符大写
    	 boolean upperCaseA = false;
    	 boolean upperCaseB = false;
    	 char a = 0,b = 0;
    	 
    	 int nameEndIndex = name.getEnd() - 1;
    	 
    	 for (int i = 0; i < map.length; i++) {
    		 elementCharrs = map[i].name.toCharArray();
    		 if (elementCharrs.length != nameChars.length) { // 字符数不同则直接跳过
    			 continue;
    		 }
    		 
			for (int j = name.getOffset(); j < name.getEnd(); j++, upperCaseA = false, upperCaseB = false) {
				// 全部转换为小写字符进行比较
				if (elementCharrs[j] >= Constants.uppercaseByteMin && elementCharrs[j] <= Constants.uppercaseByteMax) {
					upperCaseA = true;
				}
				if ( nameChars[j] >= Constants.uppercaseByteMin && nameChars[j] <= Constants.uppercaseByteMax) {
					upperCaseB = true;
				}
				
				if ( (upperCaseA && upperCaseB) || (!upperCaseA && !upperCaseB) ) {  // nameChars[j] 和 elementCharrs[j] 都是同样的大小写则直接比较
					a = elementCharrs[j];
					b = nameChars[j];
				} else if (upperCaseA && !upperCaseB) {
					a = (char) (elementCharrs[j] + Constants.LC_OFFSET); // 转换小写
					b = nameChars[j];
				} else if (!upperCaseA && upperCaseB) {
					a = elementCharrs[j];
					b = (char) (nameChars[j] + Constants.LC_OFFSET); // 转换小写
				}
				
				if ( a != b ) {
					break;
				} else if (j == nameEndIndex) { // 一直比对到末尾字符都是相等
					return i;
				}
			}
		}
 		return -1;
    }
    
    /**
	 * 在 MapElement的排序数组中查找给定名称的 MapElement。 这将返回您正在搜索的元素。 否则它将返回 <code>null</code>。
	 * 
	 * @param map  - 查找数据数组
	 * @param name - 指定查找的数据
	 * 
	 * @see #findIgnoreCase(MapElement[], CharChunk)
	 */
    private static final <T, E extends MapElement<T>> E exactFind(E[] map, CharChunk name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }
    
	/**
	 * 在Map元素的排序数组中查找给定其名称的Map元素。
	 * 
	 * @param map  - 查找数据数组
	 * @param name - 指定查找的数据
	 * @return 给定数组中相等项的索引，未找到则返回-1
	 * 
	 * @param <T> - MapElement 元素封装的对象类型
	 */
	private static final <T> int find(MapElement<T>[] map, CharChunk name) {
		char[] nameChars = name.getBuffer();
		char[] elementCharrs;

		for (int i = 0; i < map.length; i++) {
			elementCharrs = map[i].name.toCharArray();
			if (elementCharrs.length != nameChars.length) { // 字符数不同则直接跳过
				continue;
			}

			for (int j = name.getOffset(); j <= name.getEnd(); j++) {
				if (elementCharrs[j] != nameChars[j]) {
					break;
				} else if (j == name.getEnd()) { // 一直比对到末尾字符都是相等
					return i;
				}
			}
		}
		return -1;
	}
    
    /**
     * Wrapper 映射

     * @param version - 要映射的请求中包含的版本（如果有）
     * @param uri - URI
     * @param mappingData - 该结构将包含映射操作的结果
     * @throws IOException - 如果缓冲区太小而无法保存结果映射
     */
	private final void internalMapWrapper(ContextVersion contextVersion, CharChunk uri, MappingData mappingData) throws IOException {
		int pathOffset = uri.getOffset();
		int pathEnd = uri.getEnd();
		boolean noServletPath = false;

		int length = contextVersion.path.length();
		if (length == (pathEnd - pathOffset)) {
			noServletPath = true;
		}
		int servletPath = pathOffset + length;
		uri.setOffset(servletPath);

		// Rule 1 -- 全值匹配
		MappedWrapper[] exactWrappers = contextVersion.exactWrappers;
		// 匹配exactWrappers中Wrapper对象的name值是否有全值匹配的，若有则设置为匹配项
		internalMapExactWrapper(exactWrappers, uri, mappingData);

		// Rule 2 – 通配符匹配
		MappedWrapper[] wildcardWrappers = contextVersion.wildcardWrappers;
		if (mappingData.wrapper == null) {
			internalMapWildcardWrapper(wildcardWrappers, uri, mappingData);
		}

		if (mappingData.wrapper == null && noServletPath && contextVersion.object.getMapperContextRootRedirectEnabled()) {
			// 路径为空，重定向到“/”
			uri.append('/');
			pathEnd = uri.getEnd();
			mappingData.redirectPath.setChars(uri.getBuffer(), pathOffset, pathEnd - pathOffset);
			uri.setEnd(pathEnd - 1);
			return;
		}

		// Rule 3 -- 扩展名匹配
		MappedWrapper[] extensionWrappers = contextVersion.extensionWrappers;
		if (mappingData.wrapper == null) {
			internalMapExtensionWrapper(extensionWrappers, uri, mappingData, true);
		}

		// Rule 4 -- Default servlet
		if (mappingData.wrapper == null) {
			if (contextVersion.defaultWrapper != null) {
				// 设置默认Servlet Wrapper
				mappingData.wrapper = contextVersion.defaultWrapper.object;
				mappingData.requestPath.setChars(uri.getBuffer(), uri.getStart(), uri.getLength());
				mappingData.wrapperPath.setChars(uri.getBuffer(), uri.getStart(), uri.getLength());
				mappingData.matchType = MappingMatch.DEFAULT;
			}
			
			// 重定向到文件夹
			char[] buf = uri.getBuffer();
			if (contextVersion.resources != null && buf[pathEnd - 1] != '/') {
				String pathStr = uri.toString();
				// Note: 首先检查重定向以节省不必要的 getResource() 调用. See BZ 62968.
				if (contextVersion.object.getMapperDirectoryRedirectEnabled()) {
					WebResource file;
					// 处理上下文根
					if (pathStr.length() == 0) {
						file = contextVersion.resources.getResource("/");
					} else {
						file = contextVersion.resources.getResource(pathStr);
					}
					if (file != null && file.isDirectory()) {
						// Note: 这会改变路径：在此之后不要做任何处理（因为我们设置了redirectPath，所以不应该有）
						uri.setOffset(pathOffset);
						uri.append('/');
						mappingData.redirectPath.setChars(uri.getBuffer(), uri.getStart(), uri.getLength());
					} else {
						mappingData.requestPath.setString(pathStr);
						mappingData.wrapperPath.setString(pathStr);
					}
				} else {
					mappingData.requestPath.setString(pathStr);
					mappingData.wrapperPath.setString(pathStr);
				}
			}
		}
		uri.setOffset(pathOffset);
		uri.setEnd(pathEnd);
	}
	
	/**
	 * 精准映射
	 * 
	 * @param wrappers - 查找数据数组
	 * @param uri - 指定查找的路径
	 * @param mappingData - 该结构将包含映射操作的结果
	 */
	private final void internalMapExactWrapper(MappedWrapper[] wrappers, CharChunk uri, MappingData mappingData) {
		MappedWrapper wrapper = exactFind(wrappers, uri);
		if (wrapper != null) {
			mappingData.requestPath.setString(wrapper.name);
			mappingData.wrapper = wrapper.object;
			if (uri.equals("/")) {
				// Context 根映射 servlet 的特殊处理
				mappingData.pathInfo.setString("/");
				mappingData.wrapperPath.setString("");
				mappingData.matchType = MappingMatch.CONTEXT_ROOT;
			} else {
				mappingData.wrapperPath.setString(wrapper.name);
				mappingData.matchType = MappingMatch.EXACT;
			}
		}
	}
	
	/**
	 * 通配符映射
	 * 
     * @param wrappers - 一组用于检查匹配的包装器
     * @param uri - wrapper 路径              
     * @param mappingData - 该结构将包含映射操作的结果
	 */
	private final void internalMapWildcardWrapper(MappedWrapper[] wrappers, CharChunk uri, MappingData mappingData) {
		for (int i = 0; i < wrappers.length; i++) {
			if ( uri.startsWith(wrappers[i].name) && (uri.indexOf('/', wrappers[i].name.length() + 1) == -1) ) { // 前缀匹配uri之后又出现了一次"/"则视为不匹配
				
				mappingData.wrapperPath.setString(wrappers[i].name);
				
				int length = wrappers[i].name.length();
				if (uri.getLength() > length) {
					mappingData.pathInfo.setChars(uri.getBuffer(), uri.getOffset() + length, uri.getLength() - length);
				}
				
				mappingData.requestPath.setChars(uri.getBuffer(), uri.getOffset(), uri.getLength());
				mappingData.wrapper = wrappers[i].object;
				mappingData.matchType = MappingMatch.PATH;
			}
		}
	}
	
	/**
     * 扩展映射
     *
     * @param wrappers - 一组用于检查匹配的包装器
     * @param uri - wrapper 路径              
     * @param mappingData - 该结构将包含映射操作的结果
     * @param resourceExpected - 此映射是否期望找到资源
     */
    private final void internalMapExtensionWrapper(MappedWrapper[] wrappers, CharChunk uri, MappingData mappingData, boolean resourceExpected) {
        char[] buf = uri.getBuffer();
        int pathEnd = uri.getEnd();
        int servletPath = uri.getOffset();
        
        // 末尾"/"的索引
        int slash = -1;
        for (int i = pathEnd - 1; i >= servletPath; i--) {
            if (buf[i] == '/') {
                slash = i;
                break;
            }
        }
        
        if (slash >= 0) {
            int period = -1;
            for (int i = pathEnd - 1; i > slash; i--) {
                if (buf[i] == '.') {
                    period = i;
                    break;
                }
            }
            if (period >= 0) {
                uri.setOffset(period + 1); // 去除匹配扩展名之前的"."之后查找对应 Wrapper
                uri.setEnd(pathEnd);
                MappedWrapper wrapper = exactFind(wrappers, uri);
                if (wrapper != null && (resourceExpected || !wrapper.resourceOnly)) {
                    mappingData.wrapperPath.setChars(buf, servletPath, pathEnd - servletPath);
                    mappingData.requestPath.setChars(buf, servletPath, pathEnd - servletPath);
                    mappingData.wrapper = wrapper.object;
                    mappingData.matchType = MappingMatch.EXTENSION;
                }
                uri.setOffset(servletPath);
                uri.setEnd(pathEnd);
            }
        }
    }


    // -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    protected abstract static class MapElement<T> {
        public final String name;
        public final T object;

        public MapElement(String name, T object) {
            this.name = name;
            this.object = object;
        }
    }
    
    /**
     * 封装 Host
     */
    protected static final class MappedHost extends MapElement<Host> {
    	/** Host 下关联的多个 Context 路径的 Context 信息 */
        public volatile ContextList contextList;

        /** 链接到由所有别名共享的“真实”MappedHost */
        private final MappedHost realHost;

        /** 链接到所有已注册的别名，以便于枚举。 此字段仅在“真实”映射主机中可用。 在别名中，此字段为空 */
        private final List<MappedHost> aliases;
        
        /**
         * 使用真实 Host 的构造器
         *
         * @param name - 虚拟 Host 的名称
         * @param host - Host
         */
        public MappedHost(String name, Host host) {
            super(name, host);
            realHost = this;
            contextList = new ContextList();
            aliases = new CopyOnWriteArrayList<>();
        }

        /**
         * 使用 Host 别名的构造器
         *
         * @param alias - 虚拟 Host 的别名
         * @param realHost - 别名指向的 Host
         */
        public MappedHost(String alias, MappedHost realHost) {
            super(alias, realHost.object);
            this.realHost = realHost;
            this.contextList = realHost.contextList;
            this.aliases = null;
        }

        /**
         * @return true则此 MappedHost 为别名 Host
         */
        public boolean isAlias() {
            return realHost != this;
//            return aliases.isEmpty();
        }

        /**
         * @return 真实 Host
         */
        public MappedHost getRealHost() {
            return realHost;
        }

        /**
         * @return 真实 Host 名称
         */
        public String getRealHostName() {
            return realHost.name;
        }

        /**
         * @return Host 别名集合
         */
        public Collection<MappedHost> getAliases() {
            return aliases;
        }

        /**
         * @param alias - 添加的别名 Host
         */
        public void addAlias(MappedHost alias) {
            aliases.add(alias);
        }

        /**
         * @param c - 添加的别名 Host 集合
         */
        public void addAliases(Collection<? extends MappedHost> c) {
            aliases.addAll(c);
        }

        /**
         * @param alias - 删除的别名 Host
         */
        public void removeAlias(MappedHost alias) {
            aliases.remove(alias);
        }
    }
    
    /**
     * 封装 Context, 每一个 ContextVersion 都代表着一个不同版本的 Context
     */
    protected static final class ContextVersion extends MapElement<Context> {
    	/** Context 路径 */
        public final String path;
        /** Context 路径中 '/' 符计数 */ 
        public final int slashCount;
        /** Context 资源 */
        public final WebResourceRoot resources;
        /** welcome 文件资源路径 */
        public String[] welcomeResources;
        /** 默认 Wrapper */
        public MappedWrapper defaultWrapper = null;
        /** 全值匹配的 Wrapper（不属于通配符 Wrapper 和 扩展 Wrapper 的剩余 Wrapper） */
        public MappedWrapper[] exactWrappers = new MappedWrapper[0];
        /** 通配符 Wrapper（wrapper mapping 以“/*”结尾） */
        public MappedWrapper[] wildcardWrappers = new MappedWrapper[0];
        /** 扩展名 Wrapper（wrapper mapping 以 "*." 开头，如：*.html） */
        public MappedWrapper[] extensionWrappers = new MappedWrapper[0];
        /** 嵌套数 */
        public int nesting = 0;
        /** 暂停 */
        private volatile boolean paused;

        public ContextVersion(String version, String path, int slashCount, Context context, WebResourceRoot resources, String[] welcomeResources) {
            super(version, context);
            this.path = path;
            this.slashCount = slashCount;
            this.resources = resources;
            this.welcomeResources = welcomeResources;
        }

        public boolean isPaused() {
            return paused;
        }

        public void markPaused() {
            paused = true;
        }
    }
    
    /**
     * 内部持有 Context 路径名和 ContextVersion 数组的引用
     */
    protected static final class MappedContext extends MapElement<Void> {
        public volatile ContextVersion[] versions;
        
        
        public MappedContext(String name, ContextVersion firstVersion) {
            super(name, null);
            this.versions = new ContextVersion[] { firstVersion };
        }
    }
    
    protected static final class ContextList {
        public final MappedContext[] contexts;
        
        /** 嵌套数(Context 路径中 '/' 符计数) */
        public final int nesting;
        
        /** 默认 Context 索引 */
        public int defaultContextIndex = -1;
        
        public ContextList() {
            this(new MappedContext[0], 0);
        }

        private ContextList(MappedContext[] contexts, int nesting) {
            this.contexts = contexts;
            this.nesting = nesting;
            for (int i = 0; i < contexts.length; i++) {
				MappedContext mappedContext = contexts[i];
				if (mappedContext.name.equals("")) {
					this.defaultContextIndex = i;
					break;
				}
			}
        }

        /**
         * 添加 MappedContext 和设置嵌套数
         * @param mappedContext
         * @param slashCount - 嵌套数
         * @return 若添加的 MappedContext 替换了旧值则返回一个新的ContextList，其中保存这最新的 MappedContext 数组，若未替换则返回 null
         */
        public ContextList addContext(MappedContext mappedContext, int slashCount) {
            MappedContext[] newContexts = new MappedContext[contexts.length + 1];
            if (insertMap(contexts, newContexts, mappedContext)) {
                return new ContextList(newContexts, Math.max(nesting, slashCount));
            }
            return null;
        }

        /**
         * 删除指定 Context 路径的映射 MappedContext, 并更新嵌套数
         * @param path - Context 路径
         * @return 若成功删除指定 MappedContext 则返回一个新的ContextList，其中保存着最新的 MappedContext 数组，
         * 若未成功删除(未找到指定 Context 路径的映射 MappedContext)则返回 null
         */
        public ContextList removeContext(String path) {
            MappedContext[] newContexts = new MappedContext[contexts.length - 1];
            if (removeMap(contexts, newContexts, path)) {
                int newNesting = 0;
                for (MappedContext context : newContexts) {
                    newNesting = Math.max(newNesting, slashCount(context.name));
                }
                return new ContextList(newContexts, newNesting);
            }
            return null;
        }
        
        /**
         * @return 缺省的默认 Context
         */
        public MappedContext defaultMappedContext() {
        	return defaultContextIndex == -1 ? null : contexts[defaultContextIndex];
        }
    }
    
    /**
     * 封装 Wrapper
     * <p>
     * 注意：
     * <ul>
     * <li>扩展名匹配: *.html ==> name 为"html"</li>
     * <li>通配符匹配: /user/name/* ==> name 为"/user/name"</li>
     * <li></li>
     * </ul>
     */
    protected static class MappedWrapper extends MapElement<Wrapper> {
    	/** 如果 Wrapper 对应于 JspServlet 并且映射路径包含通配符，则为 true; 否则为false */
//        public final boolean jspWildCard;
        public final boolean resourceOnly;
    	
		public MappedWrapper(String name, Wrapper wrapper, boolean resourceOnly) {
			super(name, wrapper);
			this.resourceOnly = resourceOnly;
		}
    }
}
