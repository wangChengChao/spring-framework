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

package org.springframework.test.web.client;

import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.max;
import static org.springframework.test.web.client.ExpectedCount.min;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link SimpleRequestExpectationManager}.
 *
 * @author Rossen Stoyanchev
 */
public class SimpleRequestExpectationManagerTests {

  private final SimpleRequestExpectationManager manager = new SimpleRequestExpectationManager();

  @Test
  public void unexpectedRequest() throws Exception {
    try {
      this.manager.validateRequest(createRequest(GET, "/foo"));
    } catch (AssertionError error) {
      assertThat(error.getMessage())
          .isEqualTo(
              ("No further requests expected: HTTP GET /foo\n" + "0 request(s) executed.\n"));
    }
  }

  @Test
  public void zeroExpectedRequests() throws Exception {
    this.manager.verify();
  }

  @Test
  public void sequentialRequests() throws Exception {
    this.manager
        .expectRequest(once(), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(once(), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());

    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.verify();
  }

  @Test
  public void sequentialRequestsTooMany() throws Exception {
    this.manager
        .expectRequest(max(1), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(max(1), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> this.manager.validateRequest(createRequest(GET, "/baz")))
        .withMessage(
            "No further requests expected: HTTP GET /baz\n"
                + "2 request(s) executed:\n"
                + "GET /foo\n"
                + "GET /bar\n");
  }

  @Test
  public void sequentialRequestsTooFew() throws Exception {
    this.manager
        .expectRequest(min(1), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(min(1), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager.validateRequest(createRequest(GET, "/foo"));
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> this.manager.verify())
        .withMessage(
            "Further request(s) expected leaving 1 unsatisfied expectation(s).\n"
                + "1 request(s) executed:\nGET /foo\n");
  }

  @Test
  public void repeatedRequests() throws Exception {
    this.manager
        .expectRequest(times(3), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(times(3), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());

    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.verify();
  }

  @Test
  public void repeatedRequestsTooMany() throws Exception {
    this.manager
        .expectRequest(max(2), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(max(2), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> this.manager.validateRequest(createRequest(GET, "/foo")))
        .withMessage(
            "No further requests expected: HTTP GET /foo\n"
                + "4 request(s) executed:\n"
                + "GET /foo\n"
                + "GET /bar\n"
                + "GET /foo\n"
                + "GET /bar\n");
  }

  @Test
  public void repeatedRequestsTooFew() throws Exception {
    this.manager
        .expectRequest(min(2), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(min(2), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> this.manager.verify())
        .withMessageContaining(
            "3 request(s) executed:\n" + "GET /foo\n" + "GET /bar\n" + "GET /foo\n");
  }

  @Test
  public void repeatedRequestsNotInOrder() throws Exception {
    this.manager
        .expectRequest(twice(), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(twice(), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(twice(), requestTo("/baz"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> this.manager.validateRequest(createRequest(POST, "/foo")))
        .withMessage("Unexpected HttpMethod expected:<GET> but was:<POST>");
  }

  @Test // SPR-15672
  public void sequentialRequestsWithDifferentCount() throws Exception {
    this.manager
        .expectRequest(times(2), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(once(), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());

    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
  }

  @Test // SPR-15719
  public void repeatedRequestsInSequentialOrder() throws Exception {
    this.manager
        .expectRequest(times(2), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    this.manager
        .expectRequest(times(2), requestTo("/bar"))
        .andExpect(method(GET))
        .andRespond(withSuccess());

    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/foo"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
    this.manager.validateRequest(createRequest(GET, "/bar"));
  }

  @Test // SPR-16132
  public void sequentialRequestsWithFirstFailing() throws Exception {
    this.manager
        .expectRequest(once(), requestTo("/foo"))
        .andExpect(method(GET))
        .andRespond(
            request -> {
              throw new SocketException("pseudo network error");
            });
    this.manager
        .expectRequest(once(), requestTo("/handle-error"))
        .andExpect(method(POST))
        .andRespond(withSuccess());
    assertThatExceptionOfType(SocketException.class)
        .isThrownBy(() -> this.manager.validateRequest(createRequest(GET, "/foo")));
    this.manager.validateRequest(createRequest(POST, "/handle-error"));
    this.manager.verify();
  }

  private ClientHttpRequest createRequest(HttpMethod method, String url) {
    try {
      return new MockClientHttpRequest(method, new URI(url));
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
