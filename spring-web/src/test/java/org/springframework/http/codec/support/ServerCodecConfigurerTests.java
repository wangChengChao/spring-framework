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

package org.springframework.http.codec.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.ByteBufferDecoder;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.DataBufferDecoder;
import org.springframework.core.codec.DataBufferEncoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageReader;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.json.Jackson2SmileDecoder;
import org.springframework.http.codec.json.Jackson2SmileEncoder;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.SynchronossPartHttpMessageReader;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufHttpMessageWriter;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.ResolvableType.forClass;

/**
 * Unit tests for {@link ServerCodecConfigurer}.
 *
 * @author Rossen Stoyanchev
 */
public class ServerCodecConfigurerTests {

  private final ServerCodecConfigurer configurer = new DefaultServerCodecConfigurer();

  private final AtomicInteger index = new AtomicInteger(0);

  @Test
  public void defaultReaders() {
    List<HttpMessageReader<?>> readers = this.configurer.getReaders();
    assertThat(readers.size()).isEqualTo(13);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(ByteArrayDecoder.class);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(ByteBufferDecoder.class);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(DataBufferDecoder.class);
    assertThat(readers.get(this.index.getAndIncrement()).getClass())
        .isEqualTo(ResourceHttpMessageReader.class);
    assertStringDecoder(getNextDecoder(readers), true);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(ProtobufDecoder.class);
    assertThat(readers.get(this.index.getAndIncrement()).getClass())
        .isEqualTo(FormHttpMessageReader.class);
    assertThat(readers.get(this.index.getAndIncrement()).getClass())
        .isEqualTo(SynchronossPartHttpMessageReader.class);
    assertThat(readers.get(this.index.getAndIncrement()).getClass())
        .isEqualTo(MultipartHttpMessageReader.class);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(Jackson2JsonDecoder.class);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(Jackson2SmileDecoder.class);
    assertThat(getNextDecoder(readers).getClass()).isEqualTo(Jaxb2XmlDecoder.class);
    assertStringDecoder(getNextDecoder(readers), false);
  }

  @Test
  public void defaultWriters() {
    List<HttpMessageWriter<?>> writers = this.configurer.getWriters();
    assertThat(writers.size()).isEqualTo(11);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(ByteArrayEncoder.class);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(ByteBufferEncoder.class);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(DataBufferEncoder.class);
    assertThat(writers.get(index.getAndIncrement()).getClass())
        .isEqualTo(ResourceHttpMessageWriter.class);
    assertStringEncoder(getNextEncoder(writers), true);
    assertThat(writers.get(index.getAndIncrement()).getClass())
        .isEqualTo(ProtobufHttpMessageWriter.class);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(Jackson2JsonEncoder.class);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(Jackson2SmileEncoder.class);
    assertThat(getNextEncoder(writers).getClass()).isEqualTo(Jaxb2XmlEncoder.class);
    assertSseWriter(writers);
    assertStringEncoder(getNextEncoder(writers), false);
  }

  @Test
  public void jackson2EncoderOverride() {
    Jackson2JsonEncoder encoder = new Jackson2JsonEncoder();
    this.configurer.defaultCodecs().jackson2JsonEncoder(encoder);

    assertThat(
            this.configurer.getWriters().stream()
                .filter(writer -> ServerSentEventHttpMessageWriter.class.equals(writer.getClass()))
                .map(writer -> (ServerSentEventHttpMessageWriter) writer)
                .findFirst()
                .map(ServerSentEventHttpMessageWriter::getEncoder)
                .filter(e -> e == encoder)
                .orElse(null))
        .isSameAs(encoder);
  }

  private Decoder<?> getNextDecoder(List<HttpMessageReader<?>> readers) {
    HttpMessageReader<?> reader = readers.get(this.index.getAndIncrement());
    assertThat(reader.getClass()).isEqualTo(DecoderHttpMessageReader.class);
    return ((DecoderHttpMessageReader<?>) reader).getDecoder();
  }

  private Encoder<?> getNextEncoder(List<HttpMessageWriter<?>> writers) {
    HttpMessageWriter<?> writer = writers.get(this.index.getAndIncrement());
    assertThat(writer.getClass()).isEqualTo(EncoderHttpMessageWriter.class);
    return ((EncoderHttpMessageWriter<?>) writer).getEncoder();
  }

  @SuppressWarnings("unchecked")
  private void assertStringDecoder(Decoder<?> decoder, boolean textOnly) {
    assertThat(decoder.getClass()).isEqualTo(StringDecoder.class);
    assertThat(decoder.canDecode(forClass(String.class), MimeTypeUtils.TEXT_PLAIN)).isTrue();
    Object expected = !textOnly;
    assertThat(decoder.canDecode(forClass(String.class), MediaType.TEXT_EVENT_STREAM))
        .isEqualTo(expected);

    Flux<String> flux =
        (Flux<String>)
            decoder.decode(
                Flux.just(
                    new DefaultDataBufferFactory()
                        .wrap("line1\nline2".getBytes(StandardCharsets.UTF_8))),
                ResolvableType.forClass(String.class),
                MimeTypeUtils.TEXT_PLAIN,
                Collections.emptyMap());

    assertThat(flux.collectList().block(Duration.ZERO)).isEqualTo(Arrays.asList("line1", "line2"));
  }

  private void assertStringEncoder(Encoder<?> encoder, boolean textOnly) {
    assertThat(encoder.getClass()).isEqualTo(CharSequenceEncoder.class);
    assertThat(encoder.canEncode(forClass(String.class), MimeTypeUtils.TEXT_PLAIN)).isTrue();
    Object expected = !textOnly;
    assertThat(encoder.canEncode(forClass(String.class), MediaType.TEXT_EVENT_STREAM))
        .isEqualTo(expected);
  }

  private void assertSseWriter(List<HttpMessageWriter<?>> writers) {
    HttpMessageWriter<?> writer = writers.get(this.index.getAndIncrement());
    assertThat(writer.getClass()).isEqualTo(ServerSentEventHttpMessageWriter.class);
    Encoder<?> encoder = ((ServerSentEventHttpMessageWriter) writer).getEncoder();
    assertThat(encoder).isNotNull();
    assertThat(encoder.getClass()).isEqualTo(Jackson2JsonEncoder.class);
  }
}
