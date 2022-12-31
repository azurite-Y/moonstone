package org.zy.moonStone.core.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.container.Contained;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Lifecycle;
import org.zy.moonStone.core.interfaces.container.Pipeline;
import org.zy.moonStone.core.interfaces.container.Valve;
import org.zy.moonStone.core.util.ExceptionUtils;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardPipeline extends LifecycleBase implements Pipeline {
	/**
	 * 与此管道相关联的基本Valve(如果有的话).
	 */
	protected Valve basic = null;

	/**
	 * 与此管道相关联的容器
	 */
	protected Container container = null;

	/**
	 * 与这个管道相关联的第一个Valve.
	 */
	protected Valve first = null;

	/**
	 * 构造一个新的没有关联容器的StandardPipeline实例.
	 */
	public StandardPipeline() {
		this(null);
	}

	/**
	 * 构造一个新的StandardPipeline实例，它与指定的容器相关联.
	 *
	 * @param container - 应该与之关联的容器
	 */
	public StandardPipeline(Container container) {
		super();
		setContainer(container);
	}

	// --------------------------------------------------------- 公共方法 ---------------------------------------------------------
	@Override
	public Container getContainer() {
		return this.container;
	}

	@Override
	public void setContainer(Container container) {
		this.container = container;
	}

	@Override
	public Valve getBasic() {
		return this.basic;
	}

	@Override
	public void setBasic(Valve valve) {
		if (valve == null) return;

		Valve oldBasic = this.basic;
		if (oldBasic == valve) return;

		// 如有必要，停止旧组件
		if (oldBasic != null) {
			if (getState().isAvailable() && (oldBasic instanceof Lifecycle)) {
				try {
					((Lifecycle) oldBasic).stop();
				} catch (LifecycleException e) {
					logger.error("旧基本Valve停止异常", e);
				}
			}
			if (oldBasic instanceof Contained) {
				try {
					((Contained) oldBasic).setContainer(null);
				} catch (Throwable t) {
					ExceptionUtils.handleThrowable(t);
				}
			}
		}

		// 必要时启动新组件
		if (valve instanceof Contained) {
			((Contained) valve).setContainer(this.container);
		}
		if (getState().isAvailable() && valve instanceof Lifecycle) {
			try {
				((Lifecycle) valve).start();
			} catch (LifecycleException e) {
				logger.error("替换的基本Valve启动异常", e);
				return;
			}
		}

		// 更新管道
		Valve current = first;
		while (current != null) {
			if (current.getNext() == oldBasic) {
				current.setNext(valve);
				break;
			}
			current = current.getNext();
		}
		this.basic = valve;
	}

	@Override
	public void addValve(Valve valve) {
		// 验证我们是否可以添加这个Valve
		if (valve instanceof Contained) ((Contained) valve).setContainer(this.container);

		// 必要时启动新组件
		if (getState().isAvailable()) {
			if (valve instanceof Lifecycle) {
				try {
					((Lifecycle) valve).start();
				} catch (LifecycleException e) {
					logger.error("启动新组件异常", e);
				}
			}
		}

		// 将此Valve添加到与此管道相关联的集合中
		if (first == null) {
			first = valve;
			valve.setNext(basic);
		} else {
			Valve current = first;
			// 添加到末尾
			while (current != null) {
				if (current.getNext() == basic) {
					current.setNext(valve);
					valve.setNext(basic);
					break;
				}
				current = current.getNext();
			}
		}

		container.fireContainerEvent(Container.ADD_VALVE_EVENT, valve);
	}

	@Override
	public Valve[] getValves() {
		List<Valve> valveList = new ArrayList<>();
		Valve current = first;
		if (current == null) {
			current = basic;
		}
		while (current != null) {
			valveList.add(current);
			current = current.getNext();
		}

		return valveList.toArray(new Valve[0]);
	}

	@Override
	public void removeValve(Valve valve) {
		Valve current;
		if(first == valve) { // 删除第一个Valve则将下一Valve作为第一个Valve
			first = first.getNext();
			current = null;
		} else {
			current = first;
		}
		while (current != null) {
			// 若删除Valve链式中的任意一个Valve，则将上一Value的next指定为当前Valve的下一Value（断键相连）
			if (current.getNext() == valve) {
				current.setNext(valve.getNext());
				break;
			}
			current = current.getNext();
		}

		if (first == basic) first = null;

		// 与容器解除关联
		if (valve instanceof Contained) ((Contained) valve).setContainer(null);

		if (valve instanceof Lifecycle) {
			// 必要时关闭Valve
			if (getState().isAvailable()) {
				try {
					((Lifecycle) valve).stop();
				} catch (LifecycleException e) {
					logger.error("停止删除的Valve异常", e);
				}
			}
			try {
				((Lifecycle) valve).destroy();
			} catch (LifecycleException e) {
				logger.error("销毁删除的Valve异常", e);
			}
		}

		container.fireContainerEvent(Container.REMOVE_VALVE_EVENT, valve);
	}

	@Override
	public Valve getFirst() {
		if (first != null) {
			return first;
		}
		return basic;
	}

	@Override
	public void findNonAsyncValves(Set<String> result) {
		Valve valve = (first!=null) ? first : basic;
		while (valve != null) {
			if (!valve.isAsyncSupported()) {
				result.add(valve.getClass().getName());
			}
			valve = valve.getNext();
		}
	}

	@Override
	public boolean isAsyncSupported() {
		Valve valve = (first!=null)?first:basic;
		boolean supported = true;
		while (supported && valve!=null) {
			supported = supported & valve.isAsyncSupported();
			valve = valve.getNext();
		}
		return supported;		
	}

	@Override
	protected void initInternal() throws LifecycleException {

	}

	@Override
	protected void startInternal() throws LifecycleException {
		// 启动管道中的Valve(包括基本的)，如果有的话
		Valve current = first;
		if (current == null) {
			current = basic;
		}
		while (current != null) {
			if (current instanceof Lifecycle) ((Lifecycle) current).start();
			current = current.getNext();
		}

		setState(LifecycleState.STARTING);		
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		setState(LifecycleState.STOPPING);

		// 停止我们管道中的Valve(包括基本的)，如果有的话
		Valve current = first;
		if (current == null) {
			current = basic;
		}
		while (current != null) {
			if (current instanceof Lifecycle)
				((Lifecycle) current).stop();
			current = current.getNext();
		}		
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		Valve[] valves = getValves();
		for (Valve valve : valves) {
			removeValve(valve);
		}
	}

}
