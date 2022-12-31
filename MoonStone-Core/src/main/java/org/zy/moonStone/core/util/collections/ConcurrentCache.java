package org.zy.moonStone.core.util.collections;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @dateTime 2022年9月25日;
 * @author zy(azurite-Y);
 * @description
 */
public class ConcurrentCache<K,V> {
	private final int size;

	private final Map<K,V> eden;

	private final Map<K,V> longterm;

	public ConcurrentCache(int size) {
		this.size = size;
		this.eden = new ConcurrentHashMap<>(size);
		this.longterm = new WeakHashMap<>(size);
	}

	public V get(K k) {
		V v = this.eden.get(k);
		if (v == null) {
			synchronized (longterm) {
				v = this.longterm.get(k);
			}
			if (v != null) {
				this.eden.put(k, v);
			}
		}
		return v;
	}

	public void put(K k, V v) {
		if (this.eden.size() >= size) {
			synchronized (longterm) {
				this.longterm.putAll(this.eden);
			}
			this.eden.clear();
		}
		this.eden.put(k, v);
	}
}
