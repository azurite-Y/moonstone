package org.zy.moonStone.core.servlets;

import java.util.Locale;

import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * @dateTime 2022年11月16日;
 * @author zy(azurite-Y);
 * @description
 * 围绕 {@link HttpServletResponse } 的包装器，它将应用程序响应对象(可能是传递给servlet的原始对象，
 * 也可能是基于 {@link HttpServletResponseWrapper} 类)转换回内部的 {@link HttpResponse}。
 * <p>
 * <strong>WARNING</strong>: 
 * 由于Java不支持多重继承，{@link ApplicationResponse }中的所有逻辑都在ApplicationHttpResponses中重复。在进行更改时，需确保这两个类保持同步！
 */
class ApplicationResponse extends ServletResponseWrapper {
	/**
	 * 此包装的响应是否Include()主体方法调用
	 */
    protected boolean included = false;

    
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    /**
     * 围绕指定的servlet响应构造一个新的包装响应
     *
     * @param response - 正在包装的servlet响应
     * @param included - 如果此响应正在由<code>RequestDispatcher.include()</code>调用处理，则返回<code>true</code>
     */
    public ApplicationResponse(ServletResponse response, boolean included) {
        super(response);
        setIncluded(included);
    }
    
    
	// -------------------------------------------------------------------------------------
	// ServletResponseWrapper 实现方法
	// -------------------------------------------------------------------------------------
    /**
     * 禁止在 included 响应上调用<code>reset()</code>
     *
     * @exception IllegalStateException - 如果响应已经提交
     */
    @Override
    public void reset() {
        // 如果已经提交，包装的响应将抛出ISE
        if (!included || getResponse().isCommitted())
            getResponse().reset();
    }


    /**
     * 禁止在 included 响应上调用<code>setContentLength(int)</code>
     *
	 * @param len - 新内容长度
     */
    @Override
    public void setContentLength(int len) {

        if (!included)
            getResponse().setContentLength(len);

    }


    /**
     * 禁止在 included 响应上调用<code>setContentLengthLong(long)</code>
     *
	 * @param len - 新内容长度
     */
    @Override
    public void setContentLengthLong(long len) {

        if (!included)
            getResponse().setContentLengthLong(len);

    }


    /**
     * 禁止在 included 响应上调用<code>setContentType()</code>
     *
	 * @param type - 新的内容类型
     */
    @Override
    public void setContentType(String type) {

        if (!included)
            getResponse().setContentType(type);

    }


	/**
	 * 忽略在 included 响应上调用<code>setLocale()</code>
	 *
	 * @param loc - 新的地址
	 */
    @Override
    public void setLocale(Locale loc) {
        if (!included)
            getResponse().setLocale(loc);
    }


    /**
     * 忽略在 included 响应上调用<code>setBufferSize()</code>
     *
     * @param size - 缓存尺寸
     */
    @Override
    public void setBufferSize(int size) {
        if (!included)
            getResponse().setBufferSize(size);
    }


    /**
     * 设置正在包装的响应
     *
     * @param response - 新包装的响应
     */
    @Override
    public void setResponse(ServletResponse response) {
        super.setResponse(response);
    }


	// -------------------------------------------------------------------------------------
	// 包方法
	// -------------------------------------------------------------------------------------
    /**
     * 设置此响应的 included 标志
     *
     * @param included - 新的 included 标志
     */
    void setIncluded(boolean included) {
        this.included = included;
    }
}
