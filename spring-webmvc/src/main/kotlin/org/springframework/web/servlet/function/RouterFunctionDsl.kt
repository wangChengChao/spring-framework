/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.servlet.function

import org.springframework.core.io.Resource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.net.URI
import java.util.*
import java.util.function.Supplier

/**
 * Allow to create easily a WebMvc.fn [RouterFunction] with a [Reactive router Kotlin DSL][RouterFunctionDsl].
 *
 * Example:
 *
 * ```
 * @Configuration
 * class RouterConfiguration {
 *
 * 	@Bean
 * 	fun mainRouter(userHandler: UserHandler) = router {
 * 		accept(TEXT_HTML).nest {
 * 			(GET("/user/") or GET("/users/")).invoke(userHandler::findAllView)
 * 			GET("/users/{login}", userHandler::findViewById)
 * 		}
 * 		accept(APPLICATION_JSON).nest {
 * 			(GET("/api/user/") or GET("/api/users/")).invoke(userHandler::findAll)
 * 			POST("/api/users/", userHandler::create)
 * 		}
 * 	}
 *
 * }
 * ```
 * @author Sebastien Deleuze
 * @since 5.2
 */
fun router(routes: (RouterFunctionDsl.() -> Unit)) = RouterFunctionDsl(routes).build()

/**
 * Provide a WebMvc.fn [RouterFunction] Reactive Kotlin DSL created by [`router { }`][router] in order to be able to write idiomatic Kotlin code.
 *
 * @author Sebastien Deleuze
 * @since 5.2
 */
class RouterFunctionDsl(private val init: (RouterFunctionDsl.() -> Unit)) {

	private val builder = RouterFunctions.route()

	/**
	 * Return a composed request predicate that tests against both this predicate AND
	 * the [other] predicate (String processed as a path predicate). When evaluating the
	 * composed predicate, if this predicate is `false`, then the [other] predicate is not
	 * evaluated.
	 * @see RequestPredicate.and
	 * @see RequestPredicates.path
	 */
	infix fun RequestPredicate.and(other: String): RequestPredicate = this.and(path(other))

	/**
	 * Return a composed request predicate that tests against both this predicate OR
	 * the [other] predicate (String processed as a path predicate). When evaluating the
	 * composed predicate, if this predicate is `true`, then the [other] predicate is not
	 * evaluated.
	 * @see RequestPredicate.or
	 * @see RequestPredicates.path
	 */
	infix fun RequestPredicate.or(other: String): RequestPredicate = this.or(path(other))

	/**
	 * Return a composed request predicate that tests against both this predicate (String
	 * processed as a path predicate) AND the [other] predicate. When evaluating the
	 * composed predicate, if this predicate is `false`, then the [other] predicate is not
	 * evaluated.
	 * @see RequestPredicate.and
	 * @see RequestPredicates.path
	 */
	infix fun String.and(other: RequestPredicate): RequestPredicate = path(this).and(other)

	/**
	 * Return a composed request predicate that tests against both this predicate (String
	 * processed as a path predicate) OR the [other] predicate. When evaluating the
	 * composed predicate, if this predicate is `true`, then the [other] predicate is not
	 * evaluated.
	 * @see RequestPredicate.or
	 * @see RequestPredicates.path
	 */
	infix fun String.or(other: RequestPredicate): RequestPredicate = path(this).or(other)

	/**
	 * Return a composed request predicate that tests against both this predicate AND
	 * the [other] predicate. When evaluating the composed predicate, if this
	 * predicate is `false`, then the [other] predicate is not evaluated.
	 * @see RequestPredicate.and
	 */
	infix fun RequestPredicate.and(other: RequestPredicate): RequestPredicate = this.and(other)

	/**
	 * Return a composed request predicate that tests against both this predicate OR
	 * the [other] predicate. When evaluating the composed predicate, if this
	 * predicate is `true`, then the [other] predicate is not evaluated.
	 * @see RequestPredicate.or
	 */
	infix fun RequestPredicate.or(other: RequestPredicate): RequestPredicate = this.or(other)

	/**
	 * Return a predicate that represents the logical negation of this predicate.
	 */
	operator fun RequestPredicate.not(): RequestPredicate = this.negate()

	/**
	 * Route to the given router function if the given request predicate applies. This
	 * method can be used to create *nested routes*, where a group of routes share a
	 * common path (prefix), header, or other request predicate.
	 * @see RouterFunctions.nest
	 */
	fun RequestPredicate.nest(r: (RouterFunctionDsl.() -> Unit)) {
		builder.nest(this, Supplier(RouterFunctionDsl(r)::build))
	}


	/**
	 * Route to the given router function if the given request predicate (String
	 * processed as a path predicate) applies. This method can be used to create
	 * *nested routes*, where a group of routes share a common path
	 * (prefix), header, or other request predicate.
	 * @see RouterFunctions.nest
	 * @see RequestPredicates.path
	 */
	fun String.nest(r: (RouterFunctionDsl.() -> Unit)) = path(this).nest(r)

	/**
	 * Route to the given handler function if the given request predicate applies.
	 * @see RouterFunctions.route
	 */
	fun GET(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.GET(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `GET`
	 * and the given `pattern` matches against the request path.
	 * @see RequestPredicates.GET
	 */
	fun GET(pattern: String): RequestPredicate = RequestPredicates.GET(pattern)

	/**
	 * Route to the given handler function if the given request predicate applies.
	 * @see RouterFunctions.route
	 */
	fun HEAD(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.HEAD(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `HEAD`
	 * and the given `pattern` matches against the request path.
	 * @see RequestPredicates.HEAD
	 */
	fun HEAD(pattern: String): RequestPredicate = RequestPredicates.HEAD(pattern)

	/**
	 * Route to the given handler function if the given `POST` predicate applies.
	 * @see RouterFunctions.route
	 */
	fun POST(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.POST(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `POST`
	 * and the given `pattern` matches against the request path.
	 * @see RequestPredicates.POST
	 */
	fun POST(pattern: String): RequestPredicate = RequestPredicates.POST(pattern)

	/**
	 * Route to the given handler function if the given `PUT` predicate applies.
	 * @see RouterFunctions.route
	 */
	fun PUT(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.PUT(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `PUT`
	 * and the given `pattern` matches against the request path.
	 * @see RequestPredicates.PUT
	 */
	fun PUT(pattern: String): RequestPredicate = RequestPredicates.PUT(pattern)

	/**
	 * Route to the given handler function if the given `PATCH` predicate applies.
	 * @see RouterFunctions.route
	 */
	fun PATCH(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.PATCH(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `PATCH`
	 * and the given `pattern` matches against the request path.
	 * @param pattern the path pattern to match against
	 * @return a predicate that matches if the request method is `PATCH` and if the given pattern
	 * matches against the request path
	 */
	fun PATCH(pattern: String): RequestPredicate = RequestPredicates.PATCH(pattern)

	/**
	 * Route to the given handler function if the given `DELETE` predicate applies.
	 * @see RouterFunctions.route
	 */
	fun DELETE(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.DELETE(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `DELETE`
	 * and the given `pattern` matches against the request path.
	 * @param pattern the path pattern to match against
	 * @return a predicate that matches if the request method is `DELETE` and if the given pattern
	 * matches against the request path
	 */
	fun DELETE(pattern: String): RequestPredicate = RequestPredicates.DELETE(pattern)

	/**
	 * Route to the given handler function if the given OPTIONS predicate applies.
	 * @see RouterFunctions.route
	 */
	fun OPTIONS(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.OPTIONS(pattern, HandlerFunction(f))
	}

	/**
	 * Return a [RequestPredicate] that matches if request's HTTP method is `OPTIONS`
	 * and the given `pattern` matches against the request path.
	 * @param pattern the path pattern to match against
	 * @return a predicate that matches if the request method is `OPTIONS` and if the given pattern
	 * matches against the request path
	 */
	fun OPTIONS(pattern: String): RequestPredicate = RequestPredicates.OPTIONS(pattern)

	/**
	 * Route to the given handler function if the given accept predicate applies.
	 * @see RouterFunctions.route
	 */
	fun accept(mediaType: MediaType, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.accept(mediaType), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests if the request's
	 * [accept][ServerRequest.Headers.accept] } header is
	 * [compatible][MediaType.isCompatibleWith] with any of the given media types.
	 * @param mediaType the media types to match the request's accept header against
	 * @return a predicate that tests the request's accept header against the given media types
	 */
	fun accept(vararg mediaType: MediaType): RequestPredicate = RequestPredicates.accept(*mediaType)

	/**
	 * Route to the given handler function if the given contentType predicate applies.
	 * @see RouterFunctions.route
	 */
	fun contentType(mediaType: MediaType, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.contentType(mediaType), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests if the request's
	 * [content type][ServerRequest.Headers.contentType] is
	 * [included][MediaType.includes] by any of the given media types.
	 * @param mediaTypes the media types to match the request's content type against
	 * @return a predicate that tests the request's content type against the given media types
	 */
	fun contentType(vararg mediaTypes: MediaType): RequestPredicate = RequestPredicates.contentType(*mediaTypes)

	/**
	 * Route to the given handler function if the given headers predicate applies.
	 * @see RouterFunctions.route
	 */
	fun headers(headersPredicate: (ServerRequest.Headers) -> Boolean, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.headers(headersPredicate), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests the request's headers against the given headers predicate.
	 * @param headersPredicate a predicate that tests against the request headers
	 * @return a predicate that tests against the given header predicate
	 */
	fun headers(headersPredicate: (ServerRequest.Headers) -> Boolean): RequestPredicate =
			RequestPredicates.headers(headersPredicate)

	/**
	 * Route to the given handler function if the given method predicate applies.
	 * @see RouterFunctions.route
	 */
	fun method(httpMethod: HttpMethod, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.method(httpMethod), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests against the given HTTP method.
	 * @param httpMethod the HTTP method to match to
	 * @return a predicate that tests against the given HTTP method
	 */
	fun method(httpMethod: HttpMethod): RequestPredicate = RequestPredicates.method(httpMethod)

	/**
	 * Route to the given handler function if the given path predicate applies.
	 * @see RouterFunctions.route
	 */
	fun path(pattern: String, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.path(pattern), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests the request path against the given path pattern.
	 * @see RequestPredicates.path
	 */
	fun path(pattern: String): RequestPredicate = RequestPredicates.path(pattern)

	/**
	 * Route to the given handler function if the given pathExtension predicate applies.
	 * @see RouterFunctions.route
	 */
	fun pathExtension(extension: String, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.pathExtension(extension), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that matches if the request's path has the given extension.
	 * @param extension the path extension to match against, ignoring case
	 * @return a predicate that matches if the request's path has the given file extension
	 */
	fun pathExtension(extension: String): RequestPredicate = RequestPredicates.pathExtension(extension)

	/**
	 * Route to the given handler function if the given pathExtension predicate applies.
	 * @see RouterFunctions.route
	 */
	fun pathExtension(predicate: (String) -> Boolean, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.pathExtension(predicate), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that matches if the request's path matches the given
	 * predicate.
	 * @see RequestPredicates.pathExtension
	 */
	fun pathExtension(predicate: (String) -> Boolean): RequestPredicate =
			RequestPredicates.pathExtension(predicate)

	/**
	 * Route to the given handler function if the given queryParam predicate applies.
	 * @see RouterFunctions.route
	 */
	fun param(name: String, predicate: (String) -> Boolean, f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.param(name, predicate), HandlerFunction(f)))
	}

	/**
	 * Return a [RequestPredicate] that tests the request's query parameter of the given name
	 * against the given predicate.
	 * @param name the name of the query parameter to test against
	 * @param predicate predicate to test against the query parameter value
	 * @return a predicate that matches the given predicate against the query parameter of the given name
	 * @see ServerRequest#queryParam
	 */
	fun param(name: String, predicate: (String) -> Boolean): RequestPredicate =
			RequestPredicates.param(name, predicate)

	/**
	 * Route to the given handler function if the given request predicate applies.
	 * @see RouterFunctions.route
	 */
	operator fun RequestPredicate.invoke(f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(this, HandlerFunction(f)))
	}

	/**
	 * Route to the given handler function if the given predicate (String
	 * processed as a path predicate) applies.
	 * @see RouterFunctions.route
	 */
	operator fun String.invoke(f: (ServerRequest) -> ServerResponse) {
		builder.add(RouterFunctions.route(RequestPredicates.path(this), HandlerFunction(f)))
	}

	/**
	 * Route requests that match the given pattern to resources relative to the given root location.
	 * @see RouterFunctions.resources
	 */
	fun resources(path: String, location: Resource) {
		builder.resources(path, location)
	}

	/**
	 * Route to resources using the provided lookup function. If the lookup function provides a
	 * [Resource] for the given request, it will be it will be exposed using a
	 * [HandlerFunction] that handles GET, HEAD, and OPTIONS requests.
	 */
	fun resources(lookupFunction: (ServerRequest) -> Resource?) {
		builder.resources {
			Optional.ofNullable(lookupFunction.invoke(it))
		}
	}

	/**
	 * Return a composed routing function created from all the registered routes.
	 */
	internal fun build(): RouterFunction<ServerResponse> {
		init()
		return builder.build()
	}

	/**
	 * @see ServerResponse.from
	 */
	fun from(other: ServerResponse) =
			ServerResponse.from(other)

	/**
	 * @see ServerResponse.created
	 */
	fun created(location: URI) =
			ServerResponse.created(location)

	/**
	 * @see ServerResponse.ok
	 */
	fun ok() = ServerResponse.ok()

	/**
	 * @see ServerResponse.noContent
	 */
	fun noContent() = ServerResponse.noContent()

	/**
	 * @see ServerResponse.accepted
	 */
	fun accepted() = ServerResponse.accepted()

	/**
	 * @see ServerResponse.permanentRedirect
	 */
	fun permanentRedirect(location: URI) = ServerResponse.permanentRedirect(location)

	/**
	 * @see ServerResponse.temporaryRedirect
	 */
	fun temporaryRedirect(location: URI) = ServerResponse.temporaryRedirect(location)

	/**
	 * @see ServerResponse.seeOther
	 */
	fun seeOther(location: URI) = ServerResponse.seeOther(location)

	/**
	 * @see ServerResponse.badRequest
	 */
	fun badRequest() = ServerResponse.badRequest()

	/**
	 * @see ServerResponse.notFound
	 */
	fun notFound() = ServerResponse.notFound()

	/**
	 * @see ServerResponse.unprocessableEntity
	 */
	fun unprocessableEntity() = ServerResponse.unprocessableEntity()

	/**
	 * @see ServerResponse.status
	 */
	fun status(status: HttpStatus) = ServerResponse.status(status)

	/**
	 * @see ServerResponse.status
	 */
	fun status(status: Int) = ServerResponse.status(status)

}

/**
 * Equivalent to [RouterFunction.and].
 */
operator fun <T : ServerResponse> RouterFunction<T>.plus(other: RouterFunction<T>) =
		this.and(other)