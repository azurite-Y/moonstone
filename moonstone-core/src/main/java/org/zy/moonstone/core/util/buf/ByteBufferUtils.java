package org.zy.moonstone.core.util.buf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonstone.core.util.compat.JreCompat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * @dateTime 2022年1月20日;
 * @author zy(azurite-Y);
 * @description
 */
public class ByteBufferUtils {
	private static final Logger logger = LoggerFactory.getLogger(ByteBufferUtils.class);

    private static final Object unsafe;
    private static final Method cleanerMethod;
    private static final Method cleanMethod;
    private static final Method invokeCleanerMethod;

    static {
    	// 直接分配一个新的字节缓冲区
        ByteBuffer tempBuffer = ByteBuffer.allocateDirect(0);
        Method cleanerMethodLocal = null;
        Method cleanMethodLocal = null;
        Object unsafeLocal = null;
        Method invokeCleanerMethodLocal = null;
        if (JreCompat.isJre9Available()) {
            try {
                Class<?> clazz = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = clazz.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafeLocal = theUnsafe.get(null);
                invokeCleanerMethodLocal = clazz.getMethod("invokeCleaner", ByteBuffer.class);
                invokeCleanerMethodLocal.invoke(unsafeLocal, tempBuffer);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | NoSuchFieldException e) {
                logger.warn("byteBufferUtils.cleaner", e);
                unsafeLocal = null;
                invokeCleanerMethodLocal = null;
            }
        } else {
            try {
            	// 分配一个新的直接字节缓冲区
                cleanerMethodLocal = tempBuffer.getClass().getMethod("cleaner");
                cleanerMethodLocal.setAccessible(true);
                Object cleanerObject = cleanerMethodLocal.invoke(tempBuffer);
                cleanMethodLocal = cleanerObject.getClass().getMethod("clean");
                cleanMethodLocal.invoke(cleanerObject);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                logger.warn("byteBufferUtils.cleaner", e);
                cleanerMethodLocal = null;
                cleanMethodLocal = null;
            }
        }
        cleanerMethod = cleanerMethodLocal;
        cleanMethod = cleanMethodLocal;
        unsafe = unsafeLocal;
        invokeCleanerMethod = invokeCleanerMethodLocal;
    }

    private ByteBufferUtils() {
        // 隐藏默认构造函数，因为这是一个实用程序类
    }


    /**
     * 将缓冲区扩展到给定大小，除非它已经很大或更大。假定缓冲区处于“写入”模式，因为在“读取”模式下不需要扩展缓冲区。
     *
     * @param in - 缓冲区扩大
     * @param newSize - 应该扩展缓冲区的大小
     * @return 扩展的缓冲区，其中包含来自输入缓冲区的任何数据复制到它或原始缓冲区（如果不需要扩展）
     */
    public static ByteBuffer expand(ByteBuffer in, int newSize) {
        if (in.capacity() >= newSize) {
            return in;
        }

        ByteBuffer out;
        boolean direct = false;
        if (in.isDirect()) { // 说明这个字节缓冲区是否是直接的
        	// 分配一个新的直接字节缓冲区
            out = ByteBuffer.allocateDirect(newSize);
            direct = true;
        } else {
        	// 分配一个新的字节缓冲区
            out = ByteBuffer.allocate(newSize);
        }

        // Copy data
        in.flip();
        out.put(in);

        if (direct) {
            cleanDirectBuffer(in);
        }

        return out;
    }

    public static void cleanDirectBuffer(ByteBuffer buf) {
        if (cleanMethod != null) {	
            try {
                cleanMethod.invoke(cleanerMethod.invoke(buf));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("byteBufferUtils.cleaner", e);
                }
            }
        } else if (invokeCleanerMethod != null) {
            try {
                invokeCleanerMethod.invoke(unsafe, buf);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("byteBufferUtils.cleaner", e);
                }
            }
        }
    }
}
