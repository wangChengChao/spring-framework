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

package org.springframework.test.context.junit4.spr9645;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.tests.transaction.CallCountingTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the behavior requested in <a
 * href="https://jira.spring.io/browse/SPR-9645">SPR-9645</a>.
 *
 * @author Sam Brannen
 * @since 3.2
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@Transactional
public class LookUpTxMgrByTypeTests {

  private static final CallCountingTransactionManager txManager =
      new CallCountingTransactionManager();

  @Configuration
  static class Config {

    @Bean
    public PlatformTransactionManager txManager() {
      return txManager;
    }
  }

  @BeforeTransaction
  public void beforeTransaction() {
    txManager.clear();
  }

  @Test
  public void transactionalTest() {
    assertThat(txManager.begun).isEqualTo(1);
    assertThat(txManager.inflight).isEqualTo(1);
    assertThat(txManager.commits).isEqualTo(0);
    assertThat(txManager.rollbacks).isEqualTo(0);
  }

  @AfterTransaction
  public void afterTransaction() {
    assertThat(txManager.begun).isEqualTo(1);
    assertThat(txManager.inflight).isEqualTo(0);
    assertThat(txManager.commits).isEqualTo(0);
    assertThat(txManager.rollbacks).isEqualTo(1);
  }
}
