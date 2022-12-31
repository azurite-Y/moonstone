package org.zy.moonStone.core.exceptions;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description 由ThreadPoolExecutorto抛出的自定义RuntimeException，表示线程应该被处理掉
 */
public class StopPooledThreadException extends RuntimeException {
	private static final long serialVersionUID = 3240913778247356223L;

	public StopPooledThreadException(String msg) {
        super(msg);
    }
}
