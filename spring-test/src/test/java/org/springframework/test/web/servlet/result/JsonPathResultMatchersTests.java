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

package org.springframework.test.web.servlet.result;

import java.nio.charset.StandardCharsets;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.StubMvcResult;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link JsonPathResultMatchers}.
 *
 * @author Rossen Stoyanchev
 * @author Craig Andrews
 * @author Sam Brannen
 * @author Brian Clozel
 * @author Sebastien Deleuze
 */
public class JsonPathResultMatchersTests {

  private static final String RESPONSE_CONTENT =
      "{"
          + //
          "'str':         'foo',           "
          + //
          "'utf8Str':     'Příliš',        "
          + //
          "'num':         5,               "
          + //
          "'bool':        true,            "
          + //
          "'arr':         [42],            "
          + //
          "'colorMap':    {'red': 'rojo'}, "
          + //
          "'emptyString': '',              "
          + //
          "'emptyArray':  [],              "
          + //
          "'emptyMap':    {}               "
          + //
          "}";

  private static final StubMvcResult stubMvcResult;

  static {
    try {
      MockHttpServletResponse response = new MockHttpServletResponse();
      response.addHeader("Content-Type", "application/json");
      response.getOutputStream().write(RESPONSE_CONTENT.getBytes(StandardCharsets.UTF_8));
      stubMvcResult = new StubMvcResult(null, null, null, null, null, null, response);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void valueWithMismatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").value("bogus").match(stubMvcResult));
  }

  @Test
  public void valueWithDirectMatch() throws Exception {
    new JsonPathResultMatchers("$.str").value("foo").match(stubMvcResult);
  }

  @Test // gh-23219
  public void utf8ValueWithDirectMatch() throws Exception {
    new JsonPathResultMatchers("$.utf8Str").value("Příliš").match(stubMvcResult);
  }

  @Test // SPR-16587
  public void valueWithNumberConversion() throws Exception {
    new JsonPathResultMatchers("$.num").value(5.0f).match(stubMvcResult);
  }

  @Test
  public void valueWithMatcher() throws Exception {
    new JsonPathResultMatchers("$.str").value(Matchers.equalTo("foo")).match(stubMvcResult);
  }

  @Test // SPR-16587
  public void valueWithMatcherAndNumberConversion() throws Exception {
    new JsonPathResultMatchers("$.num")
        .value(Matchers.equalTo(5.0f), Float.class)
        .match(stubMvcResult);
  }

  @Test
  public void valueWithMatcherAndMismatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                new JsonPathResultMatchers("$.str")
                    .value(Matchers.equalTo("bogus"))
                    .match(stubMvcResult));
  }

  @Test
  public void exists() throws Exception {
    new JsonPathResultMatchers("$.str").exists().match(stubMvcResult);
  }

  @Test
  public void existsForAnEmptyArray() throws Exception {
    new JsonPathResultMatchers("$.emptyArray").exists().match(stubMvcResult);
  }

  @Test
  public void existsForAnEmptyMap() throws Exception {
    new JsonPathResultMatchers("$.emptyMap").exists().match(stubMvcResult);
  }

  @Test
  public void existsNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.bogus").exists().match(stubMvcResult));
  }

  @Test
  public void doesNotExist() throws Exception {
    new JsonPathResultMatchers("$.bogus").doesNotExist().match(stubMvcResult);
  }

  @Test
  public void doesNotExistNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").doesNotExist().match(stubMvcResult));
  }

  @Test
  public void doesNotExistForAnEmptyArray() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.emptyArray").doesNotExist().match(stubMvcResult));
  }

  @Test
  public void doesNotExistForAnEmptyMap() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.emptyMap").doesNotExist().match(stubMvcResult));
  }

  @Test
  public void isEmptyForAnEmptyString() throws Exception {
    new JsonPathResultMatchers("$.emptyString").isEmpty().match(stubMvcResult);
  }

  @Test
  public void isEmptyForAnEmptyArray() throws Exception {
    new JsonPathResultMatchers("$.emptyArray").isEmpty().match(stubMvcResult);
  }

  @Test
  public void isEmptyForAnEmptyMap() throws Exception {
    new JsonPathResultMatchers("$.emptyMap").isEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForString() throws Exception {
    new JsonPathResultMatchers("$.str").isNotEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForNumber() throws Exception {
    new JsonPathResultMatchers("$.num").isNotEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForBoolean() throws Exception {
    new JsonPathResultMatchers("$.bool").isNotEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForArray() throws Exception {
    new JsonPathResultMatchers("$.arr").isNotEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForMap() throws Exception {
    new JsonPathResultMatchers("$.colorMap").isNotEmpty().match(stubMvcResult);
  }

  @Test
  public void isNotEmptyForAnEmptyString() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.emptyString").isNotEmpty().match(stubMvcResult));
  }

  @Test
  public void isNotEmptyForAnEmptyArray() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.emptyArray").isNotEmpty().match(stubMvcResult));
  }

  @Test
  public void isNotEmptyForAnEmptyMap() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.emptyMap").isNotEmpty().match(stubMvcResult));
  }

  @Test
  public void isArray() throws Exception {
    new JsonPathResultMatchers("$.arr").isArray().match(stubMvcResult);
  }

  @Test
  public void isArrayForAnEmptyArray() throws Exception {
    new JsonPathResultMatchers("$.emptyArray").isArray().match(stubMvcResult);
  }

  @Test
  public void isArrayNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.bar").isArray().match(stubMvcResult));
  }

  @Test
  public void isMap() throws Exception {
    new JsonPathResultMatchers("$.colorMap").isMap().match(stubMvcResult);
  }

  @Test
  public void isMapForAnEmptyMap() throws Exception {
    new JsonPathResultMatchers("$.emptyMap").isMap().match(stubMvcResult);
  }

  @Test
  public void isMapNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").isMap().match(stubMvcResult));
  }

  @Test
  public void isBoolean() throws Exception {
    new JsonPathResultMatchers("$.bool").isBoolean().match(stubMvcResult);
  }

  @Test
  public void isBooleanNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").isBoolean().match(stubMvcResult));
  }

  @Test
  public void isNumber() throws Exception {
    new JsonPathResultMatchers("$.num").isNumber().match(stubMvcResult);
  }

  @Test
  public void isNumberNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").isNumber().match(stubMvcResult));
  }

  @Test
  public void isString() throws Exception {
    new JsonPathResultMatchers("$.str").isString().match(stubMvcResult);
  }

  @Test
  public void isStringNoMatch() throws Exception {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.arr").isString().match(stubMvcResult));
  }

  @Test
  public void valueWithJsonPrefixNotConfigured() throws Exception {
    String jsonPrefix = "prefix";
    StubMvcResult result = createPrefixedStubMvcResult(jsonPrefix);
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> new JsonPathResultMatchers("$.str").value("foo").match(result));
  }

  @Test
  public void valueWithJsonWrongPrefix() throws Exception {
    String jsonPrefix = "prefix";
    StubMvcResult result = createPrefixedStubMvcResult(jsonPrefix);
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.str").prefix("wrong").value("foo").match(result));
  }

  @Test
  public void valueWithJsonPrefix() throws Exception {
    String jsonPrefix = "prefix";
    StubMvcResult result = createPrefixedStubMvcResult(jsonPrefix);
    new JsonPathResultMatchers("$.str").prefix(jsonPrefix).value("foo").match(result);
  }

  @Test
  public void prefixWithPayloadNotLongEnough() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.addHeader("Content-Type", "application/json");
    response.getWriter().print(new String("test".getBytes("ISO-8859-1")));
    StubMvcResult result = new StubMvcResult(null, null, null, null, null, null, response);

    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> new JsonPathResultMatchers("$.str").prefix("prefix").value("foo").match(result));
  }

  private StubMvcResult createPrefixedStubMvcResult(String jsonPrefix) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.addHeader("Content-Type", "application/json");
    response.getWriter().print(jsonPrefix + new String(RESPONSE_CONTENT.getBytes("ISO-8859-1")));
    return new StubMvcResult(null, null, null, null, null, null, response);
  }
}
