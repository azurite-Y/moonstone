package org.zy.moonstone.core.session.interfaces;

/**
 * @dateTime 2021年12月30日;
 * @author zy(azurite-Y);
 * @description
 */
public interface SessionIdGenerator {
	/**
     * @return 与此节点关联的节点标识符，将包含在生成的会话ID中.
     */
    public String getJvmRoute();

    /**
     * 指定与此节点关联的节点标识符，它将包含在生成的会话ID中.
     *
     * @param jvmRoute - 节点标识符
     */
    public void setJvmRoute(String jvmRoute);

    /**
     * @return  会话ID的字节数
     */
    public int getSessionIdLength();

    /**
     * 指定会话ID的字节数
     *
     * @param sessionIdLength   字节数
     */
    public void setSessionIdLength(int sessionIdLength);

    /**
     * 生成并返回一个新的会话标识符.
     *
     * @return 新生成的会话id
     */
    public String generateSessionId();

    /**
     * 生成并返回一个新的会话标识符.
     *
     * @param route - 要包含在生成的id中的节点标识符
     * @return 新生成的会话id
     */
    public String generateSessionId(String route);
}
