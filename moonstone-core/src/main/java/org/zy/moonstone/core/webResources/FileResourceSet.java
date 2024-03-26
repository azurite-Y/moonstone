package org.zy.moonstone.core.webResources;

import java.io.File;
import java.io.InputStream;
import java.util.Set;

import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.webResources.WebResource;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.interfaces.webResources.WebResourceSet;
import org.zy.moonstone.core.util.ResourceSet;

/**
 * @dateTime 2022年8月26日;
 * @author zy(azurite-Y);
 * @description 表示单个文件的 {@link WebResourceSet } 
 */
public class FileResourceSet extends AbstractFileResourceSet {

    public FileResourceSet() {
        super("/");
    }

    /**
     * 基于文件创建一个新的 {@link WebResourceSet}
     *
     * @param root - 这个新的 {@link WebResourceSet} 将添加到的 {@link WebResourceRoot}
     * @param webAppMount -  此 WebResourceSet 将挂载在web应用程序中的路径。例如，要给一个web应用程序添加一个jar目录，该目录将被挂载在“WEB-INF/lib/”
     * @param base - 将提供资源的文件系统上文件的绝对路径。
     * @param internalPath - 这个新的 WebResourceSet 中的路径，资源将从这里提供。
     */
    public FileResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath) {
        super(internalPath);
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);

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
        if (path.equals(webAppMount)) { // 若获取资源路径为当前资源挂载路径则依据路径创建资源对象
            File f = file("", true);
            if (f == null) {
                return new EmptyResource(root, path);
            }
            return new FileResource(root, path, f, isReadOnly(), null);
        }

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }

        if (webAppMount.startsWith(path)) {
        	// 截去末尾的 "/"
            String name = path.substring(0, path.length() - 1);
            // 截取路径表示的文件名
            name = name.substring(name.lastIndexOf('/') + 1);
            if (name.length() > 0) {
                return new VirtualResource(root, path, name);
            }
        }
        return new EmptyResource(root, path);
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) { // 若 path 包含 webAppMount
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                return new String[] {getFileBase().getName()};
            } else {
                // 虚拟目录
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                	// 截取 "/" 之前的路径内容
                    return new String[] {webAppMount.substring(0, i)};
                }
            }
        }

        return EMPTY_STRING_ARRAY;
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);

        ResourceSet<String> result = new ResourceSet<>();

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) { // 若 path 包含 webAppMount
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                result.add(path + getFileBase().getName());
            } else {
                // 虚拟目录
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                    result.add(path + webAppMount.substring(0, i + 1));
                }
            }
        }

        result.setLocked(true);
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        return false;
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);
        return false;
    }

    @Override
    protected void checkType(File file) {
        if (file.isFile() == false) {
            throw new IllegalArgumentException("FileResourceSet 文件路径未指向一个文件, by: path: " + getBase() + File.separator + getInternalPath());
        }
    }

}
