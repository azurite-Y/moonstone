package org.zy.moonStone.core.container.valves;

import org.slf4j.Logger;
import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.container.Contained;
import org.zy.moonStone.core.interfaces.container.Container;
import org.zy.moonStone.core.interfaces.container.Valve;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 实现Valve接口的方便基类。子类必须实现invoke()方法来提供所需的功能，并且可以实现Lifecycle接口来提供配置管理和生命周期支持
 */
public abstract class ValveBase extends LifecycleBase implements Contained, Valve  {
	/**
     * 这个Valve是否支持Servlet 3+异步请求?
     */
    protected boolean asyncSupported;

    protected Container container = null;

    protected Logger containerLog = null;

    protected Valve next = null;
    
	
    public ValveBase() {
        this(false);
    }
    
    /**
     * 
     * @param asyncSupported - 这个Valve是否支持Servlet 3+异步请求?
     */
    public ValveBase(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public Container getContainer() {
        return container;
    }

    @Override
    public void setContainer(Container container) {
        this.container = container;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public Valve getNext() {
        return next;
    }

    @Override
    public void setNext(Valve valve) {
        this.next = valve;
    }


    //---------------------------------------------------------- 公共方法 ----------------------------------------------------------
    @Override
    public void backgroundProcess() {
    }

    @Override
    protected void initInternal() throws LifecycleException {
        containerLog = getContainer().getLogger();
    }

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }
    
    @Override
	protected void destroyInternal() throws LifecycleException {}
}
