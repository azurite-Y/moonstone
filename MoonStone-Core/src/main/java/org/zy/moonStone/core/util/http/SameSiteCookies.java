package org.zy.moonStone.core.util.http;

/**
 * @dateTime 2022年8月6日;
 * @author zy(azurite-Y);
 * @description
 */
public enum SameSiteCookies {
	/**
     * 不设置 SameSite cookie 属性
     */
    UNSET("Unset"),

    /**
     * Cookie 总是在跨站请求中发送。
     * 如果设置SameSite=None那么Cookie必须是Secure的，只能https传送
     */
    NONE("None"),

    /**
     * Cookie 仅在同站点请求和跨站点导航 GET 请求时发送
     */
    LAX("Lax"),

    /**
     * 阻止浏览器在所有跨站请求中发送cookie
     */
    STRICT("Strict");

    private final String value;

    SameSiteCookies(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SameSiteCookies fromString(String value) {
        for (SameSiteCookies sameSiteCookies : SameSiteCookies.values()) {
            if (sameSiteCookies.getValue().equalsIgnoreCase(value)) {
                return sameSiteCookies;
            }
        }
        throw new IllegalStateException("无效的 SameSiteCookies，by：" + value);
    }
}
