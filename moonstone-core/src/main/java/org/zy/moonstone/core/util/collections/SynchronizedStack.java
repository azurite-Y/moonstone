package org.zy.moonstone.core.util.collections;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description 
 * 当需要创建可重用对象池而无需缩小池时，这旨在作为 java.util.concurrent.ConcurrentLinkedQueue 的（主要）无 GC 替代方案。
 * 目的是提供所需的最低限度 以最少的垃圾尽快实现功能
 * @param <T> - 由该堆栈管理的对象类型
 */
public class SynchronizedStack<T> {
	public static final int DEFAULT_SIZE = 128;
    private static final int DEFAULT_LIMIT = -1;
    
    /**
     * 队列初始容量 
     */
    private int size;
    /**
     * 队列最大容量限制
     */
    private final int limit;

    /**
     * 指向堆栈中的下一个可用对象
     */
    private int index = -1;

    private Object[] stack;


    public SynchronizedStack() {
        this(DEFAULT_SIZE, DEFAULT_LIMIT);
    }

    public SynchronizedStack(int size, int limit) {
        if (limit > -1 && size > limit) {
            this.size = limit;
        } else {
            this.size = size;
        }
        this.limit = limit;
        stack = new Object[size];
    }

    /**
     * 向队列中添加指定对象
     * @param obj
     * @return 若添加成功则返回true，反之队列已满则返回false
     */
    public synchronized boolean push(T obj) {
        index++;
        if (index == size) {
            if (limit == -1 || size < limit) {
                expand();
            } else {
                index--;
                return false;
            }
        }
        stack[index] = obj;
        return true;
    }

    /**
     * 移除队列末尾的一个对象
     * @return
     */
    @SuppressWarnings("unchecked")
    public synchronized T pop() {
        if (index == -1) {
            return null;
        }
        T result = (T) stack[index];
        stack[index--] = null;
        return result;
    }

    public synchronized void clear() {
        if (index > -1) {
            for (int i = 0; i < index + 1; i++) {
                stack[i] = null;
            }
        }
        index = -1;
    }

    /**
     * 扩容
     */
    private void expand() {
        int newSize = size * 2;
        if (limit != -1 && newSize > limit) {
            newSize = limit;
        }
        Object[] newStack = new Object[newSize];
        System.arraycopy(stack, 0, newStack, 0, size);
        // 这是通过丢弃旧数组创建垃圾的唯一点。 请注意，只有数组，而不是内容，才会变成垃圾。
        stack = newStack;
        size = newSize;
    }
}
