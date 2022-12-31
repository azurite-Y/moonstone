package org.zy.moonStone.core.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @dateTime 2022年9月21日;
 * @author zy(azurite-Y);
 * @description
 */
public class IntrospectionUtils {
	private static final Logger logger = LoggerFactory.getLogger(IntrospectionUtils.class);

    private static final Hashtable<Class<?>,Method[]> objectMethods = new Hashtable<>();
    
	/**
	 * 找到具有正确名称的方法如果找到，调用该方法(如果参数为 int 或 boolean 则将在此之前将值转换为正确的类型)
	 * 
     * @param o - 要对其设置属性的对象
     * @param name - 属性名
     * @param value - 属性值
     * @return 如果操作成功，则为<code>true</code>
     */
    public static boolean setProperty(Object o, String name, String value) {
        return setProperty(o,name,value,true);
    }

    /**
	 * 找到具有正确名称的方法如果找到，调用该方法(如果参数为 int 或 boolean 则将在此之前将值转换为正确的类型)
	 * 
     * @param o - 要对其设置属性的对象
     * @param name - 属性名
     * @param value - 属性值
     * @param invokeSetProperty - 是否调用 <code>setProperty</code> 方法
     * @return 如果操作成功，则为<code>true</code>
     */
    public static boolean setProperty(Object o, String name, String value, boolean invokeSetProperty) {
        if (logger.isDebugEnabled())
            logger.debug("setProperty(" + o.getClass().getSimpleName() + " " + name + "=" + value + ")");

        String setter = "set" + capitalize(name);
        try {
            Method methods[] = findMethods(o.getClass());
            Method setPropertyMethodVoid = null;
            Method setPropertyMethodBool = null;

            // 首先，理想的情况是 setXXX 方法
            for (Method item : methods) {
                Class<?> paramT[] = item.getParameterTypes();
                if (setter.equals(item.getName()) && paramT.length == 1 && "java.lang.String".equals(paramT[0].getName())) {
                    item.invoke(o, new Object[]{value});
                    return true;
                }
            }

            // Try a setFoo ( int ) or ( boolean )
            for (Method method : methods) {
                boolean ok = true;
                if (setter.equals(method.getName()) && method.getParameterTypes().length == 1) {
                    // 匹配-找到类型并调用它
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object params[] = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName()) || "int".equals(paramType.getName())) {
                        try {
                            params[0] = Integer.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( long )
                    } else if ("java.lang.Long".equals(paramType.getName()) || "long".equals(paramType.getName())) {
                        try {
                            params[0] = Long.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }

                        // Try a setFoo ( boolean )
                    } else if ("java.lang.Boolean".equals(paramType.getName()) || "boolean".equals(paramType.getName())) {
                        params[0] = Boolean.valueOf(value);

                        // Try a setFoo ( InetAddress )
                    } else if ("java.net.InetAddress".equals(paramType.getName())) {
                        try {
                            params[0] = InetAddress.getByName(value);
                        } catch (UnknownHostException exc) {
                            if (logger.isDebugEnabled())
                                logger.debug("IntrospectionUtils: 无法解析的 host name:" + value);
                            ok = false;
                        }
                    } else {
                    	// 未知类型
                        if (logger.isDebugEnabled())
                            logger.debug("IntrospectionUtils: 未知类型 " + paramType.getName());
                    }

                    if (ok) {
                        method.invoke(o, params);
                        return true;
                    }
                }

                if ("setProperty".equals(method.getName())) {
                    if (method.getReturnType() == Boolean.TYPE) {
                        setPropertyMethodBool = method;
                    } else {
                        setPropertyMethodVoid = method;
                    }
                }
            }

            // 未找到 setXXX 则尝试 setProperty("name", "value")
            if (invokeSetProperty && (setPropertyMethodBool != null || setPropertyMethodVoid != null)) {
                Object params[] = new Object[2];
                params[0] = name;
                params[1] = value;
                if (setPropertyMethodBool != null) {
                    try {
                        return ((Boolean) setPropertyMethodBool.invoke(o, params)).booleanValue();
                    }catch (IllegalArgumentException biae) {
                        // boolean 类型参数错误，尝试其他类型
                        if (setPropertyMethodVoid!=null) {
                            setPropertyMethodVoid.invoke(o, params);
                            return true;
                        }else {
                            throw biae;
                        }
                    }
                } else {
                    setPropertyMethodVoid.invoke(o, params);
                    return true;
                }
            }

        } catch (IllegalArgumentException | SecurityException | IllegalAccessException e) {
            logger.warn(String.format("属性设置错误, by name: %s, value: %s, object: %s", name, value, o.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            logger.warn(String.format("属性设置错误, by name: %s, value: %s, object: %s", name, value, o.getClass().getName()), e);
        }
        return false;
    }

    /**
     * 反射调用指定名称的参数获取方法已获得目标参数值
     * @param o - 实例对象
     * @param name - 参数获取方法名
     * @return 目标参数值
     */
    public static Object getProperty(Object o, String name) {
        String getter = "get" + capitalize(name);
        String isGetter = "is" + capitalize(name);

        try {
            Method methods[] = findMethods(o.getClass());
            Method getPropertyMethod = null;

            // 首先，理想的情况是 getXxx() 方法
            for (Method method : methods) {
                Class<?> paramT[] = method.getParameterTypes();
                if (getter.equals(method.getName()) && paramT.length == 0) {
                    return method.invoke(o, (Object[]) null);
                }
                if (isGetter.equals(method.getName()) && paramT.length == 0) {
                    return method.invoke(o, (Object[]) null);
                }

                if ("getProperty".equals(method.getName())) {
                    getPropertyMethod = method;
                }
            }

            if (getPropertyMethod != null) {
                Object params[] = new Object[1];
                params[0] = name;
                return getPropertyMethod.invoke(o, params);
            }
        } catch (IllegalArgumentException | SecurityException | IllegalAccessException e) {
            logger.warn(String.format("属性获取错误, by name: %s, object: %s", name, o.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof NullPointerException) {
                // 假设底层对象使用存储来表示未设置的属性
                return null;
            }
            ExceptionUtils.handleThrowable(e.getCause());
            logger.warn(String.format("属性获取错误, by name: %s, object: %s", name, o.getClass().getName()), e);
        }
        return null;
    }
    
    /**
     * 首字母大写
     * @param name - 需处理的 name
     * @return 反转之后的 string
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }
    
    /**
     * 清除类方法缓存
     */
    public static void clear() {
        objectMethods.clear();
    }
    
    /**
     * 查询指定类的方法集并将之缓存
     * @param c - 查询类
     * @return 指定类的方法集
     */
    public static Method[] findMethods(Class<?> c) {
        Method methods[] = objectMethods.get(c);
        if (methods != null)
            return methods;

        methods = c.getMethods();
        objectMethods.put(c, methods);
        return methods;
    }

    /**
     * 获得指定类中指定名称和方法参数的 Method对象
     * 
     * @param c - 查询类
     * @param name - 方法名
     * @param params - 方法参数
     * @return 指定类中指定名称和方法参数的 Method对象
     */
    public static Method findMethod(Class<?> c, String name, Class<?> params[]) {
        Method methods[] = findMethods(c);
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                Class<?> methodParams[] = method.getParameterTypes();
                if (params == null && methodParams.length == 0) {
                    return method;
                }
                if (params.length != methodParams.length) {
                    continue;
                }
                boolean found = true;
                for (int j = 0; j < params.length; j++) {
                    if (params[j] != methodParams[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return method;
                }
            }
        }
        return null;
    }
}
