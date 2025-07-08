package com.hmdp.config;

import com.hmdp.interceptor.JWTInterceptor;
import com.hmdp.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebMvcConfig.class);

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private JWTInterceptor jwtInterceptor;

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        logger.info("Registering AuthInterceptor.");
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                )
                .order(1);
        // 可以添加更多的拦截器
    }

    /**
     * 配置静态资源处理 (可选，Spring Boot有默认配置)
     * 如果你的静态资源放在 'src/main/resources/static' 或 'src/main/resources/public' 等，
     * Spring Boot 会自动处理。这里只是展示如何自定义。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 例如，将 /static/** 请求映射到 classpath:/custom-static/ 目录下
        // registry.addResourceHandler("/static/**")
        //         .addResourceLocations("classpath:/custom-static/");
        // Swagger UI 静态资源 (如果手动集成或版本较旧)
        // registry.addResourceHandler("swagger-ui.html")
        //        .addResourceLocations("classpath:/META-INF/resources/");
        // registry.addResourceHandler("/webjars/**")
        //        .addResourceLocations("classpath:/META-INF/resources/webjars/");
        logger.debug("Custom resource handlers configured (if any).");
    }

    /**
     * 配置跨域支持 (CORS) (可选)
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        logger.info("Configuring CORS settings.");
        registry.addMapping("/**") // 对所有路径生效
                .allowedOriginPatterns("*") // 允许所有来源 (生产环境应配置具体域名, e.g., "https://yourdomain.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的方法
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true) // 是否允许发送Cookie
                .maxAge(3600); // 预检请求的有效期，单位秒d
    }

    // 其他 WebMvcConfigurer 方法可以按需覆盖，例如：
    // configureMessageConverters, addFormatters, configureViewResolvers 等
}

