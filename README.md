# Moonstone

#### 介绍
Tomcat的基本实现，当前可内嵌到Fluorite中作为Servlet Web 容器运作，暂不支持支持外部目录式部署的运作方式

#### 软件架构
1. 当前依托于Fluorite的自动配置而嵌入其内部运作。当前考虑到应用自动重启的冲突性，故在嵌入时不启用 Context 重加载。而由Fluorite控制应用程序的自动重启。


#### 安装教程

1.  引入Fluorite和MoonStone依赖
```xml
<dependencies>
	<dependency>
		<groupId>org.zy.fluorite</groupId>
		<artifactId>fluorite-boot</artifactId>
		<version>1.0.0.RELEASE</version>
	</dependency>
	<dependency>
		<groupId>org.zy.moonStone</groupId>
		<artifactId>MoonStone-Core</artifactId>
		<version>1.0.0.RELEASE</version>
	</dependency>
    <!-- Fluorite @ConfigurationProperties 注解读取配置文件支持 -->
    <dependency>
		<groupId>com.alibaba</groupId>
		<artifactId>fastjson</artifactId>
		<version>1.2.62</version>
	</dependency>
    <!-- Fluorite Servlet 环境支持 -->
	<dependency>
		<groupId>javax.servlet</groupId>
		<artifactId>javax.servlet-api</artifactId>
		<version>4.0.1</version>
	</dependency>
    <!-- Fluorite使用的日志框架 -->
    <dependency>
		<groupId>org.slf4j</groupId>
		<artifactId>slf4j-api</artifactId>
		<version>1.7.25</version>
	</dependency>
	<dependency>
		<groupId>ch.qos.logback</groupId>
		<artifactId>logback-classic</artifactId>
		<version>1.2.3</version>
	</dependency>
<dependencies>
```
2. 新建 Fluorite 根启动类

```java
@RunnerAs
public class App {
    public static void main(String[] args) {
       	FluoriteApplication.run(App.class, args);
	}
}
```

#### 使用说明

1.  当前Fluorite还未开发出如SpringMVC那样的Servlet封装支持，所以当前只能使用MoonStone自带的 DefaultServlet 响应请求。所以静态资源和请求的响应都由其完成。但resources目录下的static和templates目录下的资源，如果有的话MoonStone会自动挂载到根目录下，以符合Servlet映射要求。
2.  当前对内嵌MoonStone的配置粒度有限，参见Fluorite-autoconfigure包下org.zy.fluorite.autoconfigure.web.ServerProperties类
3.  对于响应请求支持基本的Context-Length报文格式和Chunked格式。支持GZIP压缩处理
