package org.zy.moonstone.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.util.http.ActionCode;

/**
 * @dateTime 2022年5月23日;
 * @author zy(azurite-Y);
 * @description 保存请求和响应对象的结构。它还保存有关请求处理的统计信息，并提供有关正在处理的请求的管理信息。
 * 每个线程使用一个请求/响应对，该对在每个请求上循环使用。这个对象提供了一个收集全局低级统计信息的地方，而无需处理同步（因为每个线程都有自己的
 */
public class RequestInfo {
	protected Logger logger = LoggerFactory.getLogger(getClass());

	// ------------------------------------------------------------------- 成员变量  ------------------------------------------------------------------------
	private final Request req;
	private int stage = Globals.STAGE_NEW;
	private String workerThreadName;

	// --------------------------------------------------------- 统计数据 (在每个请求结束时收集) ---------------------------------------------------------
	/** 请求发送的字节数 */
	private long bytesSent;
	/** 读取请求的字节数 */
	private long bytesReceived;

	/** 总时间 = 除以请求计数以获得平均值。 */
	private long processingTime;
	/** 请求的最长响应时间 */
	private long maxTime;
	/** 花费最长时间的请求的 URI */
	private String maxRequestUri;
	/** 请求计数 */
	private int requestCount;
	/** 错误响应次数，响应码>= 400 */
	private int errorCount;

	// 最后一次请求的时间
	private long lastRequestProcessingTime = 0;

	// ------------------------------------------------------------------- 构造器  ------------------------------------------------------------------------
	public RequestInfo( Request req) {
		this.req=req;
	}
	   
	/** 
	 * 在回收请求之前由处理器调用。它会收集统计信息。
	 */
	protected void updateCounters() {
		bytesReceived+=req.getBytesRead();
		bytesSent+=req.getResponse().getContentWritten();

		requestCount++;
		if( req.getResponse().getStatus() >=400 ) errorCount++;
		long t0=req.getStartTime();
		long t1=System.currentTimeMillis();
		long time=t1-t0;
		this.lastRequestProcessingTime = time;
		processingTime+=time;
		if( maxTime < time ) {
			maxTime=time;
			maxRequestUri=req.requestURI().toString();
		}
	}

	/**
     * @return http方法
     */
	public String getMethod() {
        return req.method().toString();
    }

	/**
     * @return 请求uri
     */
    public String getCurrentUri() {
        return req.requestURI().toString();
    }

    /**
     * @return 请求的查询参数
     */
    public String getCurrentQueryString() {
        return req.queryString().toString();
    }

    /**
     * @return 请求额协议
     */
    public String getProtocol() {
        return req.protocol().toString();
    }

    /**
     * @return 获取从主机派生的“虚拟主机”:请求头
     */
    public String getVirtualHost() {
        return req.serverName().toString();
    }

    /**
     * @return 请求访问的端口
     */
    public int getServerPort() {
        return req.getServerPort();
    }

    /**
     * @return 发送请求的远程地址
     */
    public String getRemoteAddr() {
        req.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, null);
        return req.remoteAddr().toString();
    }

    /**
     * 获取由中间代理（如果有）报告的此连接的远程地址。
     * @return 此连接的远程地址
     */
    public String getRemoteAddrForwarded() {
        String remoteAddrProxy = (String) req.getAttribute(Globals.REMOTE_ADDR_ATTRIBUTE);
        if (remoteAddrProxy == null) {
            return getRemoteAddr();
        }
        return remoteAddrProxy;
    }

    /**
     * @return 请求的内容长度
     */
    public int getContentLength() {
        return req.getContentLength();
    }

    /**
     * @return 读取请求的字节数
     */
    public long getRequestBytesReceived() {
        return req.getBytesRead();
    }

    /**
     * 
     * @return 请求发送的字节数
     */
    public long getRequestBytesSent() {
        return req.getResponse().getContentWritten();
    }

    /**
     * @return 请求的处理时间
     */
    public long getRequestProcessingTime() {
        // 在回收请求之前由处理器调用。它会收集统计信息。
        long startTime = req.getStartTime();
        if (getStage() == Globals.STAGE_ENDED || startTime < 0) {
            return 0;
        } else {
            return System.currentTimeMillis() - startTime;
        }
    }
	
    /**
     * @return 请求状态
     */
	public int getStage() {
		return stage;
	}
	public void setStage(int stage) {
		this.stage = stage;
	}

	public long getBytesSent() {
		return bytesSent;
	}
	public void setBytesSent(long bytesSent) {
		this.bytesSent = bytesSent;
	}

	public long getBytesReceived() {
		return bytesReceived;
	}
	public void setBytesReceived(long bytesReceived) {
		this.bytesReceived = bytesReceived;
	}

	public long getProcessingTime() {
		return processingTime;
	}
	public void setProcessingTime(long processingTime) {
		this.processingTime = processingTime;
	}

	public long getMaxTime() {
		return maxTime;
	}
	public void setMaxTime(long maxTime) {
		this.maxTime = maxTime;
	}

	public String getMaxRequestUri() {
		return maxRequestUri;
	}
	public void setMaxRequestUri(String maxRequestUri) {
		this.maxRequestUri = maxRequestUri;
	}

	public int getRequestCount() {
		return requestCount;
	}
	public void setRequestCount(int requestCount) {
		this.requestCount = requestCount;
	}

	public int getErrorCount() {
		return errorCount;
	}
	public void setErrorCount(int errorCount) {
		this.errorCount = errorCount;
	}

	public String getWorkerThreadName() {
		return workerThreadName;
	}
	public void setWorkerThreadName(String workerThreadName) {
	    this.workerThreadName = workerThreadName;
	}

	public long getLastRequestProcessingTime() {
        return lastRequestProcessingTime;
    }
    public void setLastRequestProcessingTime(long lastRequestProcessingTime) {
        this.lastRequestProcessingTime = lastRequestProcessingTime;
    }
}
