package org.zy.moonstone.core.threads;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @dateTime 2022年1月1日;
 * @author zy(azurite-Y);
 * @description 
 */
public class InlineExecutorService extends AbstractExecutorService {
	private volatile boolean shutdown;
    private volatile boolean taskRunning;
    private volatile boolean terminated;

    private final Object lock = new Object();

    @Override
    public void shutdown() {
        shutdown = true;
        synchronized (lock) {
            terminated = !taskRunning;
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return null;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (lock) {
            if (terminated) {
                return true;
            }
            lock.wait(unit.toMillis(timeout));
            return terminated;
        }
    }

    @Override
    public void execute(Runnable command) {
        synchronized (lock) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            taskRunning = true;
        }
        command.run();
        synchronized (lock) {
            taskRunning = false;
            if (shutdown) {
                terminated = true;
                lock.notifyAll();
            }
        }
    }
}
