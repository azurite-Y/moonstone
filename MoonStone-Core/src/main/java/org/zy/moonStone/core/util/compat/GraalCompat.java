package org.zy.moonStone.core.util.compat;

import java.io.IOException;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description
 */
class GraalCompat extends JreCompat {
	private static final boolean GRAAL;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = Boolean.TRUE.equals(nativeImageClazz.getMethod("inImageCode").invoke(null));
        } catch (ClassNotFoundException e) {
            // 必须Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // 不应该发生
        }
        GRAAL = result;
    }

    static boolean isSupported() {
        // 本机映像是否不存在此属性
        return GRAAL;
    }

    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
    }
}
