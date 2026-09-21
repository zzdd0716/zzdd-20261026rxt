package cn.lcj.rongxiaotong20260918.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 静态资源映射配置
 *
 * 作用：把 URL /images/** 映射到磁盘 uploads/ 目录
 * 上传一张图片 uploads/20260921/abc.jpg
 * → 浏览器访问 http://localhost:8088/images/20260921/abc.jpg 即可看到
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${application.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // URL 前缀 /images/** → 磁盘目录 file:${uploadPath}/
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
