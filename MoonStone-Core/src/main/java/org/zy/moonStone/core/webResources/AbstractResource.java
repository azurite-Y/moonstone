package org.zy.moonStone.core.webResources;

import java.io.InputStream;

import org.slf4j.Logger;
import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.util.http.FastHttpDateFormat;

/**
 * @dateTime 2022年8月26日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractResource implements WebResource {
	/** 此新的 {@link WebResource} 将添加到的 {@link WebResourceRoot} */
	private final WebResourceRoot root;
	/** 当前资源在web项目下的相对路径 */
	private final String webAppPath;
	/** 与资源关联的 mime 类型 */
	private String mimeType = null;
	/** 存储内容长度和数据最后修改时间 */
	private volatile String weakETag;


	protected AbstractResource(WebResourceRoot root, String webAppPath) {
		this.root = root;
		this.webAppPath = webAppPath;
	}


	@Override
	public final WebResourceRoot getWebResourceRoot() {
		return root;
	}

	@Override
	public final String getWebappPath() {
		return webAppPath;
	}

	@Override
	public final String getLastModifiedHttp() {
		return FastHttpDateFormat.formatDate(getLastModified());
	}

	@Override
	public final String getETag() {
		if (weakETag == null) {
			synchronized (this) {
				if (weakETag == null) {
					long contentLength = getContentLength();
					long lastModified = getLastModified();
					if ((contentLength >= 0) || (lastModified >= 0)) {
						weakETag = "W/\"" + contentLength + "-" + lastModified + "\"";
					}
				}
			}
		}
		return weakETag;
	}

	@Override
	public final void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	@Override
	public final String getMimeType() {
		return mimeType;
	}

	@Override
	public final InputStream getInputStream() {
		InputStream is = doGetInputStream();

		if (is == null || !root.getTrackLockedFiles()) {
			return is;
		}

		return new TrackedInputStream(root, getName(), is);
	}

	protected abstract InputStream doGetInputStream();

	protected abstract Logger getLogger();
}
