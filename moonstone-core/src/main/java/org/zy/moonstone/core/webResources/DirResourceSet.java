package org.zy.moonstone.core.webResources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot.ResourceSetType;
import org.zy.moonstone.core.interfaces.webResources.WebResourceSet;
import org.zy.moonstone.core.util.ResourceSet;

/**
 * @dateTime 2022年8月26日;
 * @author zy(azurite-Y);
 * @description
 */
public class DirResourceSet extends AbstractFileResourceSet {
	private static final Logger logger = LoggerFactory.getLogger(DirResourceSet.class);

    public DirResourceSet() {
        super("/");
    }
    /**
     * 基于目录创建一个新的 {@link WebResourceSet}
     *
     
     * @param root - 此新的 {@link WebResourceSet} 将添加到的 {@link WebResourceRoot}
     * @param webAppMount -  此 WebResourceSet 将挂载在web应用程序中的路径。例如，要给一个web应用程序添加一个jar目录，该目录将被挂载在“WEB-INF/lib/”
     * @param base - 将提供资源的文件系统上文件的绝对路径。
     * @param internalPath - 这个新的 WebResourceSet 中的路径，资源将从这里提供。
     */
    public DirResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath) {
        super(internalPath);
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);

        if (root.getContext().getAddWebinfClassesResources()) {
            File f = new File(base, internalPath);
            f = new File(f, "/WEB-INF/classes/META-INF/resources");

            if (f.isDirectory()) {
                root.createWebResourceSet(ResourceSetType.RESOURCE_JAR, "/", f.getAbsolutePath(), null, "/");
            }
        }

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        
        String webAppMount = getWebAppMount();
        
        WebResourceRoot root = getRoot();
        if (path.startsWith(webAppMount)) { // webAppMount 包含 path
        	// 截去 path 中 webAppMount 的路径
            File f = file(path.substring(webAppMount.length()), false);
            if (f == null) {
                return new EmptyResource(root, path);
            }
            if (!f.exists()) {
                return new EmptyResource(root, path, f);
            }
            if (f.isDirectory() && path.charAt(path.length() - 1) != '/') {
                path = path + '/';
            }
            return new FileResource(root, path, f, isReadOnly(), getManifest());
        } else {
            return new EmptyResource(root, path);
        }
    }

    @Override
    public String[] list(String path) {
        checkPath(path);
        
        String webAppMount = getWebAppMount();
        if (path.startsWith(webAppMount)) { // 若 webAppMount 包含 path
            File f = file(path.substring(webAppMount.length()), true);
            if (f == null) {

            	return EMPTY_STRING_ARRAY;
            }
            String[] result = f.list();
            if (result == null) {
                return EMPTY_STRING_ARRAY;
            } else {
                return result;
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) { // 若 path 包含 webAppMount
            	// 获得 path 字符之后出现第一个 "/" 字符的索引
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                	// 截去 webAppMount 中 path 的路径
                    return new String[] {webAppMount.substring(path.length())};
                } else {
                	// 截取 webAppMount 中 path 字符之后和第一个 '/' 之前的字符
                    return new String[] {webAppMount.substring(path.length(), i)};
                }
            }
            return EMPTY_STRING_ARRAY;
        }
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);
        
        String webAppMount = getWebAppMount();
        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) { // 若 path 包含 webAppMount
        	// 获得当前对象代表的目录 File 对象
            File f = file(path.substring(webAppMount.length()), true);
            
            if (f != null) {
                File[] list = f.listFiles();
                
                if (list != null) {
                    for (File entry : list) {
                        StringBuilder sb = new StringBuilder(path);
                        if (path.charAt(path.length() - 1) != '/') {
                            sb.append('/');
                        }
                        sb.append(entry.getName());
                        if (entry.isDirectory()) {
                            sb.append('/');
                        }
                        result.add(sb.toString());
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) { // 若 webAppMount 包含 path
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    result.add(webAppMount + "/");
                } else {
                    result.add(webAppMount.substring(0, i + 1));
                }
            }
        }
        result.setLocked(true);
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        
        if (isReadOnly()) {
            return false;
        }
        
        String webAppMount = getWebAppMount();
        if (path.startsWith(webAppMount)) {
            File f = file(path.substring(webAppMount.length()), false);
            if (f == null) {
                return false;
            }
            return f.mkdir();
        } else {
            return false;
        }
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException("输入流不能为 null");
        }

        if (isReadOnly()) {
            return false;
        }

        // write() 用于创建文件，因此确保路径不以 ‘/’ 结尾
        if (path.endsWith("/")) {
            return false;
        }

        File dest = null;
        String webAppMount = getWebAppMount();
        if (path.startsWith(webAppMount)) {
            dest = file(path.substring(webAppMount.length()), false);
            if (dest == null) {
                return false;
            }
        } else {
            return false;
        }

        if (dest.exists() && !overwrite) {
            return false;
        }

        try {
            if (overwrite) {
            	/**
            	 * 将输入流中的所有字节复制到文件中。返回时，输入流将位于流的末尾。
            	 * 
            	 * 默认情况下，如果目标文件已经存在或者是非符号链接，则复制失败。
            	 * 如果指定了 REPLACE_EXISTING 选项，并且目标文件已经存在，则如果它不是非空目录，则将其替换。
            	 * 如果目标文件存在并且是符号链接，则替换符号链接。在此版本中，REPLACE_EXISTING 选项是此方法需要支持的唯一选项。
            	 * 在未来的版本中可能会支持其他选项。
            	 * 
            	 * 如果从输入流读取或写入文件时发生 I/O 错误，则它可能会在创建目标文件并读取或写入一些字节后发生。
            	 * 因此输入流可能不在流的末尾，并且可能处于不一致的状态。强烈建议如果发生 I/O 错误，请及时关闭输入流。
            	 * 
            	 * 此方法可能会无限期地阻止从输入流读取(或写入文件)。
            	 * 输入流在复制过程中被异步关闭或线程中断时的行为，该行为与输入流和文件系统提供程序相关，因此未指定。
            	 * 
            	 * <p>
            	 * StandardCopyOption.REPLACE_EXISTING: 如果现有文件存在，则替换该文件。
            	 * StandardCopyOption.COPY_ATTRIBUTES: 将属性复制到新文件
            	 * StandardCopyOption.ATOMIC_MOVE: 作为一个原子文件系统操作移动文件。
            	 * 
            	 * @param in: 要从中读取的输入流
            	 * @param target: 文件的路径
            	 * @param options: 指定应如何完成复制的选项
            	 * @return 读取或写入的字节数
            	 * 
            	 * @exception IOException: 如果在读取或写入时发生I/O错误
            	 * @exception FileAlreadyExistsException: 如果目标文件存在但由于未指定 REPLACE_EXISTING 选项而无法替换（可选特定异常）
            	 * @exception DirectoryNotEmptyException: 指定了 REPLACE_EXISTING 选项，但文件不能被替换，因为它是一个非空目录（可选的特定例外）
            	 * @exception UnsupportedOperationException: 如果 options 包含不受支持的复制选项
            	 * @exception SecurityException: 在默认提供程序的情况下，并且安装了安全管理器，将调用 checkWrite 方法来检查对文件的写访问。 
            	 *                                                    如果指定了 REPLACE_EXISTING 选项，则会调用安全管理器的 checkDelete 方法来检查是否可以删除现有文件
            	 */
                Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(is, dest.toPath());
            }
        } catch (IOException ioe) {
            return false;
        }

        return true;
    }

    @Override
    protected void checkType(File file) {
        if (file.isDirectory() == false) {
            throw new IllegalArgumentException("DirResourceSet 文件路径未指向一个目录, by: path: " + getBase() + File.separator + getInternalPath());
        }
    }

	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        // 这是一个爆炸的web应用程序吗?
        if (getWebAppMount().equals("")) { // 是根路径则尝试读取 MANIFEST.MF
            // 查找 manifest
            File mf = file("META-INF/MANIFEST.MF", true);
            
            if (mf != null && mf.isFile()) {
                try (FileInputStream fis = new FileInputStream(mf)) {
                    setManifest(new Manifest(fis));
                } catch (IOException e) {
                    logger.warn("读取 MANIFEST.MF 文件发生 IO 错误, absolutePath: " + mf.getAbsolutePath(), e);
                }
            }
        }
    }
	
}
