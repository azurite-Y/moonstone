package org.zy.moonStone.core.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.Globals;
import org.zy.moonStone.core.http.Response;
import org.zy.moonStone.core.interfaces.container.Context;
import org.zy.moonStone.core.security.SecurityUtil;
import org.zy.moonStone.core.session.SessionConfig;
import org.zy.moonStone.core.session.interfaces.Session;
import org.zy.moonStone.core.util.RequestUtil;
import org.zy.moonStone.core.util.buf.CharChunk;
import org.zy.moonStone.core.util.buf.UriUtil;
import org.zy.moonStone.core.util.http.ActionCode;
import org.zy.moonStone.core.util.http.FastHttpDateFormat;
import org.zy.moonStone.core.util.http.MimeHeaders;
import org.zy.moonStone.core.util.http.parser.MediaTypeCache;
import org.zy.moonStone.core.util.security.Escape;

/**
 * @dateTime 2022年6月16日;
 * @author zy(azurite-Y);
 * @description {@link Response } 的包装类
 */
public class HttpResponse implements HttpServletResponse {
    private static final Logger logger = LoggerFactory.getLogger(Response.class);

    private static final MediaTypeCache MEDIA_TYPE_CACHE = new MediaTypeCache(100);

    /**
     * 符合SRV.15.2.22.1。如果没有指定字符编码，则调用response.getwriter()将导致后续调用response.getcharacterencoding()返回ISO-8859-1，
     * 并且Content-Type响应头将包含一个charset=ISO-8859-1组件。
     * 
     */
    private static final boolean ENFORCE_ENCODING_IN_GET_WRITER = true;
    
    /** 关联的原初响应 */
    protected Response response;
    
    /** 应用程序提交标识 */
    protected boolean appCommitted = false;

    /** 关联的输出缓冲区 */
    protected OutputBuffer outputBuffer;
    
    /** 输出缓冲区尺寸 */
    private final int outputBufferSize;

    /** 关联的输出流 */
    protected ServletByteOutputStream outputStream;

    /** 相关的 writer */
    protected CharBufferWriter writer;
    
    /** 包含的标志 */
    protected boolean included = false;
    
    /** 已设置字符编码集的标识 */
    private boolean isCharacterEncodingSet = false;

    /** OutputStream 流使用的标识 */
    protected boolean usingOutputStream = false;

    /** writer 流使用的标识 */
    protected boolean usingWriter = false;

    /** 存放重定向URL的可回收缓冲区 */
    protected final CharChunk redirectURLCC = new CharChunk();

    /** 不是严格要求，但如果在响应被回收之前保留这些请求，则生成 HTTP/2 推送请求会容易得多 */
    private final List<Cookie> cookies = new ArrayList<>();

    private HttpServletResponse applicationResponse = null;

    /** 与此响应关联的请求 */
    protected HttpRequest httpRequest = null;
    
    /** 与此响应关联的外观 */
    protected ResponseFacade facade = null;
    
    
	// -------------------------------------------------------------------------------------
	// 构造器方法
	// -------------------------------------------------------------------------------------
    public HttpResponse() {
        this(OutputBuffer.DEFAULT_BUFFER_SIZE);
    }

    public HttpResponse(int outputBufferSize) {
    	this.outputBufferSize = outputBufferSize;
    	this.outputBuffer = new OutputBuffer(outputBufferSize);
    }
    
    
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
    /**
     * 释放所有对象引用，初始化实例变量，为重用该对象做准备。
     */
    public void recycle() {
        cookies.clear();
        outputBuffer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        included = false;
        isCharacterEncodingSet = false;

        applicationResponse = null;
        if (Globals.IS_SECURITY_ENABLED || Connector.RECYCLE_FACADES) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (outputStream != null) {
                outputStream.clear();
                outputStream = null;
            }
            if (writer != null) {
                writer.clear();
                writer = null;
            }
        } else if (writer != null) {
            writer.recycle();
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 响应方法
	// -------------------------------------------------------------------------------------
    /**
     * 设置原初响应对象
     * 设置与此响应关联的原初
     *
     * @param response - 原初响应对象
     */
    public void setResponse(Response response) {
        this.response = response;
        this.outputBuffer.setResponse(response);
    }
    /**
     * @return 原初响应
     */
    public Response getResponse() {
        return this.response;
    }
    
    /**
     * @return 与此响应关联的请求
     */
    public HttpRequest getHttpRequest() {
        return this.httpRequest;
    }
    /**
     * 设置与此响应关联的请求对象
     *
     * @param httpRequest - 关联的请求对象
     */
    public void setHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }
    
    /**
     * @return the <code>ServletResponse</code>的外观
     */
    public HttpServletResponse getHttpServletResponse() {
        if (facade == null) {
            facade = new ResponseFacade(this);
        }
        if (applicationResponse == null) {
            applicationResponse = facade;
        }
        return applicationResponse;
    }
    /**
     * 设置一个包装好的HttpServletResponse传递给应用程序。封装响应的组件应该通过getHttpServletResponse()获取响应，封装它，然后用封装的响应调用这个方法。
     * 
     * @param applicationResponse - 传递给应用程序的包装响应
     */
    public void setHttpServletResponse(HttpServletResponse applicationResponse) {
        // 检查包装器包装这个请求
        ServletResponse r = applicationResponse;
        while (r instanceof HttpServletResponseWrapper) {
            r = ((HttpServletResponseWrapper) r).getResponse();
        }
        if (r != facade) {
            throw new IllegalArgumentException("非法的 wrapper");
        }
        this.applicationResponse = applicationResponse;
    }
    
    /**
     * @return 处理此请求的上下文
     */
    public Context getContext() {
        return httpRequest.getContext();
    }
    
    public List<Cookie> getCookies() {
        return cookies;
    }

    /**
     * @return 应用程序实际写入输出流的字节数。这不包括分块、压缩等以及响应头
     */
    public long getContentWritten() {
        return outputBuffer.getContentWritten();
    }

    /**
     * @return 实际写入套接字的字节数。这包括分块、压缩等，但不包括响应头
     * @param flush - 如果为<code>true</code>，将首先执行缓冲区刷新
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            try {
                outputBuffer.flush();
            } catch (IOException ioe) {
                // 忽略-客户端可能已经关闭了连接
            }
        }
        return getResponse().getBytesWritten(flush);
    }
    
    /**
     * 设置应用程序提交标志
     *
     * @param appCommitted - 新的应用程序提交标志值
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }
    /**
     *
     * @return 如果应用程序已提交响应，则为<code>true</code>
     */
    public boolean isAppCommitted() {
        return this.appCommitted || isCommitted() || isSuspended() || ((getContentLength() > 0) && (getContentWritten() >= getContentLength()));
    }
    
    /**
     * 设置挂起标志
     *
     * @param suspended - 新的挂起标志值
     */
    public void setSuspended(boolean suspended) {
        outputBuffer.setSuspended(suspended);
    }
    /**
     * @return 如果响应被暂停，则为 <code>true</code>
     */
    public boolean isSuspended() {
        return outputBuffer.isSuspended();
    }

    /**
     *
     * @return 如果响应已关闭，则为 <code>true</code>
     */
    public boolean isClosed() {
        return outputBuffer.isClosed();
    }

    /**
     *
     * @return 如果已经设置了错误标志，则为 false<code>false</code>
     */
    public boolean setError() {
        return getResponse().setError();
    }
    /**
     *
     * @return 如果响应遇到错误，则为 <code>true</code>
     */
    public boolean isError() {
        return getResponse().isError();
    }

    /**
     * 是否需要错误报告，需要则为true
     */
    public boolean isErrorReportRequired() {
        return getResponse().isErrorReportRequired();
    }
    /**
     * 设置需错误报告的标识，返回true则代表设置成功
     */
    public boolean setErrorReported() {
        return getResponse().setErrorReported();
    }

    /**
     * 在单个操作中执行刷新和关闭输出流或写入器所需的任何操作
     *
     * @exception IOException - 如果出现输入/输出错误
     */
    public void finishResponse() throws IOException {
        // 写剩余的字节
        outputBuffer.close();
    }

    /**
     * @return 为此响应设置或计数的内容长度
     */
    public int getContentLength() {
        return getResponse().getContentLength();
    }
    
    // -------------------------------------------------------------------------------------
	// ServletRequest 方法
	// -------------------------------------------------------------------------------------
    /**
     * 返回用于在此响应中发送的 MIME 正文的内容类型。 在提交响应之前，必须使用 setContentType 指定正确的内容类型。 
     * 如果未指定内容类型，则此方法返回 null。如果已指定内容类型，并且已按 getCharacterEncoding 或 getWriter 中的说明显式或隐式指定字符编码，
     * 则返回的字符串中包含 charset 参数。如果没有字符 已指定编码，则省略字符集参数。
     * @return 指定内容类型的字符串，例如 text/html； charset=UTF-8，或 null
     */
    @Override
    public String getContentType() {
        return getResponse().getContentType();
    }
    /**
     * 设置此响应的内容类型
     *
     * @param type - 新的内容类型
     */
    @Override
    public void setContentType(String type) {
        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        if (type == null) {
            getResponse().setContentType(null);
            return;
        }

        String[] m = MEDIA_TYPE_CACHE.parse(type);
        if (m == null) {
            // 无效-假设没有字符集，只传递用户提供的任何内容
            getResponse().setContentTypeNoCharset(type);
            return;
        }

        getResponse().setContentTypeNoCharset(m[0]);

        if (m[1] != null) {
            // 如果已调用getWriter()，则忽略字符集
            if (!usingWriter) {
                try {
                    getResponse().setCharacterEncoding(m[1]);
                } catch (UnsupportedEncodingException e) {
                    logger.warn(String.format("无效的字符集编码，by {}", m[1]), e);
                }

                isCharacterEncodingSet = true;
            }
        }
    }
    
    /**
     * 设置此响应的内容长度（以字节为单位）。
     *
     * @param length - 新的内容长度
     */
    @Override
    public void setContentLength(int length) {
        setContentLengthLong(length);
    }
    /**
     * 设置响应中内容正文的长度 在 HTTP servlet 中，此方法使用 HTTP Content-Length 请求头。
     */
    @Override
    public void setContentLengthLong(long length) {
        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        getResponse().setContentLength(length);
    }
    
	@Override
    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
        getResponse().setTrailerFields(supplier);
    }
	
    @Override
    public Supplier<Map<String, String>> getTrailerFields() {
        return getResponse().getTrailerFields();
    }
    
    /**
     * 使用 setLocale 方法返回为此响应指定的语言环境。 提交响应后对 setLocale 的调用无效。 如果没有指定区域设置，则返回容器的默认区域设置。
     * 
     * @return 分配给此响应的语言环境
     */
    @Override
    public Locale getLocale() {
        return getResponse().getLocale();
    }
    
    /**
	 * 设置适合此响应的区域设置，包括设置适当的字符编码。
	 * <p>
	 * 如果尚未提交响应，则设置响应的区域设置。它还为区域设置适当地设置响应的字符编码，如果尚未使用 setContentType 或 setCharacterEncoding 显式设置字符编码，则尚未调用 getWriter，并且尚未提交响应。
	 * 如果部署描述符包含 loc1ale-enco1ding -mapping-list 元素，并且该元素提供给定语言环境的映射，使用该映射。否则，从语言环境到字符编码的映射取决于容器。
	 * <p>
	 * 可以重复调用此方法来更改语言环境和字符编码。如果在响应提交后调用该方法无效。
	 * 如果在使用字符集规范调用 setContentType、调用 setCharacterEncoding、调用 getWriter 或提交响应之后调用它，则不会设置响应的字符编码。
	 * <p>
	 * 如果协议提供了这样做的方法，容器必须将用于 servlet 响应的编写器的语言环境和字符编码传达给客户端。
	 * 在 HTTP 的情况下，区域设置通过 Content-Language 标头进行通信，字符编码作为文本媒体类型的 Content-Typeheader 的一部分。
	 * 请注意，如果 servlet 未指定内容类型，则字符编码无法通过 HTTP 标头进行通信；但是，它仍然用于对通过 servlet 响应的编写器编写的文本进行编码。
	 *
	 * @param locale The new locale
	 */
	@Override
	public void setLocale(Locale locale) {
	    if (isCommitted()) {
	        return;
	    }
	
	    // 忽略来自包含的servlet的任何调用
	    if (included) {
	        return;
	    }
	
	    getResponse().setLocale(locale);
	
	    // 忽略getWriter被调用后的任何调用。应该使用默认值
	    if (usingWriter) {
	        return;
	    }
	
	    if (isCharacterEncodingSet) {
	        return;
	    }
	
	    // 在某些错误处理场景中，上下文是未知的（例如，当 ROOT 上下文不存在时出现 404）
	    Context context = getContext();
	    if (context != null) {
	        String charset = context.getCharset(locale);
	        if (charset != null) {
	            try {
	                getResponse().setCharacterEncoding(charset);
	            } catch (UnsupportedEncodingException e) {
                    logger.warn(String.format("无效的字符集编码，by {}", charset), e);
	            }
	        }
	    }
	}

	/**
     * 此响应的输出是否已提交？
     *
     * @return 如果响应已提交，则为 <code>true</code>
     */
    @Override
    public boolean isCommitted() {
        return getResponse().isCommitted();
    }

    /**
     * 清除缓冲区中存在的任何数据以及状态码、标题。调用getWriter或getOutputStream的状态也被清除。它是合法的，例如，调用getWriter，reset，然后getOutputStream。
     * 如果getWriter或getOutputStream在此方法之前被调用，则相应返回的Writer或OutputStream将被关闭，并且使用过时对象的行为未定义。如果已经提交了响应，该方法会抛出一个IllegalStateException。
     * <p>
     * 清除写入缓冲区的任何内容
     * 
     * @exception IllegalStateException - 如果响应已经提交
     */
    @Override
    public void reset() {
        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        getResponse().reset();
        outputBuffer.reset();
        usingOutputStream = false;
        usingWriter = false;
        isCharacterEncodingSet = false;
    }

    /**
     * 清除响应中底层缓冲区的内容，而不清除标头或状态代码。 如果已提交响应，则此方法将引发 IllegalStateException。
     * <p>
     * 重置数据缓冲区，但不重置任何状态或请求头信息
     *
     * @exception IllegalStateException - 如果响应已经提交
     */
    @Override
    public void resetBuffer() {
        resetBuffer(false);
    }

    /**
     * 重置数据缓冲区和 Writer/Stream 使用标志，但不重置任何状态或标头信息。
     *
     * @param resetWriterStreamFlags - 如果内部 <code>usingWriter</code>, <code>usingOutputStream</code>, <code>isCharacterEncodingSet</code> 标志也应重置，则为 true
     *
     * @exception IllegalStateException - 如果响应已经提交
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {
        if (isCommitted()) {
            throw new IllegalStateException("响应已提交，无法重置缓冲区");
        }

        outputBuffer.reset();

        if(resetWriterStreamFlags) {
            usingOutputStream = false;
            usingWriter = false;
            isCharacterEncodingSet = false;
        }
    }

    /**
     * 设置响应正文的首选缓冲区大小。servlet 容器将使用至少与请求大小一样大的缓冲区。 使用 getBufferSize 可以获得实际使用的缓冲区大小。
     * <p>
     * 更大的缓冲区允许在实际发送任何内容之前写入更多内容，从而为 servlet 提供更多时间来设置适当的状态代码和标头。 
     * 较小的缓冲区会减少服务器内存负载并允许客户端更快地开始接收数据。
     * <p>
     * 必须在写入任何响应正文内容之前调用此方法； 如果已写入内容或已提交响应对象，则此方法将引发 IllegalStateException。
     *
     * @param size - 新的缓冲区大小
     * @exception IllegalStateException - 如果在为此响应提交输出后调用此方法
     */
    @Override
    public void setBufferSize(int size) {
        if (isCommitted() || !outputBuffer.isNew()) {
            throw new IllegalStateException("当前响应已提交或缓冲区已被使用，无法设置缓冲区大小");
        }

        outputBuffer.setBufferSize(size);
    }
    /**
	 * 返回用于响应的实际缓冲区大小。 如果不使用缓冲，则此方法返回 0。
	 * 
	 * @return 实际使用的缓冲区大小
	 */
	@Override
	public int getBufferSize() {
        return outputBuffer.getBufferSize();
	}

	/**
     * 返回用于在此响应中发送的正文的字符编码（MIME 字符集）的名称。
     * 返回这些方法中的第一个产生结果的方法。根据请求，可以使用 setCharacterEncoding 和 setContentType 方法显式指定响应的字符集, 或隐式使用 setLocale(java.util.Locale) 方法。
     * 显式规范优先于隐式规范。在调用 getWriter 或提交响应后对这些方法的调用对字符编码没有影响。如果未指定字符编码，则返回 ISO-8859-1。
     * 
     * 有关字符编码和 MIME 的更多信息，请参阅 RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt)。
     * 
     * @return 一个字符串，指定字符编码的名称，例如 UTF-8
     */
	@Override
	public String getCharacterEncoding() {
		String charset = getResponse().getCharacterEncoding();
        if (charset != null) {
            return charset;
        }

        Context context = getContext();
        String result = null;
        if (context != null) {
            result =  context.getResponseCharacterEncoding();
        }

        if (result == null) {
            result = Globals.DEFAULT_BODY_CHARSET.name();
        }

        return result;
	}
	/**
     * 覆盖请求正文中使用的字符编码的名称。 必须在读取请求参数或使用 getReader() 读取输入之前调用此方法。
     *
     * @param charset - 包含字符编码名称的字符串
     */
    @Override
    public void setCharacterEncoding(String charset) {
        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        // 在调用 getWriter 后忽略任何调用
        if (usingWriter) {
            return;
        }

        try {
            getResponse().setCharacterEncoding(charset);
        } catch (UnsupportedEncodingException e) {
            logger.warn(String.format("无效的字符集编码，by {}", charset), e);
            return;
        }
        isCharacterEncodingSet = true;
    }

	/**
	 * 返回适合在响应中写入二进制数据的 ServletOutputStream。 servlet 容器不对二进制数据进行编码。
	 * <p>
	 * 在 ServletOutputStream 上调用 flush() 会提交响应。可以调用此方法或 getReader 来读取正文，但不能同时调用两者。除非已调用 reset 。
	 * 
	 * @return 用于写入二进制数据的 ServletOutputStream
	 * @throws IOException - 如果发生输入或输出异常
	 */
	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (usingWriter) throw new IllegalStateException("已获取字符流(PrintWriter) 以写入响应数据，不能再获取字节流(ServletOutputStream)");

        usingOutputStream = true;
        if (outputStream == null) {
        	if (outputBuffer == null ) {
        		outputBuffer = new OutputBuffer(this.outputBufferSize);
        		outputBuffer.setResponse(response);
        	}
        	outputBuffer.setUseByteBuffer(true);
        	
            outputStream = new ServletByteOutputStream(outputBuffer);
        }
        return outputStream;
	}

	/**
	 * 返回一个可以向客户端发送字符文本的 PrintWriter 对象。PrintWriter 使用 getCharacterEncoding 返回的字符编码。
	 * 如果没有按照 getCharacterEncoding 中的描述指定响应的字符编码（即该方法只返回默认值 ISO-8859-1）， getWriter 将其更新为 ISO-8859-1。
	 * 
	 * @return 一个可以向客户端返回字符数据的 PrintWriter 对象
	 * @throws IOException - 如果发生输入或输出异常
	 */
	@Override
	public PrintWriter getWriter() throws IOException {
		if (usingOutputStream)  throw new IllegalStateException("已获取字节流(ServletOutputStream) 以写入响应数据，不能再获取字符流(PrintWriter)");

		if (ENFORCE_ENCODING_IN_GET_WRITER) {
			/*
			 * 如果响应的字符编码没有按照getCharacterEncoding(即，该方法只返回默认值ISO-8859-1)，
			 * getWriter将其更新为ISO-8859-1(结果是后续调用getContentType()将包括一个字符集=ISO-8859-1组件，
			 * 该组件也将反映在Content-Type响应头中，从而满足Servlet规范的要求，即容器必须将用于Servlet响应 Writer 的字符编码传递给客户端)。
			 */
			setCharacterEncoding(getCharacterEncoding());
		}

		usingWriter = true;
		if (writer == null) {
			if (outputBuffer == null ) {
        		outputBuffer = new OutputBuffer(this.outputBufferSize);
        		outputBuffer.setResponse(response);
        	}
			outputBuffer.setUseByteBuffer(false);
			writer = new CharBufferWriter(outputBuffer);
		}
		return writer;
	}

	/**
	 * 返回一个PrintWriter，该PrintWriter可用于呈现错误消息，而不管是否已获取 stream 或 writer 。
	 *
	 * @return 可用于错误报告的写入器。
	 * 如果响应不是使用sendError返回的错误报告，或者不是由servlet处理期间抛出的意外异常触发的(仅在这种情况下)，那么如果响应流已经被使用，将返回null。
	 *
	 * @exception IOException if an input/output error occurs
	 */
	public PrintWriter getReporter() throws IOException {
	    if (outputBuffer.isNew()) {
	        if (writer == null) {
	        	if (outputBuffer == null ) {
	        		outputBuffer = new OutputBuffer(this.outputBufferSize);
	        		outputBuffer.setResponse(response);
	        	}
	        	outputBuffer.setUseByteBuffer(false);
	            writer = new CharBufferWriter(outputBuffer);
	        }
	        return writer;
	    } else {
	        return null;
	    }
	}

	/**
	 * 强制将缓冲区中的任何内容写入客户端。 调用此方法会自动提交响应，这意味着将写入状态码和响应头。
	 * 
	 * @throws IOException - 如果无法完成刷新缓冲区的操作
	 */
	@Override
	public void flushBuffer() throws IOException {
        outputBuffer.flush();
	}

	// -------------------------------------------------------------------------------------
	// HttpResponse 方法
	// -------------------------------------------------------------------------------------
    /**
	 * 获取具有给定名称的响应头的值。
	 * <p>
	 * 如果具有给定名称的响应头存在并且包含多个值，则将返回首先添加的值。
	 * <p>
	 * 此方法仅考虑分别通过 setHeader、addHeader、setDateHeader、addDateHeader、setIntHeader 或 addIntHeader 设置或添加的响应标头。
	 * 
	 * @param name - 要返回其值的响应标头的名称
	 * @return 具有给定名称的响应头的值，如果没有在此响应上设置具有给定名称的响应头，则返回 null
	 */
	@Override
    public String getHeader(String name) {
        return getResponse().getMimeHeaders().getHeaderValue(name);
    }

    /**
	 * 获取具有给定名称的响应头的值。
	 * <p>
	 * 此方法仅考虑分别通过 {@link #setHeader}、{@link #addHeader}、{@link #setDateHeader}、{@link #addDateHeader}、{@link #setIntHeader} 或 {@link #addIntHeader} 设置或添加的响应头。
	 * <p>
	 * 对返回的 <code>Collection</code> 的任何更改都不得影响此 <code>HttpServletResponse</code>。
	 * 
	 * @param name - 要返回其值的响应头的名称
	 * @return 具有给定名称的响应头的值的集合（可能为空）
	 */
	@Override
    public Collection<String> getHeaderNames() {
        MimeHeaders headers = getResponse().getMimeHeaders();
        int n = headers.size();
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(headers.getHeadName(i).toString());
        }
        return result;

    }

	/**
	 * 获取具有给定名称的响应标头的值。
	 * <p>
	 * 此方法仅考虑分别通过 {@link #setHeader}、{@link #addHeader}、{@link #setDateHeader}、{@link #addDateHeader}、{@link #setIntHeader} 或 {@link #addIntHeader} 设置或添加的响应标头。
	 * <p>
	 * 对返回的 <code>Collection</code> 的任何更改都不得影响此 <code>HttpServletResponse</code>。
	 * 
	 * @param name - 要返回其值的响应头名称
	 * @return 具有给定名称的响应头的值的集合（可能为空）
	 */
	@Override
    public Collection<String> getHeaders(String name) {
        Enumeration<String> enumeration = getResponse().getMimeHeaders().values(name);
        Set<String> result = new LinkedHashSet<>();
        while (enumeration.hasMoreElements()) {
            result.add(enumeration.nextElement());
        }
        return result;
    }

	/**
	 * 设置具有给定名称和值的响应标头。如果标头已设置，则新值将覆盖先前的值。 containsHeader 方法可用于在设置响应头值之前测试响应头是否存在。
	 * @param name - 响应头的名称
	 * @param value - 响应头值。如果包含八位字节字符串，则应根据 RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt) 进行编码
	 */
	@Override
	public void setHeader(String name, String value) {
		if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        char cc=name.charAt(0);
        if (cc=='C' || cc=='c') {
            if (checkSpecialHeader(name, value)) {
                return;
            }
        }

        getResponse().setHeader(name, value);		
	}

    /**
     * {@link org.zy.moonStone.core.http.Response} 中存在此的扩展版本。
     * 此处需要此检查以确保应用 {@link #setContentType(String)} 中的 usingWriter 检查，因为 usingWriter 对 {@link org.zy.moonStone.core.http.Response} 不可见。
     * <p>
     * 从 set/addHeader 的调用
     * 
     * @return 如果响应头特殊，则为 <code>true</code>，无需设置标头
     */
    private boolean checkSpecialHeader(String name, String value) {
        if (name.equalsIgnoreCase("Content-Type")) { // 检查设置响应头，确保字符集编码能被正确设置
            setContentType(value);
            return true;
        }
        return false;
    }
	
	/**
	 * 设置具有给定名称和整数值的响应头。 如果已经设置了响应头，则新值将覆盖前一个值。 containsHeader 方法可用于在设置响应头值之前测试响应头是否存在。
	 * 
	 * @param name - 响应头的名称
	 * @param value - 分配的整数值
	 */
	@Override
	public void setIntHeader(String name, int value) {
		if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        addHeader(name, "" + value);		
	}

	/**
	 * 添加具有给定名称和值的响应标头。此方法允许响应标头具有多个值。
	 * 
	 * @param name - 响应头的名称
	 * @param value 附加响应头值。如果它包含八位字节字符串，则应根据 RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt) 对其进行编码
	 */
	@Override
	public void addHeader(String name, String value) {
        addHeader(name, value, null);
	}
	
	private void addHeader(String name, String value, Charset charset) {

        if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        char cc=name.charAt(0);
        if (cc=='C' || cc=='c') {
            if (checkSpecialHeader(name, value))
            return;
        }

        getResponse().addHeader(name, value, charset);
    }

	/**
	 * 添加具有给定名称和整数值的响应头。 此方法允许响应头具有多个值。
	 * 
	 * @param name  - 响应头的名称
	 * @param value - 分配的整数值
	 */
	@Override
	public void addIntHeader(String name, int value) {
		if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        addHeader(name, "" + value);		
	}

	/**
	 * 设置具有给定名称和日期值的响应头。 日期以自纪元以来的毫秒数指定。 如果已经设置了标头，则新值将覆盖前一个值。 containsHeader 方法可用于在设置响应头值之前测试响应头是否存在。
	 * @param name - 要设置的 header 名称
	 * @param date - 分配的日期值
	 */
	@Override
	public void setDateHeader(String name, long date) {
		if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        setHeader(name, FastHttpDateFormat.formatDate(date));		
	}

	/**
	 * 添加具有给定名称和日期值的响应标头。 日期以自纪元以来的毫秒数指定。 此方法允许响应头具有多个值。 
	 * 
	 * @param name - 要设置的 header 名称
	 * @param date - 附加日期值
	 */
	@Override
	public void addDateHeader(String name, long date) {
		if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        addHeader(name, FastHttpDateFormat.formatDate(date));		
	}

	/**
	 * 返回一个布尔值，指示是否已设置命名的响应标头
	 * 
	 * @param name - 响应头名称
	 * @return 返回一个布尔值，指示是否已设置命名的响应标头
	 */
	@Override
	public boolean containsHeader(String name) {
		// 由于在  org.zy.moonStone.core.http.Response 中对它们进行了特殊处理，因此需要对 Content-Type 和 Content-Length 进行特殊处理
        char cc=name.charAt(0);
        if(cc=='C' || cc=='c') {
            if(name.equalsIgnoreCase("Content-Type")) {
                // 如果尚未设置，将返回 null
                return (getResponse().getContentType() != null);
            }
            if(name.equalsIgnoreCase("Content-Length")) {
                // -1 表示未知且不发送给客户端
                return (getResponse().getContentLengthLong() != -1);
            }
        }

        return getResponse().containsHeader(name);
	}

	/**
	 * 获取此响应的当前状态代码。
	 * 
	 * @return 此响应的当前状态码
	 */
	@Override
	public int getStatus() {
		return getResponse().getStatus();
	}

	/**
	 * 设置此响应的状态代码。
	 * <p>
	 * 此方法用于设置没有错误时的返回状态码（例如，对于 SC_OK 或 SC_MOVED_TEMPORARILY 状态码）。
	 * <p>
	 * 如果使用该方法设置错误码，则不会触发容器的错误页面机制。 如果出现错误并且调用者希望调用 web 应用程序中定义的错误页面，则必须使用 sendError 代替。
	 * <p>
	 * 此方法保留所有 cookie 和其他响应标头。
	 * <p>
	 * 有效的状态代码是 2XX、3XX、4XX 和 5XX 范围内的代码。其他状态代码被视为特定于容器。
	 * 
	 * @param status - 状态码
	 */
	@Override
	public void setStatus(int status) {
        response.setStatus(status);
	}

	/**
	 * @param sc - 错误状态码
	 * @param msg - 描述性消息
	 * @deprecated 从 Java Servlet API 的 2.1 版开始，由于 message 参数的含义不明确，此方法已被弃用。
	 */
	@Override
	@Deprecated
	public void setStatus(int status, String message) {
		if (isCommitted()) {
            return;
        }

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        getResponse().setStatus(status);
        getResponse().setMessage(message);
	}

	/**
	 * @return 使用 sendError() 为此响应设置的错误消息
	 */
	public String getMessage() {
	    return getResponse().getMessage();
	}

	/**
	 * 将指定的 cookie 添加到响应中。 可以多次调用此方法来设置多个cookie。
	 * @param cookie - 返回给客户端的 Cookie
	 */
	@Override
	public void addCookie(Cookie cookie) {
		// 忽略来自包含的 servlet 的任何调用
        if (included || isCommitted()) {
            return;
        }

        cookies.add(cookie);

        String header = generateCookieString(cookie);
        // 如果到达这里，没有例外，cookie 是有效的，响应头名称是 Set-Cookie 对于“旧”和 v.1 (RFC2109) 浏览器不支持 RFC2965，Servlet 规范要求 2109。
        addHeader("Set-Cookie", header, getContext().getCookieProcessor().getCharset());		
	}
	
	/**
     * 添加会话 cookie 的特殊方法
     *
     * @param cookie - 添加响应的新会话 cookie
     */
    public void addSessionCookieInternal(final Cookie cookie) {
        if (isCommitted()) {
            return;
        }

        String name = cookie.getName();
        final String headername = "Set-Cookie";
        final String startsWith = name + "=";
        String header = generateCookieString(cookie);
        boolean set = false;
        MimeHeaders headers = getResponse().getMimeHeaders();
        int n = headers.size();
        for (int i = 0; i < n; i++) {
            if (headers.getHeadName(i).toString().equals(headername)) {
                if (headers.getHeadValue(i).toString().startsWith(startsWith)) {
                    headers.getHeadValue(i).setString(header);
                    set = true;
                }
            }
        }
        if (!set) {
            addHeader(headername, header);
        }
    }

    public String generateCookieString(final Cookie cookie) {
        // Web 应用程序代码可以从 generateHeader() 调用中接收到 IllegalArgumentException
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return AccessController.doPrivileged(new PrivilegedGenerateCookieString(getContext(), cookie, httpRequest.getHttpServletRequest()));
        } else {
            return getContext().getCookieProcessor().generateHeader(cookie, httpRequest.getHttpServletRequest());
        }
    }
	
	/**
	 * 通过包含会话 ID 对指定的 URL 进行编码，或者如果不需要编码，则原样返回 URL。该方法的实现包括判断 URL 中是否需要对会话 ID 进行编码的逻辑。
	 * 例如，如果浏览器 支持 cookie，或者会话跟踪被关闭，URL 编码是不必要的。
	 * <p>
	 * 对于强大的会话跟踪，servlet 发出的所有 URL 都应通过此方法运行。 否则，不支持 cookie 的浏览器无法使用 URL 重写。
	 * <p>
	 * 如果 URL 是相对的，它总是相对于当前的 HttpServletRequest。
	 * 
	 * @param url - 要编码的 url
	 * @return 如果需要编码，则为编码的 URL；否则为未更改的 URL。
	 */
	@Override
	public String encodeURL(String url) {
		String absolute;
        try {
            absolute = toAbsolute(url);
        } catch (IllegalArgumentException iae) {
            // 相对URL
            return url;
        }

        if (isEncodeable(absolute)) {
            // W3C规范明确表示
            if (url.equalsIgnoreCase("")) {
                url = absolute;
            } else if (url.equals(absolute) && !hasPath(url)) {
                url += '/';
            }
            return toEncoded(url, httpRequest.getSessionInternal().getIdInternal());
        } else {
            return url;
        }
	}

	/**
	 * 编码指定的 URL 以在 sendRedirect 方法中使用，或者，如果不需要编码，则返回未更改的 URL。 该方法的实现包括确定会话 ID 是否需要在 URL 中编码的逻辑。 
	 * 例如，如果浏览器支持 cookie，或者会话跟踪被关闭，则 URL 编码是不必要的。 因为做出这个决定的规则与决定是否对一个正常链接进行编码的规则不同，所以这个方法与 encodeURL 方法是分开的。
	 * <p>
	 * 所有发送到 HttpServletResponse.sendRedirect 方法的 URL 都应该通过这个方法运行。 否则，不支持 cookie 的浏览器无法使用 URL 重写。
	 * <p>
	 * 如果 URL 是相对的，它总是相对于当前的 HttpServletRequest。
	 * 
	 * @param url - 要编码的重定向 url
	 * @return 如果需要编码，则为编码的 URL；否则为未更改的 URL
	 */
	@Override
	public String encodeRedirectURL(String url) {
        if (isEncodeable(toAbsolute(url))) {
            return toEncoded(url, httpRequest.getSessionInternal().getIdInternal());
        } else {
            return url;
        }
	}

	/**
	 * @param url - 要编码的 url
	 * @return 如果需要编码，则为编码的 URL；否则为未更改的 URL
	 */
	@Override
    @Deprecated
	public String encodeUrl(String url) {
		return encodeURL(url);
	}

	/**
	 * @param url - 要编码的 url
	 * @return 如果需要编码，则为编码的 URL；否则为未更改的 URL
	 */
	@Override
    @Deprecated
	public String encodeRedirectUrl(String url) {
		return encodeRedirectURL(url);
	}

	/**
     * 发送对请求的确认
     *
     * @exception IOException - 如果发生输入/输出错误
     */
    public void sendAcknowledgement() throws IOException {
        if (isCommitted()) {
            return;
        }

        // 忽略来自包含的servlet的任何调用
        if (included) {
            return;
        }

        getResponse().action(ActionCode.ACK, null);
    }
	
	/**
	 * 使用指定状态向客户端发送错误响应并清除缓冲区。服务器默认创建响应，使其看起来像包含指定消息的 HTML 格式的服务器错误页面，将内容类型设置为“text/html”。
	 * 调用者不负责对消息进行转义或重新编码以确保它对于当前响应编码和内容类型是安全的。这方面的安全是容器的责任，因为它正在生成包含消息的错误页面。
	 * 服务器将保留 cookie，并可能清除或更新将错误页面作为有效响应提供服务所需的任何标头。
	 * <p>
	 * 如果针对传入的状态码对应的 web 应用程序进行了错误页面声明，它将优先于建议的 msg 参数返回，并且 msg 参数将被忽略。
	 * <p>
	 * 如果响应已经提交，则此方法抛出 IllegalStateException。使用此方法后，应将响应视为已提交，不应写入。
	 * 
	 * @param status - 错误状态码
	 * @param message - 描述性消息
	 * @throws IOException - 如果发生输入或输出异常
	 */
	@Override
	public void sendError(int status, String message) throws IOException {
		if (isCommitted()) throw new IllegalStateException("响应已提交，无法再使用指定状态向客户端发送错误响应");

        // 忽略来自包含的 servlet 的任何调用
        if (included) {
            return;
        }

        setError();

        getResponse().setStatus(status);
        getResponse().setMessage(message);

        // 清除任何已缓冲的数据内容
        resetBuffer();

        // 导致响应完成（从应用程序的角度来看）
        setSuspended(true);
	}

	/**
	 * 使用指定的状态码向客户端发送错误响应并清除缓冲区。服务器将保留 cookie，并可能清除或更新将错误页面作为有效响应提供服务所需的任何标头。
	 * 如果已为 Web 做出错误页面声明 应用程序对应传入的状态码，会返回错误页面
	 * <p>
	 * 如果响应已经提交，则此方法抛出 IllegalStateException。使用此方法后，应将响应视为已提交，不应写入。
	 * 
	 * @param status - 错误状态码
	 * @throws IOException - 如果发生输入或输出异常
	 */
	@Override
	public void sendError(int status) throws IOException {
        sendError(status, null);
	}

	/**
	 * 使用指定的重定向位置 URL 向客户端发送临时重定向响应并清除缓冲区。缓冲区将被此方法设置的数据替换。
	 * 调用此方法将状态码设置为 SC_FOUND 302（已找到）。此方法可以接受相对 URL；servlet 容器必须在将响应发送到客户端之前将相对 URL 转换为绝对 URL。
	 * 如果该位置是相对的，没有前导“/”，则容器将其解释为相对于当前请求 URI。如果位置与前导“/”相关，则容器将其解释为相对于 servlet 容器根。
	 * 如果位置与两个前导“/”相关，则容器将其解释为网络路径引用（参见 RFC 3986：统一资源标识符 (URI)：通用语法，第 4.2 节“相对引用”）。
	 * <p>
	 * 如果响应已经提交，则此方法抛出 IllegalStateException。使用此方法后，应将响应视为已提交，不应写入。
	 * 
	 * @param location - 重定向位置 URL
	 * @throws IOException - 如果发生输入或输出异常
	 */
	@Override
	public void sendRedirect(String location) throws IOException {
        sendRedirect(location, SC_FOUND);
	}
	
	
	// -------------------------------------------------------------------------------------
	// 公共方法
	// -------------------------------------------------------------------------------------
	/**
     * 允许以 {@link HttpServletResponse#SC_FOUND} (302) 以外的状态发送重定向的内部方法。 不尝试验证状态代码。
     *
     * @param location - 要重定向到的位置 URL
     * @param status - 将发送的 HTTP 状态码
     * @throws IOException - 发生 IO 异常
     */
    public void sendRedirect(String location, int status) throws IOException {
        if (isCommitted()) throw new IllegalStateException("响应已提交，不允许再重定向");

        // 忽略来自包含的servlet的任何调用
        if (included) {
            return;
        }

        // 清除任何已缓冲的数据内容
        resetBuffer(true);

        // 生成到指定位置的临时重定向
        try {
            String locationUri;
            // 相对重定向需要 HTTP/1.1
            if (getHttpRequest().getRequest().getSupportsRelativeRedirects() && getContext().getUseRelativeRedirects()) {
                locationUri = location;
            } else {
                locationUri = toAbsolute(location);
            }
            setStatus(status);
            setHeader("Location", locationUri);
            if (getContext().getSendRedirectBody()) {
                PrintWriter writer = getWriter();
                writer.print( Escape.htmlElementContent(locationUri) );
                flushBuffer();
            }
        } catch (IllegalArgumentException e) {
            logger.warn(String.format("设置重定向失败，by url：{}", location), e);
            setStatus(SC_NOT_FOUND);
        }

        // 导致响应完成（从应用程序的角度来看）
        setSuspended(true);
    }
	
    
	// -------------------------------------------------------------------------------------
	// 保护方法
	// -------------------------------------------------------------------------------------
    /**
     * 转换(如果需要)并返回绝对URL，该URL表示这个可能的相对URL引用的资源。如果这个URL已经是绝对的，则不加修改地返回它。
     *
     * @param location - 要(可能)转换然后返回的URL
     * @return 编码后的URL
     * @exception IllegalArgumentException - 如果将相对URL转换为绝对URL时抛出MalformedURLException异常
     */
    protected String toAbsolute(String location) {
        if (location == null) {
            return location;
        }

        boolean leadingSlash = location.startsWith("/");

        if (location.startsWith("//")) {
            // 相对 Scheme
            redirectURLCC.recycle();
            // 添加的 scheme
            String scheme = httpRequest.getScheme();
            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append(':');
                redirectURLCC.append(location, 0, location.length());
                return redirectURLCC.toString();
            } catch (IOException e) {
                IllegalArgumentException iae = new IllegalArgumentException(location);
                iae.initCause(e);
                throw iae;
            }
        } else if (leadingSlash || !UriUtil.hasScheme(location)) {
            redirectURLCC.recycle();

            String scheme = httpRequest.getScheme();
            String name = httpRequest.getServerName();
            int port = httpRequest.getServerPort();

            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append("://", 0, 3);
                redirectURLCC.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
                    redirectURLCC.append(':');
                    String portS = port + "";
                    redirectURLCC.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = httpRequest.getDecodedRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    String encodedURI = null;
                    if (SecurityUtil.isPackageProtectionEnabled() ){
                        try{
                            encodedURI = AccessController.doPrivileged(new PrivilgedEncodeUrl(getCharacterEncoding(), relativePath, pos));
                        } catch (PrivilegedActionException pae){
                            IllegalArgumentException iae = new IllegalArgumentException(location);
                            iae.initCause(pae.getException());
                            throw iae;
                        }
                    } else {
                        encodedURI = URLEncoder.encode(new String(relativePath.toCharArray(), 0, pos), getCharacterEncoding());
                    }
                    redirectURLCC.append(encodedURI);
                    redirectURLCC.append('/');
                }
                redirectURLCC.append(RequestUtil.normalize(location));
            } catch (IOException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException(location);
                iae.initCause(e);
                throw iae;
            }

            return redirectURLCC.toString();
        } else {
            return location;
        }
    }
    
    /**
     * 如果应使用会话标识符对指定的URL进行编码，则返回 true。如果满足以下所有条件，则为真：
     * <ul>
     * 		<li>正在响应的请求要求一个有效的会话
     * 		<li>没有通过cookie接收到请求的会话ID
     * 		<li>指定的URL指向Web应用程序中响应此请求的某个位置
     * </ul>
     *
     * @param location - 要验证的绝对URL
     * @return 如果URL应该被编码，则为<code>true</code>
     */
    protected boolean isEncodeable(final String location) {
        if (location == null) {
            return false;
        }

        // 这是文档内引用吗?
        if (location.startsWith("#")) {
            return false;
        }
        
        // 是否处于没有使用cookie的有效会话中?
        final HttpRequest hreq = httpRequest;
        final Session session = hreq.getSessionInternal(false);
        if (session == null) {
            return false;
        }
        if (hreq.isRequestedSessionIdFromCookie()) {
            return false;
        }

        // 是否允许URL编码
        if (!hreq.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.URL)) {
            return false;
        }

        if (SecurityUtil.isPackageProtectionEnabled()) {
            Boolean result =  AccessController.doPrivileged(new PrivilegedDoIsEncodable(getContext(), hreq, session, location));
            return result.booleanValue();
        } else {
            return doIsEncodeable(getContext(), hreq, session, location);
        }
    }
    
    /**
     * 返回带有适当编码的指定会话标识符的指定URL
     *
     * @param url - 与会话id一起编码的URL
     * @param sessionId - 将包含在已编码URL中的会话id
     * @return 编码的URL
     */
    protected String toEncoded(String url, String sessionId) {
        if ((url == null) || (sessionId == null)) {
            return url;
        }

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if( sb.length() > 0 ) { // Jsessionid不能是第一个
            sb.append(";");
            sb.append(SessionConfig.getSessionUriParamName(httpRequest.getContext()));
            sb.append("=");
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return sb.toString();
    }
    
	// -------------------------------------------------------------------------------------
	// 私有方法
	// -------------------------------------------------------------------------------------
    private static boolean doIsEncodeable(Context context, HttpRequest hreq, Session session, String location) {
    	// 这是一个有效的绝对URL吗?
    	URL url = null;
    	try {
    		url = new URL(location);
    	} catch (MalformedURLException e) {
    		return false;
    	}

    	// 此URL是否与(并包括)上下文路径匹配？
    	if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol())) {
    		return false;
    	}
    	if (!hreq.getServerName().equalsIgnoreCase(url.getHost())) {
    		return false;
    	}
    	int serverPort = hreq.getServerPort();
    	if (serverPort == -1) {
    		if ("https".equals(hreq.getScheme())) {
    			serverPort = 443;
    		} else {
    			serverPort = 80;
    		}
    	}
    	int urlPort = url.getPort();
    	if (urlPort == -1) {
    		if ("https".equals(url.getProtocol())) {
    			urlPort = 443;
    		} else {
    			urlPort = 80;
    		}
    	}
    	if (serverPort != urlPort) {
    		return false;
    	}

    	String contextPath = context.getPath();
    	if (contextPath != null) {
    		String file = url.getFile();
    		if (!file.startsWith(contextPath)) {
    			return false;
    		}
    		String tok = ";" + SessionConfig.getSessionUriParamName(context) + "=" + session.getIdInternal();
    		if( file.indexOf(tok, contextPath.length()) >= 0 ) {
    			return false;
    		}
    	}

    	// 此URL属于Web应用程序，因此它是可编码的
    	return true;
    }
    
    /**
     * 确定绝对URL是否具有路径组件
     *
     * @param uri - 将被检查的URL
     * @return 如果URL有路径，则为<code>true</code>
     */
    private boolean hasPath(String uri) {
        int pos = uri.indexOf("://");
        if (pos < 0) {
            return false;
        }
        pos = uri.indexOf('/', pos + 3);
        if (pos < 0) {
            return false;
        }
        return true;
    }
    
    
	// -------------------------------------------------------------------------------------
	// 安全控制对象
	// -------------------------------------------------------------------------------------
    private static class PrivilegedGenerateCookieString implements PrivilegedAction<String> {
        private final Context context;
        private final Cookie cookie;
        private final HttpServletRequest request;

        public PrivilegedGenerateCookieString(Context context, Cookie cookie, HttpServletRequest request) {
            this.context = context;
            this.cookie = cookie;
            this.request = request;
        }

        @Override
        public String run(){
            return context.getCookieProcessor().generateHeader(cookie, request);
        }
    }
    
    private static class PrivilegedDoIsEncodable implements PrivilegedAction<Boolean> {
        private final Context context;
        private final HttpRequest httpRequest;
        private final Session session;
        private final String location;

        public PrivilegedDoIsEncodable(Context context, HttpRequest httpRequest, Session session, String location) {
            this.context = context;
            this.httpRequest = httpRequest;
            this.session = session;
            this.location = location;
        }

        @Override
        public Boolean run(){
            return Boolean.valueOf(doIsEncodeable(context, httpRequest, session, location));
        }
    }

    private static class PrivilgedEncodeUrl implements PrivilegedExceptionAction<String> {
        private final String characterEncoding;
        private final String relativePath;
        private final int end;

        public PrivilgedEncodeUrl(String characterEncoding, String relativePath, int end) {
            this.characterEncoding = characterEncoding;
            this.relativePath = relativePath;
            this.end = end;
        }

        @Override
        public String run() throws IOException{
        	return URLEncoder.encode(new String(relativePath.toCharArray(), 0, end), characterEncoding);
        }
    }
}
