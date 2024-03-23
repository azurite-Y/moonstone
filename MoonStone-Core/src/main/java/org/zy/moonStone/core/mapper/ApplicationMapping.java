package org.zy.moonstone.core.mapper;

import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.MappingMatch;

/**
 * @dateTime 2022年6月29日;
 * @author zy(azurite-Y);
 * @description 表示获取此对象的请求如何映射到相关的servlet
 */
public class ApplicationMapping {
	private final MappingData mappingData;

    private volatile HttpServletMapping mapping = null;

    public ApplicationMapping(MappingData mappingData) {
        this.mappingData = mappingData;
    }

    public HttpServletMapping getHttpServletMapping() {
        if (mapping == null) {
            String servletName;
            
            if (mappingData.wrapper == null) {
                servletName = "";
            } else {
                servletName = mappingData.wrapper.getName();
            }
            
            if (mappingData.matchType == null) {
                mapping = new MappingImpl("", "", null, servletName);
            } else {
                switch (mappingData.matchType) {
                    case CONTEXT_ROOT: // 当映射与应用程序的上下文根完全匹配时，就会使用这种方式。
                        mapping = new MappingImpl("", "", mappingData.matchType, servletName);
                        break;
                    case DEFAULT: // 当映射与应用程序的默认servlet '/'字符精确匹配时使用。
                        mapping = new MappingImpl("", "/", mappingData.matchType, servletName);
                        break;
                    case EXACT: // 当映射与传入请求完全匹配时使用。
                        mapping = new MappingImpl(mappingData.wrapperPath.toString().substring(1),
                                mappingData.wrapperPath.toString(), mappingData.matchType, servletName);
                        break;
                    case EXTENSION: // 当使用扩展名实现映射时，例如“*.xhtml”，就会使用这种方式。
                        String path = mappingData.wrapperPath.toString();
                        int extIndex = path.lastIndexOf('.');
                        mapping = new MappingImpl(path.substring(1, extIndex),
                                "*" + path.substring(extIndex), mappingData.matchType, servletName);
                        break;
                    case PATH: // 当使用路径实现映射时使用，例如“/faces/*”。
                        String matchValue;
                        if (mappingData.pathInfo.isNull()) {
                            matchValue = null;
                        } else {
                            matchValue = mappingData.pathInfo.toString().substring(1);
                        }
                        mapping = new MappingImpl(matchValue, mappingData.wrapperPath.toString() + "/*", mappingData.matchType, servletName);
                        break;
                }
            }
        }

        return mapping;
    }

    public void recycle() {
        mapping = null;
    }

    private static class MappingImpl implements HttpServletMapping {
        private final String matchValue;
        private final String pattern;
        private final MappingMatch mappingType;
        private final String servletName;

        public MappingImpl(String matchValue, String pattern, MappingMatch mappingType, String servletName) {
            this.matchValue = matchValue;
            this.pattern = pattern;
            this.mappingType = mappingType;
            this.servletName = servletName;
        }

        /**
         * 返回导致此请求匹配的 URI 路径部分。 如果 {@link #getMappingMatch} 值为 {@code CONTEXT_ROOT } 或 {@code DEFAULT }，则此方法必须返回空字符串。
         * 如果 {@link #getMappingMatch} 值为 {@code EXACT }，则此方法必须返回与 servlet 匹配的路径部分，省略任何前导斜杠。
         * 如果 {@link #getMappingMatch} 值为 {@code EXTENSION } 或 {@code PATH }，则此方法必须返回与“*”匹配的值
         */
        @Override
        public String getMatchValue() {
            return matchValue;
        }

        /**
         * 返回这个映射的url模式的String表示。如果 {@link #getMappingMatch} 的值是 {@code CONTEXT_ROOT }或 {@code DEFAULT }，这个方法必须返回空字符串。
         * 如果 {@link #getMappingMatch} 值是 {@code EXTENSION }，这个方法必须返回不带前导斜杠的模式。
         * 否则，该方法将返回与描述符或Java配置中指定的模式完全相同的模式。
         * @return 这个映射的url模式的String表示
         */
        @Override
        public String getPattern() {
            return pattern;
        }

        /**
         * 这个实例的MappingMatch
         * @return 这个实例的MappingMatch
         */
        @Override
        public MappingMatch getMappingMatch() {
            return mappingType;
        }

        /**
         * 返回此映射的servlet名称的String表示。如果提供响应的Servlet是默认Servlet，这个方法返回的是默认Servlet的名称，这是特定于容器的。
         * @return 这个映射的servlet名称的String表示
         */
        @Override
        public String getServletName() {
            return servletName;
        }
    }
}
