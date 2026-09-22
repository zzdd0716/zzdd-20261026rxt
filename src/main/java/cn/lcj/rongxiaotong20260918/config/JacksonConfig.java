package cn.lcj.rongxiaotong20260918.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局时间格式配置
 *
 * 项目接口文档统一使用 "yyyy-MM-dd HH:mm:ss"（如 createTime、deadline、appointmentTime），
 * 而 Jackson 默认的 LocalDateTime 格式是 ISO-8601（"2026-10-01T12:00:00"，带 T）。
 * 这里统一改为带空格的格式，保证入参出参与文档一致。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(FMT))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(FMT));
    }
}
