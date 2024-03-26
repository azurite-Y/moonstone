package org.zy.moonstone.core.threads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.exceptions.StopPooledThreadException;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description 一个线程实现，记录它被创建的时间
 */
public class TaskThread extends Thread {
	private static final Logger logger = LoggerFactory.getLogger(TaskThread.class);
	private final long creationTime;

	public TaskThread(ThreadGroup group, Runnable target, String name) {
		super(group, new WrappingRunnable(target), name);
		this.creationTime = System.currentTimeMillis();
	}

	/**
	 * @return 线程被创建的时间(以毫秒为单位)
	 */
	public final long getCreationTime() {
		return creationTime;
	}

	/**
	 * 包装一个{@link Runnable}以抑制任何{@link StopPooledThreadException}而不是抛出它，并可能在调试器中触发中断
	 */
	private static class WrappingRunnable implements Runnable {
		private Runnable wrappedRunnable;
		
		WrappingRunnable(Runnable wrappedRunnable) {
			this.wrappedRunnable = wrappedRunnable;
		}
		@Override
		public void run() {
			try {
				wrappedRunnable.run();
			} catch(StopPooledThreadException exc) {
				// 预期的:我们只是抑制这个异常，以避免干扰调试器，比如eclipse的
				logger.debug("线程故意退出", exc);
			}
		}

	}
}
