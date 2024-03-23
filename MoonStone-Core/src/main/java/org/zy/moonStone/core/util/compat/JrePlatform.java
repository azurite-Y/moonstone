package org.zy.moonstone.core.util.compat;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

/**
 * @dateTime 2022年8月26日;
 * @author zy(azurite-Y);
 * @description
 */
public class JrePlatform {
	public static final boolean IS_MAC_OS;
	public static final boolean IS_WINDOWS;

	private static final String OS_NAME_PROPERTY = "os.name";

	static {
		/*
		 * Java API 的行为取决于底层平台且这些行为差异对 moonstone 的影响。
		 * 因此，moonstone 需要能够确定它运行的平台以解决这些差异。
		 */

		// 此检查源自 Apache Commons Lang 中的检查
		String osName;
		if (System.getSecurityManager() == null) {
			osName = System.getProperty(OS_NAME_PROPERTY);
		} else {
			osName = AccessController.doPrivileged(new PrivilegedAction<String>() {
				@Override
				public String run() {
					return System.getProperty(OS_NAME_PROPERTY);
				}
			});
		}

		IS_MAC_OS = osName.toLowerCase(Locale.ENGLISH).startsWith("mac os x");

		IS_WINDOWS = osName.startsWith("Windows");
	}
}
