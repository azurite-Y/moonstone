package org.zy.moonstone.core.util;

import java.util.Locale;

/**
 * @dateTime 2022年8月18日;
 * @author zy(azurite-Y);
 * @description 用于管理上下文名称的实用程序类，以便在同一个地方进行基本名称、路径和版本之间的转换
 */
public class ContextName {
	public static final String ROOT_NAME = "ROOT";
    private static final String VERSION_MARKER = "##";
    private static final String FWD_SLASH_REPLACEMENT = "#";

    private final String baseName;
    private final String path;
    private final String version;
    private final String name;

    /**
     * 从上下文名称、显示名称、基本名称、目录名称或WAR 名称创建实例
     *
     * @param name - 用作此对象的基础名称
     * @param stripFileExtension - 如果 .war 文件扩展名出现在提供的名称的末尾, 是否应该将其删除？
     */
    public ContextName(String name, boolean stripFileExtension) {
        String tmp1 = name;

        // 去掉任何前导 "/"
        if (tmp1.startsWith("/")) {
            tmp1 = tmp1.substring(1);
        }

        // 更换任何剩余的 "/" 为 "#"
        tmp1 = tmp1.replaceAll("/", FWD_SLASH_REPLACEMENT);

        // 如果需要，插入根名称
        if (tmp1.startsWith(VERSION_MARKER) || "".equals(tmp1)) {
            tmp1 = ROOT_NAME + tmp1;
        }

        // 删除任何文件扩展名
        if ( stripFileExtension && (tmp1.toLowerCase(Locale.ENGLISH).endsWith(".war")) ) {
            tmp1 = tmp1.substring(0, tmp1.length() -4);
        }

        baseName = tmp1;

        String tmp2;
        // 提取版本号
        int versionIndex = baseName.indexOf(VERSION_MARKER);
        if (versionIndex > -1) {
            version = baseName.substring(versionIndex + 2);
            tmp2 = baseName.substring(0, versionIndex);
        } else {
            version = "";
            tmp2 = baseName;
        }

        if (ROOT_NAME.equals(tmp2)) {
            path = "";
        } else {
            path = "/" + tmp2.replaceAll(FWD_SLASH_REPLACEMENT, "/");
        }

        if (versionIndex > -1) {
            this.name = path + VERSION_MARKER + version;
        } else {
            this.name = path;
        }
    }

    /**
     * 从路径和版本构造一个实例
     *
     * @param path - 使用的上下文路径
     * @param version - 要使用的上下文版本
     */
    public ContextName(String path, String version) {
        // 路径永远不应为null, '/' or '/ROOT'
        if (path == null || "/".equals(path) || "/ROOT".equals(path)) {
            this.path = "";
        } else {
            this.path = path;
        }

        // version 永远不应该为空
        if (version == null) {
            this.version = "";
        } else {
            this.version = version;
        }

        // Name 为 path + version
        if ("".equals(this.version)) {
            name = this.path;
        } else {
            name = this.path + VERSION_MARKER + this.version;
        }

        // 基本名称转换为 path + version
        StringBuilder tmp = new StringBuilder();
        if ("".equals(this.path)) {
            tmp.append(ROOT_NAME);
        } else {
            tmp.append(this.path.substring(1).replaceAll("/", FWD_SLASH_REPLACEMENT));
        }
        if (this.version.length() > 0) {
            tmp.append(VERSION_MARKER);
            tmp.append(this.version);
        }
        this.baseName = tmp.toString();
    }

    public String getBaseName() {
        return baseName;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        StringBuilder tmp = new StringBuilder();
        if ("".equals(path)) {
            tmp.append('/');
        } else {
            tmp.append(path);
        }

        if (!"".equals(version)) {
            tmp.append(VERSION_MARKER);
            tmp.append(version);
        }

        return tmp.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
