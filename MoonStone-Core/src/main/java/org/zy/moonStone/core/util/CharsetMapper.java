package org.zy.moonStone.core.util;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import org.zy.moonStone.core.util.compat.JreCompat;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description 当 Content-Type 标头不包含时，尝试从 Locale 映射到用于解释输入文本（或生成输出文本）的相应字符集的实用程序类。 
 * 可以通过修改它加载的映射数据或通过子类化它（以更改算法）然后为特定的 Web 应用程序使用您自己的版本来自定义此类的行为。
 */
public class CharsetMapper {
	/**
     * 默认属性资源名称
     */
    public static final String DEFAULT_RESOURCE = "org/zy/moonStone/core/util/CharsetMapperDefault.properties";
    
    /**
     * 已从指定或默认属性资源初始化的映射属性
     */
    private Properties map = new Properties();
    
    public CharsetMapper() {
        this(DEFAULT_RESOURCE);
    }
    
    /**
     * 使用指定的属性资源构造一个新的 CharsetMapper.
     * 
     * @param name - 要加载的属性资源的名称
     * @exception IllegalArgumentException - 如果由于任何原因无法加载指定的属性资源
     */
    public CharsetMapper(String name) {
        if (JreCompat.isGraalAvailable()) {
            map.put("en", "ISO-8859-1");
        } else {
            try (InputStream stream = ClassLoader.getSystemResourceAsStream(name)) {
                map.load(stream);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                throw new IllegalArgumentException(t);
            }
        }
    }
    
    /**
     * 计算假定的字符集名称，给定指定的区域设置，并且不存在指定为内容类型标头的一部分的字符集
     *
     * @param locale - 为其计算字符集的语言环境
     * @return 字符集名称
     */
    public String getCharset(Locale locale) {
        // 首先匹配完整的 language_country_variant，然后是 language_country，然后是仅语言
        String charset = map.getProperty(locale.toString());
        if (charset == null) {
            charset = map.getProperty(locale.getLanguage() + "_" + locale.getCountry());
            if (charset == null) {
                charset = map.getProperty(locale.getLanguage());
            }
        }
        return charset;
    }

    /**
     * 部署描述符可以有一个 locale-encoding-mapping-list 元素，它描述了 web 应用程序从语言环境到字符集的所需映射。 在处理上下文的配置类时调用此方法
     *
     * @param locale - 字符集的语言环境
     * @param charset - 要与语言环境关联的字符集
     */
    public void addCharsetMappingFromDeploymentDescriptor(String locale, String charset) {
        map.put(locale, charset);
    }
}
