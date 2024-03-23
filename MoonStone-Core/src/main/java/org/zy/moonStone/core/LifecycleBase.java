package org.zy.moonstone.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.container.Lifecycle;
import org.zy.moonstone.core.interfaces.container.LifecycleListener;
import org.zy.moonstone.core.util.ExceptionUtils;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description
 * 实现生命周期状态转换规则的生命周期接口的基本实现。{@link Lifecycle#start()} 和 {@link Lifecycle#stop()}
 */
public abstract class LifecycleBase implements Lifecycle {
	protected Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * 事件通知的已注册LifecycleListeners列表.
	 */
	private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

	/**
	 * 源组件的当前状态.
	 */
	private volatile LifecycleState state = LifecycleState.NEW;

	private boolean throwOnFailure = true;



	@Override
	public void addLifecycleListener(LifecycleListener listener) {
		lifecycleListeners.add(listener);
	}

	@Override
	public LifecycleListener[] findLifecycleListeners() {
		return lifecycleListeners.toArray(new LifecycleListener[0]);
	}

	@Override
	public void removeLifecycleListener(LifecycleListener listener) {
		lifecycleListeners.remove(listener);
	}

	@Override
	public final synchronized void init() throws LifecycleException {
		if (!state.equals(LifecycleState.NEW)) {
			invalidTransition(Lifecycle.BEFORE_INIT_EVENT);
		}

		try {
			setStateInternal(LifecycleState.INITIALIZING, null, false);
			initInternal();
			setStateInternal(LifecycleState.INITIALIZED, null, false);
		} catch (Throwable t) {
			handleSubClassException(t, format("组件初始化失败, by %s", toString()));
		}
	}

	/**
	 * 子类实现此方法来执行所需的任何实例初始化
	 * @throws LifecycleException - 如果初始化失败
	 */
	protected abstract void initInternal() throws LifecycleException;

	@Override
	public final synchronized void start() throws LifecycleException {
		if (LifecycleState.STARTING_PREP.equals(state) || LifecycleState.STARTING.equals(state) ||	LifecycleState.STARTED.equals(state)) {
			if (logger.isDebugEnabled()) {
				Exception e = new LifecycleException();
				logger.debug("当前组件已启动", e);
			} else if (logger.isInfoEnabled()) {
				logger.info("当前组件已启动");
			}
			return;
		}

		if (state.equals(LifecycleState.NEW)) {
			init();
		} else if (state.equals(LifecycleState.FAILED)) {
			stop();
		} else if (!state.equals(LifecycleState.INITIALIZED) && !state.equals(LifecycleState.STOPPED)) {
			invalidTransition(Lifecycle.BEFORE_START_EVENT);
		}

		try {
			setStateInternal(LifecycleState.STARTING_PREP, null, false);
			startInternal();
			if (state.equals(LifecycleState.FAILED)) {
				// 这是一个“受控”故障。组件将自身置于失败状态，因此调用stop()以完成清理.
				stop();
			} else if (!state.equals(LifecycleState.STARTING)) {
				// 不应该是必需的，但作为检查子类是否正在执行它们应该执行的操作.
				invalidTransition(Lifecycle.AFTER_START_EVENT);
			} else {
				setStateInternal(LifecycleState.STARTED, null, false);
			}
		} catch (Throwable t) {
			// 这是一个“非受控”故障，因此将组件置于故障状态并引发异常.
			handleSubClassException(t, format("组件启动失败，by %s", toString()));
		}
	}

	/**
	 * 子类必须确保状态被更改为 {@link LifecycleState#STARTING} 在此方法执行期间。改变状态将触发{@link Lifecycle#START_EVENT}。
	 * 如果一个组件启动失败，它可能会抛出一个{@link LifecycleException}，这将导致它的父组件启动失败，它可以将自己置于错误状态，
	 * 在这种情况下，{@link #stop()}将在失败的组件上调用，但父组件将继续正常启动
	 * @throws LifecycleException - 如果此组件检测到阻止使用此组件的致命错误
	 */
	protected abstract void startInternal() throws LifecycleException;


	@Override
	public final synchronized void stop() throws LifecycleException {

		if (LifecycleState.STOPPING_PREP.equals(state) || LifecycleState.STOPPING.equals(state) || LifecycleState.STOPPED.equals(state)) {
			if (logger.isDebugEnabled()) {
				Exception e = new LifecycleException();
				logger.debug("当前组件已停止", e);
			} else if (logger.isInfoEnabled()) {
				logger.debug("当前组件已停止");
			}
			return;
		}

		if (state.equals(LifecycleState.NEW)) {
			state = LifecycleState.STOPPED;
			return;
		}

		if (!state.equals(LifecycleState.STARTED) && !state.equals(LifecycleState.FAILED)) {
			invalidTransition(Lifecycle.BEFORE_STOP_EVENT);
		}

		try {
			if (state.equals(LifecycleState.FAILED)) {
				// 不要过渡到STOP _PREP，因为这会短暂地将组件标记为可用，但要确保触发BEFORE_STOP_EVENT
				fireLifecycleEvent(BEFORE_STOP_EVENT, null);
			} else {
				setStateInternal(LifecycleState.STOPPING_PREP, null, false);
			}

			stopInternal();

			// 不应该是必需的，但作为检查子类是否正在执行它们应该执行的操作.
			if (!state.equals(LifecycleState.STOPPING) && !state.equals(LifecycleState.FAILED)) {
				invalidTransition(Lifecycle.AFTER_STOP_EVENT);
			}

			setStateInternal(LifecycleState.STOPPED, null, false);
		} catch (Throwable t) {
			handleSubClassException(t, format("组件停止失败，by %s", toString()));
		} finally {
			if (this instanceof Lifecycle.SingleUse) {
				// 首先完成停止过程
				setStateInternal(LifecycleState.STOPPED, null, false);
				destroy();
			}
		}
	}

	/**
	 * 子类必须确保状态被更改为LifecycleState.STOPPING在这个方法的执行过程中。
	 * 状态的改变将触发Lifecycle.STOP_EVENT事件.
	 *
	 * @throws LifecycleException - 如果此组件检测到阻止使用此组件的致命错误
	 */
	protected abstract void stopInternal() throws LifecycleException;

	@Override
	public final synchronized void destroy() throws LifecycleException {
		if (LifecycleState.FAILED.equals(state)) {
			try {
				// 触发清理
				stop();
			} catch (LifecycleException e) {
				logger.error("停止失败.", e);
			}
		}

		if (LifecycleState.DESTROYING.equals(state) || LifecycleState.DESTROYED.equals(state)) {
			if (logger.isDebugEnabled()) {
				Exception e = new LifecycleException();
				logger.debug("当前组件已销毁", e);
			} else if (logger.isInfoEnabled() && !(this instanceof Lifecycle.SingleUse)) {
				logger.info("当前组件已销毁，by {}", toString());
			}
			return;
		}

		if (!state.equals(LifecycleState.STOPPED) && !state.equals(LifecycleState.FAILED) && !state.equals(LifecycleState.NEW) && !state.equals(LifecycleState.INITIALIZED)) {
			invalidTransition(Lifecycle.BEFORE_DESTROY_EVENT);
		}

		try {
			setStateInternal(LifecycleState.DESTROYING, null, false);
			destroyInternal();
			setStateInternal(LifecycleState.DESTROYED, null, false);
		} catch (Throwable t) {
			handleSubClassException(t, format("组件销毁失败, by %s", toString()));
		}
	}

	/**
	 * 子类实现此方法来执行所需的任何实例销毁.
	 *
	 * @throws LifecycleException - 如果销毁失败
	 */
	protected abstract void destroyInternal() throws LifecycleException;

	
	// ------------------------------------------------------------- getter、setter -------------------------------------------------------------
	@Override
	public LifecycleState getState() {
		return state;
	}

	@Override
	public String getStateName() {
		return getState().toString();
	}

	/**
	 * 为子类提供更新组件状态的机制。调用此方法将自动触发任何相关的生命周期事件。它还将检查任何尝试的状态转换对子类是否有效.
	 *
	 * @param state - 此组件的新状态
	 * @throws LifecycleException - 当试图设置无效状态时
	 */
	protected synchronized void setState(LifecycleState state) throws LifecycleException {
		setStateInternal(state, null, true);
	}

	/**
	 * 为子类提供更新组件状态的机制。调用此方法将自动触发任何相关的生命周期事件。它还将检查任何尝试的状态转换对子类是否有效.
	 *
	 * @param state - 此组件的新状态
	 * @param data - 传递给关联生命周期事件的数据
	 * @throws LifecycleException - 当试图设置无效状态时
	 */
	protected synchronized void setState(LifecycleState state, Object data) throws LifecycleException {
		setStateInternal(state, data, true);
	}

	private synchronized void setStateInternal(LifecycleState state, Object data, boolean check) throws LifecycleException {
//		if (logger.isDebugEnabled()) {
//			logger.debug("生命周期状态变更: {}-->{}", this.state, state);
//		}

		if (check) {
			if (state == null) {
				invalidTransition("state设置为null");
				return;
			}

			if (!(state == LifecycleState.FAILED
					|| (this.state == LifecycleState.STARTING_PREP  && state == LifecycleState.STARTING)
					|| (this.state == LifecycleState.STOPPING_PREP  && state == LifecycleState.STOPPING)
					|| (this.state == LifecycleState.FAILED && state == LifecycleState.STOPPING))) {
				invalidTransition(state.name());
			}
		}

		this.state = state;
		String lifecycleEvent = state.getLifecycleEvent();
		if (lifecycleEvent != null) {
			// 发布生命周期事件
			fireLifecycleEvent(lifecycleEvent, data);
		}
	}

	/**
	 * 允许子类触发生命周期事件.
	 *
	 * @param type - 事件类型
	 * @param data - 与事件相关的数据
	 */
	protected void fireLifecycleEvent(String type, Object data) {
		LifecycleEvent event = new LifecycleEvent(this, type, data);
		for (LifecycleListener listener : lifecycleListeners) {
			listener.lifecycleEvent(event);
		}
	}

	private void invalidTransition(String type) throws LifecycleException {
		throw new LifecycleException(String.format("%s 无效的生命周期过度, %s --> %s", this, type, state))  ;
	}

	private void handleSubClassException(Throwable t, String msg) throws LifecycleException {
		ExceptionUtils.handleThrowable(t);
		setStateInternal(LifecycleState.FAILED, null, false);
		if (getThrowOnFailure()) {
			if (!(t instanceof LifecycleException)) {
				t = new LifecycleException(msg, t);
			}
			throw (LifecycleException) t;
		} else {
			logger.error(msg, t);
		}
	}
	
	private String format(String format, Object... args) {
		return String.format(format, args);
	}

	/**
	 * 在 {@link #initInternal()}, {@link #startInternal()}, {@link LifecycleException}  {@link #stopInternal()} 或 {@link #destroyInternal()} 期间子类抛出的LifecycleException将被调用者重新抛出处理，还是将被记录?
	 *
	 * @return 如果异常将被重新抛出，则为True，否则为false
	 */
	public boolean getThrowOnFailure() {
		return throwOnFailure;
	}


	/**
	 * 配置一个子类在 {@link #initInternal()}, {@link #startInternal()}, {@link LifecycleException}  {@link #stopInternal()} 或 {@link #destroyInternal()} 期间抛出的LifecycleException是否会被重新抛出，以便调用者处理，或者是否会被记录.
	 * 默认值为true.
	 *
	 * @param throwOnFailure - 如果应该重新抛出异常，则为true，否则为false
	 */
	public void setThrowOnFailure(boolean throwOnFailure) {
		this.throwOnFailure = throwOnFailure;
	}
}
