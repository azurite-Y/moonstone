package org.zy.moonStone.core.util.http;

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.zy.moonStone.core.Globals;

/**
 * @dateTime 2022年8月29日;
 * @author zy(azurite-Y);
 * @description
 */
public class FastHttpDateFormat {
    private static final int CACHE_SIZE = Integer.parseInt(System.getProperty(Globals.HTTP_DATE_FORMAT_CACHE_SIZE, "1000"));
    
	private static final String DATE_RFC5322 = "EE, dd MM yyyy HH:mm:ss z";
    private static final String DATE_OBSOLETE_RFC850 = "EEEEEE, dd-MMM-yy HH:mm:ss zzz";
    private static final String DATE_OBSOLETE_ASCTIME = "EEE MMMM d HH:mm:ss yyyy";
	
    private static final String DATE_OBJECT_DAY_TIME= "yyyy-MM-dd HH:mm:ss";
	/**
     * Formatter cache.
     */
    private static final Map<Long, String> formatCache = new ConcurrentHashMap<>(CACHE_SIZE);
    
    /**
     * Parser cache.
     */
    private static final Map<String, Long> parseCache = new ConcurrentHashMap<>(CACHE_SIZE);
    
    private static final ConcurrentDateFormat FORMAT_RFC5322;
    private static final ConcurrentDateFormat FORMAT_OBSOLETE_RFC850;
    private static final ConcurrentDateFormat FORMAT_OBSOLETE_ASCTIME;
    private static final ConcurrentDateFormat FORMAT_DAY_TIME;
    
    private static final ConcurrentDateFormat[] httpParseFormats;

    /**
     * 生成 currentDate 对象的瞬间
     */
    private static volatile long currentDateGenerated = 0L;

    /**
     * 当前格式化日期
     */
    private static String currentDate = null;
    
    
    static {
        // 所有使用时区的格式都使用 GMT
        TimeZone tz = TimeZone.getTimeZone("GMT");
        
        FORMAT_RFC5322 = new ConcurrentDateFormat(DATE_RFC5322, Locale.US, TimeZone.getDefault());
        FORMAT_OBSOLETE_RFC850 = new ConcurrentDateFormat(DATE_OBSOLETE_RFC850, Locale.US, tz);
        FORMAT_OBSOLETE_ASCTIME = new ConcurrentDateFormat(DATE_OBSOLETE_ASCTIME, Locale.US, tz);
        FORMAT_DAY_TIME = new ConcurrentDateFormat(DATE_OBJECT_DAY_TIME);

        httpParseFormats = new ConcurrentDateFormat[] {FORMAT_RFC5322, FORMAT_OBSOLETE_RFC850, FORMAT_OBSOLETE_ASCTIME, FORMAT_DAY_TIME };
    }

    
    /**
     * 以 HTTP 格式获取当前日期
     * @return HTTP 格式的日期
     */
    public static final String getCurrentDate() {
        long now = System.currentTimeMillis();
        if ((now - currentDateGenerated) > 1000) {
            currentDate = FORMAT_RFC5322.format(new Date(now));
            currentDateGenerated = now;
        }
        return currentDate;
    }
    
	/**
	 * 获取指定日期的HTTP格式
     */
    public static final String formatDate(long value) {
    	// 获得Long 实例
        Long longValue = Long.valueOf(value);
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null) {
            return cachedDate;
        }

        String newDate = FORMAT_RFC5322.format(new Date(value));
        updateFormatCache(longValue, newDate);
        return newDate;
    }
    
    /**
	 * 获取指定日期的HTTP格式
     */
    public static final String formatDayTime(long value) {
    	// 获得Long 实例
        Long longValue = Long.valueOf(value);
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null) {
            return cachedDate;
        }

        String newDate = FORMAT_DAY_TIME.format(new Date(value));
        updateFormatCache(longValue, newDate);
        return newDate;
    }
    
    /**
     * 尝试将给定日期解析为 HTTP 日期。
     * 
     * @param value - HTTP日期
     * @return 如果不能解析该值，则将日期设置为一个 Long 值或 <code>-1</code>
     */
    public static final long parseDate(String value) {
        Long cachedDate = parseCache.get(value);
        if (cachedDate != null) {
            return cachedDate.longValue();
        }

        long date = -1;
        for (int i = 0; (date == -1) && (i < httpParseFormats.length); i++) {
            try {
                date = httpParseFormats[i].parse(value).getTime();
                updateParseCache(value, Long.valueOf(date));
            } catch (ParseException e) {
                // Ignore
            }
        }

        return date;
    }


    /**
     * Update cache.
     */
    private static void updateFormatCache(Long key, String value) {
        if (value == null) {
            return;
        }
        if (formatCache.size() > CACHE_SIZE) {
            formatCache.clear();
        }
        formatCache.put(key, value);
    }


    /**
     * Update cache.
     */
    private static void updateParseCache(String key, Long value) {
        if (value == null) {
            return;
        }
        if (parseCache.size() > CACHE_SIZE) {
            parseCache.clear();
        }
        parseCache.put(key, value);
    }
    
}
