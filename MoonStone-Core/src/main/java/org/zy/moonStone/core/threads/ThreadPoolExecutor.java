package org.zy.moonStone.core.threads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.zy.moonStone.core.Constants;
import org.zy.moonStone.core.exceptions.StopPooledThreadException;

/**
 * @dateTime 2022年1月12日;
 * @author zy(azurite-Y);
 * @description 
 * 与java.util.concurrent.ThreadPoolExecutor相同，但实现了一个更有效的 {@link #getSubmittedCount()} 方法，用于正确地处理工作队列。
 * 如果没有指定RejectedExecutionHandler，将会配置一个默认处理程序，并且这个处理程序将总是抛出一个RejectedExecutionException异常
 */
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
	/**
	 * 提交但尚未完成的任务数。
	 * 这包括队列中的任务和已经交给工作线程但后者尚未开始执行该任务的任务。
	 * 这个数字总是大于或等于{@link #getActiveCount()}。
	 */
	private final AtomicInteger submittedCount = new AtomicInteger(0);

	/**
	 * 最后一个上下文停止时间
	 */
	private final AtomicLong lastContextStoppedTime = new AtomicLong(0L);

	/**
	 * 最近一次(以毫秒为单位)线程决定终止自己以避免潜在的内存泄漏。可以用来控制线程的更新速率
	 */
	private final AtomicLong lastTimeThreadKilledItself = new AtomicLong(0L);

	/**
	 * 更新两个线程之间的延迟(毫秒)。如果是负的，不要更新线程。
	 */
	private long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;

	/**
	 * 使用给定的初始参数和默认线程工厂创建一个新的ThreadPoolExecutor
	 * @param corePoolSize				 - 线程池中的线程数，即使它们是空闲的，除非设置了allowCoreThreadTimeOut
	 * @param maximumPoolSize		 - 池中允许的最大线程数
	 * @param keepAliveTime			 - 当线程数大于内核数时，这是剩余空闲线程在终止前等待新任务的最大时间
	 * @param unit							 - keepAliveTime参数的时间单位
	 * @param workQueue				 - 在任务执行之前用来保存任务的队列。这个队列将只保存由execute方法提交的Runnable任务
	 * @param handler						 - 由于达到线程边界和队列容量而阻塞执行时要使用的处理程序
	 */
	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
		// 启动所有核心线程，使它们空闲地等待工作。这将覆盖只有在执行新任务时才启动核心线程的默认策略。
		prestartAllCoreThreads();
	}

	/**
	 * 使用给定的初始参数创建一个新的ThreadPoolExecutor
	 * @param corePoolSize				 - 线程池中的线程数，即使它们是空闲的，除非设置了allowCoreThreadTimeOut
	 * @param maximumPoolSize		 - 池中允许的最大线程数
	 * @param keepAliveTime			 - 当线程数大于内核数时，这是剩余空闲线程在终止前等待新任务的最大时间
	 * @param unit							 - keepAliveTime参数的时间单位
	 * @param workQueue				 - 在任务执行之前用来保存任务的队列。这个队列将只保存由execute方法提交的Runnable任务
	 * @param threadFactory			 - 当执行器创建一个新线程时使用的工厂
	 * @param handler						 - 由于达到线程边界和队列容量而阻塞执行时要使用的处理程序
	 * 
	 */
	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		prestartAllCoreThreads();
	}


	/**
	 * 使用给定的初始参数创建一个新的ThreadPoolExecutor
	 * @param corePoolSize				 - 线程池中的线程数，即使它们是空闲的，除非设置了allowCoreThreadTimeOut
	 * @param maximumPoolSize		 - 池中允许的最大线程数
	 * @param keepAliveTime			 - 当线程数大于内核数时，这是剩余空闲线程在终止前等待新任务的最大时间
	 * @param unit							 - keepAliveTime参数的时间单位
	 * @param workQueue				 - 在任务执行之前用来保存任务的队列。这个队列将只保存由execute方法提交的Runnable任务
	 * @param threadFactory			 - 当执行器创建一个新线程时使用的工厂
	 */
	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
		prestartAllCoreThreads();
	}

	/**
	 * 使用给定的初始参数和默认线程工厂创建一个新的ThreadPoolExecutor
	 * @param corePoolSize					 - 线程池中的线程数，即使它们是空闲的，除非设置了allowCoreThreadTimeOut
	 * @param maximumPoolSize		 - 池中允许的最大线程数
	 * @param keepAliveTime			 - 当线程数大于内核数时，这是剩余空闲线程在终止前等待新任务的最大时间
	 * @param unit							 - keepAliveTime参数的时间单位
	 * @param workQueue				 - 在任务执行之前用来保存任务的队列。这个队列将只保存由execute方法提交的Runnable任务
	 */
	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new RejectHandler());
		prestartAllCoreThreads();
	}

	public long getThreadRenewalDelay() {
		return threadRenewalDelay;
	}

	public void setThreadRenewalDelay(long threadRenewalDelay) {
		this.threadRenewalDelay = threadRenewalDelay;
	}

	/**
	 * 在完成给定Runnable的执行时调用的方法。此方法由执行任务的线程调用。
	 * 如果非null，则Throwable是导致执行突然终止的未捕获的RuntimeExceptionor错误。
	 * <p>
	 * 此实现不做任何事情，但可以在类中自定义。
	 * 注意：为了正确嵌套多个重写，子类通常应该调用super.afterExecute，在该方法的开头执行。
	 * <p>
	 * 注意：当操作显式或通过submit等方法包含在任务（如FutureTask）中时，这些任务对象捕获并维护计算异常，
	 * 因此它们不会导致abrupttermination，并且内部异常不会传递给此方法。
	 * 如果您想在此方法中捕获这两种类型的故障，则可以进一步探测此类情况，
	 * 如在这个示例子类中，如果任务已中止，则打印直接原因或底层异常：
	 *  <pre> {@code
	 * class ExtendedExecutor extends ThreadPoolExecutor {
	 *   // ...
	 *   protected void afterExecute(Runnable r, Throwable t) {
	 *     super.afterExecute(r, t);
	 *     if (t == null && r instanceof Future<?>) {
	 *       try {
	 *         Object result = ((Future<?>) r).get();
	 *       } catch (CancellationException ce) {
	 *           t = ce;
	 *       } catch (ExecutionException ee) {
	 *           t = ee.getCause();
	 *       } catch (InterruptedException ie) {
	 *           Thread.currentThread().interrupt(); // ignore/reset
	 *       }
	 *     }
	 *     if (t != null)
	 *       System.out.println(t);
	 *   }
	 * }}</pre>
	 * 
	 * @param r - 已完成的可运行程序
	 * @param t - 导致终止的异常，或执行正常完成时为null
	 */
	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		submittedCount.decrementAndGet();

		if (t == null) {
			stopCurrentThreadIfNeeded();
		}
	}

	/**
	 * 如果当前线程是在最后一次上下文停止之前启动的，则会抛出一个异常以停止当前线程。
	 */
	protected void stopCurrentThreadIfNeeded() {
		if (currentThreadShouldBeStopped()) {
			long lastTime = lastTimeThreadKilledItself.longValue();
			if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
				if (lastTimeThreadKilledItself.compareAndSet(lastTime, System.currentTimeMillis() + 1)) {
					final String msg = String.format("线程停止以避免潜在的泄漏，by name：%s", Thread.currentThread().getName());
					throw new StopPooledThreadException(msg);
				}
			}
		}
	}

	protected boolean currentThreadShouldBeStopped() {
		if (threadRenewalDelay >= 0 && Thread.currentThread() instanceof TaskThread) {
			TaskThread currentTaskThread = (TaskThread) Thread.currentThread();
			if (currentTaskThread.getCreationTime() < this.lastContextStoppedTime.longValue()) {
				return true;
			}
		}
		return false;
	}

	public int getSubmittedCount() {
		return submittedCount.get();
	}

	/**
	 * 在将来的某个时候执行给定的任务。任务可以在新线程或现有池线程中执行。
	 * 如果由于此执行器已关闭或已达到其容量而无法提交任务执行，则该任务将由当前的RejectedExecutionHandler处理。
	 * 
	 * @param command - 要执行的任务
	 * @throws RejectedExecutionException - 如果任务不能被接受执行，则由RejectedExecutionHandler自行决定
	 * @throws NullPointerException -  如果{@code command}为空
	 */
	@Override
	public void execute(Runnable command) {
		/**
		 * 在将来的某个时间执行给定的命令。该命令可以在新线程、池线程或CallingRead中执行，具体取决于执行者实现。
		 * 如果没有可用的线程，它将被添加到工作队列中。如果工作队列已满，系统将等待指定的时间，如果在此之后队列仍满，系统将抛出RejectedExecutionException。
		 */
		execute(command,0,TimeUnit.MILLISECONDS);
	}

	/**
	 * 在将来的某个时间执行给定的命令。该命令可以在新线程、池线程或调用线程中执行，由Executor实现决定。
	 * 如果没有可用的线程，则将其添加到工作队列中。如果工作队列已满，系统将等待指定的时间，如果在此之后队列仍满，系统将抛出RejectedExecutionException。
	 *
	 * @param command - 可运行的任务
	 * @param timeout - 完成任务的超时时间
	 * @param unit - 超时时间单位
	 * @throws RejectedExecutionException - 如果此任务无法接受执行-队列已满
	 * @throws NullPointerException - 如果command或unit为空
	 */
	public void execute(Runnable command, long timeout, TimeUnit unit) {
		submittedCount.incrementAndGet();
		try {
			super.execute(command);
		} catch (RejectedExecutionException rx) {
			if (super.getQueue() instanceof TaskQueue) {
				final TaskQueue queue = (TaskQueue)super.getQueue();
				try {
					// 再次尝试将此队列元素添加到队列中
					if (!queue.force(command, timeout, unit)) {
						// 等待超时后还是无法添加则抛出异常
						submittedCount.decrementAndGet();
						throw new RejectedExecutionException("线程池执行器-队列已满");
					}
				} catch (InterruptedException x) {
					submittedCount.decrementAndGet();
					throw new RejectedExecutionException(x);
				}
			} else {
				submittedCount.decrementAndGet();
				throw rx;
			}
		}
	}

	public void contextStopping() {
		this.lastContextStoppedTime.set(System.currentTimeMillis());

		// 保存当前池参数以便以后恢复它们
		int savedCorePoolSize = this.getCorePoolSize();
		/*
		 * getQueue()
		 * 返回此执行器使用的任务队列。对任务队列的访问主要用于调试和监视。此队列可能正在使用中。检索任务队列不会阻止队列中的任务执行。
		 */
		TaskQueue taskQueue = getQueue() instanceof TaskQueue ? (TaskQueue) getQueue() : null;
		if (taskQueue != null) {
			/*
			 * 很奇怪的是 threadPoolExecutor.setCorePoolSize 检查 queue.remainingCapacity()==0。 
			 * 不明白为什么，但为了达到唤醒空闲线程的预期效果，暂时伪造了这个条件。
			 */
			taskQueue.setForcedRemainingCapacity(Integer.valueOf(0));
		}

		/**
		 * 设置核心线程数。 这会覆盖构造函数中设置的任何值。
		 * 如果新值小于当前值，多余的现有线程将在下次空闲时终止。 
		 * 如果更大，则将在需要时启动新线程以执行任何排队的任务。
		 * 
		 * setCorePoolSize(0)：唤醒空闲线程
		 */
		this.setCorePoolSize(0);

		// TaskQueue.take()负责处理超时，这样就可以确保池中的所有线程都在有限的时间内被更新，比如(threadKeepAlive +最长请求时间)

		if (taskQueue != null) {
			// 恢复队列和池的状态
			taskQueue.setForcedRemainingCapacity(null);
		}
		this.setCorePoolSize(savedCorePoolSize);
	}

	private static class RejectHandler implements RejectedExecutionHandler {
		@Override
		public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor executor) {
			throw new RejectedExecutionException();
		}

	}
}
