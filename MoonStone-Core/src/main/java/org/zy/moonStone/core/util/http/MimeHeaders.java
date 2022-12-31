package org.zy.moonStone.core.util.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description
 */
public class MimeHeaders {
	/**
	 * 初始大小，应==每个请求的平均请求头数
	 */
	public static final int DEFAULT_HEADER_SIZE = 15;

	/**
	 * 请求头属性集
	 */
	private MimeHeaderField[] headers = new MimeHeaderField[DEFAULT_HEADER_SIZE];

	/**
	 * 请求头的当前数目
	 */
	private int count;

	/**
	 * 请求头数目限制
	 */
	private int limit = -1;

	/**
	 * 使用默认缓冲区大小创建新的MimeHeaders对象
	 */
	public MimeHeaders() {
	}

	/**
	 * 设置请求头数目限制
	 * 
	 * @param limit - 新的请求头数目限制
	 */
	public void setLimit(int limit) {
		this.limit = limit;
		if (limit > 0 && headers.length > limit && count < limit) {
			// 收缩请求头属性数组
			MimeHeaderField tmp[] = new MimeHeaderField[limit];
			System.arraycopy(headers, 0, tmp, 0, count);
			headers = tmp;
		}
	}

	/**
	 * 清除所有请求头字段
	 */
	public void recycle() {
		clear();
	}

	/**
	 * 清除所有请求头字段
	 */
	public void clear() {
		for (int i = 0; i < count; i++) {
			headers[i].recycle();
		}
		count = 0;
	}

	
	
	@Override
	public String toString() { // 仅用于调试
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.println("=== MimeHeaders ===");
		for (MimeHeaderField mimeHeaderField : headers) {
			if (mimeHeaderField == null) break;
			
			pw.println(mimeHeaderField);
		}
		pw.println("======");
		return sw.toString();
	}

	// ----------------------------------------------------- 访问请求头
	// -----------------------------------------------------
	/**
	 * @return 请求头的当前数目
	 */
	public int size() {
		return count;
	}

	/**
	 * @param n - 请求头属性索引
	 * @return 第n个标头名称，如果没有此类标头，则为null。这可用于遍历所有标题字段。
	 */
	public MessageBytes getHeadName(int n) {
		return n >= 0 && n < count ? headers[n].getName() : null;
	}

	/**
	 * @param n - 请求头属性索引
	 * @return 第n个标头值，如果没有此类标头，则为null。这可用于遍历所有标题字段。
	 */
	public MessageBytes getHeadValue(int n) {
		return n >= 0 && n < count ? headers[n].getValue() : null;
	}

	/**
	 * 查找具有给定名称的请求头的索引。
	 * 
	 * @param name     - 请求头名称
	 * @param starting - 开始查找的索引
	 * @return 请求头属性索引
	 */
	public int findHeader(String name, int starting) {
		for (int i = starting; i < count; i++) {
			if (headers[i].getName().equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 返回表示标头字段名称的字符串枚举。字段名称可能在此枚举中出现多次，表示此标头中存在多个具有该名称的字段。
	 * 
	 * @return the enumeration
	 */
	public Enumeration<String> names() {
		return new NamesEnumerator(this);
	}

	public Enumeration<String> values(String name) {
		return new ValuesEnumerator(this, name);
	}

	// ----------------------------------------------------- 添加请求头
	// -----------------------------------------------------
	/**
	 * 在未超过限值的情况下，重新构建新请求头属性集以添加新的请求头属性
	 * 
	 * @return 未初始化的 MimeHeaderField 对象
	 */
	private MimeHeaderField createHeader() {
		if (limit > -1 && count >= limit) {
			throw new IllegalStateException(
					String.format("超过请求头数量限值，by count：%s，limit：%s", Integer.valueOf(count), Integer.valueOf(limit)));
		}
		MimeHeaderField mh;
		int len = headers.length;
		if (count >= len) {
			// 扩容
			int newLength = count * 2;
			if (limit > 0 && newLength > limit) {
				newLength = limit;
			}
			MimeHeaderField tmp[] = new MimeHeaderField[newLength];
			System.arraycopy(headers, 0, tmp, 0, len);
			headers = tmp;
		}
		if ((mh = headers[count]) == null) {
			headers[count] = mh = new MimeHeaderField();
		}
		count++;
		return mh;
	}

	/**
	 * 创建一个新的 MimeHeaderField ，返回新值的 MessageBytes 容器
	 * 
	 * @param name The header name
	 * @return the message bytes container for the value
	 */
	public MessageBytes addHeadNameValue(String headName) {
		MimeHeaderField mh = createHeader();
		mh.getName().setString(headName);
		return mh.getValue();
	}

	/**
	 * 使用未解码的字节[] 创建一个新的命名标头。可以延迟到字符的转换，直到编码已知。
	 * 
	 * @param b      - 请求头名称字节数组
	 * @param startN - 偏移量
	 * @param len    - 请求头名称字节数组长度
	 * @return 该值的消息字节容器
	 */
	public MessageBytes addHeadNameValue(byte b[], int startN, int len) {
		MimeHeaderField mhf = createHeader();
		mhf.getName().setBytes(b, startN, len);
		return mhf.getValue();
	}

	/**
	 * 允许“设置”操作，这会删除此标头的所有当前值。
	 * 
	 * @param name - 请求头名称
	 * @return 此值的 MessageBytes 对象
	 */
	public MessageBytes setValue(String name) {
		for (int i = 0; i < count; i++) {
			if (headers[i].getName().equalsIgnoreCase(name)) {
				for (int j = i + 1; j < count; j++) {
					if (headers[j].getName().equalsIgnoreCase(name)) {
						removeHeader(j--);
					}
				}
				return headers[i].getValue();
			}
		}
		MimeHeaderField mh = createHeader();
		mh.getName().setString(name);
		return mh.getValue();
	}

	// ----------------------------------------------------- 
	// 获取请求头
	// -----------------------------------------------------
	/**
	 * 查找并返回具有给定名称的请求头字段。 如果不存在这样的字段，则返回 null。 如果头部中有多个这样的字段，则返回任意一个。
	 * 
	 * @param name - 请求头名称
	 * @return 请求头属性值
	 */
	public MessageBytes getValue(String name) {
		for (int i = 0; i < count; i++) {
			if (headers[i].getName().equalsIgnoreCase(name)) {
				return headers[i].getValue();
			}
		}
		return null;
	}

	/**
	 * 获得指定请求头参数名的参数值
	 * 
	 * @param name 请求头参数名
	 * @return 指定请求头参数名的参数值
	 * @throws NullPointerException - 在未设置 Charset encoding 时
	 */
	public String getHeaderValue(String name) {
		MessageBytes mh = getValue(name);
		return mh != null ? mh.toString() : null;
	}

	/**
	 * 查找并返回具有给定名称的唯一请求头字段。 如果不存在这样的字段，则返回 null。 如果指定的标头字段不是唯一的，则抛出
	 * {@link IllegalArgumentException}。
	 * 
	 * @param name - 请求头名称
	 * @return 唯一的请求头字段值
	 * @throws IllegalArgumentException - 如果请求头有多个值
	 */
	public MessageBytes getUniqueValue(String name) {
		MessageBytes result = null;
		for (int i = 0; i < count; i++) {
			if (headers[i].getName().equalsIgnoreCase(name)) {
				if (result == null) {
					result = headers[i].getValue();
				} else {
					throw new IllegalArgumentException();
				}
			}
		}
		return result;
	}

	// ----------------------------------------------------- 删除请求头
	// -----------------------------------------------------
	/**
	 * 删除具有指定名称的请求头字段。 如果找不到这样的字段，则不执行任何操作。
	 * 
	 * @param name - 要删除的请求头字段的名称
	 */
	public void removeHeader(String name) {
		for (int i = 0; i < count; i++) {
			if (headers[i].getName().equalsIgnoreCase(name)) {
				removeHeader(i--);
			}
		}
	}

	/**
	 * 重置并与最后一个请求头交换
	 * 
	 * @param idx - 要删除的标头的索引
	 */
	private void removeHeader(int idx) {
		MimeHeaderField mh = headers[idx];

		mh.recycle();
		headers[idx] = headers[count - 1];
		headers[count - 1] = mh;
		count--;
	}

	/**
	 * 复制请求头
	 * 
	 * @param source
	 * @throws IOException
	 */
	public void duplicate(MimeHeaders source) throws IOException {
		for (int i = 0; i < source.size(); i++) {
			MimeHeaderField mhf = createHeader();
			mhf.getName().duplicate(source.getHeadName(i));
			mhf.getValue().duplicate(source.getHeadValue(i));
		}
	}
}

/** 枚举不同的标题名称 */
class NamesEnumerator implements Enumeration<String> {
	/** 当前索引 */
	private int pos;
	/** 当前请求头数目 */
	private final int size;
	/** 下一请求头属性名 */
	private String next;
	private final MimeHeaders headers;

	public NamesEnumerator(MimeHeaders headers) {
		this.headers = headers;
		pos = 0;
		size = headers.size();
		findNext();
	}

	private void findNext() {
		next = null;
		for (; pos < size; pos++) {
			next = headers.getHeadName(pos).toString();
			for (int j = 0; j < pos; j++) {
				if (headers.getHeadName(j).equalsIgnoreCase(next)) {
					// 重复的
					next = null;
					break;
				}
			}
			if (next != null) {
				// 非重复
				break;
			}
		}
		// 下次调用 findNext 时，它将尝试下一个元素
		pos++;
	}

	@Override
	public boolean hasMoreElements() {
		return next != null;
	}

	@Override
	public String nextElement() {
		String current = next;
		findNext();
		return current;
	}
}

/** 枚举（可能）多值元素的值. */
class ValuesEnumerator implements Enumeration<String> {
	/** 当前索引 */
	private int pos;
	/** 当前请求头数目 */
	private final int size;
	/** 下一请求头属性 */
	private MessageBytes next;
	private final MimeHeaders headers;
	/** 关联的请求头属性名 */
	private final String name;

	ValuesEnumerator(MimeHeaders headers, String name) {
		this.name = name;
		this.headers = headers;
		pos = 0;
		size = headers.size();
		findNext();
	}

	private void findNext() {
		next = null;
		for (; pos < size; pos++) {
			MessageBytes n1 = headers.getHeadName(pos);
			if (n1.equalsIgnoreCase(name)) {
				next = headers.getHeadValue(pos);
				break;
			}
		}
		pos++;
	}

	@Override
	public boolean hasMoreElements() {
		return next != null;
	}

	@Override
	public String nextElement() {
		MessageBytes current = next;
		findNext();
		return current.toString();
	}
}

class MimeHeaderField {
	private final MessageBytes nameB = MessageBytes.newInstance();
	private final MessageBytes valueB = MessageBytes.newInstance();

	/**
	 * 创建一个新的、未初始化的请求头字段
	 */
	public MimeHeaderField() {
	}

	public void recycle() {
		nameB.recycle();
		valueB.recycle();
	}

	public MessageBytes getName() {
		return nameB;
	}

	public MessageBytes getValue() {
		return valueB;
	}

	@Override
	public String toString() {
		return "MimeHeaderField [nameB=" + nameB + ", valueB=" + valueB + "]";
	}

}
