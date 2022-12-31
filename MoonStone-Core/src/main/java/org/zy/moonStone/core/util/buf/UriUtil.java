package org.zy.moonStone.core.util.buf;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * @dateTime 2022年9月8日;
 * @author zy(azurite-Y);
 * @description
 */
public class UriUtil {
	private static final Pattern PATTERN_EXCLAMATION_MARK = Pattern.compile("!/");
	private static final Pattern PATTERN_CARET = Pattern.compile("\\^/");
	private static final Pattern PATTERN_ASTERISK = Pattern.compile("\\*/");

	private static final String WAR_SEPARATOR = "*/";

	
	// -------------------------------------------------------------------------------------
	// jarUrl
	// -------------------------------------------------------------------------------------
	public static URL buildJarUrl(File jarFile) throws MalformedURLException {
        return buildJarUrl(jarFile, null);
    }

    public static URL buildJarUrl(File jarFile, String entryPath) throws MalformedURLException {
        return buildJarUrl(jarFile.toURI().toString(), entryPath);
    }

    public static URL buildJarUrl(String fileUrlString) throws MalformedURLException {
        return buildJarUrl(fileUrlString, null);
    }
    
    public static URL buildJarUrl(String fileUrlString, String entryPath) throws MalformedURLException {
        String safeString = makeSafeForJarUrl(fileUrlString);
        StringBuilder sb = new StringBuilder();
        sb.append(safeString);
        sb.append("!/");
        if (entryPath != null) {
            sb.append(makeSafeForJarUrl(entryPath));
        }
        return new URL("jar", null, -1, sb.toString());
    }

    
    public static URL buildJarSafeUrl(File file) throws MalformedURLException {
        String safe = makeSafeForJarUrl(file.toURI().toString());
        return new URL(safe);
    }
    
    /**
     * 确保Jar Url的安全
     */
    private static String makeSafeForJarUrl(String input) {
        // 由于 '!/' 在JAR URL中具有特殊含义，需确保序列正确转义（如果存在）。
        String tmp = PATTERN_EXCLAMATION_MARK.matcher(input).replaceAll("%21/");
        
        // 自定义 jar:war: URL 将 "^/" 和 "*/" 视为特殊处理
        tmp = PATTERN_CARET.matcher(tmp).replaceAll("%5e/"); 
        tmp = PATTERN_ASTERISK.matcher(tmp).replaceAll("%2a/");
     
        return tmp;
    }
    
    
	// -------------------------------------------------------------------------------------
	// other
	// -------------------------------------------------------------------------------------
    /**
     * 确定 URI 字符串是否具有 <code>scheme</code> 组件。
     *
     * @param uri - 测试的 URI
     * @return 如果存在 <code>scheme</code>，则为 true，否则为 false
     */
    public static boolean hasScheme(CharSequence uri) {
        int len = uri.length();
        for(int i=0; i < len ; i++) {
            char c = uri.charAt(i);
            if(c == ':') {
                return i > 0;
            } else if(!UriUtil.isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * 将 <code>war:file:...</code> 形式的 URL 转换为 <code>jar:file:...</code>
     *
     * @param warUrl - 要转换的 WAR URL
     * @return 等效的 JAR URL
     * @throws MalformedURLException - 如果转换失败
     */
    public static URL warToJar(URL warUrl) throws MalformedURLException {
        // 假设规范是绝对路径，并以 war:file:/… 开始
        String file = warUrl.getFile();
        if (file.contains("*/")) {
            file = file.replaceFirst("\\*/", "!/");
        } else if (file.contains("^/")) {
            file = file.replaceFirst("\\^/", "!/");
        }

        return new URL("jar", warUrl.getHost(), warUrl.getPort(), file);
    }
    
    /**
     * 确定是否允许在 URI 的方案中使用该字符。参见 RFC 2396，第 3.1 节
     *
     * @param c - 要测试的字符
     * @return 如果允许使用此字符，则为 true，反之为 false
     */
    private static boolean isSchemeChar(char c) {
    	/*
    	 * isLetterOrDigit(String):
    	 * 确定指定的字符是字母还是数字。
    	 * 
    	 * 如果字符的 Character.isLetter(Char Ch) 或 Character.isDigit(Char Ch) 返回 true，则该字符被视为字母或数字。
    	 * 
    	 * 注：此方法不能处理补充字符。要支持所有 Unicode 字符，包括补充字符，请使用 isLetterOrDigit(Int) 方法。
    	 * @param ch: 测试的字符
    	 * @return 如果字符是字母或数字，则为 true；否则为 false。
    	 */
        return Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
    }
    
    public static String getWarSeparator() {
        return WAR_SEPARATOR;
    }
}
