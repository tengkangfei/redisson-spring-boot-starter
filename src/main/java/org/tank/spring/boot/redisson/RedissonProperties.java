package org.tank.spring.boot.redisson;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by kfteng on 2020/12/11 14:51
 */
@ConfigurationProperties(prefix = "spring.redis.redisson")
public class RedissonProperties {

    private String config;

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

}