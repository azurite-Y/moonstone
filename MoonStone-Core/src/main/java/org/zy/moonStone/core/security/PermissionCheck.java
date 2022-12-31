package org.zy.moonStone.core.security;

import java.security.Permission;

/**
 * @dateTime 2022年8月22日;
 * @author zy(azurite-Y);
 * @description
 * 此接口由组件实现，以使特权代码能够检查组件是否具有给定权限。
 * 当特权组件(例如容器)代表不受信任的组件(例如Web应用程序)执行操作而当前线程未通过不受信任的组件提供的代码源时，通常使用此接口。
 * 因为当前线程没有通过不受信任组件提供的代码源，所以SecurityManager假定代码是受信任的，因此不能使用标准检查机制。
 */
public interface PermissionCheck {
    /**
     * 此组件是否具有给定的权限？
     *
     * @param permission - 测试权限
     * @return 如果启用了 SecurityManager 并且组件没有给定的权限，则为 {@code false}，否则为 {@code true}
     */
    boolean check(Permission permission);
}
