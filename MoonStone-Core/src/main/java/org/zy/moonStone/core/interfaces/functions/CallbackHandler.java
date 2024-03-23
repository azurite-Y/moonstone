package org.zy.moonstone.core.interfaces.functions;

/**
 * @dateTime 2022年11月23日;
 * @author zy(azurite-Y);
 * @description
 */
@FunctionalInterface
public interface CallbackHandler<T, R> {
	void work(T t, R r);
}
