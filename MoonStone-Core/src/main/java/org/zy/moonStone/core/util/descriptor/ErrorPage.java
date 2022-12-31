package org.zy.moonStone.core.util.descriptor;

import java.io.Serializable;

/**
 * @dateTime 2022年1月5日;
 * @author zy(azurite-Y);
 * @description web应用程序错误页面
 */
public class ErrorPage implements Serializable {
	private static final long serialVersionUID = 3680586216928867200L;

    /**
     * 此错误页处于活动状态的错误（状态）代码。请注意，状态代码0用于默认错误页。
     */
    private int errorCode = 0;

    /**
     * 此错误页处于活动状态的异常类型.
     */
    private String exceptionType = null;

    /**
     * 处理此错误或异常的上下文相对位置.
     */
    private String location = null;

    public int getErrorCode() {
        return this.errorCode;
    }
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 设置错误代码.
     *
     * @param errorCode
     */
    public void setErrorCode(String errorCode) {
        try {
            this.errorCode = Integer.parseInt(errorCode);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }
    }

    public String getExceptionType() {
        return this.exceptionType;
    }


    /**
     * 设置异常类型.
     *
     */
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getLocation() {
        return this.location;
    }


    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ErrorPage[");
        if (exceptionType == null) {
            sb.append("errorCode=");
            sb.append(errorCode);
        } else {
            sb.append("exceptionType=");
            sb.append(exceptionType);
        }
        sb.append(", location=");
        sb.append(location);
        sb.append("]");
        return sb.toString();
    }

    public String getName() {
        if (exceptionType == null) {
            return Integer.toString(errorCode);
        } else {
            return exceptionType;
        }
    }
}
