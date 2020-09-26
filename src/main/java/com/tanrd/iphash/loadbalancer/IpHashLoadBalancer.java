package com.tanrd.iphash.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.reactive.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.reactive.Request;
import org.springframework.cloud.client.loadbalancer.reactive.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.List;


/**
 * IP Hash负载均衡器
 *
 * @author Tan RD
 */
@Slf4j
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
        if (serviceInstanceListSupplierProvider != null) {
            ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                    .getIfAvailable(NoopServiceInstanceListSupplier::new);
            return supplier.get().next().map((instances) -> getInstanceResponse(request, instances));
        }
        return null;
    }

    private Response<ServiceInstance> getInstanceResponse(
            Request request,
            List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        String ip = (String) request.getContext();
        int hash = Math.abs(ip.hashCode());
        int pos = hash % instances.size();
        ServiceInstance instance = instances.get(pos);
        return new DefaultResponse(instance);
    }
}
