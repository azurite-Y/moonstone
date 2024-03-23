package org.zy.moonstone.core.util;

import java.io.Serializable;
import java.util.*;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description
 * 包含锁定属性的 java.util.Map 的实现。 此类可用于安全地将 Moon 内部参数映射对象公开给用户类，而无需克隆它们以避免修改。 
 * 首次创建时，ParmaeterMap 实例未锁定。
 */
public final class ParameterMap<K,V> implements Map<K,V>, Serializable {
	private static final long serialVersionUID = -6288022046256108360L;

	private final Map<K,V> delegatedMap;
	
	/** 不可变Map */
	private final Map<K,V> unmodifiableDelegatedMap;

	/** 此参数映射的当前锁定状态 */
	private boolean locked = false;
	
	/**
	 * 使用默认的初始容量和负载因子构造一个新的Map
	 */
	public ParameterMap() {
		delegatedMap = new LinkedHashMap<>();
		unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
	}

	/**
	 * 使用指定的初始容量和默认负载因子构造一个新的空Map
	 *
	 * @param initialCapacity - map的初始容量
	 */
	public ParameterMap(int initialCapacity) {
		delegatedMap = new LinkedHashMap<>(initialCapacity);
		unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
	}

	/**
	 * 使用指定的初始容量和负载因子构造一个新的空Map
	 *
	 * @param initialCapacity - Map的初始容量
	 * @param loadFactor - Map的负载因子
	 */
	public ParameterMap(int initialCapacity, float loadFactor) {
		delegatedMap = new LinkedHashMap<>(initialCapacity, loadFactor);
		unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
	}

	public ParameterMap(Map<K,V> map) {
		delegatedMap = new LinkedHashMap<>(map);
		unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
	}

	/**
	 * @return 此参数映射的锁定状态
	 */
	public boolean isLocked() {
		return locked;
	}

	/**
	 * 设置此参数映射的锁定状态
	 * @param locked - 新的锁定状态
	 */
	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Override
	public void clear() {
		checkLocked();
		delegatedMap.clear();
	}

	@Override
	public V put(K key, V value) {
		checkLocked();
		return delegatedMap.put(key, value);
	}

	@Override
	public void putAll(Map<? extends K,? extends V> map) {
		checkLocked();
		delegatedMap.putAll(map);
	}

	@Override
	public V remove(Object key) {
		checkLocked();
		return delegatedMap.remove(key);
	}

	private void checkLocked() {
		if (locked) {
			throw new IllegalStateException("当前 parameterMap 已锁定");
		}
	}

	@Override
	public int size() {
		return delegatedMap.size();
	}

	@Override
	public boolean isEmpty() {
		return delegatedMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return delegatedMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return delegatedMap.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return delegatedMap.get(key);
	}

	@Override
	public Set<K> keySet() {
		if (locked) {
			return unmodifiableDelegatedMap.keySet();
		}

		return delegatedMap.keySet();
	}

	@Override
	public Collection<V> values() {
		if (locked) {
			return unmodifiableDelegatedMap.values();
		}

		return delegatedMap.values();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		if (locked) {
			return unmodifiableDelegatedMap.entrySet();
		}

		return delegatedMap.entrySet();
	}
}
