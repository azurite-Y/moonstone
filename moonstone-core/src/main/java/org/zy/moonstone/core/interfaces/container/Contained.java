package org.zy.moonstone.core.interfaces.container;

/**
 * @dateTime 2021年12月29日;
 * @author zy(azurite-Y);
 * @description 解耦接口，该接口指定一个实现类最多只能与一个容器实例关联
 */
public interface Contained {
	/**
     * 获取与此实例关联的容器.
     *
     * @return 与该实例关联的容器，如果没有关联到该容器，则为空
     */
    Container getContainer();


    /**
     * 设置与此实例关联的容器.
     *
     * @param container - 与该实例相关联的容器实例，或为空解除该实例与任何容器的关联
     */
    void setContainer(Container container);
}
