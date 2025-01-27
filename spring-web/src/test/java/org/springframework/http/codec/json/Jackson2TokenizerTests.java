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

package org.springframework.http.codec.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.AbstractLeakCheckingTestCase;
import org.springframework.core.io.buffer.DataBuffer;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 */
public class Jackson2TokenizerTests extends AbstractLeakCheckingTestCase {

  private JsonFactory jsonFactory;

  private ObjectMapper objectMapper;

  @BeforeEach
  public void createParser() {
    this.jsonFactory = new JsonFactory();
    this.objectMapper = new ObjectMapper(this.jsonFactory);
  }

  @Test
  public void doNotTokenizeArrayElements() {
    testTokenize(
        singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
        singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
        false);

    testTokenize(
        asList("{\"foo\": \"foofoo\"", ", \"bar\": \"barbar\"}"),
        singletonList("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}"),
        false);

    testTokenize(
        singletonList(
            "["
                + "{\"foo\": \"foofoo\", \"bar\": \"barbar\"},"
                + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        singletonList(
            "["
                + "{\"foo\": \"foofoo\", \"bar\": \"barbar\"},"
                + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        false);

    testTokenize(
        singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"),
        singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"),
        false);

    testTokenize(
        asList(
            "[" + "{\"foo\": \"foofoo\", \"bar\"",
            ": \"barbar\"}," + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        singletonList(
            "["
                + "{\"foo\": \"foofoo\", \"bar\": \"barbar\"},"
                + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        false);

    testTokenize(
        asList(
            "[",
            "{\"id\":1,\"name\":\"Robert\"}",
            ",",
            "{\"id\":2,\"name\":\"Raide\"}",
            ",",
            "{\"id\":3,\"name\":\"Ford\"}",
            "]"),
        singletonList(
            "["
                + "{\"id\":1,\"name\":\"Robert\"},"
                + "{\"id\":2,\"name\":\"Raide\"},"
                + "{\"id\":3,\"name\":\"Ford\"}]"),
        false);

    // SPR-16166: top-level JSON values
    testTokenize(asList("\"foo", "bar\""), singletonList("\"foobar\""), false);

    testTokenize(asList("12", "34"), singletonList("1234"), false);

    testTokenize(asList("12.", "34"), singletonList("12.34"), false);

    // note that we do not test for null, true, or false, which are also valid top-level values,
    // but are unsupported by JSONassert
  }

  @Test
  public void tokenizeArrayElements() {
    testTokenize(
        singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
        singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
        true);

    testTokenize(
        asList("{\"foo\": \"foofoo\"", ", \"bar\": \"barbar\"}"),
        singletonList("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}"),
        true);

    testTokenize(
        singletonList(
            "["
                + "{\"foo\": \"foofoo\", \"bar\": \"barbar\"},"
                + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        asList(
            "{\"foo\": \"foofoo\", \"bar\": \"barbar\"}",
            "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}"),
        true);

    testTokenize(
        singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"),
        asList("{\"foo\": \"bar\"}", "{\"foo\": \"baz\"}"),
        true);

    // SPR-15803: nested array
    testTokenize(
        singletonList(
            "["
                + "{\"id\":\"0\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]},"
                + "{\"id\":\"1\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]},"
                + "{\"id\":\"2\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}"
                + "]"),
        asList(
            "{\"id\":\"0\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}",
            "{\"id\":\"1\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}",
            "{\"id\":\"2\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}"),
        true);

    // SPR-15803: nested array, no top-level array
    testTokenize(
        singletonList("{\"speakerIds\":[\"tastapod\"],\"language\":\"ENGLISH\"}"),
        singletonList("{\"speakerIds\":[\"tastapod\"],\"language\":\"ENGLISH\"}"),
        true);

    testTokenize(
        asList(
            "[" + "{\"foo\": \"foofoo\", \"bar\"",
            ": \"barbar\"}," + "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
        asList(
            "{\"foo\": \"foofoo\", \"bar\": \"barbar\"}",
            "{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}"),
        true);

    testTokenize(
        asList(
            "[",
            "{\"id\":1,\"name\":\"Robert\"}",
            ",",
            "{\"id\":2,\"name\":\"Raide\"}",
            ",",
            "{\"id\":3,\"name\":\"Ford\"}",
            "]"),
        asList(
            "{\"id\":1,\"name\":\"Robert\"}",
            "{\"id\":2,\"name\":\"Raide\"}",
            "{\"id\":3,\"name\":\"Ford\"}"),
        true);

    // SPR-16166: top-level JSON values
    testTokenize(asList("\"foo", "bar\""), singletonList("\"foobar\""), true);

    testTokenize(asList("12", "34"), singletonList("1234"), true);

    testTokenize(asList("12.", "34"), singletonList("12.34"), true);

    // SPR-16407
    testTokenize(asList("[1", ",2,", "3]"), asList("1", "2", "3"), true);
  }

  @Test
  public void errorInStream() {
    DataBuffer buffer = stringBuffer("{\"id\":1,\"name\":");
    Flux<DataBuffer> source = Flux.just(buffer).concatWith(Flux.error(new RuntimeException()));
    Flux<TokenBuffer> result =
        Jackson2Tokenizer.tokenize(source, this.jsonFactory, this.objectMapper, true);

    StepVerifier.create(result).expectError(RuntimeException.class).verify();
  }

  @Test // SPR-16521
  public void jsonEOFExceptionIsWrappedAsDecodingError() {
    Flux<DataBuffer> source = Flux.just(stringBuffer("{\"status\": \"noClosingQuote}"));
    Flux<TokenBuffer> tokens =
        Jackson2Tokenizer.tokenize(source, this.jsonFactory, this.objectMapper, false);

    StepVerifier.create(tokens).expectError(DecodingException.class).verify();
  }

  private void testTokenize(
      List<String> source, List<String> expected, boolean tokenizeArrayElements) {
    Flux<TokenBuffer> tokens =
        Jackson2Tokenizer.tokenize(
            Flux.fromIterable(source).map(this::stringBuffer),
            this.jsonFactory,
            this.objectMapper,
            tokenizeArrayElements);

    Flux<String> result =
        tokens.map(
            tokenBuffer -> {
              try {
                TreeNode root = this.objectMapper.readTree(tokenBuffer.asParser());
                return this.objectMapper.writeValueAsString(root);
              } catch (IOException ex) {
                throw new UncheckedIOException(ex);
              }
            });

    StepVerifier.FirstStep<String> builder = StepVerifier.create(result);
    expected.forEach(s -> builder.assertNext(new JSONAssertConsumer(s)));
    builder.verifyComplete();
  }

  private DataBuffer stringBuffer(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = this.bufferFactory.allocateBuffer(bytes.length);
    buffer.write(bytes);
    return buffer;
  }

  private static class JSONAssertConsumer implements Consumer<String> {

    private final String expected;

    JSONAssertConsumer(String expected) {
      this.expected = expected;
    }

    @Override
    public void accept(String s) {
      try {
        JSONAssert.assertEquals(this.expected, s, true);
      } catch (JSONException ex) {
        throw new RuntimeException(ex);
      }
    }
  }
}
