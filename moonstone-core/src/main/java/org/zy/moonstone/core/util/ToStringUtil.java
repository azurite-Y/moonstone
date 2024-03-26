package org.zy.moonstone.core.util;

import org.zy.moonstone.core.interfaces.container.Contained;
import org.zy.moonstone.core.interfaces.container.Container;
import org.zy.moonstone.core.session.interfaces.Manager;

/**
 * @dateTime 2022年8月10日;
 * @author zy(azurite-Y);
 * @description 用于帮助生成调用 Object.toString() 的返回值的实用程序类
 */
public class ToStringUtil {
	private ToStringUtil() {}

    public static final String toString(Contained contained) {
        return toString(contained, contained.getContainer());
    }

    public static final String toString(Object obj, Container container) {
        return containedToString(obj, container, "Container");
    }

    public static final String toString(Object obj, Manager manager) {
        return containedToString(obj, manager, "Manager");
    }

    private static final String containedToString(Object contained, Object container, String containerTypeName) {
        StringBuilder sb = new StringBuilder(contained.getClass().getSimpleName());
        sb.append('[');
        if (container == null) {
            sb.append(containerTypeName);
            sb.append(" is null");
        } else {
            sb.append(container.toString());
        }
        sb.append(']');
        return sb.toString();
    }
}
