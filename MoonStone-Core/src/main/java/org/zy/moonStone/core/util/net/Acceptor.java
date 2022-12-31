package org.zy.moonStone.core.util.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description MoonStone 接收器，接受来自服务器套接字的下一个传入连接，如果成功则将套接字交给适当的处理器
 */
public class Acceptor<U> implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Acceptor.class);

	private static final int INITIAL_ERROR_DELAY = 50;
	private static final int MAX_ERROR_DELAY = 1600;

	private final AbstractEndpoint<?,U> endpoint;
	private String threadName;
	protected volatile AcceptorState state = AcceptorState.NEW;


	public Acceptor(AbstractEndpoint<?,U> endpoint) {
		this.endpoint = endpoint;
	}

	public final AcceptorState getState() {
		return state;
	}

	final void setThreadName(final String threadName) {
		this.threadName = threadName;
	}

	final String getThreadName() {
		return threadName;
	}

	@Override
	public void run() {
		int errorDelay = 0;

		// 循环直到我们收到关机命令
		while (endpoint.isRunning()) {
			// 如果端点暂停则循环
			while (endpoint.isPaused() && endpoint.isRunning()) {
				state = AcceptorState.PAUSED;
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// Ignore
				}
			}

			if (!endpoint.isRunning()) {
				break;
			}
			state = AcceptorState.RUNNING;

			try {
				// 如果已达到最大连接数，请稍候
				endpoint.countUpOrAwaitConnection();

				// 端点在等待锁时可能已暂停。如果是这种情况，不接受新连接
				if (endpoint.isPaused()) {
					continue;
				}

				U socket = null;
				try {
					// 接受来自服务器套接字的下一个传入连接
					socket = endpoint.serverSocketAccept();
				} catch (Exception ioe) {
					// 没有获得socket
					endpoint.countDownConnection();
					if (endpoint.isRunning()) {
						// 如有必要，引入延迟
						errorDelay = handleExceptionWithDelay(errorDelay);
						// 抛出异常让外围try-catch 处理异常
						throw ioe;
					} else {
						break;
					}
				}
				// 接受成功，重置错误延迟
				errorDelay = 0;

				// 配置 socket
				if (endpoint.isRunning() && !endpoint.isPaused()) {
					// 如果注册事件成功，setSocketOptions()将把套接字移交给适当的处理器并返回true
					if (!endpoint.setSocketOptions(socket)) {
						endpoint.closeSocket(socket);
					}
				} else {
					endpoint.destroySocket(socket);
				}
			} catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				String msg = "端节点接收失败";
				logger.error(msg, t);
			}
		}
		state = AcceptorState.ENDED;
	}

	/**
	 * 处理需要延迟以防止线程进入紧密循环的异常，该循环将消耗CPU并可能触发大量日志记录。
	 * 例如，如果达到openfiles的ulimit，就会发生这种情况。
	 *
	 * @param currentErrorDelay - 失败时应用的当前延迟
	 * @return 应用于下一次失败的延迟
	 */
	protected int handleExceptionWithDelay(int currentErrorDelay) {
		// 不延迟第一个异常
		if (currentErrorDelay > 0) {
			try {
				Thread.sleep(currentErrorDelay);
			} catch (InterruptedException e) {
				// Ignore
			}
		}

		// 在随后的异常中，在 50ms 开始延迟，延迟加倍在每个后续异常上，直到延迟达到 1.6 秒。
		if (currentErrorDelay == 0) {
			return INITIAL_ERROR_DELAY;
		} else if (currentErrorDelay < MAX_ERROR_DELAY) {
			return currentErrorDelay * 2;
		} else {
			return MAX_ERROR_DELAY;
		}
	}

	public enum AcceptorState {
		NEW, RUNNING, PAUSED, ENDED
	}
}
