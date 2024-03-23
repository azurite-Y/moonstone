package org.zy.moonstone.core.http.fileupload;

import org.zy.moonstone.core.Constants;
import org.zy.moonstone.core.exceptions.FileUploadException;
import org.zy.moonstone.core.interfaces.http.fileupload.FileItem;
import org.zy.moonstone.core.interfaces.http.fileupload.FileItemFactory;
import org.zy.moonstone.core.interfaces.http.fileupload.FileItemHeaders;
import org.zy.moonstone.core.interfaces.http.fileupload.RequestContext;
import org.zy.moonstone.core.util.ArraysUtils;
import org.zy.moonstone.core.util.http.parser.HttpParser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * @dateTime 2022年11月22日;
 * @author zy(azurite-Y);
 * @description 用于处理文件上传的API
 * <p>
 * 单个部件的数据存储方式由用于创建它们的工厂决定；给定部件可能在内存、磁盘或其他地方。
 */
public class ServletFileUpload {
    /**
     * HTTP 表单类型头名称
     */
    public static final String CONTENT_TYPE = "Content-type";

    /**
     * HTTP 表单描述头名称
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";

    /**
     * 用于创建新 {@link FileItem } 的工厂
     */
    private FileItemFactory fileItemFactory;
    
    /**
     * 完整请求所允许的最大大小。-1表示不设置最大值。
     */
    private long sizeMax = -1;

    /**
     * 单个上传文件允许的最大大小。-1表示不设置最大值。
     */
    private long fileSizeMax = -1;

    /**
     * 读取表单头时要使用的内容编码
     */
    private String headerEncoding;
    
    
    
	// -------------------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------------------
    public ServletFileUpload() {
    	super();
    }
    
    /**
     * 构造此类的实例，该实例使用提供的工厂创建 <code>FileItem</code> 实例。
     *
     * @param fileItemFactory - 用于创建 <code>FileItem</code> 的工厂
     */
    public ServletFileUpload(FileItemFactory fileItemFactory) {
        this.fileItemFactory = fileItemFactory;
    }


	// -------------------------------------------------------------------------------------
	// getter、setter methods
	// -------------------------------------------------------------------------------------
    /**
     *
     * @return 用于创建 <code>FileItem</code> 的工厂
     */
    public FileItemFactory getFileItemFactory() {
        return fileItemFactory;
    }

    /**
     * 设置用于创建 <code>FileItem</code> 的工厂
     *
     * @param factory - 用于创建 <code>FileItem</code> 的工厂
     */
    public void setFileItemFactory(FileItemFactory factory) {
        this.fileItemFactory = factory;
    }
    
    /**
     * 返回完整请求的最大允许大小
     *
     * @return 允许的最大大小（字节）。默认值-1表示没有限制。
     * @see #setSizeMax(long)
     *
     */
    public long getSizeMax() {
        return sizeMax;
    }

    /**
     * 设置一个完整请求的最大允许大小
     *
     * @param sizeMax - 允许的最大大小(以字节为单位)。默认值-1表示不限制。
     * @see #getSizeMax()
     *
     */
    public void setSizeMax(long sizeMax) {
        this.sizeMax = sizeMax;
    }

    /**
     * 返回单个上传文件的最大允许大小
     *
     * @return 单个上传文件的最大大小
     * @see #setFileSizeMax(long)
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * 设置单个上传文件的最大允许大小
     *
     * @param fileSizeMax - 单个上传文件的最大大小
     * @see #getFileSizeMax()
     */
    public void setFileSizeMax(long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * 检索读取单个表单项头时使用的字符编码。如果未指定或为空，则使用请求编码。如果该参数也未指定或为空，则使用平台默认编码。
     *
     * @return 用于读取表单项头的编码
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }

    /**
     * 指定在读取各个表单项头时使用的字符编码。如果未指定或为空，则使用请求编码。如果该参数也未指定或为空，则使用平台默认编码。
     *
     * @param encoding - 用于读取表单项头的编码
     */
    public void setHeaderEncoding(String encoding) {
        headerEncoding = encoding;
    }
    
    
	// -------------------------------------------------------------------------------------
	// 普通方法
	// -------------------------------------------------------------------------------------
    /**
     * 处理符合 <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a> 的<code>multipart/form-data</code> 数据流
     *
     * @param ctx - 要解析的请求的上下文
     * @return 从请求解析的 <code>FileItem</code> 实例列表，按照它们传输的顺序。
     *
     * @throws FileUploadException - 如果读取/解析请求或存储文件存在问题
     */
    public List<FileItem> parseRequest(RequestContext ctx) throws FileUploadException {
    	RequestBodyParseHandle requestBodyParseHandle = new RequestBodyParseHandle(ctx);
		return requestBodyParseHandle.handle();
    }
    
    /**
     * 请求体数据解析类
     * @author Azurite-Y
     *
     */
    class RequestBodyParseHandle {
		List<FileItem> items = new ArrayList<>();

		private byte[] boundaryBytes ;
		private byte[] assumeBoundaryByte;
		private int boundaryIndex = -1;
		/* 是否已读取边界 */
		private boolean boundary = false;

		private byte[] predictionByteArr = { (byte)'\r', (byte)'\n', (byte)'\r',(byte)'\n' };
		/*
		 * 比对 predictionByteArr[0] 匹配之后， predictionIndex = i + 1 位索引指向predictionByteArr[1]，
		 * 以此类推都命中 predictionByteArr 数组元素视为已读取到空行
		 */
		private int predictionIndex = -1;
		private byte[] assumePredictionByte = new byte[4];
		/* 是否已读取空行 */
		private boolean afterLine = false;
		
		private int endIndex;

		// 存储表单描述及其类型数据
    	private List<Byte> formDataName = new ArrayList<>();
    	// 存储表单提交数据
		private OutputStream formValueOutputStream = null;
		
		private Supplier<Byte> requestBodySupplier;

//		private RequestContext ctx;
		
		
		public RequestBodyParseHandle(RequestContext ctx) {
			super();
//			this.ctx = ctx;
			requestBodySupplier = ctx.getRequestBodySupplier();

			boundaryBytes = ctx.getBoundaryArray();
			assumeBoundaryByte = new byte[boundaryBytes.length];

			/*
			 * 减去最后边界字符【------WebKitFormBoundaryfQoXOk4w328eD3O5--】
			 * int endIndex = requestBodyLine.length - (boundaryBytes.length - 1) - 2;
			 */
			endIndex = (int) (ctx.getContentLength() - boundaryBytes.length - 3);

		}
		
		
		public void createFileItem(List<Byte> byteList) {
	    	FileItemHeaders createFileItemHeaders = createFileItemHeaders(byteList);

			String contentDisposition = createFileItemHeaders.getHeader("Content-Disposition");
			Map<String, String> formDataMap = HttpParser.parseSeparator(contentDisposition, ";", "=");
			String filename = formDataMap.get("filename");
			
			String contentType = createFileItemHeaders.getHeader("Content-Type");
			
			FileItem createItem = fileItemFactory.createItem(formDataMap.get("name"), contentType, filename == null, filename);
			createItem.setHeaders(createFileItemHeaders);
			try {
				formValueOutputStream = createItem.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
			items.add(createItem);
	    }
	    
		/**
		 * 
		 * @param byteList - 给定的表单描述及其类型数据字节
		 * @return 根据给定的表单描述及其类型数据字节创建 {@link FileItemHeaders }
		 */
	    public FileItemHeaders createFileItemHeaders(List<Byte> byteList) {
			byte[] formNameArr = ArraysUtils.getByte(byteList);
			String string = new String(formNameArr);
			Map<String, String> parseSeparator = HttpParser.parseSeparator(string.trim(), "\n", ":");
			
			FileItemHeadersImpl fileItemHeadersImpl = new FileItemHeadersImpl();
			for (Iterator<Entry<String, String>> iterator = parseSeparator.entrySet().iterator(); iterator.hasNext();) {
				Entry<String, String> entry = iterator.next();
//				System.out.printf("[%s==%s]", entry.getKey(), entry.getValue());
				fileItemHeadersImpl.addHeader(entry.getKey(), entry.getValue());
			}
			return fileItemHeadersImpl;
		}
	    
	    /**
	     * @apiNote 若已读字节不是边界的字符数据 ({@link #assumeBoundaryByte }) 则继续比对是否包含空行，若包含则跳过
	     */
	    void parseNewLineToAssumeBoundaryByte() {
			for (int k = 0; k <= boundaryIndex; k++) {
				byte bodyByte = assumeBoundaryByte[k];
				
				if ( bodyByte == Constants.CR &&  assumeBoundaryByte[k+1] == Constants.LF ) {
					if (!afterLine) {
						if ( assumeBoundaryByte[k+2] == Constants.CR  && assumeBoundaryByte[k+3] == Constants.LF ) { // 尝试判断是否已可读取到空行
							afterLine = true;
							k += 4; // 跳过"\r\n\r\n"
							bodyByte = assumeBoundaryByte[k];
						}
					}
				}
				
				addFileItemData(bodyByte);
			}
		}

	    /**
	     * @apiNote - 若假定字节数据 ( {@link #assumePredictionByte } ) 不是 "\r\n\r\n" 的字节数组则是否已读取到空行来将数据保存到不同的数据容器中
	     */
		void parseAssumePredictionByte() {
			for (int i = 0; i <= predictionIndex; i++) {
				byte bodyByte = assumePredictionByte[i];
				
				addFileItemData(bodyByte);
			}
		}
		
	    List<FileItem> handle() {
			byte bodyByte = 0;
			// 在开始读取请求体提交数据时为true, 检测到边界时为false。
			boolean checkBoundary = false;
			
			basic: for (int i = 0; i <= endIndex; i++) {
				if (checkBoundary) {
					checkBoundary = false;
				} else {
					bodyByte = requestBodySupplier.get();
				}
//				System.out.print("[" + (char)bodyByte + "]");

				// 边界之后才是可取字节数据，所以首先匹配头部，头部匹配之后按序匹配之后边界字符，若全额匹配则视为边界
				if (bodyByte == boundaryBytes[0]) {
					// 先手认为它是边界
					boundary = true;

					/*
					 * 保留比对之后的数据
					 * 
					 * 对于assumeBoundaryByte 数组来说 '-1' 有特殊意义，但为了不抛出 ArrayIndexOutOfBoundsException 所以在此自增为'0'
					 */
					assumeBoundaryByte[++boundaryIndex] = bodyByte;

					for (int j = 1; j < boundaryBytes.length; j++) {
						// 当前已比对完请求体数据，但边界数据却没有匹配到最后一位，可判断为未匹配到边界
						if (i == endIndex && j <= boundaryBytes.length - 1 ) {
							boundary = false;
							// 显示剩余字节
							parseNewLineToAssumeBoundaryByte();
							break basic;
						}
						
						i += 1;
						assumeBoundaryByte[++boundaryIndex] = bodyByte = requestBodySupplier.get();
						if ( bodyByte != boundaryBytes[j] ) {
							boundary = false;
							break ;
						}
						
					}

					if (boundary) { // 检测到边界
						boundary = false;
						boundaryIndex = -1;
						afterLine = false;
						formValueOutputStream = null;
					} else {
						if (boundaryIndex != -1) {
							parseNewLineToAssumeBoundaryByte();
							boundaryIndex = -1;
						}
					}
					continue;
				}
				
				if ( bodyByte == predictionByteArr[0] ) {
					/*
					 * 保留比对之后的数据
					 * 
					 * 对于assumePredictionByte 数组来说 '-1' 有特殊意义，但为了不抛出 ArrayIndexOutOfBoundsException 所以在此自增为'0'
					 */
					assumePredictionByte[++predictionIndex] = bodyByte;

					for (int j = 1; j < 4; j++) {
						i += 1;
						assumePredictionByte[++predictionIndex] = bodyByte = requestBodySupplier.get();
						if ( bodyByte != predictionByteArr[j] ) {
							break;
						}
						
						if (j == 3) {
							afterLine = true;
						}
					}

					if (afterLine) {  // 空行之后的数据处理操作
						if (!formDataName.isEmpty()) {
							createFileItem(formDataName);
							formDataName = new ArrayList<>();
						} else if (bodyByte == boundaryBytes[0]) {
							checkBoundary = true;
						} else {
							parseAssumePredictionByte();
						}
					} else { // 空行之前的数据处理操作
						// 处理空行前字符
						if (predictionIndex != -1) {
							if (bodyByte == boundaryBytes[0]) { // 出现"-"则自当前字节开始匹配是否到达边界
								checkBoundary = true;
							} else {
								parseAssumePredictionByte();
							}
						}
					}
					predictionIndex = -1;
					continue;
				}
				
				addFileItemData(bodyByte);
			}
			return items;
		}
	    
	    /**
	     * 根据是否已读取到空行来将数据保存到不同的数据容器中
	     * 
	     * @param bodyByte - 需保存的数据
	     */
	    void addFileItemData(byte bodyByte) {
	    	if (afterLine) {
				try {
//					System.out.print("{" + (char)bodyByte + "}");
					formValueOutputStream.write(bodyByte);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
//				System.out.print("[" + (char)bodyByte + "]");
				formDataName.add(bodyByte);
			}
	    }
    }
}
