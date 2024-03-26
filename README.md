# Moonstone

## 一、介绍
Tomcat 的基本实现，当前可内嵌到Fluorite中作为Servlet Web 容器运作，暂不支持外部目录式部署的运作方式。

## 二、测试项目地址
框架测试项目地址：https://gitee.com/azurite_y/moonstone-test <br/>

## 三、使用说明

1.  当前依托于 Fluorite 的自动配置而嵌入其内部运作。考虑到文件变动导致的应用自动重启的冲突性，故在嵌入时不启用 Context 重加载。而由 Fluorite 控制应用程序的自动重启。
2.  当前 Fluorite 还未开发出如 SpringMVC 那样的 Servlet 封装支持，所以当前只能使用 Moonstone 自带的 DefaultServlet 响应请求。所以静态资源和请求的响应都由其完成。但 resources 目录下的 static 和templates 目录下的资源，如果有的话 Moonstone 会自动挂载到根目录下，以符合 Servlet 映射要求。
3.  当前对内嵌 Moonstone 的配置粒度有限，参见 Fluorite-autoconfigure 包下 “org.zy.fluorite.autoconfigure.web.ServerProperties” 类。
4.  对于响应请求支持基本的 Context-Length 报文格式和 Chunked 格式。支持 GZIP 压缩处理。
