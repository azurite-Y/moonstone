package org.zy.moonstone.core.http.fileupload;

import org.zy.moonstone.core.connector.HttpRequest;
import org.zy.moonstone.core.interfaces.http.fileupload.RequestContext;

import java.util.function.Supplier;

/**
 * @dateTime 2022年11月22日;
 * @author zy(azurite-Y);
 * @description
 */
public class ServletRequestContext implements RequestContext {
	/**
	 * 为其提供上下文的httpRequest
     */
    private final HttpRequest httpRequest;


    /**
     * 为这个httpRequest构造一个上下文
     *
     * @param httpRequest - 应用此上下文的httpRequest
     */
    public ServletRequestContext(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }


    @Override
    public String getCharacterEncoding() {
        return httpRequest.getCharacterEncoding();
    }

    @Override
    public String getContentType() {
        return httpRequest.getContentType();
    }

    @Override
    public long getContentLength() {
        return httpRequest.getContentLengthLong();
    }

//    @Override
//    public InputStream getInputStream() throws IOException {
//        return httpRequest.getInputStream();
//    }

    /**
     * 返回此对象的字符串表示形式
     *
     * @return 此对象的字符串表示形式
     */
    @Override
    public String toString() {
        return String.format("ContentLength=%s, ContentType=%s", Long.valueOf(this.getContentLength()), this.getContentType());
    }

	@Override
	public byte[] getBoundaryArray() {
		return this.httpRequest.getRequest().getBoundaryArray();
	}
	
	@Override
	public Supplier<Byte> getRequestBodySupplier() {
		return this.httpRequest.getRequest().getRequestBodySupplier();
	}

}
