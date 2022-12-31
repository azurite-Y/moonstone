package org.zy.moonStone.core.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * @dateTime 2022年5月12日;
 * @author zy(azurite-Y);
 * @description
 */
public class ServerInfo {
	/**
	 * 识别服务器信息字符串
	 */
    private static final String serverInfo;

    /**
     * 服务器版本号
     */
    private static final String serverNumber;

    static {

        String info = null;
        String number = null;

        Properties props = new Properties();
    	try ( InputStream is = ClassLoader.getSystemResourceAsStream("org/zy/moonStone/core/util/ServerInfo.properties") ) {
            props.load(is);
            info = props.getProperty("server.info");
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

        serverInfo = info;
        serverNumber = number;
    }


    /**
     * @return 当前版本的服务器标识
     */
    public static String getServerInfo() {
        return serverInfo;
    }

    /**
     * @return 服务器版本
     */
    public static String getServerNumber() {
        return serverNumber;
    }

    public static void main(String args[]) {
    	System.out.println("Server version: " + getServerInfo());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " + System.getProperty("os.name"));
        System.out.println("OS Version:     " + System.getProperty("os.version"));
        System.out.println("Architecture:   " + System.getProperty("os.arch"));
        System.out.println("JVM Version:    " + System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " + System.getProperty("java.vm.vendor"));
    }
}
