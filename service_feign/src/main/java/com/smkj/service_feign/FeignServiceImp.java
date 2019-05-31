package com.smkj.service_feign;

import org.springframework.stereotype.Component;

/**
 * @Author j
 * @Date 2019/5/31 16:46
 */
@Component
public class FeignServiceImp implements IFeignService {

    @Override
    public String sayHiFromClientOne(String name) {
        return "sorry " + name;
    }
}
