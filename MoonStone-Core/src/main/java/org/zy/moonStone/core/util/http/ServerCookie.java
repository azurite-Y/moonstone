package org.zy.moonStone.core.util.http;

import java.io.Serializable;

import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年5月11日;
 * @author zy(azurite-Y);
 * @description 服务器端cookie表示。允许循环使用，并使用MessageBytes作为低级表示（因此字节->字符转换可以延迟，直到知道字符集为止）。MoonStone 使用这个可回收的对象来表示cookie，facade将其转换为外部表示。
 */
public class ServerCookie implements Serializable {
	private static final long serialVersionUID = -3535693131076731466L;
	/** Cookie的名称 */
	private final MessageBytes name=MessageBytes.newInstance();
	/** Cookie的值 */
	private final MessageBytes value=MessageBytes.newInstance();
	/** 定义了Web站点上可以访问该Cookie的站点域 */
	private final MessageBytes path=MessageBytes.newInstance();
	/** 创建Cookie 的网页所拥有的域名 */
	private final MessageBytes domain=MessageBytes.newInstance();
	/** Cookie 的注释 */
	private final MessageBytes comment=MessageBytes.newInstance();
	
	private int version = 0;

	public ServerCookie() {}
	
	public void recycle() {
		name.recycle();
		value.recycle();
		comment.recycle();
		path.recycle();
		domain.recycle();
		version=0;
	}
	
	public MessageBytes getComment() {
        return comment;
    }
	
    public MessageBytes getDomain() {
        return domain;
    }

    public MessageBytes getPath() {
        return path;
    }
    
    public MessageBytes getName() {
        return name;
    }
    
    public MessageBytes getValue() {
        return value;
    }
    
    public int getVersion() {
        return version;
    }

    public void setVersion(int v) {
        version = v;
    }

    @Override
    public String toString() {
        return "Cookie " + getName() + "=" + getValue() + " ; " + getVersion() + " " + getPath() + " " + getDomain();
    }
}
