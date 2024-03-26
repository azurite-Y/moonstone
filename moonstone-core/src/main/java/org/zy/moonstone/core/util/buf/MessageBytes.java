package org.zy.moonstone.core.util.buf;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * @dateTime 2022年5月24日;
 * @author zy(azurite-Y);
 * @description
 */
public class MessageBytes implements Cloneable, Serializable {
	private static final long serialVersionUID = 7139599310104129715L;

	/** 主要类型（无论设置为原始值是什么）*/
    private int type = T_NULL;

    public static final int T_NULL = 0;
    /** 如果用于创建 MessageBytes 的对象是字符串，则 getType() 为 T_STR */
    public static final int T_STR  = 1;
    /** 如果用于创建 MessageBytes 的对象是byte[]，则 getType() 为 T_BYTES */
    public static final int T_BYTES = 2;
    /** 如果用于创建 MessageBytes 的对象是char[]，则 getType() 为 T_CHARS */
    public static final int T_CHARS = 3;
    /** 如果用于创建 MessageBytes 的对象是long，则 getType() 为 T_LONG */
    public static final int T_LONG = 4;

    private int hashCode=0;
    /** 是否需计算hashCode */
    private boolean hasHashCode=false;

    /** 表示字节数组+偏移量的内部对象 */
    private final ByteChunk byteC=new ByteChunk();
    /** 表示字符数组+偏移量的内部对象 */
    private final CharChunk charC=new CharChunk();

    private String strValue;
    /** 如果计算了String值，则为true */
    private boolean hasStrValue=false;

    private long longValue;
    private boolean hasLongValue=false;
    
    /**
     * 创建一个新的、未初始化的 MessageBytes 对象。使用静态的newInstance()回调本构造器
     */
    private MessageBytes() {}

    /**
     * 构造一个新的 MessageBytes 实例。
     * @return the instance
     */
    public static MessageBytes newInstance() {
        return factory.newInstance();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isNull() {
        return byteC.isNull() && charC.isNull() && !hasStrValue && !hasLongValue;
    }

    /**
     * 将消息字节重置为未初始化 (NULL) 状态
     */
    public void recycle() {
        type=T_NULL;
        byteC.recycle();
        charC.recycle();

        strValue=null;

        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * 将内容设置为指定的字节子数组
     * @param b the bytes
     */
    public void setBytes(byte[] b) {
    	setBytes(b, 0, b.length);
    }
    
    /**
     * 将内容设置为指定的字节子数组
     * @param b the bytes
     * @param off - 字节的起始偏移量
     * @param len - 字节的长度
     */
    public void setBytes(byte[] b, int off, int len) {
        byteC.setBytes( b, off, len );
        type=T_BYTES;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * 将内容设置为 char[]
     *
     * @param c the chars
     * @param off - 字符的起始偏移量
     * @param len - 字符的长度
     */
    public void setChars( char[] c, int off, int len ) {
        charC.setChars( c, off, len );
        type=T_CHARS;
        strValue = null;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * 将内容设置为字符串
     * @param s The string
     */
    public void setString( String s ) {
        strValue=s;
        hasHashCode=false;
        hasLongValue=false;
        if (s == null) {
            hasStrValue=false;
            type=T_NULL;
        } else {
            hasStrValue=true;
            type=T_STR;
        }
    }

    @Override
    public String toString() {
        if (hasStrValue) {
            return strValue;
        }

        switch (type) {
	        case T_CHARS:
	            strValue = charC.getString();
	            hasStrValue = true;
	            return strValue;
	        case T_BYTES:
	            strValue = byteC.getString();
	            hasStrValue = true;
	            return strValue;
	        case T_LONG : 
	        	strValue = String.valueOf(longValue);
	        	hasStrValue = true;
	        	return strValue;
        }
        return null;
    }
    
    /**
     * 返回原始内容的类型。 可以是 T_STR、T_BYTES、T_CHARS 或 T_NULL
     * @return the type
     */
    public int getType() {
        return type;
    }
    
    /**
     * 返回ByteChunk，表示字节[] 和偏移量/长度。 仅在 T_BYTES 或进行转换后有效。
     * @return the byte chunk
     */
    public ByteChunk getByteChunk() {
        return byteC;
    }

    /**
     * 返回CharChunk，表示 char[] 和偏移量/长度。 仅当 T_CHARS 或进行转换后才有效。
     * @return the char chunk
     */
    public CharChunk getCharChunk() {
        return charC;
    }

    /**
     * 返回字符串值。仅在 T_STR 或进行转换后有效。
     * @return the string
     */
    public String getString() {
        return strValue;
    }

    /**
     * @return 用于字符串&lt;-&gt;字节转换的字符集
     */
    public Charset getCharset() {
        return byteC.getCharset();
    }

    /**
     * 设置用于字符串字节转换的字符集
     * @param charset The charset
     */
    public void setCharset(Charset charset) {
        byteC.setCharset(charset);
    }

    /**
     * 进行 char-&gt;byte 转换
     */
    public void toBytes() {
        if (isNull()) {
            return;
        }
        if (!byteC.isNull()) {
            type = T_BYTES;
            return;
        }
        toString();
        type = T_BYTES;
        Charset charset = byteC.getCharset();
        ByteBuffer result = charset.encode(strValue);
        
        byte[] arr = new byte[result.limit()];
        result.get(arr);
        byteC.setBytes(arr, 0, arr.length);
    }

    /**
     * 转换为 char[] 并填充 CharChunk
     */
    public void toChars() {
        if (isNull()) {
            return;
        }
        if (!charC.isNull()) {
            type = T_CHARS;
            return;
        }
        toString();
        type = T_CHARS;
        char cc[] = strValue.toCharArray();
        charC.setChars(cc, 0, cc.length);
    }

    /**
     * 返回原始缓冲区的长度。 请注意，以字节为单位的长度可能与以字符为单位的长度不同。
     * @return the length
     */
    public int getLength() {
        if(type==T_BYTES) {
            return byteC.getLength();
        }
        if(type==T_CHARS) {
            return charC.getLength();
        }
        if(type==T_STR) {
            return strValue.length();
        }
        toString();
        if( strValue==null ) {
            return 0;
        }
        return strValue.length();
    }

    /**
     * 设置Long类型的消息字节
     * @param l The long
     */
    public void setLong(long l) {
        longValue=l;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=true;
        type=T_LONG;
    }

    /**
     * @return 返回Long类型的消息字节
     */
    public long getLong() {
        if( hasLongValue ) {
            return longValue;
        }

        switch (type) {
        case T_BYTES:
            longValue=byteC.getLong();
            break;
        default:
            longValue=Long.parseLong(toString());
        }

        hasLongValue=true;
        return longValue;

     }

    /**
     * 将消息字节与指定的 String 对象进行比较。
     * @param s - 要比较的字符串
     * @return 如果比较成功，则为 true，否则为 false
     */
    public boolean equals(String s) {
        switch (type) {
	        case T_STR:
	            if (strValue == null) {
	                return s == null;
	            }
	            return strValue.equals( s );
	        case T_CHARS: return charC.equals( s );
	        case T_BYTES: return byteC.equals( s );
	        default: return false;
        }
    }

    /**
     * 将消息字节与指定的 String 对象进行比较。
     * @param s - 要比较的字符串
     * @return 如果比较成功，则为 true，否则为 false
     */
    public boolean equalsIgnoreCase(String s) {
        switch (type) {
        case T_STR:
            if (strValue == null) {
                return s == null;
            }
            return strValue.equalsIgnoreCase( s );
        case T_CHARS:
            return charC.equalsIgnoreCase( s );
        case T_BYTES:
            return byteC.equalsIgnoreCase( s );
        default:
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageBytes) {
            return equals((MessageBytes) obj);
        }
        return false;
    }

    public boolean equals(MessageBytes mb) {
        switch (type) {
	        case T_STR: return mb.equals( strValue );
        }

        if( mb.type != T_CHARS && mb.type!= T_BYTES ) {
            // 它是一个字符串或int/date字符串值
            return equals( mb.toString() );
        }

        /*
         * mb和this 二者可能是char[]或byte[]
         */
        if( mb.type == T_CHARS && type==T_CHARS ) {
            return charC.equals( mb.charC );
        }
        if( mb.type==T_BYTES && type== T_BYTES ) {
            return byteC.equals( mb.byteC );
        }
        if( mb.type== T_CHARS && type== T_BYTES ) {
            return byteC.equals( mb.charC );
        }
        if( mb.type== T_BYTES && type== T_CHARS ) {
            return mb.byteC.equals( charC );
        }
        // 不可能发生
        return true;
    }
    
    /**
     * @return <code>true</code> if the message bytes starts with the specified string.
     * @param s the string
     * @param pos The start position
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        switch (type) {
        case T_STR:
            if( strValue==null ) {
                return false;
            }
            if( strValue.length() < pos + s.length() ) {
                return false;
            }

            for( int i=0; i<s.length(); i++ ) {
                if( Ascii.toLower( s.charAt( i ) ) !=
                    Ascii.toLower( strValue.charAt( pos + i ))) {
                    return false;
                }
            }
            return true;
        case T_CHARS:
            return charC.startsWithIgnoreCase( s, pos );
        case T_BYTES:
            return byteC.startsWithIgnoreCase( s, pos );
        default:
            return false;
        }
    }

    @Override
    public  int hashCode() {
        if( hasHashCode ) {
            return hashCode;
        }
        int code = 0;

        code=hash();
        hashCode=code;
        hasHashCode=true;
        return code;
    }

    private int hash() {
        int code=0;
        switch (type) {
        case T_STR:
            // 使用相同的哈希函数
            for (int i = 0; i < strValue.length(); i++) {
                code = code * 37 + strValue.charAt( i );
            }
            return code;
        case T_CHARS:
            return charC.hash();
        case T_BYTES:
            return byteC.hash();
        default:
            return 0;
        }
    }
    
    public int indexOf(String s, int starting) {
        toString();
        return strValue.indexOf( s, starting );
    }

    public int indexOf(String s) {
        return indexOf( s, 0 );
    }

    public int indexOfIgnoreCase(String s, int starting) {
        toString();
        String upper=strValue.toUpperCase(Locale.ENGLISH);
        String sU=s.toUpperCase(Locale.ENGLISH);
        return upper.indexOf( sU, starting );
    }

    /**
     * 将src复制到当前 MessageBytes 中，如果需要，可以分配更多空间。
     * @param src
     * @throws IOException - 将溢出数据写入输出通道失败
     */
    public void duplicate( MessageBytes src ) throws IOException {
        switch( src.getType() ) {
	        case MessageBytes.T_BYTES:
	            type=T_BYTES;
	            ByteChunk bc=src.getByteChunk();
	            byteC.allocate( 2 * bc.getLength(), -1 );
	            byteC.append( bc );
	            break;
	        case MessageBytes.T_CHARS:
	            type=T_CHARS;
	            CharChunk cc=src.getCharChunk();
	            charC.allocate( 2 * cc.getLength(), -1 );
	            charC.append( cc );
	            break;
	        case MessageBytes.T_STR:
	            type=T_STR;
	            String sc=src.getString();
	            this.setString( sc );
	            break;
        }
        setCharset(src.getCharset());
    }
    
    private static final MessageBytesFactory factory=new MessageBytesFactory();
    private static class MessageBytesFactory {
        protected MessageBytesFactory() {}
        public MessageBytes newInstance() {
            return new MessageBytes();
        }
    }
}
