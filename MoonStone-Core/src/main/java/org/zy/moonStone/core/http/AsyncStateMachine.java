package org.zy.moonStone.core.http;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicLong;

import org.zy.moonStone.core.Constants;
import org.zy.moonStone.core.interfaces.container.AsyncContextCallback;
import org.zy.moonStone.core.security.PrivilegedGetTccl;
import org.zy.moonStone.core.security.PrivilegedSetTccl;
import org.zy.moonStone.core.util.net.AbstractEndpoint.Handler.SocketState;
import org.zy.moonStone.core.util.net.ContainerThreadMarker;

/**
 * @dateTime 2022年12月4日;
 * @author zy(azurite-Y);
 * @description
 * 管理异步请求的状态转换。
 * <p>
 * 使用的内部状态为:
 * <pre>
 * DISPATCHED - 标准请求。不处于异步模式。
 * STARTING - 已从 Servlet.service() 调用 ServletRequest.startAsync() ，但 service() 尚未退出。
 * STARTED - 已从 Servlet.service() 调用ServletRequest.startAsync()，且 service() 已退出。
 * READ_WRITE_OP - 执行异步读或写
 * MUST_COMPLETE - 在单个 Servlet.service() 方法期间调用了 ServletRequest.startAsync() 和complete()。一旦Servlet.service()退出，就会立即处理complete()。
 * COMPLETE_PENDING - ServletRequest.startAsync()已经从Servlet.service()调用，但是在service()退出之前，从另一个线程调用complete()。一旦Servlet.service()退出，complete()就会被处理。
 * COMPLETING - 一旦请求处于STARTED状态，就会调用complete()。
 * TIMING_OUT - 异步请求已超时，正在等待调用complete()或dispatch()。如果没有这样做，则会进入错误状态。
 * MUST_DISPATCH    - 在单个 Servlet.service() 方法期间，调用了 ServletRequest.startAsync() 和 dispatch() 。一旦 Servlet.service() 退出，将立即处理 dispatch()。
 * DISPATCH_PENDING - 已从 Servlet.service() 调用 ServletRequest.startAsync() ，但在 service() 退出之前，已从另一个线程调用 dispatch() 。一旦 Servlet.service() 退出，将立即处理 dispatch()。
 * DISPATCHING - 正在处理调度
 * MUST_ERROR - 已从Servlet调用 ServletRequest.startAsync() 。service() ，但在 service() 退出之前，另一个线程上发生了I/O错误。当 Servlet.service() 退出时，容器将执行必要的错误处理。
 * ERROR - 出问题了
 *
 *
 * 有效的状态转换为:
 *
 *                  post()                                        dispatched()
 *    |-------»------------------»---------|    |-------«-----------------------«-----|
 *    |                                    |    |                                     |
 *    |                                    |    |        post()                       |
 *    |               post()              \|/  \|/       dispatched()                 |
 *    |           |-----»----------------»DISPATCHED«-------------«-------------|     |
 *    |           |                          | /|\ |                            |     |
 *    |           |              startAsync()|  |--|timeout()                   |     |
 *    ^           |                          |                                  |     |
 *    |           |        complete()        |                  dispatch()      ^     |
 *    |           |   |--«---------------«-- | ---«--MUST_ERROR--»-----|        |     |
 *    |           |   |                      |         /|\             |        |     |
 *    |           ^   |                      |          |              |        |     |
 *    |           |   |                      |    /-----|error()       |        |     |
 *    |           |   |                      |   /                     |        ^     |
 *    |           |  \|/  ST-complete()     \|/ /   ST-dispatch()     \|/       |     |
 *    |    MUST_COMPLETE«--------«--------STARTING--------»---------»MUST_DISPATCH    |
 *    |                                    / | \                                      |
 *    |                                   /  |  \                                     |
 *    |                    OT-complete() /   |   \    OT-dispatch()                   |
 *    |   COMPLETE_PENDING«------«------/    |    \-------»---------»DISPATCH_PENDING |
 *    |          |                           |                           |            |
 *    |    post()|   timeout()         post()|   post()            post()|  timeout() |
 *    |          |   |--|                    |  |--|                     |    |--|    |
 *    |         \|/ \|/ |   complete()      \|/\|/ |   dispatch()       \|/  \|/ |    |
 *    |--«-----COMPLETING«--------«----------STARTED--------»---------»DISPATCHING----|
 *            /|\  /|\ /|\                   | /|\ \                   /|\ /|\ /|\
 *             |    |   |                    |  \   \asyncOperation()   |   |   |
 *             |    |   |           timeout()|   \   \                  |   |   |
 *             |    |   |                    |    \   \                 |   |   |
 *             |    |   |                    |     \   \                |   |   |
 *             |    |   |                    |      \   \               |   |   |
 *             |    |   |                    |       \   \              |   |   |
 *             |    |   |                    |  post()\   \   dispatch()|   |   |
 *             |    |   |   complete()       |         \ \|/            |   |   |
 *             |    |   |---«------------«-- | --«---READ_WRITE----»----|   |   |
 *             |    |                        |                              |   |
 *             |    |       complete()      \|/         dispatch()          |   |
 *             |    |------------«-------TIMING_OUT--------»----------------|   |
 *             |                                                                |
 *             |            complete()                     dispatch()           |
 *             |---------------«-----------ERROR--------------»-----------------|
 *
 *
 * Notes: 
 * 		* 为清楚起见，转换到ERROR时，除了未显示STARTING（启动）。
 *     * 所有转换都可能发生在 Servlet.service() 线程（ST）或任何其他线程（OT），除非明确标记。
 * </pre>
 */
public class AsyncStateMachine {
	private volatile AsyncState state = AsyncState.DISPATCHED;
	private volatile long lastAsyncStart = 0;

	/**
	 * 跟踪此状态机当前生成的异步处理。每次启动异步处理时，生成都会递增。
	 * 这样做的主要目的是使 MoonStone 能够检测和阻止尝试使用当前一代处理上一代事件，因为处理此类事件通常结局很糟糕
	 */
	private final AtomicLong generation = new AtomicLong(0);
	
	// 需要这个来触发监听器完成
	private AsyncContextCallback asyncCtxt = null;
	private final AbstractProcessor processor;

	
	AsyncStateMachine(AbstractProcessor processor) {
		this.processor = processor;
	}

	/**
	 * 是否异步 
	 */
	boolean isAsync() {
		return state.isAsync();
	}

	/**
	 * 是否完成中 
	 */
	boolean isAsyncDispatching() {
		return state.isDispatching();
	}

	/**
	 * 是否已启动 
	 */
	boolean isAsyncStarted() {
		return state.isStarted();
	}

	/**
	 * 是否异步处理超时
	 */
	boolean isAsyncTimingOut() {
		return state == AsyncState.TIMING_OUT;
	}

	/**
	 * 异步调用是否错误
	 */
	boolean isAsyncError() {
		return state == AsyncState.ERROR;
	}

	/**
	 * 是否调度中 
	 */
	boolean isCompleting() {
		return state.isCompleting();
	}

	/**
	 * 获取此连接上次转换为异步处理的时间。
	 *
	 * @return 此连接上次转换为异步的时间（由 {@link System#currentTimeMillis()} 返回）
	 */
	long getLastAsyncStart() {
		return lastAsyncStart;
	}

	/**
	 * 获取当前阶段
	 */
	long getCurrentGeneration() {
		return generation.get();
	}

	/**
	 * 异步启动
	 * 
	 * @param asyncCtxt
	 */
	synchronized void asyncStart(AsyncContextCallback asyncCtxt) {
		if (state == AsyncState.DISPATCHED) {
			generation.incrementAndGet();
			state = AsyncState.STARTING;
			this.asyncCtxt = asyncCtxt;
			lastAsyncStart = System.currentTimeMillis();
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncStart()-无效的异步状态, by state: " + state);
		}
	}

	/**
	 * 异步操作
	 */
	synchronized void asyncOperation() {
		if (state == AsyncState.STARTED) {
			state = AsyncState.READ_WRITE_OP;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncOperation()-无效的异步状态, by state: " + state);
		}
	}

	/**
	 * Async已被处理。是否进入长轮询取决于当前状态。例如，根据SRV.2.3.3.3，现在可以处理对complete()或dispatch()的调用。
	 */
	synchronized SocketState asyncPostProcess() {
		if (state == AsyncState.COMPLETE_PENDING) {
			clearNonBlockingListeners();
			state = AsyncState.COMPLETING;
			return SocketState.ASYNC_END;
			
		} else if (state == AsyncState.DISPATCH_PENDING) {
			clearNonBlockingListeners();
			state = AsyncState.DISPATCHING;
			return SocketState.ASYNC_END;
			
		} else if (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP) {
			state = AsyncState.STARTED;
			return SocketState.LONG;
			
		} else if (state == AsyncState.MUST_COMPLETE || state == AsyncState.COMPLETING) {
			asyncCtxt.fireOnComplete();
			state = AsyncState.DISPATCHED;
			return SocketState.ASYNC_END;
			
		} else if (state == AsyncState.MUST_DISPATCH) {
			state = AsyncState.DISPATCHING;
			return SocketState.ASYNC_END;
			
		} else if (state == AsyncState.DISPATCHING) {
			state = AsyncState.DISPATCHED;
			return SocketState.ASYNC_END;
			
		} else if (state == AsyncState.STARTED) {
			// 如果异步侦听器在onTimeout期间向异步servlet进行分派，则会发生这种情况
			return SocketState.LONG;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncPostProcess()-无效的异步状态, by state: " + state);
		}
	}

	/**
	 * 异步完成
	 * 
	 * @return
	 */
	synchronized boolean asyncComplete() {
		if (!ContainerThreadMarker.isContainerThread() && state == AsyncState.STARTING) {
			state = AsyncState.COMPLETE_PENDING;
			return false;
		}

		clearNonBlockingListeners();
		// 是否触发调度
		boolean triggerDispatch = false;
		if (state == AsyncState.STARTING || state == AsyncState.MUST_ERROR) {
			// 处理是在容器线程上进行的，因此不需要将处理转移到新的容器线程
			state = AsyncState.MUST_COMPLETE;
		} else if (state == AsyncState.STARTED) {
			state = AsyncState.COMPLETING;
			/*
			 * 总是需要向容器线程分派。如果在非容器线程上，则需要返回到容器线程以完成处理。
			 * 如果在容器线程上，当前请求/响应不是与AsyncContext关联的请求/响应，那么需要一个新的容器线程来处理不同的请求/应答。
			 */
			triggerDispatch = true;
		} else if (state == AsyncState.READ_WRITE_OP || state == AsyncState.TIMING_OUT || state == AsyncState.ERROR) {
			/*
			 * 读/写操作可以在容器线程上或外发生，但在此状态下，对侦听器的调用将触发读/写，该调用将在容器线程中进行。
			 * 超时和错误的处理可能发生在容器线程上或外（更可能发生在上），但在此状态下，触发超时的调用将在容器线程中进行。
			 * 
			 * 当容器线程退出AbstractConnectionHandler.process()方法时，套接字将被添加到轮询器，因此不要在这里执行分派操作，否则会将其第二次添加到轮询器。
			 */
			state = AsyncState.COMPLETING;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncComplete()-无效的异步状态, by state: " + state);
		}
		return triggerDispatch;
	}

	/**
	 * 异步超时
	 * 
	 * @return
	 */
	synchronized boolean asyncTimeout() {
		if (state == AsyncState.STARTED) {
			state = AsyncState.TIMING_OUT;
			return true;
		} else if (state == AsyncState.COMPLETING || state == AsyncState.DISPATCHING || state == AsyncState.DISPATCHED) {
			// 应用程序在超时触发和执行到达此点之间调用了 complete() 或dispatch() 
			return false;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncTimeout()-无效的异步状态, by state: " + state);
		}
	}

	/**
	 * 异步调度
	 * 
	 * @return
	 */
	synchronized boolean asyncDispatch() {
		if (!ContainerThreadMarker.isContainerThread() && state == AsyncState.STARTING) {
			state = AsyncState.DISPATCH_PENDING;
			return false;
		}

		clearNonBlockingListeners();
		// 是否触发调度
		boolean triggerDispatch = false;
		if (state == AsyncState.STARTING || state == AsyncState.MUST_ERROR) {
			// 处理是在容器线程上进行的，因此不需要将处理转移到新的容器线程
			state = AsyncState.MUST_DISPATCH;
		} else if (state == AsyncState.STARTED) {
			state = AsyncState.DISPATCHING;
			/*
			 * 总是需要对容器线程的分派。如果在非容器线程上，则需要返回到容器线程上完成处理。
			 * 如果在容器线程中，当前的请求/响应不是与AsyncContext相关联的请求/响应，那么需要一个新的容器线程来处理不同的请求/响应。
			 */
			triggerDispatch = true;
		} else if (state == AsyncState.READ_WRITE_OP || state == AsyncState.TIMING_OUT || state == AsyncState.ERROR) {
			/*
			 * 读/写操作可以发生在容器线程上或容器线程外，但在这种状态下，对触发读/写的侦听器的调用将在容器线程上进行。
			 * 超时和错误的处理可以发生在容器线程上或容器线程外(更可能发生在容器上)，但在这种状态下，触发超时的调用将在容器线程上进行。
			 * 当容器线程退出AbstractConnectionHandler.process()方法时，套接字将被添加到轮询器，因此不要在这里执行分派操作，否则会将其第二次添加到轮询器。
			 */
			state = AsyncState.DISPATCHING;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncDispatch()-无效的异步状态, by state: " + state);
		}
		return triggerDispatch;
	}

	/**
	 * 完成异步调度
	 */
	synchronized void asyncDispatched() {
		if (state == AsyncState.DISPATCHING || state == AsyncState.MUST_DISPATCH) {
			state = AsyncState.DISPATCHED;
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncDispatched()-无效的异步状态, by state: " + state);
		}
	}

	/**
	 * 异步错误处理
	 * 
	 * @return 应用程序创建的
	 */
	synchronized boolean asyncError() {
		clearNonBlockingListeners();
		if (state == AsyncState.STARTING) {
			state = AsyncState.MUST_ERROR;
		} else {
			state = AsyncState.ERROR;
		}
		return !ContainerThreadMarker.isContainerThread();
	}

	/**
	 * 异步执行指定 {@link Runnable } 内容
	 * 
	 * @param runnable - 异步执行的 {@link Runnable } 
	 */
	synchronized void asyncRun(Runnable runnable) {
		if (state == AsyncState.STARTING || state == AsyncState.STARTED || state == AsyncState.READ_WRITE_OP) {
			// 使用连接器线程池中的容器线程执行可运行线程。使用包装器来防止内存泄漏
			ClassLoader oldCL;
			if (Constants.IS_SECURITY_ENABLED) {
				PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
				oldCL = AccessController.doPrivileged(pa);
			} else {
				oldCL = Thread.currentThread().getContextClassLoader();
			}
			try {
				if (Constants.IS_SECURITY_ENABLED) {
					PrivilegedAction<Void> pa = new PrivilegedSetTccl(this.getClass().getClassLoader());
					AccessController.doPrivileged(pa);
				} else {
					Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
				}

				processor.execute(runnable);
			} finally {
				if (Constants.IS_SECURITY_ENABLED) {
					PrivilegedAction<Void> pa = new PrivilegedSetTccl(oldCL);
					AccessController.doPrivileged(pa);
				} else {
					Thread.currentThread().setContextClassLoader(oldCL);
				}
			}
		} else {
			throw new IllegalStateException("AsyncStateMachine#asyncRun()-无效的异步状态, by state: " + state);
		}

	}

	synchronized boolean isAvailable() {
		if (asyncCtxt == null) {
			// 异步处理可能已经在另一个线程中完成。触发超时以确保处理器被清理。
			return false;
		}
		return asyncCtxt.isAvailable();
	}

	/**
	 * 回收资源
	 */
	synchronized void recycle() {
		// 使用lastAsyncStart来确定这个实例自上一次回收以来是否被使用过。如果没有，则不需要再次回收，这节省了对notifyAll()的相对昂贵的调用
		if (lastAsyncStart == 0) {
			return;
		}
		// 确保在发生错误时，任何已暂停的非容器线程都未暂停。
		notifyAll();
		asyncCtxt = null;
		state = AsyncState.DISPATCHED;
		lastAsyncStart = 0;
	}

	/**
	 * 清楚非阻塞监听器
	 */
	private void clearNonBlockingListeners() {
		processor.getRequest().setReadListener(null);
		processor.getRequest().getResponse().setWriteListener(null);
	}

	private enum AsyncState {
		/**
		 * 已调度
		 */
		DISPATCHED(false, false, false, false), 
		
		/**
		 * 启动中
		 */
		STARTING(true, true, false, false), 
		
		/**
		 * 已启动
		 */
		STARTED(true, true, false, false),
		
		/**
		 * 必须完成
		 */
		MUST_COMPLETE(true, true, true, false), 
		
		/**
		 * 等待完成
		 */
		COMPLETE_PENDING(true, true, false, false),
		
		/**
		 * 完成中
		 */
		COMPLETING(true, false, true, false), 
		
		/**
		 * 超时
		 */
		TIMING_OUT(true, true, false, false),
		
		/**
		 * 必须调度
		 */
		MUST_DISPATCH(true, true, false, true), 
		
		/**
		 * 等待调度
		 */
		DISPATCH_PENDING(true, true, false, false),
		
		/**
		 * 调度中
		 */
		DISPATCHING(true, false, false, true), 
		
		/**
		 * 读写操作
		 */
		READ_WRITE_OP(true, true, false, false),
		
		/**
		 * 必要错误
		 */
		MUST_ERROR(true, true, false, false), 
		
		/**
		 * 错误
		 */
		ERROR(true, true, false, false);

		
		/** 是否异步 */
		private final boolean isAsync;
		/** 是否已启动 */
		private final boolean isStarted;
		/** 是否完成中 */
		private final boolean isCompleting;
		/** 是否调度中 */
		private final boolean isDispatching;

		/**
		 * 实例化一个 AsyncState 对象
		 * 
		 * @param isAsync - 是否异步
		 * @param isStarted - 是否已启动
		 * @param isCompleting - 是否完成中
		 * @param isDispatching - 是否派遣中
		 */
		private AsyncState(boolean isAsync, boolean isStarted, boolean isCompleting, boolean isDispatching) {
			this.isAsync = isAsync;
			this.isStarted = isStarted;
			this.isCompleting = isCompleting;
			this.isDispatching = isDispatching;
		}

		/** 是否异步 */
		boolean isAsync() {
			return isAsync;
		}

		/** 是否已启动 */
		boolean isStarted() {
			return isStarted;
		}

		/** 是否完成中 */
		boolean isDispatching() {
			return isDispatching;
		}

		/** 是否调度中 */
		boolean isCompleting() {
			return isCompleting;
		}
	}
}
