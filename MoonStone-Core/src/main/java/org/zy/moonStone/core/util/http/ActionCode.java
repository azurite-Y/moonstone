package org.zy.moonStone.core.util.http;

/**
 * @dateTime 2022年5月23日;
 * @author zy(azurite-Y);
 * @description ActionCode 表示从 servlet 容器到连接器的回调。通过使用actionhook接口的ProtocolHandler实现
 */
public enum ActionCode {
	ACK,
    CLOSE,
    COMMIT,

    /**
     * 发生了无法安全恢复的严重错误。 应忽略进一步写入响应的尝试，并且需要尽快关闭连接。 如果在响应提交后发生错误，这也可以用于强制关闭连接。
     */
    CLOSE_NOW,

    /**
     * 由客户端发起的flush()操作(例如，servlet输出流或写入器上的flush()，由servlet调用)。参数是Response。
     */
    CLIENT_FLUSH,

    /**
     * 处理器是否处于错误状态?注意，响应可能没有适当的错误代码集。
     */
    IS_ERROR,

    /**
     * 处理器可能已进入错误状态，并且某些错误状态不允许任何进一步的 I/O。 当前是否允许 I/O？
     */
    IS_IO_ALLOWED,

    /**
     * 如果应该禁用吞咽请求输入，则调用该钩子。例如:取消上传大文件。
     */
//    DISABLE_SWALLOW_INPUT,

    /**
     * 延迟评估的回调 - 提取远程主机名和地址。
     */
    REQ_HOST_ATTRIBUTE,

    /**
     * 延迟评估的回调 - 提取远程主机地址。
     */
    REQ_HOST_ADDR_ATTRIBUTE,

    /**
     * 延迟评估的回调 - 提取与 SSL 相关的属性，包括客户端证书（如果存在）
     */
    REQ_SSL_ATTRIBUTE,

    /**
     * 强制 TLS 重新握手并使生成的客户端证书（如果有）可用作请求属性
     */
    REQ_SSL_CERTIFICATE,

    /**
     * 延迟评估的回调 - 套接字远程端口
     */
    REQ_REMOTEPORT_ATTRIBUTE,

    /**
     * 延迟评估的回调 - 套接字本地端口
     */
    REQ_LOCALPORT_ATTRIBUTE,

    /**
     * 延迟评估的回调 - 本地地址
     */
    REQ_LOCAL_ADDR_ATTRIBUTE,

    /**
     * 延迟评估的回调 - 本地主机名
     */
    REQ_LOCAL_NAME_ATTRIBUTE,

    /**
     * 设置表单验证体重放的回调
     */
//    REQ_SET_BODY_REPLAY,

    /**
     * 用于获取可用字节数的回调
     */
//    AVAILABLE,

    /**
     * 异步请求的回调
     */
    ASYNC_START,

    /**
     * 对 {@link javax.servlet.AsyncContext#dispatch()} 的异步调用的回调 .
     */
    ASYNC_DISPATCH,

    /**
     * 回调，以指示实际分派已经启动，并且需要更改异步状态
     */
    ASYNC_DISPATCHED,

    /**
     * 对 {@link javax.servlet.AsyncContext#start(Runnable)} 进行异步调用的回调
     */
    ASYNC_RUN,

    /**
     * 对 {@link javax.servlet.AsyncContext#complete()} 进行异步调用的回调
     */
    ASYNC_COMPLETE,

    /**
     * 触发异步超时处理的回调
     */
    ASYNC_TIMEOUT,

    /**
     * 触发错误处理的回调
     */
    ASYNC_ERROR,

    /**
     * 对 {@link javax.servlet.AsyncContext#setTimeout(long)} 进行异步调用的回调
     */
    ASYNC_SETTIMEOUT,

    /**
     * 确定异步处理是否正在进行中的回调
     */
    ASYNC_IS_ASYNC,

    /**
     * 确定异步调度是否正在进行中的回调
     */
    ASYNC_IS_STARTED,

    /**
     * 确定异步完成是否正在进行中的回调
     */
    ASYNC_IS_COMPLETING,

    /**
     * 确定异步调度是否正在进行的回调
     */
    ASYNC_IS_DISPATCHING,

    /**
     * 确定异步是否超时的回调
     */
    ASYNC_IS_TIMINGOUT,

    /**
    * 确定异步是否出错的回调
    */
    ASYNC_IS_ERROR,

    /**
     * 回调以触发后处理。 通常仅在错误处理期间用于触发否则将被跳过的基本处理。
     */
    ASYNC_POST_PROCESS,

    /**
     * 回调以触发 HTTP 升级过程。
     */
    UPGRADE,

    /**
     * 指示 Servlet 有兴趣在数据可供读取时收到通知
     */
//    NB_READ_INTEREST,

    /**
     * 与非阻塞写入一起使用以确定当前是否允许写入（将传递的参数设置为 <code>true</code>）或不允许（将传递的参数设置为 <code>false</code>）。 如果不允许写入，那么当写入再次成为可能时，将在将来某个时间点触发回调。
     */
//    NB_WRITE_INTEREST,

    /**
     * 指示请求正文是否已被完全读取
     */
//    REQUEST_BODY_FULLY_READ,

    /**
     * 表示容器需要为注册的非阻塞读监听触发onDataAvailable()的调用
     */
    DISPATCH_READ,

    /**
     * 表示容器需要为注册的非阻塞写监听触发onWritePossible()的调用
     */
    DISPATCH_WRITE,

    /**
     * 执行通过 {@link #DISPATCH_READ} 或 {@link #DISPATCH_WRITE} 注册的任何非阻塞调度。 当非阻塞侦听器配置在处理不是由套接字上的读取或写入事件触发的线程上时，通常需要。
     */
    DISPATCH_EXECUTE,

    /**
     * 当前请求是否支持并允许服务器推送？
     */
    IS_PUSH_SUPPORTED,

    /**
     * 代表当前请求的客户端推送请求。
     */
    PUSH_REQUEST,

    /**
     * 请求尾部字段准备好被读取了吗?注意，如果已知请求尾部字段不受支持，则直接返回true，这样就可以读取尾部的空集合。
     */
    IS_TRAILER_FIELDS_READY,

    /**
     * 当前响应是否支持 HTTP 尾字段？ 请注意，一旦提交了 HTTP/1.1 响应，它将不再支持尾部字段
     */
    IS_TRAILER_FIELDS_SUPPORTED,
    
    /**
     * 获取请求的连接标识符。用于多种协议，如HTTP/2
     */
    CONNECTION_ID,

    /**
     * 获取请求的流标识符。用于多种协议，如HTTP/2
     */
    STREAM_ID
}
