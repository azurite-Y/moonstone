package org.zy.moonStone.core.connector;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.http.AbstractHttp11Protocol;
import org.zy.moonStone.core.http.AbstractProtocol;
import org.zy.moonStone.core.interfaces.connector.Adapter;
import org.zy.moonStone.core.interfaces.connector.ProtocolHandler;
import org.zy.moonStone.core.interfaces.connector.UpgradeProtocol;
import org.zy.moonStone.core.interfaces.container.Service;

/**
 * @dateTime 2022年1月7日;
 * @author zy(azurite-Y);
 * @description
 */
public class Connector extends LifecycleBase {
	public static final boolean RECYCLE_FACADES = Globals.RECYCLE_FACADES;
	public static final String INTERNAL_EXECUTOR_NAME = "Internal";

	/**
	 * 关联的 <code>Service</code> （如果有）.
	 */
	protected Service service = null;

	/**
	 * 是否允许 TRACE
	 */
	protected boolean allowTrace = false;

	/**
	 * 异步请求的默认超时 (ms).
	 */
	protected long asyncTimeout = 30000;

	/**
	 * 此连接器的“启用 DNS 查找”标志.
	 */
	protected boolean enableLookups = false;

	/**
	 * 是否启用/禁用了 X-Powered-By 响应标头的生成
	 */
	protected boolean xpoweredBy = false;

	/**
	 * 服务器名，我们应该假装对这个connector的请求被定向到该服务器名。
	 * 当在代理服务器后面操作Tomcat时，这是很有用的，这样重定向就可以准确地构建。
	 * 如果未指定，则使用 <code>Host</code> 头中包含的服务器名称.
	 */
	protected String proxyName = null;

	/**
	 * 服务器端口，我们应该假装对该connector的请求被定向到该端口。
	 * 当在代理服务器后面操作Tomcat时，这是很有用的，这样重定向就可以准确地构建。
	 * 如果未指定，则使用 <code>port</code> 属性指定的端口号。
	 */
	protected int proxyPort = 0;

	/**
	 * 非 SSL 到 SSL 重定向的重定向端口
	 */
	protected int redirectPort = 443;

	/**
	 * 将在通过此连接器收到的所有请求上设置的请求方案
	 */
	protected String scheme = "http";

	/**
	 * 将在通过此连接器收到的所有请求上设置的安全连接标志
	 */
	protected boolean secure = false;

	/**
	 * 请求允许的最大 cookie 数。 使用小于零的值表示无限制。 默认为 200
	 */
	private int maxCookieCount = 200;

	/**
	 * 容器将自动解析的最大参数数（GET 加 POST）。 默认为 10000。 小于 0 的值表示没有限制
	 */
	protected int maxParameterCount = 10000;

	/**
	 * 将由容器自动解析的 POST 的最大大小。 默认为 20MB
	 */
	protected int maxPostSize = 20 * 1024 * 1024;

	/**
	 * 身份验证期间容器将保存的 POST 的最大大小。 默认为 4kB
	 */
	protected int maxSavePostSize = 4 * 1024;

	/**
	 * 将根据应用程序/x-www-form-urlencoded请求体的POST样式规则分析的HTTP方法的逗号分隔列表
	 */
	protected String parseBodyMethods = "POST";

	/**
	 * 由 {@link #parseBodyMethods} 决定的一组方法, 指示需要解析请求体数据的Http Method
	 */
	protected HashSet<String> parseBodyMethodsSet;

	/**
	 * 使用基于ip的虚拟主机的标志
	 */
	protected boolean useIPVHosts = false;

	/**
	 * ProtocolHandler 实现类全限定类名
	 */
	protected final String protocolHandlerClassName;

	/**
	 * ProtocolHandler 实现类实例
	 */
	protected final AbstractProtocol<?> protocolHandler;

	/**
	 * Coyote adapter.
	 */
	protected Adapter adapter = null;

	/**
	 * uri解码字符集
	 */
	private Charset uriCharset = StandardCharsets.UTF_8;

	/**
	 * URI 编码为正文
	 */
	protected boolean useBodyEncodingForURI = false;

	// ------------------------------------------------------------------ 构造器 ------------------------------------------------------------------
	/**
	 * 默认使用 HTTP/1.1 NIO 实现
	 */
	public Connector() {
		this("org.zy.moonStone.core.http11.Http11NioProtocol");
	}

	public Connector(String protocol) {
		if ("HTTP/1.1".equals(protocol) || protocol == null) {
			protocolHandlerClassName = "org.zy.moonStone.core.http.Http11NioProtocol";
		} else {
			protocolHandlerClassName = protocol;
		}

		AbstractProtocol<?> p = null;
		try {
			Class<?> clazz = Class.forName(protocolHandlerClassName);
			p = (AbstractProtocol<?>) clazz.getConstructor().newInstance();
		} catch (Exception e) {
			logger.error("ProtocolHandler 实现实例化失败", e);
		} finally {
			this.protocolHandler = p;
		}

		setThrowOnFailure(Boolean.getBoolean(Globals.THROW_ON_FAILURE));
	}

	// ------------------------------------------------------------------ getter、setter ------------------------------------------------------------------
	/**
	 * @return 关联的服务（如果有）
	 */
	public Service getService() {
		return this.service;
	}
	/**
	 * 设置关联的服务（如果有）
	 *
	 * @param service - 拥有此Engine的Service
	 */
	public void setService(Service service) {
		this.service = service;
	}

	/**
	 * @return 如果允许 TRACE 方法，则为 true。 默认值为false.
	 */
	public boolean getAllowTrace() {
		return this.allowTrace;
	}
	/**
	 * 设置 allowTrace 标志，以禁用或启用 HTTP Trace 方法.
	 * @param allowTrace - 新的 allowTrace 标志
	 */
	public void setAllowTrace(boolean allowTrace) {
		this.allowTrace = allowTrace;
	}

	/**
	 * @return 以毫秒为单位的异步请求的默认超时.
	 */
	public long getAsyncTimeout() {
		return asyncTimeout;
	}
	/**
	 * 设置异步请求的默认超时
	 * @param asyncTimeout - 新的以毫秒为单位的超时时间
	 */
	public void setAsyncTimeout(long asyncTimeout) {
		this.asyncTimeout= asyncTimeout;
	}

	/**
	 * @return “启用 DNS 查找”标志
	 */
	public boolean getEnableLookups() {
		return this.enableLookups;
	}
	/**
	 * 设置“启用 DNS 查找”标志
	 * @param enableLookups - 新的“启用 DNS 查找”标志值
	 */
	public void setEnableLookups(boolean enableLookups) {
		this.enableLookups = enableLookups;
	}

	public int getMaxCookieCount() {
		return maxCookieCount;
	}
	public void setMaxCookieCount(int maxCookieCount) {
		this.maxCookieCount = maxCookieCount;
	}

	/**
	 * @return 容器将自动解析的最大参数数量(GET + POST)。值小于0意味着没有限制
	 */
	public int getMaxParameterCount() {
		return maxParameterCount;
	}
	/**
	 * 设置容器将自动解析的最大参数数量(GET + POST)。值小于0意味着没有限制.
	 */
	public void setMaxParameterCount(int maxParameterCount) {
		this.maxParameterCount = maxParameterCount;
	}

	/**
	 * @return 将被容器自动解析的POST的最大大小.
	 */
	public int getMaxPostSize() {
		return maxPostSize;
	}
	/**
	 * 设置将被容器自动解析的POST的最大大小.
	 * @param maxPostSize 新的将被容器自动解析的POST的最大大小(以字节为单位)
	 */
	public void setMaxPostSize(int maxPostSize) {
		this.maxPostSize = maxPostSize;
	}

	/**
	 * @return 容器在身份验证期间保存的POST文件的最大大小.
	 */
	public int getMaxSavePostSize() {
		return maxSavePostSize;
	}
	/**
	 * 设置容器在身份验证期间保存的POST文件的最大大小.
	 *
	 * @param maxSavePostSize - 身份验证期间容器将保存的POST的新最大大小（以字节为单位）
	 */
	public void setMaxSavePostSize(int maxSavePostSize) {
		this.maxSavePostSize = maxSavePostSize;
	}

	/**
	 * @return 支持正文参数解析的 HTTP 方法
	 */
	public String getParseBodyMethods() {
		return this.parseBodyMethods;
	}
	/**
	 * 设置应该允许正文参数解析的 HTTP 方法列表。 这默认为 <code>POST</code>
	 * @param methods - 逗号分隔的 HTTP 方法名称列表
	 */
	public void setParseBodyMethods(String methods) {
		HashSet<String> methodSet = new HashSet<>();

		if (null != methods) {
			methodSet.addAll(Arrays.asList(methods.split("\\s*,\\s*")));
		}

		if (methodSet.contains("TRACE")) {
			throw new IllegalArgumentException("Trace 方法不能解析body");
		}

		this.parseBodyMethods = methods;
		this.parseBodyMethodsSet = methodSet;
	}

	/**
	 * 判断是否需要解析请求体数据
	 * 
	 * @param method - 需判断的请求
	 * @return 为 true, 则需解析请求体数据
	 */
	protected boolean isParseBodyMethod(String method) {
		return parseBodyMethodsSet.contains(method);
	}

	public boolean isSSLEnabled() {
		if (protocolHandler instanceof AbstractHttp11Protocol<?>) {
			return ((AbstractHttp11Protocol<?>) protocolHandler).isSSLEnabled();
		}
		return false;
	}
	
	public void setBindOnInit(boolean bindOnInit) {
		if (protocolHandler instanceof AbstractProtocol<?>) {
			((AbstractProtocol<?>) protocolHandler).setBindOnInit(bindOnInit);
		}
	}
	
	/**
	 * @return 此连接器配置为侦听请求的端口号。 特殊值 0 表示在绑定套接字时选择一个随机的空闲端口。
	 */
	public int getPort() {
		if (protocolHandler instanceof AbstractProtocol<?>) {
			return ((AbstractProtocol<?>) protocolHandler).getPort();
		}
		return -1;
	}
	/**
	 * 设置监听请求的端口号.
	 *
	 * @param port - 新的端口号
	 */
	public void setPort(int port) {
		this.protocolHandler.setPort(port);
	}

	public int getPortOffset() {
		return ((AbstractProtocol<?>) protocolHandler).getPortOffset();
	}
	public void setPortOffset(int portOffset) {
		protocolHandler.setPortOffset(portOffset);
	}

	public int getPortWithOffset() {
		int port = getPort();
		// 零是一种特殊情况，负值无效
		if (port > 0) {
			return port + getPortOffset();
		}
		return port;
	}

	/**
	 * @return 此连接器正在侦听请求的端口号。如果使用 {@link #getPort} 的特殊值为零，则此方法将报告实际端口绑定.
	 */
	public int getLocalPort() {
		return this.protocolHandler.getLocalPort();
	}

	/**
	 * @return 使用的协议处理程序
	 */
	public String getProtocol() {
		return "org.zy.moonStone.core.http.Http11NioProtocol".equals(getProtocolHandlerClassName()) ? "HTTP/1.1" : getProtocolHandlerClassName();
	}

	/**
	 * @return 当前使用的协议处理程序的类的全限定类名.
	 */
	public String getProtocolHandlerClassName() {
		return this.protocolHandlerClassName;
	}

	/**
	 * @return 与连接器相关联的协议处理程序
	 */
	public ProtocolHandler getProtocolHandler() {
		return this.protocolHandler;
	}

	/**
	 * @return 此连接器的代理服务器名称
	 */
	public String getProxyName() {
		return this.proxyName;
	}
	/**
	 * 设置此连接器的代理服务器名称
	 * @param proxyName - 新的代理服务器名称
	 */
	public void setProxyName(String proxyName) {
		if(proxyName != null && proxyName.length() > 0) {
			this.proxyName = proxyName;
		} else {
			this.proxyName = null;
		}
	}

	/**
	 * @return 此连接器的代理服务器端口
	 */
	public int getProxyPort() {
		return this.proxyPort;
	}
	/**
	 * 为此连接器设置代理服务器端口
	 * @param proxyPort - 新的代理服务器端口
	 */
	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}


	/**
	 * @return 如果请求来自非 SSL 端口并且受具有需要 SSL 的传输保证的安全约束，则该请求应重定向到的端口号.
	 */
	public int getRedirectPort() {
		return this.redirectPort;
	}
	/**
	 * 设置重定向的端口号.
	 * @param redirectPort - 重定向端口号（非 SSL 到 SSL）
	 */
	public void setRedirectPort(int redirectPort) {
		this.redirectPort = redirectPort;
	}
	
	/**
	 * @return 进行端口偏移后的重定向端口号
	 */
	public int getRedirectPortWithOffset() {
		return getRedirectPort() + getPortOffset();
	}

	/**
	 * @return 将分配给通过此连接器接收的请求的方案。 默认值为“http”.
	 */
	public String getScheme() {
		return this.scheme;
	}
	/**
	 * 设置将分配给通过此连接器接收的请求的方案.
	 * @param scheme - 新的方案
	 */
	public void setScheme(String scheme) {
		this.scheme = scheme;
	}


	/**
	 * @return 将分配给通过此连接器接收的请求的安全连接标志。 默认值为 "false".
	 */
	public boolean getSecure() {
		 return (protocolHandler instanceof AbstractHttp11Protocol<?>) ? ((AbstractHttp11Protocol<?>) protocolHandler).getSecure() : this.secure;
	}
	/**
	 * 设置将分配给通过此连接器接收的请求的安全连接标志
	 * @param secure - 新的安全连接标志
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
		
		if (protocolHandler instanceof AbstractProtocol<?>) {
            ((AbstractHttp11Protocol<?>) protocolHandler).setSecure(secure);
        }
	}

	/**
	 * @return 使用原始大小写用于 URI 的字符编码名称.
	 */
	public String getURIEncoding() {
		return uriCharset.name();
	}
	/**
	 * @return 用于将原始 URI 字节（在 %nn 解码后）转换为字符的字符集。 这永远不会为空
	 */
	public Charset getURICharset() {
		return uriCharset;
	}
	/**
	 * 设置要用于 URI 的 URI 编码
	 * @param URIEncoding - 新的 URI 字符编码
	 */
	public void setURIEncoding(String URIEncoding) {
		if (Charset.isSupported(URIEncoding)) {
			uriCharset = Charset.forName(URIEncoding);
		} else {
			logger.error("不支持的字符集编码，by URIEncoding：{}", URIEncoding);
		}
	}


	/**
	 * @return 如果正文字符集编码要应用于URI，则为true
	 */
	public boolean getUseBodyEncodingForURI() {
		return this.useBodyEncodingForURI;
	}
	/**
	 * 设置是否应将实体正文编码用于URI
	 */
	public void setUseBodyEncodingForURI(boolean useBodyEncodingForURI) {
		this.useBodyEncodingForURI = useBodyEncodingForURI;
	}

	/**
	 * 指示是否为此连接器启用或禁用为 Servlet 生成的响应生成 X-Powered-By 响应头.
	 * @return 如果启用了X-Powered-By响应头的生成，则为true，否则为false
	 */
	public boolean getXpoweredBy() {
		return xpoweredBy;
	}
	/**
	 * 对于由连接器返回的所有Servlet生成的响应，启用或禁用X-Powered-By报头的生成.
	 * @param xpoweredBy - 如果要启用 X-Powered-By 响应标头的生成，则为 true，否则为 false
	 */
	public void setXpoweredBy(boolean xpoweredBy) {
		this.xpoweredBy = xpoweredBy;
	}

	/**
	 * 启用基于 IP 的虚拟主机.
	 * @param useIPVHosts - 如果主机通过IP识别，则为true，如果主机通过名称识别，则为false
	 */
	public void setUseIPVHosts(boolean useIPVHosts) {
		this.useIPVHosts = useIPVHosts;
	}
	/**
	 * 测试是否启用了基于ip的虚拟主机
	 * @return 如果启用则为<code>true</code>
	 */
	public boolean getUseIPVHosts() {
		return useIPVHosts;
	}

	public String getExecutorName() {
		java.util.concurrent.Executor obj = protocolHandler.getExecutor();
		if (obj instanceof org.zy.moonStone.core.interfaces.connector.Executor) {
			return ((org.zy.moonStone.core.interfaces.connector.Executor) obj).getName();
		}
		return INTERNAL_EXECUTOR_NAME;
	}

//	public void addSslHostConfig(SSLHostConfig sslHostConfig) {
//		protocolHandler.addSslHostConfig(sslHostConfig);
//	}
//	public SSLHostConfig[] findSslHostConfigs() {
//		return protocolHandler.findSslHostConfigs();
//	}

	public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
		protocolHandler.addUpgradeProtocol(upgradeProtocol);
	}
	public UpgradeProtocol[] findUpgradeProtocols() {
		return protocolHandler.findUpgradeProtocols();
	}


	// --------------------------------------------------------- 公共方法 ---------------------------------------------------------
	/**
	 * 创建（或分配）并返回一个适合指定 Request 内容的 Request 对象给负责的 Container.
	 *
	 * @return 一个新的 Servlet 请求对象
	 */
	public HttpRequest createRequest() {
		return new HttpRequest(this);
	}
	
	/**
	 * 创建（或分配）并返回一个 Response 对象，该对象适合从负责的 Container 接收 Response 的内容
	 * @return 一个新的 Servlet 响应对象
	 */
	public HttpResponse createResponse() {
		return new HttpResponse();
	}

	/**
	 * 暂停连接器
	 */
	public void pause() {
		try {
			if (protocolHandler != null) {
				protocolHandler.pause();
			}
		} catch (Exception e) {
			logger.error("连接器暂停失败", e);
		}
	}

	/**
	 * 恢复连接器.
	 */
	public void resume() {
		try {
			if (protocolHandler != null) {
				protocolHandler.resume();
			}
		} catch (Exception e) {
			logger.error("连接器恢复失败", e);
		}
	}

	@Override
	protected void initInternal() throws LifecycleException {
		if (protocolHandler == null) {
			throw new LifecycleException(String.format("ProtocolHandler实例创建失败，by className：%s", getProtocolHandlerClassName()));
		}
		
		// 初始化适配器
		adapter = new MoonAdapter(this);
		protocolHandler.setAdapter(adapter);
		if (service != null) {
			protocolHandler.setUtilityExecutor(service.getServer().getUtilityExecutor());
		}

		// 确保 parseBodyMethodsSet 有一个默认值
		if (null == parseBodyMethodsSet) {
			setParseBodyMethods(getParseBodyMethods());
		}

		try {
			protocolHandler.init();
		} catch (Exception e) {
			throw new LifecycleException("ProtocolHandler实例初始化失败", e);
		}
	}

	/**
	 * 开始通过此连接器处理请求.
	 * @exception LifecycleException - 如果发生致命的启动错误
	 */
	@Override
	protected void startInternal() throws LifecycleException {
		// 在开始之前验证设置
		if (getPortWithOffset() < 0) {
			throw new LifecycleException(String.format("无效的端口号，by port %s", Integer.valueOf(getPortWithOffset())));
		}

		setState(LifecycleState.STARTING);

		try {
			protocolHandler.start();
		} catch (Exception e) {
			throw new LifecycleException("ProtocolHandler启动失败", e);
		}
	}

	/**
	 * 通过此连接器终止处理请求.
	 * @exception LifecycleException - 如果发生致命的关闭错误
	 */
	@Override
	protected void stopInternal() throws LifecycleException {
		setState(LifecycleState.STOPPING);

		try {
			if (protocolHandler != null) {
				protocolHandler.stop();
			}
		} catch (Exception e) {
			throw new LifecycleException("ProtocolHandler关闭失败", e);
		}
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
		try {
			if (protocolHandler != null) {
				protocolHandler.destroy();
			}
		} catch (Exception e) {
			throw new LifecycleException("ProtocolHandler销毁失败", e);
		}

		if (getService() != null) {
			getService().removeConnector(this);
		}
	}

	@Override
	public String toString() {
		// 无需缓存
		StringBuilder sb = new StringBuilder("Connector[");
		sb.append(getProtocol());
		sb.append('-');
		int port = getPortWithOffset();
		if (port > 0) {
			sb.append(port);
		} else {
			sb.append("auto-");
			sb.append(this.protocolHandler.getNameIndex());
		}
		sb.append(']');
		return sb.toString();
	}
}
