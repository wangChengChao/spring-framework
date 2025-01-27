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

package org.springframework.web.servlet.handler;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.WebContentGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CORS-related handling in {@link AbstractHandlerMapping}.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 */
public class CorsAbstractHandlerMappingTests {

  private MockHttpServletRequest request;

  private AbstractHandlerMapping handlerMapping;

  @BeforeEach
  public void setup() {
    StaticWebApplicationContext context = new StaticWebApplicationContext();
    this.handlerMapping = new TestHandlerMapping();
    this.handlerMapping.setInterceptors(mock(HandlerInterceptor.class));
    this.handlerMapping.setApplicationContext(context);
    this.request = new MockHttpServletRequest();
    this.request.setRemoteHost("domain1.com");
  }

  @Test
  public void actualRequestWithoutCorsConfigurationProvider() throws Exception {
    this.request.setMethod(RequestMethod.GET.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    boolean condition = chain.getHandler() instanceof SimpleHandler;
    assertThat(condition).isTrue();
  }

  @Test
  public void preflightRequestWithoutCorsConfigurationProvider() throws Exception {
    this.request.setMethod(RequestMethod.OPTIONS.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    boolean condition = chain.getHandler() instanceof SimpleHandler;
    assertThat(condition).isTrue();
  }

  @Test
  public void actualRequestWithCorsConfigurationProvider() throws Exception {
    this.request.setMethod(RequestMethod.GET.name());
    this.request.setRequestURI("/cors");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    boolean condition = chain.getHandler() instanceof CorsAwareHandler;
    assertThat(condition).isTrue();
    assertThat(getRequiredCorsConfiguration(chain, false).getAllowedOrigins())
        .isEqualTo(Collections.singletonList("*"));
  }

  @Test
  public void preflightRequestWithCorsConfigurationProvider() throws Exception {
    this.request.setMethod(RequestMethod.OPTIONS.name());
    this.request.setRequestURI("/cors");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    assertThat(chain.getHandler()).isNotNull();
    assertThat(chain.getHandler().getClass().getSimpleName()).isEqualTo("PreFlightHandler");
    assertThat(getRequiredCorsConfiguration(chain, true).getAllowedOrigins())
        .isEqualTo(Collections.singletonList("*"));
  }

  @Test
  public void actualRequestWithMappedCorsConfiguration() throws Exception {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("*");
    this.handlerMapping.setCorsConfigurations(Collections.singletonMap("/foo", config));
    this.request.setMethod(RequestMethod.GET.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    boolean condition = chain.getHandler() instanceof SimpleHandler;
    assertThat(condition).isTrue();
    assertThat(getRequiredCorsConfiguration(chain, false).getAllowedOrigins())
        .isEqualTo(Collections.singletonList("*"));
  }

  @Test
  public void preflightRequestWithMappedCorsConfiguration() throws Exception {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("*");
    this.handlerMapping.setCorsConfigurations(Collections.singletonMap("/foo", config));
    this.request.setMethod(RequestMethod.OPTIONS.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    assertThat(chain.getHandler()).isNotNull();
    assertThat(chain.getHandler().getClass().getSimpleName()).isEqualTo("PreFlightHandler");
    assertThat(getRequiredCorsConfiguration(chain, true).getAllowedOrigins())
        .isEqualTo(Collections.singletonList("*"));
  }

  @Test
  public void actualRequestWithCorsConfigurationSource() throws Exception {
    this.handlerMapping.setCorsConfigurationSource(new CustomCorsConfigurationSource());
    this.request.setMethod(RequestMethod.GET.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    boolean condition = chain.getHandler() instanceof SimpleHandler;
    assertThat(condition).isTrue();
    CorsConfiguration config = getRequiredCorsConfiguration(chain, false);
    assertThat(config).isNotNull();
    assertThat(config.getAllowedOrigins()).isEqualTo(Collections.singletonList("*"));
    assertThat(config.getAllowCredentials()).isEqualTo(true);
  }

  @Test
  public void preflightRequestWithCorsConfigurationSource() throws Exception {
    this.handlerMapping.setCorsConfigurationSource(new CustomCorsConfigurationSource());
    this.request.setMethod(RequestMethod.OPTIONS.name());
    this.request.setRequestURI("/foo");
    this.request.addHeader(HttpHeaders.ORIGIN, "https://domain2.com");
    this.request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
    HandlerExecutionChain chain = handlerMapping.getHandler(this.request);

    assertThat(chain).isNotNull();
    assertThat(chain.getHandler()).isNotNull();
    assertThat(chain.getHandler().getClass().getSimpleName()).isEqualTo("PreFlightHandler");
    CorsConfiguration config = getRequiredCorsConfiguration(chain, true);
    assertThat(config).isNotNull();
    assertThat(config.getAllowedOrigins()).isEqualTo(Collections.singletonList("*"));
    assertThat(config.getAllowCredentials()).isEqualTo(true);
  }

  @SuppressWarnings("ConstantConditions")
  private CorsConfiguration getRequiredCorsConfiguration(
      HandlerExecutionChain chain, boolean isPreFlightRequest) {
    CorsConfiguration corsConfig = null;
    if (isPreFlightRequest) {
      Object handler = chain.getHandler();
      assertThat(handler.getClass().getSimpleName()).isEqualTo("PreFlightHandler");
      DirectFieldAccessor accessor = new DirectFieldAccessor(handler);
      corsConfig = (CorsConfiguration) accessor.getPropertyValue("config");
    } else {
      HandlerInterceptor[] interceptors = chain.getInterceptors();
      if (!ObjectUtils.isEmpty(interceptors)) {
        DirectFieldAccessor accessor = new DirectFieldAccessor(interceptors[0]);
        corsConfig = (CorsConfiguration) accessor.getPropertyValue("config");
      }
    }
    assertThat(corsConfig).isNotNull();
    return corsConfig;
  }

  public class TestHandlerMapping extends AbstractHandlerMapping {

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
      if (request.getRequestURI().equals("/cors")) {
        return new CorsAwareHandler();
      }
      return new SimpleHandler();
    }
  }

  public class SimpleHandler extends WebContentGenerator implements HttpRequestHandler {

    public SimpleHandler() {
      super(METHOD_GET);
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

      response.setStatus(HttpStatus.OK.value());
    }
  }

  public class CorsAwareHandler extends SimpleHandler implements CorsConfigurationSource {

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
      CorsConfiguration config = new CorsConfiguration();
      config.addAllowedOrigin("*");
      return config;
    }
  }

  public class CustomCorsConfigurationSource implements CorsConfigurationSource {

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
      CorsConfiguration config = new CorsConfiguration();
      config.addAllowedOrigin("*");
      config.setAllowCredentials(true);
      return config;
    }
  }
}
