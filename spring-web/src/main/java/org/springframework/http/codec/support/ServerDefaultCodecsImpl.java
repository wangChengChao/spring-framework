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

import java.util.List;

import org.springframework.core.codec.Encoder;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.SynchronossPartHttpMessageReader;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Default implementation of {@link ServerCodecConfigurer.ServerDefaultCodecs}.
 *
 * @author Rossen Stoyanchev
 */
class ServerDefaultCodecsImpl extends BaseDefaultCodecs
    implements ServerCodecConfigurer.ServerDefaultCodecs {

  private static final boolean synchronossMultipartPresent =
      ClassUtils.isPresent(
          "org.synchronoss.cloud.nio.multipart.NioMultipartParser",
          DefaultServerCodecConfigurer.class.getClassLoader());

  @Nullable private Encoder<?> sseEncoder;

  @Override
  public void serverSentEventEncoder(Encoder<?> encoder) {
    this.sseEncoder = encoder;
  }

  @Override
  protected void extendTypedReaders(List<HttpMessageReader<?>> typedReaders) {
    if (synchronossMultipartPresent) {
      boolean enable = isEnableLoggingRequestDetails();

      SynchronossPartHttpMessageReader partReader = new SynchronossPartHttpMessageReader();
      partReader.setEnableLoggingRequestDetails(enable);
      typedReaders.add(partReader);

      MultipartHttpMessageReader reader = new MultipartHttpMessageReader(partReader);
      reader.setEnableLoggingRequestDetails(enable);
      typedReaders.add(reader);
    }
  }

  @Override
  protected void extendObjectWriters(List<HttpMessageWriter<?>> objectWriters) {
    objectWriters.add(new ServerSentEventHttpMessageWriter(getSseEncoder()));
  }

  @Nullable
  private Encoder<?> getSseEncoder() {
    return this.sseEncoder != null
        ? this.sseEncoder
        : jackson2Present ? getJackson2JsonEncoder() : null;
  }
}
