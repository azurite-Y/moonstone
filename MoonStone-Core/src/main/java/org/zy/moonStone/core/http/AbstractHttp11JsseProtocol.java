package org.zy.moonStone.core.http;

import org.zy.moonStone.core.util.net.AbstractJsseEndpoint;

/**
 * @dateTime 2022年1月11日;
 * @author zy(azurite-Y);
 * @description SSL 相关暂未实现
 */
public abstract class AbstractHttp11JsseProtocol<S> extends AbstractHttp11Protocol<S> {

	public AbstractHttp11JsseProtocol(AbstractJsseEndpoint<S,?> endpoint) {
        super(endpoint);
    }


    @Override
    protected AbstractJsseEndpoint<S,?> getEndpoint() {
        return (AbstractJsseEndpoint<S,?>) super.getEndpoint();
    }

    /*
    protected String getSslImplementationShortName() {
        if (OpenSSLImplementation.class.getName().equals(getSslImplementationName())) {
            return "openssl";
        }
        return "jsse";
    }

    public String getSslImplementationName() { return getEndpoint().getSslImplementationName(); }
    public void setSslImplementationName(String s) { getEndpoint().setSslImplementationName(s); }


    public int getSniParseLimit() { return getEndpoint().getSniParseLimit(); }
    public void setSniParseLimit(int sniParseLimit) {
        getEndpoint().setSniParseLimit(sniParseLimit);
    }
    */

}
