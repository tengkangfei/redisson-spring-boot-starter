package org.tank.spring.boot.redisson;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Created by kfteng on 2020/8/27 11:11:23
 */
@Configuration
@ConditionalOnClass({Redisson.class, RedisOperations.class})
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties({RedissonProperties.class, RedisProperties.class})
public class RedissonAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RedissonAutoConfiguration.class);

    @Autowired
    private RedissonProperties redissonProperties;

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private ApplicationContext ctx;

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        logger.info("Redisson初始化RedisTemplate完成");
        if(redisConnectionFactory instanceof JedisConnectionFactory){
            logger.info("初始化的是Jedis");
        } else{
            logger.info("初始化的不是Jedis");
        }
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        logger.info("Redisson初始化stringRedisTemplate完成");
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        logger.info("Redisson初始化RedissonConnectionFactory完成");
        return new RedissonConnectionFactory(redisson);
    }

    @SuppressWarnings("all")
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson() {
        Config config;
        Method clusterMethod = ReflectionUtils.findMethod(RedisProperties.class, "getCluster");
        Method timeoutMethod = ReflectionUtils.findMethod(RedisProperties.class, "getTimeout");
        assert timeoutMethod != null;
        Object timeoutValue = ReflectionUtils.invokeMethod(timeoutMethod, redisProperties);
        int timeout;
        if (null == timeoutValue) {
            timeout = 0;
        } else if (!(timeoutValue instanceof Integer)) {
            Method millisMethod = ReflectionUtils.findMethod(timeoutValue.getClass(), "toMillis");
            assert millisMethod != null;
            timeout = ((Long) Objects.requireNonNull(ReflectionUtils.invokeMethod(millisMethod, timeoutValue))).intValue();
        } else {
            timeout = (Integer) timeoutValue;
        }
        if (redisProperties == null) {
            logger.error("redisProerties is NULL ctx:{}", ctx.getApplicationName());
            return null;
        }

        if (redissonProperties.getConfig() != null && redissonProperties.getConfig().length() > 0) {
            try {
                InputStream is = getConfigStream();
                config = Config.fromJSON(is);
            } catch (IOException e) {
                // trying next format
                try {
                    InputStream is = getConfigStream();
                    config = Config.fromYAML(is);
                } catch (IOException e1) {
                    throw new IllegalArgumentException("Can't parse config", e1);
                }
            }
        } else if (redisProperties.getSentinel() != null) {
            Method nodesMethod = ReflectionUtils.findMethod(RedisProperties.Sentinel.class, "getNodes");
            assert nodesMethod != null;
            Object nodesValue = ReflectionUtils.invokeMethod(nodesMethod, redisProperties.getSentinel());

            String[] nodes;
            if (nodesValue instanceof String) {
                nodes = convert(Arrays.asList(((String) nodesValue).split(",")));
            } else {
                assert nodesValue != null;
                nodes = convert((List<String>) nodesValue);
            }

            config = new Config();
            config.useSentinelServers()
                    .setMasterName(redisProperties.getSentinel().getMaster())
                    .addSentinelAddress(nodes)
                    .setDatabase(redisProperties.getDatabase())
                    .setConnectTimeout(timeout)
                    .setPassword(redisProperties.getPassword());
        } else if (clusterMethod != null && ReflectionUtils.invokeMethod(clusterMethod, redisProperties) != null) {
            Object clusterObject = ReflectionUtils.invokeMethod(clusterMethod, redisProperties);
            Method nodesMethod = ReflectionUtils.findMethod(clusterObject.getClass(), "getNodes");
            List<String> nodesObject = (List) ReflectionUtils.invokeMethod(nodesMethod, clusterObject);

            String[] nodes = convert(nodesObject);

            config = new Config();
            config.useClusterServers()
                    .addNodeAddress(nodes)
                    .setConnectTimeout(timeout)
                    .setPassword(redisProperties.getPassword());
        } else {
            config = new Config();
            String prefix = "redis://";
            Method method = ReflectionUtils.findMethod(RedisProperties.class, "isSsl");
            if (method != null && (Boolean) ReflectionUtils.invokeMethod(method, redisProperties)) {
                logger.info("使用了rediss的方式接入redis");
                prefix = "rediss://";
            }

            config.useSingleServer()
                    .setAddress(prefix + redisProperties.getHost() + ":" + redisProperties.getPort())
                    .setConnectTimeout(timeout)
                    .setDatabase(redisProperties.getDatabase())
                    .setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }

    private String[] convert(List<String> nodesObject) {
        if (nodesObject == null) {
            return new String[0];
        }
        List<String> nodes = new ArrayList<>(nodesObject.size());
        for (String node : nodesObject) {
            if (!node.startsWith("redis://") && !node.startsWith("rediss://")) {
                nodes.add("redis://" + node);
            } else {
                nodes.add(node);
            }
        }
        if (nodes.size() == 0) {
            return new String[0];
        }
        return nodes.toArray(new String[0]);
    }

    private InputStream getConfigStream() throws IOException {
        Resource resource = ctx.getResource(redissonProperties.getConfig());
        return resource.getInputStream();
    }


}