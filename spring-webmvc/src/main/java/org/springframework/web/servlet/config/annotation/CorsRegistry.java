/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet.config.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.cors.CorsConfiguration;

/**
 * Assists with the registration of global, URL pattern based {@link CorsConfiguration} mappings.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.2
 * @see CorsRegistration
 */
public class CorsRegistry {

  private final List<CorsRegistration> registrations = new ArrayList<>();

  /**
   * Enable cross-origin request handling for the specified path pattern.
   *
   * <p>Exact path mapping URIs (such as {@code "/admin"}) are supported as well as Ant-style path
   * patterns (such as {@code "/admin/**"}).
   *
   * <p>By default, all origins, all headers, credentials and {@code GET}, {@code HEAD}, and {@code
   * POST} methods are allowed, and the max age is set to 30 minutes.
   *
   * <p>The following defaults are applied to the {@link CorsRegistration}:
   *
   * <ul>
   *   <li>Allow all origins.
   *   <li>Allow "simple" methods {@code GET}, {@code HEAD} and {@code POST}.
   *   <li>Allow all headers.
   *   <li>Set max age to 1800 seconds (30 minutes).
   * </ul>
   */
  public CorsRegistration addMapping(String pathPattern) {
    CorsRegistration registration = new CorsRegistration(pathPattern);
    this.registrations.add(registration);
    return registration;
  }

  /** Return the registered {@link CorsConfiguration} objects, keyed by path pattern. */
  protected Map<String, CorsConfiguration> getCorsConfigurations() {
    Map<String, CorsConfiguration> configs = new LinkedHashMap<>(this.registrations.size());
    for (CorsRegistration registration : this.registrations) {
      configs.put(registration.getPathPattern(), registration.getCorsConfiguration());
    }
    return configs;
  }
}
