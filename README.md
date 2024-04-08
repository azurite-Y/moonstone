# <font face="宋体" color=#ED7D31>Moonstone</font>

## <font face="宋体" color=#5B9BD5>一、介绍</font>
Tomcat 的基本实现，当前可内嵌到 Fluorite 中作为 Servlet Web 容器运作。暂不支持外部目录式部署的运作方式，所以当前没办法单独运行。

## <font face="宋体" color=#5B9BD5>二、测试项目地址</font>
框架测试项目地址：[moonstone-test](https://gitee.com/azurite_y/moonstone-test)

## <font face="宋体" color=#5B9BD5>三、使用说明</font>

1. 当前依托于 Fluorite 的自动配置而嵌入其内部运作。考虑到文件变动导致的应用自动重启功能的冲突性，故在嵌入时不启用 Context 重加载。而由 Fluorite 控制应用程序的自动重启与否。
2.  当前 Fluorite 还未开发出如 SpringMVC 那样的 Servlet 封装支持，所以当前只能使用 Moonstone 自带的 DefaultServlet 响应请求。所以静态资源和请求的响应都由其完成。"resources/static" 和 “resources/templates” 目录下的资源，如果有的话 Moonstone 会自动挂载到根目录下，以符合 Servlet 映射要求。
3.  当前对内嵌 Moonstone 的配置粒度有限，参见 Fluorite-autoconfigure 包下 “org.zy.fluorite.autoconfigure.web.ServerProperties” 类。
4.  对于响应请求支持基本的 Context-Length 报文格式和 Chunked 格式。支持 GZIP 压缩处理。
