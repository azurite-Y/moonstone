package org.zy.moonstone.core.interfaces.connector;

import java.util.concurrent.Executor;

/**
 * @dateTime 2022年12月6日;
 * @author zy(azurite-Y);
 * @description 可调整大小的执行器
 */
public interface ResizableExecutor extends Executor {
	/**
     * 返回池中当前线程数
     *
     * @return 线程数
     */
    public int getPoolSize();

    public int getMaxThreads();

    /**
     * 返回 actively 中正在执行任务线程的大致数量
     *
     * @return 线程数
     */
    public int getActiveCount();

    public boolean resizePool(int corePoolSize, int maximumPoolSize);

    public boolean resizeQueue(int capacity);
}
