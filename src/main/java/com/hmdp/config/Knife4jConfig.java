package com.hmdp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("点评项目 API 文档")
                        .version("1.0")
                        .description("这是一个点评项目的API接口文档，方便前后端开发人员进行接口调试和查看。")
                        .termsOfService("http://doc.xiaominfo.com")
                        // .license(new License().name("Apache 2.0").url("http://doc.xiaominfo.com"))
                );
    }
}