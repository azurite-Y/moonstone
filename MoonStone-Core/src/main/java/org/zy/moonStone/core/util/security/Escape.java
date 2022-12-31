package org.zy.moonStone.core.util.security;

/**
 * @dateTime 2022年7月20日;
 * @author zy(azurite-Y);
 * @description 提供实用方法来转义不同上下文的内容。 对于要使用数据的上下文，使用的转义是正确的，这一点至关重要。
 */
public class Escape {
	private Escape() {}

    /**
     * 转义内容以在 HTML 中使用。 这种转义适用于以下用途：
     * <ul>
     * <li>当转义数据将直接放置在<p>、<td>等标签内时的元素内容。</li>
     * <li>用“或”引用属性值时的属性值。</li>
     * </ul>
     *
     * @param content - 要转义的内容
     * @return 转义的内容或  {@code null}（如果内容为  {@code null}）
     */
    public static String htmlElementContent(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&#39;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '/') {
                sb.append("&#47;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString() : content;
    }

    /**
     * 通过 Object.toString() 将对象转换为字符串，然后 HTML 转义生成的字符串以用于 HTML 内容。
     *
     * @param obj  - 要转换为字符串然后转义的对象
     * @return 转义的内容或“？”(如果 obj 为{@code null})
     */
    public static String htmlElementContent(Object obj) {
        if (obj == null) {
            return "?";
        }

        try {
            return htmlElementContent(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 转义内容以在 XML 中使用。
     *
     * @param content - 要转义的内容
     * @return 转义的内容或 null（如果内容为 null）
     */
    public static String xml(String content) {
        return xml(null, content);
    }

    /**
     * 转义内容以在 XML 中使用。
     *
     * @param ifNull - 内容为 {@code null} 时返回的值
     * @param content - 要转义的内容
     *
     * @return 转义的内容或 ifNull 的值（如果内容为{@code null}）
     */
    public static String xml(String ifNull, String content) {
        return xml(ifNull, false, content);
    }

    /**
     * 转义内容以在 XML 中使用。
     *
     * @param ifNull - 内容为 {@code null} 时返回的值
     * @param escapeCRLF - CR 和 LF 也应该被转义吗？
     * @param content - 要转义的内容      
     * @return 转义的内容或 ifNull 的值（如果内容为{@code null}）
     */
    public static String xml(String ifNull, boolean escapeCRLF, String content) {
        if (content == null) {
            return ifNull;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (escapeCRLF && c == '\r') {
                sb.append("&#13;");
            } else if (escapeCRLF && c == '\n') {
                sb.append("&#10;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString(): content;
    }
}
