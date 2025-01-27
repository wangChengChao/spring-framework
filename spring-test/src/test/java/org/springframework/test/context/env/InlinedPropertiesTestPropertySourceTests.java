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

package org.springframework.test.context.env;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.support.TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME;

/**
 * Integration tests for {@link TestPropertySource @TestPropertySource} support with inlined
 * properties.
 *
 * @author Sam Brannen
 * @since 4.1
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestPropertySource(
    properties = {
      "",
      "foo = bar",
      "baz quux",
      "enigma: 42",
      "x.y.z = a=b=c",
      "server.url = https://example.com",
      "key.value.1: key=value",
      "key.value.2 key=value",
      "key.value.3 key:value"
    })
class InlinedPropertiesTestPropertySourceTests {

  @Autowired private ConfigurableEnvironment env;

  private String property(String key) {
    return env.getProperty(key);
  }

  @Test
  void propertiesAreAvailableInEnvironment() {
    // Simple key/value pairs
    assertThat(property("foo")).isEqualTo("bar");
    assertThat(property("baz")).isEqualTo("quux");
    assertThat(property("enigma")).isEqualTo("42");

    // Values containing key/value delimiters (":", "=", " ")
    assertThat(property("x.y.z")).isEqualTo("a=b=c");
    assertThat(property("server.url")).isEqualTo("https://example.com");
    assertThat(property("key.value.1")).isEqualTo("key=value");
    assertThat(property("key.value.2")).isEqualTo("key=value");
    assertThat(property("key.value.3")).isEqualTo("key:value");
  }

  @Test
  @SuppressWarnings("rawtypes")
  void propertyNameOrderingIsPreservedInEnvironment() {
    final String[] expectedPropertyNames =
        new String[] {
          "foo", "baz", "enigma", "x.y.z", "server.url", "key.value.1", "key.value.2", "key.value.3"
        };
    EnumerablePropertySource eps =
        (EnumerablePropertySource)
            env.getPropertySources().get(INLINED_PROPERTIES_PROPERTY_SOURCE_NAME);
    assertThat(eps.getPropertyNames()).isEqualTo(expectedPropertyNames);
  }

  // -------------------------------------------------------------------

  @Configuration
  static class Config {
    /* no user beans required for these tests */
  }
}
