package org.tank.spring.boot.redisson;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by kfteng on 2020/12/11 14:51
 */
@ConfigurationProperties(prefix = "spring.redis.redisson")
public class RedissonProperties {

    private String config;

    private String ext;

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }
}