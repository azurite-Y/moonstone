package org.zy.moonstone.core.http.fileupload;

import org.zy.moonstone.core.interfaces.http.fileupload.FileItem;
import org.zy.moonstone.core.util.http.parser.HttpParser;

import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @dateTime 2022年11月19日;
 * @author zy(azurite-Y);
 * @description
 */
public class ApplicationPart implements Part {
	private final FileItem fileItem;
    private final File location;

    public ApplicationPart(FileItem fileItem, File location) {
        this.fileItem = fileItem;
        this.location = location;
    }

    /**
     * 删除文件项的底层存储，包括删除任何相关的临时磁盘文件。
     * 
     * @throws IOException - 如果发生错误。
     */
    @Override
    public void delete() throws IOException {
        fileItem.delete();
    }

    /**
     * 获取此部分的内容类型。
     * 
     * @return 此部分的内容类型。
     */
    @Override
    public String getContentType() {
        return fileItem.getContentType();
    }

    /**
     * 返回字符串中指定的mime头的值。如果Part没有包含指定名称的头，则此方法返回null。
     * 如果有多个具有相同名称的头，则此方法返回部分中的第一个头。表单头名称不区分大小写。可以对任何请求头使用此方法。
     * 
     * @param name - 指定表单头名称的字符串
     * @return 包含所指定表单头值的字符串，如果该部分没有该名称的表单头，则为空
     */
    @Override
    public String getHeader(String name) {
        if (fileItem instanceof DiskFileItem) {
            return fileItem.getHeaders().getHeader(name);
        }
        return null;
    }

    /**
     * 获取此部件的标头名称。
     * <p>
     * 有些servlet容器不允许servlet使用此方法访问头，在这种情况下，此方法返回null
     * <p>
     * 对返回的集合的任何更改不得影响本部分。
     * 
     * @return 本部分表单项的集合
     */
    @Override
    public Collection<String> getHeaderNames() {
        if (fileItem instanceof DiskFileItem) {
            LinkedHashSet<String> headerNames = new LinkedHashSet<>();
            Iterator<String> iter =
                fileItem.getHeaders().getHeaderNames();
            while (iter.hasNext()) {
                headerNames.add(iter.next());
            }
            return headerNames;
        }
        return Collections.emptyList();
    }

    /**
     * 获取具有给定名称的Part标头的值。
     * <p>
     * 对返回的集合的任何更改不得影响本部分。
     * <p>
     * 部件头名称不区分大小写。
     * 
     * @param name - 要返回其值的头名称
     * @return 具有给定名称的表单头值的集合（可能为空）
     */
    @Override
    public Collection<String> getHeaders(String name) {
        if (fileItem instanceof DiskFileItem) {
            LinkedHashSet<String> headers = new LinkedHashSet<>();
            Iterator<String> iter = fileItem.getHeaders().getHeaders(name);
            while (iter.hasNext()) {
                headers.add(iter.next());
            }
            return headers;
        }
        return Collections.emptyList();
    }

    /**
     * 获取此部分的内容作为InputStream
     * 
     * @return 此部分的内容作为InputStream
     * @throws IOException - 如果将内容作为InputStream检索时出错
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return fileItem.getInputStream();
    }

    /**
     * 获取此部件的名称
     * 
     * @return 此部分的名称作为字符串
     */
    @Override
    public String getName() {
        return fileItem.getFieldName();
    }

    /**
     * @return 此文件项的大小
     */
    @Override
    public long getSize() {
        return fileItem.getSize();
    }

    /**
     * 一种将此上传项写入磁盘的方便方法。
     * 
     * 如果对同一部分多次调用此方法，则不能保证成功。这允许一个特定的实现使用，例如，文件重命名，在可能的情况下，
     * 而不是复制所有的底层数据，从而获得显著的性能优势
     */
    @Override
    public void write(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.isAbsolute()) {
            file = new File(location, fileName);
        }
        try {
            fileItem.write(file);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * 使用指定的编码将文件项的内容作为字符串返回。该方法使用 get() 检索项的内容。
     * 
     * @param encoding - 要使用的字符编码
     * @return 作为字符串的表单值内容
     * 
     * @throws UnsupportedEncodingException - 若指定的字符集编码不支持
     */
    public String getString(String encoding) throws UnsupportedEncodingException {
        return fileItem.getString(encoding);
    }

    /**
     * 获取客户端指定的文件名
     * 
     * @return 提交的文件名
     */
    @Override
    public String getSubmittedFileName() {
        String fileName = null;
        String cd = getHeader("Content-Disposition");
        if (cd != null) {
            String cdl = cd.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith("form-data") || cdl.startsWith("attachment")) {
                Map<String,String> params = HttpParser.parseSeparator(cd, ";", "=");
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    if (fileName != null) {
                        // RFC 6266. 这要么是令牌，要么是引用字符串
                        if (fileName.indexOf('\\') > -1) {
                            // 这是一个引用字符串
                            fileName = HttpParser.unquote(fileName.trim());
                        } else {
                            // 这是一个令牌
                            fileName = fileName.trim();
                        }
                    } else {
                        // 即使没有值，参数也存在，因此我们返回一个空文件名而不是没有文件名。
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }

}
