package org.zy.moonStone.core.threads;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @dateTime 2022年1月12日;
 * @author zy(azurite-Y);
 * @description
 * 作为专门为与线程池执行程序一起运行而设计的任务队列。
 * 任务队列经过优化，以正确利用线程池执行器中的线程。
 * 如果使用普通队列，执行器会在有空闲线程的时候产生线程，你不能强制添加队列元素到队列本身。
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {
	private static final long serialVersionUID = 912321662268269042L;

	private transient volatile ThreadPoolExecutor parent = null;

	/** 强制追加的队列元素剩余量 */
	private Integer forcedRemainingCapacity = null;

	public TaskQueue() {
		super();
	}
	public TaskQueue(int capacity) {
		super(capacity);
	}
	public TaskQueue(Collection<? extends Runnable> c) {
		super(c);
	}
	public void setParent(ThreadPoolExecutor tp) {
		parent = tp;
	}

	/**
	 * 将队列元素强制放到队列中，在任务被拒绝时使用。<br/>
	 * 如果可以的话，在不超过队列容量的情况下，将指定的元素插入到队列尾部，成功时返回true，如果队列已满则返回false。<br/>
	 * 当使用容量受限的队列时，这个方法通常比add方法更可取，因为add方法只能通过抛出异常来表示插入元素失败。
	 * 
	 * @param o - 添加的元素
	 * @return 如果元素已添加到此队列，则为true，否则为false
	 */
	public boolean force(Runnable o) {
		if (parent==null || parent.isShutdown()) throw new RejectedExecutionException("任务队列未运行");
		return super.offer(o);
	}

	/**
	 * 将指定的元素插入到队列的尾部，如果有必要，将等待指定的等待时间以获得可用的空间。
	 * @param o - 添加的元素
	 * @param timeout - 强制放入的超时时间
	 * @param unit - 超时时间单位
	 * @return 如果成功，则为true；若强制放入超时则返回false
	 * @throws InterruptedException - 如果在等待时被中断
	 */
	public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
		if ( parent==null || parent.isShutdown() ) throw new RejectedExecutionException("任务队列未运行");
		return super.offer(o,timeout,unit);
	}

	/**
	 * 将队列元素强制放到队列中，在任务被拒绝时使用。<br/>
	 * 如果可以的话，在不超过队列容量的情况下，将指定的元素插入到队列尾部，成功时返回true，如果队列已满则返回false。<br/>
	 * 当使用容量受限的队列时，这个方法通常比add方法更可取，因为add方法只能通过抛出异常来表示插入元素失败。
	 * 
	 * @param o - 添加的元素
	 * @return 如果元素已添加到此队列，则为true，否则为false
	 */
	@Override
	public boolean offer(Runnable o) {
		if (parent==null) return super.offer(o);

		// 在线程上达到了上限，只需将对象排队
		if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);

		// 有空闲线程则将它添加到队列中
		if (parent.getSubmittedCount()<=(parent.getPoolSize())) return super.offer(o);

		// 如果当前线程池中线程数小于线程池最大线程数
		if (parent.getPoolSize()<parent.getMaximumPoolSize()) return false;

		return super.offer(o);
	}


	/**
	 * 检索并删除此队列的头，如果需要此元素变为可用，则等待指定等待时间
	 * 
	 * @param timeout - 等待超时时间
	 * @param unit - 超时时间参数的时间单位
	 * @return 此队列的头元素，如果在元素可用之前经过了指定的等待时间，则返回 null
	 * @throws InterruptedException - 如果在等待时被打断
	 */
	@Override
	public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
		Runnable runnable = super.poll(timeout, unit);
		if (runnable == null && parent != null) {
			// 轮询超时，如果需要，它可以停止当前线程以避免内存泄漏
			parent.stopCurrentThreadIfNeeded();
		}
		return runnable;
	}

	/**
	 * 检索并移除此队列的头部，如有必要，等待直到元素可用
	 * @return 队列的头元素
	 * @throws InterruptedException - 如果在等待时被打断
	 */
	@Override
	public Runnable take() throws InterruptedException {
		if (parent != null && parent.currentThreadShouldBeStopped()) {
			/*
			 * getKeepAliveTime()
			 * 返回线程保持活动时间，这是超过核心池大小的线程在被终止之前可能处于空闲状态的时间量 
			 */
			return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
			// 可能会返回null(在超时的情况下)，这通常不会发生在take()，但ThreadPoolExecutor实现允许这样做
		}
		return super.take();
	}

	/**
	 * 返回该队列理想情况下(在没有内存或资源限制的情况下)可以接受且不阻塞的额外元素的数量。<br/>
	 * 它总是等于这个无队列的初始容量，也就是这个队列的当前大小。
	 * <p>
	 * 请注意，您不能总是通过检查剩余容量来判断插入元素的尝试是否会成功，因为可能是另一个线程即将插入或删除元素的情况。
	 * 
	 * @return 剩余容量 
	 */
	@Override
	public int remainingCapacity() {
		if (forcedRemainingCapacity != null) {
			// 转换为int类型后由该对象表示的数值
			return forcedRemainingCapacity.intValue();
		}
		return super.remainingCapacity();
	}

	/**
	 * 设置强制追加的队列元素剩余量
	 * @param forcedRemainingCapacity - 新的强制追加的队列元素剩余量
	 */
	public void setForcedRemainingCapacity(Integer forcedRemainingCapacity) {
		this.forcedRemainingCapacity = forcedRemainingCapacity;
	}
}
