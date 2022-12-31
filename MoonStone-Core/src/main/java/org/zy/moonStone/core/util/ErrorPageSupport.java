package org.zy.moonStone.core.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.zy.moonStone.core.util.descriptor.ErrorPage;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description 支持跟踪每个异常类型和每个HTTP状态代码错误页面
 */
public class ErrorPageSupport {
	// 关联全限定类名和错误页
    private Map<String, ErrorPage> exceptionPages = new ConcurrentHashMap<>();

    // 关联HTTP状态码和错误页
    private Map<Integer, ErrorPage> statusPages = new ConcurrentHashMap<>();


    public void add(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.put(Integer.valueOf(errorPage.getErrorCode()), errorPage);
        } else {
            exceptionPages.put(exceptionType, errorPage);
        }
    }

    public void remove(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.remove(Integer.valueOf(errorPage.getErrorCode()), errorPage);
        } else {
            exceptionPages.remove(exceptionType, errorPage);
        }
    }

    public ErrorPage find(Integer statusCode) {
        return statusPages.get(statusCode);
    }

    /**
     * 找到指定异常类型的ErrorPage(如果有的话).
     *
     * @param exceptionType - 异常类型的完全限定类名
     * @return 指定异常类型的ErrorPage，如果未配置，则为null
     */
    public ErrorPage find(String exceptionType) {
        return exceptionPages.get(exceptionType);
    }


    public ErrorPage find(Throwable exceptionType) {
        if (exceptionType == null) {
            return null;
        }
        Class<?> clazz = exceptionType.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ErrorPage errorPage = exceptionPages.get(name);
            if (errorPage != null) {
                return errorPage;
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            name = clazz.getName();
        }
        return null;
    }

    public ErrorPage[] findAll() {
        Set<ErrorPage> errorPages = new HashSet<>();
        errorPages.addAll(exceptionPages.values());
        errorPages.addAll(statusPages.values());
        return errorPages.toArray(new ErrorPage[errorPages.size()]);
    }
}
