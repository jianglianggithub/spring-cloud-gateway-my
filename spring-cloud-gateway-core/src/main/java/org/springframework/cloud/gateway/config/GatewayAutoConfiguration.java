/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.config;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Consumer;

import com.netflix.hystrix.HystrixObservableCommand;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientOptions;
import reactor.ipc.netty.options.ClientProxyOptions;
import reactor.ipc.netty.resources.PoolResources;
import rx.RxReactiveStreams;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnEnabledEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.ForwardPathFilter;
import org.springframework.cloud.gateway.filter.ForwardRoutingFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.route.CachingRouteLocator;
import org.springframework.cloud.gateway.route.CompositeRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.CompositeRouteLocator;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.RouteRefreshListener;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.filter.reactive.HiddenHttpMethodFilter;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.DISABLED;
import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.FIXED;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@EnableConfigurationProperties
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter({ GatewayLoadBalancerClientAutoConfiguration.class,
		GatewayClassPathWarningAutoConfiguration.class })
@ConditionalOnClass(DispatcherHandler.class)
public class GatewayAutoConfiguration {

	// ????????? ???????????? ???????????? ??? res ????????? client???
	// ?????? spring cloud gateway ????????????????????????????????? ??????????????????????????????
	// ???????????? ?????? netty  ??? reactor stream ?????????
	@Configuration
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public HttpClient httpClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return HttpClient.create(options);
		}


		/**
		 *  ???????????? ???????????? ?????? ??????????????? ??????channle options new ServerBootStrap option() ??????
		 *  ???????????????????????????
		 * @param properties
		 * @return
		 */
		@Bean
		public Consumer<? super HttpClientOptions.Builder> nettyClientOptions(HttpClientProperties properties) {
			return opts -> {

				if (properties.getConnectTimeout() != null) {
					opts.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout());
				}

				// configure ssl
				HttpClientProperties.Ssl ssl = properties.getSsl();
				opts.sslHandshakeTimeoutMillis(ssl.getHandshakeTimeoutMillis());
				opts.sslCloseNotifyFlushTimeoutMillis(ssl.getCloseNotifyFlushTimeoutMillis());
				opts.sslCloseNotifyReadTimeoutMillis(ssl.getCloseNotifyReadTimeoutMillis());
				X509Certificate[] trustedX509Certificates = ssl
						.getTrustedX509CertificatesForTrustManager();
				if (trustedX509Certificates.length > 0) {
					opts.sslSupport(sslContextBuilder -> {
						sslContextBuilder.trustManager(trustedX509Certificates);
					});
				}
				else if (ssl.isUseInsecureTrustManager()) {
					opts.sslSupport(sslContextBuilder -> {
						sslContextBuilder
								.trustManager(InsecureTrustManagerFactory.INSTANCE);
					});
				}

				// configure pool resources
				HttpClientProperties.Pool pool = properties.getPool();

				if (pool.getType() == DISABLED) {
					opts.disablePool();
				} else if (pool.getType() == FIXED) {
					PoolResources poolResources = PoolResources.fixed(pool.getName(),
							pool.getMaxConnections(), pool.getAcquireTimeout());
					opts.poolResources(poolResources);
				} else {
					PoolResources poolResources = PoolResources.elastic(pool.getName());
					opts.poolResources(poolResources);
				}


				// configure proxy if proxy host is set.
				HttpClientProperties.Proxy proxy = properties.getProxy();
				if (StringUtils.hasText(proxy.getHost())) {
					opts.proxy(typeSpec -> {
						ClientProxyOptions.Builder builder = typeSpec
								.type(ClientProxyOptions.Proxy.HTTP)
								.host(proxy.getHost());

						PropertyMapper map = PropertyMapper.get();

						map.from(proxy::getPort)
								.whenNonNull()
								.to(builder::port);
						map.from(proxy::getUsername)
								.whenHasText()
								.to(builder::username);
						map.from(proxy::getPassword)
								.whenHasText()
								.to(password -> builder.password(s -> password));
						map.from(proxy::getNonProxyHostsPattern)
								.whenHasText()
								.to(builder::nonProxyHosts);

						return builder;
					});
				}
			};
		}

		@Bean
		public HttpClientProperties httpClientProperties() {
			return new HttpClientProperties();
		}

		@Bean
		public NettyRoutingFilter routingFilter(HttpClient httpClient,
												ObjectProvider<List<HttpHeadersFilter>> headersFilters,
												HttpClientProperties properties) {
			return new NettyRoutingFilter(httpClient, headersFilters, properties);
		}

		@Bean
		public NettyWriteResponseFilter nettyWriteResponseFilter(GatewayProperties properties) {
			return new NettyWriteResponseFilter(properties.getStreamingMediaTypes());
		}

		@Bean
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return new ReactorNettyWebSocketClient(options);
		}
	}

	//TODO: remove when not needed anymore
	// either https://jira.spring.io/browse/SPR-17291 or
	// https://github.com/spring-projects/spring-boot/issues/14520 needs to be fixed
	@Bean
	public HiddenHttpMethodFilter hiddenHttpMethodFilter() {
		return new HiddenHttpMethodFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
				return chain.filter(exchange);
			}
		};
	}



	/**  ?????????Router ?????????Builder ????????????????????? */
	@Bean
	public RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) {
		return new RouteLocatorBuilder(context);
	}

	// ???????????????????????? ????????????routeDefinition
	@Bean
	@ConditionalOnMissingBean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	// ??????????????? ??????????????????????????????new?????? ?????????????????????????????????????????????
	@Bean
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}
	/**  routeDefinition ??????????????????  */


	// ???????????? ??????RouteDefinition?????????  ????????????Router
	// ??????????????? ????????? ????????? ???????????????????????? RouterDefnition
	// ??????????????????  Properties ???????????? ??? ????????????????????? DiscoveryClientRouteDefinitionLocator
	// ????????????????????????????????? ?????????????????????????????????????????? ???????????????
	@Bean
	@Primary
	public RouteDefinitionLocator routeDefinitionLocator(List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(Flux.fromIterable(routeDefinitionLocators));
	}




	/**
	 *  ???bean ?????? ??????????????????????????? RouteDefinition -> Route ????????? bean
	 */
	@Bean
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
												   List<GatewayFilterFactory> GatewayFilters,
												   List<RoutePredicateFactory> predicates,
												   RouteDefinitionLocator routeDefinitionLocator) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates, GatewayFilters, properties);
	}

	/**
	 *  ???bean??? ?????? ??????????????????Route ??? yml????????????route ??????????????????
	 */
	@Bean
	@Primary
	//TODO: property to disable composite?
	public RouteLocator cachedCompositeRouteLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	@Bean
	public RouteRefreshListener routeRefreshListener(ApplicationEventPublisher publisher) {
		return new RouteRefreshListener(publisher);
	}

	/**
	 *  ??????bean ????????? ??????????????????????????? boot bean
	 * @param globalFilters
	 * @return
	 */
	@Bean
	public FilteringWebHandler filteringWebHandler(List<GlobalFilter> globalFilters) {
		return new FilteringWebHandler(globalFilters);
	}

	@Bean
	public GlobalCorsProperties globalCorsProperties() {
		return new GlobalCorsProperties();
	}

	/**
	 *  ????????? ????????? ???????????? wrapper ???bean
	 * @param webHandler
	 * @param routeLocator  ?????????  ????????? /path  handle?????? ???????????????
	 * @param globalCorsProperties
	 * @param environment
	 * @return
	 */
	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(
						FilteringWebHandler webHandler,
						RouteLocator routeLocator,
						GlobalCorsProperties globalCorsProperties,
						Environment environment
	) {


		return new RoutePredicateHandlerMapping(webHandler, routeLocator,
				globalCorsProperties, environment);
	}

	// ConfigurationProperty beans

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	// HttpHeaderFilter beans

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.forwarded.enabled", matchIfMissing = true)
	public ForwardedHeadersFilter forwardedHeadersFilter() {
		return new ForwardedHeadersFilter();
	}

	@Bean
	public RemoveHopByHopHeadersFilter removeHopByHopHeadersFilter() {
		return new RemoveHopByHopHeadersFilter();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.gateway.x-forwarded.enabled", matchIfMissing = true)
	public XForwardedHeadersFilter xForwardedHeadersFilter() {
		return new XForwardedHeadersFilter();
	}

	// GlobalFilter beans  ??????????????????????????????
	
	@Bean
	public AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter() {
		return new AdaptCachedBodyGlobalFilter();
	}

	@Bean
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	@ConditionalOnBean(DispatcherHandler.class)
	public ForwardRoutingFilter forwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new ForwardRoutingFilter(dispatcherHandler);
	}

	@Bean
	public ForwardPathFilter forwardPathFilter() {
		return new ForwardPathFilter();
	}

	@Bean
	public WebSocketService webSocketService() {
		return new HandshakeWebSocketService();
	}

	@Bean
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient,
														 WebSocketService webSocketService,
														 ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService, headersFilters);
	}

	// =========================================================================
	// ???????????? reset  ??????????????? ?????? subcribe ???RouteDefinition => Route
	@Bean
	public WeightCalculatorWebFilter weightCalculatorWebFilter(Validator validator, ObjectProvider<RouteLocator> routeLocator) {
		return new WeightCalculatorWebFilter(validator, routeLocator);
	}

	/**
	  ??????2???bean ???????????? NettyClientWriteResponseFilter ??? NettyRoutingFilter ???2???bean  ???????????????????????????
	   ??????webClinet ??? ????????????????????????

	 * */

	/*@Bean
	//TODO: default over netty? configurable
	public WebClientHttpRoutingFilter webClientHttpRoutingFilter() {
		//TODO: WebClient bean
		return new WebClientHttpRoutingFilter(WebClient.routes().build());
	}

	@Bean
	public WebClientWriteResponseFilter webClientWriteResponseFilter() {
		return new WebClientWriteResponseFilter();
	}*/

	// Predicate Factory beans  ??????????????? ???yml??????????????????  Predicate ?????? ????????????Path ??????????????????????????????
	// ?????? ??????????????????????????? ???????????? ????????????cookie =xxx ????????? ??????????????????????????????
	// ?????????????????????????????????
	@Bean
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	@Bean
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	@Bean
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	@Bean
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	@Bean
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	@Bean
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	@Bean
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	// ????????????????????????
	@Bean
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	@Bean
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	@Bean
	public ReadBodyPredicateFactory readBodyPredicateFactory() {
		return new ReadBodyPredicateFactory();
	}

	@Bean
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}


	// ====================================================================================================

	@Bean
	@DependsOn("weightCalculatorWebFilter")
	public WeightRoutePredicateFactory weightRoutePredicateFactory() {
		return new WeightRoutePredicateFactory();
	}

	@Bean
	public CloudFoundryRouteServiceRoutePredicateFactory cloudFoundryRouteServiceRoutePredicateFactory() {
		return new CloudFoundryRouteServiceRoutePredicateFactory();
	}

	// GatewayFilter Factory beans
	// ???yml routers ????????? Filter Field ????????? ???????????????
	// ??????????????? ???????????? ?????????????????? ?????? router -> filter ??????

	@Bean
	public AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
		return new AddRequestHeaderGatewayFilterFactory();
	}

	@Bean
	public AddRequestParameterGatewayFilterFactory addRequestParameterGatewayFilterFactory() {
		return new AddRequestParameterGatewayFilterFactory();
	}

	@Bean
	public AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
		return new AddResponseHeaderGatewayFilterFactory();
	}

	@Configuration
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	protected static class HystrixConfiguration {
		@Bean
		public HystrixGatewayFilterFactory hystrixGatewayFilterFactory(ObjectProvider<DispatcherHandler> dispatcherHandler) {
			return new HystrixGatewayFilterFactory(dispatcherHandler);
		}
	}

	@Bean
	public DedupeResponseHeaderGatewayFilterFactory dedupeResponseHeaderGatewayFilterFactory() {
		return new DedupeResponseHeaderGatewayFilterFactory();
	}

	@Bean
	public ModifyRequestBodyGatewayFilterFactory modifyRequestBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		return new ModifyRequestBodyGatewayFilterFactory(codecConfigurer);
	}

	@Bean
	public ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		return new ModifyResponseBodyGatewayFilterFactory(codecConfigurer);
	}

	@Bean
	public PrefixPathGatewayFilterFactory prefixPathGatewayFilterFactory() {
		return new PrefixPathGatewayFilterFactory();
	}

	@Bean
	public PreserveHostHeaderGatewayFilterFactory preserveHostHeaderGatewayFilterFactory() {
		return new PreserveHostHeaderGatewayFilterFactory();
	}

	@Bean
	public RedirectToGatewayFilterFactory redirectToGatewayFilterFactory() {
		return new RedirectToGatewayFilterFactory();
	}

	@Bean
	public RemoveRequestHeaderGatewayFilterFactory removeRequestHeaderGatewayFilterFactory() {
		return new RemoveRequestHeaderGatewayFilterFactory();
	}

	@Bean
	public RemoveResponseHeaderGatewayFilterFactory removeResponseHeaderGatewayFilterFactory() {
		return new RemoveResponseHeaderGatewayFilterFactory();
	}


	//======================================================================================

	@Bean(name = PrincipalNameKeyResolver.BEAN_NAME)
	@ConditionalOnBean(RateLimiter.class)
	@ConditionalOnMissingBean(KeyResolver.class)
	public PrincipalNameKeyResolver principalNameKeyResolver() {
		return new PrincipalNameKeyResolver();
	}


	// ?????????????????? ??????????????? globalFilter ????????? ??????????????????
	// ???gatewayFilter  ????????????????????????????????? ???request ?????? ?????? ???????????????filter


	/*  ??????????????????????????????????????? ???????????????????????? ??????????????? */

	@Bean
	@ConditionalOnBean({RateLimiter.class, KeyResolver.class})
	public RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory(RateLimiter rateLimiter, KeyResolver resolver) {
		return new RequestRateLimiterGatewayFilterFactory(rateLimiter, resolver);
	}

	@Bean
	public RewritePathGatewayFilterFactory rewritePathGatewayFilterFactory() {
		return new RewritePathGatewayFilterFactory();
	}

	@Bean
	public RetryGatewayFilterFactory retryGatewayFilterFactory() {
		return new RetryGatewayFilterFactory();
	}

	@Bean
	public SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
		return new SetPathGatewayFilterFactory();
	}

	@Bean
	public SecureHeadersGatewayFilterFactory secureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties);
	}

	@Bean
	public SetRequestHeaderGatewayFilterFactory setRequestHeaderGatewayFilterFactory() {
		return new SetRequestHeaderGatewayFilterFactory();
	}

	@Bean
	public SetResponseHeaderGatewayFilterFactory setResponseHeaderGatewayFilterFactory() {
		return new SetResponseHeaderGatewayFilterFactory();
	}

	@Bean
	public RewriteResponseHeaderGatewayFilterFactory rewriteResponseHeaderGatewayFilterFactory() {
		return new RewriteResponseHeaderGatewayFilterFactory();
	}

	@Bean
	public SetStatusGatewayFilterFactory setStatusGatewayFilterFactory() {
		return new SetStatusGatewayFilterFactory();
	}

	@Bean
	public SaveSessionGatewayFilterFactory saveSessionGatewayFilterFactory() {
		return new SaveSessionGatewayFilterFactory();
	}

	@Bean
	public StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
		return new StripPrefixGatewayFilterFactory();
	}

	@Bean
	public RequestHeaderToRequestUriGatewayFilterFactory requestHeaderToRequestUriGatewayFilterFactory() {
		return new RequestHeaderToRequestUriGatewayFilterFactory();
	}


	/**
	 *  ??????bean?????? ??????????????? ??????gateway ??????????????? ??????????????????http??????

	 *  ??????????????? globalFilter gatewayFilter[request?????????-condition] ????????????
	 *
	 *  ??????????????? ????????????????????? http????????????????????????router????????????
	 */
	@Configuration
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		@ConditionalOnEnabledEndpoint
		public GatewayControllerEndpoint gatewayControllerEndpoint(RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
																List<GatewayFilterFactory> GatewayFilters, RouteDefinitionWriter routeDefinitionWriter,
																RouteLocator routeLocator) {
			return new GatewayControllerEndpoint(routeDefinitionLocator, globalFilters, GatewayFilters, routeDefinitionWriter, routeLocator);
		}
	}

}

