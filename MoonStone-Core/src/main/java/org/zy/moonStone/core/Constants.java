package org.zy.moonstone.core;

import org.zy.moonstone.core.util.buf.ByteChunk;

/**
 * @dateTime 2022年6月9日;
 * @author zy(azurite-Y);
 * @description
 */
public final class Constants {
	/** 默认连接超时时间 */
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;

    /**
     * CRLF.
     */
    public static final String CRLF = "\r\n";
	
	/**
     * 回车-13 (CR-{@code '\r' }) .
     */
    public static final byte CR = (byte) '\r';

    /**
     * 换行-10 (LF-{@code '\n' }).
     */
    public static final byte LF = (byte) '\n';

    /**
     * 空格-32 (SP-{@code '\t' }).
     */
    public static final byte SP = (byte) ' ';

    /**
     * 制表符-9 (HT-{@code '\r' }).
     */
    public static final byte HT = (byte) '\t';

    /**
     * 冒号-58 (COLON-{@code ':' }).
     */
    public static final byte COLON = (byte) ':';

    /**
     * 分号-44 (SEMI_COLON-{@code ';' }).
     */
    public static final byte SEMI_COLON = (byte) ';';
    
    /**
     * 等号-61 (EQUAL_COLON-{@code ';' }).
     */
    public static final byte EQUAL_COLON = (byte) '=';

    /**
     * 'A'.
     */
    public static final byte A = (byte) 'A';

    /**
     * 'a'.
     */
    public static final byte a = (byte) 'a';

    /**
     * 'Z'.
     */
    public static final byte Z = (byte) 'Z';

    /**
     * '?'.
     */
    public static final byte QUESTION = (byte) '?';
	
    /**
     * 大写字节的极小值
     */
    public static final byte uppercaseByteMin = A - 1;
    
    /**
     * 大写字节数的极大值
     */
    public static final byte uppercaseByteMax = Z + 1;
    
    /**
     * 转换小写字节偏移量
     */
    public static final byte LC_OFFSET = A - a;
    
    public static final String CONNECTION = "connection";
    public static final String CLOSE_TOKEN = "close";
    public static final String KEEP_ALIVE_HEADER_VALUE_TOKEN = "keep-alive";
    public static final String TRANSFERENCODING = "Transfer-Encoding";
    public static final String CHUNKED = "chunked";

    /** "close"的字节数组 */
    public static final byte[] CLOSE_BYTES = CLOSE_TOKEN.getBytes();
    /** "keep-alive"的字节数组 */
    public static final byte[] KEEP_ALIVE_HEADER_VALUE_BYTES = KEEP_ALIVE_HEADER_VALUE_TOKEN.getBytes();

    public static final String CONTINUE_100= "100-continue";
    /** "100-continue"的字节数组 */
    public static final byte[] CONTINUE_100_BYTES= CONTINUE_100.getBytes();
    
    /** "HTTP/1.1 100 \r\n\r\n"的字节数组 */
    public static final byte[] ACK_BYTES = ByteChunk.convertToBytes("HTTP/1.1 100 " + CRLF + CRLF);
    /** "200"的字节数组 */
    public static final byte[] _200_BYTES = ByteChunk.convertToBytes("200");
    /** "400"的字节数组 */
    public static final byte[] _400_BYTES = ByteChunk.convertToBytes("400");
    /** "404"的字节数组 */
    public static final byte[] _404_BYTES = ByteChunk.convertToBytes("404");

	public static final String COOKIE = "cookie";
	public static final String HOST = "host";
	public static final String CONTENT_LENGTH = "content-length";
	public static final String CONTENT_TYPE = "content-type";
	public static final String CONTENT_ENCODING = "content-encoding";
	public static final String GZIP = "gzip";
	public static final String ACCEPT = "accept";
	public static final String ACCEPT_ENCODING = "accept-encoding";
	public static final String ACCEPT_LANGUAGE = "accept-language";
	/** HTTP/1.0 */
    public static final String HTTP_10 = "HTTP/1.0";
    /** HTTP/1.1 */
    public static final String HTTP_11 = "HTTP/1.1";
    
    /** "HTTP/1.1"的字节数组 */
    public static final byte[] HTTP_11_BYTES = ByteChunk.convertToBytes(HTTP_11);
    /** "content-length"的字节数组 */
    public static final byte[] CONTENT_LENGTH_BYTES = CONTENT_LENGTH.getBytes();
    /** "content-type"的字节数组 */
    public static final byte[] CONTENT_TYPE_BYTES = CONTENT_TYPE.getBytes();
    
    /** 更新两个线程之间的延迟(毫秒) */
    public static final long DEFAULT_THREAD_RENEWAL_DELAY = 1000L;
    
    /** security 是否已打开 */
    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);

    /**
     * 设置为布尔值的请求属性。如果处理此请求的连接器支持使用sendfile，则为 true
     */
    public static final String SENDFILE_SUPPORTED_ATTR = "org.zy.moonstone.core.sendfile.support";

    /**
     * ervlet可以使用的request属性，将sendfile要服务的文件的名称传递给连接器。
     * 值应为 {@code java.lang.String}，即要提供的文件的 {@code File.getCanonicalPath()}。
     */
    public static final String SENDFILE_FILENAME_ATTR = "org.zy.moonstone.core.sendfile.filename";


    /**
     * servlet可以使用的请求属性，将sendfile要提供的文件部分的起始偏移量传递给连接器。该值应该是 {@code java.lang.Long} 。
     * 为了提供完整的文件，值应该是 {@code Long.valueOf(0)}。
     */
    public static final String SENDFILE_FILE_START_ATTR = "org.zy.moonstone.core.sendfile.start";


    /**
     * servlet可以使用的请求属性，将sendfile所服务的文件部分的结束偏移量（不包括）传递给连接器。该值应该是 {@code java.lang.Long}。
     * 为了提供完整的文件，该值应该等于文件的长度。
     */
    public static final String SENDFILE_FILE_END_ATTR = "org.zy.moonstone.core.sendfile.end";


    /**
     * RemoteIpFilter、RemoteIpValve(可能由其他类似组件设置)设置的请求属性，当通过一个或多个代理接收请求时，该属性为连接器标识声称与此请求关联的远程IP地址。
     * 它通常通过X-Forwarded-For HTTP请求头提供。
     */
    public static final String REMOTE_ADDR_ATTRIBUTE = "org.zy.moonstone.core.remoteAddr";
    

    /**
     * Chunked filter 索引
     */
    public static final int CHUNKED_FILTER = 0;


    /**
     * GZIP filter 索引
     */
    public static final int GZIP_FILTER = 1;
    
    /** "GET"的字节数组 */
    public static final byte[] HTTP_GET = "GET".getBytes();
    /** "POST"的字节数组 */
    public static final byte[] HTTP_POST = "POST".getBytes();
    /** "PUT"的字节数组 */
    public static final byte[] HTTP_PUT = "PUT".getBytes();
    /** "HEAD"的字节数组 */
    public static final byte[] HTTP_HEAD = "HEAD".getBytes();
    /** "DELETE"的字节数组 */
    public static final byte[] HTTP_DELETE = "DELETE".getBytes();
    
}
