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

package org.springframework.web.reactive.function.server;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.HEAD;

/** @author Arjen Poutsma */
public class RouterFunctionBuilderTests {

  @Test
  public void route() {
    RouterFunction<ServerResponse> route =
        RouterFunctions.route()
            .GET("/foo", request -> ServerResponse.ok().build())
            .POST(
                "/",
                RequestPredicates.contentType(MediaType.TEXT_PLAIN),
                request -> ServerResponse.noContent().build())
            .route(HEAD("/foo"), request -> ServerResponse.accepted().build())
            .build();

    MockServerRequest getFooRequest =
        MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("http://localhost/foo"))
            .build();

    Mono<Integer> responseMono =
        route
            .route(getFooRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(getFooRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).expectNext(200).verifyComplete();

    MockServerRequest headFooRequest =
        MockServerRequest.builder()
            .method(HttpMethod.HEAD)
            .uri(URI.create("http://localhost/foo"))
            .build();

    responseMono =
        route
            .route(headFooRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(getFooRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).expectNext(202).verifyComplete();

    MockServerRequest barRequest =
        MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create("http://localhost/"))
            .header("Content-Type", "text/plain")
            .build();

    responseMono =
        route
            .route(barRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(barRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).expectNext(204).verifyComplete();

    MockServerRequest invalidRequest =
        MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create("http://localhost/"))
            .build();

    responseMono =
        route
            .route(invalidRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(invalidRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).verifyComplete();
  }

  @Test
  public void resources() {
    Resource resource = new ClassPathResource("/org/springframework/web/reactive/function/server/");
    assertThat(resource.exists()).isTrue();

    RouterFunction<ServerResponse> route =
        RouterFunctions.route().resources("/resources/**", resource).build();

    MockServerRequest resourceRequest =
        MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("http://localhost/resources/response.txt"))
            .build();

    Mono<Integer> responseMono =
        route
            .route(resourceRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(resourceRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).expectNext(200).verifyComplete();

    MockServerRequest invalidRequest =
        MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create("http://localhost/resources/foo.txt"))
            .build();

    responseMono =
        route
            .route(invalidRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(invalidRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).verifyComplete();
  }

  @Test
  public void nest() {
    RouterFunction<?> route =
        RouterFunctions.route()
            .path(
                "/foo",
                builder ->
                    builder.path(
                        "/bar",
                        () ->
                            RouterFunctions.route()
                                .GET("/baz", request -> ServerResponse.ok().build())
                                .build()))
            .build();

    MockServerRequest fooRequest =
        MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("http://localhost/foo/bar/baz"))
            .build();

    Mono<Integer> responseMono =
        route
            .route(fooRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(fooRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(responseMono).expectNext(200).verifyComplete();
  }

  @Test
  public void filters() {
    AtomicInteger filterCount = new AtomicInteger();

    RouterFunction<?> route =
        RouterFunctions.route()
            .GET("/foo", request -> ServerResponse.ok().build())
            .GET("/bar", request -> Mono.error(new IllegalStateException()))
            .before(
                request -> {
                  int count = filterCount.getAndIncrement();
                  assertThat(count).isEqualTo(0);
                  return request;
                })
            .after(
                (request, response) -> {
                  int count = filterCount.getAndIncrement();
                  assertThat(count).isEqualTo(3);
                  return response;
                })
            .filter(
                (request, next) -> {
                  int count = filterCount.getAndIncrement();
                  assertThat(count).isEqualTo(1);
                  Mono<ServerResponse> responseMono = next.handle(request);
                  count = filterCount.getAndIncrement();
                  assertThat(count).isEqualTo(2);
                  return responseMono;
                })
            .onError(
                IllegalStateException.class,
                (e, request) -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
            .build();

    MockServerRequest fooRequest =
        MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("http://localhost/foo"))
            .build();

    Mono<ServerResponse> fooResponseMono =
        route.route(fooRequest).flatMap(handlerFunction -> handlerFunction.handle(fooRequest));

    StepVerifier.create(fooResponseMono)
        .consumeNextWith(serverResponse -> assertThat(filterCount.get()).isEqualTo(4))
        .verifyComplete();

    filterCount.set(0);

    MockServerRequest barRequest =
        MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("http://localhost/bar"))
            .build();

    Mono<Integer> barResponseMono =
        route
            .route(barRequest)
            .flatMap(handlerFunction -> handlerFunction.handle(barRequest))
            .map(ServerResponse::statusCode)
            .map(HttpStatus::value);

    StepVerifier.create(barResponseMono).expectNext(500).verifyComplete();
  }
}
