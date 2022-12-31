package org.zy.moonStone.core.util;

import java.lang.reflect.InvocationTargetException;

/**
 * @dateTime 2021年12月31日;
 * @author zy(azurite-Y);
 * @description
 */
public class ExceptionUtils {
	/**
     * 检查提供的Throwable是否需要被抛出并抑制其他所有对象.
     * @param t - 检查的Throwable
     */
    public static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof StackOverflowError) {
            // 抑制堆栈溢出异常 - gc应该可以处理
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
    }

    /**
     * 检查提供的Throwable是否是InvocationTargetException的实例，并返回由其包装的Throwable（如果有）.
     *
     * @param t - 可供检查的Throwable
     * @return <code>t</code> 或 <code>t.getCause()</code>
     */
    public static Throwable unwrapInvocationTargetException(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
