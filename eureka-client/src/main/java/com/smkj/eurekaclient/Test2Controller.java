package com.smkj.eurekaclient;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author j
 * @Date 2019/5/30 17:24
 */

@RestController
public class Test2Controller {
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
