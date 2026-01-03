package com.wikicoding.springakkatemperaturesensorsdemo.appconfigs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfigs {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
