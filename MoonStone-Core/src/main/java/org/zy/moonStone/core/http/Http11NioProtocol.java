package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.net.NioChannel;
import org.zy.moonstone.core.util.net.NioEndpoint;

/**
 * @dateTime 2022年1月7日;
 * @author zy(azurite-Y);
 * @description 抽象协议实现，包括线程等。处理器是单线程的，并且特定于基于流的协议
 */
public class Http11NioProtocol extends AbstractHttp11JsseProtocol<NioChannel> {
	private static final Logger logger = LoggerFactory.getLogger(Http11NioProtocol.class);


	public Http11NioProtocol() {
		super(new NioEndpoint());
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	protected String getNamePrefix() {
		/*
		if (isSSLEnabled()) {
			return "https-" + getSslImplementationShortName()+ "-nio";
		} else {
			return "http-nio";
		}
		*/
		return "http-nio";
	}

	

}
