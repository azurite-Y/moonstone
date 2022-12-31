package org.zy.moonStone.core.util.http;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description 这个类不是线程安全的
 */
public class ServerCookies {
	 private ServerCookie[] serverCookies;

	    private int cookieCount = 0;
	    private int limit = 200;

	    public ServerCookies(int initialSize) {
	        serverCookies = new ServerCookie[initialSize];
	    }

	    /**
	     * 注册一个新的、初始化的cookie。cookie会被回收，大多数时候会返回一个现有的ServerCookie对象。调用者可以为cookie设置名称/值和属性。
	     * @return 新的 cookie
	     */
	    public ServerCookie addCookie() {
	        if (limit > -1 && cookieCount >= limit) {
	            throw new IllegalArgumentException(String.format("服务器端cookie 存储超过最大限制，by count：%s", Integer.valueOf(limit)));
	        }

	        if (cookieCount >= serverCookies.length) { // 扩容
	            int newSize = limit > -1 ? Math.min(2*cookieCount, limit) : 2*cookieCount;
	            ServerCookie scookiesTmp[] = new ServerCookie[newSize];
	            System.arraycopy(serverCookies, 0, scookiesTmp, 0, cookieCount);
	            serverCookies = scookiesTmp;
	        }

	        ServerCookie c = serverCookies[cookieCount];
	        if (c == null) {
	            c = new ServerCookie();
	            serverCookies[cookieCount] = c;
	        }
	        cookieCount++;
	        return c;
	    }

	    public ServerCookie getCookie(int idx) {
	        return serverCookies[idx];
	    }

	    public int getCookieCount() {
	        return cookieCount;
	    }

	    public void setLimit(int limit) {
	        this.limit = limit;
	        if (limit > -1 && serverCookies.length > limit && cookieCount <= limit) {
	            // 缩小 cookie 列表数组
	            ServerCookie scookiesTmp[] = new ServerCookie[limit];
	            System.arraycopy(serverCookies, 0, scookiesTmp, 0, cookieCount);
	            serverCookies = scookiesTmp;
	        }
	    }

	    /**
	     * 回收资源
	     */
	    public void recycle() {
	        for (int i = 0; i < cookieCount; i++) {
	            serverCookies[i].recycle();
	        }
	        cookieCount = 0;
	    }
}
