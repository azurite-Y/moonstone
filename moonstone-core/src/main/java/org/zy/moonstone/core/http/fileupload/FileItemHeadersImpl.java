package org.zy.moonstone.core.http.fileupload;

import org.zy.moonstone.core.interfaces.http.fileupload.FileItemHeaders;

import java.io.Serializable;
import java.util.*;

/**
 * @dateTime 2022年11月21日;
 * @author zy(azurite-Y);
 * @description {@link FileItemHeaders} 接口的默认实现，且忽略大小写
 */
public class FileItemHeadersImpl implements FileItemHeaders, Serializable  {

    /**
	 * 
	 */
	private static final long serialVersionUID = 228071877981820075L;
	
	/**
	 * 字符串键到字符串实例列表的映射
     */
    private final Map<String,List<String>> headerNameToValueListMap = new LinkedHashMap<>();


    @Override
    public String getHeader(String name) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            return null;
        }
        return headerValueList.get(0);
    	
    }

    @Override
    public Iterator<String> getHeaderNames() {
        return headerNameToValueListMap.keySet().iterator();
    }

    @Override
    public Iterator<String> getHeaders(String name) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            headerValueList = Collections.emptyList();
        }
        return headerValueList.iterator();
    }

    @Override
    public synchronized void addHeader(String name, String value) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            headerValueList = new ArrayList<>();
            headerNameToValueListMap.put(nameLower, headerValueList);
        }
        headerValueList.add(value);
    }
}
