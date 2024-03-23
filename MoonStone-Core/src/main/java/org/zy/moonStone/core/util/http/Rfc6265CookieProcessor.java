package org.zy.moonstone.core.util.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.util.buf.ByteChunk;
import org.zy.moonstone.core.util.buf.MessageBytes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.FieldPosition;
import java.util.BitSet;
import java.util.Date;

/**
 * @dateTime 2022年8月3日;
 * @author zy(azurite-Y);
 * @description
 */
public class Rfc6265CookieProcessor extends CookieProcessorBase {
    private static final Logger logger = LoggerFactory.getLogger(Rfc6265CookieProcessor.class);

    private static final BitSet domainValid = new BitSet(128);
    static {
        for (char c = '0'; c <= '9'; c++) {
            domainValid.set(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            domainValid.set(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            domainValid.set(c);
        }
        domainValid.set('.');
        domainValid.set('-');
    }
    
	@Override
	public void parseCookieHeader(MimeHeaders headers, ServerCookies serverCookies) {
		if (headers == null) {
            return;
        }
		
		MessageBytes cookiesMB = headers.getValue(Constants.COOKIE);
            if (cookiesMB != null && !cookiesMB.isNull() ) {
                if (cookiesMB.getType() != MessageBytes.T_BYTES ) {
                    if (logger.isDebugEnabled()) {
                        Exception e = new Exception();
                        logger.debug("解析的 cookie 是字符串. 期望是字节.", e);
                    }
                    cookiesMB.toBytes();
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Parsing b[]: " + cookiesMB.toString());
                }
                ByteChunk bc = cookiesMB.getByteChunk();

                parseCookie(bc, serverCookies);
            }
	}
	
	/**
	 * 解析 cookie 请求头
	 * @param bc
	 * @param serverCookies
	 */
	private void parseCookie(ByteChunk bc, ServerCookies serverCookies) {
		byte[] buffer = bc.getBuffer();
		int lastEnd = 0;
		
		ServerCookie serverCookie = null;
		for (int i = 0; i < bc.getEnd(); i++) {
			byte b = buffer[i];
			
			if (b == Constants.EQUAL_COLON) {
				serverCookie = serverCookies.addCookie();
				serverCookie.getName().setBytes(buffer, lastEnd, i  - lastEnd);
				lastEnd = i + 1;
			} else if (b == Constants.SEMI_COLON) {
				serverCookie.getValue().setBytes(buffer, lastEnd, i  - lastEnd);
				lastEnd = i + 1;
			} else if (i == bc.getEnd() - 1) {
				serverCookie.getValue().setBytes(buffer, lastEnd, i  - lastEnd);
			}
		}
	}

	@Override
	public String generateHeader(Cookie cookie, HttpServletRequest request) {
        StringBuffer header = new StringBuffer();

        header.append(cookie.getName());
        header.append('=');
        String value = cookie.getValue();
        if (value != null && value.length() > 0) {
            validateCookieValue(value);
            header.append(value);
        }

        // 相对过期时间,以秒为单位
        int maxAge = cookie.getMaxAge();
        if (maxAge > -1) {
            // 负的Max-Age等于没有Max-Age
            header.append("; Max-Age=");
            header.append(maxAge);

            // 微软IE和微软Edge不理解Max-Age，所以发送过期。如果不这样做，持久cookie在这些浏览器上就会失败
            header.append ("; Expires=");
            // 要立即过期，需要设置过去的时间
            if (maxAge == 0) {
                header.append(ANCIENT_DATE);
            } else {
                COOKIE_DATE_FORMAT.get().format(new Date(System.currentTimeMillis() + maxAge * 1000L), header, new FieldPosition(0));
            }
        }

        String domain = cookie.getDomain();
        if (domain != null && domain.length() > 0) {
            validateDomain(domain);
            header.append("; Domain=");
            header.append(domain);
        }

        String path = cookie.getPath();
        if (path != null && path.length() > 0) {
            validatePath(path);
            header.append("; Path=");
            header.append(path);
        }

        if (cookie.getSecure()) {
            header.append("; Secure");
        }

        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }

        SameSiteCookies sameSiteCookiesValue = getSameSiteCookies();

        if (!sameSiteCookiesValue.equals(SameSiteCookies.UNSET)) {
            header.append("; SameSite=");
            header.append(sameSiteCookiesValue.getValue());
        }

        return header.toString();
	}

	@Override
    public String generateHeader(Cookie cookie) {
        // 由于DateFormat不能使用StringBuilder
        StringBuffer header = new StringBuffer();
        /*
         * 名称验证在Cookie中进行，不能按上下文进行配置。将其移至此处将允许按上下文配置，但将验证延迟到生成标头。
         * 然而，该规范要求Cookie生成时出现IllegalArgumentException。
         */
        header.append(cookie.getName());
        header.append('=');
        String value = cookie.getValue();
        if (value != null && value.length() > 0) {
            validateCookieValue(value);
            header.append(value);
        }

        // RFC 6265倾向于“最大年龄”而不是“过期”
        int maxAge = cookie.getMaxAge();
        if (maxAge > -1) {
            // 负最大年龄等于无最大年龄
            header.append("; Max-Age=");
            header.append(maxAge);

            /*
             * 微软IE和微软Edge不理解Max-Age，所以发送也会过期。如果不这样做，持久cookie在这些浏览器中就会失败。
             * 
             * Wdy, DD-Mon-YY HH:MM:SS GMT ( 失效时间格式 )
             */
            header.append ("; Expires=");
            // 要立即过期，需要设置过去的时间
            if (maxAge == 0) {
                header.append(ANCIENT_DATE);
            } else {
                COOKIE_DATE_FORMAT.get().format(new Date(System.currentTimeMillis() + maxAge * 1000L), header, new FieldPosition(0));
            }
        }

        String domain = cookie.getDomain();
        if (domain != null && domain.length() > 0) {
            validateDomain(domain);
            header.append("; Domain=");
            header.append(domain);
        }

        String path = cookie.getPath();
        if (path != null && path.length() > 0) {
            validatePath(path);
            header.append("; Path=");
            header.append(path);
        }

        if (cookie.getSecure()) {
            header.append("; Secure");
        }

        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }

        SameSiteCookies sameSiteCookiesValue = getSameSiteCookies();

        if (!sameSiteCookiesValue.equals(SameSiteCookies.NONE)) {
            header.append("; SameSite=");
            header.append(sameSiteCookiesValue.getValue());
        }

        return header.toString();
    }
	
	@Override
	public Charset getCharset() {
		 return StandardCharsets.UTF_8;
	}

	private void validateCookieValue(String value) {
        int start = 0;
        int end = value.length();

        if (end > 1 && value.charAt(0) == '"' && value.charAt(end - 1) == '"') { // 去除首尾存在的双引号
            start = 1;
            end--;
        }

        char[] chars = value.toCharArray();
        for (int i = start; i < end; i++) {
            char c = chars[i];
            if (c < 0x21/* ! */  
            		|| c == 0x22 /* " */  
            		|| c == 0x2c /* , */   
            		|| c == 0x3b /* ; */  
            		|| c == 0x5c /* \ */  
            		|| c == 0x7f) {
                throw new IllegalArgumentException("无效字符，by：" + new String(Character.toChars(c)));
            }
        }
    }

    private void validateDomain(String domain) {
        int i = 0;
        int prev = -1;
        int cur = -1;
        char[] chars = domain.toCharArray();
        while (i < chars.length) {
            prev = cur;
            cur = chars[i];
            if (!domainValid.get(cur)) {
                throw new IllegalArgumentException("无效 Domain-不适用的字符，by：" + domain);
            }
            // 首字符(.)之后下一字符需为字母或数字
            if ((prev == '.' || prev == -1) && (cur == '.' || cur == '-')) {
            	throw new IllegalArgumentException("无效 Domain，首字符(.)之后下一字符需为字母或数字，by：" + domain);
            }
            // 字符(.)之后下一字符需为字母或数字
            if (prev == '-' && cur == '.') {
            	throw new IllegalArgumentException("无效 Domain，字符(.)之后下一字符需为字母或数字，by：" + domain);
            }
            i++;
        }
        // domain 必须以字母或数字结尾
        if (cur == '.' || cur == '-') {
        	throw new IllegalArgumentException("无效 Domain，必须以字母或数字结尾，by：" + domain);
        }
    }

    private void validatePath(String path) {
        char[] chars = path.toCharArray();

        for (char ch : chars) {
            if (ch < 0x20 || ch > 0x7E || ch == ';') {
                throw new IllegalArgumentException("无效 Path，by：" + path);
            }
        }
    }
}
