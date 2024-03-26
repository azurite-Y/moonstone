package org.zy.moonstone.core.interfaces.connector;

import java.util.concurrent.TimeUnit;

import org.zy.moonstone.core.interfaces.container.Lifecycle;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description
 */
public interface Executor extends java.util.concurrent.Executor, Lifecycle {
    public String getName();

    /**
     * 在将来的某个时间执行给定的命令。该命令可以在新线程、池线程或调用线程中执行，由Executor实现决定。
     * 如果没有可用的线程，则将其添加到工作队列中。
     * 如果工作队列已满，系统将等待指定的时间，直到抛出一个RejectedExecutionException异常
     *
     * @param command - 可运行的任务
     * @param timeout - 等待任务完成的时间长度
     * @param unit - 表示超时的单位
     *
     * @throws java.util.concurrent.RejectedExecutionException - 如果此任务不能被接受执行-队列已满
     * @throws NullPointerException - 如果command或unit为空
     */
    void execute(Runnable command, long timeout, TimeUnit unit);
}
