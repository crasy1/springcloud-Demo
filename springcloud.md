#springcloud 学习
[https://blog.csdn.net/forezp/article/details/70148833](https://blog.csdn.net/forezp/article/details/70148833)
---
## 一.创建服务注册中心[https://blog.csdn.net/forezp/article/details/81040925](https://blog.csdn.net/forezp/article/details/81040925 "服务注册")
采用Eureka作为服务注册与发现的组件

1. 创建gradle主工程
2. 加入依赖

		ext {
		    springcloud = "2.1.1.RELEASE"
		    springboot = "2.1.3.RELEASE"
		    fastjson = '1.2.57'
		    lombok = '1.18.6'
		}
		dependencies {
		    compile group: 'org.springframework.boot', name: 'spring-boot-starter-web', version: rootProject.ext.springboot
		    compile group: 'org.springframework.cloud', name: 'spring-cloud-starter', version: rootProject.ext.springcloud
		    compile group: 'com.alibaba', name: 'fastjson', version: rootProject.ext.fastjson
		    compile group: 'org.projectlombok', name: 'lombok', version: rootProject.ext.lombok
		}

3. 新建 `eureka server` 模块 
 
	    file->project structure->modules->new module->spring initializr->gradle project->cloud discovery->eureka server

4. 启动一个服务注册中心，在springboot工程的启动 `application` 类上加 `@EnableEurekaServer`
5. 配置 `eureka server` 模块 `yml`

		server:
		  port: 8761
		
		eureka:
		  instance:
		    hostname: localhost
		  client:
		    registerWithEureka: false
		    fetchRegistry: false
		    serviceUrl:
		      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
		
		spring:
		  application:
		    name: eurka-server

	此处有一坑,服务名不能用下划线,否则实现ribbon负载均衡的时候会找不到服务名

6. 新建 `eureka client` (服务提供者)模块(与 `eureka server` 模块类似),
   加入依赖 `spring-cloud-starter-netflix-eureka-client` , 启动 `application` 类上加 `@EnableEurekaClient` ,再配置一个 `controller`

		@RestController
		public class TestController {
		    @Value("${server.port}")
		    String port;
		
		    @RequestMapping("/hi")
		    public String home(@RequestParam(value = "name", defaultValue = "forezp") String name) {
		        return "hi " + name + " ,i am from port:" + port;
		    }
		}
7. 在配置文件中 `application.yml` 注明自己的服务注册中心的地址
		
		server:
		  port: 8762
		
		spring:
		  application:
		    name: eureka-client
		
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8761/eureka/
8. 启动 `eureka_client` 再启动 `eureka_server`,打开 [http://localhost:8761](http://localhost:8761 "eureka_server") 会发现一个服务已经注册在服务中了 ,服务名 `EUREKA_CLIENT`,端口 `8762` 这时候打开 `http://localhost:8762/hi?name=forezp`

 ---
## 二.服务消费者[https://blog.csdn.net/forezp/article/details/81040946](https://blog.csdn.net/forezp/article/details/81040946 "服务消费者ribbon+restTemplate")
rest+ribbon

1. 在idea  `application` vm启动参数中加 `-Dserver.port=123` 就可以模拟集群
2. 新建 `service-ribbon` 模块,加入依赖

	    implementation 'org.springframework.boot:spring-boot-starter-web'
	    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
	    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-ribbon'

3. 配置文件

		eureka:
	  		client:
			    serviceUrl:
			      defaultZone: http://localhost:8761/eureka/
		server:
		  port: 8764
		spring:
		  application:
		    name: service-ribbon
4. 在启动类中通过 `@EnableDiscoveryClient` 向服务中心注册,并且向程序的ioc注入一个bean: `restTemplate`;并通过 `@LoadBalanced` 注解表明这个 `restRemplate` 开启负载均衡的功能

		@SpringBootApplication
		@EnableEurekaClient
		@EnableDiscoveryClient
		public class ServiceRibbonApplication {
		
		    public static void main(String[] args) {
		        SpringApplication.run( ServiceRibbonApplication.class, args );
		    }
		
		    @Bean
		    @LoadBalanced
		    RestTemplate restTemplate() {
		        return new RestTemplate();
		    }
		
		}

5. 新建 `service` 和 `contorller`   

`HelloService` 过之前注入ioc容器的restTemplate来消费 `eureka-client` 服务的 `/hi` 接口  

		@Service
		public class HelloService {
		
		    @Autowired
		    RestTemplate restTemplate;
		
		    public String hiService(String name) {
		        return restTemplate.getForObject("http://EUREKA-CLIENT/hi?name="+name,String.class);
		    }
		
		
		}  

`HelloControler`  在浏览器上多次访问 `http://localhost:8764/hi?name=forezp` 发现会交替请求   
`hi forezp,i am from port:8762`  
`hi forezp,i am from port:8763` 说明通过 `restTemplate.getForObject` 做了负载均衡

		@RestController
		public class HelloControler {
		
		    @Autowired
		    HelloService helloService;
		
		    @GetMapping(value = "/hi")
		    public String hi(@RequestParam String name) {
		        return helloService.hiService( name );
		    }
		}


---
## 三.服务消费者[https://blog.csdn.net/forezp/article/details/81040965](https://blog.csdn.net/forezp/article/details/81040965 "服务消费者feign")  
`Feign` 是一个声明式的伪Http客户端,`Feign` 采用的是基于接口的注解 `Feign` 整合了 `ribbon`

1. 新建 `service_feign` 模块,加入依赖

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}

	启动类加上 `@EnableFeignClients`注解

2. 配置 `yml`   

		server:
		  port: 8765
		spring:
		  application:
		    name: service-feign
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8761/eureka/


3. 新建 `IFeignService` 接口

		@FeignClient(value = "eureka-client")
		public interface IFeignService {
		    @RequestMapping(value = "/hi", method = RequestMethod.GET)
		    String sayHiFromClientOne(@RequestParam(value = "name") String name);
		}
4. 新建 `FeignController` 控制器,访问 `http://localhost:8765/hi?name=aaa` 也实现了负载均衡

		@RestController
		public class FeignController {
		
		    @Autowired
		    IFeignService iFeignService;
		
		    @RequestMapping(value = "/hi", method = RequestMethod.GET)
		    public String sayHi(@RequestParam String name) {
		        return iFeignService.sayHiFromClientOne(name);
		    }
		
		}


## 四.断路器[https://blog.csdn.net/forezp/article/details/81040990](https://blog.csdn.net/forezp/article/details/81040990 "断路器")
`Hystrix` 在微服务架构中，根据业务来拆分成一个个的服务，服务与服务之间可以相互调用（RPC），在Spring Cloud可以用`RestTemplate`+`Ribbon`和`Feign`来调用。为了保证其高可用，单个服务通常会集群部署。由于网络原因或者自身的原因，服务并不能保证100%可用，如果单个服务出现问题，调用这个服务就会出现线程阻塞，此时若有大量的请求涌入，Servlet容器的线程资源会被消耗完毕，导致服务瘫痪。服务与服务之间的依赖性，故障会传播，会对整个微服务系统造成灾难性的严重后果，这就是服务故障的“雪崩”效应

####ribbon中使用断路器
1. 依赖加 `spring-cloud-starter-netflix-hystrix` ,启动类上加 `@EnableHystrix` 开启断路器
2. 改造 `HelloService`
	
		@HystrixCommand(fallbackMethod = "hiError")
	    public String hiService(String name) {
	        return restTemplate.getForObject("http://SERVICE-HI/hi?name="+name,String.class);
	    }
	
	    public String hiError(String name) {
	        return "hi,"+name+",sorry,error!";
	    }
	
标识该方法创建熔断功能,指定 `fallbackMethod`熔断方法,直接返回字符串  
3. 启动 `service-robbin` 和 `eureka-client` 访问 `http://localhost:8764/hi?name=forezp` 显示 `hi forezp,i am from port:8762` ,关闭 `eureka-client` ,再次访问,显示 `hi ,forezp,orry,error!` .表示`eureka-client`不可用的时候,会快速执行失败,而不是等待响应超时,控制了线程阻塞

####Feign中使用断路器
1. `feign` 是字段断路器的,默认没有打开,在配置中加入 `feign.hystrix.enabled=true` 打开断路器
2. 基于 `service-feign` 工程进行改造，需要在 `IFeignService` 接口的注解中加上 `fallback` 的指定类

		@FeignClient(value = "eureka-client",fallback = FeignServiceImp.class)
		public interface IFeignService {
		    @RequestMapping(value = "/hi", method = RequestMethod.GET)
		    String sayHiFromClientOne(@RequestParam(value = "name") String name);
		}  

然后新建一个 `FeignServiceImp` 类实现 `IFeignService` ,并注入IoC容器 
    
		@Component
		public class FeignServiceImp implements IFeignService {
		
		    @Override
		    public String sayHiFromClientOne(String name) {
		        return "sorry " + name;
		    }
		}

3. 启动 `service-feign` 访问 `http://localhost:8765/hi?name=aaa` 返回 `sorry aaa` ,表明断路器起作用了

## 五.路由网关[https://blog.csdn.net/forezp/article/details/81041012](https://blog.csdn.net/forezp/article/details/81041012 "路由网关")
`zuul` 的主要功能是路由转发和过滤.路由功能是微服务的一部分,比如 `/api/user` 转发到到 `user` 服务, `/api/shop` 转发到到 `shop` 服务. `zuul` 默认和 `Ribbon` 结合实现了负载均衡的功能

1. 创建 `service-zuul` 模块,依赖

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-zuul'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}

2. 启动类加上 `@EnableZuulProxy`,  

		@SpringBootApplication
		@EnableZuulProxy
		@EnableEurekaClient
		@EnableDiscoveryClient
		public class ServiceZuulApplication {
		
		    public static void main(String[] args) {
		        SpringApplication.run( ServiceZuulApplication.class, args );
		    }
		}

 	配置 `yml` 文件

		server:
		  port: 8769
		spring:
		  application:
		    name: service-zuul
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8761/eureka/
		zuul:
		  routes:
		    api-a:
		      path: /api-a/**
		      serviceId: service-ribbon
		    api-b:
		      path: /api-b/**
		      serviceId: service-feign


	以 `/api-a/` 开头的请求都转发给 `service-ribbon` 服务；以 `/api-b/` 开头的请求都转发给 `service-feign` 服务

3.  浏览 `http://localhost:8769/api-a/hi?name=forezp` 和 `http://localhost:8769/api-b/hi?name=forezp` 都能跑通,说明 `zuul`起到路由作用了

### 服务过滤
`zuul`还能做一些安全验证

1. 新建 `MyFilter`继承 `ZuulFilter`

		@Component
		public class MyFilter extends ZuulFilter {
		
		    private static Logger log = LoggerFactory.getLogger(MyFilter.class);
		
		    @Override
		    public String filterType() {
		        return "pre";
		    }
		
		    @Override
		    public int filterOrder() {
		        return 0;
		    }
		
		    @Override
		    public boolean shouldFilter() {
		        return true;
		    }
		
		    @Override
		    public Object run() {
		        RequestContext ctx = RequestContext.getCurrentContext();
		        HttpServletRequest request = ctx.getRequest();
		        log.info(String.format("%s >>> %s", request.getMethod(), request.getRequestURL().toString()));
		        Object accessToken = request.getParameter("token");
		        if (accessToken == null) {
		            log.warn("token is empty");
		            ctx.setSendZuulResponse(false);
		            ctx.setResponseStatusCode(401);
		            try {
		                ctx.getResponse().getWriter().write("token is empty");
		            } catch (Exception e) {
		            }
		
		            return null;
		        }
		        log.info("ok");
		        return null;
		    }
		}
	* `filterType`  
	返回一个字符串代表过滤器的类型, `zuul` 中定义了四种类型,分别是

			pre:路由前
			routing:路由时
			post:路由后
			error:发送错误调用

	* `filterOrder`  
	过滤的顺序
	* `shouldFilter`  
	这里可设计逻辑,是否要过滤,例子为 `true` ,永源过滤
	* `run`
	过滤器的具体逻辑,可用复杂,包括能用 `sql`,`nosql`去判断

2. 访问 `http://localhost:8769/api-a/hi?name=forezp` 返回 `token is empty` ,访问 `http://localhost:8769/api-a/hi?name=forezp&token=22` 返回 `hi forezp,i am from port:8762`,说明过滤器有效

## 六.分布式配置中心[https://blog.csdn.net/forezp/article/details/81041028](https://blog.csdn.net/forezp/article/details/81041028 "分布式配置中心")
`Spring Cloud Config`  
 在分布式系统中,由于服务数量巨多,为了方便服务配置文件统一管理,实时更新,所以需要分布式配置中心组件.在`Spring Cloud`中,有分布式配置中心组件`spring cloud config` ,它支持配置服务放在配置服务的内存中(即本地),也支持放在远程Git仓库中.在`spring cloud config` 组件中,分两个角色,一是`config server`,二是`config client`

### config-server
1. 新建 `config-service` 模块,加入依赖 

		implementation 'org.springframework.boot:spring-boot-starter-web'
	    implementation 'org.springframework.cloud:spring-cloud-config-server'
	    testImplementation 'org.springframework.boot:spring-boot-starter-test'
2. 启动类上加注解 `@EnableConfigServer`,配置 `yml`
		
		server:
		  port: 8888
		spring:
		  application:
		    name: config-server
		  cloud:
		    config:
		      label: master
		      server:
		        git:
		          uri: https://github.com/crasy1/springcloudDemo/
		          searchPaths: respo
		          username:
		          password:

	`spring.cloud.config.server.git.uri`：配置git仓库地址
	`spring.cloud.config.server.git.searchPaths`：配置仓库路径
	`spring.cloud.config.label`：配置仓库的分支
	`spring.cloud.config.server.git.username`：访问git仓库的用户名
	`spring.cloud.config.server.git.password`：访问git仓库的用户密码  
	如果是公开的仓库可以不用写用户名和密码
	在仓库建议一个文件`config-client-dev.yml`

		foo: foo version 3

	启动程序：访问 `http://localhost:8888/foo/dev` 可以得到一些配置,证明配置服务中心可以从远程程序获取配置信息。

	http请求地址和资源文件映射如下:
	
	`/{application}/{profile}[/{label}]`  
	`/{application}-{profile}.yml`  
	`/{label}/{application}-{profile}.yml`  
	`/{application}-{profile}.properties`  
	`/{label}/{application}-{profile}.properties`


### config-client

1. 创建 `config-client`模块,引入依赖 

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-starter-config'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}
2. 配置文件 `bootstrap.yml`

		server.port: 8881
		spring.application.name: config-client
		spring:
		  cloud:
		    config:
		      label: master
		      profile: dev
		      uri: http://localhost:8888/
		
	`spring.cloud.config.label`   
		指明远程仓库的分支

	`spring.cloud.config.profile`
	* `dev` 开发环境配置文件
	* `test` 测试环境
	* `pro` 正式环境
	
	`spring.cloud.config.uri: http://localhost:8888/`  
	 指明配置服务中心的网址

3. 创建 `ConfigController` 

		@RestController
		public class ConfigController {
		
		    @Value("${foo}")
		    String foo;
		
		    @RequestMapping(value = "/hi")
		    public String hi() {
		        return foo;
		    }
		
		}
	`/hi` 返回从配置中心读取的 `foo` 变量的值
	打开 [http://localhost:8881/hi](http://localhost:8881/hi "配置") ,网页显示 `foo version 3` ,说明 `config-client` 从 `config-server` 获取了foo属性, `config-server` 的属性是从 `git` 仓库获取的
4. **注意避坑:**  
   * `config-client` 的`spring.application.name` 必须与`git`上的配置文件名字相同,例如 `spring.application.name: config-client` ,那么`git`上的配置文件名需要以 `config-client`开头,比如 `config-client-dev.yml`,`config-client-test.yml`,`config-client-pro.yml`,后面的属性表示开发环境
   * `config-client`加载的配置会覆盖`config-server` 的配置,比如读取到 `config-client-dev.yml`中配置的端口为`8882`,那么此时`config-client`读取配置文件的地址端口是 `8882` 而不是 `8881` 
   * 如果不是 `github` 比如 `gitlab` ,仓库 `url` 后面可能需要加`.git`的完整路径


## 七.高可用的分布式配置中心[https://blog.csdn.net/forezp/article/details/81041045](https://blog.csdn.net/forezp/article/details/81041045 "高可用分布式配置中心")
`Spring Cloud Config` 当服务实例很多时，都从配置中心读取文件，这时可以考虑将配置中心做成一个微服务，将其集群化，从而达到高可用，架构图如下
![配置集群化](https://i.imgur.com/47At5jS.png)

### 创建 `eureka-server` 工程
1. 创建一个 `eureka-service` 引入依赖

		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}
2. `application.yml` 为

		server:
		  port: 8889

		eureka:
		  instance:
		    hostname: localhost
		  client:
		    registerWithEureka: false
		    fetchRegistry: false
		    serviceUrl:
		      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

	在启动类加上注解 `@EnableEurekaServer`

### 改造`config-server`
1. 添加依赖

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-config-server'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		}

2. `application.yml`文件为

		server:
		  port: 8888
		spring:
		  application:
		    name: config-server
		  cloud:
		    config:
		      label: master
		      server:
		        git:
		          uri: https://github.com/crasy1/springcloudConfig/
		          searchPaths: respo
		          username:
		          password:
		
		eureka.client.serviceUrl.defaultZone: http://localhost:8889/eureka/
3. 启动类加上 `@EnableEurekaClient` 

### 改造`config-client`
1. 添加依赖

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-starter-config'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		}
2. `bootstrap.yml`文件

		#bootstrap.yml加载会先于application.yml
		server:
		  port: 8881
		spring:
		  application:
		    name: config-client
		  cloud:
		    config:
		      label: master
		      profile: dev
		#      uri: http://localhost:8888/
		      discovery:
		        enabled: true
		        serviceId: config-server
		
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8889/eureka/
		
	加上服务注册地址为`http://localhost:8889/eureka/`
	* spring.cloud.config.discovery.enabled:  
	 是从配置中心读取文件
	* spring.cloud.config.discovery.serviceId:  
	 配置中心的servieId，即服务名  

这时发现,在读取配置文件不再写ip地址,而是服务名,这时如果配置服务部署多份,通过负载均衡,从而高可用

3. 依次启动`eureka-servr`,`config-server`,`config-client`
访问网址：`http://localhost:8889/`,能看到注册中心.访问`http://localhost:8881/hi`显示`foo version 3`

## 八.消息总线[https://blog.csdn.net/forezp/article/details/81041062](https://blog.csdn.net/forezp/article/details/81041062 "消息总线")
`Spring Cloud Bus` 将分布式的节点用轻量的消息代理连接起来.它可以用于广播配置文件的更改或者服务之间的通讯,也可以用于监控.这里要讲述的是用`Spring Cloud Bus`实现通知微服务架构的配置文件的更改

1. 需要用到`rabbitmq`,所以需要先下载安装`rabbitmq`
2. 改造`config-client`,
		
		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    implementation 'org.springframework.cloud:spring-cloud-starter-config'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
			implementation 'org.springframework.boot:spring-boot-starter-actuator'
		    implementation 'org.springframework.cloud:spring-cloud-starter-bus-amqp'
		}
3. 在配置文件`application.yml`中加上`RabbitMq`的配置，包括`RabbitMq`的地址、端口，用户名、密码,并需要加上`spring.cloud.bus`的三个配置

		#bootstrap.yml加载会先于application.yml
		server:
		  port: 8881
		spring:
		  application:
		    name: config-client
		  cloud:
		    bus:
		      enabled: true
		      trace:
		        enabled: true
		    config:
		      label: master
		      profile: dev
		#      uri: http://localhost:8888/
		      discovery:
		        enabled: true
		        serviceId: config-server
		  rabbitmq:
		    host: localhost
		    port: 5672
		    username: admin
		    password: 123456
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8761/eureka/
		#      defaultZone: http://localhost:8889/eureka/
		
		management.endpoints.web.exposure.include: bus-refresh

4. 加上注解  
启动类 

		@EnableEurekaClient
		@EnableDiscoveryClient
		@SpringBootApplication
		public class ConfigClientApplication {
		
		    public static void main(String[] args) {
		        SpringApplication.run(ConfigClientApplication.class, args);
		    }
		
		}

`controller`

		@RefreshScope
		@RestController
		public class ConfigController {
		
		    @Value("${foo}")
		    String foo;
		
		    @RequestMapping(value = "/hi")
		    public String hi() {
		        return foo;
		    }
		
		}

5. 依次启动`eureka-server`、`confg-server`,启动两个`config-client`，端口为：`8881`、`8882`.访问[http://localhost:8881/actuator/bus-refresh](http://localhost:8881/actuator/bus-refresh) 或者[http://localhost:8882/hi](http://localhost:8882/hi) 会显示`foo version 3`.此时去`git`更改文件配置为`foo: foo version 5`,然后发送请求 [http://localhost:8881/actuator/bus-refresh](http://localhost:8881/actuator/bus-refresh) 发现`config-client`重新读取了文件,访问[http://localhost:8881/hi](http://localhost:8881/hi) 或者 [http://localhost:8882/hi](http://localhost:8882/hi) 返回的是`foo version 5`.另外,`/actuator/bus-refresh`接口可以指定服务,即使用`destination`参数,比如 `/actuator/bus-refresh?destination=customers:**` 即刷新服务名为`customers`的所有服务.
6. 当`git`文件更改的时候,通过`pc`端用`post` 向端口为`8882`的`config-client`发送请求[http://localhost:8882/actuator/bus-refresh](http://localhost:8882/actuator/bus-refresh) ,此时`8882`端口会发送一个消息，由消息总线向其他服务传递，从而使整个微服务集群都达到更新配置文件

## 九.服务链路追踪[https://blog.csdn.net/forezp/article/details/81041078](https://blog.csdn.net/forezp/article/details/81041078 "服务链")
服务追踪组件`zipkin`，`Spring Cloud Sleuth`集成了`zipkin`组件.  
### 服务追踪分析
微服务架构上通过业务来划分服务的,通过`REST`调用,对外暴露的一个接口,可能需要很多个服务协同才能完成这个接口功能,如果链路上任何一个服务出现问题或者网络超时,都会形成导致接口调用失败.随着业务的不断扩张,服务之间互相调用会越来越复杂.
### 术语
* `Span`：  
基本工作单元，例如，在一个新建的`span`中发送一个`RPC`等同于发送一个回应请求给`RPC`，`span`通过一个64位ID唯一标识，`trace`以另一个64位ID表示，`span`还有其他数据信息，比如摘要、时间戳事件、关键值注释(tags)、`span`的ID、以及进度ID(通常是IP地址)
`span`在不断的启动和停止，同时记录了时间信息，当你创建了一个`span`，你必须在未来的某个时刻停止它。
* `Trace`：  
一系列`spans`组成的一个树状结构，例如，如果你正在跑一个分布式大数据工程，你可能需要创建一个`trace`。
* `Annotation`：  
用来及时记录一个事件的存在，一些核心`annotations`用来定义一个请求的开始和结束
	* `cs` - `Client Sent`:  
	客户端发起一个请求，这个`annotion`描述了这个`span`的开始
	* `sr` - `Server Received`:  
	服务端获得请求并准备开始处理它，如果将其`sr`减去`cs`时间戳便可得到网络延迟
	* `ss` - `Server Sent`:  
	注解表明请求处理的完成(当请求返回客户端)，如果`ss`减去`sr`时间戳便可得到服务端需要的处理请求时间
	* `cr` - `Client Received`:  
	表明`span`的结束，客户端成功接收到服务端的回复，如果`cr`减去`cs`时间戳便可得到客户端从服务端获取回复的所有所需时间
	将`Span`和`Trace`在一个系统中使用`Zipkin`注解的过程图形化：
![](https://i.imgur.com/oUxOBYT.png)

### 构建工程

本文的案例主要有三个工程组成:一个`server-zipkin`,它的主要作用使用`ZipkinServer` 的功能，收集调用数据，并展示；一个`service-hi`,对外暴露`hi`接口；一个`service-miya`,对外暴露`miya`接口；这两个`service`可以相互调用；并且只有调用了，`server-zipkin`才会收集数据的，这就是为什么叫服务追踪了

1. 构建`service-zipkin`,下载地址 [https://dl.bintray.com/openzipkin/maven/io/zipkin/java/zipkin-server/](https://dl.bintray.com/openzipkin/maven/io/zipkin/java/zipkin-server/ "zipkin下载地址"),下载完之后运行`jar`包:`java -jar zipkin-server-2.10.1-exec.jar`,启动后可以打开[http://localhost:9411](http://localhost:9411)
2. 创建`service-hi`,加入依赖

		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-zipkin'
		    compileOnly 'org.projectlombok:lombok'
		    annotationProcessor 'org.projectlombok:lombok'
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}
3. `application.yml`指定`zipkin server`的地址，头通过配置`spring.zipkin.base-url`指定

		server.port: 8988
		spring:
		  zipkin.base-url: http://localhost:9411
		  application.name: service-hi

4. 新建`HiController`

		@Log4j2
		@RestController
		public class HiController {
		    @Autowired
		    private RestTemplate restTemplate;
		
		    @Bean
		    public RestTemplate getRestTemplate(){
		        return new RestTemplate();
		    }
		
		    @RequestMapping("/hi")
		    public String callHome(){
		        log.info("calling trace service-hi  ");
		        return restTemplate.getForObject("http://localhost:8989/miya", String.class);
		    }
		    @RequestMapping("/info")
		    public String info(){
		        log.info("calling trace service-hi ");
		        return "i'm service-hi";
		
		    }
		
		    @Bean
		    public Sampler defaultSampler() {
		        return Sampler.ALWAYS_SAMPLE;
		    }
		}
5. 创建`service-miya`引入相同依赖`application.yml`的`port`和`name`改一下,新建`MiyaController`

		@Log4j2
		@RestController
		public class MiyaController {
		    @Autowired
		    private RestTemplate restTemplate;
		
		    @Bean
		    public RestTemplate getRestTemplate() {
		        return new RestTemplate();
		    }
		
		    @Bean
		    public Sampler defaultSampler() {
		        return Sampler.ALWAYS_SAMPLE;
		    }
		
		    @RequestMapping("/hi")
		    public String home() {
		        log.info("hi is being called");
		        return "hi i'm miya!";
		    }
		
		    @RequestMapping("/miya")
		    public String info() {
		        log.info("info is being called");
		        return restTemplate.getForObject("http://localhost:8988/info", String.class);
		    }
		
		}

6. 启动服务访问[http://localhost:9411](http://localhost:9411),请求[http://localhost:8988/hi](http://localhost:8988/hi),然后查看![](https://i.imgur.com/h9n5G4g.png),![](https://i.imgur.com/qkpCTQA.png)

## 十.高可用的服务注册中心
`Eureka Server`,是一个实例，当成千上万个服务向它注册的时候,它的负载是非常高的,这在生产环境上是不太合适的,这篇文章主要介绍怎么将`Eureka Server`集群化

###改造 `eureka-service`
1. 在`eureka-service`模块新建配置文件`application-peer1.yml`

		server:
		  port: 8761
		
		spring:
		  profiles: peer1
		eureka:
		  instance:
		    hostname: peer1
		  client:
		    serviceUrl:
		      defaultZone: http://peer2:8769/eureka/

	`application-peer2.yml`

		server:
		  port: 8769
		
		spring:
		  profiles: 8769
		eureka:
		  instance:
		    hostname: 8769
		  client:
		    serviceUrl:
		      defaultZone: http://peer1:8761/eureka/
2. 更改`etc/hosts`,添加

		127.0.0.1 peer1
		127.0.0.1 peer2

3. 更改 `eureka-client`

		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://peer1:8761/eureka/
		server:
		  port: 8762
		spring:
		  application:
		    name: eureka-client
4. 复制`idea`上面`eureka-service`启动类,在其`vm`参数中加入`-Dspring.profiles.active=peer1`,另外一个加入`-Dspring.profiles.active=peer2`,然后分别启动.访问[http://localhost:8761](http://localhost:8761 "peer1")发现![](https://i.imgur.com/aA9JIJc.png),会有个`peer2`节点,同理访问[http://localhost:8769](http://localhost:8769 "peer2")会有个`peer1`节点,都有`client`的注册信息.`eureka.instance.preferIpAddress: true`是通过设置`ip`让`eureka`让其他服务注册它

### 填坑 [https://blog.csdn.net/forezp/article/details/81041101](https://blog.csdn.net/forezp/article/details/81041101 "原帖地址")
上面的方法其实不好,`hosts`不能随便改的,所以需要更合理的注册方式  

1. 要实现高可用的服务注册中心，可以让多台服务注册中心互相注册，比如有三个服务注册中心`Server1`、`Server2`、`Server3`，让`Server1`注册`Server2`和`Server3`，同理`Server2`、`Server3`分别注册另外两台服务注册中心，然后让`Client`注册到每个服务注册中心上，例如：

		Server1：
		eureka:
		  instance:
		    hostname: localhost
		  client:
		    registerWithEureka: false
		    fetchRegistry: false
		    serviceUrl:
		      defaultZone: http://${Server2IP}:${server2.port}/eureka/,http://${Server3IP}:${server3.port}/eureka/
		 
		Client：
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://Server1.ip:server1.port/eureka/,http://Server2.ip:server2.port/eureka/,http://Server3.ip:server3.port/eureka/
	
## 十一.docker部署spring cloud项目
[https://blog.csdn.net/forezp/article/details/70198649](https://blog.csdn.net/forezp/article/details/70198649)

		
## 十二.断路器监控
[https://blog.csdn.net/forezp/article/details/81041113](https://blog.csdn.net/forezp/article/details/81041113)
`Hystrix Dashboard`  
### `Hystrix Dashboard`简介
在微服务架构中为例保证程序的可用性，防止程序出错导致网络阻塞，出现了断路器模型.断路器的状况反应了一个程序的可用性和健壮性，它是一个重要指标.Hystrix Dashboard是作为断路器状态的一个组件，提供了数据监控和友好的图形化界面.

1. 改造`eureka-client`

		
		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		    implementation 'org.springframework.boot:spring-boot-starter-web'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		    implementation 'org.springframework.boot:spring-boot-starter-actuator'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-hystrix'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-hystrix-dashboard'
		}
 `application.yml`

		server:
		  port: 8762
		
		spring:
		  application:
		    name: eureka-client
		
		eureka:
		  client:
		    serviceUrl:
		#      defaultZone: http://peer1:8761/eureka/
		      defaultZone: http://localhost:8761/eureka/,http://localhost:8889/eureka/,http://localhost:8890/eureka/
		
		management:
		  endpoints:
		    web:
		      exposure:
		        include: "*"
		      cors:
		        allowed-origins: "*"
		        allowed-methods: "*"
	启动类,`@EnableHystrix`注解开启断路器，这个是必须的，并且需要在程序中声明断路点`@HystrixCommand`；加上`@EnableHystrixDashboard`注解，开启`HystrixDashboard`

		@EnableHystrix
		@EnableEurekaClient
		@SpringBootApplication
		@EnableDiscoveryClient
		@EnableHystrixDashboard
		@EnableCircuitBreaker
		public class EurekaClientApplication {
		
		    public static void main(String[] args) {
		        SpringApplication.run(EurekaClientApplication.class, args);
		    }
		
		}

	`controller`

		@RestController
		public class TestController {
		    @Value("${server.port}")
		    String port;
		
		    @HystrixCommand(fallbackMethod = "hiError")
		    @RequestMapping("/hi")
		    public String home(@RequestParam(value = "name", defaultValue = "forezp") String name) {
		        return "hi " + name + " ,i am from port:" + port;
		    }
		
		    public String hiError(String name){
		        return "error";
		    }
		}


	运行程序： 依次开启`eureka-server` 和`eureka-client`.

2. `Hystrix Dashboard`图形展示,打开[http://localhost:8762/actuator/hystrix.stream](http://localhost:8762/actuator/hystrix.stream) 会一直显示 `ping`,需要先访问 [http://localhost:8762/hi?name=forezp](http://localhost:8762/hi?name=forezp),再打开就会出现一些数据,访问[localhost:8762/hystrix](localhost:8762/hystrix)可以看到一个图形界面.在界面依次输入：`http://localhost:8762/actuator/hystrix.stream` 、`2000` 、`miya`；点确定会跳转到图形界面

## 十三.断路器聚合监控
`Hystrix Turbine`
[https://blog.csdn.net/forezp/article/details/70233227](https://blog.csdn.net/forezp/article/details/70233227)  
看单个的`Hystrix Dashboard`的数据并没有什么多大的价值，要想看这个系统的`Hystrix Dashboard`数据就需要用到`Hystrix Turbine`。`Hystrix Turbine`将每个服务`Hystrix Dashboard`数据进行了整合。`Hystrix Turbine`的使用非常简单，只需要引入相应的依赖和加上注解和配置就可以了

1. 新建`service-turbine`模块,依赖

		dependencies {
		    implementation 'org.springframework.boot:spring-boot-starter-actuator'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-turbine'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}
2. 启动类加`@EnableTurbine`，开启`turbine`  
3. `yml`

		spring:
		  application.name: service-turbine
		server:
		  port: 8769
		#security:
		#  basic:
		#    enabled: false
		turbine:
		  aggregator:
		    clusterConfig: default   # 指定聚合哪些集群，多个使用","分割，默认为default。可使用http://.../turbine.stream?cluster={clusterConfig之一}访问
		  appConfig: eureka-client,eureka-client2  # 配置Eureka中的serviceId列表，表明监控哪些服务
		  clusterNameExpression: new String("default")
		  # 1. clusterNameExpression指定集群名称，默认表达式appName；此时：turbine.aggregator.clusterConfig需要配置想要监控的应用名称
		  # 2. 当clusterNameExpression: default时，turbine.aggregator.clusterConfig可以不写，因为默认就是default
		  # 3. 当clusterNameExpression: metadata['cluster']时，假设想要监控的应用配置了eureka.instance.metadata-map.cluster: ABC，则需要配置，同时turbine.aggregator.clusterConfig: ABC
		eureka:
		  client:
		    serviceUrl:
		      defaultZone: http://localhost:8761/eureka/

4. 新建`service-client2`和`service-client`的配置差不多
5. `eureka-server`、`service-client`、`service-client2`、`service-turbine`,打开浏览器输入[http://localhost:8769/turbine.stream](http://localhost:8769/turbine.stream),请求[http://localhost:8762/hi?name=forezp](http://localhost:8762/hi?name=forezp)和[http://localhost:8759/hi?name=forezp](http://localhost:8759/hi?name=forezp),打开[http://localhost:8762/hystrix](http://localhost:8762/hystrix),输入监控流`http://localhost:8769/turbine.stream`点击`monitor stream`进入图形界面,发现页面聚合了2个`service`的`hystrix dashbord`数据。

## Spring Cloud Gateway初体验
官方文档[https://cloud.spring.io/spring-cloud-static/spring-cloud-gateway/2.0.0.RELEASE/single/spring-cloud-gateway.html](https://cloud.spring.io/spring-cloud-static/spring-cloud-gateway/2.0.0.RELEASE/single/spring-cloud-gateway.html "gateway官方文档")
Spring Cloud Gateway是Spring Cloud官方推出的第二代网关框架，取代Zuul网关。网关作为流量的，在微服务系统中有着非常作用，网关常见的功能有路由转发、权限校验、限流控制等作用
[https://blog.csdn.net/forezp/article/details/83792388](https://blog.csdn.net/forezp/article/details/83792388)  
官方案例 [https://github.com/spring-guides/gs-gateway](https://github.com/spring-guides/gs-gateway)

1. 创建`gateway-first-sight`模块,依赖

	dependencies {
	    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
	    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-hystrix'
	    compileOnly 'org.projectlombok:lombok'
	    annotationProcessor 'org.projectlombok:lombok'
	    testImplementation 'org.springframework.boot:spring-boot-starter-test'
	    testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'
	}
2. **创建一个简单的路由**  
	新建`GatewayController`

		@RestController
		public class GatewayController {
		    @Bean
		    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
		        return builder.routes()
		                .route(p -> p
		                        .path("/get")
		                        .filters(f -> f.addRequestHeader("Hello", "World"))
		                        .uri("http://httpbin.org:80"))
		                .build();
		    }
		}
3.在`spring cloud gateway`中使用`RouteLocator`的`Bean`进行路由转发，将请求进行处理，最后转发到目标的下游服务。在本案例中，会将请求转发到`http://httpbin.org:80`这个地址上.  
在上面的`myRoutes`方法中，使用了一个`RouteLocatorBuilder`的`bean`去创建路由，除了创建路由`RouteLocatorBuilder`可以让你添加各种`predicates`和`filters`，`predicates`断言的意思，顾名思义就是根据具体的请求的规则，由具体的`route`去处理，`filters`是各种过滤器，用来对请求做各种判断和修改.  
上面创建的`route`可以让请求`/get`请求都转发到`http://httpbin.org/get`.在route配置上，我们添加了一个`filter`，该`filter`会将请求添加一个`header`,`key`为`hello`，`value`为`world`.启动项目,访问[http://localhost:8080/get](http://localhost:8080/get)

		{
			args: { },
			headers: {
				Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3",
				Accept-Encoding: "gzip, deflate, br",
				Accept-Language: "zh-CN,zh;q=0.9",
				Cookie: "Idea-dd3bbdc1=7eefd86d-8ecd-4ba1-96df-78765851c0e8",
				Forwarded: "proto=http;host="localhost:8080";for="0:0:0:0:0:0:0:1:14279"",
				Hello: "World",
				Host: "httpbin.org",
				Upgrade-Insecure-Requests: "1",
				User-Agent: "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36",
				X-Forwarded-Host: "localhost:8080"
			},
			origin: "0:0:0:0:0:0:0:1, 125.123.239.14, ::1",
			url: "https://localhost:8080/get"
		}

	可见当我们向`gateway`工程请求`/get`,`gateway`会将工程的请求转发到`http://httpbin.org/get`，并且在转发之前，加上一个`filter`，该`filter`会将请求添加一个`header`,`key`为`hello`，`value`为`world`.

	注意`HTTPBin`展示了请求的`header` `hello`和值`world`
### 使用Hystrix
在`spring cloud gateway`中可以使用`Hystrix`,`Hystrix`是 `spring cloud`中一个服务熔断降级的组件，在微服务系统有着十分重要的作用.
`Hystrix`是 `spring cloud gateway`中是以`filter`的形式使用的

		@Bean
		    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
		        String httpUri = "http://httpbin.org:80";
		        return builder.routes()
		                .route(p -> p
		                        .path("/get")
		                        .filters(f -> f.addRequestHeader("Hello", "World"))
		                        .uri(httpUri))
		                .route(p -> p
		                        .host("*.hystrix.com")
		                        .filters(f -> f
		                                .hystrix(config -> config
		                                        .setName("mycmd")
		                                        .setFallbackUri("forward:/fallback")))
		                        .uri(httpUri))
		                .build();
		    }
		
		    @RequestMapping("/fallback")
		    public Mono<String> fallback() {
		        return Mono.just("fallback");
		    }

上面的代码中，我们使用了另外一个`router`，该`router`使用`host`去断言请求是否进入该路由，当请求的`host`有`*.hystrix.com`，都会进入该`router`，该`router`中有一个`hystrix`的`filter`,该`filter`可以配置名称、和指向性`fallback`的逻辑的地址，比如本案例中重定向到了`/fallback`

使用`curl`执行以下命令   

		curl --dump-header - --header Host: www.hystrix.com http://localhost:8080/delay/3
响应`fallback`

## Spring Cloud Gateway 之Predict篇
[https://blog.csdn.net/forezp/article/details/84926662](https://blog.csdn.net/forezp/article/details/84926662)
在之前的文章的`Spring Cloud Gateway`初体验中，对`Spring Cloud Gateway`的功能有一个初步的认识，网关作为一个系统的流量的入口，有着举足轻重的作用，通常的作用如下：

* 协议转换，路由转发
* 流量聚合，对流量进行监控，日志输出
* 作为整个系统的前端工程，对流量进行控制，有限流的作用
* 作为系统的前端边界，外部流量只能通过网关才能访问系统
* 可以在网关层做权限的判断
* 可以在网关层做缓存
`Spring Cloud Gateway`作为`Spring Cloud`框架的第二代网关，在功能上要比`Zuul`更加的强大，性能也更好。随着`Spring Cloud`的版本迭代，`Spring Cloud`官方有打算弃用`Zuul`的意思。在笔者调用了`Spring Cloud Gateway`的使用和功能上，`Spring Cloud Gateway`替换掉`Zuul`的成本上是非常低的，几乎可以无缝切换。`Spring Cloud Gateway`几乎包含了`zuul`的所有功能
### predicate简介
`Predicate`来自于`java8`的接口。`Predicate` 接受一个输入参数，返回一个布尔值结果。该接口包含多种默认方法来将`Predicate`组合成其他复杂的逻辑（比如：与，或，非）。可以用于接口请求参数校验、判断新老数据是否有变化需要进行更新操作。`add`–与、`or`–或、`negate`–非  
`Spring Cloud Gateway`内置了许多`Predict`,这些`Predict`的源码在`org.springframework.cloud.gateway.handler.predicate`包中
### predicate实战

		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}
### After Route Predicate Factory

`AfterRoutePredicateFactory`，可配置一个时间，当请求的时间在配置时间之后，才交给 `router`去处理。否则则报错，不通过路由。

在工程的`application.yml`配置如下

		server:
		  port: 8081
		spring:
		  profiles:
		    active: after_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: after_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - After=2017-01-20T17:42:47.789-07:00[America/Denver]
		  profiles: after_route



在上面的配置文件中，配置了服务的端口为`8081`，配置`spring.profiles.active:after_route`指定了程序的`spring`的启动文件为`after_route`文件。在`application.yml`再建一个配置文件，语法是三个横线，在此配置文件中通过`spring.profiles`来配置文件名，和`spring.profiles.active`一致，然后配置`spring cloud gateway` 相关的配置，`id`标签配置的是`router`的`id`，每个`router`都需要一个唯一的`id`，`uri`配置的是将请求路由到哪里，本案例全部路由到`http://httpbin.org:80/get`。

`predicates：
After=2017-01-20T17:42:47.789-07:00[America/Denver]` 会被解析成`PredicateDefinition`对象 `（name =After ，args= 2017-01-20T17:42:47.789-07:00[America/Denver]）`。在这里需要注意的是`predicates`的`After`这个配置，遵循的契约大于配置的思想，它实际被`AfterRoutePredicateFactory`这个类所处理，这个`After`就是指定了它的`Gateway web handler`类为`AfterRoutePredicateFactory`，同理，其他类型的`predicate`也遵循这个规则。

当请求的时间在这个配置的时间之后，请求会被路由到`http://httpbin.org:80/get`。

启动工程，在浏览器上访问[http://localhost:8081](http://localhost:8081)，会显示`http://httpbin.org:80/get`返回的结果，此时`gateway`路由到了配置的`uri`。如果我们将配置的时间设置到当前时之后，浏览器会显示`404`，此时证明没有路由到配置的`uri`

### Header Route Predicate Factory
`Header Route Predicate Factory`需要2个参数，一个是`header`名，另外一个`header`值，该值可以是一个正则表达式。当此断言匹配了请求的`header`名和值时，断言通过，进入到`router`的规则中去


		spring:
		  profiles:
		    active: header_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: header_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Header=X-Request-Id, \d+
		  profiles: header_route
		

在上面的配置中，当请求的`Header`中有`X-Request-Id`的`header`名，且`header`值为数字时，请求会被路由到配置的 `uri`. 

		curl -H 'X-Request-Id:1' localhost:8081
执行命令后，会正确的返回请求结果，结果省略。如果在请求中没有带上`X-Request-Id`的`header`名，并且值不为数字时，请求就会报`404`，路由没有被正确转发

### Cookie Route Predicate Factory
`Cookie Route Predicate Factory`需要2个参数，一个时`cookie`名字，另一个时值，可以为正则表达式。它用于匹配请求中，带有该名称的`cookie`和`cookie`匹配正则表达式的请求

		spring:
		  profiles:
		    active: cookie_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: cookie_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Cookie=name, forezp
		  profiles: cookie_route
		
在上面的配置中，请求带有`cookie`名为
`name`, `cookie`值为`forezp` 的请求将都会转发到uri为 `http://httpbin.org:80/get`的地址上。
使用`curl`命令进行请求，在请求中带上 `cookie`，会返回正确的结果，否则，请求报`404`错误

### Host Route Predicate Factory
`Host Route Predicate Factory`需要一个参数即`hostname`，它可以使用.` * `等去匹配`host`。这个参数会匹配请求头中的`host`的值，一致，则请求正确转发
		
		spring:
		  profiles:
		    active: host_route
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: host_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Host=**.fangzhipeng.com
		  profiles: host_route
在上面的配置中，请求头中含有`Host`为`fangzhipeng.com`的请求将会被路由转发转发到配置的`uri`。 启动工程，执行以下的`curl`命令，请求会返回正确的请求结果：

	curl -H Host:www.fangzhipeng.com localhost:8081

### Method Route Predicate Factory
`Method Route Predicate Factory` 需要一个参数，即请求的类型。比如`GET`类型的请求都转发到此路由。在工程的配置文件加上以下的配置


		spring:
		  profiles:
		    active: method_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: method_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Method=GET
		  profiles: method_route

在上面的配置中，所有的`GET`类型的请求都会路由转发到配置的`uri`

### Path Route Predicate Factory

`Path Route Predicate Factory` 需要一个参数: 一个`spel`表达式，应用匹配路径

		spring:
		  profiles:
		    active: path_route
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: path_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Path=/foo/{segment}
		  profiles: path_route
		
在上面的配置中，所有的请求路径满足`/foo/{segment}`的请求将会匹配并被路由，比如`/foo/1` 、`/foo/bar`的请求，将会命中匹配，并成功转发。

### Query Route Predicate Factory
`Query Route Predicate Factory` 需要2个参数:一个参数名和一个参数值的正则表达式


		spring:
		  profiles:
		    active: query_route
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: query_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Query=foo, bar
		  profiles: query_route
		


在上面的配置文件中，配置了请求中含有参数`foo`，并且`foo`的值匹配`bar`，则请求命中路由，比如一个请求中含有参数名为`foo`，值的为`bar`，能够被正确路由转发。

`Query Route Predicate Factory`也可以只填一个参数，填一个参数时，则只匹配参数名，即请求的参数中含有配置的参数名，则命中路由。比如以下的配置中，配置了请求参数中含有参数名为`foo` 的参数将会被请求转发到`uri`为`http://httpbin.org:80/get`

		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: query_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - Query=foo
		  profiles: query_route

## spring cloud gateway之filter篇
[https://blog.csdn.net/forezp/article/details/85057268](https://blog.csdn.net/forezp/article/details/85057268)
### filter的作用和生命周期
由`filter`工作流程点，可以知道`filter`有着非常重要的作用，在`pre`类型的过滤器可以做参数校验、权限校验、流量监控、日志输出、协议转换等，在`post`类型的过滤器中可以做响应内容、响应头的修改，日志的输出，流量监控等。首先需要弄清一点为什么需要网关这一层，这就不得不说下`filter`的作用了。

### 作用
当我们有很多个服务时，客户端请求各个服务的Api时，每个服务都需要做相同的事情，比如鉴权、限流、日志输出等。对于这样重复的工作，有没有办法做的更好，答案是肯定的。在微服务的上一层加一个全局的权限控制、限流、日志输出的`Api Gatewat`服务，然后再将请求转发到具体的业务服务层。这个`Api Gateway`服务就是起到一个服务边界的作用，外接的请求访问系统，必须先通过网关层。
### 生命周期
`Spring Cloud Gateway`同`zuul`类似，有`pre`和`post`两种方式的`filter`。客户端的请求先经过`pre`类型的`filter`，然后将请求转发到具体的业务服务，收到业务服务的响应之后，再经过`post`类型的`filter`处理，最后返回响应到客户端.  
与`zuul`不同的是，`filter`除了分为`pre`和`post`两种方式的`filter`外，在`Spring Cloud Gateway`中，`filter`从作用范围可分为另外两种，一种是针对于单个路由的`gateway filter`，它在配置文件中的写法同`predict`类似；另外一种是针对于所有路由的`global gateway filer`。现在从作用范围划分的维度来讲解这两种`filter`

### gateway filter
过滤器允许以某种方式修改传入的HTTP请求或传出的HTTP响应。过滤器可以限定作用在某些特定请求路径上。 `Spring Cloud Gateway`包含许多内置的`GatewayFilter`工厂。

`GatewayFilter`工厂同上一篇介绍的`Predicate`工厂类似，都是在配置文件`application.yml`中配置，遵循了约定大于配置的思想，只需要在配置文件配置`GatewayFilter Factory`的名称，而不需要写全部的类名，比如`AddRequestHeaderGatewayFilterFactory`只需要在配置文件中写`AddRequestHeader`，而不是全部类名。在配置文件中配置的`GatewayFilter Factory`最终都会相应的过滤器工厂类处理.  
每一个过滤器工厂在官方文档都给出了详细的使用案例，如果不清楚的还可以在`org.springframework.cloud.gateway.filter.factory`看每一个过滤器工厂的源码
###AddRequestHeader GatewayFilter Factory

		server:
		  port: 8081
		spring:
		  profiles:
		    active: add_request_header_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: add_request_header_route
		        uri: http://httpbin.org:80/get
		        filters:
		        - AddRequestHeader=X-Request-Foo, Bar
		        predicates:
		        - After=2017-01-20T17:42:47.789-07:00[America/Denver]
		  profiles: add_request_header_route


在上述的配置中，工程的启动端口为`8081`，配置文件为`add_request_header_route`，在`add_request_header_route`配置中，配置了`router`的`id`为`add_request_header_route`，路由地址为`http://httpbin.org:80/get`，该`router`有`AfterPredictFactory`，有一个`filter`为`AddRequestHeaderGatewayFilterFactory(约定写成AddRequestHeader)`，`AddRequestHeader`过滤器工厂会在请求头加上一对请求头，名称为`X-Request-Foo`，值为`Bar`

### RewritePath GatewayFilter Factory
在`Nginx`服务启中有一个非常强大的功能就是重写路径，`Spring Cloud Gateway`默认也提供了这样的功能，这个功能是`Zuul`没有的。在配置文件中加上以下的配置

		
		spring:
		  profiles:
		    active: rewritepath_route
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: rewritepath_route
		        uri: https://blog.csdn.net
		        predicates:
		        - Path=/foo/**
		        filters:
		        - RewritePath=/foo/(?<segment>.*), /$\{segment}
		  profiles: rewritepath_route

上面的配置中，所有的`/foo/**`开始的路径都会命中配置的`router`，并执行过滤器的逻辑，在本案例中配置了`RewritePath`过滤器工厂，此工厂将`/foo/(?.*)`重写为`{segment}`，然后转发到`https://blog.csdn.net`。比如在网页上请求[http://localhost:8081/foo/forezp](http://localhost:8081/foo/forezp)，此时会将请求转发到`https://blog.csdn.net/forezp`的页面，比如在网页上请求`localhost:8081/foo/forezp/1`，页面显示`404`，就是因为不存在`https://blog.csdn.net/forezp/1`这个页面。

###自定义过滤器
`Spring Cloud Gateway`内置了19种强大的过滤器工厂，能够满足很多场景的需求，那么能不能自定义自己的过滤器呢，当然是可以的。在`spring Cloud Gateway`中，过滤器需要实现`GatewayFilter`和`Ordered`2个接口。写一个`RequestTimeFilter`


		public class RequestTimeFilter implements GatewayFilter, Ordered {
		
		    private static final Log log = LogFactory.getLog(GatewayFilter.class);
		    private static final String REQUEST_TIME_BEGIN = "requestTimeBegin";
		
		    @Override
		    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		
		        exchange.getAttributes().put(REQUEST_TIME_BEGIN, System.currentTimeMillis());
		        return chain.filter(exchange).then(
		                Mono.fromRunnable(() -> {
		                    Long startTime = exchange.getAttribute(REQUEST_TIME_BEGIN);
		                    if (startTime != null) {
		                        log.info(exchange.getRequest().getURI().getRawPath() + ": " + (System.currentTimeMillis() - startTime) + "ms");
		                    }
		                })
		        );
		
		    }
		
		    @Override
		    public int getOrder() {
		        return 0;
		    }
		}
在上面的代码中，`Ordered`中的`int getOrder()`方法是来给过滤器设定优先级别的，值越大则优先级越低。还有有一个`filterI(exchange,chain)`方法，在该方法中，先记录了请求的开始时间，并保存在`ServerWebExchange`中，此处是一个`pre`类型的过滤器，然后再`chain.filter`的内部类中的`run()`方法中相当于`post`过滤器，在此处打印了请求所消耗的时间。然后将该过滤器注册到`router`中，代码如下

	    @Bean
	    public RouteLocator customerRouteLocator(RouteLocatorBuilder builder) {
	        // @formatter:off
	        return builder.routes()
	                .route(r -> r.path("/customer/**")
	                        .filters(f -> f.filter(new RequestTimeFilter())
	                                .addResponseHeader("X-Response-Default-Foo", "Default-Bar"))
	                        .uri("http://httpbin.org:80/get")
	                        .order(0)
	                        .id("customer_filter_router")
	                )
	                .build();
	        // @formatter:on
	    }
	
启动程序,访问

	 curl localhost:8081/customer/123

### 自定义过滤器工厂
创建一个自定义过滤工厂类,这样就能在配置文件中直接配置过滤器了  
过滤器工厂的顶级接口是`GatewayFilterFactory`，有2个两个较接近具体实现的抽象类，分别为`AbstractGatewayFilterFactory`和`AbstractNameValueGatewayFilterFactory`，这2个类前者接收一个参数，比如它的实现类`RedirectToGatewayFilterFactory`；后者接收2个参数，比如它的实现类`AddRequestHeaderGatewayFilterFactory`类。现在需要将请求的日志打印出来，需要使用一个参数，这时可以参照`RedirectToGatewayFilterFactory`的写法


		public class RequestTimeGatewayFilterFactory extends AbstractGatewayFilterFactory<RequestTimeGatewayFilterFactory.Config> {
		
		
		    private static final Log log = LogFactory.getLog(GatewayFilter.class);
		    private static final String REQUEST_TIME_BEGIN = "requestTimeBegin";
		    private static final String KEY = "withParams";
		
		    @Override
		    public List<String> shortcutFieldOrder() {
		        return Arrays.asList(KEY);
		    }
		
		    public RequestTimeGatewayFilterFactory() {
		        super(Config.class);
		    }
		
		    @Override
		    public GatewayFilter apply(Config config) {
		        return (exchange, chain) -> {
		            exchange.getAttributes().put(REQUEST_TIME_BEGIN, System.currentTimeMillis());
		            return chain.filter(exchange).then(
		                    Mono.fromRunnable(() -> {
		                        Long startTime = exchange.getAttribute(REQUEST_TIME_BEGIN);
		                        if (startTime != null) {
		                            StringBuilder sb = new StringBuilder(exchange.getRequest().getURI().getRawPath())
		                                    .append(": ")
		                                    .append(System.currentTimeMillis() - startTime)
		                                    .append("ms");
		                            if (config.isWithParams()) {
		                                sb.append(" params:").append(exchange.getRequest().getQueryParams());
		                            }
		                            log.info(sb.toString());
		                        }
		                    })
		            );
		        };
		    }
		
		
		    public static class Config {
		
		        private boolean withParams;
		
		        public boolean isWithParams() {
		            return withParams;
		        }
		
		        public void setWithParams(boolean withParams) {
		            this.withParams = withParams;
		        }
		
		    }
		}
		
在上面的代码中 `apply(Config config)`方法内创建了一个`GatewayFilter`的匿名类，具体的实现逻辑跟之前一样，只不过加了是否打印请求参数的逻辑，而这个逻辑的开关是`config.isWithParams()`。静态内部类类`Config`就是为了接收那个`boolean`类型的参数服务的，里边的变量名可以随意写，但是要重写`List shortcutFieldOrder()`这个方法。
。

需要注意的是，在类的构造器中一定要调用下父类的构造器把`Config`类型传过去，否则会报`ClassCastException`

最后，需要在工程的启动文件`Application`类中，向`Srping Ioc`容器注册`RequestTimeGatewayFilterFactory`类的`Bean`	


	    @Bean
	    public RequestTimeGatewayFilterFactory elapsedGatewayFilterFactory() {
	        return new RequestTimeGatewayFilterFactory();
	    }


`yml`配置

		spring:
		  profiles:
		    active: elapse_route
		
		---
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: elapse_route
		        uri: http://httpbin.org:80/get
		        filters:
		        - RequestTime=false
		        predicates:
		        - After=2017-01-20T17:42:47.789-07:00[America/Denver]
		  profiles: elapse_route


启动工程，在浏览器上访问[http://localhost:8081?name=forezp](http://localhost:8081?name=forezp)，可以在控制台上看到，日志输出了请求消耗的时间和请求参数。	

### global filter
`Spring Cloud Gateway`根据作用范围划分为`GatewayFilter`和`GlobalFilter`，二者区别如下：

* `GatewayFilter` : 需要通过`spring.cloud.routes.filters` 配置在具体路由下，只作用在当前路由上或通过`spring.cloud.default-filters`配置在全局，作用在所有路由上

* `GlobalFilter` : 全局过滤器，不需要在配置文件中配置，作用在所有的路由上，最终通过`GatewayFilterAdapter`包装成`GatewayFilterChain`可识别的过滤器，它为请求业务以及路由的`URI`转换为真实业务服务的请求地址的核心过滤器，不需要配置，系统初始化时加载，并作用在每个路由上,`springcloud`中内置的`globalFilter`如下

![](https://i.imgur.com/ilpelpc.png)

上图中每一个`GlobalFilter`都作用在每一个`router`上，能够满足大多数的需求。但是如果遇到业务上的定制，可能需要编写满足自己需求的`GlobalFilter`。在下面的案例中将讲述如何编写自己`GlobalFilter`，该`GlobalFilter`会校验请求中是否包含了请求参数`token`，如何不包含请求参数`token`则不转发路由，否则执行正常的逻辑

		public class TokenFilter implements GlobalFilter, Ordered {
		
		    Logger logger=LoggerFactory.getLogger( TokenFilter.class );
		    @Override
		    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		        String token = exchange.getRequest().getQueryParams().getFirst("token");
		        if (token == null || token.isEmpty()) {
		            logger.info( "token is empty..." );
		            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		            return exchange.getResponse().setComplete();
		        }
		        return chain.filter(exchange);
		    }
		
		    @Override
		    public int getOrder() {
		        return -100;
		    }
		}


在上面的`TokenFilter`需要实现`GlobalFilter`和`Ordered`接口，这和实现`GatewayFilter`很类似。然后根据`ServerWebExchange`获取`ServerHttpRequest`，然后根据`ServerHttpRequest`中是否含有参数`token`，如果没有则完成请求，终止转发，否则执行正常的逻辑。

然后需要将`TokenFilter`在工程的启动类中注入到`Spring Ioc`容器中
	
		
		@Bean
		public TokenFilter tokenFilter(){
		        return new TokenFilter();
		}

## spring cloud gateway 之限流篇
[https://blog.csdn.net/forezp/article/details/85081162](https://blog.csdn.net/forezp/article/details/85081162)
在高并发的系统中，往往需要在系统中做限流，一方面是为了防止大量的请求使服务器过载，导致服务不可用，另一方面是为了防止网络攻击。

常见的限流方式，比如`Hystrix`适用线程池隔离，超过线程池的负载，走熔断的逻辑。在一般应用服务器中，比如tomcat容器也是通过限制它的线程数来控制并发的；也有通过时间窗口的平均速度来控制流量。常见的限流纬度有比如通过`Ip`来限流、通过`uri`来限流、通过用户访问频次来限流。

一般限流都是在网关这一层做，比如`Nginx`、`Openresty`、`kong`、`zuul`、`Spring Cloud Gateway`等；也可以在应用层通过Aop这种方式去做限流。
### 令牌桶算法

在令牌桶算法中，存在一个桶，用来存放固定数量的令牌。算法中存在一种机制，以一定的速率往桶中放令牌。每次请求调用需要先获取令牌，只有拿到令牌，才有机会继续执行，否则选择选择等待可用的令牌、或者直接拒绝。放令牌这个动作是持续不断的进行，如果桶中令牌数达到上限，就丢弃令牌，所以就存在这种情况，桶中一直有大量的可用令牌，这时进来的请求就可以直接拿到令牌执行，比如设置qps为100，那么限流器初始化完成一秒后，桶中就已经有100个令牌了，这时服务还没完全启动好，等启动完成对外提供服务时，该限流器可以抵挡瞬时的100个请求。所以，只有桶中没有令牌时，请求才会进行等待，最后相当于以一定的速率执行。  
**实现思路**：可以准备一个队列，用来保存令牌，另外通过一个线程池定期生成令牌放到队列中，每来一个请求，就从队列中获取一个令牌，并继续执行

### Spring Cloud Gateway限流

在`Spring Cloud Gateway`中，有`Filter`过滤器，因此可以在`pre`类型的`Filter`中自行实现上述三种过滤器。但是限流作为网关最基本的功能，`Spring Cloud Gateway`官方就提供了`RequestRateLimiterGatewayFilterFactory`这个类，适用`Redis`和`lua`脚本实现了令牌桶的方式  
引入依赖

		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
		    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}

`yml`

		server:
		  port: 8081
		spring:
		  cloud:
		    gateway:
		      routes:
		      - id: limit_route
		        uri: http://httpbin.org:80/get
		        predicates:
		        - After=2017-01-20T17:42:47.789-07:00[America/Denver]
		        filters:
		        - name: RequestRateLimiter
		          args:
		            key-resolver: '#{@hostAddrKeyResolver}'
		            redis-rate-limiter.replenishRate: 1
		            redis-rate-limiter.burstCapacity: 3
		  application:
		    name: gateway-limiter
		  redis:
		    host: localhost
		    port: 6379
		    database: 0

在上面的配置文件，指定程序的端口为`8081`，配置了 `redis`的信息，并配置了`RequestRateLimiter`的限流过滤器，该过滤器需要配置三个参数：

* `burstCapacity`，令牌桶总容量。
* `replenishRate`，令牌桶每秒填充平均速率。
* `key-resolver`，用于限流的键的解析器的 `Bean` 对象的名字。它使用 `SpEL` 表达式根据`#{@beanName}`从 `Spring `容器中获取 `Bean `对象。  
`KeyResolver`需要实现`resolve`方法，比如根据`Hostname`进行限流，则需要用`hostAddress`去判断。实现完`KeyResolver`之后，需要将这个类的`Bean`注册到`Ioc`容器中


		public class HostAddrKeyResolver implements KeyResolver {
		
		    @Override
		    public Mono<String> resolve(ServerWebExchange exchange) {
		        return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
		    }
		
		}
		
		 @Bean
		    public HostAddrKeyResolver hostAddrKeyResolver() {
		        return new HostAddrKeyResolver();
		    }

可以根据`uri`去限流，这时`KeyResolver`代码如下：


		public class UriKeyResolver  implements KeyResolver {
		
		    @Override
		    public Mono<String> resolve(ServerWebExchange exchange) {
		        return Mono.just(exchange.getRequest().getURI().getPath());
		    }
		
		}
		
		 @Bean
		    public UriKeyResolver uriKeyResolver() {
		        return new UriKeyResolver();
		    }
		
		 
也可以以用户的维度去限流：
	
	
	   @Bean
	    KeyResolver userKeyResolver() {
	        return exchange -> Mono.just(exchange.getRequest().getQueryParams().getFirst("user"));
	    }



用`jmeter`进行压测，配置`10thread`去循环请求[http://lcoalhost:8081](http://lcoalhost:8081)，循环间隔1s。从压测的结果上看到有部分请求通过，由部分请求失败。通过`redis`客户端去查看`redis`中存在的`key`

## spring cloud gateway之服务注册与发现
[https://blog.csdn.net/forezp/article/details/85210153](https://blog.csdn.net/forezp/article/details/85210153)

### 工程介绍

		工程名	          端口	      作用
		eureka-server	8761	注册中心eureka server
		eureka-client   8762	服务提供者 eurka client
		gateway-service	8081	路由网关 eureka client

其中`eureka-client`、`gateway-service`向注册中心`eureka-server`注册。用户的请求首先经过`gateway-service`，根据路径由`gateway`的`predict` 去断言进到哪一个` router`， `router`经过各种过滤器处理后，最后路由到具体的业务服务，比如 `eureka-client`  

1. 使用之前的`eureka-server`,`eureka-client`模块
2. 新建`gateway-service`模块,引入依赖
		
		
		dependencies {
		    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
		    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
		    testImplementation 'org.springframework.boot:spring-boot-starter-test'
		}

`yml`

		server:
		  port: 8081
		
		spring:
		  application:
		    name: sc-gateway-service
		  cloud:
		    gateway:
		      discovery:
		        locator:
		          enabled: true
		          lowerCaseServiceId: true
		          
		eureka:
		  client:
		    service-url:
		      defaultZone: http://localhost:8761/eureka/
		
其中，`spring.cloud.gateway.discovery.locator.enabled`为`true`，表明`gateway`开启服务注册和发现的功能，并且`spring cloud gateway`自动根据服务发现为每一个服务创建了一个`router`，这个`router`将以服务名开头的请求路径转发到对应的服务。`spring.cloud.gateway.discovery.locator.lowerCaseServiceId`是将请求路径上的服务名配置为小写（因为服务注册的时候，向注册中心注册时将服务名转成大写的了），比如以`/eureka-client/*`的请求路径被路由转发到服务名为`eureka-client`的服务上,访问[http://localhost:8081/eureka-client/hi?name=1323](http://localhost:8081/eureka-client/hi?name=1323),可以访问.  
**自定义请求路径**  

		spring:
		  application:
		    name: sc-gateway-server
		  cloud:
		    gateway:
		      discovery:
		        locator:
		          enabled: false
		          lowerCaseServiceId: true
		      routes:
		      - id: service-hi
		        uri: lb://SERVICE-HI
		        predicates:
		          - Path=/demo/**
		        filters:
		          - StripPrefix=1
		         
在上面的配置中，配置了一个`Path` 的`predict`,将以`/demo/**`开头的请求都会转发到`uri`为`lb://EUREKA-CLIENT`的地址上，`lb://EUREKA-CLIENT`即`eureka-client`服务的负载均衡地址，并用`StripPrefix`的`filter` 在转发之前将`/demo`去掉。同时将`spring.cloud.gateway.discovery.locator.enabled`改为`false`，如果不改的话，之前的`localhost:8081/eureka-client/hi?name=1323`这样的请求地址也能正常访问，因为这时为每个服务创建了2个`router`。

在浏览器上请求[http://localhost:8081/demo/hi?name=1323](http://localhost:8081/demo/hi?name=1323)
