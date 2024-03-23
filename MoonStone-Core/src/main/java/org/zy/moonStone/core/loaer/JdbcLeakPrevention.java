package org.zy.moonstone.core.loaer;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

/**
 * @dateTime 2022年8月24日;
 * @author zy(azurite-Y);
 * @description
 * 这个类是由WebappClassLoaderBase加载的，用来注销被web应用程序遗忘的JDBC驱动。
 * 不要使用new关键字创建该类的新实例。因为这个类是由WebappClassLoaderBase加载的，所以它不能引用任何内部Tomcat类。
 * 
 */
public class JdbcLeakPrevention {
	public List<String> clearJdbcDriverRegistrations() throws SQLException {
        List<String> driverNames = new ArrayList<>();

        /*
         * DriverManager.getDrivers()有一个令人讨厌的副作用，即注册对这个类加载器可见但尚未加载的驱动程序。因此，对该方法的第一次调用:
         * a)获取最初加载的驱动程序列表，
         * b)触发不必要的副作用。
         * 第二次调用获得完整的驱动程序列表，确保原始驱动程序和由于副作用而加载的任何驱动程序都被注销。
         */
        Set<Driver> originalDrivers = new HashSet<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            originalDrivers.add(drivers.nextElement());
        }
        drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            // 仅卸载此web应用程序加载的驱动程序
            if (driver.getClass().getClassLoader() != this.getClass().getClassLoader()) {
                continue;
            }
            /**
             * 如果此集合包含指定的元素，则返回true。更正式地说，返回true当且仅当这个集合包含一个元素e这样(o==null ?e = = null: o.equals (e))
             * 
             * @param o - 要测试其是否存在于此集合中的元素
             * @return 如果此集合包含指定的元素，则为 true
             * 
             * @exception ClassCastException - 如果指定元素的类型与此集合不兼容(可选)
             * @exception NullPointerException -如果指定的元素为空，并且该集合不允许有空元素(可选)
             */
            if (originalDrivers.contains(driver)) {
                driverNames.add(driver.getClass().getCanonicalName());
            }
            /**
             * 从DriverManager的注册驱动列表中删除指定的驱动。
             * 
             * 如果为要删除的驱动程序指定了空值，则不采取任何操作。
             * 
             * 如果存在安全管理器并且其 checkPermission 拒绝许可，则将引发 SecurityException。
             * 
             * 如果在注册的驱动程序列表中没有找到指定的驱动程序，则不采取任何措施。 如果找到驱动程序，它将从注册驱动程序列表中删除。
             * 
             * 如果在注册 JDBC 驱动程序时指定了 DriverAction 实例，则在将驱动程序从已注册驱动程序列表中删除之前，将调用其 deregister 方法。
             * 
             * @param driver - 要删除的JDBC驱动程序
             * 
             * @exception SQLException - 如果数据库访问出错
             * @exception SecurityException - 如果 SecurityManager 存在并且其 checkPermission 方法拒绝取消注册驱动程序的权限
             */
            DriverManager.deregisterDriver(driver);
        }
        return driverNames;
    }
}
