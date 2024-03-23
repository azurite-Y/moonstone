package org.zy.moonstone.core.util.net;

/**
 * @dateTime 2022年5月31日;
 * @author zy(azurite-Y);
 * @description
 * 
 * 用于标记由容器分配来处理来自传入连接的数据的线程。应用程序创建的线程不是容器线程，也不是从容器线程池中获取的用于执行AsyncContext.start(Runnable)的线程。
 */
public class ContainerThreadMarker {
	private static final ThreadLocal<Boolean> marker = new ThreadLocal<>();

	/**
	 * 标识当前线程是否容器线程
	 * 
	 * @return true则代表是容器分配来处理来自传入连接数据的线程，反之则是应用程序创建的线程
	 */
    public static boolean isContainerThread() {
        Boolean flag = marker.get();
        if (flag == null) {
            return false;
        } else {
            return flag.booleanValue();
        }
    }

    public static void set() {
        marker.set(Boolean.TRUE);
    }

    public static void clear() {
        marker.set(Boolean.FALSE);
    }
}
