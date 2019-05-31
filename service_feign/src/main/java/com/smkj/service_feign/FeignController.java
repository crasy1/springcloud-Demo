package com.smkj.service_feign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author j
 * @Date 2019/5/31 16:08
 */
@RestController
public class FeignController {

    @Autowired
    IFeignService iFeignService;

    @RequestMapping(value = "/hi", method = RequestMethod.GET)
    public String sayHi(@RequestParam String name) {
        return iFeignService.sayHiFromClientOne(name);
    }

}
