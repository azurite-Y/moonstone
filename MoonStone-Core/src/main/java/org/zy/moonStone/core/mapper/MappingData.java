package org.zy.moonStone.core.mapper;

import javax.servlet.http.MappingMatch;

import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.interfaces.container.Host;
import org.zy.moonStone.core.interfaces.container.Wrapper;
import org.zy.moonStone.core.util.buf.MessageBytes;

/**
 * @dateTime 2022年6月29日;
 * @author zy(azurite-Y);
 * @description
 */
public class MappingData {
	public Host host = null;
    public Context context = null;
    public int contextSlashCount = 0;
    public Context[] contexts = null;
    public Wrapper wrapper = null;

    public final MessageBytes requestPath = MessageBytes.newInstance();
    public final MessageBytes wrapperPath = MessageBytes.newInstance();
    public final MessageBytes pathInfo = MessageBytes.newInstance();

    public final MessageBytes redirectPath = MessageBytes.newInstance();

    // ApplicationMapping 用于实现 javax.servlet.http.HttpServletMapping 的字段
    public MappingMatch matchType = null;

    public void recycle() {
        host = null;
        context = null;
        contextSlashCount = 0;
        contexts = null;
        wrapper = null;
        requestPath.recycle();
        wrapperPath.recycle();
        pathInfo.recycle();
        redirectPath.recycle();
        matchType = null;
    }
}
