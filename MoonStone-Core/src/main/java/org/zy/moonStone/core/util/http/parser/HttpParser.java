package org.zy.moonStone.core.util.http.parser;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.zy.moonStone.core.interfaces.functions.CallbackHandler;
import org.zy.moonStone.core.util.ArraysUtils;

/**
 * @dateTime 2022年9月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class HttpParser {
	public static String unquote(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        int start;
        int end;

        // 如果有的话，跳过周围的引号
        if (input.charAt(0) == '"') {
            start = 1;
            end = input.length() - 1;
        } else {
            start = 0;
            end = input.length();
        }

        StringBuilder result = new StringBuilder();
        for (int i = start ; i < end; i++) {
            char c = input.charAt(i);
            if (input.charAt(i) == '\\') {
                i++;
                result.append(input.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
	
	/**
	 * 字符串分割
	 * 
	 * @param str - 字符串
	 * @param separator - 字符串分隔符
	 * @param boundary - 字符串边界符，根据此边界符对分割后的字符串划分为key与value。如："="
	 * @return 分割字符串的结果
	 */
	public static Map<String,String> parseSeparator(String str, String separator, String boundary) {
		if (str == null || str.isEmpty()) {
            return new HashMap<>();
        }
		HashMap<String, String> result = new HashMap<>();
		

		StringTokenizer st = new StringTokenizer(str, separator);
		String tempStr = null;
		while (st.hasMoreTokens()) {
			tempStr = st.nextToken();
			
			if (!tempStr.isEmpty()) {
				String[] strings = tempStr.split(boundary);
				if (strings.length > 1) {
					
					String value =strings[1];
					if (strings[1].startsWith("\"") && strings[1].endsWith("\"")) {  // 去除双引号
						value = strings[1].substring(1, strings[1].length() - 1);
					}
					
					result.put(strings[0].trim(), value.trim());
				} else {
					result.put(tempStr, null);
				}
			}
		}

		return result;
	}
	
	/**
	 * 数组数据分割
	 * 
	 * @param byteArr - 需分割数组
	 * @param start - 分割数据开始索引
	 * @param len - 分割数据结束索引
	 * @param separator - 字节分隔符
	 * @param boundary - 字节边界符，根据此边界符对分割后的字符串划分为key与value。如："="
	 * @return 分割字符串的结果
	 */
	public static boolean parseSeparator(byte[] byteArr, Charset charset, byte separator, byte boundary, CallbackHandler<String, String> handler) {
		return parseSeparator(byteArr, 0, byteArr.length, charset, separator, boundary, handler);
	}
	/**
	 * 数组数据分割
	 * 
	 * @param byteArr - 需分割数组
	 * @param start - 分割数据开始索引
	 * @param len - 分割数据结束索引
	 * @param separator - 字节分隔符
	 * @param boundary - 字节边界符，根据此边界符对分割后的字符串划分为key与value。如："="
	 * @return 分割字符串的结果
	 */
	public static boolean parseSeparator(byte[] byteArr, int start, int len, Charset charset, byte separator, byte boundary, CallbackHandler<String, String> handler) {
		if (byteArr == null || byteArr.length == 0) {
            return false;
        }
		
		List<Byte> name = new ArrayList<>();
		List<Byte> value = new ArrayList<>();
		boolean isNameByte = true;
		int endIndex = len - 1;
		
		for (int i = start; i < len; i++) {
			Byte data = (Byte)byteArr[i];
			
			if (data == separator || i == endIndex) {
				handler.work(new String(ArraysUtils.getByte(name), charset).trim(), new String(ArraysUtils.getByte(value), charset).trim());
				
				name.clear();
				value.clear();
				isNameByte = true;
				continue;
			} else if (data ==boundary){
				isNameByte = false;
				continue;
			}
			
			if (isNameByte) {
				name.add(data);
			} else {
				value.add(data);
			}
		}
		
		return true;
	}
	
	public static void main(String[] args) {
		
	}
	
	static void parseSeparatorStrTest() {
		String str = "Content-Disposition: form-data; name=\"upload\"; filename=\"寒窑赋.txt\"\r\n" + 
				"Content-Type: text/plain";
		Map<String, String> parseSeparator = parseSeparator(str, "\n", ":");

		for (Iterator<Entry<String, String>> iterator = parseSeparator.entrySet().iterator(); iterator.hasNext();) {
			Entry<String, String> entry = iterator.next();
			System.out.printf("[%s == %s]", entry.getKey(), entry.getValue());
		}
	}
	
	static void parseSeparatorArrTest() {
		String str = "name=zse&pwd=1234";
		char a = '&';
		parseSeparator(str.getBytes(), Charset.forName("UTF-8"), ((byte)a), ((byte)'='), (String key, String value) -> {
			System.out.printf("[%s-%s]", key, value);
		});
	}
}
