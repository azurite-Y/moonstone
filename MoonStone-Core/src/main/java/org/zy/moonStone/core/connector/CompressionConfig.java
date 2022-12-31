package org.zy.moonStone.core.connector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.zy.moonStone.core.http.Request;
import org.zy.moonStone.core.http.Response;
import org.zy.moonStone.core.util.buf.MessageBytes;
import org.zy.moonStone.core.util.http.MimeHeaders;
import org.zy.moonStone.core.util.http.parser.AcceptEncoding;

/**
 * @dateTime 2022年11月28日;
 * @author zy(azurite-Y);
 * @description
 */
public class CompressionConfig {
//    private static final Logger logger = LoggerFactory.getLogger(CompressionConfig.class);

    private int compressionLevel = 0;
    private Pattern noCompressionUserAgents = null;
    /** 需压缩的文件类型，以逗号相隔 */
    private String compressibleMimeType = "text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json,application/xml";
    private String[] compressibleMimeTypes = null;
    /** 需压缩的最小文件大小，默认值2k */
    private int compressionMinSize = 2048;
    /** 不需要压缩处理的浏览器类型，以正则方式匹配userAgent */
    private boolean noCompressionStrongETag = true;


    /**
     * 设置压缩级别
     *
     * @param compression - 为 ON、FORCE、OFF或表示ON的最小压缩大小(以字节为单位)之一
     */
    public void setCompression(String compression) {
        if (compression.equals("on")) {
            this.compressionLevel = 1;
        } else if (compression.equals("force")) {
            this.compressionLevel = 2;
        } else if (compression.equals("off")) {
            this.compressionLevel = 0;
        } else {
            try {
                // 尝试将压缩解析为int，这将给出最小的压缩大小
                setCompressionMinSize(Integer.parseInt(compression));
                this.compressionLevel = 1;
            } catch (Exception e) {
                this.compressionLevel = 0;
            }
        }
    }

    /**
     * 返回压缩级别
     *
     * @return 字符串形式的当前压缩级别(off/on/force)
     */
    public String getCompression() {
        switch (compressionLevel) {
        case 0:
            return "off";
        case 1:
            return "on";
        case 2:
            return "force";
        }
        return "off";
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * 获取正则表达式的String形式，该表达式定义了不使用gzip的用户代理。
     *
     * @return 正则表达式作为字符串
     */
    public String getNoCompressionUserAgents() {
        if (noCompressionUserAgents == null) {
            return null;
        } else {
            return noCompressionUserAgents.toString();
        }
    }

    public Pattern getNoCompressionUserAgentsPattern() {
        return noCompressionUserAgents;
    }

    /**
     * 不设置压缩用户代理模式。Pattern支持的正则表达式。例如: <code>gorilla|desesplorer|tigrus</code>.
     *
     * @param noCompressionUserAgents - 不应应用压缩的 UserAgent 字符串的正则表达式
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        if (noCompressionUserAgents == null || noCompressionUserAgents.length() == 0) {
            this.noCompressionUserAgents = null;
        } else {
            this.noCompressionUserAgents = Pattern.compile(noCompressionUserAgents);
        }
    }

    public String getCompressibleMimeType() {
        return compressibleMimeType;
    }

    public void setCompressibleMimeType(String valueS) {
        compressibleMimeType = valueS;
        compressibleMimeTypes = null;
    }

    /**
     * 
     * @return 可压缩MimeType数组
     */
    public String[] getCompressibleMimeTypes() {
        String[] result = compressibleMimeTypes;
        if (result != null) {
            return result;
        }
        List<String> values = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer(compressibleMimeType, ",");
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            if (token.length() > 0) {
                values.add(token);
            }
        }
        result = values.toArray(new String[0]);
        compressibleMimeTypes = result;
        return result;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    /**
     * 设置最小大小以触发压缩
     *
     * @param compressionMinSize - 以字节为单位的压缩所需的最小内容长度
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    /**
     * 确定是否应该为给定的响应启用压缩，如果是，设置任何必要的请求头将其标记为压缩。
     *
     * @param request - 触发响应的请求
     * @param response - 考虑压缩的响应
     *
     * @return 如果为给定响应启用了压缩，则为 {@code true}，否则为{@code false}
     */
    public boolean useCompression(Request request, Response response) {
        // 检查是否启用压缩
        if (compressionLevel == 0) {
            return false;
        }

        MimeHeaders responseHeaders = response.getMimeHeaders();

        // 检查内容是否尚未压缩
        MessageBytes contentEncodingMB = responseHeaders.getValue("Content-Encoding");
        if (contentEncodingMB != null) {
            // 内容编码值已排序，但顺序对于此检查并不重要，因此请使用Set而不是List
            Set<String> tokens = new HashSet<>();
            String headerValue = responseHeaders.getHeaderValue( "Content-Encoding" );
            for (String token : headerValue.split(",")) {
            	tokens.add(token.trim());
            }
            
            if (tokens.contains("gzip") || tokens.contains("br")) {
                return false;
            }
        }

        // 如果强制模式，则跳过长度和MIME类型检查
        if (compressionLevel != 2) {
            // 检查响应长度是否足以触发压缩
            long contentLength = response.getContentLengthLong();
            if (contentLength != -1 && contentLength < compressionMinSize) {
                return false;
            }

            // 检查兼容的MIME-TYPE
            String[] compressibleMimeTypes = getCompressibleMimeTypes();
            if (compressibleMimeTypes != null && !startsWithStringArray(compressibleMimeTypes, response.getContentType())) {
                return false;
            }
        }

        //---------------------------------------------------
        // HTTP 缓存
        //---------------------------------------------------
        // 检查资源是否具有强ETag
        if (noCompressionStrongETag) {
            String eTag = responseHeaders.getHeaderValue("ETag");
            if (eTag != null && !eTag.trim().startsWith("W/")) {
                // 有一个ETag不是以“W/…”开始的，所以它一定是一个强ETag
                return false;
            }
        }

        // 如果处理达到这个程度，响应可能会被压缩。因此，设置Vary报头以保持代理满意
//        ResponseUtil.addVaryFieldName(responseHeaders, "accept-encoding");

        /**
         * 检查用户代理是否支持gzip编码。
         * 只关心是否支持gzip编码。其他编码和权重可以忽略。
         */
        Enumeration<String> headerValues = request.getMimeHeaders().values("accept-encoding");
        boolean foundGzip = false;
        while (!foundGzip && headerValues.hasMoreElements()) {
            List<AcceptEncoding> acceptEncodings = null;
            try {
                acceptEncodings = AcceptEncoding.parse(headerValues.nextElement());
            } catch (IOException ioe) {
                // 如果读取响应头时出现问题，需禁用压缩
                return false;
            }

            for (AcceptEncoding acceptEncoding : acceptEncodings) {
                if ("gzip".equalsIgnoreCase(acceptEncoding.getEncoding())) {
                    foundGzip = true;
                    break;
                }
            }
        }

        if (!foundGzip) {
            return false;
        }

        // 如果强制模式，则跳过浏览器检查
        if (compressionLevel != 2) {
            // 检查不兼容的浏览器
            Pattern noCompressionUserAgents = this.noCompressionUserAgents;
            if (noCompressionUserAgents != null) {
                MessageBytes userAgentValueMB = request.getMimeHeaders().getValue("user-agent");
                if(userAgentValueMB != null) {
                    String userAgentValue = userAgentValueMB.toString();
                    if (noCompressionUserAgents.matcher(userAgentValue).matches()) {
                        return false;
                    }
                }
            }
        }

        // 所有的检查都通过了。启用压缩。

        // 压缩内容的长度是未知的，所以要这样标记。
        response.setContentLength(-1);
        // 配置压缩后的内容编码
        responseHeaders.setValue("Content-Encoding").setString("gzip");

        return true;
    }

    /**
     * 检查字符串数组中是否有以指定值开头的条目
     *
     * @param sArray 字符串数组
     * @param value - 指定字符串
     */
    private static boolean startsWithStringArray(String sArray[], String value) {
        if (value == null) {
            return false;
        }
        for (String s : sArray) {
            if (value.startsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
