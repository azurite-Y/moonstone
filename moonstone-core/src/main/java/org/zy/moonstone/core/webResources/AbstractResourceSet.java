package org.zy.moonstone.core.webResources;

import java.util.jar.Manifest;

import org.zy.moonstone.core.LifecycleBase;
import org.zy.moonstone.core.LifecycleState;
import org.zy.moonstone.core.exceptions.LifecycleException;
import org.zy.moonstone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonstone.core.interfaces.webResources.WebResourceSet;

/**
 * @dateTime 2022年8月25日;
 * @author zy(azurite-Y);
 * @description
 */
public abstract class AbstractResourceSet extends LifecycleBase implements WebResourceSet {
	/** 新的 {@link WebResourceSet} 将添加到的 {@link WebResourceRoot} */
	private WebResourceRoot root;
	/** 基础路径，此资源的绝对路径 */
    private String base;
    /** 内部路径 */
    private String internalPath = "";
    /** 此 WebResourceSet 将挂载在web应用程序中的路径 */
    private String webAppMount;
    /** 是否仅类加载器 */
    private boolean classLoaderOnly;
    /** 仅静态 */
    private boolean staticOnly;
    private Manifest manifest;
    
    
	// -------------------------------------------------------------------------------------
	// getter、setter
	// -------------------------------------------------------------------------------------
    @Override
    public final void setRoot(WebResourceRoot root) {
        this.root = root;
    }
    /**
     * @return 新的 {@link WebResourceSet} 将添加到的 {@link WebResourceRoot}
     */
    protected final WebResourceRoot getRoot() {
        return root;
    }

    /**
     * @return 内部路径 
     */
    protected final String getInternalPath() {
        return internalPath;
    }
    public final void setInternalPath(String internalPath) {
        checkPath(internalPath);
        // 优化内部处理
        if (internalPath.equals("/")) {
            this.internalPath = "";
        } else {
            this.internalPath = internalPath;
        }
    }

    public final void setWebAppMount(String webAppMount) {
        checkPath(webAppMount);
        // 优化内部处理
        if (webAppMount.equals("/")) {
            this.webAppMount = "";
        } else {
            this.webAppMount = webAppMount;
        }
    }
    /**
     * @return 此 WebResourceSet 将挂载在web应用程序中的路径 
     */
    protected final String getWebAppMount() {
        return webAppMount;
    }

    public final void setBase(String base) {
        this.base = base;
    }
    /**
     * @return 基础路径
     */
    protected final String getBase() {
        return base;
    }

    @Override
    public boolean getClassLoaderOnly() {
        return classLoaderOnly;
    }
    @Override
    public void setClassLoaderOnly(boolean classLoaderOnly) {
        this.classLoaderOnly = classLoaderOnly;
    }

    @Override
    public boolean getStaticOnly() {
        return staticOnly;
    }
    @Override
    public void setStaticOnly(boolean staticOnly) {
        this.staticOnly = staticOnly;
    }

    protected final void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }
    protected final Manifest getManifest() {
        return manifest;
    }

    /**
     * 检查指定路径是否非空非空串且以'/'开头
     * @param path
     */
    protected final void checkPath(String path) {
        if (path == null || path.length() == 0 || path.charAt(0) != '/') {
            throw new IllegalArgumentException("路径无效, 其需非空非空串且以'/'开头, by path: " + path);
        }
    }
    
    
	// -------------------------------------------------------------------------------------
	// 生命周期方法
	// -------------------------------------------------------------------------------------
    @Override
    protected final void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected final void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected final void destroyInternal() throws LifecycleException {
        gc();
    }
}
