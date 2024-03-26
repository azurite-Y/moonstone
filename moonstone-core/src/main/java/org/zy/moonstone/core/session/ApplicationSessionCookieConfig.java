package org.zy.moonstone.core.session;

import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.container.context.StandardContext;
import org.zy.moonstone.core.interfaces.container.Context;

import javax.servlet.SessionCookieConfig;
import javax.servlet.http.Cookie;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description
 */
public class ApplicationSessionCookieConfig implements SessionCookieConfig {
    /** 是否仅使用于http，若为true则无法通过js获取cookie */
	private boolean httpOnly;
    /** cookie 安全标记，若设置为true则此cookie仅在Https协议下传输 */
    private boolean secure;
    /** cookie的最大有效期 */
    private int maxAge = -1;
    /** 会话cookie的注释 */
    private String comment;
    /** cookie 作用域 */
    private String domain;
    /** cookie 名称 */
    private String name;
    /** cookie 使用路径 */
    private String path;
    /** 应用程序上下文 */
    private StandardContext context;

    public ApplicationSessionCookieConfig(StandardContext context) {
        this.context = context;
    }

    /**
     * 获取将分配给代表应用程序创建的任何会话cookie 的注释，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * @return 通过 setComment 设置的 cookie 注释，如果从未调用 setComment，则返回 null
     */
    @Override
    public String getComment() {
        return comment;
    }
    
    /**
     * 获取将分配给代表应用程序创建的任何会话跟踪cookies 的域，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     */
    @Override
    public String getDomain() {
        return domain;
    }

    /**
     * 获取代表应用程序创建的会话跟踪 cookie 的生命周期（以秒为单位），该应用程序由获取此 SessionCookieConfig 的 ServletContext 表示。
     * <p>
     * 默认情况下，返回 -1。
     * @return 代表应用程序创建的会话跟踪cookie 的生命周期（以秒为单位），该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示，或 -1（默认值）
     */
    @Override
    public int getMaxAge() {
        return maxAge;
    }

    /**
     * 获取将分配给代表应用程序创建的任何会话跟踪cookies 的名称，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * <p>
     * 默认情况下，JSESSIONID 将用作 cookie 名称。
     * @return 通过 setName 设置的 cookie 名称，如果从未调用过 setName，则为 null
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取将分配给代表应用程序创建的任何会话跟踪cookie 的路径，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * @return cookie 路径设置为设置路径，或 nullif 设置路径从未被调用
     */
    @Override
    public String getPath() {
        return path;
    }

    /**
     * 检查代表 ServletContext 所代表的应用程序创建的会话跟踪 cookie 是否将被标记为 HttpOnly。
     */
    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    /**
     * 检查代表 ServletContext 所代表的应用程序创建的会话跟踪 cookie 是否将被标记为安全，即使启动相应会话的请求使用纯 HTTP 而不是 HTTPS。
     * @return 如果需将ServletContext所代表的应用程序创建的会话跟踪cookie被标记为安全的，则为true，即使发起相应会话的请求使用的是纯HTTP而不是HTTPS。
     * 如果仅当发起相应会话的请求也是安全的，才将其标记为安全的，则为false
     */
    @Override
    public boolean isSecure() {
        return secure;
    }

    /**
     * 设置将分配给代表应用程序创建的任何会话跟踪cookie 的注释，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * <p>
     * 作为此调用的副作用，会话跟踪 cookie 将被标记为版本属性等于 1。
     * @param comment - 要使用的 cookie 注释
     */
    @Override
    public void setComment(String comment) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "comment", context.getPath()));
        }
        this.comment = comment;
    }

    /**
     * 设置将分配给代表应用程序创建的任何会话跟踪cookies 的域，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * @param domain - 要使用的 cookie 域
     */
    @Override
    public void setDomain(String domain) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "domain name",context.getPath()));
        }
        this.domain = domain;
    }

    /**
     * 标记或取消标记在ServletContext所代表的应用程序的行为上创建的会话跟踪cookie，该ServletContext从中获取此SessionOkieConfig，并将其作为HttpOnly。
     * <p>
     * 通过向cookie添加HttpOnly属性，cookie被标记为HttpOnly。HttpOnly Cookie不应该暴露于客户端脚本代码中，因此可能有助于缓解某些类型的跨站点脚本攻击。
     * @param httpOnly - 如果需ServletContext所代表的应用程序创建的会话跟踪cookie（从中获取此SessionOkieConfigwas）应标记为HttpOnly，则为true，否则为false
     */
    @Override
    public void setHttpOnly(boolean httpOnly) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "HttpOnly", context.getPath()));
        }
        this.httpOnly = httpOnly;
    }

    /**
     * 为代表应用程序创建的会话跟踪 cookie 设置有效期（以秒为单位），该应用程序由获取此 SessionCookieConfig 的 ServletContext 表示。
     */
    @Override
    public void setMaxAge(int maxAge) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "max age", context.getPath()));
        }
        this.maxAge = maxAge;
    }

    /**
     * 设置将分配给代表应用程序创建的任何会话跟踪cookie 的名称，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * <p>
     * 注意：更改会话跟踪 cookie 的名称可能会破坏假定 cookie 名称等于默认 JSESSIONID 的其他层（例如，负载平衡前端），因此应谨慎操作。
     */
    @Override
    public void setName(String name) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "name", context.getPath()));
        }
        this.name = name;
    }

    /**
     * 设置将分配给代表应用程序创建的任何会话跟踪cookies 的路径，该应用程序由从中获取此 SessionCookieConfig 的 ServletContext 表示。
     * @param path - 要使用的 cookie 路径
     */
    @Override
    public void setPath(String path) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "path", context.getPath()));
        }
        this.path = path;
    }

    /**
     * 标记或取消标记代表应用程序创建的会话跟踪 cookie 为安全的，该应用程序由获取此 SessionCookieConfig 的 ServletContext 表示。
     * <p>
     * 将会话跟踪 cookie 标记为安全的一个用例是，即使发起会话的请求来自 HTTP，也是为了支持 Web 容器位于 SSL 卸载负载平衡器前端的拓扑。
     * 在这种情况下， 客户端和负载均衡器将通过 HTTPS，而负载均衡器和 Web 容器之间的流量将通过 HTTP。
     * @param secure - 如果代表获得SessionCookieConfig的servletcontext所代表的应用程序创建的会话跟踪cookie应该标记为安全，即使发起相应会话的请求使用普通HTTP而不是HTTPS，则为true。
     * 如果只有当发起相应会话的请求也是安全的，它们才会被标记为安全的，则为false
     */
    @Override
    public void setSecure(boolean secure) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(String.format("cookie %s 错误的设置时机（上下文状态不为组件启动前状态-LifecycleState.STARTING_PREP），by context：%s", "secure", context.getPath()));
        }
        this.secure = secure;
    }

    /**
     * 为给定的会话ID创建一个新的会话cookie
     * 
     * <ul>注意：会话 cookie 配置的优先顺序是：
     * <li>1.上下文级别配置</li>
     * <li>2. 来自 SessionCookieConfig 的值</li>
     * <li>3. 默认值</li>
     * </ul>
     * @param context - web应用程序的上下文
     * @param sessionId - 要为其创建cookie的会话的ID
     * @param secure - 会话cookie应该配置为安全的吗
     * @return 会话 cookie
     */
    public static Cookie createSessionCookie(Context context, String sessionId, boolean secure) {
        SessionCookieConfig sessionCookieConfig = context.getServletContext().getSessionCookieConfig();

        Cookie cookie = new Cookie(SessionConfig.getSessionCookieName(context), sessionId);

        cookie.setMaxAge(sessionCookieConfig.getMaxAge());
        cookie.setComment(sessionCookieConfig.getComment());

        if (context.getSessionCookieDomain() == null) {
            if (sessionCookieConfig.getDomain() != null) {
                cookie.setDomain(sessionCookieConfig.getDomain());
            }
        } else {
            cookie.setDomain(context.getSessionCookieDomain());
        }

        // 如果请求是安全的，则始终设置为安全
        if (sessionCookieConfig.isSecure() || secure) {
            cookie.setSecure(true);
        }

        if (sessionCookieConfig.isHttpOnly() || context.getUseHttpOnly()) {
            cookie.setHttpOnly(true);
        }

        cookie.setPath(SessionConfig.getSessionCookiePath(context));

        return cookie;
    }
}
