package org.zy.moonstone.core.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @dateTime 2022年8月11日;
 * @author zy(azurite-Y);
 * @description
 */
public class SessionConstants {
	/**
	 * moonstone 内部使用的一组会话属性名称，在会话被持久保存和复制之前，应始终将其从会话中删除的会话属性
     */
    public static final Set<String> excludedAttributeNames;

    static {
        Set<String> names = new HashSet<>();
        excludedAttributeNames = Collections.unmodifiableSet(names);
    }
}
