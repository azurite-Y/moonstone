package org.zy.moonstone.core.util.collections;

import java.util.Arrays;

/**
 * @dateTime 2022年5月17日;
 * @author zy(azurite-Y);
 * @description 当需要创建一个无界队列而不需要收缩队列时，这旨在作为 java.util.concurrent.ConcurrentLinkedQueue 的（主要）无 GC 替代方案。 目的是尽可能快地以最少的垃圾提供所需的最少功能。
 */
public class SynchronizedQueue<T> {
	public static final int DEFAULT_SIZE = 128;

	private Object[] queue;
	private int size;
	private int insert = 0;
	private int remove = 0;

	public SynchronizedQueue() {
		this(DEFAULT_SIZE);
	}

	public SynchronizedQueue(int initialSize) {
		queue = new Object[initialSize];
		size = initialSize;
	}

	/**
	 * 添加指定对象到队列中
	 * @param t
	 * @return
	 */
	public synchronized boolean offer(T t) {
		queue[insert++] = t;

		if (insert == size) { // 此时队列已装满，那么下一次将从首部保存
			insert = 0;
		}

		if (insert == remove) {
			expand();
		}
		return true;
	}

	/**
	 * 从队列中删除指定对象
	 * @return
	 */
	public synchronized T poll() {
		if (insert == remove) {
			// empty
			return null;
		}

		@SuppressWarnings("unchecked")
		T result = (T) queue[remove];
		queue[remove] = null;
		remove++;

		if (remove == size) { // 此时队列已清空，下一次将从首部删除
			remove = 0;
		}

		return result;
	}

	/**
	 * 重新整合队列，将未删除的数据前移
	 */
	private void expand() {
		int newSize = size * 2;
		Object[] newQueue = new Object[newSize];

		// 向 newQueue 写入 queue 写入索引之后的剩余数据
		System.arraycopy(queue, insert, newQueue, 0, size - insert);

		// 设置写入索引
		insert = size;
		remove = 0;
		queue = newQueue;
		size = newSize;
	}

	public synchronized int size() {
		int result = insert - remove;
		if (result < 0) {
			result += size;
		}
		return result;
	}

	public synchronized void clear() {
		queue = new Object[size];
		insert = 0;
		remove = 0;
	}
	
	public static void main(String[] args) {
		Object[] queue = new Object[5];
		int insert = 2;
		for (int i = 0; i < queue.length; i++) {
			 queue[i] = i;
		}
		Object[] newQueue = new Object[10];
		
		int size = queue.length;
		
		System.arraycopy(queue, insert, newQueue, 0, size - insert);
		System.out.println(Arrays.asList(queue));
		System.out.println(Arrays.asList(newQueue));
		
		System.out.println("=======================================");
		
		System.arraycopy(queue, 0, newQueue, size - insert, insert);
		System.out.println(Arrays.asList(queue));
		System.out.println(Arrays.asList(newQueue));
	}
}
