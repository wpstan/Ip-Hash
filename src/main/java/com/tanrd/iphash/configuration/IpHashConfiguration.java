package com.tanrd.iphash.configuration;

import com.tanrd.iphash.filter.IpHashLoadBalancerClientFilter;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Tan RD
 */
@Configuration
@LoadBalancerClient(value = "serviceA", configuration = IpHashLoadBalancerClientConfiguration.class)
public class IpHashConfiguration {

    @Bean
    public IpHashLoadBalancerClientFilter ipHashLoadBalancerClientFilter(
            LoadBalancerClientFactory clientFactory, LoadBalancerProperties properties) {
        return new IpHashLoadBalancerClientFilter(clientFactory, properties);
    }
}
