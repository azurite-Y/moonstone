package org.zy.moonStone.core.util.http.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @dateTime 2022年12月6日;
 * @author zy(azurite-Y);
 * @description
 */
public class AcceptEncoding {
	private final String encoding;
	private final double quality;

	protected AcceptEncoding(String encoding, double quality) {
		this.encoding = encoding;
		this.quality = quality;
	}

	public String getEncoding() {
		return encoding;
	}

	public double getQuality() {
		return quality;
	}

	/**
	 * 解析 accept-language 请求值
	 *
	 * @param acceptLanguageValue - 解析的 accept-language 头值
	 * @param locales             - 解析的结果
	 */
	public static List<AcceptEncoding> parse(String acceptEncodingValue) throws IOException {
		List<AcceptEncoding> result = new ArrayList<>();

		int indexOf = acceptEncodingValue.indexOf(",");
		int indexOf2 = acceptEncodingValue.indexOf("=");

		if (indexOf2 != -1) {
			if (indexOf != -1) { // Accept-Encoding: gzip, compress, br
				String[] acceptEncodings = acceptEncodingValue.split(",");
				for (String acceptEncoding : acceptEncodings) {
					result.add(new AcceptEncoding(acceptEncoding.trim(), 0));
				}
			} else { // Accept-Encoding: gzip
				result.add(new AcceptEncoding(acceptEncodingValue.trim(), 0));
			}
		} else {
			parseForQuality(acceptEncodingValue, result);
		}
		return result;
	}

	private static void parseForQuality(String acceptEncodingValue, List<AcceptEncoding> result) {
		//
		char[] charArray = acceptEncodingValue.toCharArray();

		List<String> acceptEncoding = new ArrayList<>();
		int endIndex = charArray.length - 1;

		int nameStart = 0, qualityStart = 0;
		boolean isReady = false;
		double quality = 0;
		boolean create = false;

		/*
		 * Accept-Encoding: br;q=1.0, gzip;q=0.8, *;q=0.1 1.0-[br] 0.8-[gzip] 0.1-[*]
		 */
		for (int i = 0; i < charArray.length; i++) {
			char data = charArray[i];

			if (!isReady) {
				switch (data) {
				case ',':
					acceptEncoding.add(acceptEncodingValue.substring(nameStart, i));
					// 跳过当前 ","
					nameStart = i + 1;
					break;
				case ';':
					acceptEncoding.add(acceptEncodingValue.substring(nameStart, i));
					isReady = true;
					// 跳过 ";q="
					i += 3;
					qualityStart = i;
					break;
				}
			} else {
				if (endIndex == i) {
					quality = Double.parseDouble(acceptEncodingValue.substring(qualityStart, i + 1));
					create = true;
				} else if (isReady && data == ',') {
					quality = Double.parseDouble(acceptEncodingValue.substring(qualityStart, i));
					create = true;
					nameStart = i + 1;
				}

				if (create) {
					for (String tag : acceptEncoding) {
						result.add(new AcceptEncoding(tag, quality));
					}
					acceptEncoding.clear();
					isReady = false;
					create = false;
				}
			}
		}
	}
}
