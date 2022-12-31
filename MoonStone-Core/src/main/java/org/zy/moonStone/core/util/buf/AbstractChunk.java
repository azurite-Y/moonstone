package org.zy.moonStone.core.util.buf;

import java.io.Serializable;

/**
 * @dateTime 2022年5月24日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractChunk implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

	/**
	 * JVM 可能会将最大数组大小限制为略小于 Integer.MAX_VALUE。
	 * ArrayList 和其他类的JRE 源代码中的注释表明它在某些系统上可能低至MAX_VALUE - 8。
	 */
	public static final int ARRAY_MAX_SIZE = Integer.MAX_VALUE - 8;

    private int hashCode = 0;
    protected boolean hasHashCode = false;

    protected boolean isSet;

    private int limit = -1;
    
    /** 块数据起始索引 */
    protected int start;
    /** 块数据长度 */
    protected int end;
    
    /**
     * 此缓冲区中的最大数据量。 如果 -1 或未设置，缓冲区将增长到 {{@link #ARRAY_MAX_SIZE}。
     * 可以小于当前缓冲区大小（不会缩小）。当达到限制时，缓冲区将被刷新（如果设置了 out）或抛出异常。
     *
     * @param limit The new limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * @return 当前块的最大限制量，若limit值小于0则取 {@linkplain #ARRAY_MAX_SIZE }
     */
    protected int getLimitInternal() {
        if (limit > 0) {
            return limit;
        } else {
            return ARRAY_MAX_SIZE;
        }
    }

    /**
     * @return 数据起始位置
     */
    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int i) {
        end = i;
    }

    /**
     * @return 数据偏移量
     */
    public int getOffset() {
        return start;
    }

    public void setOffset(int off) {
        if (end < off) {
            end = off;
        }
        start = off;
    }

    /**
     * @return 数据长度
     */
    public int getLength() {
        return end - start;
    }
    
    /**
     * 将 Chunk 重置为初始状态
     */
    public void recycle() {
        hasHashCode = false;
        isSet = false;
        start = 0;
        end = 0;
    }
    
    public boolean isNull() {
        if (end > 0) {
            return false;
        }
        return !isSet;
    }
    
    @Override
    public int hashCode() {
        if (hasHashCode) {
            return hashCode;
        }
        int code = 0;

        code = hash();
        hashCode = code;
        hasHashCode = true;
        return code;
    }

    public int hash() {
        int code = 0;
        for (int i = start; i < end; i++) {
            code = code * 37 + getBufferElement(i);
        }
        return code;
    }
    
    @Override
	public String toString() {
		if (isNull()) {
			return null;
		} else if (end - start == 0) {
			return "";
		}
		String string = getString();
		return string != null ? string : String.format( "%s[start=%s eng=%s limit=%s]", this.getClass().getSimpleName(), start, end, limit );
	}
    /**
     * 子类选择实现的字符串转换逻辑
     * @return
     */
    protected String getString() { return null;}
    
    /**
     * 获得缓冲区中指定下标对应的数据
     * @param index
     * @return
     */
    protected abstract int getBufferElement(int index);
}
