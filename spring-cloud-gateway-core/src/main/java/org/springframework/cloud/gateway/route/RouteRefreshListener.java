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

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.event.ParentHeartbeatEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.Assert;

// see ZuulDiscoveryRefreshListener
	// 该bean 主要用于 手动 发布一个 启动加载事件 直接将 router 缓存起来 避免第一次请求时routerDefinition 加载问题

	// 还有一种情况下也可以手动发布 就是 服务列表发生改变的时候 发布这个时间将缓存的 服务列表刷新
	// 但是还要remove() 掉cacheRouter
// 该 Lisitener 主要用于定时刷新 Route信息 从registerCenter 因为里面有  注册中心 看来这个是 pull模式 不是push
// TODO: make abstract class in commons?
public class RouteRefreshListener
		implements ApplicationListener<ApplicationEvent> {

	private HeartbeatMonitor monitor = new HeartbeatMonitor();
	private final ApplicationEventPublisher publisher;

	public RouteRefreshListener(ApplicationEventPublisher publisher) {
		Assert.notNull(publisher, "publisher may not be null");
		this.publisher = publisher;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent
				|| event instanceof RefreshScopeRefreshedEvent
				|| event instanceof InstanceRegisteredEvent) {
			reset();
		}
		else if (event instanceof ParentHeartbeatEvent) {
			ParentHeartbeatEvent e = (ParentHeartbeatEvent) event;
			resetIfNeeded(e.getValue());
		}
		else if (event instanceof HeartbeatEvent) {
			HeartbeatEvent e = (HeartbeatEvent) event;
			resetIfNeeded(e.getValue());
		}
	}

	private void resetIfNeeded(Object value) {
		if (this.monitor.update(value)) {
			reset();
		}
	}

	private void reset() {
		this.publisher.publishEvent(new RefreshRoutesEvent(this));
	}

}
