package org.zy.moonstone.core.connector;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.connector.Executor;
import org.zy.moonstone.core.interfaces.connector.ResizableExecutor;
import org.zy.moonstone.core.threads.TaskQueue;
import org.zy.moonstone.core.threads.TaskThreadFactory;
import org.zy.moonstone.core.threads.ThreadPoolExecutor;

/**
 * @dateTime 2022年12月6日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardThreadExecutor extends LifecycleBase implements Executor, ResizableExecutor {
	/**
     * Default thread priority
     */
    protected int threadPriority = Thread.NORM_PRIORITY;

    /**
     * Run threads in daemon or non-daemon state
     */
    protected boolean daemon = true;

    /**
     * Default name prefix for the thread name
     */
    protected String namePrefix = "tomcat-exec-";

    /**
     * max number of threads
     */
    protected int maxThreads = 200;

    /**
     * min number of threads
     */
    protected int minSpareThreads = 25;

    /**
     * idle time in milliseconds
     */
    protected int maxIdleTime = 60000;

    /**
     * The executor we use for this component
     */
    protected ThreadPoolExecutor executor = null;

    /**
     * the name of this thread pool
     */
    protected String name;

    /**
     * prestart threads?
     */
    protected boolean prestartminSpareThreads = false;

    /**
     * The maximum number of elements that can queue up before we reject them
     */
    protected int maxQueueSize = Integer.MAX_VALUE;

    /**
     * After a context is stopped, threads in the pool are renewed. To avoid
     * renewing all threads at the same time, this delay is observed between 2
     * threads being renewed.
     */
    protected long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    private TaskQueue taskqueue = null;
    // ---------------------------------------------- Constructors
    public StandardThreadExecutor() {
        //empty constructor for the digester
    }


    // ---------------------------------------------- Public Methods

    @Override
    protected void initInternal() throws LifecycleException {}


    /**
     * Start the component and implement the requirements
     * of {@link LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        taskqueue = new TaskQueue(maxQueueSize);
        TaskThreadFactory tf = new TaskThreadFactory(namePrefix,daemon,getThreadPriority());
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), maxIdleTime, TimeUnit.MILLISECONDS,taskqueue, tf);
        executor.setThreadRenewalDelay(threadRenewalDelay);
        if (prestartminSpareThreads) {
            executor.prestartAllCoreThreads();
        }
        taskqueue.setParent(executor);

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop the component and implement the requirements
     * of {@link LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = null;
        taskqueue = null;
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
    }


    @Override
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        if (executor != null) {
            executor.execute(command,timeout,unit);
        } else {
            throw new IllegalStateException("StandardThreadExecutor 未启动");
        }
    }


    @Override
    public void execute(Runnable command) {
        if (executor != null) {
            try {
                executor.execute(command);
            } catch (RejectedExecutionException rx) {
                //there could have been contention around the queue
                if (!((TaskQueue) executor.getQueue()).force(command)) {
                    throw new RejectedExecutionException("StandardThreadExecutor 队列已满");
                }
            }
        } else {
        	throw new IllegalStateException("StandardThreadExecutor 未启动");
        }
    }

    public void contextStopping() {
        if (executor != null) {
            executor.contextStopping();
        }
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public boolean isDaemon() {

        return daemon;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isPrestartminSpareThreads() {

        return prestartminSpareThreads;
    }
    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        if (executor != null) {
            executor.setKeepAliveTime(maxIdleTime, TimeUnit.MILLISECONDS);
        }
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        if (executor != null) {
            executor.setMaximumPoolSize(maxThreads);
        }
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        if (executor != null) {
            executor.setCorePoolSize(minSpareThreads);
        }
    }

    public void setPrestartminSpareThreads(boolean prestartminSpareThreads) {
        this.prestartminSpareThreads = prestartminSpareThreads;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMaxQueueSize(int size) {
        this.maxQueueSize = size;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
        if (executor != null) {
            executor.setThreadRenewalDelay(threadRenewalDelay);
        }
    }

    // Statistics from the thread pool
    @Override
    public int getActiveCount() {
        return (executor != null) ? executor.getActiveCount() : 0;
    }

    public long getCompletedTaskCount() {
        return (executor != null) ? executor.getCompletedTaskCount() : 0;
    }

    public int getCorePoolSize() {
        return (executor != null) ? executor.getCorePoolSize() : 0;
    }

    public int getLargestPoolSize() {
        return (executor != null) ? executor.getLargestPoolSize() : 0;
    }

    @Override
    public int getPoolSize() {
        return (executor != null) ? executor.getPoolSize() : 0;
    }

    public int getQueueSize() {
        return (executor != null) ? executor.getQueue().size() : -1;
    }


    @Override
    public boolean resizePool(int corePoolSize, int maximumPoolSize) {
        if (executor == null)
            return false;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaximumPoolSize(maximumPoolSize);
        return true;
    }


    @Override
    public boolean resizeQueue(int capacity) {
        return false;
    }
}
