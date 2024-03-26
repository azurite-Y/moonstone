package org.zy.moonstone.core.webResources;

import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;

/**
 * @dateTime 2022年8月31日;
 * @author zy(azurite-Y);
 * @description
 */
public class VirtualResource extends EmptyResource {
    private final String name;

    public VirtualResource(WebResourceRoot root, String webAppPath, String name) {
        super(root, webAppPath);
        this.name = name;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }
}
