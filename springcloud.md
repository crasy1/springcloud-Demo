#springcloud 学习
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
	


		
