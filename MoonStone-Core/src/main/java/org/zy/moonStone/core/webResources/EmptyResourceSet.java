package org.zy.moonStone.core.webResources;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

import org.zy.moonStone.core.LifecycleBase;
import org.zy.moonStone.core.LifecycleState;
import org.zy.moonStone.core.exceptions.LifecycleException;
import org.zy.moonStone.core.interfaces.webResources.WebResource;
import org.zy.moonStone.core.interfaces.webResources.WebResourceRoot;
import org.zy.moonStone.core.interfaces.webResources.WebResourceSet;

/**
 * @dateTime 2022年8月25日;
 * @author zy(azurite-Y);
 * @description 不受文件系统支持的WebResourceSet实现，其行为就像它没有可用的资源一样。
 * 当web应用程序完全以编程方式配置且不使用文件系统中的任何静态资源时，这主要用于嵌入式模式。
 */
public class EmptyResourceSet extends LifecycleBase implements WebResourceSet {
	private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private WebResourceRoot root;
    private boolean classLoaderOnly;
    private boolean staticOnly;

    public EmptyResourceSet(WebResourceRoot root) {
        this.root = root;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回 {@link EmptyResource}
     */
    @Override
    public WebResource getResource(String path) {
        return new EmptyResource(root, path);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回空数组
     */
    @Override
    public String[] list(String path) {
        return EMPTY_STRING_ARRAY;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回空集
     */
    @Override
    public Set<String> listWebAppPaths(String path) {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回 false
     */
    @Override
    public boolean mkdir(String path) {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回 false
     */
    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        return false;
    }

    @Override
    public void setRoot(WebResourceRoot root) {
        this.root = root;
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

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回 null
     */
    @Override
    public URL getBaseUrl() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 对此方法的调用将被忽略，因为此实现始终是只读的。
     */
    @Override
    public void setReadOnly(boolean readOnly) {}

    /**
     * {@inheritDoc}
     * <p>
     * 此实现总是返回 true
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void gc() {}

    @Override
    protected void initInternal() throws LifecycleException {}

    @Override
    protected void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {}
}
