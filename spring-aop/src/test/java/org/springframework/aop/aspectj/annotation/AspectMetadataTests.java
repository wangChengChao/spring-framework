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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.junit.jupiter.api.Test;
import test.aop.PerTargetAspect;

import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactoryTests.ExceptionAspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @since 2.0
 * @author Rod Johnson
 * @author Chris Beams
 */
public class AspectMetadataTests {

  @Test
  public void testNotAnAspect() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AspectMetadata(String.class, "someBean"));
  }

  @Test
  public void testSingletonAspect() {
    AspectMetadata am = new AspectMetadata(ExceptionAspect.class, "someBean");
    assertThat(am.isPerThisOrPerTarget()).isFalse();
    assertThat(am.getPerClausePointcut()).isSameAs(Pointcut.TRUE);
    assertThat(am.getAjType().getPerClause().getKind()).isEqualTo(PerClauseKind.SINGLETON);
  }

  @Test
  public void testPerTargetAspect() {
    AspectMetadata am = new AspectMetadata(PerTargetAspect.class, "someBean");
    assertThat(am.isPerThisOrPerTarget()).isTrue();
    assertThat(am.getPerClausePointcut()).isNotSameAs(Pointcut.TRUE);
    assertThat(am.getAjType().getPerClause().getKind()).isEqualTo(PerClauseKind.PERTARGET);
  }

  @Test
  public void testPerThisAspect() {
    AspectMetadata am = new AspectMetadata(PerThisAspect.class, "someBean");
    assertThat(am.isPerThisOrPerTarget()).isTrue();
    assertThat(am.getPerClausePointcut()).isNotSameAs(Pointcut.TRUE);
    assertThat(am.getAjType().getPerClause().getKind()).isEqualTo(PerClauseKind.PERTHIS);
  }
}
