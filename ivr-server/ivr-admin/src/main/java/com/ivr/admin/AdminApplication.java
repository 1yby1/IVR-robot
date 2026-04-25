package com.ivr.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IVR 管理后台启动入口。
 *
 * <p>扫描全量 com.ivr 包，引入其他模块（engine / ai / call）的 Bean。
 */
@SpringBootApplication(scanBasePackages = "com.ivr")
@MapperScan(basePackages = "com.ivr.admin.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
