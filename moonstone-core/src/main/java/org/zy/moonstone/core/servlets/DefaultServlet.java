package org.zy.moonstone.core.servlets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.Globals;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.servlets.function.HttpServletServiceCallback;
import org.zy.moonstone.core.servlets.function.HttpServletServiceGetCallback;
import org.zy.moonstone.core.servlets.function.HttpServletServicePostCallback;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @dateTime 2022年12月3日;
 * @author zy(azurite-Y);
 * @description
 * 
 * <p>大多数web应用程序的默认资源服务servlet，用于服务静态资源，如HTML页面和图像。
 * 
 * <p>
 * 此servlet旨在映射到<em>/</em>
 * 
 * <p>
 * 它可以映射到子路径，但是在所有情况下，资源都是使用来自web应用程序上下文根的完整路径从web应用程序资源根提供的。该映射仅将 <code>/static/*</code> 映射到默认servlet
 */
public class DefaultServlet extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(DefaultServlet.class);

	/** */
	private static final long serialVersionUID = 5414014237046423213L;

    /** 文件传输缓冲区的大小(字节) */
    protected static final int BUFFER_SIZE = 4096;
    
    /** 读取静态文件时要使用的文件编码。如果未指定，则使用平台默认值 */
    protected String fileEncoding = null;
    
    /** 读取静态文件时要使用的文件编码字符集。如果未指定，则使用平台默认值 */
    private transient Charset fileEncodingCharset = null;
    
    /** 发送文件使用的最小大小(字节) */
    protected int sendfileSize = 48 * 1024;
    
    /** 全套web应用程序资源 */
    protected transient WebResourceRoot resources = null;

    private Map<String, ServletMapping> servletStaticResourceMapping = new HashMap<>();
    
    
 // -------------------------------------------------------------------------------------
 // 实现方法
 // -------------------------------------------------------------------------------------
	/**
	 * 从公共服务方法接收标准HTTP请求，并将其分派给该类中定义的doXXX方法
	 * 
	 * @param req - {@link HttpServletRequest }对象，该对象包含客户端对servlet发出的请求
	 * @param resp - 包含servlet返回给客户端的响应的 {@link HttpServletResponse }对象
	 */
	@Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
        } else {
            super.service(req, resp);
        }
    }
    
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 提供请求的资源，包括数据内容
        serveResource(req, resp, true, fileEncoding);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (logger.isDebugEnabled()) {
			logger.debug("Post Request. request: {}", req.getRequestURI());
		}
		
		ServletMapping servletMapping = servletStaticResourceMapping.get(req.getRequestURI());
		if (servletMapping != null) {
			if (servletMapping.postCallback != null) {
				servletMapping.postCallback.doPost(req, resp);
				return ;
			} else if (servletMapping.callback != null) {
				servletMapping.callback.doPost(req, resp);
				return ;
			}
		}
		
		super.doPost(req, resp);
	}

	protected void serveResource(HttpServletRequest request, HttpServletResponse response, boolean serveContent, String inputEncoding) throws IOException, ServletException {
		DispatcherType dispatcherType = request.getDispatcherType();
		// 确定请求的资源路径
		String path = null;
		if (dispatcherType == DispatcherType.INCLUDE && request instanceof ApplicationHttpRequest) {
			ApplicationHttpRequest applicationHttpRequest = (ApplicationHttpRequest) request;
			path = (String) applicationHttpRequest.getRequestDispatcherPath();
		} else {
			path = request.getRequestURI();
		}
		

		if (logger.isDebugEnabled()) {
			if (serveContent)
				logger.debug("DefaultServlet#serveResource: Serving resource [" + path + "] headers and data.");
			else
				logger.debug("DefaultServlet#serveResource: Serving resource [" + path + "] headers only.");
		}
		
		ServletMapping servletMapping = servletStaticResourceMapping.get(path);
		
		if (servletMapping == null) {
			WebResource resource = this.resources.getResource(path);
			if (resource.exists()) {
				/*
				 * 参见 org.zy.moonstone.core.connector.HttpResponse#isAppCommitted() 
				 * 设置了ContentLength之后就视为请求已提交。故而在此首先设置ContentType
				 */
				response.setContentType(resource.getMimeType());
				response.setContentLengthLong(resource.getContentLength());
				service(request, response, resource);
				return ;
			}
		} else if (servletMapping.isSendFile()) {
			servletMapping.sendFIleToAttribute(request, response);
			return ;
		} else if (servletMapping.getGetCallback() != null) {
			servletMapping.getGetCallback().doGet(request, response);
			return ;
		} else if (servletMapping.getCallback() != null) {
			servletMapping.callback.doGet(request, response);
			return ;
		}
		
		WebResource resource = this.resources.getResource("/404");
		service(request, response, resource);
	}
	
	public void  service(HttpServletRequest req, HttpServletResponse resp, WebResource webResource) {
		// 获得输出流
		byte[] sendFileByte = new byte[sendfileSize];
		
		InputStream inputStream = webResource.getInputStream();
		int len = 0;

		// 在开始写入数据之前设置ContentLength, 否则在写入数据时未确定contentLength则使用Chunked块传输
		//	resp.setContentLengthLong(file.length());
		
		try {
			ServletOutputStream os = resp.getOutputStream();
			while((len= inputStream.read(sendFileByte)) != -1) {
				// 输出二进制数组
				os.write(sendFileByte, 0, len);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("DefaultServlet#sendFIleToStream. by uri: [{}], canonicalPath: [{}], len: {}, mimeType: [{}]", 
						req.getRequestURI(), webResource.getCanonicalPath(), webResource.getContentLength(), webResource.getMimeType());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
	@Override
	public void init() throws ServletException {
        // 加载web资源
        resources = (WebResourceRoot) getServletContext().getAttribute(Globals.RESOURCES_ATTR);
        if (resources == null) {
            throw new UnavailableException("无 web资源");
        }
        
        fileEncoding = getServletConfig().getInitParameter("fileEncoding");
        if (fileEncoding == null) {
            fileEncodingCharset = Charset.defaultCharset();
            fileEncoding = fileEncodingCharset.name();
        } else {
            fileEncodingCharset = Charset.forName(fileEncoding);
        }
        
        initServletMapping();
	}
	
	private void initServletMapping() {
//		ServletMapping headImg = new ServletMapping("/head.jpg", true);
//		servletStaticResourceMapping.put("/head.jpg", headImg);
		
		//--
		ServletMapping cookies = new ServletMapping("/cookies", true);
		servletStaticResourceMapping.put("/cookies", cookies);
		cookies.setPostCallback(new HttpServletServicePostCallback() {
			@Override
			public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				Cookie[] cookiesObj = req.getCookies();
				for (Cookie cookie : cookiesObj) {
					System.out.println("====");
					String cookieName = cookie.getName();
					String value = cookie.getValue();
					String domain = cookie.getDomain();
					int maxAge = cookie.getMaxAge();
					String path = cookie.getPath();
					boolean secure = cookie.getSecure();
					int version = cookie.getVersion();
					String comment = cookie.getComment();

					System.out.println("cookieName：" + cookieName);
					System.out.println("value：" + value);
					System.out.println("domain：" + domain);
					System.out.println("maxAge：" + maxAge);
					System.out.println("path：" + path);
					System.out.println("secure：" + secure);
					System.out.println("version：" + version);
					System.out.println("comment：" + comment);
					System.out.println("====");
				}

				Cookie cookie = new Cookie("MYSELFCOOKIE", "123456789");
				cookie.setMaxAge(60 * 60 * 24); // 一天后过期
				cookie.setPath("/"); // 在这个路径下面的页面才可以访问该Cookie
				cookie.setHttpOnly(false);  // 如果设置了"HttpOnly"属性，那么通过程序(JS脚本、Applet等)将无法访问该Cookie
				resp.addCookie(cookie);

				resp.getWriter().print("[cookies] Running -  Self Cookie Add");				
			}
		});
		
		//--
		ServletMapping fileUpload = new ServletMapping("/fileUpload", true);
		servletStaticResourceMapping.put("/fileUpload", fileUpload);
		fileUpload.setPostCallback(new HttpServletServicePostCallback() {
			@Override
			public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				Map<String, String[]> parameterMap = req.getParameterMap();
				Enumeration<String> parameterNames = req.getParameterNames();
				while(parameterNames.hasMoreElements()) {
					String parameterName = parameterNames.nextElement();
					logger.info("paramete. {}: {}", parameterName, Arrays.asList( parameterMap.get(parameterName) ));
				}
				
				System.out.println("==Part==");
				Collection<Part> parts = req.getParts();
				for (Part part : parts) {
					/**
					 * getName：multipart form 形式返回的参数名
					 * getSubmittedFileName：返回客户端文件系统中的原始文件名
					 */
					System.out.println( String.format("name: [%s], submittedFileName: [%s], size: [%s], contentType：[%s]", part.getName(), part.getSubmittedFileName(), part.getSize(), part.getContentType()) );
					Collection<String> headerNames = part.getHeaderNames();
					headerNames.forEach((headName) -> {
						System.out.println(String.format("\theaderName: [%s], headValue: [%s]", headName, part.getHeaders(headName)));
					});
				}
				System.out.println("==Part==");
				
				resp.getWriter().print("[fileUpload] Running");				
			}
		});
		
		//--
		ServletMapping forwardToFileDownload = new ServletMapping("/forwardToFileDownload");
		servletStaticResourceMapping.put("/forwardToFileDownload", forwardToFileDownload);
		forwardToFileDownload.setCallback(new HttpServletServiceCallback() {
			@Override
			public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				if (logger.isDebugEnabled()) {
					logger.debug("Forward Request. request: {}, forwardReq: {}", req.getRequestURI(), "/fileDownload");
				}

				req.getRequestDispatcher("/fileDownload").forward(req, resp);				
			}
		});
		
		
		//--
		ServletMapping redirectToFileDownload = new ServletMapping("/redirectToFileDownload");
		servletStaticResourceMapping.put("/redirectToFileDownload", redirectToFileDownload);
		redirectToFileDownload.setCallback(new HttpServletServiceCallback() {
			@Override
			public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				if (logger.isDebugEnabled()) {
					logger.debug("Redirect Request. request: {}, redirectReq: {}", req.getRequestURI(), "/fileDownload");
				}
				resp.sendRedirect("/fileDownload");				
			}
		});
		
		
		//--
		ServletMapping fileDownloadInclude = new ServletMapping("/include/fileDownload");
		servletStaticResourceMapping.put("/include/fileDownload", fileDownloadInclude);
		fileDownloadInclude.setCallback(new HttpServletServiceCallback() {
			@Override
			public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				if (logger.isDebugEnabled()) {
					logger.debug("URI: [/include/fileDownload], Attribute: [{}]", req.getAttribute("includeKey"));
				}
				resp.getWriter().print("[includeFileDownload] Running");				
			}
		});
		
		
		//--
		ServletMapping requestIncludeDispatcher = new ServletMapping("/include/requestDispatcher");
		servletStaticResourceMapping.put("/include/requestDispatcher", requestIncludeDispatcher);
		requestIncludeDispatcher.setCallback(new HttpServletServiceCallback() {
			@Override
			public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				req.setAttribute("includeKey", "fileDownloadInclude-value");
				if (logger.isDebugEnabled()) {
					logger.debug("URI: [{}] , include URI: [{}], Set Attribute: [{}]", req.getRequestURI(), "/include/fileDownload", "includeKey");
				}
				
				req.getRequestDispatcher("/include/fileDownload").include(req, resp);
			}
		});
	}
	
	/**
	 * 自定义的Servet映射，当前MVC模型的暂时性替代方法
	 */
	class ServletMapping {
		private String path = "";
		private int code = 200;
		/** 是否使用 SendFile 操作*/
		private boolean isSendFile = false;
		private HttpServletServiceGetCallback getCallback;
		private HttpServletServicePostCallback postCallback;
		private HttpServletServiceCallback callback;
		
		
		public String getPath() {
			return path;
		}
		public void setPath(String path) {
			this.path = path;
		}
		public void setCode(int code) {
			this.code = code;
		}
		public boolean isSendFile() {
			return isSendFile;
		}
		public void setSendFile(boolean isSendFile) {
			this.isSendFile = isSendFile;
		}
		public ServletMapping(String path) {
			super();
			this.path = path;
		}
		public ServletMapping(String path, boolean isSendFile) {
			super();
			this.path = path;
			this.isSendFile = isSendFile;
		}
		public HttpServletServiceGetCallback getGetCallback() {
			return getCallback;
		}
		public void setGetCallback(HttpServletServiceGetCallback getCallback) {
			this.getCallback = getCallback;
		}
		public HttpServletServicePostCallback getPostCallback() {
			return postCallback;
		}
		public void setPostCallback(HttpServletServicePostCallback postCallback) {
			this.postCallback = postCallback;
		}
		public HttpServletServiceCallback getCallback() {
			return callback;
		}
		public void setCallback(HttpServletServiceCallback callback) {
			this.callback = callback;
		}
		
		@Override
		public String toString() {
			return "ServletMapping [path=" + path + ", code=" + code + ", isSendFile=" + isSendFile + "]";
		}
		
		public void sendFIleToAttribute(HttpServletRequest req, HttpServletResponse resp) {
			WebResource resource = resources.getResource(path);
			// 转换为File对象
			req.setAttribute(Constants.SENDFILE_SUPPORTED_ATTR, true);
			String canonicalPath = resource.getCanonicalPath();
			req.setAttribute(Constants.SENDFILE_FILENAME_ATTR, canonicalPath);
			req.setAttribute(Constants.SENDFILE_FILE_START_ATTR, (long)0);
			req.setAttribute(Constants.SENDFILE_FILE_END_ATTR, (long)resource.getContentLength());

			if (logger.isDebugEnabled()) {
				logger.debug("DefaultServlet#sendFileToAttribute. by uri: {}, path: {}, canonicalPath: {}, len: {}", this.path, path, canonicalPath, resource.getContentLength());
			}
		}
		
	}
}
