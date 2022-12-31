package org.zy.moonStone.core.interfaces.webResources;

import java.io.Closeable;

/**
 * @dateTime 2022年4月12日;
 * @author zy(azurite-Y);
 * @description
 */
public interface TrackedWebResource extends Closeable {
	Exception getCreatedBy();
	String getName();
}
