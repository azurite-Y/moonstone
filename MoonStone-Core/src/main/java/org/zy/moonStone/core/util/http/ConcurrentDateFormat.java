package org.zy.moonstone.core.util.http;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @dateTime 2022年8月29日;
 * @author zy(azurite-Y);
 * @description 围绕SimpleDateFormat的线程安全包装器，它不使用ThreadLocal，从广义上讲，它只创建足够的SimpleDateFormat对象来满足并发性需求。
 */
public class ConcurrentDateFormat {
	private final String format;
    private final Locale locale;
    private final TimeZone timezone;
    private final Queue<SimpleDateFormat> queue = new ConcurrentLinkedQueue<>();

    public ConcurrentDateFormat(String format) {
    	this(format, null, null);
    }
    public ConcurrentDateFormat(String format, Locale locale) {
    	this(format, locale, null);
    }
    public ConcurrentDateFormat(String format, Locale locale, TimeZone timezone) {
        this.format = format;
        this.locale = locale;
        this.timezone = timezone;
        SimpleDateFormat initial = createInstance();
        queue.add(initial);
    }

    public String format(Date date) {
        SimpleDateFormat sdf = queue.poll();
        if (sdf == null) {
            sdf = createInstance();
        }
        String result = sdf.format(date);
        queue.add(sdf);
        return result;
    }

    public Date parse(String source) throws ParseException {
        SimpleDateFormat sdf = queue.poll();
        if (sdf == null) {
            sdf = createInstance();
        }
        Date result = sdf.parse(source);
        sdf.setTimeZone(timezone);
        queue.add(sdf);
        return result;
    }

    private SimpleDateFormat createInstance() {
    	SimpleDateFormat sdf = null;
    	if (locale == null ) {
    		sdf = new SimpleDateFormat(format);
    	} else {
    		sdf = new SimpleDateFormat(format, locale);
    	}
    	
    	if (timezone != null) {
    		sdf.setTimeZone(timezone);
    	}
        return sdf;
    }
}
