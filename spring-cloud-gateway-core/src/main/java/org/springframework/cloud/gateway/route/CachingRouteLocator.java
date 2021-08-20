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

package org.springframework.cloud.gateway.route;

import java.util.*;

import reactor.cache.CacheFlux;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class CachingRouteLocator implements RouteLocator {

	private final RouteLocator delegate;
	private final Flux<Route> routes;
	private final Map<String, List> cache = new HashMap<>();

	public static void main(String[] args) {
		Map<String, List> cache1 = new HashMap<>();
		CacheFlux.FluxCacheBuilderMapMiss<Date> routes =
				CacheFlux.lookup(cache1, "routes", Date.class);
		Flux<Date> routeFlux = routes.onCacheMissResume(() -> Flux.just(new Date()));
		routeFlux.subscribe();
	}
	public CachingRouteLocator(RouteLocator delegate) {
		/**
		 *  这里之所以做一个缓存 应该就是我之前 说的。如果每次 一个request 来都重新builder 就有问题
		 *  因为 router 在Flux 流试模型中都是要通过 Flux<RouteDefitnion> -> Flux<Router>的
		 *  所以这里做了缓存
		 *  下面就是个简单的缓存功能只是用 reactor stream 写了一遍。
		 *  see org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder#build()
		 *  */

		CacheFlux.FluxCacheBuilderMapMiss<Route> routes = CacheFlux.lookup(cache, "routes", Route.class);
		// 首先这里有一个问题 就是 启动会发布2次 刷新事件 这个应该是跟父子容器有关系。
		// 第二就是 他虽然启动 发布了事件将RouteDefinition => Route Flux<Route> 了。
		// 但是
		this.delegate = delegate;
		this.routes = CacheFlux.lookup(cache, "routes", Route.class)
				.onCacheMissResume(
						// 如果缓存未命中
						() -> {
							System.out.println("初始化routerDefinition => Route");// 每次getRouters 都会重新加载
							return this.delegate.getRoutes().sort(AnnotationAwareOrderComparator.INSTANCE);
						}
				);
	}

	@Override
	public Flux<Route> getRoutes() {
		System.out.println();
		return this.routes; // suscribe() 直接到 defer() 里面 走 () -> {} getRoutes() 完成初始化
	}

	/**
	 * Clears the routes cache
	 * @return routes flux
	 */
	public Flux<Route> refresh() {
		this.cache.clear();
		return this.routes;
	}

	@EventListener(RefreshRoutesEvent.class)
	/* for testing */ void handleRefresh() {
		refresh();
	}
}
