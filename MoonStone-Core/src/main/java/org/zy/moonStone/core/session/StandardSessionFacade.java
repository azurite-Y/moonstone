package org.zy.moonStone.core.session;

import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

/**
 * @dateTime 2022年8月12日;
 * @author zy(azurite-Y);
 * @description StandardSession 对象的外观
 */
public class StandardSessionFacade implements HttpSession {
    /**
     * 构造一个新的会话 facade
     *
     * @param session - 要包装的会话实例
     */
    public StandardSessionFacade(HttpSession session) {
        this.session = session;
    }

    /**
     * 包装的会话对象
     */
    private final HttpSession session;

    @Override
    public long getCreationTime() {
        return session.getCreationTime();
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public long getLastAccessedTime() {
        return session.getLastAccessedTime();
    }

    @Override
    public ServletContext getServletContext() {
        return session.getServletContext();
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        session.setMaxInactiveInterval(interval);
    }

    @Override
    public int getMaxInactiveInterval() {
        return session.getMaxInactiveInterval();
    }

    @Override
    @Deprecated
    public javax.servlet.http.HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return session.getAttribute(name);
    }

    /**
     * @deprecated - 从 2.2 版开始，此方法由 {@link #getAttribute} 替换
     */
    @Override
    @Deprecated
    public Object getValue(String name) {
        return session.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return session.getAttributeNames();
    }

    /**
     * @deprecated - 从 2.2 版开始，此方法由 {@link #getAttributeNames} 替换
     */
    @Override
    @Deprecated
    public String[] getValueNames() {
        return session.getValueNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
        session.setAttribute(name, value);
    }

    /**
     * @deprecated - 从 2.2 版开始，此方法由 {@link #setAttribute} 替换
     */
    @Override
    @Deprecated
    public void putValue(String name, Object value) {
        session.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        session.removeAttribute(name);
    }

    /**
     * @deprecated - 从 2.2 版开始，此方法由 {@link #removeAttribute} 替换
     */
    @Override
    @Deprecated
    public void removeValue(String name) {
        session.removeAttribute(name);
    }

    @Override
    public void invalidate() {
        session.invalidate();
    }

    @Override
    public boolean isNew() {
        return session.isNew();
    }
}
