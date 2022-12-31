package org.zy.moonStone.core.exceptions;

/**
 * @dateTime 2022年8月9日;
 * @author zy(azurite-Y);
 * @description 一个异常。表示已达到活动会话的最大数量，服务器拒绝创建任何新的会话。
 */
public class TooManyActiveSessionsException extends IllegalStateException {
	private static final long serialVersionUID = -7400589766184431434L;
	
	/** 服务器将容纳的最大活跃会话数 */
    private final int maxActiveSessions;

    /**
     * 创建一个新的 TooManyActiveSessionsException.
     *
     * @param message - 异常的描述
     * @param maxActive - 会话管理器允许的最大活跃会话数
     */
    public TooManyActiveSessionsException(String message, int maxActive) {
        super(message);
        maxActiveSessions = maxActive;
    }

    /**
     * 获取会话管理器允许的最大会话数
     *
     * @return 会话管理器允许的最大会话数
     */
    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }
}
