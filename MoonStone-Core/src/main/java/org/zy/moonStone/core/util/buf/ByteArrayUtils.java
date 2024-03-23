package org.zy.moonstone.core.util.buf;

import java.util.ArrayList;
import java.util.List;

/**
 * @dateTime 2022年6月14日;
 * @author zy(azurite-Y);
 * @description 字节数组操作类
 */
public class ByteArrayUtils {
	
	/**
	 * 字节容器转换为字节数组
	 * @param byteList
	 * @return
	 */
	public static byte[] getByte(List<Byte> byteList) {
		byte[] byteLine = new byte[byteList.size()];
		for (int i = 0; i < byteList.size() ; i++) {
			byteLine[i] = byteList.get(i);
		}
		return byteLine;
	}
	
	/**
	 * 字节容器转换为字节数组
	 * @param byteArray
	 * @return
	 */
	public static List<Byte> getList(byte[] byteArray) {
		List<Byte> byteList = new ArrayList<>();
		for (Byte byte1 : byteArray) {
			byteList.add(byte1);
		}
		return byteList;
	}
	
	/**
	 * 字节数组内容前序比对
	 * @param src - 源字节数组
	 * @param compared - 比对字节数据
	 * @return
	 */
	public static boolean  equalsByte(byte[] src, byte[] compared) {
		if (src.length > compared.length) {
			if (src[compared.length] != 0) { // 末尾数据不一致，或不是空数组元素
				return false;
			}
		} else if (src.length < compared.length) {
			return false;
		}
		
		for (int i = 0; i < compared.length; i++) {
			if (compared[i] != src[i]) {
				return false;
			}
		}
		return true;
	}
}
