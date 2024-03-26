package org.zy.moonstone.core.util;

import org.zy.moonstone.core.Constants;

import javax.servlet.http.HttpServletRequest;

/**
 * @dateTime 2022年8月26日;
 * @author zy(azurite-Y);
 * @description
 */
public class RequestUtil {
	private RequestUtil() {}

    /**
     * 规范化可能具有相对值（"/./"、"/../"等）的相对 URI 路径。
     * <strong>警告</strong> - 此方法仅用于规范化应用程序生成的路径。 它不会尝试对恶意输入执行安全检查。
     *
     * @param path - 要规范化的相对路径
     * @return 规范化路径; 如果路径无法规范化, 则返回 null
     */
    public static String normalize(String path) {
        return normalize(path, true);
    }


    /**
     * 规范化可能具有相对值（“/./”、“/../”等）的相对 URI 路径。
     * <strong>警告</strong> - 此方法仅用于规范化应用程序生成的路径。 它不会尝试对恶意输入执行安全检查。
     *
     * @param path - 要规范化的相对路径
     * @param replaceBackSlash - 是否应将 "\\" 替换为 "/"
     *
     * @return 规范化路径; 如果路径无法规范化, 则返回 null
     */
    public static String normalize(String path, boolean replaceBackSlash) {
        if (path == null) {
            return null;
        }

        // 为规范化路径创建位置
        String normalized = path;

        if (replaceBackSlash && normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // 如有必要，添加前导“/”
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        // 如有必要，添加末尾“/”
        boolean addedTrailingSlash = false;
        if (normalized.endsWith("/.") || normalized.endsWith("/..")) {
            normalized = normalized + "/";
            addedTrailingSlash = true;
        }

        // 解析规范化路径中出现的“//”
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 1);
        }

        // 解析规范化路径中出现的“/./”
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0) {
                break;
            }
            // 去除 "/.", 而保留"/"
            normalized = normalized.substring(0, index) + normalized.substring(index + 2);
        }

        // 解析规范化路径中出现的“/../”
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                break;
            }
            if (index == 0) {
                return null;  // 试图脱离的语境
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            // 去除 "/..", 而保留"/"
            normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
        }

        if (normalized.length() > 1 && addedTrailingSlash) {
        	// 删除末尾的 "/"
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // 返回已经完成的规范化路径
        return normalized;
    }
    
    /**
     * 基于所提供的请求对象为 {@link HttpServletRequest#getRequestURL()} 构建适当的返回值。
     * 注意，这也适用于 {@link javax.servlet.http.HttpServletRequestWrapper} 的实例。
     *
     * @param request - 应该为其构建URL的请求对象
     * @return 给定请求对象的请求URL
     */
    public static StringBuffer getRequestURL(HttpServletRequest request) {
        StringBuffer url = new StringBuffer();
        String scheme = request.getScheme();
        int port = request.getServerPort();
        if (port < 0) {
            // 解决java.net.URL错误
            port = 80;
        }

        url.append(scheme);
        url.append("://");
        url.append(request.getServerName());
        if ((scheme.equals("http") && (port != 80)) || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(request.getRequestURI());

        return url;
    }
    
    /**
	 * 判断当前方法时否为"GET"、"HEAD"、"DELETE"
	 * @return 是则为true
	 */
	public static boolean hasRequestBody(byte[] method) {
		return ( ArraysUtils.equalsByte(method, Constants.HTTP_GET) ||  ArraysUtils.equalsByte(method, Constants.HTTP_HEAD) || ArraysUtils.equalsByte(method, Constants.HTTP_DELETE))
				? false : true;
	}
}
