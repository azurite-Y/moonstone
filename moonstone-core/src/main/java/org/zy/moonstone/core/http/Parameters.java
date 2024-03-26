package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.buf.ByteChunk;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.parser.HttpParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @dateTime 2022年6月14日;
 * @author zy(azurite-Y);
 * @description
 */
public class Parameters {
    private static final Logger logger = LoggerFactory.getLogger(Parameters.class);

    private static final Charset DEFAULT_BODY_CHARSET = StandardCharsets.ISO_8859_1;
    private static final Charset DEFAULT_URI_CHARSET = StandardCharsets.UTF_8;
    
    private Charset charset = StandardCharsets.ISO_8859_1;
    /** URI 查询字符串参数 */
    private Charset queryStringCharset = StandardCharsets.UTF_8;
    
    private final Map<String,ArrayList<String>> paramHashValues = new LinkedHashMap<>();
    
    private MessageBytes queryMB;
    private MessageBytes requestBodyMB;
    
    /** 容器将自动解析的最大参数数量 */
    private int limit = -1;
    private int parameterCount = 0;
    private boolean didQueryParameters=false;
    
    private final MessageBytes decodedQuery = MessageBytes.newInstance();
    
    /**
     * 如果在参数分析过程中出现故障，则设置为故障原因（如果有多个故障，则以第一个故障为准）
     */
    private FailReason parseFailedReason = null;

	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    public Parameters() {
		super();
	}

    
	// -------------------------------------------------------------------------------------
	// getter、setter
	// -------------------------------------------------------------------------------------
	public void setQuery( MessageBytes queryMB ) {
        this.queryMB=queryMB;
    }
	public MessageBytes getQueryMB() {
		return queryMB;
	}
	
	public void setRequestBodyMB(MessageBytes requestBodyMB) {
		this.requestBodyMB = requestBodyMB;
	}
	public MessageBytes getRequestBodyMB() {
		return requestBodyMB;
	}

	public void setLimit(int limit) {
        this.limit = limit;
    }
	public int getLimit() {
		return limit;
	}

    public Charset getCharset() {
        return charset;
    }
    public void setCharset(Charset charset) {
        if (charset == null) {
            charset = DEFAULT_BODY_CHARSET;
        }
        this.charset = charset;
        if(logger.isDebugEnabled()) {
        	logger.debug("Set encoding to " + charset.name());
        }
    }
    
    public void setQueryStringCharset(Charset queryStringCharset) {
        if (queryStringCharset == null) {
            queryStringCharset = DEFAULT_URI_CHARSET;
        }
        this.queryStringCharset = queryStringCharset;

        if(logger.isDebugEnabled()) {
            logger.debug("Set query string encoding to " + queryStringCharset.name());
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    public void recycle() {
        parameterCount = 0;
        paramHashValues.clear();
        charset = DEFAULT_BODY_CHARSET;
    }
    
    public String getParameter(String name ) {
        handleQueryParameters();
        ArrayList<String> values = paramHashValues.get(name);
        if (values != null) {
            if(values.size() == 0) {
                return "";
            }
            return values.get(0);
        } else {
            return null;
        }
    }
    
    /**
     * 处理uri中查询字符串内容
     */
	public void handleQueryParameters() {
		if (didQueryParameters) {
            return;
        }

        didQueryParameters = true;

        if (queryMB == null || queryMB.isNull()) {
            return;
        }

        if(logger.isDebugEnabled()) {
            logger.debug("Decoding query " + decodedQuery + " " + queryStringCharset.name());
        }

        try {
            decodedQuery.duplicate(queryMB);
        } catch (IOException e) {
            e.printStackTrace();
        }
        processParameters(decodedQuery, queryStringCharset);
	}
	
	public void processParameters(MessageBytes data, Charset charset) {
        if( data==null || data.isNull() || data.getLength() <= 0 ) {
            return;
        }

        if( data.getType() != MessageBytes.T_BYTES ) {
            data.toBytes();
        }
        ByteChunk bc=data.getByteChunk();
        processParameters(bc.getBytes(), bc.getOffset(), bc.getLength(), charset);
    }
	
    public void processParameters( byte bytes[], int start, int len, Charset charset) {
        processParameters( bytes, start, len, charset, (byte)'&', (byte)'=' );

    }
    
    /**
     * 解析请求体数据
     */
    public void processRequestBodyParameters(byte[] bytes) {
    	processParameters(bytes , 0, bytes.length, charset, (byte)'&', (byte)'=' );
    }
    
    public FailReason getParseFailedReason() {
        return parseFailedReason;
    }

    public void setParseFailedReason(FailReason failReason) {
        if (this.parseFailedReason == null) {
            this.parseFailedReason = failReason;
        }
    }
    
    public void addParameter( String key, String value ) throws IllegalStateException {
        if( key==null ) {
            return;
        }

        parameterCount ++;
        if (limit > -1 && parameterCount > limit) {
            // 处理此参数将超出限制
            setParseFailedReason(FailReason.TOO_MANY_PARAMETERS);
            throw new IllegalStateException("解析参数量超限, parameterCount: " + parameterCount + ", limit: " + limit);
        }

        ArrayList<String> values = paramHashValues.get(key);
        if (values == null) {
            values = new ArrayList<>(1);
            paramHashValues.put(key, values);
        }
        values.add(value);
    }
    
    public String[] getParameterValues(String name) {
        handleQueryParameters();
        // no "facade"
        ArrayList<String> values = paramHashValues.get(name);
        if (values == null) {
            return null;
        }
        return values.toArray(new String[values.size()]);
    }
    
    public Enumeration<String> getParameterNames() {
        handleQueryParameters();
        return Collections.enumeration(paramHashValues.keySet());
    }
	// -------------------------------------------------------------------------------------
	// 私有方法
	// -------------------------------------------------------------------------------------
    private void processParameters(byte bytes[], int start, int len, Charset charset, byte separator, byte boundary) {
    	HttpParser.parseSeparator(bytes, start, len, charset, separator, boundary, (String key, String value) -> {
        	addParameter(key, value);
//			System.out.printf("[%s-%s]", key, value);
		});
    }
    
	
	// -------------------------------------------------------------------------------------
	// 内部类、枚举
	// -------------------------------------------------------------------------------------
    public enum FailReason {
    	/** 
    	 * 客户端断开连接<br/>
    	 * 从连接通道中读取数据触发 {@link IOException}
    	 */
        CLIENT_DISCONNECT,
        /** 由解析到的上传数据文件存储目录指向的是一个文件触发 */
        MULTIPART_CONFIG_INVALID,
        /** 无效的content_type */
        INVALID_CONTENT_TYPE,
        /** io错误，标识读取文件时的 {@link IOException}，触发于文件路径错误 */
        IO_ERROR,
        /** 请求体数据过大 */
        POST_TOO_LARGE,
        /**
         * 请求正文不完整<br/>
         * 请求头指示的content_length和实际读取到的内容长度不一致
         */
        REQUEST_BODY_INCOMPLETE,
        /** 请求参数数量超限 */
        TOO_MANY_PARAMETERS,
        UNKNOWN,
    }


}
