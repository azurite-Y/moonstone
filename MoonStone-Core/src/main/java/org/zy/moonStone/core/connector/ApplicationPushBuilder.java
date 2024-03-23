package org.zy.moonstone.core.connector;

import org.zy.moonstone.core.http.Request;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.http.CookieProcessor;
import org.zy.moonstone.core.session.SessionConfig;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.collections.CaseInsensitiveKeyMap;
import org.zy.moonstone.core.util.http.ActionCode;

import javax.servlet.SessionTrackingMode;
import javax.servlet.http.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @dateTime 2022年11月24日;
 * @author zy(azurite-Y);
 * @description 构建要推送的请求。根据RFC 7540的8.2节，一个被承诺的请求必须是可缓存的，并且没有请求体是安全的.
 * <p>
 * PushBuilder是通过调用 {@link HttpServletRequest#newPushBuilder()} 来获得的。
 * 对该方法的每次调用都将基于当前HttpServletRequest生成一个新的PushBuilder实例，或者为空。
 * 返回的PushBuilder的任何改变都不会反映在未来的返回中。
 *
 * <p>实例初始化如下:</p>
 * <ul>
 * 		<li>方法初始化为 "GET"</li>
 * 		<li>当前{@link HttpServletRequest}的现有请求头被添加到构建器中，除了:
 * 				<ul>
 *   					<li>条件头(在RFC 7232中定义)
 *   					<li>范围标头
 *   					<li>预计头
 *   					<li>授权标头
 *   					<li>引用头文件
 * 				</ul></li>
 * 		<li>如果请求经过身份验证，则将使用容器生成的令牌设置Authorization标头，该令牌将导致推送请求的等效Authorization。</li>
 * 		<li>会话ID将是从HttpServletRequest.getRequestedSessionId()返回的值，除非HttpServletRequest.getSession(boolean)在调用创建PushBuilder之前已经被调用来创建一个新的HttpSession，在这种情况下，新的会话ID将被用作PushBuilder请求的会话ID。
 * 		注意，从请求返回的会话id可以有效地来自两个“来源”之一:cookie或URL (分别在 {@link HttpServletRequest#isRequestedSessionIdFromCookie() } 和 {@link HttpServletRequest#isRequestedSessionIdFromURL() } 中指定)。PushBuilder的会话ID也将来自与请求相同的源。</li>
 * 		<li>Referer(sic)头将被设置为 {@link HttpServletRequest#getRequestURL() } 加上任何 {@link HttpServletRequest#getQueryString() }</li>
 * 		<li>如果 {@link HttpServletResponse#addCookie(Cookie)} 已在相关响应中调用，则相应的Cookie标头将添加到PushBuilder中，除非 {@link Cookie#getMaxAge()} &lt;=0，在这种情况下，Cookie将从生成器中删除。</li>
 * </ul>
 * <p>
 * 在调用 {@link #path} 之前，必须在PushBuilderinstance上调用 {@link #path} 方法。如果这样做失败，则必须导致从 {@link #push} 抛出异常，如该方法中指定的那样
 * 
 * <p>
 * 在调用 {@link #push} 方法之前，可以通过链式调用mutatormethods对PushBuilder进行定制，以使用构建器的当前状态发起异步推送请求。
 * 在调用 {@link #push} 之后，构建器可以用于另一个push，但是实现必须在从push返回之前清除 {@link #path(String) }和条件头文件(在RFC 7232中定义)的值。所有其他值都保留在对 {@link #push} 的调用上。
 */

public class ApplicationPushBuilder implements PushBuilder {
    private static final Set<String> DISALLOWED_METHODS = new HashSet<>();

    static {
        DISALLOWED_METHODS.add("POST");
        DISALLOWED_METHODS.add("PUT");
        DISALLOWED_METHODS.add("DELETE");
        DISALLOWED_METHODS.add("CONNECT");
        DISALLOWED_METHODS.add("OPTIONS");
        DISALLOWED_METHODS.add("TRACE");
    }

    private final HttpServletRequest baseRequest;
    private final HttpRequest httpRequest;
    private final Request request;
    private final String sessionCookieName;
    private final String sessionPathParameterName;
    private final boolean addSessionCookie;
    private final boolean addSessionPathParameter;

    private final Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private String method = "GET";
    private String path;
    private String queryString;
    private String sessionId;


    public ApplicationPushBuilder(HttpRequest httpRequest, HttpServletRequest request) {
        baseRequest = request;
        this.httpRequest = httpRequest;
        this.request = httpRequest.getRequest();

        // 填充HTTP响应头的初始列表
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            List<String> values = new ArrayList<>();
            headers.put(headerName, values);
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                values.add(headerValues.nextElement());
            }
        }

        // Remove the headers
        headers.remove("if-match");
        headers.remove("if-none-match");
        headers.remove("if-modified-since");
        headers.remove("if-unmodified-since");
        headers.remove("if-range");
        headers.remove("range");
        headers.remove("expect");
        headers.remove("authorization");
        headers.remove("referer");
        // 还要删除cookie头，因为它将重新生成
        headers.remove("cookie");

        // 设置引用头
        StringBuffer referer = request.getRequestURL();
        if (request.getQueryString() != null) {
            referer.append('?');
            referer.append(request.getQueryString());
        }
        addHeader("referer", referer.toString());

        // Session
        Context context = httpRequest.getContext();
        sessionCookieName = SessionConfig.getSessionCookieName(context);
        sessionPathParameterName = SessionConfig.getSessionUriParamName(context);

        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }
        if (sessionId == null) {
            sessionId = request.getRequestedSessionId();
        }
        if (!request.isRequestedSessionIdFromCookie() && !request.isRequestedSessionIdFromURL() &&
                sessionId != null) {
            Set<SessionTrackingMode> sessionTrackingModes =
                    request.getServletContext().getEffectiveSessionTrackingModes();
            addSessionCookie = sessionTrackingModes.contains(SessionTrackingMode.COOKIE);
            addSessionPathParameter = sessionTrackingModes.contains(SessionTrackingMode.URL);
        } else {
            addSessionCookie = request.isRequestedSessionIdFromCookie();
            addSessionPathParameter = request.isRequestedSessionIdFromURL();
        }

        // Cookies
        if (request.getCookies() != null) {
            for (Cookie requestCookie : request.getCookies()) {
                cookies.add(requestCookie);
            }
        }
        for (Cookie responseCookie : httpRequest.getHttpResponse().getCookies()) {
            if (responseCookie.getMaxAge() < 0) {
                // 路径信息不可用，因此只能根据名称删除。
                Iterator<Cookie> cookieIterator = cookies.iterator();
                while (cookieIterator.hasNext()) {
                    Cookie cookie = cookieIterator.next();
                    if (cookie.getName().equals(responseCookie.getName())) {
                        cookieIterator.remove();
                    }
                }
            } else {
                cookies.add(new Cookie(responseCookie.getName(), responseCookie.getValue()));
            }
        }
        List<String> cookieValues = new ArrayList<>(1);
        cookieValues.add(generateCookieHeader(cookies, httpRequest.getContext().getCookieProcessor()));
        headers.put("cookie", cookieValues);

        // Authentication
//        if (httpRequest.getPrincipal() != null) {
//            if ((session == null) || httpRequest.getSessionInternal(false).getPrincipal() == null 
//            		|| !(context.getAuthenticator() instanceof AuthenticatorBase)
//                    || !((AuthenticatorBase) context.getAuthenticator()).getCache()) {
//                // 只有在主体没有会话缓存时才设置用户名
//                userName = httpRequest.getPrincipal().getName();
//            }
//            setHeader("authorization", "x-push");
//        }
    }

    @Override
    public PushBuilder path(String path) {
        if (path.startsWith("/")) {
            this.path = path;
        } else {
            String contextPath = baseRequest.getContextPath();
            int len = contextPath.length() + path.length() + 1;
            StringBuilder sb = new StringBuilder(len);
            sb.append(contextPath);
            sb.append('/');
            sb.append(path);
            this.path = sb.toString();
        }
        return this;
    }

    /**
     * 返回用于推送的URI路径
     * 
     * @return 用于推送的URI路径
     */
    @Override
    public String getPath() {
        return path;
    }

    /**
     * 设置要用于推送的方法
     * 
     * @param method - 用于推送的方法
     * @return 当前对象
     */
    @Override
    public PushBuilder method(String method) {
        String upperMethod = method.trim().toUpperCase(Locale.ENGLISH);
        if (DISALLOWED_METHODS.contains(upperMethod) || upperMethod.length() == 0) {
            throw new IllegalArgumentException("无效方法, by method: " + upperMethod);
        }
        // 检查是否提供了令牌
        for (char c : upperMethod.toCharArray()) {
        	// TODO
//            if (!HttpParser.isToken(c)) {
//                throw new IllegalArgumentException("method 未提供 Token, by method: " + upperMethod);
//            }
        }
        this.method = method;
        return this;
    }

    /**
     * @return 要用于推送的方法
     */
    @Override
    public String getMethod() {
        return method;
    }

    /**
     * 设置用于推送的查询字符串。查询字符串将被附加到调用path(string)中包含的任何查询字符串。
     * 任何重复的参数都必须保留。当使用相同的查询字符串进行多个push()调用时，应该使用此方法而不是path(String)中的查询。
     * 
     * @param queryString - 要用于推送的查询字符串
     * @return 当前对象
     */
    @Override
    public PushBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * @return 推流要使用的查询字符串
     */
    @Override
    public String getQueryString() {
        return queryString;
    }

    /**
     * 设置要用于推送的会话ID。会话ID的设置方式与在关联请求上的设置方式相同(如果关联请求使用cookie，则将其设置为cookie；
     * 如果关联请求使用url参数，则将其设置为url参数)。
     * 默认为请求的会话ID或来自新创建的会话的任何新分配的会话ID。
     * 
     * @param sessionId - 用于推送的SessionID
     * @return 当前对象
     */
    @Override
    public PushBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * @return 用于推送的SessionID
     */
    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 添加用于推流的请求头
     * 
     * @param name - 添加的header名
     * @param value - 添加的header值
     * @return 当前对象
     */
    @Override
    public PushBuilder addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);

        return this;
    }

    /**
     * 设置一个用于推送的请求头。如果构建器具有同名的现有header，则其值将被覆盖
     * 
     * @param name - 要设置的头名称
     * @param value - 要设置的头值
     */
    @Override
    public PushBuilder setHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        } else {
            values.clear();
        }
        values.add(value);

        return this;
    }
    
    /**
     * 删除命名的请求标头。如果标头不存在，则不采取任何操作
     * 
     * @param name - 要删除的标头的名称
     * @return 当前对象
     */
    @Override
    public PushBuilder removeHeader(String name) {
        headers.remove(name);
        return this;
    }

    /**
     * 返回用于推送的报头集。
     * <p>
     * 返回的集合不受PushBuilder对象的支持，因此返回集合中的更改不会反映在PushBuilder对象中，反之亦然。
     * 
     * @return 用于推送的标头集
     */
    @Override
    public Set<String> getHeaderNames() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    /**
     * 返回要用于推送的给定名称的头文件
     * 
     * @param name - 响应头的名称
     * @return 将用于推送的给定名称的响应头值
     */
    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        if (values == null) {
            return null;
        } else {
            return values.get(0);
        }
    }

    /**
     * 在给定构建器的当前状态下推送资源，该方法必须是非阻塞的。
     * <p>
     * 根据PushBuilder的当前状态推送资源。调用这个方法并不能保证资源会被推送，因为客户端可能会使用底层的HTTP/2协议拒绝接受推送的资源。
     * <p>
     * 如果构建器有会话ID，则推送的请求将以Cookie或适当的URI参数的形式包含会话ID。构建器查询字符串与任何传递的查询字符串合并。
     * <p>
     * 在从这个方法返回之前，构建器的路径、条件头文件(在RFC 7232中定义)为空。所有其他字段保持原样，以便在另一个推送中重用。
     */
    @Override
    public void push() {
        if (path == null) {
            throw new IllegalStateException("path 不能为 null");
        }

        Request pushTarget = new Request();

        pushTarget.method().setString(method);
        // 接下来的三个是由Javadoc getPath()隐含的
        pushTarget.serverName().setString(baseRequest.getServerName());
        pushTarget.setServerPort(baseRequest.getServerPort());
        pushTarget.scheme().setString(baseRequest.getScheme());

        // 复制响应头
        for (Map.Entry<String,List<String>> header : headers.entrySet()) {
            for (String value : header.getValue()) {
                pushTarget.getMimeHeaders().addHeadNameValue(header.getKey()).setString(value);
            }
        }

        // Path and query string
        int queryIndex = path.indexOf('?');
        String pushPath;
        String pushQueryString = null;
        if (queryIndex > -1) {
            pushPath = path.substring(0, queryIndex);
            if (queryIndex + 1 < path.length()) {
                pushQueryString = path.substring(queryIndex + 1);
            }
        } else {
            pushPath = path;
        }

        // 会话ID(在设置路径之前执行此操作，因为它可能会更改它)
        if (sessionId != null) {
            if (addSessionPathParameter) {
                pushPath = pushPath + ";" + sessionPathParameterName + "=" + sessionId;
                pushTarget.addPathParameter(sessionPathParameterName, sessionId);
            }
            if (addSessionCookie) {
                String sessionCookieHeader = sessionCookieName + "=" + sessionId;
                MessageBytes mb = pushTarget.getMimeHeaders().getValue("cookie");
                if (mb == null) {
                    mb = pushTarget.getMimeHeaders().addHeadNameValue("cookie");
                    mb.setString(sessionCookieHeader);
                } else {
                    mb.setString(mb.getString() + ";" + sessionCookieHeader);
                }
            }
        }

        // 未编码的路径-只是%nn编码
        pushTarget.requestURI().setString(pushPath);
        pushTarget.decodedURI().setString(decode(pushPath, httpRequest.getConnector().getURICharset()));

        // Query string
        if (pushQueryString == null && queryString != null) {
            pushTarget.queryString().setString(queryString);
        } else if (pushQueryString != null && queryString == null) {
            pushTarget.queryString().setString(pushQueryString);
        } else if (pushQueryString != null && queryString != null) {
            pushTarget.queryString().setString(pushQueryString + "&" +queryString);
        }

        // Authorization
//        if (userName != null) {
//            pushTarget.getRemoteUser().setString(userName);
//            pushTarget.setRemoteUserNeedsAuthorization(true);
//        }

        request.action(ActionCode.PUSH_REQUEST, pushTarget);

        // 为下次调用此方法重置
        path = null;
        headers.remove("if-none-match");
        headers.remove("if-modified-since");
    }

    private static String generateCookieHeader(List<Cookie> cookies, CookieProcessor cookieProcessor) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Cookie cookie : cookies) {
            if (first) {
                first = false;
            } else {
                result.append(';');
            }
            /*
             * CookieProcessor生成的cookie标头值最初用于响应上的Set cookie标头。
             * 但是，如果传递了一个只有名称和值集的Cookie，它将为推送请求的Cookie标头生成一个适当的标头。
             */
            result.append(cookieProcessor.generateHeader(cookie));
        }
        return result.toString();
    }
    

    // 将其打包为私有，以便进行测试。charsetName必须小写。
    static String decode(String input, Charset charset) {
        int start = input.indexOf('%');
        int end = 0;

        // Shortcut
        if (start == -1) {
            return input;
        }

        StringBuilder result = new StringBuilder(input.length());
        while (start != -1) {
            // 找到%nn序列的开头。将从最后一个端点到这个起点的所有内容复制到输出。
            result.append(input.substring(end, start));
            // 提前结束3个字符：%nn
            end = start + 3;
            while (end <input.length() && input.charAt(end) == '%') {
                end += 3;
            }
            result.append(decodePercentSequence(input.substring(start, end), charset));
            start = input.indexOf('%', end);
        }
        // 附加剩余文本
        result.append(input.substring(end));

        return result.toString();
    }


    private static String decodePercentSequence(String sequence, Charset charset) {
        byte[] bytes = new byte[sequence.length()/3];
        for (int i = 0; i < bytes.length; i += 3) {
        	// TODO
//            bytes[i] = (byte) ((HexUtils.getDec(sequence.charAt(1 + 3 * i)) << 4) + HexUtils.getDec(sequence.charAt(2 + 3 * i)));
        }

        return new String(bytes, charset);
    }
}
