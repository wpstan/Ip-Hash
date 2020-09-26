package com.tanrd.iphash.loadbalancer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.Request;
import org.springframework.cloud.client.loadbalancer.reactive.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;


/**
 * IP Hash负载均衡器
 */

public class IpHashLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    public String serviceId;
    public ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public IpHashLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
            String serviceId) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return null;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose() {
        return null;
    }
}
