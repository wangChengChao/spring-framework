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

package org.springframework.core.codec;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.AbstractLeakCheckingTestCase;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.io.buffer.DataBufferUtils.release;

/**
 * Abstract base class for {@link Encoder} unit tests. Subclasses need to implement {@link
 * #canEncode()} and {@link #encode()}, possibly using the wide * variety of helper methods like
 * {@link #testEncodeAll}.
 *
 * @author Arjen Poutsma
 * @since 5.1.3
 */
@SuppressWarnings("ProtectedField")
public abstract class AbstractEncoderTestCase<E extends Encoder<?>>
    extends AbstractLeakCheckingTestCase {

  /** The encoder to test. */
  protected final E encoder;

  /**
   * Construct a new {@code AbstractEncoderTestCase} for the given parameters.
   *
   * @param encoder the encoder
   */
  protected AbstractEncoderTestCase(E encoder) {

    Assert.notNull(encoder, "Encoder must not be null");

    this.encoder = encoder;
  }

  /** Subclasses should implement this method to test {@link Encoder#canEncode}. */
  @Test
  public abstract void canEncode() throws Exception;

  /**
   * Subclasses should implement this method to test {@link Encoder#encode}, possibly using {@link
   * #testEncodeAll} or other helper methods.
   */
  @Test
  public abstract void encode() throws Exception;

  /**
   * Helper methods that tests for a variety of encoding scenarios. This methods invokes:
   *
   * <ul>
   *   <li>{@link #testEncode(Publisher, ResolvableType, Consumer, MimeType, Map)}
   *   <li>{@link #testEncodeError(Publisher, ResolvableType, MimeType, Map)}
   *   <li>{@link #testEncodeCancel(Publisher, ResolvableType, MimeType, Map)}
   *   <li>{@link #testEncodeEmpty(ResolvableType, MimeType, Map)}
   * </ul>
   *
   * @param input the input to be provided to the encoder
   * @param inputClass the input class
   * @param stepConsumer a consumer to {@linkplain StepVerifier verify} the output
   * @param <T> the output type
   */
  protected <T> void testEncodeAll(
      Publisher<? extends T> input,
      Class<? extends T> inputClass,
      Consumer<StepVerifier.FirstStep<DataBuffer>> stepConsumer) {
    testEncodeAll(input, ResolvableType.forClass(inputClass), stepConsumer, null, null);
  }

  /**
   * Helper methods that tests for a variety of decoding scenarios. This methods invokes:
   *
   * <ul>
   *   <li>{@link #testEncode(Publisher, ResolvableType, Consumer, MimeType, Map)}
   *   <li>{@link #testEncodeError(Publisher, ResolvableType, MimeType, Map)}
   *   <li>{@link #testEncodeCancel(Publisher, ResolvableType, MimeType, Map)}
   *   <li>{@link #testEncodeEmpty(ResolvableType, MimeType, Map)}
   * </ul>
   *
   * @param input the input to be provided to the encoder
   * @param inputType the input type
   * @param stepConsumer a consumer to {@linkplain StepVerifier verify} the output
   * @param mimeType the mime type to use for decoding. May be {@code null}.
   * @param hints the hints used for decoding. May be {@code null}.
   * @param <T> the output type
   */
  protected <T> void testEncodeAll(
      Publisher<? extends T> input,
      ResolvableType inputType,
      Consumer<StepVerifier.FirstStep<DataBuffer>> stepConsumer,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {
    testEncode(input, inputType, stepConsumer, mimeType, hints);
    testEncodeError(input, inputType, mimeType, hints);
    testEncodeCancel(input, inputType, mimeType, hints);
    testEncodeEmpty(inputType, mimeType, hints);
  }

  /**
   * Test a standard {@link Encoder#encode encode} scenario.
   *
   * @param input the input to be provided to the encoder
   * @param inputClass the input class
   * @param stepConsumer a consumer to {@linkplain StepVerifier verify} the output
   * @param <T> the output type
   */
  protected <T> void testEncode(
      Publisher<? extends T> input,
      Class<? extends T> inputClass,
      Consumer<StepVerifier.FirstStep<DataBuffer>> stepConsumer) {
    testEncode(input, ResolvableType.forClass(inputClass), stepConsumer, null, null);
  }

  /**
   * Test a standard {@link Encoder#encode encode} scenario.
   *
   * @param input the input to be provided to the encoder
   * @param inputType the input type
   * @param stepConsumer a consumer to {@linkplain StepVerifier verify} the output
   * @param mimeType the mime type to use for decoding. May be {@code null}.
   * @param hints the hints used for decoding. May be {@code null}.
   * @param <T> the output type
   */
  protected <T> void testEncode(
      Publisher<? extends T> input,
      ResolvableType inputType,
      Consumer<StepVerifier.FirstStep<DataBuffer>> stepConsumer,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {

    Flux<DataBuffer> result =
        encoder().encode(input, this.bufferFactory, inputType, mimeType, hints);
    StepVerifier.FirstStep<DataBuffer> step = StepVerifier.create(result);
    stepConsumer.accept(step);
  }

  /**
   * Test a {@link Encoder#encode encode} scenario where the input stream contains an error. This
   * test method will feed the first element of the {@code input} stream to the encoder, followed by
   * an {@link InputException}. The result is expected to contain one "normal" element, followed by
   * the error.
   *
   * @param input the input to be provided to the encoder
   * @param inputType the input type
   * @param mimeType the mime type to use for decoding. May be {@code null}.
   * @param hints the hints used for decoding. May be {@code null}.
   * @see InputException
   */
  protected void testEncodeError(
      Publisher<?> input,
      ResolvableType inputType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {

    input = Flux.concat(Flux.from(input).take(1), Flux.error(new InputException()));

    Flux<DataBuffer> result =
        encoder().encode(input, this.bufferFactory, inputType, mimeType, hints);

    StepVerifier.create(result)
        .consumeNextWith(DataBufferUtils::release)
        .expectError(InputException.class)
        .verify();
  }

  /**
   * Test a {@link Encoder#encode encode} scenario where the input stream is canceled. This test
   * method will feed the first element of the {@code input} stream to the decoder, followed by a
   * cancel signal. The result is expected to contain one "normal" element.
   *
   * @param input the input to be provided to the encoder
   * @param inputType the input type
   * @param mimeType the mime type to use for decoding. May be {@code null}.
   * @param hints the hints used for decoding. May be {@code null}.
   */
  protected void testEncodeCancel(
      Publisher<?> input,
      ResolvableType inputType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {

    Flux<DataBuffer> result =
        encoder().encode(input, this.bufferFactory, inputType, mimeType, hints);

    StepVerifier.create(result).consumeNextWith(DataBufferUtils::release).thenCancel().verify();
  }

  /**
   * Test a {@link Encoder#encode encode} scenario where the input stream is empty. The output is
   * expected to be empty as well.
   *
   * @param inputType the input type
   * @param mimeType the mime type to use for decoding. May be {@code null}.
   * @param hints the hints used for decoding. May be {@code null}.
   */
  protected void testEncodeEmpty(
      ResolvableType inputType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

    Flux<?> input = Flux.empty();
    Flux<DataBuffer> result =
        encoder().encode(input, this.bufferFactory, inputType, mimeType, hints);

    StepVerifier.create(result).verifyComplete();
  }

  /**
   * Create a result consumer that expects the given bytes.
   *
   * @param expected the expected bytes
   * @return a consumer that expects the given data buffer to be equal to {@code expected}
   */
  protected final Consumer<DataBuffer> expectBytes(byte[] expected) {
    return dataBuffer -> {
      byte[] resultBytes = new byte[dataBuffer.readableByteCount()];
      dataBuffer.read(resultBytes);
      release(dataBuffer);
      assertThat(resultBytes).isEqualTo(expected);
    };
  }

  /**
   * Create a result consumer that expects the given string, using the UTF-8 encoding.
   *
   * @param expected the expected string
   * @return a consumer that expects the given data buffer to be equal to {@code expected}
   */
  protected Consumer<DataBuffer> expectString(String expected) {
    return dataBuffer -> {
      byte[] resultBytes = new byte[dataBuffer.readableByteCount()];
      dataBuffer.read(resultBytes);
      release(dataBuffer);
      String actual = new String(resultBytes, UTF_8);
      assertThat(actual).isEqualTo(expected);
    };
  }

  @SuppressWarnings("unchecked")
  private <T> Encoder<T> encoder() {
    return (Encoder<T>) this.encoder;
  }

  /** Exception used in {@link #testEncodeError}. */
  @SuppressWarnings("serial")
  public static class InputException extends RuntimeException {}
}
