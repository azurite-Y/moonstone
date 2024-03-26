package org.zy.moonstone.core.interfaces.connector;

import org.zy.moonstone.core.http.Request;
import org.zy.moonstone.core.http.Response;
import org.zy.moonstone.core.util.net.SocketEvent;

/**
 * @dateTime 2022年1月21日;
 * @author zy(azurite-Y);
 * @description 适配器。这表示servlet容器中的入口点
 */
public interface Adapter {
	/**
     * 调用服务方法，并通知所有的监听器
     *
     * @param req - 请求对象
     * @param res - 响应对象
     *
     * @exception Exception - 如果在处理请求期间发生错误。 常见错误有：
     *   <ul>
     *   	 <li>如果发生输入/输出错误并且我们正在处理包含的 servlet（否则它会被顶级错误处理程序机制吞下和处理）
     *       <li>如果 servlet 抛出异常并且我们正在处理包含的 servlet（否则它会被顶级错误处理机制吞并和处理）
     *  </ul>
     *  moonstone 应该能够处理和记录任何其他异常（包括运行时异常）
     */
    public void service(Request req, Response res) throws Exception;

    /**
     * 准备给定的请求/响应以进行处理。 此方法要求请求对象已填充 HTTP 标头中可用的信息
     *
     * @param req - 请求对象
     * @param res - 响应对象
     *
     * @return 如果处理可以继续，则为 true，否则为 false，在这种情况下，将在响应中设置适当的错误
     * @throws Exception - 如果处理意外失败
     */
//    public boolean prepare(Request req, Response res) throws Exception;

    public boolean asyncDispatch(Request req,Response res, SocketEvent status) throws Exception;

    public void log(Request req, Response res, long time);

    /**
     * 断言请求和响应已被回收。如果没有，则记录警告并强制回收。
     * 当一个处理器被回收并可能被返回到池中进行重用时，这个方法被作为一个安全检查调用。
     *
     * @param req - 请求对象
     * @param res - 响应对象
     */
    public void checkRecycled(Request req, Response res);
}
