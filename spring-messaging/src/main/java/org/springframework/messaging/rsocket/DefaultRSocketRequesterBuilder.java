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

package org.springframework.messaging.rsocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.rsocket.Payload;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Default implementation of {@link RSocketRequester.Builder}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 5.2
 */
final class DefaultRSocketRequesterBuilder implements RSocketRequester.Builder {

  private static final Map<String, Object> HINTS = Collections.emptyMap();

  @Nullable private MimeType dataMimeType;

  @Nullable private MimeType metadataMimeType;

  @Nullable private Object setupData;

  @Nullable private String setupRoute;

  @Nullable private Object[] setupRouteVars;

  @Nullable private Map<Object, MimeType> setupMetadata;

  @Nullable private RSocketStrategies strategies;

  private List<Consumer<RSocketStrategies.Builder>> strategiesConfigurers = new ArrayList<>();

  private List<ClientRSocketFactoryConfigurer> rsocketConfigurers = new ArrayList<>();

  @Override
  public RSocketRequester.Builder dataMimeType(@Nullable MimeType mimeType) {
    this.dataMimeType = mimeType;
    return this;
  }

  @Override
  public RSocketRequester.Builder metadataMimeType(MimeType mimeType) {
    Assert.notNull(mimeType, "`metadataMimeType` is required");
    this.metadataMimeType = mimeType;
    return this;
  }

  @Override
  public RSocketRequester.Builder setupData(Object data) {
    this.setupData = data;
    return this;
  }

  @Override
  public RSocketRequester.Builder setupRoute(String route, Object... routeVars) {
    this.setupRoute = route;
    this.setupRouteVars = routeVars;
    return this;
  }

  @Override
  public RSocketRequester.Builder setupMetadata(Object metadata, @Nullable MimeType mimeType) {
    this.setupMetadata = (this.setupMetadata == null ? new LinkedHashMap<>(4) : this.setupMetadata);
    this.setupMetadata.put(metadata, mimeType);
    return this;
  }

  @Override
  public RSocketRequester.Builder rsocketStrategies(@Nullable RSocketStrategies strategies) {
    this.strategies = strategies;
    return this;
  }

  @Override
  public RSocketRequester.Builder rsocketStrategies(
      Consumer<RSocketStrategies.Builder> configurer) {
    this.strategiesConfigurers.add(configurer);
    return this;
  }

  @Override
  public RSocketRequester.Builder rsocketFactory(ClientRSocketFactoryConfigurer configurer) {
    this.rsocketConfigurers.add(configurer);
    return this;
  }

  @Override
  public RSocketRequester.Builder apply(Consumer<RSocketRequester.Builder> configurer) {
    configurer.accept(this);
    return this;
  }

  @Override
  public Mono<RSocketRequester> connectTcp(String host, int port) {
    return connect(TcpClientTransport.create(host, port));
  }

  @Override
  public Mono<RSocketRequester> connectWebSocket(URI uri) {
    return connect(WebsocketClientTransport.create(uri));
  }

  @Override
  public Mono<RSocketRequester> connect(ClientTransport transport) {
    return Mono.defer(() -> doConnect(transport));
  }

  private Mono<RSocketRequester> doConnect(ClientTransport transport) {
    RSocketStrategies rsocketStrategies = getRSocketStrategies();
    Assert.isTrue(!rsocketStrategies.encoders().isEmpty(), "No encoders");
    Assert.isTrue(!rsocketStrategies.decoders().isEmpty(), "No decoders");

    RSocketFactory.ClientRSocketFactory factory = RSocketFactory.connect();
    this.rsocketConfigurers.forEach(configurer -> configurer.configure(factory));

    if (rsocketStrategies.dataBufferFactory() instanceof NettyDataBufferFactory) {
      factory.frameDecoder(PayloadDecoder.ZERO_COPY);
    }

    MimeType metaMimeType =
        this.metadataMimeType != null
            ? this.metadataMimeType
            : MimeTypeUtils.parseMimeType(
                WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString());

    MimeType dataMimeType = getDataMimeType(rsocketStrategies);
    factory.dataMimeType(dataMimeType.toString());
    factory.metadataMimeType(metaMimeType.toString());

    Payload setupPayload = getSetupPayload(dataMimeType, metaMimeType, rsocketStrategies);
    if (setupPayload != null) {
      factory.setupPayload(setupPayload);
    }

    return factory
        .transport(transport)
        .start()
        .map(
            rsocket ->
                new DefaultRSocketRequester(
                    rsocket, dataMimeType, metaMimeType, rsocketStrategies));
  }

  @Nullable
  private Payload getSetupPayload(
      MimeType dataMimeType, MimeType metaMimeType, RSocketStrategies strategies) {
    DataBuffer metadata = null;
    if (this.setupRoute != null || !CollectionUtils.isEmpty(this.setupMetadata)) {
      metadata =
          new MetadataEncoder(metaMimeType, strategies)
              .metadataAndOrRoute(this.setupMetadata, this.setupRoute, this.setupRouteVars)
              .encode();
    }
    DataBuffer data = null;
    if (this.setupData != null) {
      try {
        ResolvableType type = ResolvableType.forClass(this.setupData.getClass());
        Encoder<Object> encoder = strategies.encoder(type, dataMimeType);
        Assert.notNull(encoder, () -> "No encoder for " + dataMimeType + ", " + type);
        data =
            encoder.encodeValue(
                this.setupData, strategies.dataBufferFactory(), type, dataMimeType, HINTS);
      } catch (Throwable ex) {
        if (metadata != null) {
          DataBufferUtils.release(metadata);
        }
        throw ex;
      }
    }
    if (metadata == null && data == null) {
      return null;
    }
    metadata = metadata != null ? metadata : emptyBuffer(strategies);
    data = data != null ? data : emptyBuffer(strategies);
    return PayloadUtils.createPayload(data, metadata);
  }

  private DataBuffer emptyBuffer(RSocketStrategies strategies) {
    return strategies.dataBufferFactory().wrap(new byte[0]);
  }

  private RSocketStrategies getRSocketStrategies() {
    if (!this.strategiesConfigurers.isEmpty()) {
      RSocketStrategies.Builder builder =
          this.strategies != null ? this.strategies.mutate() : RSocketStrategies.builder();
      this.strategiesConfigurers.forEach(c -> c.accept(builder));
      return builder.build();
    } else {
      return this.strategies != null ? this.strategies : RSocketStrategies.builder().build();
    }
  }

  private MimeType getDataMimeType(RSocketStrategies strategies) {
    if (this.dataMimeType != null) {
      return this.dataMimeType;
    }
    // First non-basic Decoder (e.g. CBOR, Protobuf)
    for (Decoder<?> candidate : strategies.decoders()) {
      if (!isCoreCodec(candidate) && !candidate.getDecodableMimeTypes().isEmpty()) {
        return getMimeType(candidate);
      }
    }
    // First core decoder (e.g. String)
    for (Decoder<?> decoder : strategies.decoders()) {
      if (!decoder.getDecodableMimeTypes().isEmpty()) {
        return getMimeType(decoder);
      }
    }
    throw new IllegalArgumentException("Failed to select data MimeType to use.");
  }

  private static boolean isCoreCodec(Object codec) {
    return codec.getClass().getPackage().equals(StringDecoder.class.getPackage());
  }

  private static MimeType getMimeType(Decoder<?> decoder) {
    MimeType mimeType = decoder.getDecodableMimeTypes().get(0);
    return mimeType.getParameters().isEmpty()
        ? mimeType
        : new MimeType(mimeType, Collections.emptyMap());
  }
}
