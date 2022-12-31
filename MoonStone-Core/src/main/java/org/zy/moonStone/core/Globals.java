package org.zy.moonStone.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.zy.moonStone.core.exceptions.LifecycleException;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 全局常量类
 */
public final class Globals {
	/**
	 * 控制严格servlet规范遵从性的主标志.
	 */
	public static final boolean STRICT_SERVLET_COMPLIANCE = false;

	/**
	 * 获取资源需要斜杠
	 */
	public static final boolean GET_RESOURCE_REQUIRE_SLASH = false;

	/**
	 * 根据对规范的严格解释，访问会话(如果存在)是否更新上次访问时间
	 */
	public static final boolean ACCESS_SESSION = false;

	/**
	 * 回收镜像.
	 */
	public static final boolean RECYCLE_FACADES = false ;

	/**
	 * NIO选择器是否共享
	 */
	public static final boolean NIO_SELECTOR_SHARED = true ;

	/**
	 * 异步支持属性
	 */
	public static final String ASYNC_SUPPORTED_ATTR = "org.zy.moonStone.core.ASYNC_SUPPORTED";

	/**
	 * 请求调度程序路径.
	 */
	public static final String DISPATCHER_REQUEST_PATH_ATTR = "org.zy.moonStone.core.DISPATCHER_REQUEST_PATH";

	/**
	 * 请求调度程序状态.
	 */
	public static final String DISPATCHER_TYPE_ATTR = "org.zy.moonStone.core.DISPATCHER_TYPE";

	 /** 
	  * 与上下文关联的WebResourceRoot。这可以用于操作静态文件
	  */
    public static final String RESOURCES_ATTR = "org.zy.moonStone.core.resources";
    
    /**
     * 在 {@link #initInternal()}, {@link #startInternal()}, {@link LifecycleException}  {@link #stopInternal()} 或 {@link #destroyInternal()} 期间抛出的LifecycleException是否会被重新抛出，
     * 以便调用者处理，或者是否会被记录. 默认值为true
     */
    public  static final String THROW_ON_FAILURE = "org.zy.moonStone.core.EXIT_ON_INIT_FAILURE";

    /**
     * servlet上下文属性，在该属性下存储应用程序类加载器的类路径(作为String类型的对象)，用该平台的适当路径分隔符分隔。
     */
    public static final String CLASS_PATH_ATTR =  "org.zy.moonStone.classpath";
    
	// --------------------------------------------------------------------------
	// 属性常量
	// --------------------------------------------------------------------------
	/**
	 * 服务器名称
	 */
	public static final String WEB_APPLICATION_NAME = "MoonStone";
	/**
	 * 服务器Home路径系统属性名
	 */
	public static final String WEB_APPLICATION_HOME = "moon.home";
	/**
	 * 服务器Base路径系统属性名
	 */
	public static final String WEB_APPLICATION_BASE = "moon.base";
	
	/** 
	 * uri编码字符集
	  */
	public static final Charset DEFAULT_URI_CHARSET = StandardCharsets.UTF_8;
	
	/**
	 * 主体内容编码字符集
	 */
    public static final Charset DEFAULT_BODY_CHARSET = StandardCharsets.ISO_8859_1;

    /**
     * 是否开启了安全功能
     */
    public static final boolean IS_SECURITY_ENABLED = System.getSecurityManager() != null;
    
    /**
     * 请求/响应注释数据数组尺寸
     */
    public static final int MAX_NOTES = 32;

	// --------------------------------------------------------------------------
	// Request states
	// --------------------------------------------------------------------------
    /** 新阶段 */
    public static final int STAGE_NEW = 0;
    /** 解析阶段 */
    public static final int STAGE_PARSE = 1;
    /** 准备阶段 */
    public static final int STAGE_PREPARE = 2;
    /** 服务阶段 */
    public static final int STAGE_SERVICE = 3;
    /** 输入结束阶段 */
    public static final int STAGE_ENDINPUT = 4;
    /** 输出结束阶段 */
    public static final int STAGE_ENDOUTPUT = 5;
    /** 保持连接阶段 */
    public static final int STAGE_KEEPALIVE = 6;
    /** 结束阶段 */
    public static final int STAGE_ENDED = 7;

    
	// --------------------------------------------------------------------------
	// 默认协议设置
	// --------------------------------------------------------------------------
    public static final int DEFAULT_CONNECTION_LINGER = -1;
    public static final boolean DEFAULT_TCP_NO_DELAY = true;

    
    /**
     * 如果连接器处理此请求支持使用 sendfile，则设置为 Boolean.TRUE 值的请求属性名
     */
    public static final String SENDFILE_SUPPORTED_ATTR = "org.zy.moonStone.sendfile.support";

    /**
     * servlet 可以使用的 request 属性将 sendfile 提供的文件的名称传递给连接器。 该值应该是 {@code java.lang.String} 类型，即要提供的文件的 {@code File.getCanonicalPath()}
     */
    public static final String SENDFILE_FILENAME_ATTR = "org.zy.moonStone.sendfile.filename";

    /**
     * servlet 可以使用的请求属性，用于将 sendfile 提供的文件部分的起始偏移量传递给连接器。 该值应为 {@code java.lang.Long} 类型。 要提供完整的文件，值应该是 {@code Long.valueOf(0)}
     */
    public static final String SENDFILE_FILE_START_ATTR = "org.zy.moonStone.sendfile.start";

    /**
     * servlet 可以使用的请求属性，用于将 sendfile 提供的文件部分的结束偏移量（不包括）传递给连接器。 该值应为 {@code java.lang.Long} 类型。 要提供完整的文件，该值应等于文件的长度
     */
    public static final String SENDFILE_FILE_END_ATTR = "org.zy.moonStone.sendfile.end";

    /**
     * 请求属性由RemoteIpFilter, RemoteIpValve设置(也可能由其他类似组件设置)，当一个请求通过一个或多个代理接收时，标识声称与该请求相关联的连接器的远程IP地址。它通常是通过X-Forwarded-For HTTP报头提供的
     */
    public static final String REMOTE_ADDR_ATTRIBUTE = "org.zy.moonStone.remoteAddr";
    
    /**
     * HTTP 格式日期缓存数
     */
    public static final String  HTTP_DATE_FORMAT_CACHE_SIZE= "org.zy.moonStone.util.http.FastHttpDateFormat.CACHE_SIZE";

    
	// --------------------------------------------------------------------------
	// SSH
	// --------------------------------------------------------------------------
    /**
     * 存储代表客户端提供的证书链的 X509Certificate 对象数组的请求属性（如果有）
     */
    public static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";

    /**
     * 请求属性，在该属性下存储SSL连接中使用的密码套件的名称(作为java.lang.String类型的对象)。
     */
    public static final String CIPHER_SUITE_ATTR = "javax.servlet.request.cipher_suite";
    
    /**
     * 请求属性，在该属性下存储用于此SSL连接的密钥大小(作为java.lang.Integer类型的对象)
     */
    public static final String KEY_SIZE_ATTR = "javax.servlet.request.key_size";

    /**
     * 请求属性，在该属性下存储用于此SSL连接的会话id(作为java.lang.String类型的对象)
     */
    public static final String SSL_SESSION_ID_ATTR = "javax.servlet.request.ssl_session_id";

    /**
     * 会话管理器的请求属性键。这是 MoonStone 对 Servlet 规范的扩展。
     */
    public static final String SSL_SESSION_MGR_ATTR = "javax.servlet.request.ssl_session_mgr";

    /**
     * 请求属性，在该属性下存储命名调度程序请求上的servlet名称。
     */
	public static final String NAMED_DISPATCHER_ATTR = null;
	
	// --------------------------------------------------------------------------
	// Session attribute names
	// --------------------------------------------------------------------------
    /**
     * 运行访问控制器上下文的 subject
     */
    public static final String SUBJECT_ATTR = "javax.security.auth.subject";
}
