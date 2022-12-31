package org.zy.moonStone.core.util.http.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @dateTime 2022年11月23日;
 * @author zy(azurite-Y);
 * @description
 */
public class AcceptLanguage {
	private final Locale locale;
	private final double quality;

	protected AcceptLanguage(Locale locale, double quality) {
		this.locale = locale;
		this.quality = quality;
	}

	public Locale getLocale() {
		return locale;
	}

	public double getQuality() {
		return quality;
	}
	
	
    @Override
	public String toString() {
		return "AcceptLanguage [locale=" + locale + ", quality=" + quality + "]";
	}

    
	/**
     * 解析 accept-language 请求值
     *
     * @param acceptLanguageValue - 解析的 accept-language 头值
     * @param locales - 解析的结果
     */
	public static List<AcceptLanguage> parse(String acceptLanguageValue) throws IOException {
        List<AcceptLanguage> result = new ArrayList<>();
        char[] charArray = acceptLanguageValue.toCharArray();

		List<String> localeNames = new ArrayList<>();
		int endIndex = charArray.length - 1;
        
		int nameStart = 0, qualityStart = 0;
		boolean isReady = false;
		double quality = 0;
		boolean create = false;
		
		/*
		 * zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
		 * 0.9-[zh-CN, zh]
		 * 0.8-[en]
		 * 0.7-[en-GB]
		 * 0.6-[en-US]
		 */
        for (int i = 0; i < charArray.length; i++) {
        	char data = charArray[i];
        	
        	if(!isReady) {
        		switch (data) {
	        		case ',' : 
	        			localeNames.add(acceptLanguageValue.substring(nameStart, i));
	        			// 跳过当前 ","
	        			nameStart = i + 1;
	        			break;
	        		case ';' : 
	        			localeNames.add(acceptLanguageValue.substring(nameStart, i));
	        			isReady = true;
	        			// 跳过 ";q="
	        			i +=3;
	    				qualityStart = i;
	    				break;
        		}
        	} else {
        		if (endIndex == i) {
        			quality = Double.parseDouble( acceptLanguageValue.substring(qualityStart, i + 1) );
        			create = true;
        		} else if (isReady && data == ',') {
        			quality = Double.parseDouble( acceptLanguageValue.substring(qualityStart, i) );
        			create = true;
    				nameStart = i + 1;
        		}
        		
        		if (create) {
        			for (String languageTag : localeNames) {
    					result.add(new AcceptLanguage( Locale.forLanguageTag(languageTag), quality ));
    				}
    				localeNames.clear();
    				isReady = false;
    				create = false;
        		}
        	}
		}
        return result;
    }
	
	public static void main(String[] args) {
		String temp = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6";
		
		try {
			List<AcceptLanguage> parse = parse(temp);
			
			for (AcceptLanguage acceptLanguage : parse) {
				System.out.println(acceptLanguage);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
