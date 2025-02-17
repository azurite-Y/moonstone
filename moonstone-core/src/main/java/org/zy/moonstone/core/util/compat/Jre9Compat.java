package org.zy.moonstone.core.util.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Deque;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description
 */
class Jre9Compat extends JreCompat {
	private static final Logger logger = LoggerFactory.getLogger(Jre9Compat.class);

    private static final Class<?> inaccessibleObjectExceptionClazz;
    private static final Method setApplicationProtocolsMethod;
    private static final Method getApplicationProtocolMethod;
    private static final Method setDefaultUseCachesMethod;
    private static final Method bootMethod;
    private static final Method configurationMethod;
    private static final Method modulesMethod;
    private static final Method referenceMethod;
    private static final Method locationMethod;
    private static final Method isPresentMethod;
    private static final Method getMethod;
    private static final Constructor<JarFile> jarFileConstructor;
    private static final Method isMultiReleaseMethod;
    private static final Object RUNTIME_VERSION;
    private static final int RUNTIME_MAJOR_VERSION;
    private static final Method canAccessMethod;
    private static final Method getModuleMethod;
    private static final Method isExportedMethod;

    static {
        Class<?> c1 = null;
        Method m2 = null;
        Method m3 = null;
        Method m4 = null;
        Method m5 = null;
        Method m6 = null;
        Method m7 = null;
        Method m8 = null;
        Method m9 = null;
        Method m10 = null;
        Method m11 = null;
        Constructor<JarFile> c12 = null;
        Method m13 = null;
        Object o14 = null;
        Object o15 = null;
        Method m16 = null;
        Method m17 = null;
        Method m18 = null;

        try {
            // 顺序对于下面的错误处理很重要。必须先查找c1
            c1 = Class.forName("java.lang.reflect.InaccessibleObjectException");

            Class<?> moduleLayerClazz = Class.forName("java.lang.ModuleLayer");
            Class<?> configurationClazz = Class.forName("java.lang.module.Configuration");
            Class<?> resolvedModuleClazz = Class.forName("java.lang.module.ResolvedModule");
            Class<?> moduleReferenceClazz = Class.forName("java.lang.module.ModuleReference");
            Class<?> optionalClazz = Class.forName("java.util.Optional");
            Class<?> versionClazz = Class.forName("java.lang.Runtime$Version");
            Method runtimeVersionMethod = JarFile.class.getMethod("runtimeVersion");
            Method majorMethod = versionClazz.getMethod("major");

            m2 = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            m3 = SSLEngine.class.getMethod("getApplicationProtocol");
            m4 = URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
            m5 = moduleLayerClazz.getMethod("boot");
            m6 = moduleLayerClazz.getMethod("configuration");
            m7 = configurationClazz.getMethod("modules");
            m8 = resolvedModuleClazz.getMethod("reference");
            m9 = moduleReferenceClazz.getMethod("location");
            m10 = optionalClazz.getMethod("isPresent");
            m11 = optionalClazz.getMethod("get");
            c12 = JarFile.class.getConstructor(File.class, boolean.class, int.class, versionClazz);
            m13 = JarFile.class.getMethod("isMultiRelease");
            o14 = runtimeVersionMethod.invoke(null);
            o15 = majorMethod.invoke(o14);
            m16 = AccessibleObject.class.getMethod("canAccess", new Class<?>[] { Object.class });
            m17 = Class.class.getMethod("getModule");
            Class<?> moduleClass = Class.forName("java.lang.Module");
            m18 = moduleClass.getMethod("isExported", String.class);

        } catch (ClassNotFoundException e) {
            if (c1 == null) {
                // 必须是 Java 9 之前的版本
//            	if (logger.isDebugEnabled() ) {
//            		logger.debug( "必须是 Java 9 之前的版本, cause: " + e.getLocalizedMessage() );
//            	} 
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // 永远不应该发生
            logger.error("JRE版本适配",e);
        }

        inaccessibleObjectExceptionClazz = c1;
        setApplicationProtocolsMethod = m2;
        getApplicationProtocolMethod = m3;
        setDefaultUseCachesMethod = m4;
        bootMethod = m5;
        configurationMethod = m6;
        modulesMethod = m7;
        referenceMethod = m8;
        locationMethod = m9;
        isPresentMethod = m10;
        getMethod = m11;
        jarFileConstructor = c12;
        isMultiReleaseMethod = m13;

        RUNTIME_VERSION = o14;
        if (o15 != null) {
            RUNTIME_MAJOR_VERSION = ((Integer) o15).intValue();
        } else {
            // 必须是 Java 8
            RUNTIME_MAJOR_VERSION = 8;
        }

        canAccessMethod = m16;
        getModuleMethod = m17;
        isExportedMethod = m18;
    }


    static boolean isSupported() {
        return inaccessibleObjectExceptionClazz != null;
    }


    @Override
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        if (t == null) {
            return false;
        }

        return inaccessibleObjectExceptionClazz.isAssignableFrom(t.getClass());
    }


    @Override
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        try {
            setApplicationProtocolsMethod.invoke(sslParameters, (Object) protocols);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public String getApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) getApplicationProtocolMethod.invoke(sslEngine);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
        try {
            setDefaultUseCachesMethod.invoke(null, "JAR", Boolean.FALSE);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        try {
            Object bootLayer = bootMethod.invoke(null);
            Object bootConfiguration = configurationMethod.invoke(bootLayer);
            Set<?> resolvedModules = (Set<?>) modulesMethod.invoke(bootConfiguration);
            for (Object resolvedModule : resolvedModules) {
                Object moduleReference = referenceMethod.invoke(resolvedModule);
                Object optionalURI = locationMethod.invoke(moduleReference);
                Boolean isPresent = (Boolean) isPresentMethod.invoke(optionalURI);
                if (isPresent.booleanValue()) {
                    URI uri = (URI) getMethod.invoke(optionalURI);
                    try {
                        URL url = uri.toURL();
                        classPathUrlsToProcess.add(url);
                    } catch (MalformedURLException e) {
                        logger.warn(String.format("无效的模块 Uri，by uri：%s", uri), e);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public JarFile jarFileNewInstance(File f) throws IOException {
        try {
            return jarFileConstructor.newInstance(
                    f, Boolean.TRUE, Integer.valueOf(ZipFile.OPEN_READ), RUNTIME_VERSION);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IOException(e);
        }
    }


    @Override
    public boolean jarFileIsMultiRelease(JarFile jarFile) {
        try {
            return ((Boolean) isMultiReleaseMethod.invoke(jarFile)).booleanValue();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }


    @Override
    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }


    @Override
    public boolean canAcccess(Object base, AccessibleObject accessibleObject) {
        try {
            return ((Boolean) canAccessMethod.invoke(accessibleObject, base)).booleanValue();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }


    @Override
    public boolean isExported(Class<?> type) {
        try {
            String packageName = type.getPackage().getName();
            Object module = getModuleMethod.invoke(type);
            return ((Boolean) isExportedMethod.invoke(module, packageName)).booleanValue();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
