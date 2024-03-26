package org.zy.moonstone.core.util.http;

import org.zy.moonstone.core.interfaces.http.CookieProcessor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @dateTime 2022年8月3日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class CookieProcessorBase implements CookieProcessor {
	private static final String COOKIE_DATE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";

	protected static final ThreadLocal<DateFormat> COOKIE_DATE_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			DateFormat df = new SimpleDateFormat(COOKIE_DATE_PATTERN, Locale.CHINA);
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			return df;
		}
	};

	protected static final String ANCIENT_DATE;

	static {
		ANCIENT_DATE = COOKIE_DATE_FORMAT.get().format(new Date(10000));
	}
	
	private SameSiteCookies sameSiteCookies = SameSiteCookies.UNSET;

    public SameSiteCookies getSameSiteCookies() {
        return sameSiteCookies;
    }
    public void setSameSiteCookies(String sameSiteCookies) {
        this.sameSiteCookies = SameSiteCookies.fromString(sameSiteCookies);
    }
}
