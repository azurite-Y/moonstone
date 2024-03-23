package org.zy.moonstone.core.util.http.parser;

import org.zy.moonstone.core.util.collections.ConcurrentCache;
import org.zy.moonstone.core.util.http.MediaType;

import java.io.IOException;

/**
 * @dateTime 2022年9月25日;
 * @author zy(azurite-Y);
 * @description 缓存解析 content-type 请求头的结果
 */
public class MediaTypeCache {
	private final ConcurrentCache<String,String[]> cache;

    public MediaTypeCache(int size) {
        cache = new ConcurrentCache<>(size);
    }

    /**
     * 查看缓存并返回缓存值(如果存在)。如果缓存中不存在匹配项，则创建一个新的解析器，解析输入并将结果放入缓存中后返回
     *
     * @param input - 要解析的 content-type 请求头值
     * @return 结果以两个元素的字符串数组的形式提供。第一个元素是减去字符集的媒体类型，第二个元素是字符集
     */
    public String[] parse(String input) {
        String[] result = cache.get(input);

        if (result != null) {
            return result;
        }

        MediaType m = null;
        try {
            m = MediaType.parseMediaType(input);
        } catch (IOException e) {
            // Ignore - return null
        }
        if (m != null) {
            result = new String[] {m.toStringNoCharset(), m.getCharset()};
            cache.put(input, result);
        }

        return result;
    }
}
