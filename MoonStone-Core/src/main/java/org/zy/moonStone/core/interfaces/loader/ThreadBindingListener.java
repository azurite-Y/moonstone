package org.zy.moonStone.core.interfaces.loader;

/**
 * @dateTime 2022年9月22日;
 * @author zy(azurite-Y);
 * @description 绑定和解绑上下文类加载器触发的监听器
 */
public interface ThreadBindingListener {

    public void bind();
    public void unbind();

}
