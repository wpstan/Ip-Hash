### 前言
采用`spring-cloud-gateway`和`spring-cloud-loadbalancer`实现IP Hash路由。
`spring-cloud-gateway`与`zuul`一样提供网关的功能。`spring-cloud-loadbalancer`与`ribbon`一样提供负载均衡的策略，但是区别在于`ribbon`支持多种负载策略，而`spring-cloud-loadbalancer`目前仅支持`RoundRobin`负载均衡。
### 实现
1、在application.yml文件中定义配置，首先定义路由规则，请求路径带`/service/**`的url地址将被转发到`iphash://serviceA`这个uri。同时将`serviceA`对应的后端实例配置为`http://www.tanrd.com`。
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: serviceA
          uri: iphash://serviceA
          predicates:
            - Path=/service/**
    loadbalancer:
      health-check:
        path:
          serviceA: /
        initial-delay: 1000
        interval: 10000
    discovery:
      client:
        simple:
          instances:
            serviceA:
              - uri: http://www.tanrd.com

```
2、接着我们定义一个全局过滤器`IpHashLoadBalancerClientFilter`，用来处理`iphash://serviceA`的请求。这一步有一个关键步骤就是将从`ServerWebExchange`中提取到的IP地址传入`Request`对象中，作为元数据传给负载均衡器。`IpHashLoadBalancerClientFilter`过滤器的顺序可以设置为10105，保持与`ReactiveLoadBalancerClientFilter`一致即可。`ReactiveLoadBalancerClientFilter`是处理`lb://**`的过滤器。
```java
/**
 * @author Tan RD
 */
@Slf4j
public class IpHashLoadBalancerClientFilter implements GlobalFilter, Ordered {


    private static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150;

    private final LoadBalancerClientFactory clientFactory;

    private LoadBalancerProperties properties;

    public IpHashLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory,
                                          LoadBalancerProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return LOAD_BALANCER_CLIENT_FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
        if (url == null
                || (!"iphash".equals(url.getScheme()) && !"iphash".equals(schemePrefix))) {
            return chain.filter(exchange);
        }
        addOriginalRequestUrl(exchange, url);

        if (log.isTraceEnabled()) {
            log.trace(IpHashLoadBalancerClientFilter.class.getSimpleName()
                    + " url before: " + url);
        }

        return choose(exchange).doOnNext(response -> {

            if (!response.hasServer()) {
                throw NotFoundException.create(properties.isUse404(),
                        "Unable to find instance for " + url.getHost());
            }

            URI uri = exchange.getRequest().getURI();

            String overrideScheme = null;
            if (schemePrefix != null) {
                overrideScheme = url.getScheme();
            }

            DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(
                    response.getServer(), overrideScheme);

            URI requestUrl = reconstructURI(serviceInstance, uri);

            if (log.isTraceEnabled()) {
                log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
            }
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
        }).then(chain.filter(exchange));
    }

    protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
        return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
    }

    private Mono<Response<ServiceInstance>> choose(ServerWebExchange exchange) {
        URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        ReactorLoadBalancer<ServiceInstance> loadBalancer = this.clientFactory
                .getInstance(uri.getHost(), ReactorLoadBalancer.class,
                        ServiceInstance.class);
        if (loadBalancer == null) {
            throw new NotFoundException("No loadbalancer available for " + uri.getHost());
        }
        return loadBalancer.choose(createRequest(exchange));
    }

    private Request createRequest(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getLocalAddress().getAddress().getHostAddress();
        Request<String> request = new DefaultRequest<>(ip);
        return request;
    }

}
```
3、接下来就是实现负载均衡器`IpHashLoadBalancer`，从`Request`中提取IP，获取hashcode，然后对实例的数量求余。
```java
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
```
4、最后一步就是实现配置类，将`IpHashLoadBalancerClientFilter`和`IpHashLoadBalancer`进行配置。针对yml中的`serviceA`进行负载均衡配置。使用@LoadBalancerClient注解实现。
```java
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
```
```java
/**
 * @author Tan RD
 */
public class IpHashLoadBalancerClientConfiguration {
    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new IpHashLoadBalancer(loadBalancerClientFactory.getLazyProvider(name,
                ServiceInstanceListSupplier.class), name);
    }

    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .withHealthChecks()
                .build(context);
    }
}
```
5、点击右上角的Star。