package org.zy.moonStone.core.webResources;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.util.RequestUtil;
import org.zy.moonStone.core.util.compat.JrePlatform;

/**
 * @dateTime 2022年8月25日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractFileResourceSet extends AbstractResourceSet {
	protected static final String[] EMPTY_STRING_ARRAY = new String[0];

	/** 文件基础路径  */
    private File fileBase;
    /** 文件抽象路径 */
    private String absoluteBase;
    /** 文件规范化路径 */
    private String canonicalBase;
    /** 是否只读 */
    private boolean readOnly = false;

    protected AbstractFileResourceSet(String internalPath) {
        setInternalPath(internalPath);
    }

    protected final File getFileBase() {
        return fileBase;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 根据传入的 file 路径名, 创建 File 对象. <code>mustExist</code> 指示其是否存在, 若创建的 File 对象无法满足要求则返回 <code>null</code>
     * 
     * @param name - file名称
     * @param mustExist - 此 File 代表的文件是否需存在
     * @return 创建且符合要求的 File 对象
     */
    protected final File file(String name, boolean mustExist) {
        if (name.equals("/")) {
            name = "";
        }
        File file = new File(fileBase, name);

        /*
         * 如果请求的名称以'/'结尾，Java文件API将返回一个匹配的文件(如果存在)。这不是我们想要的，因为它与请求映射的Servlet规范规则不一致。
         */
        if (name.endsWith("/") && file.isFile()) {
            return null;
        }

        // 如果文件/目录必须存在但无法读取识别文件/目录，则发出未找到资源的信号
        if (mustExist && !file.canRead()) {
            return null;
        }

        // 如果启用了 allowLinking ，则文件不限于位于文件库下，因此将禁用所有进一步检查
        if (getRoot().getAllowLinking()) {
            return file;
        }

        // 其他特定于Windows的检查，以处理 File.getCanonicalPath() 的已知问题
        if (JrePlatform.IS_WINDOWS && isInvalidWindowsFilename(name)) {
            return null;
        }

        // 检查此文件是否位于WebResourceSet的基目录下
        String canPath = null;
        try {
        	// 规范化路径
            canPath = file.getCanonicalPath();
        } catch (IOException e) {
            // Ignore
        }
        if (canPath == null || !canPath.startsWith(canonicalBase)) {
            return null;
        }

        /*
         * 确保该文件不在文件库之外。这对于标准请求应该是不可能的(请求在请求处理的早期被标准化)，
         * 但是对于一些通过Servlet API(RequestDispatcher、HTTP/2推送等)的访问来说可能是可能的。
         * 因此，这些检查将作为额外的安全措施保留下来。absoluteBase 已被规范化，因此 absPath 也需要被规范化。
         */
        String absPath = normalize(file.getAbsolutePath());
        if (absoluteBase.length() > absPath.length()) {
            return null;
        }

        // 从路径的开头位置删除 fileBase，因为它不是请求路径的一部分，其余的检查只应用于请求路径
        absPath = absPath.substring(absoluteBase.length());
        canPath = canPath.substring(canonicalBase.length());

        /*
         * 区分大小写检查规范化的请求路径应该与等效的规范路径完全匹配。 如果不是，可能的原因包括:
         * 	  -- 不区分大小写的文件系统的大小写差异
         *   -- Windows 从文件名删除末尾的 ' ' 或 '.'
         * 
         * 在所有情况下，此处的不匹配都会导致找不到资源
         * 
         * absPath是规范化的，所以canPath也需要规范化，不能更早规范化canPath，因为canonicalBase不是规范化的
         */
        if (canPath.length() > 0) {
            canPath = normalize(canPath);
        }
        if (!canPath.equals(absPath)) {
            return null;
        }

        return file;
    }

    private boolean isInvalidWindowsFilename(String name) {
        final int len = name.length();
        if (len == 0) {
            return false;
        }
        // 无论输入长度如何，这始终比等效的正则表达式快约 10 倍。
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c == '\"' || c == '<' || c == '>') {
            	/*
            	 * Windows文件名中不允许使用这些字符，并且在使用Fi1le#getCanonicalPath()时，使用这些字符的文件名存在已知问题。
            	 * 
            	 * 注意：Windows文件名中有不允许使用的其他字符，但在使用File#getCanonicalPath()时，这些字符不会导致问题。
            	 */
                return true;
            }
        }
        /*
         * Windows 不允许文件名以 ' ' 结尾，除非使用特定的低级 API 来创建绕过各种检查的文件。
         * 已知以“ ”结尾的文件名会在使用 File#getCanonicalPath() 时引起问题。
         */
        if (name.charAt(len -1) == ' ') {
            return true;
        }
        return false;
    }

    /**
     * 返回一个上下文相对路径，以"/"开头，表示在".."和"."元素之后解析出的指定路径的规范版本。
     * 如果指定的路径试图超出当前上下文的边界(即存在太多的".." path元素)，则返回 <code>null</code>。
     *
     * @param path - 规范化的路径
     * 
     * @implNote {@link File.separatorChar }:
     * 系统相关的默认名称分隔符字符。此字段初始化为包含系统属性file.separator值的第一个字符。
     * 在UNIX系统上，此字段的值为“/”；在Microsoft Windows系统上，它是“\\”。
     */
    private String normalize(String path) {
        return RequestUtil.normalize(path, File.separatorChar == '\\');
    }

    @Override
    public URL getBaseUrl() {
        try {
            return getFileBase().toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认情况下，这是基于文件资源集的无操作。
     */
    @Override
    public void gc() {}


	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    protected void initInternal() throws LifecycleException {
        fileBase = new File(getBase(), getInternalPath());
        checkType(fileBase);

        this.absoluteBase = normalize(fileBase.getAbsolutePath());

        try {
            this.canonicalBase = fileBase.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected abstract void checkType(File file);
}
