package org.zy.moonStone.core.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.zy.moonStone.core.Constants;
import org.zy.moonStone.core.connector.CompressionConfig;
import org.zy.moonStone.core.interfaces.connector.Processor;
import org.zy.moonStone.core.interfaces.connector.UpgradeProtocol;
import org.zy.moonStone.core.util.net.AbstractEndpoint;

/**
 * @dateTime 2022年1月11日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractHttp11Protocol<S> extends AbstractProtocol<S> {
	
    private final CompressionConfig compressionConfig = new CompressionConfig();

	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
	public AbstractHttp11Protocol(AbstractEndpoint<S,?> endpoint) {
		super(endpoint);
		setConnectionTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
		ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
		setHandler(cHandler);
		getEndpoint().setHandler(cHandler);
	}


	/**
	 * 该字段指示协议是否被视为安全的。这通常意味着正在使用https，但也可以用来在反向代理后伪造https。
	 */
	private boolean secure;
	public boolean getSecure() { return secure; }
	public void setSecure(boolean b) {
		secure = b;
	}

	@Override
	public void init() throws Exception {
		/*
		 * 必须首先配置升级协议，因为端点init（通过下面的 super.init() 触发）使用此列表来配置要播发的ALPN协议列表
		 */
		for (UpgradeProtocol upgradeProtocol : upgradeProtocols) {
			configureUpgradeProtocol(upgradeProtocol);
		}

		super.init();
	}

	@Override
	protected String getProtocolName() {
		return "Http";
	}

	/**
	 * 在此重写以使方法对嵌套类可见
	 */
	@Override
	protected AbstractEndpoint<S,?> getEndpoint() {
		return super.getEndpoint();
	}


	// -------------------------------------------------------------------------------------
	// HTTPS 特定属性 - 在ProtocolHandler中管理
	// -------------------------------------------------------------------------------------
	private boolean useKeepAliveResponseHeader = true;
    public boolean getUseKeepAliveResponseHeader() {
        return useKeepAliveResponseHeader;
    }
    public void setUseKeepAliveResponseHeader(boolean useKeepAliveResponseHeader) {
        this.useKeepAliveResponseHeader = useKeepAliveResponseHeader;
    }
    
	private String relaxedPathChars = null;
	public String getRelaxedPathChars() {
		return relaxedPathChars;
	}
	public void setRelaxedPathChars(String relaxedPathChars) {
		this.relaxedPathChars = relaxedPathChars;
	}

	private String relaxedQueryChars = null;
	public String getRelaxedQueryChars() {
		return relaxedQueryChars;
	}
	public void setRelaxedQueryChars(String relaxedQueryChars) {
		this.relaxedQueryChars = relaxedQueryChars;
	}

	private boolean allowHostHeaderMismatch = false;
	/**
	 * 如果Host头与请求行中指定的主机不一致(如果有的话)，MoonStone 会接受HTTP 1.1请求吗?
	 *
	 * @return 如果Tomcat允许此类请求，则为true，否则为false  if Tomcat will allow such requests, otherwise
	 *         {@code false}
	 */
	public boolean getAllowHostHeaderMismatch() {
		return allowHostHeaderMismatch;
	}
	/**
	 * 如果主机头与请求行中指定的主机不一致(如果有的话)，MoonStone 会接受HTTP 1.1请求吗?
	 *
	 * @param allowHostHeaderMismatch - {@code true} 表示允许这样的请求，{@code false} 表示用400拒绝它们
	 */
	public void setAllowHostHeaderMismatch(boolean allowHostHeaderMismatch) {
		this.allowHostHeaderMismatch = allowHostHeaderMismatch;
	}


	private boolean rejectIllegalHeaderName = true;
	/**
	 * 如果接收到的HTTP请求包含非法的报头名称(即报头名称不是令牌)，请求将被拒绝(使用400httpResponse)或非法的报头将被忽略。
	 *
	 * @return 如果请求将被拒绝，则为 {@code true} ;如果请求头将被忽略，则为 {@code false}
	 */
	public boolean getRejectIllegalHeaderName() { return rejectIllegalHeaderName; }
	/**
	 * 如果接收到的HTTP请求包含非法的报头名称(即报头名称不是令牌)，请求应该被拒绝(使用400 httpResponse)还是应该忽略非法的报头。
	 *
	 * @param rejectIllegalHeaderName - {@code true} 表示拒绝具有非法标头名称的请求，{@code false} 表示忽略请求头
	 */
	public void setRejectIllegalHeaderName(boolean rejectIllegalHeaderName) {
		this.rejectIllegalHeaderName = rejectIllegalHeaderName;
	}


	private int maxSavePostSize = 4 * 1024;
	/**
	 * 返回在Form或CLIENT-CERT身份验证期间将保存的POST的最大大小。
	 *
	 * @return 以字节为单位的大小
	 */
	public int getMaxSavePostSize() { return maxSavePostSize; }
	/**
	 * 设置在Form或CLIENT-CERT身份验证期间缓冲的POST的最大大小。
	 * 当收到安全约束需要客户端证书的POST时，需要在进行SSL握手以获得证书的同时缓冲POST正文。
	 * 在FDORM身份验证期间需要类似的缓冲。
	 *
	 * @param maxSavePostSize - 要缓冲的最大POST正文大小(字节)
	 */
	public void setMaxSavePostSize(int maxSavePostSize) {
		this.maxSavePostSize = maxSavePostSize;
	}

	
	/**
	 * HTTP head name 最大大小，默认值：128 kib
	 */
	private int maxHttpHeaderLineNameSize = 128;
	public int getMaxHttpHeaderNameSize() { return maxHttpHeaderLineNameSize; }
	public void setMaxHttpHeaderNameSize(int valueI) { maxHttpHeaderLineNameSize = valueI; }

	
	/**
	 * HTTP head value 最大大小，默认值：1024 kib
	 */
	private int maxHttpHeaderValueSize = 1024;
	public int getMaxHttpHeaderValueSize() { return maxHttpHeaderValueSize; }
	public void setMaxHttpHeaderValueSize(int valueI) { maxHttpHeaderValueSize = valueI; }

	
    /**
     * HTTP消息头的最大大小，默认值：8192 kib
     */
    private int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }
	
	
	private int connectionUploadTimeout = 300000;
	/**
	 * 指定数据加载期间不同的(通常较长的)连接超时。在Apache HTTPD服务器中默认为5分钟。
	 *
	 * @return 超时, 以毫秒为单位
	 */
	public int getConnectionUploadTimeout() { return connectionUploadTimeout; }
	/**
	 * 设置上传超时时间
	 *
	 * @param timeout - 上传超时(毫秒)
	 */
	public void setConnectionUploadTimeout(int timeout) {
		connectionUploadTimeout = timeout;
	}


	private boolean disableUploadTimeout = true;
	/**
	 * 获取控制上传超时的标志。如果为true，则连接上传超时将被忽略，常规套接字超时将在整个连接期间使用。
	 *
	 * @return 如果单独上传超时被禁用，则为{@code true}
	 */
	public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
	/**
	 * 设置标志来控制在上传请求体时是否使用单独的连接超时。
	 *
	 * @param isDisabled 如果应该禁用单独的上传超时，则 {@code true}
	 */
	public void setDisableUploadTimeout(boolean isDisabled) {
		disableUploadTimeout = isDisabled;
	}


	public void setCompression(String compression) {
		compressionConfig.setCompression(compression);
	}
	public String getCompression() {
		return compressionConfig.getCompression();
	}
	protected int getCompressionLevel() {
		return compressionConfig.getCompressionLevel();
	}


	public String getNoCompressionUserAgents() {
		return compressionConfig.getNoCompressionUserAgents();
	}
	protected Pattern getNoCompressionUserAgentsPattern() {
		return compressionConfig.getNoCompressionUserAgentsPattern();
	}
	public void setNoCompressionUserAgents(String noCompressionUserAgents) {
		compressionConfig.setNoCompressionUserAgents(noCompressionUserAgents);
	}


	public String getCompressibleMimeType() {
		return compressionConfig.getCompressibleMimeType();
	}
	public void setCompressibleMimeType(String valueS) {
		compressionConfig.setCompressibleMimeType(valueS);
	}
	public String[] getCompressibleMimeTypes() {
		return compressionConfig.getCompressibleMimeTypes();
	}


	public int getCompressionMinSize() {
		return compressionConfig.getCompressionMinSize();
	}
	public void setCompressionMinSize(int compressionMinSize) {
		compressionConfig.setCompressionMinSize(compressionMinSize);
	}


	public boolean useCompression(Request request, Response response) {
		return compressionConfig.useCompression(request, response);
	}


	private Pattern restrictedUserAgents = null;
	/**
	 * 获取正则表达式的字符串形式，该正则表达式定义了应该限制为HTTP/1.0支持的Useragents。
	 *
	 * @return 正则表达式作为的字符串
	 */
	public String getRestrictedUserAgents() {
		if (restrictedUserAgents == null) {
			return null;
		} else {
			return restrictedUserAgents.toString();
		}
	}
	protected Pattern getRestrictedUserAgentsPattern() {
		return restrictedUserAgents;
	}
	/**
	 * 设置受限制的用户代理列表(这会将连接器降级到HTTP/1.0模式)。{@link Pattern}支持的正则表达式。
	 *
	 * @param restrictedUserAgents - {@link Pattern}为用户代理所支持的正则表达式。例如: "gorilla|desesplorer|tigrus"
	 */
	public void setRestrictedUserAgents(String restrictedUserAgents) {
		if (restrictedUserAgents == null || restrictedUserAgents.length() == 0) {
			this.restrictedUserAgents = null;
		} else {
			this.restrictedUserAgents = Pattern.compile(restrictedUserAgents);
		}
	}


	private String server;
	public String getServer() { return server; }
	/**
	 * 设置服务器 header 名称.
	 *
	 * @param server - 用于服务器请求头的新值
	 */
	public void setServer(String server) {
		this.server = server;
	}


	private boolean serverRemoveAppProvidedValues = false;
	/**
	 * 是否应删除HTTP报文中的的"Server"请求头值。请注意，如果设置了服务器，则将覆盖任何应用程序提供的值。
	 *
	 * @return 如果应删除应用程序提供的值，则为 {@code true} ，否则为 {@code false}
	 */
	public boolean getServerRemoveAppProvidedValues() { return serverRemoveAppProvidedValues; }
	public void setServerRemoveAppProvidedValues(boolean serverRemoveAppProvidedValues) {
		this.serverRemoveAppProvidedValues = serverRemoveAppProvidedValues;
	}


	/**
	 * 分块编码中扩展信息的最大大小
	 */
	private int maxTrailerSize = 8192;
	public int getMaxTrailerSize() { return maxTrailerSize; }
	public void setMaxTrailerSize(int maxTrailerSize) {
		this.maxTrailerSize = maxTrailerSize;
	}


	/**
	 * 分块编码中扩展信息的最大大小
	 */
	private int maxExtensionSize = 8192;
	public int getMaxExtensionSize() { return maxExtensionSize; }
	public void setMaxExtensionSize(int maxExtensionSize) {
		this.maxExtensionSize = maxExtensionSize;
	}


	/**
	 * 要接受的请求正文的最大尺寸（2M）
	 */
	private int maxSwallowSize = 2 * 1024 * 1024;
	public int getMaxSwallowSize() { return maxSwallowSize; }
	public void setMaxSwallowSize(int maxSwallowSize) {
		this.maxSwallowSize = maxSwallowSize;
	}


	/**
	 * 使用分块编码时允许通过尾部发送的标头的名称。它们以小写形式存储。
	 */
	private Set<String> allowedTrailerHeaders = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	public void setAllowedTrailerHeaders(String commaSeparatedHeaders) {
		Set<String> toRemove = new HashSet<>();
		toRemove.addAll(allowedTrailerHeaders);
		if (commaSeparatedHeaders != null) {
			String[] headers = commaSeparatedHeaders.split(",");
			for (String header : headers) {
				String trimmedHeader = header.trim().toLowerCase(Locale.ENGLISH);
				if (toRemove.contains(trimmedHeader)) {
					toRemove.remove(trimmedHeader);
				} else {
					allowedTrailerHeaders.add(trimmedHeader);
				}
			}
			allowedTrailerHeaders.removeAll(toRemove);
		}
	}
	protected Set<String> getAllowedTrailerHeadersInternal() {
		return allowedTrailerHeaders;
	}
	public String getAllowedTrailerHeaders() {
		// 这些行之间大小变化的可能性很小，因此没有必要进行同步。
		String[] copy = new String[allowedTrailerHeaders.size()];
		int index = 0;
		
		for (Iterator<String> iterator = allowedTrailerHeaders.iterator(); iterator.hasNext();) {
			copy[index++] = iterator.next();
		}
		return StringUtils.join(copy);
	}
	public void addAllowedTrailerHeader(String header) {
		if (header != null) {
			allowedTrailerHeaders.add(header.trim().toLowerCase(Locale.ENGLISH));
		}
	}
	public void removeAllowedTrailerHeader(String header) {
		if (header != null) {
			allowedTrailerHeaders.remove(header.trim().toLowerCase(Locale.ENGLISH));
		}
	}


	/**
	 * 已配置的升级协议实例
	 */
	private final List<UpgradeProtocol> upgradeProtocols = new ArrayList<>();
	@Override
	public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
		upgradeProtocols.add(upgradeProtocol);
	}
	@Override
	public UpgradeProtocol[] findUpgradeProtocols() {
		return upgradeProtocols.toArray(new UpgradeProtocol[0]);
	}


	/**
	 * 可通过内部 MoonStone 支持通过HTTP升级进行访问的协议。
	 */
	private final Map<String,UpgradeProtocol> httpUpgradeProtocols = new HashMap<>();
	/**
	 * 可通过内部 MoonStone 支持通过ALPN协商进行访问的协议。
	 */
	private final Map<String,UpgradeProtocol> negotiatedProtocols = new HashMap<>();
	private void configureUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
		// HTTP Upgrade
//		String httpUpgradeName = upgradeProtocol.getHttpUpgradeName(getEndpoint().isSSLEnabled());
//		boolean httpUpgradeConfigured = false;
//		if (httpUpgradeName != null && httpUpgradeName.length() > 0) {
//			httpUpgradeProtocols.put(httpUpgradeName, upgradeProtocol);
//			httpUpgradeConfigured = true;
//			getLogger().info("Http 请求升级已配置, 升级协议: {}, 原始协议: {}", httpUpgradeName, getName());
//		}


		// ALPN
//		String alpnName = upgradeProtocol.getAlpnName();
//		if (alpnName != null && alpnName.length() > 0) {
//			if (getEndpoint().isAlpnSupported()) {
//				negotiatedProtocols.put(alpnName, upgradeProtocol);
//				getEndpoint().addNegotiatedProtocol(alpnName);
//				getLogger().info("ALPN 已配置, 当前协议: {}, 升级协议: {}", getName(), alpnName);
//			} else {
//				if (!httpUpgradeConfigured) {
//					// 此连接器不支持ALPN，升级协议实现不支持标准HTTP升级，因此无法启用对该协议的支持
//					getLogger().error("此连接器不支持ALPN，升级协议实现不支持标准HTTP升级. 升级协议实现: {}, alpnName: {}, 当前协议: {}.",
//							upgradeProtocol.getClass().getName(), alpnName, getName());
//				}
//			}
//		}
	}
	@Override
	public UpgradeProtocol getNegotiatedProtocol(String negotiatedName) {
		return negotiatedProtocols.get(negotiatedName);
	}
	@Override
	public UpgradeProtocol getUpgradeProtocol(String upgradedName) {
		return httpUpgradeProtocols.get(upgradedName);
	}

	
	public boolean getUseSendfile() { return getEndpoint().getUseSendfile(); }
	public void setUseSendfile(boolean useSendfile) { getEndpoint().setUseSendfile(useSendfile); }

	
	// -------------------------------------------------------------------------------------
	// HTTPS 特定属性 - 传递到 EndPoint
	// -------------------------------------------------------------------------------------
	public boolean isSSLEnabled() { return getEndpoint().isSSLEnabled();}
	public void setSSLEnabled(boolean SSLEnabled) {
		getEndpoint().setSSLEnabled(SSLEnabled);
	}


	/**
	 * @return 在保持连接上可以执行的最大请求数。默认值与Apache HTTPServer(100)相同。
	 */
	public int getMaxKeepAliveRequests() {
		return getEndpoint().getMaxKeepAliveRequests();
	}
	/**
	 * 设置允许的最大Keep-Alive请求数。这是为了防止DoS攻击。设置为负值将禁用该限制。
	 *
	 * @param mkar - 允许的Keep-Alive请求的新最大数量
	 */
	public void setMaxKeepAliveRequests(int mkar) {
		getEndpoint().setMaxKeepAliveRequests(mkar);
	}

	
	// -------------------------------------------------------------------------------------
	// 通用代码
	// -------------------------------------------------------------------------------------
	@Override
	protected Processor createProcessor() {
		Http11Processor processor = new Http11Processor(this, adapter);
		return processor;
	}
	

	// -------------------------------------------------------------------------------------
	// HTTPS 特定属性 - 传递到EndPoint
	// -------------------------------------------------------------------------------------
	/*
	public String getDefaultSSLHostConfigName() {
		return getEndpoint().getDefaultSSLHostConfigName();
	}
	public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
		getEndpoint().setDefaultSSLHostConfigName(defaultSSLHostConfigName);
		if (defaultSSLHostConfig != null) {
			defaultSSLHostConfig.setHostName(defaultSSLHostConfigName);
		}
	}


	@Override
	public void addSslHostConfig(SSLHostConfig sslHostConfig) {
		getEndpoint().addSslHostConfig(sslHostConfig);
	}


	@Override
	public SSLHostConfig[] findSslHostConfigs() {
		return getEndpoint().findSslHostConfigs();
	}


	public void reloadSslHostConfigs() {
		getEndpoint().reloadSslHostConfigs();
	}


	public void reloadSslHostConfig(String hostName) {
		getEndpoint().reloadSslHostConfig(hostName);
	}


	// -------------------------------------------------------------------------------------
	// HTTPS 特定属性 - 通过SSLHostConfig处理
	// -------------------------------------------------------------------------------------
	private SSLHostConfig defaultSSLHostConfig = null;
	
	private void registerDefaultSSLHostConfig() {
		if (defaultSSLHostConfig == null) {
			for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
				if (getDefaultSSLHostConfigName().equals(sslHostConfig.getHostName())) {
					defaultSSLHostConfig = sslHostConfig;
					break;
				}
			}
			if (defaultSSLHostConfig == null) {
				defaultSSLHostConfig = new SSLHostConfig();
				defaultSSLHostConfig.setHostName(getDefaultSSLHostConfigName());
				getEndpoint().addSslHostConfig(defaultSSLHostConfig);
			}
		}
	}


	// TODO: 一旦不再需要支持旧的配置属性，就可以删除所有这些SSL getter和setter。
	public String getSslEnabledProtocols() {
		registerDefaultSSLHostConfig();
		return StringUtils.join(defaultSSLHostConfig.getEnabledProtocols());
	}
	public void setSslEnabledProtocols(String enabledProtocols) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setProtocols(enabledProtocols);
	}
	public String getSSLProtocol() {
		registerDefaultSSLHostConfig();
		return StringUtils.join(defaultSSLHostConfig.getEnabledProtocols());
	}
	public void setSSLProtocol(String sslProtocol) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setProtocols(sslProtocol);
	}


	public String getKeystoreFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeystoreFile();
	}
	public void setKeystoreFile(String keystoreFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeystoreFile(keystoreFile);
	}
	public String getSSLCertificateChainFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateChainFile();
	}
	public void setSSLCertificateChainFile(String certificateChainFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateChainFile(certificateChainFile);
	}
	public String getSSLCertificateFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateFile();
	}
	public void setSSLCertificateFile(String certificateFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateFile(certificateFile);
	}
	public String getSSLCertificateKeyFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeyFile();
	}
	public void setSSLCertificateKeyFile(String certificateKeyFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeyFile(certificateKeyFile);
	}


	public String getAlgorithm() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getKeyManagerAlgorithm();
	}
	public void setAlgorithm(String keyManagerAlgorithm) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setKeyManagerAlgorithm(keyManagerAlgorithm);
	}


	public String getClientAuth() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateVerificationAsString();
	}
	public void setClientAuth(String certificateVerification) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateVerification(certificateVerification);
	}


	public String getSSLVerifyClient() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateVerificationAsString();
	}
	public void setSSLVerifyClient(String certificateVerification) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateVerification(certificateVerification);
	}


	public int getTrustMaxCertLength(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateVerificationDepth();
	}
	public void setTrustMaxCertLength(int certificateVerificationDepth){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
	}
	public int getSSLVerifyDepth() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateVerificationDepth();
	}
	public void setSSLVerifyDepth(int certificateVerificationDepth) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
	}


	public boolean getUseServerCipherSuitesOrder() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getHonorCipherOrder();
	}
	public void setUseServerCipherSuitesOrder(boolean honorCipherOrder) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
	}
	public boolean getSSLHonorCipherOrder() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getHonorCipherOrder();
	}
	public void setSSLHonorCipherOrder(boolean honorCipherOrder) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
	}


	public String getCiphers() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCiphers();
	}
	public void setCiphers(String ciphers) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCiphers(ciphers);
	}
	public String getSSLCipherSuite() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCiphers();
	}
	public void setSSLCipherSuite(String ciphers) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCiphers(ciphers);
	}


	public String getKeystorePass() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeystorePassword();
	}
	public void setKeystorePass(String certificateKeystorePassword) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeystorePassword(certificateKeystorePassword);
	}


	public String getKeyPass() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeyPassword();
	}
	public void setKeyPass(String certificateKeyPassword) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
	}
	public String getSSLPassword() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeyPassword();
	}
	public void setSSLPassword(String certificateKeyPassword) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
	}


	public String getCrlFile(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateRevocationListFile();
	}
	public void setCrlFile(String certificateRevocationListFile){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
	}
	public String getSSLCARevocationFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateRevocationListFile();
	}
	public void setSSLCARevocationFile(String certificateRevocationListFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
	}
	public String getSSLCARevocationPath() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateRevocationListPath();
	}
	public void setSSLCARevocationPath(String certificateRevocationListPath) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateRevocationListPath(certificateRevocationListPath);
	}


	public String getKeystoreType() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeystoreType();
	}
	public void setKeystoreType(String certificateKeystoreType) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeystoreType(certificateKeystoreType);
	}


	public String getKeystoreProvider() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeystoreProvider();
	}
	public void setKeystoreProvider(String certificateKeystoreProvider) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeystoreProvider(certificateKeystoreProvider);
	}


	public String getKeyAlias() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCertificateKeyAlias();
	}
	public void setKeyAlias(String certificateKeyAlias) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCertificateKeyAlias(certificateKeyAlias);
	}


	public String getTruststoreAlgorithm(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTruststoreAlgorithm();
	}
	public void setTruststoreAlgorithm(String truststoreAlgorithm){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTruststoreAlgorithm(truststoreAlgorithm);
	}


	public String getTruststoreFile(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTruststoreFile();
	}
	public void setTruststoreFile(String truststoreFile){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTruststoreFile(truststoreFile);
	}


	public String getTruststorePass(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTruststorePassword();
	}
	public void setTruststorePass(String truststorePassword){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTruststorePassword(truststorePassword);
	}


	public String getTruststoreType(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTruststoreType();
	}
	public void setTruststoreType(String truststoreType){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTruststoreType(truststoreType);
	}


	public String getTruststoreProvider(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTruststoreProvider();
	}
	public void setTruststoreProvider(String truststoreProvider){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTruststoreProvider(truststoreProvider);
	}


	public String getSslProtocol() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getSslProtocol();
	}
	public void setSslProtocol(String sslProtocol) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setSslProtocol(sslProtocol);
	}


	public int getSessionCacheSize(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getSessionCacheSize();
	}
	public void setSessionCacheSize(int sessionCacheSize){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setSessionCacheSize(sessionCacheSize);
	}


	public int getSessionTimeout(){
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getSessionTimeout();
	}
	public void setSessionTimeout(int sessionTimeout){
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setSessionTimeout(sessionTimeout);
	}


	public String getSSLCACertificatePath() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCaCertificatePath();
	}
	public void setSSLCACertificatePath(String caCertificatePath) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCaCertificatePath(caCertificatePath);
	}


	public String getSSLCACertificateFile() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getCaCertificateFile();
	}
	public void setSSLCACertificateFile(String caCertificateFile) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setCaCertificateFile(caCertificateFile);
	}


	public boolean getSSLDisableCompression() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getDisableCompression();
	}
	public void setSSLDisableCompression(boolean disableCompression) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setDisableCompression(disableCompression);
	}


	public boolean getSSLDisableSessionTickets() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getDisableSessionTickets();
	}
	public void setSSLDisableSessionTickets(boolean disableSessionTickets) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setDisableSessionTickets(disableSessionTickets);
	}


	public String getTrustManagerClassName() {
		registerDefaultSSLHostConfig();
		return defaultSSLHostConfig.getTrustManagerClassName();
	}
	public void setTrustManagerClassName(String trustManagerClassName) {
		registerDefaultSSLHostConfig();
		defaultSSLHostConfig.setTrustManagerClassName(trustManagerClassName);
	}
	*/



//	@Override
//	protected Processor createUpgradeProcessor(SocketWrapperBase<?> socket, UpgradeToken upgradeToken) {
//		HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
//		if (httpUpgradeHandler instanceof InternalHttpUpgradeHandler) {
//			return new UpgradeProcessorInternal(socket, upgradeToken);
//		} else {
//			return new UpgradeProcessorExternal(socket, upgradeToken);
//		}
//	}
}
