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

package org.springframework.test.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link JdbcTestUtils}.
 *
 * @author Phillip Webb
 * @since 2.5.4
 * @see JdbcTestUtilsIntegrationTests
 */
@ExtendWith(MockitoExtension.class)
class JdbcTestUtilsTests {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void deleteWithoutWhereClause() throws Exception {
    given(jdbcTemplate.update("DELETE FROM person")).willReturn(10);
    int deleted = JdbcTestUtils.deleteFromTableWhere(jdbcTemplate, "person", null);
    assertThat(deleted).isEqualTo(10);
  }

  @Test
  void deleteWithWhereClause() throws Exception {
    given(jdbcTemplate.update("DELETE FROM person WHERE name = 'Bob' and age > 25")).willReturn(10);
    int deleted =
        JdbcTestUtils.deleteFromTableWhere(jdbcTemplate, "person", "name = 'Bob' and age > 25");
    assertThat(deleted).isEqualTo(10);
  }

  @Test
  void deleteWithWhereClauseAndArguments() throws Exception {
    given(jdbcTemplate.update("DELETE FROM person WHERE name = ? and age > ?", "Bob", 25))
        .willReturn(10);
    int deleted =
        JdbcTestUtils.deleteFromTableWhere(
            jdbcTemplate, "person", "name = ? and age > ?", "Bob", 25);
    assertThat(deleted).isEqualTo(10);
  }
}
