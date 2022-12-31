package org.zy.moonStone.core.mapper;

import org.zy.moonStone.core.interfaces.container.Wrapper;

/**
 * @dateTime 2022年8月16日;
 * @author zy(azurite-Y);
 * @description 封装用于注册 Wrapper 映射的信息
 */
public class WrapperMappingInfo {
	private final String mapping;
    private final Wrapper wrapper;
    private final boolean resourceOnly;

    public WrapperMappingInfo(String mapping, Wrapper wrapper, boolean resourceOnly) {
        this.mapping = mapping;
        this.wrapper = wrapper;
        this.resourceOnly = resourceOnly;
    }

    public String getMapping() {
        return mapping;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public boolean isResourceOnly() {
        return resourceOnly;
    }
}
