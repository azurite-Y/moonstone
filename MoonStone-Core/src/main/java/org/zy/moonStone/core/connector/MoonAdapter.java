package org.zy.moonstone.core.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.http.Request;
import org.zy.moonstone.core.http.Response;
import org.zy.moonstone.core.interfaces.connector.Adapter;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.container.Wrapper;
import org.zy.moonstone.core.session.SessionConfig;
import org.zy.moonstone.core.util.buf.CharChunk;
import org.zy.moonstone.core.util.buf.MessageBytes;
import org.zy.moonstone.core.util.http.ActionCode;
import org.zy.moonstone.core.util.http.ServerCookie;
import org.zy.moonstone.core.util.http.ServerCookies;
import org.zy.moonstone.core.util.net.SocketEvent;
import org.zy.moonstone.core.util.net.interfaces.SSLSupport;

import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @dateTime 2022年6月16日;
 * @author zy(azurite-Y);
 * @description
 */
public class MoonAdapter implements Adapter {
    private static final Logger logger = LoggerFactory.getLogger(MoonAdapter.class);
    
    private static final EnumSet<SessionTrackingMode> SSL_ONLY = EnumSet.of(SessionTrackingMode.SSL);
	
    public static final int ADAPTER_NOTES = 1;
    
    /**
     * 与此处理器关联的 Connector
     */
    private final Connector connector;
    
	private static final ThreadLocal<String> THREAD_NAME = new ThreadLocal<String>() {
		@Override
		protected String initialValue() {
			return Thread.currentThread().getName();
		}
	};
    
    /**
     * 构造一个与指定连接器关联的新处理器
     * @param connector - 拥有此处理器的连接器
     */
    public MoonAdapter(Connector connector) {
        super();
        this.connector = connector;
    }
	
	@Override
	public void service(Request request, Response response) throws Exception {
		HttpRequest httpRequest = (HttpRequest) request.getNote(ADAPTER_NOTES);
		HttpResponse httpResponse = (HttpResponse) response.getNote(ADAPTER_NOTES);

        if (httpRequest == null) {
            httpRequest = connector.createRequest();
            httpRequest.setRequest(request);
            httpResponse = connector.createResponse();
            httpResponse.setResponse(response);

            // 设置与此请求关联的响应
            httpRequest.setHttpResponse(httpResponse);
            // 设置与此响应关联的请求
            httpResponse.setHttpRequest(httpRequest);

            request.setNote(ADAPTER_NOTES, httpRequest);
            response.setNote(ADAPTER_NOTES, httpResponse);

            request.getParameters().setQueryStringCharset(connector.getURICharset());
        }

        boolean async = false;
        boolean postParseSuccess = false;

        // 设置当前工作线程的名称
        request.getRequestProcessor().setWorkerThreadName(THREAD_NAME.get());

        try {
        	// on
            postParseSuccess = postParseRequest(request, httpRequest, response, httpResponse);
            if (postParseSuccess) {
                httpRequest.setAsyncSupported(connector.getService().getContainer().getPipeline().isAsyncSupported());
                // 调用容器 Engine
                connector.getService().getContainer().getPipeline().getFirst().invoke(httpRequest, httpResponse);
            }
            if (httpRequest.isAsync()) {
                async = true;
                ReadListener readListener = request.getReadListener();
                if (readListener != null) {
                    // 可能在 service() 方法期间可能已读取所有数据，因此需要在此处检查
                    ClassLoader oldCL = null;
                    try {
                        oldCL = httpRequest.getContext().bind(false, null);
                        if (request.sendAllDataReadEvent()) {
                            request.getReadListener().onAllDataRead();
                        }
                    } finally {
                        httpRequest.getContext().unbind(false, oldCL);
                    }
                }

                Throwable throwable = (Throwable) httpRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

                // 如果异步请求已经启动，但在容器线程完成并发生错误后不会结束，则触发异步错误进程
                if (!httpRequest.isAsyncCompleting() && throwable != null) {
                    httpRequest.getAsyncContextInternal().setErrorState(throwable, true);
                }
            } else {
                httpRequest.finishRequest();
                httpResponse.finishResponse();
            }

        } catch (IOException e) {
            // Ignore
        } finally {
            AtomicBoolean error = new AtomicBoolean(false);
            request.action(ActionCode.IS_ERROR, error);

            if (httpRequest.isAsyncCompleting() && error.get()) {
                // 连接将被强制关闭，这将阻止在通常点完成。需要在此处触发对 onComplete() 的调用
                request.action(ActionCode.ASYNC_POST_PROCESS,  null);
                async = false;
            }

            request.getRequestProcessor().setWorkerThreadName(null);

            // 回收 HttpRequest 和 HttpResponse
            if (!async) {
                updateWrapperErrorCount(httpRequest, httpResponse);
                httpRequest.recycle();
                httpResponse.recycle();
            }
        }		
	}

	@Override
	public boolean asyncDispatch(Request req, Response res, SocketEvent status) throws Exception {
		// TODO 自动生成的方法存根
		return false;
	}

	@Override
	public void log(Request req, Response res, long time) {
		// TODO 自动生成的方法存根
		
	}

	@Override
	public void checkRecycled(Request req, Response res) {
		// TODO 自动生成的方法存根
		
	}

	/**
     * 在解析 HTTP 请求头后执行必要的处理，以使请求/响应能够传递到容器管道的开头进行处理。
     *
     * @param request - 原初请求对象
     * @param httpRequest - 连接器请求对象
     * @param response - 原初响应对象
     * @param httpResponse - 连接器响应对象
     *
     * @return 如果应将请求传递到容器管道的开头，则为<code>true</code>，否则为<code>false</code>
     * @throws IOException - 如果在处理标头时缓冲区中的空间不足
     * @throws ServletException - 如果无法确定目标 servlet 支持的方法
     */
	protected boolean postParseRequest(Request request, HttpRequest httpRequest, Response response, HttpResponse httpResponse) throws IOException, ServletException {
		// 如果处理器已设置方案，则也可以使用此设置安全标志。如果处理器尚未设置，则使用连接器中的设置
		if (request.scheme().isNull()) {
			// 使用连接器方案和安全配置(分别默认为"http"和false)
			request.scheme().setString(connector.getScheme());
			// 将分配给通过此连接器接收的请求的安全连接标志。默认值为“false”
			httpRequest.setSecure(connector.getSecure()); // [false]
		} else {
			// 使用处理器指定的方案来确定安全状态
			httpRequest.setSecure(request.scheme().equals("https"));
		}

		// 此时，已经处理了Host头。如果设置了proxyPort/proxyHost，则重写
		String proxyName = connector.getProxyName();
		int proxyPort = connector.getProxyPort();
		if (proxyPort != 0) {
			request.setServerPort(proxyPort);
		} else if (request.getServerPort() == -1) {
			// 不是显式地设置。根据方案使用默认端口
			if (request.scheme().equals("https")) {
				request.setServerPort(443);
			} else {
				request.setServerPort(80);
			}
		}
		if (proxyName != null) {
			request.serverName().setString(proxyName);
		}

		MessageBytes undecodedURI = request.requestURI();
		if (undecodedURI.equals("*")) {
			if (request.method().equalsIgnoreCase("OPTIONS")) {
				return false;
			} else {
				httpResponse.sendError(400, "Invalid URI");
			}
		}

		MessageBytes decodedURI = request.decodedURI();
		if (undecodedURI.getType() == MessageBytes.T_BYTES) {
			// 将原始URI复制到decodedURI中
			decodedURI.duplicate(undecodedURI);

			try { // URI解码
				decodedURI.setString( URLDecoder.decode(decodedURI.toString(), decodedURI.getCharset().name()));
			} catch (IOException ioe) {
				httpResponse.sendError(400, "Invalid URI: " + ioe.getMessage());
			}

			if (!normalize(request.decodedURI())) {
				httpResponse.sendError(400, "Invalid URI");
			}
			
			// 解析路径参数
			parsePathParameters(request, httpRequest);
		} else {
			/*
			 * URI 是字符或字符串，并且已使用内存中协议处理程序发送。 做出以下假设：
			 * - request.requestURI() 已设置为“原始”非解码、非规范化 URI
			 * - request.decodedURI() 已设置为 req.requestURI() 的解码规范化形式
			 */
			decodedURI.toChars();
			// 删除所有路径参数；任何需要的路径参数都应该使用请求对象设置，而不是在URL中传递
			CharChunk uriCC = decodedURI.getCharChunk();
			int semicolon = uriCC.indexOf(';');
			if (semicolon > 0) {
				decodedURI.setChars(uriCC.getBuffer(), uriCC.getStart(), semicolon);
			}
		}

		// 请求映射
		MessageBytes serverName;
		if (connector.getUseIPVHosts()) { // 测试是否启用了基于ip的虚拟主机 
			serverName = request.localName();
			if (serverName.isNull()) {
				// 嗯，是他们要求的
				request.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, null);
			}
		} else {
			serverName = request.serverName();
		}

		// 第二个映射循环的版本和期望为该版本获得的上下文
		String version = null;
		Context versionContext = null;
		boolean mapRequired = true;

		if (response.isError()) {
			/**
			 * 这么早出现的错误意味着URI无效。确保未将无效数据传递给映射器。注意，现仍然希望映射程序找到正确的主机
			 * 
			 * 将消息字节重置为未初始化(NULL)状态
			 */
			decodedURI.recycle();
		}

		while (mapRequired) {
			// 默认情况下，这将映射最新版本 映射指定的主机名和 URI，改变给定的映射数据
			connector.getService().getMapper().map(serverName, decodedURI, version, httpRequest.getMappingData());

			// 如果此时没有上下文，这可能是404，因为没有部署根上下文，或者URI无效，因此无法映射上下文
			if (httpRequest.getContext() == null) {
				// 不要覆盖现有错误
				if (!httpResponse.isError()) {
					httpResponse.sendError(404, "Not found");
				}
				// 允许处理继续. 如果存在，错误报告Valve将提供一个响应体
				return true;
			}
			// 现在有了上下文，我们可以从URL解析会话ID(如果有的话)。在重定向之前需要这样做，以防需要在重定向中包含会话id
			String sessionID;
			if (httpRequest.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.URL)) {
				/*
				 * 获取会话ID(如果有的话) 从上下文中尝试获得Session
				 * cookie的名称若为null则使用“jsessionid”，后以此从路径参数集合中获得SessionID的值
				 */
				sessionID = request.getPathParameter(SessionConfig.getSessionUriParamName(httpRequest.getContext()));
				if (sessionID != null) {
					httpRequest.setRequestedSessionId(sessionID);
					httpRequest.setRequestedSessionURL(true);
				}
			}

			// 在Cookie和SSL会话中查找会话ID
			try {
				parseSessionCookiesId(httpRequest);
			} catch (IllegalArgumentException e) {
				// 太多的 Cookie
				if (!httpResponse.isError()) {
					httpResponse.setError();
					httpResponse.sendError(400);
				}
				return true;
			}
			parseSessionSslId(httpRequest);

			sessionID = httpRequest.getRequestedSessionId();
			mapRequired = false;
			if (version != null && httpRequest.getContext() == versionContext) {
				// 已得到想要的版本
			} else {
				version = null;
				versionContext = null;

				Context[] contexts = httpRequest.getMappingData().contexts;
				// 单一的contextVersion意味着不需要重新映射没有会话ID意味着不可能重新映射
				if (contexts != null && sessionID != null) {
					// 找到与会话关联的上下文
					for (int i = contexts.length; i > 0; i--) {
						Context ctxt = contexts[i - 1];
						if (ctxt.getManager().findSession(sessionID) != null) {
							// 我们找到了上下文。是已经映射的那个吗?
							if (!ctxt.equals(httpRequest.getMappingData().context)) {
								// 设置版本，这样第二次通过映射找到正确的上下文
								version = ctxt.getWebappVersion();
								versionContext = ctxt;
								// Reset mapping
								httpRequest.getMappingData().recycle();
								mapRequired = true;
								// 如果正确的上下文配置了不同的设置，请回收cookie和会话信息
								httpRequest.recycleSessionInfo();
								httpRequest.recycleCookieInfo(true);
							}
							break;
						}
					}
				}
			}

			if (!mapRequired && httpRequest.getContext().getPaused()) {
				// 找到了匹配的上下文，但它被暂停了。映射数据将是错误的，因为此时可能没有注册一些Wrappers
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// 不应该发生
				}
				// 请求映射
				httpRequest.getMappingData().recycle();
				
				// 重新设置为标记，重新适配之后的上下文
				mapRequired = true;
			}
		}

		// 可能的重定向
		MessageBytes redirectPathMB = httpRequest.getMappingData().redirectPath;
		if (!redirectPathMB.isNull()) {
			String redirectPath = URLDecoder.decode(redirectPathMB.toString(), request.getCharacterEncoding());
			String query = httpRequest.getQueryString();
			if (httpRequest.isRequestedSessionIdFromURL()) {
				// 这不是最优的，但因为这不是很常见，所以也没关系
				redirectPath = redirectPath + ";" + SessionConfig.getSessionUriParamName(httpRequest.getContext()) + "=" + httpRequest.getRequestedSessionId();
			}
			if (query != null) {
				// 这不是最优的，但因为这不是很常见，所以也没关系
				redirectPath = redirectPath + "?" + query;
			}
			httpResponse.sendRedirect(redirectPath);
			return false;
		}

		// 过滤TRACE请求
		if (!connector.getAllowTrace() && request.method().equalsIgnoreCase("TRACE")) {
			Wrapper wrapper = httpRequest.getWrapper();
			String header = null;
			if (wrapper != null) {
				String[] methods = wrapper.getServletMethods();
				if (methods != null) {
					for (int i = 0; i < methods.length; i++) {
						if ("TRACE".equals(methods[i])) {
							continue;
						}
						if (header == null) {
							header = methods[i];
						} else {
							header += ", " + methods[i];
						}
					}
				}
			}
			if (header != null) {
				response.addHeader("Allow", header);
			}
			httpResponse.sendError(405, "不允许使用 TRACE 方法");
			// 可以安全地跳过此方法的其余部分
			return true;
		}
		return true;
	}
	
	/**
     * 此方法规范化“\”、“//”、“/./”和“/../”，将其替换为 "/"
     *
     * @param uriMB - 要规范化的 URI
     * @return 如果规范化的字符串是空串或不以"/"开头则返回 <code>false</code>，反之则为 <code>true</code>
     */
    public static boolean normalize(MessageBytes uriMB) {
        String uri = uriMB.getString();
        // 不接受空URL
        if (uriMB.isNull()) return false;

        char[] arr = uri.toCharArray();
        char[] arrangeArr = new char[uri.length()];
        int count = 0;
        boolean append = true;
        
        int len = arr.length - 1;
        char temp;
        for (int i = 0; i <= len; i++) {
        	temp = arr[i];
        	
        	// 替换 "\" 为 "/"
        	if ('\\' == temp) {  
        		temp =  '/';
        	}
        	
        	// URL必须以 "/" 开头
        	if (i == 0 && temp != '/' ) {
        		return false;
        	}
        	
        	if (temp == '/' ) {
//        		System.out.println(String.format("i：%s，count：%s，arr[i]：【%s】", i, count, arr[i]));
        		
        		if (arr[i+1] == '/') { // 替换 "//" 为 "/" ==> 跳过下一字符("/")
        			i++;
        		} else if (count > 0 && arrangeArr[count - 1] == '/') { // 替换 "//" 为 "/" ==> 上一字符就是“/”则忽略当前字符
        			//	i++;
        			append = false;
        		} else if (arr[i+1] == '.') {
        			if (arr[i+2] == '.') { // 替换 “/..” 为 “/” ==> 跳过下两位字符“..”
        				i += 2;
        			} else { // 替换 “/.” 为 “/”
        				i++;
        			}
        		}
        	}
        	if (append)  {
        		arrangeArr[count++] = temp;
        	} else {
        		append = true;
        	}
        }
        
        // 因在写入最后的字符之后count会递增一，所以此时count就刚好是整个字符数组的长度
        uriMB.setChars(arrangeArr, 0, count);
        return true;
    }
    
    /**
     * 从请求中提取路径参数。 这假定参数的形式是 /path;name=value;name2=value2/ 等。目前只对将采用这种形式的会话 ID 真正感兴趣。 可以安全地忽略其他参数。
     *
     * @param request - 原初请求对象
     * @param httpRequest - 连接器请求对象
     */
    protected void parsePathParameters(Request request, HttpRequest httpRequest) {
    	CharChunk decodedURI = request.decodedURI().getCharChunk();
    	char[] buffer = decodedURI.getBuffer();
    	
		int leng = buffer.length;
		
		// 路径参数名起始索引
		int nameStart = 0;
		// 路径参数名末尾索引
		int nameEnd = 0;
		
		boolean readName = true;
		char c;
		
		String name = null;
		String value = null;
		for (int i = 0; i < leng; i++) {
			c = buffer[i];
			if (c == ';') {
				nameStart = i + 1;
				if (nameStart == 0) { // 截取uri
					request.decodedURI().setString( new String(buffer, 0 , i) );
				} else if (!readName){
					value = new String(buffer, nameEnd + 1, i - nameEnd - 1);
					readName = true;
					
			    	request.addPathParameter(name, value);
				}
				continue ;
			}
			
			if (c == '=' && readName) {
				nameEnd = i;
				name = new String(buffer, nameStart, nameEnd - nameStart);
				readName = false;
				continue ;
			}
			
			if (!readName && i == leng - 1) { // 保存最后的路径参数
				value = new String(buffer, nameEnd + 1, i - nameEnd);
		    	request.addPathParameter(name, value);
			}
		}
    }

    /**
     * 解析 Cookie 中的会话 ID
     *
     * @param httpRequest Servlet 请求对象
     */
	protected void parseSessionCookiesId(HttpRequest httpRequest) {
		/**
		 * 如果通过cookie的会话跟踪在当前上下文中被禁用，不要在cookie中寻找会话ID，因为来自父上下文的cookie的会话ID可能会覆盖URL中编码的有效会话ID
		 */
		Context context = httpRequest.getMappingData().context;
		if (context != null && !context.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.COOKIE)) { // 检查当前请求是否携带cookie
			return;
		}

		// 解析cookie中的会话id
		ServerCookies serverCookies = httpRequest.getServerCookies();
		int count = serverCookies.getCookieCount();
		if (count <= 0) {
			return;
		}

		String sessionCookieName = SessionConfig.getSessionCookieName(context);
		for (int i = 0; i < count; i++) {
			ServerCookie scookie = serverCookies.getCookie(i);
			if (scookie.getName().equals(sessionCookieName)) {
				// 覆盖URL中的任何请求
				if (!httpRequest.isRequestedSessionIdFromCookie()) {
					httpRequest.setRequestedSessionId(scookie.getValue().getString());
					httpRequest.setRequestedSessionCookie(true);
					httpRequest.setRequestedSessionURL(false);
					if (logger.isDebugEnabled()) {
						logger.debug("Requested cookie session id：" + httpRequest.getRequestedSessionId());
					}
				} else {
					if (!httpRequest.isRequestedSessionIdValid()) {
						// 替换会话 id 直到一个有效
						httpRequest.setRequestedSessionId(scookie.getValue().getString());
					}
				}
			}
		}
	}

	/**
     * 如果需要，查找SSL会话ID。仅在启用了唯一的跟踪方法时才查找SSL会话ID。
     *
     * @param httpRequest - Servlet请求对象
     */
    protected void parseSessionSslId(HttpRequest httpRequest) {
        if (httpRequest.getRequestedSessionId() == null // 无法从路径参数和cookie中都没有解析到会话ID则为 null
        		&& SSL_ONLY.equals(httpRequest.getServletContext().getEffectiveSessionTrackingModes()) && httpRequest.getConnector().getSecure()) {
            String sessionId = (String) httpRequest.getAttribute(SSLSupport.SESSION_ID_KEY);
            if (sessionId != null) {
            	httpRequest.setRequestedSessionId(sessionId);
            	httpRequest.setRequestedSessionSSL(true);
            }
        }
    }

    private void updateWrapperErrorCount(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpResponse.isError()) {
            Wrapper wrapper = httpRequest.getWrapper();
            if (wrapper != null) {
                wrapper.incrementErrorCount();
            }
        }
    }
}
