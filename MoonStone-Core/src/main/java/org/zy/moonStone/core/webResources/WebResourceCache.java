package org.zy.moonStone.core.webResources;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;

/**
 * @dateTime 2022年9月16日;
 * @author zy(azurite-Y);
 * @description 此类旨在包装“原始”WebResource 并为昂贵的操作提供缓存。 廉价的操作可能会传递到底层资源。
 */
public class WebResourceCache {
    private static final Logger logger = LoggerFactory.getLogger(WebResourceCache.class);

    private static final long TARGET_FREE_PERCENT_GET = 5;
    private static final long TARGET_FREE_PERCENT_BACKGROUND = 10;

    // objectMaxSize 必须 < maxSize/20
    private static final int OBJECT_MAX_SIZE_FACTOR = 20;

    /** 关联的 {@link WebResourceRoot} */
    private final StandardRoot root;
    
    /** 缓存资源文件的总长度 */
    private final AtomicLong size = new AtomicLong(0);
    /** 缓存资源的最大文件长度数 */
    private long maxSize = 10 * 1024 * 1024;
	/** 单个资源对象最大字节数 */
    private int objectMaxSize = (int) maxSize/OBJECT_MAX_SIZE_FACTOR;
    
    /** 缓存资源的存活时间 */
    private long ttl = 5000;

    /** 缓存查找计数 */
    private AtomicLong lookupCount = new AtomicLong(0);
    /** 缓存命中计数 */
    private AtomicLong hitCount = new AtomicLong(0);

    /** WebResource 缓存 */
    private final ConcurrentMap<String,CachedResource> resourceCache = new ConcurrentHashMap<>();

    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    public WebResourceCache(StandardRoot root) {
        this.root = root;
    }
    
    
	// -------------------------------------------------------------------------------------
	// 缓存操作
	// -------------------------------------------------------------------------------------
    /**
     * 获取指定路径的 {@link WebResource } 实例
     * 
     * @param path - web 应用程序根目录的相对路径
     * @param useClassLoaderResources - 是否应仅用于类加载器资源查找
     * @return 指定路径的 {@link WebResource } 实例
     */
    protected WebResource getResource(String path, boolean useClassLoaderResources) {
        if (noCache(path)) {
            return root.getResourceInternal(path, useClassLoaderResources);
        }

    	// 查找计数
        lookupCount.incrementAndGet();

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResource(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // 本地复制，确保一致性
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            // WebResource 稍后从 WebResourceRoot 中获取，by CachedResource.validateResource(boolean)
            CachedResource newCacheEntry = new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes, useClassLoaderResources);

            // 并发调用方将以相同的CachedResource实例结束，返回与指定键关联的前一个值，如果该键没有映射，则返回 null。
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // 新CacheEntry已插入到缓存中-验证它
                cacheEntry = newCacheEntry;
                cacheEntry.validateResource(useClassLoaderResources);

                // 即使资源内容大于objectMaxSizeBytes，缓存资源元数据仍有好处
                long delta = cacheEntry.getSize();
                size.addAndGet(delta);

                if (size.get() > maxSize) {
                    // 无序地处理资源以提高速度。交换缓存效率(较年轻的条目可能会先于较老的条目被删除)以换取速度，因为这是请求处理的关键路径
                    long targetSize = maxSize * (100 - TARGET_FREE_PERCENT_GET) / 100;
                    long newSize = evict(targetSize, resourceCache.values().iterator());
                    if (newSize > maxSize) {
                        // 无法为此资源创建足够的空间，将其从缓存中删除
                        removeCacheEntry(path);
                        logger.warn("资源缓存失败，精简缓存后仍无足够的空间容纳此缓存, 将其移除. by path: {}, context: {}", path, root.getContext().getName());
                    }
                }
            } else {
                // 另一个线程已将该条目添加到缓存中，需确保已验证该条目
                cacheEntry.validateResource(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry;
    }

    protected WebResource[] getResources(String path, boolean useClassLoaderResources) {
        lookupCount.incrementAndGet();

        // 不要调用 noCache(path) 因为类加载器只缓存单个资源。 因此，总是在这里缓存集合
        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResources(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // 本地复制，确保一致性
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            // WebResource 数组稍后从 WebResourceRoot 中获取，by CachedResource.validateResources(boolean)
            CachedResource newCacheEntry = new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes, useClassLoaderResources);

            // 并发调用方将以相同的 CachedResource 实例结束，返回与指定键关联的前一个值，如果该键没有映射，则返回 null。
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // 新CacheEntry已插入到缓存中-验证它
                cacheEntry = newCacheEntry;
                cacheEntry.validateResources(useClassLoaderResources);

                // 内容不会被缓存，但我们仍然需要元数据大小
                long delta = cacheEntry.getSize();
                size.addAndGet(delta);

                if (size.get() > maxSize) {
                    // 无序地处理资源以提高速度。交换缓存效率(较年轻的条目可能会先于较老的条目被删除)以换取速度，因为这是请求处理的关键路径
                    long targetSize = maxSize * (100 - TARGET_FREE_PERCENT_GET) / 100;
                    long newSize = evict(targetSize, resourceCache.values().iterator());
                    if (newSize > maxSize) {
                        // 无法为此资源创建足够的空间，将其从缓存中删除
                        removeCacheEntry(path);
                        logger.warn("资源缓存失败，精简缓存后仍无足够的空间容纳此缓存, 将其移除. by path: {}", path);
                    }
                }
            } else {
            	// 另一个线程已将该条目添加到缓存中，需确保已验证该条目
                cacheEntry.validateResources(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry.getWebResources();
    }

    /**
     * "后台周期性移除失效资源
     */
    protected void backgroundProcess() {
        // 创建所有缓存资源的有序集合，其中最近使用最少的资源优先。这是一个后台进程，因此我们可以先花时间对元素进行排序
        TreeSet<CachedResource> orderedResources = new TreeSet<>(new EvictionOrder());
        orderedResources.addAll(resourceCache.values());

        Iterator<CachedResource> iter = orderedResources.iterator();

        long targetSize = maxSize * (100 - TARGET_FREE_PERCENT_BACKGROUND) / 100;
        long newSize = evict(targetSize, iter);

        if (newSize > targetSize) {
            logger.info("后台周期性移除失效资源未达预期, 预期移除占比: {}%, 当前移除后缓存: {}M, by context: {}", Long.valueOf(TARGET_FREE_PERCENT_BACKGROUND), 
            		Long.valueOf(newSize / 1024), root.getContext().getName());
        }
    }
    
    /**
     * 验证是否已核查指定路径的 {@link WebResource } 实例
     * 
     * @param path - web 应用程序根目录的相对路径
     * @param useClassLoaderResources - 是否应仅用于类加载器资源查找
     * @return true 则代表已缓存
     */
    private boolean noCache(String path) {
    	/*
    	 * 不缓存 classs。类加载器会处理这个问题。
    	 * 不缓存 JARs。ResourceSet 会处理这个问题。
    	 */
        if ( ( path.endsWith(".class") && (path.startsWith("/WEB-INF/classes/") || path.startsWith("/WEB-INF/lib/")) )  
        		|| ( path.startsWith("/WEB-INF/lib/") && path.endsWith(".jar")) ) {
            return true;
        }
        return false;
    }

    /**
     * 移除失效资源以减小总缓存资源字节数小于指定的缓存资源字节数
     * 
     * @param targetSize - 指定的缓存资源字节数，
     * @param iter - 资源缓存的迭代器
     * @return 移除失效资源后的当前总缓存资源字节数
     */
    private long evict(long targetSize, Iterator<CachedResource> iter) {
        long now = System.currentTimeMillis();

        long newSize = size.get();

        // 若缓存数超过十分之九则检查并移除失效资源
        while (newSize > targetSize && iter.hasNext()) {
            CachedResource resource = iter.next();

            // 不要使TTL中已检查的任何内容过期
            if (resource.getNextCheck() > now) {
                continue;
            }

            // 从缓存中删除该条目
            removeCacheEntry(resource.getWebappPath());

            newSize = size.get();
        }

        return newSize;
    }

    /**
     * 移除指定路径缓存
     * @param path - 缓存关联路径
     */
    void removeCacheEntry(String path) {
        // 对于同一路径的并发调用，条目只删除一次，缓存大小只更新一次(如果需要)
        CachedResource cachedResource = resourceCache.remove(path);
        
        if (cachedResource != null) {
            long delta = cachedResource.getSize();
            size.addAndGet(-delta);
        	if (logger.isDebugEnabled()) {
        		logger.debug("Remove Invalid CachedResource. cachedResource: [{}]", path);
        	}
        }
    }

    
	// -------------------------------------------------------------------------------------
	// getter、setter 方法
	// -------------------------------------------------------------------------------------
    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getMaxSize() {
        return maxSize / 1024;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize * 1024;
    }

    public long getLookupCount() {
        return lookupCount.get();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public void setObjectMaxSize(int objectMaxSize) {
        if (objectMaxSize * 1024L > Integer.MAX_VALUE) {
            logger.warn("objectMaxSize 值过大, by objectMaxSize: {}", Integer.valueOf(objectMaxSize));
            this.objectMaxSize = Integer.MAX_VALUE;
        }
        this.objectMaxSize = objectMaxSize * 1024;
    }

    public int getObjectMaxSize() {
        return objectMaxSize / 1024;
    }

    public int getObjectMaxSizeBytes() {
        return objectMaxSize;
    }

    public long getSize() {
        return size.get() / 1024;
    }
    
    void enforceObjectMaxSizeLimit() {
        long limit = maxSize / OBJECT_MAX_SIZE_FACTOR;
        if (limit > Integer.MAX_VALUE) {
            return;
        }
        if (objectMaxSize > limit) {
            logger.warn("objectMaxSize 值过大, by objectMaxSize: {}, limit: {}", Integer.valueOf(objectMaxSize / 1024), Integer.valueOf((int)limit / 1024));
            objectMaxSize = (int) limit;
        }
    }

    public void clear() {
    	resourceCache.clear();
    	size.set(0);
    }
    
    
	// -------------------------------------------------------------------------------------
	// 内部类
	// -------------------------------------------------------------------------------------
    private static class EvictionOrder implements Comparator<CachedResource> {
        @Override
        public int compare(CachedResource cr1, CachedResource cr2) {
            long nc1 = cr1.getNextCheck();
            long nc2 = cr2.getNextCheck();

            // 最老的资源应该是第一个（所以迭代器从最老的到最年轻的）
            if (nc1 == nc2) {
                return 0;
            } else if (nc1 > nc2) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
