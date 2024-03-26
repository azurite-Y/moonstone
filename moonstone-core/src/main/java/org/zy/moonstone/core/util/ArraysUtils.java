package org.zy.moonstone.core.util;

import java.util.List;

/**
 * @dateTime 2022年11月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class ArraysUtils {
	
	/**
	 * 将指定容器转换为存储全部容器元素的数组
	 * 
	 * @param lineByteList - 需转换的容器
	 * @return 存储全部容器元素的数组
	 */
	public static byte[] getByte(List<Byte> lineByteList) {
		byte[] byteLine = new byte[lineByteList.size()];
		for (int i = 0; i < lineByteList.size() ; i++) {
			byteLine[i] = lineByteList.get(i);
		}
		return byteLine;
	}
	
	/**
	 * 字节数组内容前序比对
	 * @param src - 源字节数组
	 * @param compared - 比对字节数据
	 * @return
	 */
	public static boolean equalsByte(byte[] src, byte[] compared) {
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
