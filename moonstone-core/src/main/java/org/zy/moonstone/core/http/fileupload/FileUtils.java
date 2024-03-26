package org.zy.moonstone.core.http.fileupload;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @dateTime 2022年11月20日;
 * @author zy(azurite-Y);
 * @description
 * 常规文件操作实用程序
 * <p>
 * 在以下区域提供设施：
 * <ul>
 * <li>写入文件
 * <li>读取文件
 * <li>创建包含父目录的目录
 * <li>复制文件和目录
 * <li>删除文件和目录
 * <li>转换为URL和从URL转换
 * <li>按筛选器和扩展名列出文件和目录
 * <li>比较文件内容
 * <li>文件上次更改日期
 * <li>计算校验和
 * </ul>
 * <p>
 * 请注意，应尽可能指定特定的字符集。依赖于平台默认值意味着代码依赖于区域设置。仅当已知文件始终使用平台默认值时，才使用默认值。
 */
public class FileUtils {
	 /**
     * 实例不应在标准编程中构造
     */
    public FileUtils() {
        super();
    }

    
	// -------------------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------------------
    /**
     * 递归删除目录
     *
     * @param directory - 删除的目录
     * 
     * @throws IOException - 如果删除失败
     * @throws IllegalArgumentException - 如果目录不存在或不是目录
     */
    public static void deleteDirectory(final File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (!isSymlink(directory)) {
            cleanDirectory(directory);
        }

        if (!directory.delete()) {
            throw new IOException("不能删除的目录, by directory: " + directory);
        }
    }

    /**
     * 清理目录而不删除它
     *
     * @param directory - 清理的目录
     * @throws IOException - 如果清理不成功
     * @throws IllegalArgumentException - 如果目录不存在或不是目录
     */
    public static void cleanDirectory(final File directory) throws IOException {
        if (!directory.exists()) {
            final String message = directory + "不存在";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            final String message = directory + "不是一个目录";
            throw new IllegalArgumentException(message);
        }

        final File[] files = directory.listFiles();
        if (files == null) {  // 如果安全性受限，则为空
            throw new IOException("无法列出" + directory + "的内容");
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

	// -------------------------------------------------------------------------------------
    /**
     * 删除文件。如果file是一个目录则删除该目录及其所有子目录。
     * <p>
     * File.delete()和这个方法的区别是:
     * <ul>
     * <li>要删除的目录不必为空</li>
     * <li>当文件或目录不能被删除时，会出现异常。(java.io.File 方法返回布尔值)</li>
     * </ul>
     *
     * @param file - 要删除的文件或目录不能为空
     * @throws NullPointerException - 如果目录为空
     * @throws FileNotFoundException - 如果未找到该文件
     * @throws IOException - 以防删除不成功
     */
    public static void forceDelete(final File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            final boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File 不存在: " + file);
                }
                final String message = "无法删除文件: " + file;
                throw new IOException(message);
            }
        }
    }

    /**
     * 在JVM退出时调度要删除的文件。如果文件是目录，删除它和所有子目录。
     *
     * @param file - 要删除的文件或目录不能为空
     * 
     * @throws NullPointerException - 如果文件为空
     * @throws IOException - 以防删除不成功
     */
    public static void forceDeleteOnExit(final File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectoryOnExit(file);
        } else {
            file.deleteOnExit();
        }
    }

    /**
     * 递归地调度一个目录以便在JVM退出时删除。
     *
     * @param directory - 要删除的目录，不能为空
     * 
     * @throws NullPointerException - 如果文件为空
     * @throws IOException - 以防删除不成功
     */
    private static void deleteDirectoryOnExit(final File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        directory.deleteOnExit();
        if (!isSymlink(directory)) {
            cleanDirectoryOnExit(directory);
        }
    }

    /**
     * 清除目录而不删除目录
     *
     * @param directory - 要清除的目录，不能为空
     * 
     * @throws NullPointerException - 如果文件为空
     * @throws IOException - 以防删除不成功
     */
    private static void cleanDirectoryOnExit(final File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " 不存在";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " 不为目录";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDeleteOnExit(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * 为给定文件创建任何必要但不存在的父目录。如果无法创建父目录，则会引发IOException。
     *
     * @param file - 具有要创建的父级的文件，不能为空
     * 
     * @throws NullPointerException - 如果file为空
     * @throws IOException - 如果无法创建父目录
     */
    public static void forceMkdirParent(final File file) throws IOException {
        final File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        forceMkdir(parent);
    }
    
    /**
     * 创建一个目录，包括任何必要但不存在的父目录。如果指定名称的文件已经存在，但它不是目录，则抛出IOException。
     * 如果无法创建目录(或目录不存在)，则抛出IOException。
     *
     * @param directory - 创建的目录，不能为空
     * @throws NullPointerException - 如果目录为空
     * @throws IOException - 如果目录无法创建，或者文件已经存在但不是目录
     */
    public static void forceMkdir(final File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                final String message = "File 对象" + directory + "不是一个目录且存在. 不能创建目录";
                throw new IOException(message);
            }
        } else {
            if (!directory.mkdirs()) {
                // 再次检查其他线程或进程是否未在后台创建目录
                if (!directory.isDirectory()) {
                    final String message = "不能创建目录 " + directory;
                    throw new IOException(message);
                }
            }
        }
    }

    /**
     * 确定指定的文件是否是符号链接而不是实际文件。
     * <p>
     * 如果路径中的任何位置都有符号链接，则仅当为特定文件时才返回true。
     * <p>
     * 注意：如果使用{@link File#separatorChar}==‘\\’检测到系统为Windows，则当前实现总是返回 false
     *
     * @param file - 要检查的文件
     * @return 如果文件是符号链接，则为true
     * @throws IOException - 如果在检查文件时发生IO错误
     */
    public static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File 不能为 null");
        }
        //FilenameUtils.isSystemWindows()
        if (File.separatorChar == '\\') {
            return false;
        }
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
            return false;
        } else {
            return true;
        }
    }
}
