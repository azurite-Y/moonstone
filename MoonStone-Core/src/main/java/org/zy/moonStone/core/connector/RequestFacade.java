package org.zy.moonStone.core.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import javax.servlet.http.PushBuilder;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.security.SecurityUtil;

/**
 * @dateTime 2022年6月29日;
 * @author zy(azurite-Y);
 * @description
 */
public class RequestFacade implements HttpServletRequest {
    /**
     * 包装的请求对象
     */
    protected HttpRequest httpRequest = null;
    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 为指定的请求构造一个包装器
     *
     * @param request - 要包装的请求
     */
    public RequestFacade(HttpRequest httpRequest) {
        if (httpRequest == null) {
            throw new IllegalStateException("RequestFacade 包装的 HttpRequest 不能为null");
        }
        
        this.httpRequest = httpRequest;
    }

	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    /**
     * Clear facade.
     */
    public void clear() {
    	httpRequest = null;
    }

    /**
     * 防止克隆 facade
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    // ------------------------------------------------------------------------------------- ServletRequest 方法 -------------------------------------------------------------------------------------
	/**
	 * 将命名属性的值作为对象返回，如果不存在给定名称的属性，则返回 null。
	 * <p>
	 * 可以通过两种方式设置属性。 servlet 容器可以设置属性以提供有关请求的自定义信息。
	 * 例如，对于使用 HTTPS 发出的请求，属性 javax.servlet.request.X509Certificate 可用于检索有关客户端证书的信息。 
	 * 也可以使用 ServletRequest.setAttribute 以编程方式设置属性。 这允许在 RequestDispatcher 调用之前将信息嵌入到请求中。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 本规范保留与 java.*、javax.* 和 sun.* 匹配的名称。
	 * @param name - 指定属性名称的字符串
	 * @return 包含属性值的对象，如果属性不存在，则返回 null
	 */
    @Override
    public Object getAttribute(String name) {
        return httpRequest.getAttribute(name);
    }


    /**
     * 返回一个枚举，其中包含此请求可用的属性的名称。如果请求没有可用的属性，则此方法返回一个空枚举。
     * 
     * @return 包含请求属性名称的字符串枚举
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetAttributePrivilegedAction());
        } else {
            return httpRequest.getAttributeNames();
        }
    }

    
	/**
	 * 返回此请求正文中使用的字符编码的名称。 如果未指定请求编码字符编码，则此方法返回 null。 
	 * 以下方法用于指定请求字符编码，按优先级降序排列：每个请求、每个 Web 应用程序（使用 ServletContext.setRequestCharacterEncoding、deploymentdescriptor）
	 * 和每个容器（对于部署在该容器中的所有 Web 应用程序，使用提供的特定配置）。
	 * @return 包含字符编码名称的字符串，如果请求未指定字符编码，则返回 null
	 */
    @Override
    public String getCharacterEncoding() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetCharacterEncodingPrivilegedAction());
        } else {
            return httpRequest.getCharacterEncoding();
        }
    }


	/**
	 * 覆盖此请求正文中使用的字符编码的名称。 此方法必须在读取请求参数或使用 getReader() 读取输入之前调用。 否则，它没有效果。
	 * @param env - 包含字符编码名称的字符串
	 * @throws UnsupportedEncodingException - 如果这个ServletRequest仍然处于可以设置字符编码的状态，但是指定的编码无效
	 */
    @Override
    public void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException {
    	httpRequest.setCharacterEncoding(env);
    }


	/**
	 * 返回请求正文的长度（以字节为单位）并由输入流提供，如果长度未知或大于 Integer.MAX_VALUE则返回 -1 。 对于 HTTP servlet，与 CGI 变量 CONTENT_LENGTH 的值相同。
	 * @return 一个包含请求正文长度的整数，如果长度未知或大于  {@code Integer.MAX_VALUE } ，则为 -1。
	 */
    @Override
    public int getContentLength() {
        return httpRequest.getContentLength();
    }


	/**
	 * 返回请求正文的 MIME 类型，如果类型未知，则返回 null。 对于 HTTP servlet，与 CGI 变量 CONTENT_TYPE 的值相同。
	 * @return 包含请求的 MIME 类型名称的字符串，如果类型未知，则返回 null
	 */
    @Override
    public String getContentType() {
        return httpRequest.getContentType();
    }


	/**
	 * 使用 ServletInputStream 将请求的主体作为二进制数据检索。 可以调用此方法或 getReader 来读取正文，不能同时调用两者。
	 * 
	 * @return 包含请求正文的 ServletInputStream 对象
	 * @throws IOException - 如果发生输入或输出异常
	 */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        return httpRequest.getInputStream();
    }


    @Override
    public String getParameter(String name) {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetParameterPrivilegedAction(name));
        } else {
            return httpRequest.getParameter(name);
        }
    }


	/**
	 * 将请求参数的值作为字符串返回，如果参数不存在，则返回 null。 请求参数是随请求发送的额外信息。 对于 HTTP servlet，参数包含在查询字符串或发布的表单数据中。
	 * <p>
	 * 仅当您确定参数只有一个值时，才应使用此方法。 如果参数可能有多个值，请使用 getParameterValues。
	 * <p>
	 * 如果将此方法与多值参数一起使用，则返回的值等于 getParameterValues 返回的数组中的第一个值。
	 * <p>
	 * 如果参数数据是在请求体中发送的，例如发生在 HTTP POST 请求中，那么直接通过 getInputStream 或 getReader 读取请求体会干扰该方法的执行。
	 * 
	 * @param name - 指定参数名称的字符串
	 * @return 一个字符串，表示参数的单个值
	 */
    @Override
    public Enumeration<String> getParameterNames() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetParameterNamesPrivilegedAction());
        } else {
            return httpRequest.getParameterNames();
        }
    }

    
	/**
	 * 返回包含给定请求参数具有的所有值的 String 对象数组，如果参数不存在，则返回 null。
	 * <p>
	 * 如果参数只有一个值，则数组的长度为 1。
	 * 
	 * @return 包含请求其值的参数名称的字符串
	 */
    @Override
    public String[] getParameterValues(String name) {
        String[] ret = null;

        // 仅当存在安全管理器时才克隆返回的数组，这样在不安全的情况下性能不会受到影响
        if (SecurityUtil.isPackageProtectionEnabled()){
            ret = AccessController.doPrivileged(new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = httpRequest.getParameterValues(name);
        }

        return ret;
    }


	/**
	 * 返回此请求参数的 java.util.Map。
	 * <p>
	 * 请求参数是与请求一起发送的额外信息。对于 HTTP servlet，参数包含在查询字符串或发布的表单数据中。
	 * 
	 * @return 一个不可变的 java.util.Map，包含参数名称作为键和参数值作为映射值。 参数映射中的键是字符串类型。 参数映射中的值是 String 数组 类型。
	 */
    @Override
    public Map<String,String[]> getParameterMap() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetParameterMapPrivilegedAction());
        } else {
            return httpRequest.getParameterMap();
        }
    }


	/**
	 * 返回请求使用的协议的名称和版本，格式为 protocol/majorVersion.minorVersion，例如 HTTP/1.1。 对于 HTTP servlet，返回的值与 CGI 变量 SERVER_PROTOCOL 的值相同。
	 * 
	 * @return 包含协议名称和版本号的字符串
	 */
    @Override
    public String getProtocol() {
        return httpRequest.getProtocol();
    }


	/**
	 * 返回用于发出此请求的方案的名称，例如 http、https 或 ftp。不同的方案具有不同的 URL 构造规则，如 RFC 1738 中所述。
	 * 
	 * @return 包含用于发出此请求的方案名称的字符串
	 */
    @Override
    public String getScheme() {
        return httpRequest.getScheme();
    }


	/**
	 * 返回请求发送到的服务器的主机名。它是 Host 请求头值中“：”之前部分的值（如果有），或者解析的服务器名称，或者服务器 IP 地址。
	 * @return 包含服务器名称的字符串
	 */
    @Override
    public String getServerName() {
        return httpRequest.getServerName();
    }


	/**
	 * 返回请求发送到的端口号。它是 Host请求头值中“:”之后部分的值，如果有的话，或者是接受客户端连接的服务器端口。
	 */
    @Override
    public int getServerPort() {
        return httpRequest.getServerPort();
    }


	/**
	 * 使用 BufferedReader 将请求的主体作为字符数据检索。 阅读器根据正文使用的字符编码转换字符数据。可以调用此方法或 getInputStream 来读取正文，不能同时调用两者。
	 * @return 包含请求正文的 BufferedReader
	 * @throws IOException - 如果发生输入或输出异常
	 */
    @Override
    public BufferedReader getReader() throws IOException {
        return httpRequest.getReader();
    }


	/**
	 * 返回发送请求的客户端或最后一个代理的 Internet 协议 (IP) 地址。对于 HTTP servlet，与 CGI 变量 REMOTE_ADDR 的值相同。
	 * 
	 * @return 一个字符串，包含发送请求的客户端的 IP 地址
	 */
    @Override
    public String getRemoteAddr() {
        return httpRequest.getRemoteAddr();
    }


	/**
	 * 返回发送请求的客户端或最后一个代理的完全限定名称。如果引擎不能或选择不解析主机名（以提高性能），则此方法返回 IP 地址的点分字符串形式。 对于 HTTP servlet，与 CGI 变量 REMOTE_HOST 的值相同。
	 * 
	 * @return 包含客户端完全限定名称的字符串
	 */
    @Override
    public String getRemoteHost() {
        return httpRequest.getRemoteHost();
    }


	/**
	 * 在此请求中存储一个属性。在请求之间重置属性。 此方法最常与 RequestDispatcher 结合使用。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 以 java.*、javax.* 和 com.sun.* 开头的名称保留供 Sun Micro 系统使用。如果传入的对象为null，效果和调用removeAttribute一样。
	 * 
	 * @apiNote 警告当请求从 servlet 分派时，RequestDispatcher 驻留在不同的 Web 应用程序中，此方法设置的对象可能无法在调用方 servlet 中正确检索。
	 * 
	 * @param name - 一个字符串，指定属性的名称
	 * @param o - 要存储的对象
	 */
    @Override
    public void setAttribute(String name, Object o) {
        httpRequest.setAttribute(name, o);
    }


	/**
	 * 从此请求中删除一个属性。 通常不需要此方法，因为属性仅在处理请求时才持续存在。
	 * <p>
	 * 属性名称应遵循与包名称相同的约定。 以 java.*、javax.* 和 com.sun.* 开头的名称保留供 Sun Micro 系统使用。
	 * 
	 * @param name - 一个字符串，指定要删除的属性的名称
	 */
    @Override
    public void removeAttribute(String name) {
        httpRequest.removeAttribute(name);
    }


	/**
	 * 根据 Accept-Language 标头返回客户端将接受内容的首选区域设置。如果客户端请求未提供 Accept-Language 标头，则此方法返回服务器的默认区域设置。
	 * 
	 * @return 客户端的首选语言环境
	 */
    @Override
    public Locale getLocale() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetLocalePrivilegedAction());
        } else {
            return httpRequest.getLocale();
        }
    }


	/**
	 * 返回一个区域设置对象的枚举，从首选区域设置开始按降序表示客户端可以接受的基于 Accept-Language 标头的区域设置。
	 * 如果客户端请求未提供 Accept-Language 标头，则此方法返回一个包含 一种语言环境，服务器的默认语言环境。
	 * 
	 * @return 客户端首选区域设置对象的枚举
	 */
    @Override
    public Enumeration<Locale> getLocales() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetLocalesPrivilegedAction());
        } else {
            return httpRequest.getLocales();
        }
    }


	/**
	 * 返回一个布尔值，指示此请求是否使用安全通道（例如 HTTPS）发出。
	 * 
	 * @return 一个布尔值，指示是否使用安全通道发出请求
	 */
    @Override
    public boolean isSecure() {
        return httpRequest.isSecure();
    }


	/**
	 * 返回一个 RequestDispatcher 对象，该对象充当位于给定路径的资源的包装器。RequestDispatcher 对象可用于将请求转发到资源或将资源包含在响应中。资源可以是动态的或静态的。
	 * <p>
	 * 指定的路径名可以是相对的，尽管它不能扩展到当前 servlet 上下文之外。 如果路径以“/”开头，则将其解释为相对于当前上下文根。如果 servlet 容器无法返回 RequestDispatcher，则此方法返回 null。
	 * <p>
	 * 该方法与 ServletContext.getRequestDispatcher 的区别在于该方法可以走相对路径。
	 * 
	 * @param path - 一个字符串，指定资源的路径名。 如果它是相对的，它必须与当前的 servlet 相对。
	 * @return 一个 RequestDispatcher 对象，它充当指定路径上资源的包装器，如果 servlet 容器无法返回 RequestDispatcher，则为 null
	 */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetRequestDispatcherPrivilegedAction(path));
        } else {
            return httpRequest.getRequestDispatcher(path);
        }
    }

    
	/**
	 * @param path - 要返回真实路径的路径。
	 * @return 真实路径，如果无法执行翻译，则返回 null
	 * @deprecated 从 Java Servlet API 2.1 版开始，使用 ServletContext.getRealPath()。
	 */
    @Override
    @Deprecated
    public String getRealPath(String path) {
        return httpRequest.getRealPath(path);
    }


	/**
	 * 返回用于保护 servlet 的身份验证方案的名称。 所有 servlet 容器都支持基本、表单和客户端证书身份验证，并且可能还支持摘要身份验证。如果 servlet 未通过身份验证，则返回 null。
	 * <p>
	 * 与 CGI 变量 AUTH_TYPE 的值相同。
	 * 
	 * @return 静态成员 BASIC_AUTH、FORM_AUTH、CLIENT_CERT_AUTH、DIGEST_AUTH（适合 == 比较）或指示身份验证方案的容器特定字符串之一，如果请求未通过身份验证，则为 null。
	 */
    @Override
    public String getAuthType() {
        return httpRequest.getAuthType();
    }


	/**
	 * 返回一个数组，其中包含客户端随此请求发送的所有 Cookie 对象。如果未发送任何 cookie，则此方法返回 null。
	 * 
	 * @return 此请求中包含的所有 Cookie 的数组，如果请求没有 cookie，则为 null
	 */
    @Override
    public Cookie[] getCookies() {
        Cookie[] ret = null;

        /*
         * 仅当存在安全管理器时才克隆返回的数组，这样在不安全的情况下性能不会受到影响
         */
        if (SecurityUtil.isPackageProtectionEnabled()){
            ret = AccessController.doPrivileged(new GetCookiesPrivilegedAction());
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = httpRequest.getCookies();
        }
        return ret;
    }


    /**
	 * 返回指定请求头的值，该值为表示Date对象的长值。对于包含日期的标题，例如If-Modified-Since，使用此方法。
	 * <p>
	 * 日期返回为自1970格林尼治标准时间1月1日以来的毫秒数。标题名称不区分大小写。
	 * <p>
	 * 如果请求没有指定名称的消息头，这个方法返回-1。如果头文件不能转换为日期，该方法会抛出IllegalArgumentException。
	 * 
	 * @param name - 指定请求头名称的字符串
	 * @return 一个 long 值，表示标头中指定的日期，表示为自 1970 年 1 月 1 日 GMT 以来的毫秒数，如果请求中未包含命名标头，则为 -1
	 * 
	 * @apiNote If-Modified-Since是标准的HTTP请求头标签，在发送HTTP请求时，把浏览器端缓存页面的最后修改时间一起发到服务器去，
	 * 服务器会把这个时间与服务器上实际文件的最后修改时间进行比较。
	 * <p>
	 * 如果时间一致，那么返回HTTP状态码304（不返回文件内容），客户端接到之后，就直接把本地缓存文件显示到浏览器中。
	 * <p>
	 * 如果时间不一致，就返回HTTP状态码200和新的文件内容，客户端接到之后，会丢弃旧文件，把新文件缓存起来，并显示到浏览器中。
	 */
    @Override
    public long getDateHeader(String name) {
        return httpRequest.getDateHeader(name);
    }


	/**
	 * 以字符串形式返回指定请求头的值。 如果请求不包含指定名称的header，该方法返回null。
	 * 如果有多个同名的header，该方法返回请求中的第一个header。header名称不区分大小写。
	 * 
	 * @param name - 指定请求头名称的字符串
	 */
    @Override
    public String getHeader(String name) {
        return httpRequest.getHeader(name);
    }


	/**
	 * 返回指定请求部的所有值，作为String对象的枚举值。
	 * <p>
	 * 有些报头，例如Accept-Language，可以由客户端以多个报头的形式发送，每个报头具有不同的值，而不是以逗号分隔的列表形式发送报头。
	 * <p>
	 * 如果请求不包含任何指定名称的头文件，此方法将返回一个空的枚举。标题名称不区分大小写。您可以对任何请求头使用此方法。
	 * 
	 * @param name - 指定请求头名称的字符串
	 * @return 一个包含所请求头值的枚举。 如果请求没有该名称的任何请求头名称，则返回一个空枚举。 如果容器不允许访问header信息，则返回null
	 */
    @Override
    public Enumeration<String> getHeaders(String name) {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetHeadersPrivilegedAction(name));
        } else {
            return httpRequest.getHeaders(name);
        }
    }


	/**
	 * 返回此请求包含的所有标请求头名称的枚举。 如果请求没有请求头，则此方法返回一个空枚举。
	 * <p>
	 * 某些 servlet 容器不允许 servlet 使用此方法访问标头，在这种情况下此方法返回 null
	 * 
	 * @return 与此请求一起发送的所有请求头名称的枚举； 如果请求没有请求头，则为空枚举；如果 servlet 容器不允许 servlet 使用此方法，则为 null
	 */
    @Override
    public Enumeration<String> getHeaderNames() {
        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(new GetHeaderNamesPrivilegedAction());
        } else {
            return httpRequest.getHeaderNames();
        }
    }


	/**
	 * 以 int 形式返回指定请求头的值。 如果请求没有指定名称的请求头，则此方法返回 -1。 如果请求头值无法转换为整数，则此方法将引发 NumberFormatException。
	 * <p>
	 * 请求头名称不区分大小写。
	 * 
	 * @param name - 一个字符串，指定请求头的名称
	 * @return 表示请求头值的整数。如果请求没有指定名称的请求头，则此方法返回 -1
	 */
    @Override
    public int getIntHeader(String name) {
        return httpRequest.getIntHeader(name);
    }


    /**
     * 返回调用此 HttpServletRequest 的 HttpServlet 的 HttpServletMapping。结果中未指示任何适用的 javax.servlet.Filters 的映射。
     * 如果当前活动的 javax.servlet.Servlet 调用是通过调用 ServletRequest.getRequestDispatcher 然后调用 RequestDispatcher.forward 获得的，
     * 则返回的 HttpServletMapping 与用于获取 RequestDispatcher 的路径相对应。
     * 如果当前活动的 Servlet 调用是通过调用 ServletRequest.getRequestDispatcher 然后调用 RequestDispatcher.include 获得的，
     * 则返回的 HttpServletMapping 是与导致调用序列中的第一个 Servlet 被调用的路径相对应的那个。
     * 如果当前活动的 Servlet 调用是通过调用 javax.servlet.AsyncContext.dispatch 获得的，
     * 则返回的 HttpServletMapping 与导致调用序列中的第一个 Servlet 被调用的路径相对应。
     * <p>
     * 如果当前活动的 Servlet 调用是通过调用 javax.servlet.ServletContext.getNamedDispatcher 获得的，
     * 则返回的 HttpServletMapping 是与上次应用于此请求的映射的路径相对应的映射。
     * <p>
     * 有关与 HttpServletMapping 相关的其他请求属性，详见参阅。
     * 
     * @return 描述当前请求被调用方式的 HttpServletMapping 实例。
     * 
     * @see javax.servlet.RequestDispatcher#FORWARD_MAPPING
     * @see javax.servlet.RequestDispatcher#INCLUDE_MAPPING
     * @see javax.servlet.AsyncContext#ASYNC_MAPPING
     */
    @Override
    public HttpServletMapping getHttpServletMapping() {
        return httpRequest.getHttpServletMapping();
    }

    
	/**
	 * 返回发出此请求的 HTTP 方法的名称，例如 GET、POST 或 PUT。与 CGI 变量 REQUEST_METHOD 的值相同。
	 * 
	 * @return 一个字符串，指定发出此请求的方法的名称
	 */
    @Override
    public String getMethod() {
        return httpRequest.getMethod();
    }


    /**
	 * 返回与客户端发出此请求时发送的 URL 关联的任何额外路径信息。额外路径信息在 servlet 路径之后，但在查询字符串之前，并以“/”字符开头。即为uri。
	 * <p>
	 * 如果没有额外的路径信息，此方法返回 null。
	 * <p>
	 * 与 CGI 变量 PATH_INFO 的值相同。
	 * 
	 * @return 一个字符串，由 web 容器解码，指定请求 URL 中 servlet 路径之后但查询字符串之前的额外路径信息；如果 URL 没有任何额外路径信息，则为 null
	 */
    @Override
    public String getPathInfo() {
        return httpRequest.getPathInfo();
    }


	/**
	 * 返回 servlet 名称之后但查询字符串之前的任何额外路径信息，并将其转换为真实路径。 与 CGI 变量 PATH_TRANSLATED 的值相同。
	 * <p>
	 * 如果 URL 没有任何额外的路径信息，则此方法返回 null 或 servlet 容器由于任何原因（例如从存档执行 Web 应用程序时）
	 * 无法将虚拟路径转换为真实路径。Web 容器不解码这个字符串。
	 * 
	 * @return 指定实际路径的字符串，如果 URL 没有任何额外的路径信息，则为 null
	 */
    @Override
    public String getPathTranslated() {
        return httpRequest.getPathTranslated();
    }


	/**
	 * 返回请求 URI 中指示请求上下文的部分。 上下文路径总是出现在 requestURI 中。 路径以“/”字符开头，但不以“/”字符结尾。 
	 * 对于默认（根）上下文中的 servlet，此方法返回“”。 容器不解码此字符串。
	 * <p>
	 * 一个 servlet 容器可能通过多个上下文路径匹配一个上下文。 
	 * 在这种情况下，此方法将返回请求使用的实际上下文路径，它可能与 javax.servlet.ServletContext.getContextPath() 方法返回的路径不同。 
	 * javax.servlet.ServletContext.getContextPath() 返回的上下文路径应该是 被视为应用程序的主要或首选上下文路径。
	 * 
	 * @return 一个字符串，指定请求 URI 中指示请求上下文的部分
	 */
    @Override
    public String getContextPath() {
        return httpRequest.getContextPath();
    }


	/**
	 * 返回路径后面的 requestURL 中包含的查询字符串。 如果 URL 没有查询字符串，则此方法返回 null。 与 CGI 变量 QUERY_STRING 的值相同。
	 * 
	 * @return 一个包含查询字符串的字符串，如果 URL 不包含查询字符串，则返回 null。 该值未被容器解码。
	 */
    @Override
    public String getQueryString() {
        return httpRequest.getQueryString();
    }


	/**
	 * 如果用户已通过身份验证，则返回发出此请求的用户的登录名；如果用户尚未通过身份验证，则返回 null。
	 * 是否随每个后续请求发送用户名取决于浏览器和身份验证类型。 与 CGI 变量 REMOTE_USER 的值相同。
	 */
    @Override
    public String getRemoteUser() {
        return httpRequest.getRemoteUser();
    }


    /**
	 * 返回一个布尔值，指示通过身份验证的用户是否包含在指定的逻辑“角色”中。可以使用部署描述符定义角色和角色成员关系。如果用户没有通过身份验证，该方法返回false。
	 * <p>
	 * 在调用isUserInRole时，角色名“*”绝对不能用作参数。使用"*"调用isUserInRole必须返回false。
	 * 如果要测试的安全角色的role-name为“**”，并且应用程序没有声明一个role-name为“**”的应用安全角色，
	 * 那么isUserInRole必须只在用户通过认证的情况下返回true;也就是说，只有当getRemoteUser和getUserPrincipal都返回非空值时。
	 * 否则，容器必须检查用户在应用程序角色中的成员资格。
	 * 
	 * @param role - 指定角色名称的字符串
	 * @return 一个布尔值，指示发出此请求的用户是否属于给定角色;如果用户没有被验证，则为false
	 */
    @Override
    public boolean isUserInRole(String role) {
        return httpRequest.isUserInRole(role);
    }


	/**
	 * 返回一个java.security.Principal对象，该对象包含当前通过身份验证的用户的名称。如果用户没有通过身份验证，该方法返回null。
	 * 
	 * @return principal包含发出请求的用户名;如果用户没有通过身份验证，则为Null
	 */
    @Override
    public java.security.Principal getUserPrincipal() {
        return httpRequest.getUserPrincipal();
    }


	/**
	 * 返回客户端指定的会话ID。这可能与此请求的当前有效会话的ID不相同。如果客户端没有指定会话ID，这个方法返回null。
	 * 
	 * @return 指定sessionID的String，如果请求没有指定会话ID，则为null
	 */
    @Override
    public String getRequestedSessionId() {
        return httpRequest.getRequestedSessionId();
    }


	/**
	 * 返回此请求URL中从协议名称到HTTP请求第一行的查询字符串的部分。web容器不解码此字符串。例如：
	 * <table summary="Examples of Returned Values">
     * 		<tr>
     * 			<th width='250px'>First line of HTTP request</th>
     * 			<th>Returned Value</th>
     * 		</tr>
     * 		<tr>
     * 			<td>POST /some/path.html HTTP/1.1<td>/some/path.html</td>
     * 		</tr>
     *  	<tr>
     *  		<td>GET http://foo.bar/a.html HTTP/1.0<td>/a.html</td>
     * 		</tr>>
     *  	<tr>
     *  		<td>HEAD /xyz?a=b HTTP/1.1<td>/xyz</td>
     * 		</tr>>
     * </table>
     * 
     * @return 包含从协议名称到查询字符串的部分URL的字符串
	 */
    @Override
    public String getRequestURI() {
        return httpRequest.getRequestURI();
    }


	/**
	 * 重构客户端用于发出请求的 URL。返回的 URL 包含协议、服务器名称、端口号和服务器路径，但不包含查询字符串参数。
	 * <p>
	 * 如果此请求已使用 javax.servlet.RequestDispatcher.forward 转发，
	 * 则重新构造的 URL 中的服务器路径必须反映用于获取 RequestDispatcher 的路径，而不是客户端指定的服务器路径。
	 * <p>
	 * 由于此方法返回的是 StringBuffer，而不是字符串，因此可以轻松修改 URL，例如附加查询参数。
	 * <p>
	 * 此方法对于创建重定向消息和报告错误很有用。
	 * 
	 * @return 包含重构 URL 的 StringBuffer 对象
	 */
    @Override
    public StringBuffer getRequestURL() {
        return httpRequest.getRequestURL();
    }


	/**
	 * 返回此请求的 URL 中调用 servlet 的部分。 此路径以“/”字符开头，包括 servlet 名称或 servlet 路径，
	 * 但不包括任何额外的路径信息或查询字符串。 与 CGI 变量 SCRIPT_NAME 的值相同。
	 * <p>
	 * 如果用于处理此请求的 servlet 使用“/*”模式匹配，则此方法将返回一个空字符串 ("")。
	 * 
	 * @return 一个字符串，其中包含被调用的 servlet 的名称或路径，如请求 URL 中所指定，已解码，或者如果用于处理请求的 servlet 使用“/*”模式匹配，则为空字符串。
	 */
    @Override
    public String getServletPath() {
        return httpRequest.getServletPath();
    }


    /**
	 * 返回与此请求关联的当前 HttpSession，或者，如果没有当前会话并且 create 为 true，则返回一个新会话。
	 * <p>
	 * 如果 create 为 false 并且请求没有有效的 HttpSession，则此方法返回 null。
	 * <p>
	 * 为了确保会话得到正确维护，您必须在提交响应之前调用此方法。 
	 * 如果容器使用 cookie 来维护会话完整性，并且在提交响应时被要求创建新会话，则会引发 IllegalStateException。
	 * 
	 * @param create - 如有必要，为该请求创建一个新会话； 如果没有当前会话，则返回 null
	 * @return 与此请求关联的 HttpSession 或 null 如果 create 为 false 并且请求没有有效会话
	 */
    @Override
    public HttpSession getSession(boolean create) {
        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.
                doPrivileged(new GetSessionPrivilegedAction(create));
        } else {
            return httpRequest.getSession(create);
        }
    }

    
	/**
	 * 返回与此请求关联的当前会话，或者如果请求没有会话，则创建一个。
	 * 
	 * @return 与此请求关联的 HttpSession
	 */
    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    
	/**
	 * 更改与此请求关联的当前会话的会话 ID，并返回新的会话 ID。
	 * 
	 * @return 新的会话 ID
	 */
    @Override
    public String changeSessionId() {
        return httpRequest.changeSessionId();
    }


    /**
	 * 检查请求的会话 ID 是否仍然有效。
	 * <p>
	 * 如果客户端未指定任何会话 ID，则此方法返回 false。
	 * 
	 * @return 如果此请求在当前会话上下文中具有有效会话的 ID，则为 true； 否则为假
	 */
    @Override
    public boolean isRequestedSessionIdValid() {
        return httpRequest.isRequestedSessionIdValid();
    }


	/**
	 * 检查请求的会话 ID 是否作为 HTTP cookie 传送到服务器。
	 * 
	 * @return 如果会话 ID 通过 HTTPcookie 传递给服务器，则为 true； 否则为false。
	 */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return httpRequest.isRequestedSessionIdFromCookie();
    }


	/**
	 * 检查请求的会话 ID 是否作为请求 URL 的一部分传送给服务器。
	 * 
	 * @return 如果会话 ID 作为 URL 的一部分传送给服务器，则为 true； 否则为false。
	 */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return httpRequest.isRequestedSessionIdFromURL();
    }


	/**
	 * 检查请求的会话 ID 是否作为请求 URL 的一部分传送给服务器。
	 * 
	 * @return 如果会话 ID 作为 URL 的一部分传送给服务器，则为 true； 否则为false。
	 */
    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return httpRequest.isRequestedSessionIdFromURL();
    }


	/**
	 * 返回接收请求的接口的 Internet 协议 (IP) 地址。
	 * @return 包含接收请求的 IP 地址的字符串。
	 */
    @Override
    public String getLocalAddr() {
        return httpRequest.getLocalAddr();
    }


	/**
	 * 返回接收请求的 Internet 协议 (IP) 接口的主机名。
	 * @return 一个字符串，其中包含接收请求的 IP 的主机名。
	 */
    @Override
    public String getLocalName() {
        return httpRequest.getLocalName();
    }


	/**
	 * 返回接收请求的接口的 Internet 协议 (IP) 端口号。
	 * 
	 * @return 指定端口号的整数
	 */
    @Override
    public int getLocalPort() {
        return httpRequest.getLocalPort();
    }


	/**
	 * 返回发送请求的客户端或最后一个代理的 Internet 协议 (IP) 源端口。
	 * 
	 * @return 指定端口号的整数
	 */
    @Override
    public int getRemotePort() {
        return httpRequest.getRemotePort();
    }


	/**
	 * 获取此 ServletRequest 上次分派到的 servlet 上下文。
	 * 
	 * @return 此 ServletRequest 上次分派到的 servlet 上下文
	 */
    @Override
    public ServletContext getServletContext() {
        return httpRequest.getServletContext();
    }


	/**
	 * 将此请求置于异步模式，并使用原始（未包装的）ServletRequest 和 ServletResponse 对象初始化其 AsyncContext。
	 * <p>
	 * 调用此方法将导致关联响应的提交延迟，直到在返回的 AsyncContext 上调用 AsyncContext.complete，或者异步操作已超时。
	 * <p>
	 * 在返回的 AsyncContext 上调用 AsyncContext.hasOriginalRequestAndResponse() 将返回 true。在此请求进入异步模式后在出站方向上调用的任何过滤器都可以将此用作指示它们在入站调用期间添加的任何请求和/或响应包装器在异步操作期间不需要停留，因此它们的任何相关资源可能会被释放。
	 * <p>
	 * 在调用每个 AsyncListener 的 onStartAsync 方法后，此方法清除使用先前调用其中一个 startAsync 方法返回的 AsyncContext 注册的 AsyncListener 实例列表（如果有）。
	 * <p>
	 * 此方法或其重载变量的后续调用将返回相同的 AsyncContext 实例，并根据需要重新初始化。
	 * 
	 * @return （重新）初始化的 AsyncContext
	 * @throws IllegalStateException - 如果此请求在不支持异步操作的过滤器或 servlet 的范围内（即 isAsyncSupported 返回 false），
	 * 或者如果在没有任何异步调度的情况下再次调用此方法（由 AsyncContext.dispatch 方法之一产生），
	 * 则为 在任何此类调度的范围之外调用，或者在同一调度的范围内再次调用，或者如果响应已经关闭
	 */
    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return httpRequest.startAsync();
    }


	/**
	 * 将此请求置于异步模式，并使用给定的请求和响应对象初始化其 AsyncContext。
	 * <p>
	 * ServletRequest 和 ServletResponse 参数必须是相同的实例，或者包装它们的 ServletRequestWrapper 和 ServletResponseWrapper 的实例，
	 * 它们分别被传递给 Servlet 的 service 方法或 Filter 的 doFilter 方法，在其范围内调用此方法。
	 * <p>
	 * 调用此方法将导致关联响应的提交延迟，直到在返回的 AsyncContext 上调用 AsyncContext.complete，或者异步操作已超时。
	 * <p>
	 * 在返回的 AsyncContext 上调用 AsyncContext.hasOriginalRequestAndResponse() 将返回 false，
	 * 除非传入的 ServletRequest 和 ServletResponse 参数是原始参数或不携带任何应用程序提供的包装器。在此请求进入异步模式后，
	 * 在出站方向调用的任何过滤器都可以使用这表明他们在入站调用期间添加的一些请求和/或响应包装器可能需要在异步操作期间保持原位，并且可能不会释放它们相关联的资源。
	 * 在过滤器的入站调用期间应用的 ServletRequestWrapper 可能仅当用于初始化 AsyncContext 并将通过调用 AsyncContext.getRequest() 返回的给定 servletRequest 不包含所述 ServletRequestWrapper 时，
	 * 才通过过滤器的出站调用释放。 ServletResponseWrapper 实例也是如此。
	 * <p>
	 * 在调用每个 AsyncListener 的 onStartAsync 方法后，此方法清除使用先前调用其中一个 startAsync 方法返回的 AsyncContext 注册的 AsyncListener 实例列表（如果有）。
	 * <p>
	 * 此方法或其零参数变量的后续调用将返回相同的 AsyncContext 实例，并根据需要重新初始化。如果调用此方法之后调用其零参数变体，
	 * 则指定的（并且可能包装的）请求和响应对象将保持锁定在返回的 AsyncContext 中。
	 * @param servletRequest - 用于初始化 AsyncContext 的 ServletRequest
	 * @param servletResponse - 用于初始化 AsyncContext 的 ServletResponse
	 * 
	 * @throws IllegalStateException - 如果此请求在不支持异步操作的过滤器或 servlet 的范围内（即 isAsyncSupported 返回 false），
	 * 或者如果在没有任何异步调度的情况下再次调用此方法（由 AsyncContext.dispatch 方法之一产生），则为 在任何此类调度的范围之外调用，或者在同一调度的范围内再次调用，或者如果响应已经关闭
	 */
    @Override
    public AsyncContext startAsync(ServletRequest request, ServletResponse response) throws IllegalStateException {
        return this.httpRequest.startAsync(request, response);
    }


	/**
	 * 检查此请求是否已进入异步模式。
	 * <p>
	 * 通过在其上调用 startAsync 或 startAsync(ServletRequest, ServletResponse) 将 ServletRequest 置于异步模式。
	 * <p>
	 * 如果此请求被置于异步模式，则此方法返回 false，但此后已使用 AsyncContext.dispatch 方法之一调度或通过调用 AsyncContext.complete 从异步模式释放。
	 * 
	 * @return 如果此请求已进入异步模式，则为 true，否则为 false
	 */
    @Override
    public boolean isAsyncStarted() {
        return httpRequest.isAsyncStarted();
    }


	/**
	 * 检查此请求是否支持异步操作。
	 * <p>
	 * 如果此请求在部署描述符中未注释或标记为能够支持异步处理的过滤器或 servlet 的范围内，则此请求的异步操作被禁用。
	 * 
	 * @return 如果此请求支持异步操作，则为 true，否则为 false
	 */
    @Override
    public boolean isAsyncSupported() {
        return httpRequest.isAsyncSupported();
    }


	/**
	 * 获取由最近调用此请求的 startAsync 或 startAsync(ServletRequest, ServletResponse) 创建或重新初始化的 AsyncContext。
	 * 
	 * @return 在此请求上最近调用 startAsync 或 startAsync(ServletRequest, ServletResponse) 创建或重新初始化的 AsyncContext
	 */
    @Override
    public AsyncContext getAsyncContext() {
        return httpRequest.getAsyncContext();
    }


	/**
	 * 获取此请求的调度程序类型。
	 * <p>
	 * 容器使用请求的调度器类型来选择需要应用于请求的过滤器：只有匹配调度器类型和 url 模式的过滤器才会被应用。
	 * <p>
	 * 允许为多个调度程序类型配置的过滤器查询其调度程序类型的请求允许过滤器根据其调度程序类型以不同方式处理请求。
	 * <p>
	 * 请求的初始调度程序类型定义为 DispatcherType.REQUEST。
	 * 通过 RequestDispatcher.forward(ServletRequest, ServletResponse) 或 RequestDispatcher.include(ServletRequest, ServletResponse) 调度的请求的
	 * 调度程序类型分别以 ​​DispatcherType.FORWARD 或 DispatcherType.INCLUDE 给出，而异步请求的调度程序类型通过 AsyncContext 调度。调度方法以 DispatcherType.ASYNC 形式给出。
	 * 最后，由容器的错误处理机制分派到错误页面的请求的分派器类型为 DispatcherType.ERROR。
	 */
    @Override
    public DispatcherType getDispatcherType() {
        return httpRequest.getDispatcherType();
    }


	/**
	 * 使用为 ServletContext 配置的容器登录机制来验证发出此请求的用户。
	 * <p>
	 * 此方法可能会修改并提交参数 HttpServletResponse。
	 * 
	 * @param response  - 与此 HttpServletRequest 关联的 HttpServletResponse
	 * @return 当getUserPrincipal、getRemoteUser和getAuthType返回的值为或已经确定为非null值时，为true。
	 * 如果认证不完整且底层登录机制已经提交，则返回false，在响应中，将返回给用户的消息(例如，挑战)和HTTP状态码。
	 * 
	 * @throws IOException - 如果在读取此请求或写入给定响应时发生输入或输出错误
	 * @throws ServletException - 如果身份验证失败并且调用者负责处理错误（即，底层登录机制没有建立要返回给用户的消息和 HTTP 状态代码）
	 */
    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return httpRequest.authenticate(response);
    }

    
	/**
	 * 在为 ServletContext 配置的 Web 容器登录机制使用的密码验证域中验证提供的用户名和密码。
	 * <p>
	 * 当为 ServletContext 配置的机制支持用户名密码验证时，并且在调用 login 时，请求调用者的身份尚未建立（即 getUserPrincipal、getRemoteUser 和 getAuthType 返回 null)，并且当提供的凭据验证成功时。
	 * 否则，此方法将引发 ServletException，如下所述。
	 * <p>
	 * 当此方法返回而不抛出异常时，它必须已确定非空值作为 getUserPrincipal、getRemoteUser 和 getAuthType 返回的值。
	 * @param username - 用户登录标识对应的 String 值
	 * @param password - 与已识别用户对应的密码字符串
	 * @throws ServletException - 如果配置的登录机制不支持用户名密码身份验证，或者如果已经建立了非空调用者身份（在调用登录之前），或者如果提供的用户名和密码的验证失败。
	 */
    @Override
    public void login(String username, String password) throws ServletException {
        httpRequest.login(username, password);
    }

    
	/**
	 * 建立null作为请求调用getUserPrincipal、getRemoteUser和getAuthType时返回的值。
	 * 
	 * @throws ServletException - 如果注销失败
	 */
    @Override
    public void logout() throws ServletException {
        httpRequest.logout();
    }

    
	/**
	 * 获取此请求的所有 Part 组件，前提是它的类型为 multipart/form-data。
	 * <p>
	 * 如果此请求是 multipart/form-data 类型，但不包含任何 Part 组件，则返回的 Collection 将为空。
	 * <p>
	 * 对返回的 Collection 的任何更改都不得影响此 HttpServletRequest。
	 * 
	 * @return 此请求的 Part 组件的（可能为空）集合
	 * @throws IOException - 如果在检索此请求的 Part 组件期间发生 I/O 错误
	 * @throws ServletException - 如果此请求不是 multipart/form-data 类型
	 * 
	 * @see javax.servlet.annotation.MultipartConfig#maxFileSize
	 * @see javax.servlet.annotation.MultipartConfig#maxRequestSize
	 */
    @Override
    public Collection<Part> getParts() throws IllegalStateException, IOException, ServletException {
        return httpRequest.getParts();
    }


	/**
	 * 获取具有给定名称的 Part。
	 * 
	 * @param name - 被请求 Part 的名称
	 * @return 具有给定名称的 Part，如果此请求的类型为 multipart/form-data，但不包含请求的部件，则为 null
	 * @throws IOException      - 如果在检索此请求的 Part 组件期间发生 I/O 错误
	 * @throws ServletException - 如果此请求不是 multipart/form-data 类型
	 * 
	 * @see javax.servlet.annotation.MultipartConfig#maxFileSize
	 * @see javax.servlet.annotation.MultipartConfig#maxRequestSize
	 */
    @Override
    public Part getPart(String name) throws IllegalStateException, IOException, ServletException {
        return httpRequest.getPart(name);
    }


	/**
	 * @return 如果允许 TRACE 方法，则为 true。 默认值为false.
	 */
    public boolean getAllowTrace() {
        return httpRequest.getConnector().getAllowTrace();
    }


	/**
	 * 返回请求正文的长度（以字节为单位）并由输入流提供，如果长度未知，则返回 -1。 对于 HTTP servlet，与 CGI 变量 CONTENT_LENGTH 的值相同。
	 * 
	 * @return 包含请求正文长度的 long 或 -1L 如果长度未知
	 */
    @Override
    public long getContentLengthLong() {
        return httpRequest.getContentLengthLong();
    }


	/**
	 * 为给定类创建 HttpUpgradeHandler 的实例，并将其用于 http 协议升级处理。
	 * 
	 * @param <T> - 继承handlerClass的HttpUpgradeHandler的类
	 * @param handlerClass - 用于升级的 HttpUpgradeHandler 类。
	 * @return HttpUpgradeHandler 的一个实例
	 * @throws IOException - 如果在升级过程中发生 I/O 错误
	 * @throws ServletException - 如果给定的 handlerClass 无法实例化
	 * 
	 * @see javax.servlet.http.HttpUpgradeHandler
	 * @see javax.servlet.http.WebConnection
	 */
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) throws java.io.IOException, ServletException {
        return httpRequest.upgrade(httpUpgradeHandlerClass);
    }


    /**
     * 实例化 PushBuilder 的新实例，用于从当前请求发出服务器推送响应。 
     * 如果当前连接不支持服务器推送，或者客户端通过 SETTINGS_ENABLE_PUSH 设置帧值 0（零）禁用了服务器推送，则此方法返回 null。
     */
    @Override
    public PushBuilder newPushBuilder() {
//    	return newPushBuilder(this);
    	return null;
    }



    @Override
    public boolean isTrailerFieldsReady() {
        return httpRequest.isTrailerFieldsReady();
    }


    @Override
    public Map<String, String> getTrailerFields() {
        return httpRequest.getTrailerFields();
    }
    
    
	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
	private final class GetAttributePrivilegedAction implements PrivilegedAction<Enumeration<String>> {
		@Override
		public Enumeration<String> run() {
			return httpRequest.getAttributeNames();
		}
	}

	private final class GetParameterMapPrivilegedAction implements PrivilegedAction<Map<String, String[]>> {
		@Override
		public Map<String, String[]> run() {
			return httpRequest.getParameterMap();
		}
	}

	private final class GetRequestDispatcherPrivilegedAction implements PrivilegedAction<RequestDispatcher> {
		private final String path;

		public GetRequestDispatcherPrivilegedAction(String path) {
			this.path = path;
		}

		@Override
		public RequestDispatcher run() {
			return httpRequest.getRequestDispatcher(path);
		}
	}

	private final class GetParameterPrivilegedAction implements PrivilegedAction<String> {
		public String name;

		public GetParameterPrivilegedAction(String name) {
			this.name = name;
		}

		@Override
		public String run() {
			return httpRequest.getParameter(name);
		}
	}

	private final class GetParameterNamesPrivilegedAction implements PrivilegedAction<Enumeration<String>> {
		@Override
		public Enumeration<String> run() {
			return httpRequest.getParameterNames();
		}
	}

	private final class GetParameterValuePrivilegedAction implements PrivilegedAction<String[]> {
		public String name;

		public GetParameterValuePrivilegedAction(String name) {
			this.name = name;
		}

		@Override
		public String[] run() {
			return httpRequest.getParameterValues(name);
		}
	}

	private final class GetCookiesPrivilegedAction implements PrivilegedAction<Cookie[]> {
		@Override
		public Cookie[] run() {
			return httpRequest.getCookies();
		}
	}

	private final class GetCharacterEncodingPrivilegedAction implements PrivilegedAction<String> {
		@Override
		public String run() {
			return httpRequest.getCharacterEncoding();
		}
	}

	private final class GetHeadersPrivilegedAction implements PrivilegedAction<Enumeration<String>> {
		private final String name;

		public GetHeadersPrivilegedAction(String name) {
			this.name = name;
		}

		@Override
		public Enumeration<String> run() {
			return httpRequest.getHeaders(name);
		}
	}

	private final class GetHeaderNamesPrivilegedAction implements PrivilegedAction<Enumeration<String>> {
		@Override
		public Enumeration<String> run() {
			return httpRequest.getHeaderNames();
		}
	}

	private final class GetLocalePrivilegedAction implements PrivilegedAction<Locale> {
		@Override
		public Locale run() {
			return httpRequest.getLocale();
		}
	}

	private final class GetLocalesPrivilegedAction implements PrivilegedAction<Enumeration<Locale>> {
		@Override
		public Enumeration<Locale> run() {
			return httpRequest.getLocales();
		}
	}

	private final class GetSessionPrivilegedAction implements PrivilegedAction<HttpSession> {
		private final boolean create;

		public GetSessionPrivilegedAction(boolean create) {
			this.create = create;
		}

		@Override
		public HttpSession run() {
			return httpRequest.getSession(create);
		}
	}
}
