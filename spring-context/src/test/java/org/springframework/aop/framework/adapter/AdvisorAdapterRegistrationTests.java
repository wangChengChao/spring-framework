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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.Advisor;
import org.springframework.aop.BeforeAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.tests.sample.beans.ITestBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * TestCase for AdvisorAdapterRegistrationManager mechanism.
 *
 * @author Dmitriy Kopylenko
 * @author Chris Beams
 */
public class AdvisorAdapterRegistrationTests {

  @BeforeEach
  @AfterEach
  public void resetGlobalAdvisorAdapterRegistry() {
    GlobalAdvisorAdapterRegistry.reset();
  }

  @Test
  public void testAdvisorAdapterRegistrationManagerNotPresentInContext() {
    ClassPathXmlApplicationContext ctx =
        new ClassPathXmlApplicationContext(
            getClass().getSimpleName() + "-without-bpp.xml", getClass());
    ITestBean tb = (ITestBean) ctx.getBean("testBean");
    // just invoke any method to see if advice fired
    assertThatExceptionOfType(UnknownAdviceTypeException.class).isThrownBy(tb::getName);
    assertThat(getAdviceImpl(tb).getInvocationCounter()).isZero();
  }

  @Test
  public void testAdvisorAdapterRegistrationManagerPresentInContext() {
    ClassPathXmlApplicationContext ctx =
        new ClassPathXmlApplicationContext(
            getClass().getSimpleName() + "-with-bpp.xml", getClass());
    ITestBean tb = (ITestBean) ctx.getBean("testBean");
    // just invoke any method to see if advice fired
    tb.getName();
    getAdviceImpl(tb).getInvocationCounter();
  }

  private SimpleBeforeAdviceImpl getAdviceImpl(ITestBean tb) {
    Advised advised = (Advised) tb;
    Advisor advisor = advised.getAdvisors()[0];
    return (SimpleBeforeAdviceImpl) advisor.getAdvice();
  }
}

interface SimpleBeforeAdvice extends BeforeAdvice {

  void before() throws Throwable;
}

@SuppressWarnings("serial")
class SimpleBeforeAdviceAdapter implements AdvisorAdapter, Serializable {

  @Override
  public boolean supportsAdvice(Advice advice) {
    return (advice instanceof SimpleBeforeAdvice);
  }

  @Override
  public MethodInterceptor getInterceptor(Advisor advisor) {
    SimpleBeforeAdvice advice = (SimpleBeforeAdvice) advisor.getAdvice();
    return new SimpleBeforeAdviceInterceptor(advice);
  }
}

class SimpleBeforeAdviceImpl implements SimpleBeforeAdvice {

  private int invocationCounter;

  @Override
  public void before() throws Throwable {
    ++invocationCounter;
  }

  public int getInvocationCounter() {
    return invocationCounter;
  }
}

final class SimpleBeforeAdviceInterceptor implements MethodInterceptor {

  private SimpleBeforeAdvice advice;

  public SimpleBeforeAdviceInterceptor(SimpleBeforeAdvice advice) {
    this.advice = advice;
  }

  @Override
  public Object invoke(MethodInvocation mi) throws Throwable {
    advice.before();
    return mi.proceed();
  }
}
