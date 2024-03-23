package org.zy.moonstone.core.session;

/**
 * @dateTime 2022年8月9日;
 * @author zy(azurite-Y);
 * @description
 */
public class StandardSessionIdGenerator extends SessionIdGeneratorBase {

	@Override
	public String generateSessionId(String route) {
		byte random[] = new byte[16];
        int sessionIdLength = getSessionIdLength();

        // 将结果呈现为十六进制数字字符串，开头有足够的空间用于会话IdLong和中等路径大小
        StringBuilder buffer = new StringBuilder(2 * sessionIdLength + 20);

        int resultLenBytes = 0;

        while (resultLenBytes < sessionIdLength) {
            getRandomBytes(random);
            for (int j = 0;
            j < random.length && resultLenBytes < sessionIdLength;
            j++) {
                byte b1 = (byte) ((random[j] & 0xf0) >> 4);
                byte b2 = (byte) (random[j] & 0x0f);
                if (b1 < 10)
                    buffer.append((char) ('0' + b1));
                else
                    buffer.append((char) ('A' + (b1 - 10)));
                if (b2 < 10)
                    buffer.append((char) ('0' + b2));
                else
                    buffer.append((char) ('A' + (b2 - 10)));
                resultLenBytes++;
            }
        }

        if (route != null && route.length() > 0) {
            buffer.append('.').append(route);
        } else {
            String jvmRoute = getJvmRoute();
            if (jvmRoute != null && jvmRoute.length() > 0) {
                buffer.append('.').append(jvmRoute);
            }
        }

        return buffer.toString();
	}

}
