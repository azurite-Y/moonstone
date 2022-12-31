package org.zy.moonStone.core.util;

import java.util.Collection;
import java.util.HashSet;

/**
 * @dateTime 2022年8月30日;
 * @author zy(azurite-Y);
 * @description 包含锁定属性的HashSet的扩展实现。此类可用于将源路径集安全地暴露给用户类，而不必克隆它们以避免修改。首次创建资源映射时，资源映射未被锁定。
 */
public class ResourceSet<T> extends HashSet<T> {
	private static final long serialVersionUID = -918350520117690175L;

    /**
     * 此资源集的当前锁定状态
     */
    private boolean locked = false;

	// ------------------------------------------------------------- 
    // 构造器
	// -------------------------------------------------------------
    /**
     * 使用默认的初始容量和负载因子构建一个新的空集
     */
    public ResourceSet() {
        super();
    }
    /**
     * 使用指定的初始容量和默认负载因子构造一个新的空集合。
     *
     * @param initialCapacity - 此集合的初始容量
     */
    public ResourceSet(int initialCapacity) {
        super(initialCapacity);
    }
    /**
     * 用指定的初始容量和负载因子构造一个新的空集合。
     *
     * @param initialCapacity - 此集合的初始容量
     * @param loadFactor - 此集合的负载因子
     */
    public ResourceSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }
    /**
     * 构造一个具有与现有集合相同内容的新集合。
     *
     * @param coll - 应该复制其内容的集合
     */
    public ResourceSet(Collection<T> coll) {
        super(coll);
    }

	// ------------------------------------------------------------- 
    // getter、setter
	// -------------------------------------------------------------
    /**
     * @return 此资源集的当前锁定状态
     */
    public boolean isLocked() {
        return this.locked;
    }
    /**
     * 设置此资源集的当前锁定状态
     *
     * @param locked - 新的锁定状态
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

	// ------------------------------------------------------------- 
    // 公共方法
	// -------------------------------------------------------------
    /**
     * 如果指定的元素不存在，则将其添加到此集合中。如果已添加元素，则返回 <code>true</code>
     *
     * @param o - 要添加的对象
     *
     * @exception IllegalStateException - 如果此资源集已锁定
     */
    @Override
    public boolean add(T o) {
    	assertLocked();
        return super.add(o);
    }

    /**
     * 从该集合中删除所有元素。
     *
     * @exception IllegalStateException - 如果此资源集已锁定
     */
    @Override
    public void clear() {
    	assertLocked();
        super.clear();
    }

    /**
     * 如果给定元素存在，则将其从该集合中移除。如果已移除该元素，则返回 <code>true</code>
     *
     * @param o - 要移除的对象
     *
     * @exception IllegalStateException - 如果此资源集已锁定
     */
    @Override
    public boolean remove(Object o) {
    	assertLocked();
        return super.remove(o);
    }
    
    private void assertLocked() {
    	 if (locked) throw new IllegalStateException("resourceSet locked");
    }
}
