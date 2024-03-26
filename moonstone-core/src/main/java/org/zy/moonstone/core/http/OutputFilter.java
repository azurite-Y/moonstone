package org.zy.moonstone.core.http;

import org.zy.moonstone.core.util.net.interfaces.HttpOutputBuffer;

/**
 * @dateTime 2022年12月5日;
 * @author zy(azurite-Y);
 * @description
 */
public interface OutputFilter extends HttpOutputBuffer {
	/**
	 * 有些Filter需要来自响应的附加参数。所有必要的读取都可以在该方法中进行，因为该方法是在响应头处理完成后调用的
     *
     * @param response - 要与此OutputFilter关联的响应
     */
    public void setResponse(Response response);


    /**
     * 使Filter准备好处理下一个请求
     */
    public void recycle();


    /**
     * 设置过滤器管道中的下一个缓冲区
     *
     * @param buffer - 下一个缓冲区实例
     */
    public void setHttpOutputBuffer(HttpOutputBuffer buffer);
}
