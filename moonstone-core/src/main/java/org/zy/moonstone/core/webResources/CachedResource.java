package org.zy.moonstone.core.webResources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.security.Permission;
import java.security.cert.Certificate;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.interfaces.container.Context;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.util.http.FastHttpDateFormat;

/**
 * @dateTime 2022年9月16日;
 * @author zy(azurite-Y);
 * @description
 */
public class CachedResource implements WebResource {
    private static final Logger logger = LoggerFactory.getLogger(CachedResource.class);

	// 估计(为安全起见，偏高)平均大小，不包括基于分析器数据的内容。
	private static final long CACHE_ENTRY_SIZE = 500;

	/** WebResource 实例缓存 */
	private final WebResourceCache webResourceCache;

	/** 此新的 {@link WebResource} 将添加到的 {@link WebResourceRoot} */
	private final StandardRoot root;
	/** 当前资源在web项目下的相对路径 */
	private final String webAppPath;
	/** 此资源的存活时间 */
	private final long ttl;
	/** 单个资源对象最大字节数 */
	private final int objectMaxSizeBytes;
	/** 是否应仅用于类加载器资源查找 */
	private final boolean usesClassLoaderResources;

	/** 缓存的资源对象 */
	private volatile WebResource webResource;
	/** */
	private volatile WebResource[] webResources;
	/** 下一次检查期限 */
	private volatile long nextCheck;

	/** 缓存的最后修改时间 */
	private volatile Long cachedLastModified = null;
	/** 缓存的 HTTP Last-Modified 标头的正确格式的此资源的最后修改时间 */
	private volatile String cachedLastModifiedHttp = null;
	/** 此资源的二进制内容，如果它在 byte[] 中不可用，则返回 null，例如，它太大了 */
	private volatile byte[] cachedContent = null;
	/** 缓存资源是否指代一个文件 */
	private volatile Boolean cachedIsFile = null;
	/** 缓存资源是否指代一个目录 */
	private volatile Boolean cachedIsDirectory = null;
	/** 缓存资源是否存在 */
	private volatile Boolean cachedExists = null;
	/** 缓存资源是否是虚拟资源 */
	private volatile Boolean cachedIsVirtual = null;
	/** 缓存资源文件的长度 */
	private volatile Long cachedContentLength = null;
	
	
	// -------------------------------------------------------------------------------------
	// 构造器
	// -------------------------------------------------------------------------------------
    public CachedResource(WebResourceCache webResourceCache, StandardRoot root, String path, long ttl, int objectMaxSizeBytes, boolean usesClassLoaderResources) {
        this.webResourceCache = webResourceCache;
        this.root = root;
        this.webAppPath = path;
        this.ttl = ttl;
        this.objectMaxSizeBytes = objectMaxSizeBytes;
        this.usesClassLoaderResources = usesClassLoaderResources;
    }

    
	// -------------------------------------------------------------------------------------
	// getter、setter
	// -------------------------------------------------------------------------------------
	@Override
	public long getLastModified() {
        Long cachedLastModified = this.cachedLastModified;
        if (cachedLastModified == null) {
            cachedLastModified = Long.valueOf(webResource.getLastModified());
            this.cachedLastModified = cachedLastModified;
        }
        return cachedLastModified.longValue();
	}

	@Override
	public String getLastModifiedHttp() {
        String cachedLastModifiedHttp = this.cachedLastModifiedHttp;
        if (cachedLastModifiedHttp == null) {
            cachedLastModifiedHttp = webResource.getLastModifiedHttp();
            this.cachedLastModifiedHttp = cachedLastModifiedHttp;
        }
        return cachedLastModifiedHttp;
	}

	@Override
	public boolean exists() {
		Boolean cachedExists = this.cachedExists;
        if (cachedExists == null) {
            cachedExists = Boolean.valueOf(webResource.exists());
            this.cachedExists = cachedExists;
        }
        return cachedExists.booleanValue();
	}

	@Override
	public boolean isVirtual() {
        Boolean cachedIsVirtual = this.cachedIsVirtual;
        if (cachedIsVirtual == null) {
            cachedIsVirtual = Boolean.valueOf(webResource.isVirtual());
            this.cachedIsVirtual = cachedIsVirtual;
        }
        return cachedIsVirtual.booleanValue();
	}

	@Override
	public boolean isDirectory() {
        Boolean cachedIsDirectory = this.cachedIsDirectory;
        if (cachedIsDirectory == null) {
            cachedIsDirectory = Boolean.valueOf(webResource.isDirectory());
            this.cachedIsDirectory = cachedIsDirectory;
        }
        return cachedIsDirectory.booleanValue();
	}

	@Override
	public boolean isFile() {
        Boolean cachedIsFile = this.cachedIsFile;
        if (cachedIsFile == null) {
            cachedIsFile = Boolean.valueOf(webResource.isFile());
            this.cachedIsFile = cachedIsFile;
        }
        return cachedIsFile.booleanValue();
	}

	@Override
	public boolean delete() {
        boolean deleteResult = webResource.delete();
        if (deleteResult) {
            webResourceCache.removeCacheEntry(webAppPath);
        }
        return deleteResult;
	}

	@Override
	public String getName() {
        return webResource.getName();
	}

	@Override
	public long getContentLength() {
        Long cachedContentLength = this.cachedContentLength;
        if (this.cachedContentLength == null) {
            long result = 0;
            if (webResource != null) {
                result = webResource.getContentLength();
                cachedContentLength = Long.valueOf(result);
                this.cachedContentLength = cachedContentLength;
            }
            return result;
        }
        return cachedContentLength.longValue();
	}

	@Override
	public String getCanonicalPath() {
        return webResource.getCanonicalPath();
	}

	@Override
	public boolean canRead() {
        return webResource.canRead();
	}

	@Override
	public String getWebappPath() {
        return webAppPath;
	}

	@Override
	public String getETag() {
        return webResource.getETag();
	}

	@Override
	public void setMimeType(String mimeType) {
        webResource.setMimeType(mimeType);		
	}

	@Override
	public String getMimeType() {
		if (webResource.getMimeType() == null) {
			String name = webResource.getName();
			String nameExtension = name.substring(name.lastIndexOf(".") + 1, name.length());
			Context context = this.root.getContext();
			webResource.setMimeType( context.findMimeMapping(nameExtension) );
			
		}
        return webResource.getMimeType();
	}

	@Override
	public InputStream getInputStream() {
		byte[] content = getContent();
        if (content == null) {
            // 不缓存 InputStreams
            return webResource.getInputStream();
        }
        return new ByteArrayInputStream(content);
	}

	@Override
	public byte[] getContent() {
		byte[] cachedContent = this.cachedContent;
        if (cachedContent == null) {
            if (getContentLength() > objectMaxSizeBytes) {
                return null;
            }
            cachedContent = webResource.getContent();
            this.cachedContent = cachedContent;
        }
        return cachedContent;
	}

	@Override
	public long getCreation() {
        return webResource.getCreation();
	}

	@Override
	public URL getURL() {
		/*
		 * 不希望使用此 URL 的应用程序直接访问资源，因为当资源在文件系统上更新但缓存项尚未过期时，这会导致结果不一致。例如在 JSP 编译中看到了这一点。
		 * - 最后修改时间是通过 ServletContext.getResource("path").openConnection().getLastModified() 获得
		 * - JSP内容通过 ServletContext.getResourceAsStream("path")获得。结果是检测到 JSP 修改但JSP内容从缓存中读取，因此使用未更新的 JSP 页面生成了 .java 和 .class 文件
		 * 
		 * 解决此问题的一种方法是对资源 URL 使用自定义 URL 方案。这将允许通过注册 URLStreamHandlerFactory 来控制资源的访问方式并确保所有访问都通过缓存。
		 * 对 war: URLs 采用了这种方法，因此可以使用 jar:war:file: URLs 来引用资源解压的 WAR 文件。
		 * 但是，由于 URL.setURLStreamHandlerFactory() 可能只触发一次，因此在使用其他也想使用自定义 URL 方案的库时，这可能会导致问题。
		 * 
		 * 下面的方法允许在不注册自定义协议的情况下插入自定义 URLStreamHandler。
		 * 唯一的限制（与注册自定义协议相比）是，如果应用程序从字符串构造相同的 URL，它们将直接访问资源，而不是通过缓存。
		 */
        URL resourceURL = webResource.getURL();
        if (resourceURL == null) {
            return null;
        }
        try {
            CachedResourceURLStreamHandler handler = new CachedResourceURLStreamHandler(resourceURL, root, webAppPath, usesClassLoaderResources);
            /**
             * 通过在指定上下文中使用指定处理程序解析给定规范来创建 URL。 如果处理程序为空，则解析过程与两个参数构造函数一样。
             * 
             * @param context - 解析规范的上下文
             * @param spec - 要解析为 URL 的字符串
             * @param handler - URL 的流处理程序
             * @exception MalformedURLException - 如果未指定协议，或找到未知协议，或规范为空。
             * @exception SecurityException - 如果存在安全管理器并且其 checkPermission 方法不允许指定流处理程序。
             */
            URL result = new URL(null, resourceURL.toExternalForm(), handler);
            handler.setAssociatedURL(result);
            return result;
        } catch (MalformedURLException e) {
            logger.error("无效的 URL" + resourceURL.toExternalForm(), e);
            return null;
        }
	}

	@Override
	public URL getCodeBase() {
        return webResource.getCodeBase();
	}

	@Override
	public WebResourceRoot getWebResourceRoot() {
        return webResource.getWebResourceRoot();
	}

	@Override
	public Certificate[] getCertificates() {
        return webResource.getCertificates();
	}

	@Override
	public Manifest getManifest() {
        return webResource.getManifest();
	}
	
    protected long getNextCheck() {
        return nextCheck;
    }
    
    WebResource getWebResource() {
        return webResource;
    }

    WebResource[] getWebResources() {
        return webResources;
    }
    
    /*
     * 假设缓存条目将始终包含内容，除非资源字节内容大于objectMaxSizeBytes。情况并非总是如此，但它使跟踪当前缓存大小变得更容易。
     */
    long getSize() {
        long result = CACHE_ENTRY_SIZE;
        if (getContentLength() <= objectMaxSizeBytes) {
            result += getContentLength();
        }
        return result;
    }
    
    /**
     * 验证当前资源的有效性
     * @param useClassLoaderResources - 是否应仅用于类加载器资源查找
     * @return true 则代表当前缓存有效且可用
     */
    protected boolean validateResource(boolean useClassLoaderResources) {
    	/**
    	 * 某些资源可能仅对给定的useClassLoaderResources值可见。因此，如果查找使用的useClassLoaderResources值与创建缓存项时使用的值不同，请使该项无效。
    	 * 这应该对性能影响最小，因为将资源同时作为静态资源和类加载器资源进行查找是不常见的。
    	 */
    	if (usesClassLoaderResources != useClassLoaderResources) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (webResource == null) {
            synchronized (this) {
                if (webResource == null) {
                    webResource = root.getResourceInternal(webAppPath, useClassLoaderResources);
                    getLastModified();
                    getContentLength();
                    nextCheck = ttl + now;
                    // exists() 是一个相对昂贵的文件检查，所以使用我们现在知道它是否存在的事实
                    if (webResource instanceof EmptyResource) {
                        cachedExists = Boolean.FALSE;
                    } else {
                        cachedExists = Boolean.TRUE;
                    }
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // 假设 WAR 中的资源不会改变
        if (!root.isPackedWarFile()) {
        	// 重新获取资源
            WebResource webResourceInternal = root.getResourceInternal(webAppPath, useClassLoaderResources);
            if (!webResource.exists() && webResourceInternal.exists()) {
                return false;
            }

            // 如果修改日期或长度变化-资源已更改/删除等
            if (webResource.getLastModified() != getLastModified() || webResource.getContentLength() != getContentLength()) {
                return false;
            }

            // 是否在不同的资源集中插入/删除了资源
            if (webResource.getLastModified() != webResourceInternal.getLastModified() || webResource.getContentLength() != webResourceInternal.getContentLength()) {
                return false;
            }
        }

        nextCheck = ttl + now;
        return true;
    }

    protected boolean validateResources(boolean useClassLoaderResources) {
        long now = System.currentTimeMillis();

        if (webResources == null) {
            synchronized (this) {
                if (webResources == null) {
                    webResources = root.getResourcesInternal(webAppPath, useClassLoaderResources);
                    nextCheck = ttl + now;
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // 假设WARs内部的资源不会改变
        if (root.isPackedWarFile()) {
            nextCheck = ttl + now;
            return true;
        } else {
            // 此时，总是使该条目过期，重新填充它可能与验证它一样昂贵。
            return false;
        }
    }
    
    
    
    @Override
	public String toString() {
		return "CachedResource [webAppPath=" + webAppPath + ", nextCheck=" + FastHttpDateFormat.formatDayTime(nextCheck) + ", cachedLastModified="
				+ FastHttpDateFormat.formatDayTime(cachedLastModified) + ", cachedExists=" + cachedExists + "]";
	}


	/*
     * 模仿目录的 FileURLConnection.getInputStream 的行为。 故意使用默认语言环境。
     */
    private static InputStream buildInputStream(String[] files) {
    	/**
    	 * 根据指定的比较器产生的顺序对指定的对象数组进行排序。
    	 * 数组中的所有元素必须通过指定的比较器相互比较（即，c.compare(e1, e2) 不得为数组中的任何元素 e1 和 e2 抛出 ClassCastException）。
    	 * 这种排序保证是稳定的：相同的元素不会因为排序而重新排序。
    	 * 
    	 * 实现说明：此实现是一种稳定的、自适应的、迭代的归并排序，当输入数组部分排序时，它需要的比较次数远少于 n lg(n)，
    	 * 而当输入数组是随机排序时，它提供了传统归并排序的性能。如果输入数组接近排序，则实现需要大约 n 次比较。
    	 * 临时存储要求从几乎排序的输入数组的小常数到随机排序的输入数组的 n/2 对象引用不等。
    	 * 
    	 * 该实现在其输入数组中同等地利用升序和降序，并且可以在同一输入数组的不同部分利用升序和降序。
    	 * 它非常适合合并两个或多个排序数组：只需连接数组并对结果数组进行排序。
    	 * 
    	 * 该实现改编自 Tim Peters 的 Python 列表排序（TimSort）。它使用了 Peter McIlroy 在 1993 年 1 月的第四届 ACM-SIAM 
    	 * 离散算法年度研讨会上的“乐观排序和信息理论复杂性”中的技术。
    	 * 
    	 * @param <T> - 要排序的对象的类
    	 * @param 要排序的数组
    	 * @param 确定数组顺序的比较器。空值表示应该使用元素的 {@linkplain Comparable 自然顺序}。
    	 * @exception ClassCastException - 如果数组包含使用指定比较器不能相互比较的元素
    	 * @exception IllegalArgumentException - （可选）如果发现比较器违反 {@link java.util.Comparator 合同}
    	 */
        Arrays.sort(files, Collator.getInstance(Locale.getDefault()));
        StringBuilder result = new StringBuilder();
        for (String file : files) {
            result.append(file);
            // 每个条目后跟 \n，包括最后一个
            result.append('\n');
        }
        return new ByteArrayInputStream(result.toString().getBytes(Charset.defaultCharset()));
    }
    
    /**
     * 抽象类URLStreamHandler是所有流协议处理程序的公共超类。流协议处理程序知道如何为特定协议类型（如http或https）建立连接。
     * 
     * 在大多数情况下，URLStreamHandlersubclass的实例不是由应用程序直接创建的。相反，在构造URL时第一次遇到协议名称时，会自动加载相应的流协议处理程序。
     */
    private static class CachedResourceURLStreamHandler extends URLStreamHandler {
    	/** 访问资源的 URL */
    	private final URL resourceURL;
    	
    	/** 关联的 URL，此URL指定了 URLStreamHandler 参数创建，而其参数就是当前对象 */
    	private URL associatedURL = null;

    	/** 此新的 {@link WebResource} 将添加到的 {@link WebResourceRoot} */
        private final StandardRoot root;

        /** 当前资源在web项目下的相对路径 */
        private final String webAppPath;
    
        /** 是否应仅用于类加载器资源查找 */
        private final boolean usesClassLoaderResources;


        public CachedResourceURLStreamHandler(URL resourceURL, StandardRoot root, String webAppPath,
                boolean usesClassLoaderResources) {
            this.resourceURL = resourceURL;
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
        }

        /**
         * 设置关联的 URL，此URL指定了 URLStreamHandler 参数创建，而其参数就是当前对象
         * @param associatedURL - 关联的 URL
         */
        protected void setAssociatedURL(URL associatedURL) {
            this.associatedURL = associatedURL;
        }

    	/**
    	 * 打开与 URL 参数引用的对象的连接。此方法应被子类覆盖。
    	 * 
    	 * 如果对于处理程序的协议（例如 HTTP 或 JAR），存在属于以下包之一或其子包之一的公共专用 URLConnection 子类：
    	 * java.lang、java.io、java.util、java.net，返回的连接 将属于该子类。 例如，对于 HTTP 将返回一个 HttpURLConnection. 对于 JAR 将返回一个 JarURLConnection。
    	 * 
    	 * @param u - 它连接到的URL
    	 * @return URL 的 URLConnection 对象
    	 * @exception IOException - 如果在打开连接时发生 I/O 错误
    	 */
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            // 这里特意使用了==。如果 u 不是此 URLStreamHandler 构造的 URL 对象，则不希望使用此 URLStreamHandler 来创建连接。
            if (associatedURL != null && u == associatedURL) {
                if ("jar".equals(associatedURL.getProtocol())) {
                    return new CachedResourceJarURLConnection(resourceURL, root, webAppPath, usesClassLoaderResources);
                } else {
                    return new CachedResourceURLConnection(resourceURL, root, webAppPath, usesClassLoaderResources);
                }
            } else {
                /*
                 * 流处理程序由一个从缓存URL构造的URL继承。需要打破这种联系
                 * 
                 * toExternalForm(): 构造此 URL 的字符串表示形式。 该字符串是通过为此对象调用流协议处理程序的 toExternalForm 方法来创建的。
                 */
                URL constructedURL = new URL(u.toExternalForm());
                /**
                 * 返回一个URLConnection实例，该实例表示指向URL引用的远程对象的连接。
                 * 
                 * 每次调用此URL的协议处理程序的URLStreamHandler.OpenConnection(URL)方法时，都会创建一个新的URLConnection实例。
                 * 
                 * 需要注意的是，URLConnection实例在创建时并不建立实际的网络连接。只有在调用URLConnection.Connect()时才会发生这种情况。
                 * 
                 * 如果对于URL的协议(如HTTP或JAR)，存在属于以下包或其子包之一的公共专用URLConnection子类：java.lang、java.io、java.util、java.net，
                 * 则返回的连接将属于该子类。例如，对于HTTP 将返回HttpURLConnection；对于JAR 将返回JarURLConnection。
                 * 
                 * @return 指向URL的URL连接链接
                 * @exception IOException - 如果发生I/O异常
                 */
                return constructedURL.openConnection();
            }
        }
    }
    
	/**
	 * 抽象类 {@code URLConnection} 是表示应用程序和 URL 之间的通信链接的所有类的超类。 此类的实例可用于读取和写入 URL
	 * 引用的资源。通常，创建到 URL 的连接是一个多步骤的过程：
	 * 
	 * <center>
	 * <table border=2 summary="描述创建与 URL 的连接的过程：随着时间推移的 openConnection() 和 connect()">
	 * <tr>
	 * 		<th>{@code openConnection()}</th><th>{@code connect()}</th>
	 * </tr>
	 * <tr>
	 * 		<td>操作影响到远程资源连接的参数</td>
	 *     <td>与资源交互;查询报头字段和内容</td>
	 *	</tr>
	 * </table>
	 * ----------------------------&gt; <br>time
	 * </center>
	 * 
	 * <ol>
	 * 		<li>通过调用URL上的OpenConnection方法创建Connection对象
	 * 		<li>对设置参数和一般请求属性进行操作
	 * 		<li>使用 {@code connect} 方法建立到远程对象的实际连接
	 * 		<li>远程对象变为可用。可以访问远程对象的 header 字段和内容
	 * </ol>
	 * <p>
	 * 使用以下方法修改设置参数：
	 * <ul>
	 *   <li>{@code setAllowUserInteraction}
	 *   <li>{@code setDoInput}
	 *   <li>{@code setDoOutput}
	 *   <li>{@code setIfModifiedSince}
	 *   <li>{@code setUseCaches}
	 * </ul>
	 * <p>
	 * 并且使用以下方法修改一般请求属性：
	 * <ul>
	 *   <li>{@code setRequestProperty}
	 * </ul>
	 * <p>
	 * 可以使用方法 {@code setDefaultAllowUserInteraction} 和 {@code setDefaultUseCaches} 设置 {@code AllowUserInteraction} 和 {@code UseCaches} 参数的默认值。
	 * <p>
	 * 上面的每个 {@code set} 方法都有一个对应的 {@code get} 方法来检索参数或一般请求属性的值。适用的特定参数和通用请求属性是特定于协议的。
	 * <p>
	 * 在连接到远程对象后，使用以下方法来访问 Header 字段和内容：
	 * <ul>
	 *   <li>{@code getContent}
	 *   <li>{@code getHeaderField}
	 *   <li>{@code getInputStream}
	 *   <li>{@code getOutputStream}
	 * </ul>
	 * <p>
	 * 某些 Header 字段被频繁访问的方法:
	 * <ul>
	 *   <li>{@code getContentEncoding}
	 *   <li>{@code getContentLength}
	 *   <li>{@code getContentType}
	 *   <li>{@code getDate}
	 *   <li>{@code getExpiration}
	 *   <li>{@code getLastModifed}
	 * </ul>
	 * <p>
	 * 提供对这些字段的方便访问。
	 * {@code getContent} 方法使用 {@code getContentType} 方法来确定远程对象的类型;子类可能会发现重写 {@code getContentType} 方法很方便。
	 * <p>
	 * 在一般情况下，所有的预连接参数和一般请求属性都可以忽略:预连接参数和请求属性默认为合理的值。
	 * 对于这个接口的大多数客户端，只有两个有趣的方法: {@code getInputStream} 和 {@code getContent}，它们通过方便的方法镜像到 {@code URL} 类中。
	 * <p>
	 * 关于 {@code http} 连接的请求属性和 header 字段的更多信息，请参见:
	 * <blockquote><pre>
	 * <a href="http://www.ietf.org/rfc/rfc2616.txt">http://www.ietf.org/rfc/rfc2616.txt</a>
	 * </pre></blockquote>
	 * 
	 * 在请求之后调用 {@code URLConnection} 的 {@code InputStream} 或 {@code OutputStream} 上的 {@code close()} 方法可能会释放与此实例关联的网络资源，
	 * 除非特定的协议规范为其指定了不同的行为。
	 */
    // 与 CachedResourceJarURLConnection 保持同步
    private static class CachedResourceURLConnection extends URLConnection {
    	/** 此新的 {@link WebResource} 将添加到的 {@link WebResourceRoot} */
    	private final StandardRoot root;
    	
    	/** 当前资源在web项目下的相对路径 */
        private final String webAppPath;
        
    	/** 是否应仅用于类加载器资源查找 */
        private final boolean usesClassLoaderResources;
        
    	/** 访问资源的 URL */
        private final URL resourceURL;

        protected CachedResourceURLConnection(URL resourceURL, StandardRoot root, String webAppPath, boolean usesClassLoaderResources) {
            super(resourceURL);
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
            this.resourceURL = resourceURL;
        }

        /**
         * 如果此类连接尚未建立，则打开指向此 URL 引用的资源的通信链接。
         * 
         * 如果connect方法在连接已经打开的情况下被调用(通过connected字段的值为true表示)，调用将被忽略。
         * 
         * URLConnection对象经历两个阶段:首先它们被重新创建，然后它们被连接。在创建之后，在连接之前，可以指定各种选项(例如: doInput和UseCaches)。
         * 连接后，试图设置它们是一个错误。依赖于被连接的操作，如getContentLength，在必要时将隐式执行连接。
         * 
         * @exception IOException - 如果在打开连接时发生I/O错误
         */
        @Override
        public void connect() throws IOException {}

        /**
         * 返回从此打开的连接读取的输入流。如果在数据可供读取之前读取超时到期，则从返回的输入流读取时会引发 SocketTimeoutException。
         * 
         * @return 从此打开连接读取的输入流。
         * @exception IOException - 如果在创建输入流时发生I/O错误
         */
        @Override
        public InputStream getInputStream() throws IOException {
            WebResource resource = getResource();
            if (resource.isDirectory()) {
                return buildInputStream(resource.getWebResourceRoot().list(webAppPath));
            } else {
                return getResource().getInputStream();
            }
        }

        /**
         * 返回一个权限对象，该权限对象表示建立此对象所表示的连接所需的权限。如果不需要进行连接的权限，则此方法返回null。
         * 默认情况下，该方法返回java.security.AllPermission。子类应该覆盖此方法并返回最能代表连接URL所需权限的权限。
         * 例如，一个表示file: URL的urlconnection会返回一个java.io.FilePermission对象。
         * 
         * 返回的权限可能取决于连接的状态。例如，连接前的权限可能与连接后的权限不同。
         * 例如，HTTPsever(例如foo.com)可能会将连接重定向到一个不同的主机(例如bar.com)。
         * 在连接之前，连接返回的权限将代表连接到foo.com所需的权限，而连接后返回的权限将代表到bar.com。
         * 
         * 权限通常用于两个目的:保护通过URLConnections获得的对象的缓存，以及检查接收方了解特定URL的权利。
         * 在第一种情况下，许可应该在获得对象之后获得。例如，在http连接中，这将表示连接到最终从其获取数据的主机的权限。
         * 在第二种情况下，应该在连接之前获得并测试权限。
         */
        @Override
        public Permission getPermission() throws IOException {
            // 不会触发对 file:// URLs 的连接调用
            return resourceURL.openConnection().getPermission();
        }

        /**
         * 返回最后一次修改日期 的header值。结果是自1970年1月1日格林威治标准时间以来的毫秒数。
         * 
         * @return 这个URLConnection引用的资源最后一次修改的日期，如果不知道，则为0。
         */
        @Override
        public long getLastModified() {
            return getResource().getLastModified();
        }

        /**
         * 返回内容长度 header 字段的值。
         * 
         * 注意:getContentLengthLong()应该优先于此方法，因为它返回一个long代替，因此更易移植。
         */
        @Override
        public long getContentLengthLong() {
            return getResource().getContentLength();
        }

        private WebResource getResource() {
            return root.getResource(webAppPath, false, usesClassLoaderResources);
        }
    }
    
   
    /**
     * 与 Java ARchive (JAR) 文件或 JAR 文件中的条目的 URL 连接。
     *
     * <p>JAR URL的语法为: <pre>jar:&lt;url&gt;!/{entry}</pre>
     *
     * <p>例如: {@code jar:http://www.foo.com/bar/baz.jar!/COM/foo/Quux.class}
     *
     * <p>Jar URL 应该用于引用 JAR 文件或 JAR 文件中的条目。 上面的示例是一个引用 JAR 条目的 JAR URL。 如果省略条目名称，则 URL 引用整个 JAR 文件：
     * {@code jar:http://www.foo.com/bar/baz.jar!/}
     *
     * <p>当用户知道自己创建的URL是JAR URL并且需要特定于jar的功能时，他们应该将通用URLConnection转换为JarURLConnection。例如:
     * <pre>
     * URL url = new URL("jar:file:/home/duke/duke.jar!/");
     * JarURLConnection jarConnection = (JarURLConnection)url.openConnection();
     * Manifest manifest = jarConnection.getManifest();
     * </pre>
     *
     * <p>JarURLConnection 实例只能用于读取 JAR 文件。使用此类无法获取 {@link java.io.OutputStream} 来修改或写入底层 JAR 文件。
     * <p>例如:
     * <dl>
     * <dt>A Jar entry
     * 		<dd>{@code jar:http://www.foo.com/bar/baz.jar!/COM/foo/Quux.class}
     * <dt>A Jar file
     * 		<dd>{@code jar:http://www.foo.com/bar/baz.jar!/}
     * <dt>A Jar directory
     * 		<dd>{@code jar:http://www.foo.com/bar/baz.jar!/COM/foo/}
     * </dl>
     *
     * <p>{@code !/} 被称为<em>分隔符</em>.
     *
     * <p>当通过 {@code new URL(context, spec)} 构建一个JAR url时，以下规则适用:
     *
     * <ul>
     * <li>如果没有上下文 URL 并且传递给 URL 构造函数的规范不包含分隔符，则认为该 URL 引用了 JarFile
     *
     * <li>如果存在上下文 URL，则假定上下文 URL 引用 JAR 文件或 Jar 目录
     *
     * <li>如果规范以“/”开头，则忽略 Jar 目录，并认为规范位于 Jar 文件的根目录
     *
     * <p>例如:
     * <dl>
     * <dt>context: <b>jar:http://www.foo.com/bar/jar.jar!/</b>,spec:<b>baz/entry.txt</b>
     * 		<dd>url:<b>jar:http://www.foo.com/bar/jar.jar!/baz/entry.txt</b>
     * <dt>context: <b>jar:http://www.foo.com/bar/jar.jar!/baz</b>,spec:<b>entry.txt</b>
     * 		<dd>url:<b>jar:http://www.foo.com/bar/jar.jar!/baz/entry.txt</b>
     * <dt>context: <b>jar:http://www.foo.com/bar/jar.jar!/baz</b>,spec:<b>/entry.txt</b>
     * 		<dd>url:<b>jar:http://www.foo.com/bar/jar.jar!/entry.txt</b>
     * </dl>
     * </ul>
     * 
     * @see java.net.URL
     * @see java.net.URLConnection
     *
     * @see java.util.jar.JarFile
     * @see java.util.jar.JarInputStream
     * @see java.util.jar.Manifest
     * @see java.util.zip.ZipEntry
     */
    // 使其与CachedResourceURLConnection保持同步
    private static class CachedResourceJarURLConnection extends JarURLConnection {
    	/** 此新的 {@link WebResource} 将添加到的 {@link WebResourceRoot} */
    	private final StandardRoot root;
    	
    	/** 当前资源在web项目下的相对路径 */
        private final String webAppPath;
        
    	/** 是否应仅用于类加载器资源查找 */
        private final boolean usesClassLoaderResources;
        
    	/** 访问资源的 URL */
        private final URL resourceURL;

        protected CachedResourceJarURLConnection(URL resourceURL, StandardRoot root, String webAppPath, boolean usesClassLoaderResources) 
        		throws IOException {
            super(resourceURL);
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
            this.resourceURL = resourceURL;
        }

        /**
         * 如果此类连接尚未建立，则打开指向此 URL 引用的资源的通信链接。
         * 
         * 如果connect方法在连接已经打开的情况下被调用(通过connected字段的值为true表示)，调用将被忽略。
         * 
         * URLConnection对象经历两个阶段:首先它们被重新创建，然后它们被连接。在创建之后，在连接之前，可以指定各种选项(例如: doInput和UseCaches)。
         * 连接后，试图设置它们是一个错误。依赖于被连接的操作，如getContentLength，在必要时将隐式执行连接。
         * 
         * @exception IOException - 如果在打开连接时发生I/O错误
         */
        @Override
        public void connect() throws IOException {}

        /**
         * 返回从此打开的连接读取的输入流。如果在数据可供读取之前读取超时到期，则从返回的输入流读取时会引发 SocketTimeoutException。
         * 
         * @return 从此打开连接读取的输入流。
         * @exception IOException - 如果在创建输入流时发生I/O错误
         */
        @Override
        public InputStream getInputStream() throws IOException {
            WebResource resource = getResource();
            if (resource.isDirectory()) {
                return buildInputStream(resource.getWebResourceRoot().list(webAppPath));
            } else {
                return getResource().getInputStream();
            }
        }

        /**
         * 返回一个权限对象，该权限对象表示建立此对象所表示的连接所需的权限。如果不需要进行连接的权限，则此方法返回null。
         * 默认情况下，该方法返回java.security.AllPermission。子类应该覆盖此方法并返回最能代表连接URL所需权限的权限。
         * 例如，一个表示file: URL的urlconnection会返回一个java.io.FilePermission对象。
         * 
         * 返回的权限可能取决于连接的状态。例如，连接前的权限可能与连接后的权限不同。
         * 例如，HTTPsever(例如foo.com)可能会将连接重定向到一个不同的主机(例如bar.com)。
         * 在连接之前，连接返回的权限将代表连接到foo.com所需的权限，而连接后返回的权限将代表到bar.com。
         * 
         * 权限通常用于两个目的:保护通过URLConnections获得的对象的缓存，以及检查接收方了解特定URL的权利。
         * 在第一种情况下，许可应该在获得对象之后获得。例如，在http连接中，这将表示连接到最终从其获取数据的主机的权限。
         * 在第二种情况下，应该在连接之前获得并测试权限。
         */
        @Override
        public Permission getPermission() throws IOException {
            // 不会触发对 jar:// URLs 的连接调用
            return resourceURL.openConnection().getPermission();
        }

        /**
         * 返回最后一次修改日期 的header值。结果是自1970年1月1日格林威治标准时间以来的毫秒数。
         * 
         * @return 这个URLConnection引用的资源最后一次修改的日期，如果不知道，则为0。
         */
        @Override
        public long getLastModified() {
            return getResource().getLastModified();
        }

        /**
         * 返回内容长度 header 字段的值。
         * 
         * 注意:getContentLengthLong()应该优先于此方法，因为它返回一个long代替，因此更易移植。
         */
        @Override
        public long getContentLengthLong() {
            return getResource().getContentLength();
        }

        private WebResource getResource() {
            return root.getResource(webAppPath, false, usesClassLoaderResources);
        }

        /**
         * 返回此连接的 JAR 文件
         * 
         * @return 此连接的 JAR 文件。 如果连接是与 JAR 文件条目的连接，则返回 JAR 文件对象
         * @exception IOException - 如果在尝试连接到此连接的 JAR 文件时发生 IOException
         */
        @Override
        public JarFile getJarFile() throws IOException {
            return ((JarURLConnection) resourceURL.openConnection()).getJarFile();
        }

        /**
         * 返回此连接的 JAR 条目对象（如果有）。 如果与此连接对应的 JAR 文件 URL 指向 JAR 文件而不是 JAR 文件条目，则此方法返回 null。
         * 
         * @return 此连接的 JAR 条目对象，如果此连接的 JAR URL 指向 JAR 文件，则返回 null。
         * @exception IOException - 如果获取此连接的 JAR 文件导致抛出 IOException。
         */
        @Override
        public JarEntry getJarEntry() throws IOException {
            if (getEntryName() == null) {
                return null;
            } else {
                return super.getJarEntry();
            }
        }
    }
}
