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
 */

package org.springframework.cloud.gateway.route.builder;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
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
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * Predicates that can be applies to a URI route.
 *
 *
 * 这里有很多操作符 比如path readBody 等等
 * 相当于 在 application.yml 中配置的
 *
 * PredicateBuilder 可能会有多个 但是 会合成一个
 */
public class PredicateSpec extends UriSpec {

	PredicateSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	public PredicateSpec order(int order) {
		this.routeBuilder.order(order);
		return this;
	}

	public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
		return asyncPredicate(toAsyncPredicate(predicate));
	}

	/**
	 * ====第一次看源码解释===
	 * 如果当前是 非BooleanSpec 的话代表 已经构建了 Predicate信息也就是路由匹配信息
	 * 可以通过这个function 来自己实现 route 匹配策略。照着cv就行
	 *
	 *  ====二次看源码解释===
	 * 当之前使用过 and or 这种操作符后 asyncPredicate 会调用BooleanOpSpec 的 操作符将 之前的 AsyncBuilder 形成 级联
	 */
	public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
		this.routeBuilder.asyncPredicate(predicate);
		return new BooleanSpec(this.routeBuilder, this.builder);
	}

	protected GatewayFilterSpec createGatewayFilterSpec() {
		return new GatewayFilterSpec(this.routeBuilder, this.builder);
	}

	/**
	 * A predicate to check if a request was made after a specific {@link ZonedDateTime}
	 * @param datetime requests would only be routed after this {@link ZonedDateTime}
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec after(ZonedDateTime datetime) {
		return asyncPredicate(getBean(AfterRoutePredicateFactory.class)
				.applyAsync(c-> c.setDatetime(datetime.toString())));
	}

	/**
	 * A predicate to check if a request was made before a specific {@link ZonedDateTime}
	 * @param datetime requests will only be routed before this {@link ZonedDateTime}
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec before(ZonedDateTime datetime) {
		return asyncPredicate(getBean(BeforeRoutePredicateFactory.class).applyAsync(c -> c.setDatetime(datetime.toString())));
	}

	/**
	 * A predicate to check if a request was made between two {@link ZonedDateTime}s
	 * @param datetime1 the request must have been made after this {@link ZonedDateTime}
	 * @param datetime2 the request must be made before this {@link ZonedDateTime}
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return asyncPredicate(getBean(BetweenRoutePredicateFactory.class)
				.applyAsync(c -> c.setDatetime1(datetime1.toString()).setDatetime2(datetime2.toString())));
	}

	/**
	 * A predicate that checks if a cookie matches a given regular expression
	 * @param name the name of the cookie
	 * @param regex the value of the cookies will be evaluated against this regular expression
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec cookie(String name, String regex) {
		return asyncPredicate(getBean(CookieRoutePredicateFactory.class)
				.applyAsync(c -> c.setName(name).setRegexp(regex)));
	}

	/**
	 * A predicate that checks if a given header is present on the request
	 * @param header the header name to check
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec header(String header) {
		return asyncPredicate(getBean(HeaderRoutePredicateFactory.class)
				.applyAsync(c -> c.setHeader(header))); //TODO: default regexp
	}

	/**
	 * A predicate that checks if a given headers has a value which matches a regular expression
	 * @param header the header name to check
	 * @param regex the regular expression to check against
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec header(String header, String regex) {
		return asyncPredicate(getBean(HeaderRoutePredicateFactory.class)
				.applyAsync(c -> c.setHeader(header).setRegexp(regex)));
	}

	/**
	 * A predicate that checks if the {@code host} header matches a given pattern
	 * @param pattern the pattern to check against.  The pattern is an Ant style pattern with {@code .} as a separator
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec host(String pattern) {
		return asyncPredicate(getBean(HostRoutePredicateFactory.class)
				.applyAsync(c-> c.setPattern(pattern)));
	}

	/**
	 * A predicate that checks if the HTTP method matches
	 * @param method the name of the HTTP method
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec method(String method) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class)
				.applyAsync(c -> c.setMethod(HttpMethod.resolve(method))));
	}

	/**
	 * A predicate that checks if the HTTP method matches
	 * @param method the HTTP method
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec method(HttpMethod method) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class)
				.applyAsync(c -> c.setMethod(method)));
	}

	/**
	 * A predicate that checks if the path of the request matches the given pattern
	 * @param pattern the pattern to check the path against.
	 *                The pattern is a {@link org.springframework.util.PathMatcher} pattern
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec path(String pattern) {
		return asyncPredicate(
				// 这几步操作 整体而言 就是 获取 对应操作符类型的工厂bean对象。
				// applayAsync 就是 接口通用接口 获取 特定的 config 内部类 然后通过 FunctionInteface 注入配置
				// 然后调用 apply 返回一个 Predicate 对象 .test 方法能够返回 是否适配这个request 请求
				// 如果适配那么这个 Router 就 会 作用于这个 request 之上。
				// 构建出 Predicate 后 就是构建出 Mono 模型即可 这个Mono模型就是 () -> Mono.just(Predicate t .test())
				getBean(PathRoutePredicateFactory.class).applyAsync(c -> c.setPattern(pattern))
		);
	}

	/**
	 * A predicate that checks if the path of the request matches the given pattern
	 * @param pattern the pattern to check the path against.
	 *                The pattern is a {@link org.springframework.util.PathMatcher} pattern
	 * @param matchOptionalTrailingSeparator set to false if you do not want this path to match
	 *                                       when there is a trailing <code>/</code>
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec path(String pattern, boolean matchOptionalTrailingSeparator) {
		return asyncPredicate(getBean(PathRoutePredicateFactory.class)
				.applyAsync(c -> c.setPattern(pattern).setMatchOptionalTrailingSeparator(matchOptionalTrailingSeparator)));
	}

	/**
	 * This predicate is BETA and may be subject to change in a future release.
	 * A predicate that checks the contents of the request body
	 * @param inClass the class to parse the body to
	 * @param predicate a predicate to check the contents of the body
	 * @param <T> the type the body is parsed to
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public <T> BooleanSpec readBody(Class<T> inClass, Predicate<T> predicate) {
		return asyncPredicate(getBean(ReadBodyPredicateFactory.class)
				.applyAsync(c -> c.setPredicate(inClass, predicate)));
	}

	/**
	 * A predicate that checks if a query parameter matches a regular expression
	 * @param param the query parameter name
	 * @param regex the regular expression to evaluate the query parameter value against
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec query(String param, String regex) {
		return asyncPredicate(getBean(QueryRoutePredicateFactory.class)
				.applyAsync(c -> c.setParam(param).setRegexp(regex)));
	}

	/**
	 * A predicate that checks if a given query parameter is present in the request URL
	 * @param param the query parameter name
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec query(String param) {
		return asyncPredicate(getBean(QueryRoutePredicateFactory.class)
				.applyAsync(c -> c.setParam(param)));
	}

	/**
	 * A predicate which checks the remote address of the request.
	 * By default the RemoteAddr Route Predicate Factory uses the remote address from the incoming request.
	 * This may not match the actual client IP address if Spring Cloud Gateway sits behind a proxy layer.
	 * Use {@link PredicateSpec#remoteAddr(RemoteAddressResolver, String...)} to customize the resolver.
	 * You can customize the way that the remote address is resolved by setting a custom RemoteAddressResolver.

	 * @param addrs the remote address to verify.  Should use CIDR-notation (IPv4 or IPv6) strings.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec remoteAddr(String... addrs) {
		return remoteAddr(null, addrs);
	}

	/**
	 * A predicate which checks the remote address of the request.  Useful if Spring Cloud Gateway site behind a proxy
	 * layer.  Spring Cloud Gateway comes with one non-default remote address resolver which is based off of the
	 * {@code X-Forwarded-For} header, {@link org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver}.
	 * See {@link org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver} for more information.
	 * @param resolver the {@link RemoteAddressResolver} to use to resolve the remote IP address against
	 * @param addrs the remote address to verify.  Should use CIDR-notation (IPv4 or IPv6) strings.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec remoteAddr(RemoteAddressResolver resolver, String... addrs) {
		return asyncPredicate(getBean(RemoteAddrRoutePredicateFactory.class).applyAsync(c -> {
			c.setSources(addrs);
			if (resolver != null) {
				c.setRemoteAddressResolver(resolver);
			}
		}));
	}

	/**
	 * A predicate which will select a route based on its assigned weight.  The
	 * @param group the group the route belongs to
	 * @param weight the weight for the route
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec weight(String group, int weight) {
		return asyncPredicate(getBean(WeightRoutePredicateFactory.class)
				.applyAsync(c -> c.setGroup(group)
						.setRouteId(routeBuilder.getId())
						.setWeight(weight)));
	}

	public BooleanSpec cloudFoundryRouteService() {
		return predicate(
				getBean(CloudFoundryRouteServiceRoutePredicateFactory.class).apply(c -> {
				}));
	}

	/**
	 * A predicate which is always true
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec alwaysTrue() {
		return predicate(exchange -> true);
	}
}
