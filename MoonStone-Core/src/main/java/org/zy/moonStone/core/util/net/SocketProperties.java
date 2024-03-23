package org.zy.moonstone.core.util.net;

import org.zy.moonstone.core.util.net.NioEndpoint.SocketProcessor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * @dateTime 2022年1月12日;
 * @author zy(azurite-Y);
 * @description 所有属性都以&quot;socket.&quot作为前缀，目前仅适用于Nio连接器
 */
public class SocketProperties {
	/**
	 * 启用/禁用socket处理器缓存，这个有界缓存存储 {@link SocketProcessor } 对象以减少GC。
	 * 默认值是500，-1为不作限制，0为被禁用
	 */
	protected int processorCache = 500;

	/**
	 * 启用/禁用轮询器事件缓存，这个有界缓存存储 {@link NioEndpoint.PollerEvent } 对象以减少轮询器的GC。
	 * 默认值是500，-1为不作限制，0为被禁用。
	 */
	protected int eventCache = 500;

	/**
	 * 启用/禁用网络缓冲区的直接缓冲区。缺省值为禁用
	 */
	protected boolean directBuffer = false;

	/**
	 * 启用/禁用SSL的网络缓冲区的直接缓冲区。缺省值为禁用
	 */
	protected boolean directSslBuffer = false;

	/**
	 * 套接字以字节为单位接收缓冲区大小(SO_RCVBUF)。如果没有设置，则使用JVM默认值。
	 */
	protected Integer rxBufSize = null;

	/**
	 * 套接字发送缓冲区字节大小(SO_SNDBUF)。如果没有设置，则使用JVM默认值。
	 */
	protected Integer txBufSize = null;

	/**
	 * 字节缓冲区的初始尺寸。
	 */
	protected int initialCapacity = 2048;

	/**
	 * 字节缓冲区的最大尺寸。
	 */
	protected int maxCapacity = 8192;

	/**
	 * Endpoint的NioChannel池大小，这个值是通道的数量—— -1表示无限缓存，0表示没有缓存。默认值是500
	 */
	protected int bufferPool = 500;

	/**
	 * 缓冲池大小（以要缓存的字节为单位）-1表示无限制，0表示无缓存。默认值为100MB（1024*1024*100字节）
	 */
	protected int bufferPoolSize = 1024*1024*100;

	/**
	 * TCP_NO_DELAY选项。如果没有设置，则使用JVM默认值。
	 */
	protected Boolean tcpNoDelay = Boolean.TRUE;

	/**
	 * SO_KEEPALIVE选项。如果没有设置，则使用JVM默认值
	 */
	protected Boolean soKeepAlive = null;

	/**
	 * OOBINLINE选项。如果没有设置，则使用JVM默认值
	 */
	protected Boolean ooBInline = null;

	/**
	 * SO_REUSEADDR选项。如果没有设置，则使用JVM默认值。
	 */
	protected Boolean soReuseAddress = null;

	/**
	 * SO_LINGER选项，与soLingerTime值配对。除非设置了这两个属性，否则将使用JVM默认值。
	 */
	protected Boolean soLingerOn = null;

	/**
	 * SO_LINGER选项，与soLingerOn值配对。除非设置了这两个属性，否则将使用JVM默认值。
	 */
	protected Integer soLingerTime = null;

	/**
	 * SO_TIMEOUT选项。默认是20000
	 */
	protected Integer soTimeout = Integer.valueOf(20000);

	/**
	 * {@link Socket#setPerformancePreferences(int, int, int) } 必须设置所有这三个性能属性，否则将设置JVM的默认值
	 * @see Socket#setPerformancePreferences(int, int, int)
	 */
	protected Integer performanceConnectionTime = null;

	/**
	 * {@link Socket#setPerformancePreferences(int, int, int) } 必须设置所有这三个性能属性，否则将设置JVM的默认值
	 * @see Socket#setPerformancePreferences(int, int, int)
	 */
	protected Integer performanceLatency = null;

	/**
	 * {@link Socket#setPerformancePreferences(int, int, int) } 必须设置所有这三个性能属性，否则将设置JVM的默认值
	 * @see Socket#setPerformancePreferences(int, int, int)
	 */
	protected Integer performanceBandwidth = null;

	/**
	 * 超时间隔的最小频率，以避免在高流量期间轮询器产生额外负载
	 */
	protected long timeoutInterval = 1000;

	/**
	 * 发生解锁的超时时间(毫秒)
	 */
	protected int unlockTimeout = 250;


	public void setProperties(Socket socket) throws SocketException{
		if (rxBufSize != null)
			socket.setReceiveBufferSize(rxBufSize.intValue());
		if (txBufSize != null)
			socket.setSendBufferSize(txBufSize.intValue());
		if (ooBInline !=null)
			socket.setOOBInline(ooBInline.booleanValue());
		if (soKeepAlive != null)
			socket.setKeepAlive(soKeepAlive.booleanValue());
		if (performanceConnectionTime != null && performanceLatency != null &&
				performanceBandwidth != null)
			socket.setPerformancePreferences(
					performanceConnectionTime.intValue(),
					performanceLatency.intValue(),
					performanceBandwidth.intValue());
		if (soReuseAddress != null)
			socket.setReuseAddress(soReuseAddress.booleanValue());
		if (soLingerOn != null && soLingerTime != null)
			socket.setSoLinger(soLingerOn.booleanValue(),
					soLingerTime.intValue());
		if (soTimeout != null && soTimeout.intValue() >= 0)
			socket.setSoTimeout(soTimeout.intValue());
		if (tcpNoDelay != null) {
			try {
				socket.setTcpNoDelay(tcpNoDelay.booleanValue());
			} catch (SocketException e) {
				// 一些套接字类型可能不支持这个默认设置的选项
			}
		}
	}

	public void setProperties(ServerSocket serverSocket) throws SocketException{
		if (rxBufSize != null)
			// 设置套接字以字节为单位接收缓冲区大小
			serverSocket.setReceiveBufferSize(rxBufSize.intValue());
		if (performanceConnectionTime != null && performanceLatency != null && performanceBandwidth != null)
			/**
			 * 设置此ServerSocket的性能首选项。
			 * 
			 * 默认情况下，套接字使用TCP/IP协议。一些实现可能提供具有不同于TCP/IP的性能特征的替代协议。
			 * 该方法允许应用程序表达自己的偏好，即当实现从可用协议中进行选择时，如何进行这些权衡。
			 * 
			 * 性能首选项由三个整数表示，这些值表示短连接时间、低延迟和高带宽的相对重要性。
			 * 整数的绝对值无关紧要；为了选择协议，对这些值进行简单比较，较大的值表示更强的偏好。
			 * 例如，如果应用程序喜欢短连接时间而不是低延迟和高带宽，那么它可以使用值(1, 0, 0)调用此方法。
			 * 如果应用程序喜欢高带宽高于低延迟，而低延迟高于短连接时间，那么它可以使用值（0，1，2）调用此方法。
			 * 
			 * 在绑定此套接字后调用此方法将无效。这意味着为了使用此功能，需要使用无参数构造函数创建套接字。
			 * 
			 * @param connectionTime - 表示短连接时间相对重要性的整数
			 * @param latency - 表示低延迟相对重要性的整数
			 * @param bandwidth -  表示高带宽相对重要性的整数
			 */
			serverSocket.setPerformancePreferences(performanceConnectionTime.intValue(), performanceLatency.intValue(), performanceBandwidth.intValue());
		if (soReuseAddress != null)
			/**
			 * 启用/禁用SO_REUSEADDR套接字选项。
			 * 
			 * 当TCP连接被关闭时，连接可能在连接被关闭后的一段时间内仍处于超时状态(通常称为TIME_WAIT状态或2MSL等待状态)。
			 * 对于使用已知套接字地址或端口的应用程序，如果有一个超时状态的连接涉及到套接字地址或端口，则可能无法将套接字绑定到所需的SocketAddress。
			 * 
			 * 在使用bind(SocketAddress)绑定套接字之前启用SO_REUSEADDR，允许即使前一个连接处于超时状态也可以绑定套接字。
			 * 创建ServerSocket时，不定义SO_REUSEADDR的初始设置。应用程序可以使用getReuseAddress()来确定SO_REUSEADDR的初始设置。
			 * 
			 * @param on - 启用或禁用套接字选项
			 */
			serverSocket.setReuseAddress(soReuseAddress.booleanValue());
		if (soTimeout != null && soTimeout.intValue() >= 0)
			/**
			 * 使用指定的超时时间启用/禁用SO_TIMEOUT，单位为毫秒。
			 * 如果将此选项设置为非零超时，则对该serversocket的accept()调用将只阻塞这个时间。
			 * 如果超时超时，则抛出java.net.SocketTimeoutException，尽管ServerSocket仍然有效。
			 * 该选项必须在进入阻塞操作之前启用才能生效。超时时间必须为> 0。零超时被解释为无限超时。
			 * 
			 * @param timeout - 指定的超时时间，以毫秒为单位
			 */
			serverSocket.setSoTimeout(soTimeout.intValue());
	}

	public void setProperties(AsynchronousSocketChannel socket) throws IOException {
		if (rxBufSize != null)
			// 设置套接字以字节为单位接收缓冲区大小
			socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
		if (txBufSize != null)
			// 设置套接字发送缓冲区的大小
			socket.setOption(StandardSocketOptions.SO_SNDBUF, txBufSize);
		if (soKeepAlive != null)
			/**
			 * 设置启用还是禁用 keep-alive
			 * 
			 * 该套接字选项的值是一个布尔值，表示该选项是启用还是禁用。
			 * 当启用SO_KEEPALIVE选项时，当连接处于空闲状态时，操作系统可能会使用keep-alive机制定期探测连接的另一端。
			 * keep-alive机制的确切语义依赖于系统，因此没有指定。
			 * 
			 * 该套接字选项的初始值为false。socket可以在任何时候启用或禁用。
			 */
			socket.setOption(StandardSocketOptions.SO_KEEPALIVE, soKeepAlive);
		if (soReuseAddress != null)
			/**
			 * 重用的地址。
			 * 
			 * 该套接字选项的值是一个布尔值，表示该选项是启用还是禁用。这个套接字选项的确切语义取决于套接字类型和系统。
			 * 
			 * 在面向流的套接字的情况下，这个套接字选项通常会决定当涉及套接字地址的前一个连接处于TIME_WAIT状态时，该套接字是否可以绑定到该套接字地址。
			 * 在语义不同的实现中，当前一个连接处于这种状态时，不需要启用套接字选项来绑定套接字，那么实现可以选择忽略此选项。
			 * 
			 * 对于面向数据报的套接字，socket选项用于允许多个程序绑定到同一地址。当套接字用于Internet协议(IP)组播时，应该启用此选项。
			 * 
			 * 实现允许在绑定或连接套接字之前设置此套接字选项。在绑定套接字后更改此套接字选项的值是没有效果的。该套接字选项的默认值与系统相关。
			 */
			socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
		if (soLingerOn != null && soLingerOn.booleanValue() && soLingerTime != null)
			/**
			 * 如果有数据，请在关闭时等待。
			 * 
			 * 此套接字选项的值是一个整数，它控制未发送数据在套接字上排队并调用关闭套接字的方法时所采取的操作。
			 * 如果套接字选项的值为零或更大，则表示超时值(以秒为单位), 称为延迟时间间隔。
			 * 延迟时间间隔是当操作系统尝试传输未发送的数据或确定无法传输数据时，关闭方法要阻止的超时时间。
			 * 如果套接字选项的值小于零，则禁用该选项。在这种情况下，关闭方法不会等到发送未发送的数据；
			 * 如果可能，操作系统将在连接关闭之前发送任何未发送的数据。
			 * 
			 * 此套接字选项仅用于在阻塞模式下配置的套接字。未定义在非阻塞套接字上启用此选项时close方法的行为。
			 * 
			 * 该套接字选项的初始值为负值，表示该选项处于禁用状态。可以随时启用该选项或更改延迟时间间隔。
			 * 逗留间隔的最大值取决于系统。将延迟间隔设置为大于其最大值的值会导致延迟间隔设置为其最大值。
			 */
			socket.setOption(StandardSocketOptions.SO_LINGER, soLingerTime);
		if (tcpNoDelay != null)
			/**
			 * 禁用Nagle算法。
			 * 
			 * 该套接字选项的值是一个布尔值，表示该选项是启用还是禁用。套接字选项特定于使用TCP/IP协议的面向流的套接字。
			 * TCP/IP使用一种称为Nagle算法的算法来合并短段，提高网络效率。
			 * 
			 * 该套接字选项的默认值是FALSE。套接字选项应该只在知道合并影响性能的情况下启用。套接字选项可以在任何时候启用。
			 * 换句话说，可以禁用Nagle算法。一旦启用了该选项，随后是否可以禁用它取决于系统。如果不能，则调用setOption方法禁用该选项没有效果。
			 */
			socket.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
	}

	public void setProperties(AsynchronousServerSocketChannel socket) throws IOException {
		if (rxBufSize != null)
			socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
		if (soReuseAddress != null)
			socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
	}

	/**
	 * 
	 * @return 启用/禁用网络缓冲区的直接缓冲区。缺省值为禁用
	 */
	public boolean getDirectBuffer() {
		return directBuffer;
	}

	public boolean getDirectSslBuffer() {
		return directSslBuffer;
	}

	public boolean getOoBInline() {
		return ooBInline.booleanValue();
	}

	public int getPerformanceBandwidth() {
		return performanceBandwidth.intValue();
	}

	public int getPerformanceConnectionTime() {
		return performanceConnectionTime.intValue();
	}

	public int getPerformanceLatency() {
		return performanceLatency.intValue();
	}

	public int getRxBufSize() {
		return rxBufSize.intValue();
	}

	public boolean getSoKeepAlive() {
		return soKeepAlive.booleanValue();
	}

	public boolean getSoLingerOn() {
		return soLingerOn.booleanValue();
	}

	public int getSoLingerTime() {
		return soLingerTime.intValue();
	}

	public boolean getSoReuseAddress() {
		return soReuseAddress.booleanValue();
	}

	public int getSoTimeout() {
		return soTimeout.intValue();
	}

	public boolean getTcpNoDelay() {
		return tcpNoDelay.booleanValue();
	}

	public int getTxBufSize() {
		return txBufSize.intValue();
	}

	public int getBufferPool() {
		return bufferPool;
	}

	public int getBufferPoolSize() {
		return bufferPoolSize;
	}

	public int getEventCache() {
		return eventCache;
	}

	/**
	 * 
	 * @return 字节缓冲区的初始尺寸
	 */
	public int getInitialCapacity() {
		return initialCapacity;
	}
	public void setInitialCapacity(int initialCapacity) {
		this.initialCapacity = initialCapacity;
	}

	/**
	 * @return 字节缓冲区的最大尺寸
	 */
	public int getMaxCapacity() {
		return maxCapacity;
	}
	public void setMaxCapacity(int maxCapacity) {
		this.maxCapacity = maxCapacity;
	}

	public int getProcessorCache() {
		return processorCache;
	}

	public long getTimeoutInterval() {
		return timeoutInterval;
	}

	public int getDirectBufferPool() {
		return bufferPool;
	}

	public void setPerformanceConnectionTime(int performanceConnectionTime) {
		this.performanceConnectionTime =
				Integer.valueOf(performanceConnectionTime);
	}

	public void setTxBufSize(int txBufSize) {
		this.txBufSize = Integer.valueOf(txBufSize);
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = Boolean.valueOf(tcpNoDelay);
	}

	public void setSoTimeout(int soTimeout) {
		this.soTimeout = Integer.valueOf(soTimeout);
	}

	public void setSoReuseAddress(boolean soReuseAddress) {
		this.soReuseAddress = Boolean.valueOf(soReuseAddress);
	}

	public void setSoLingerTime(int soLingerTime) {
		this.soLingerTime = Integer.valueOf(soLingerTime);
	}

	public void setSoKeepAlive(boolean soKeepAlive) {
		this.soKeepAlive = Boolean.valueOf(soKeepAlive);
	}

	public void setRxBufSize(int rxBufSize) {
		this.rxBufSize = Integer.valueOf(rxBufSize);
	}

	public void setPerformanceLatency(int performanceLatency) {
		this.performanceLatency = Integer.valueOf(performanceLatency);
	}

	public void setPerformanceBandwidth(int performanceBandwidth) {
		this.performanceBandwidth = Integer.valueOf(performanceBandwidth);
	}

	public void setOoBInline(boolean ooBInline) {
		this.ooBInline = Boolean.valueOf(ooBInline);
	}

	public void setDirectBuffer(boolean directBuffer) {
		this.directBuffer = directBuffer;
	}

	public void setDirectSslBuffer(boolean directSslBuffer) {
		this.directSslBuffer = directSslBuffer;
	}

	public void setSoLingerOn(boolean soLingerOn) {
		this.soLingerOn = Boolean.valueOf(soLingerOn);
	}

	public void setBufferPool(int bufferPool) {
		this.bufferPool = bufferPool;
	}

	public void setBufferPoolSize(int bufferPoolSize) {
		this.bufferPoolSize = bufferPoolSize;
	}

	public void setEventCache(int eventCache) {
		this.eventCache = eventCache;
	}

	public void setProcessorCache(int processorCache) {
		this.processorCache = processorCache;
	}

	public void setTimeoutInterval(long timeoutInterval) {
		this.timeoutInterval = timeoutInterval;
	}

	public void setDirectBufferPool(int directBufferPool) {
		this.bufferPool = directBufferPool;
	}

	public int getUnlockTimeout() {
		return unlockTimeout;
	}

	public void setUnlockTimeout(int unlockTimeout) {
		this.unlockTimeout = unlockTimeout;
	}
}
