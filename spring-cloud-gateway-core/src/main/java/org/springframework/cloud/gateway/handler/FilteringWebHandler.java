/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link GatewayFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 */
public class FilteringWebHandler implements WebHandler {
	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);

	// 进行了转换。装饰罩模式
	private final List<GatewayFilter> globalFilters;

	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this.globalFilters = loadFilters(globalFilters);
	}

	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream()
				.map(filter -> {
					GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
					if (filter instanceof Ordered) {
						int order = ((Ordered) filter).getOrder();
						return new OrderedGatewayFilter(gatewayFilter, order);
					}
					return gatewayFilter;
				}).collect(Collectors.toList());
	}

    /* TODO: relocate @EventListener(RefreshRoutesEvent.class)
    void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }*/

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		List<GatewayFilter> gatewayFilters = route.getFilters();

		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(gatewayFilters);
		//TODO: needed or cached?
		// 也就是说 gatewayfilter 和global filter 其实是同level 的
		AnnotationAwareOrderComparator.sort(combined);

		if (logger.isDebugEnabled()) {
			logger.debug("Sorted gatewayFilterFactories: "+ combined);
		}

		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	/**
	 *  其实整个 gateway 的驱动核心 都来源于 这个 chain 过滤器层层调用。
	 */
	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;
		private final List<GatewayFilter> filters;

		public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		/**
		 * exchange 交换机层 封装了 req resp 语义 跟dubbo很像
		 * @param exchange the current server exchange
		 * @return
		 */
		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					GatewayFilter filter = filters.get(this.index);
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);

					// 返回的其实也是 Mono.defer 相当于递归式调用 wrapper.subscribe()
					return filter.filter(exchange, chain);
				} else {
					return Mono.empty(); // complete
				}
			});
		}
	}

	private static class GatewayFilterAdapter implements GatewayFilter {

		private final GlobalFilter delegate;

		public GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}
	}

}
