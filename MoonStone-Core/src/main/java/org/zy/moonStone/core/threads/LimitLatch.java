package org.zy.moonStone.core.threads;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description
 * 共享锁存器允许获取锁存器的次数有限，之后所有后续获取锁存器的请求将被放置在 FIFO 队列中，直到返回其中一个共享。
 */
public class LimitLatch {
	private static final Logger logger = LoggerFactory.getLogger(LimitLatch.class);

    private final Sync sync;
    private final AtomicLong count;
    /** 限值 */
    private volatile long limit;
    /** 是否已重置 */
    private volatile boolean released = false;
	

    /**
     * 实例化具有初始限制的 LimitLatch 对象
     * @param limit - 此锁存器的最大并发获取数
     */
    public LimitLatch(long limit) {
        this.limit = limit;
        this.count = new AtomicLong(0);
        this.sync = new Sync();
    }

    /**
     * 返回锁存器的当前计数
     * @return 锁存器的当前计数
     */
    public long getCount() {
        return count.get();
    }

    /**
     * 获取当前限值
     * @return 限值
     */
    public long getLimit() {
        return limit;
    }


    /**
     * 设置新的限值。如果限值降低，则可能会在一段时间内获得比限制更多的锁存份额。 
     * 在这种情况下，将不再发行闩锁股份，直到返还足够的股份以将获得的闩锁股份数量减少到新限值以下。 
     * 如果增加限值，则当前队列中的线程可能不会被发布一个新的可用份额，直到对闩锁发出下一个请求。
     *
     * @param limit - 新的限值
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }
    
    /**
     * 如果有一个可用的共享锁存器，则获取一个共享锁存器；如果当前没有可用的共享锁存器，则等待一个共享锁存器
     * @throws InterruptedException - 如果当前线程被中断
     */
    public void countUpOrAwait() throws InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("Counting up[{}] latch=[{}]", Thread.currentThread().getName(), getCount());
        }
        /**
         * 以共享可中断模式获取一个闩锁，如果中断则中止。 首先检查中断状态，然后至少调用一次tryAcquireShared，成功时返回。 
         * 否则线程排队，可能重复阻塞和解除阻塞，调用 tryAcquireShared 直到成功或线程被中断。
         */
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 释放共享锁存器，使其可供另一个线程使用
     * @return 上一个计数器值
     */
    public long countDown() {
    	/**
    	 * 以共享模式发布。如果tryreleasshared返回true，则通过解除阻塞一个或多个线程来实现。
    	 */
        sync.releaseShared(0);
        long result = getCount();
        if (logger.isDebugEnabled()) {
            logger.debug("Counting down[{}] latch=[{}]", Thread.currentThread().getName(), result);
        }
        return result;
    }

    /**
     * 释放所有等待的线程并忽略该限值，直到 {@link #reset()} 被调用。
     * @return 如果已完成释放，则为true
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }

    /**
     * 重置锁存器并将共享获取计数器初始化为零
     * @see #releaseAll()
     */
    public void reset() {
        this.count.set(0);
        released = false;
    }

    /**
     * 如果至少有一个线程在等待获取共享锁，则返回 true，否则返回 false
     */
    public boolean hasQueuedThreads() {
    	/**
    	 * 查询是否有线程正在等待获取。 
    	 * 请注意，由于中断和超时导致的取消可能随时发生，真正的返回并不能保证任何其他线程将永远获取。
    	 * 
    	 * 在此实现中，此操作返回非恒定时间
    	 */
        return sync.hasQueuedThreads();
    }

    /**
     * 提供对等待获取此受限共享锁存器的线程列表的访问权限
     * @return 线程的集合
     */
    public Collection<Thread> getQueuedThreads() {
    	/**
    	 * 返回一个包含可能正在等待获取的线程的集合。 因为在构造这个结果时实际的线程集可能会动态变化，所以返回的集合只是一个尽力而为的估计。 
    	 * 返回集合的元素没有特定的顺序。 此方法旨在促进子类的构建，以提供更广泛的监控措施。
    	 */
        return sync.getQueuedThreads();
    }
    
    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() {
        }

        /**
         * 尝试以共享模式获取。 此方法应查询对象的状态是否允许在共享模式下获取它，如果允许则获取它。
         * 此方法始终由执行获取的线程调用。 如果此方法报告失败，则获取方法可以将线程排队。
         * 如果它尚未排队，直到它由某个其他线程的释放发出信号。
         * @param ignored - 获取参数。 此值始终是传递给获取方法的值，或者是在进入条件等待时保存的值。
         * @return 失败时的负值；如果在共享模式下采集成功，但没有后续共享模式采集成功，则为零；
         * 如果共享模式中的采集成功，且后续共享模式采集也可能成功，则为正值，在这种情况下，后续等待线程必须检查可用性。（支持三个不同的返回值使此方法能够在上下文中使用，其中获取有时仅起独占作用。）成功后，此对象已获得。
         */
        @Override
        protected int tryAcquireShared(int ignored) {
        	// 以原子方式递增当前值一
            long newCount = count.incrementAndGet();
            if (!released && newCount > limit) {
                // 超出限值，则以原子的方式将当前值减一
                count.decrementAndGet();
                return -1;
            } else {
                return 1;
            }
        }

        /**
         * 尝试设置状态以反映共享模式下的发布。
         * <p>
         * 该方法总是由执行释放的线程调用。
         */
        @Override
        protected boolean tryReleaseShared(int arg) {
            count.decrementAndGet();
            return true;
        }
    }
}
