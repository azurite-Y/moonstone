package org.zy.moonStone.core.threads;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description 用于为执行器实现创建线程的简单任务线程工厂
 */
public class TaskThreadFactory implements ThreadFactory {
	private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    /**
     * 线程名前缀
     */
    private final String namePrefix;
    private final boolean daemon;
    private final int threadPriority;

    public TaskThreadFactory(String namePrefix, boolean daemon, int priority) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        TaskThread t = new TaskThread(group, r, namePrefix + threadNumber.getAndIncrement());
        t.setDaemon(daemon);
        t.setPriority(threadPriority);
        return t;
    }
}
