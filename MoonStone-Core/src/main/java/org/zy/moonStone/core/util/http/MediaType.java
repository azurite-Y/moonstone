package org.zy.moonstone.core.util.http;

import org.zy.moonstone.core.util.http.parser.HttpParser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @dateTime 2022年5月30日;
 * @author zy(azurite-Y);
 * @description
 */
public class MediaType {
	/**
	 * 类型
	 */
	private final String type;
	
	/**
	 * 分割出的类型
	 */
    private final String subtype;
    
    /** 
     * 参数
     */
    private final LinkedHashMap<String,String> parameters;
    
    /**
     * 字符集
     */
    private final String charset;
    
    /**
     * 去除字符集后的拼接字符串，toStringNoCharset缓存字符串索引
     */
    private volatile String noCharset;
    
    /**
     * toString缓存字符串索引
     */
    private volatile String withCharset;
    
    protected MediaType(String type, String subtype, LinkedHashMap<String,String> parameters) {
        this.type = type;
        this.subtype = subtype;
        this.parameters = parameters;

        String cs = parameters.get("charset");
        if (cs != null && cs.length() > 0 && cs.charAt(0) == '"') {
            cs = HttpParser.unquote(cs);
        }
        this.charset = cs;
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getCharset() {
        return charset;
    }

    public int getParameterCount() {
        return parameters.size();
    }

    public String getParameterValue(String parameter) {
        return parameters.get(parameter.toLowerCase(Locale.ENGLISH));
    }
    
    @Override
    public String toString() {
        if (withCharset == null) {
            synchronized (this) {
                if (withCharset == null) {
                    StringBuilder result = new StringBuilder();
                    result.append(type);
                    result.append('/');
                    result.append(subtype);
                    for (Map.Entry<String, String> entry : parameters.entrySet()) {
                        String value = entry.getValue();
                        if (value == null || value.length() == 0) {
                            continue;
                        }
                        result.append(';');
                        result.append(' ');
                        result.append(entry.getKey());
                        result.append('=');
                        result.append(value);
                    }

                    withCharset = result.toString();
                }
            }
        }
        return withCharset;
    }

    public String toStringNoCharset() {
        if (noCharset == null) {
            synchronized (this) {
                if (noCharset == null) {
                    StringBuilder result = new StringBuilder();
                    result.append(type);
                    result.append('/');
                    result.append(subtype);
                    for (Map.Entry<String, String> entry : parameters.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("charset")) {
                            continue;
                        }
                        result.append(';');
                        result.append(' ');
                        result.append(entry.getKey());
                        result.append('=');
                        result.append(entry.getValue());
                    }

                    noCharset = result.toString();
                }
            }
        }
        return noCharset;
    }
    
    /**
     * 从 HTTP 请求头或应用程序解析 MediaType 值，例如：text/html;charset=UTF-8。
     *
     * @param input - 请求头文本字符流
     * @return 从给定的字符流解析的MediaType，如果无效则为空
     * @throws IOException - 如果读取输入有问题
     */
    public static MediaType parseMediaType(String input) throws IOException {
    	String[] split = input.split(";");
    	String[] types = split[0].split("/");
    	
    	LinkedHashMap<String,String> parameters = new LinkedHashMap<>();
    	if (split.length > 1) {
    		String[] charsets = split[1].split("=");
    		parameters.put(charsets[0], charsets[1]);
    	}
    	
		return new MediaType(types[0], types[1], parameters);
    }
}
