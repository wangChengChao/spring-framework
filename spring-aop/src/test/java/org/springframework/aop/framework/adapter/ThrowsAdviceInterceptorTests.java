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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.rmi.ConnectException;
import java.rmi.RemoteException;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.aop.ThrowsAdvice;
import org.springframework.tests.aop.advice.MethodCounter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Rod Johnson
 * @author Chris Beams
 */
public class ThrowsAdviceInterceptorTests {

  @Test
  public void testNoHandlerMethods() {
    // should require one handler method at least
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ThrowsAdviceInterceptor(new Object()));
  }

  @Test
  public void testNotInvoked() throws Throwable {
    MyThrowsHandler th = new MyThrowsHandler();
    ThrowsAdviceInterceptor ti = new ThrowsAdviceInterceptor(th);
    Object ret = new Object();
    MethodInvocation mi = mock(MethodInvocation.class);
    given(mi.proceed()).willReturn(ret);
    assertThat(ti.invoke(mi)).isEqualTo(ret);
    assertThat(th.getCalls()).isEqualTo(0);
  }

  @Test
  public void testNoHandlerMethodForThrowable() throws Throwable {
    MyThrowsHandler th = new MyThrowsHandler();
    ThrowsAdviceInterceptor ti = new ThrowsAdviceInterceptor(th);
    assertThat(ti.getHandlerMethodCount()).isEqualTo(2);
    Exception ex = new Exception();
    MethodInvocation mi = mock(MethodInvocation.class);
    given(mi.proceed()).willThrow(ex);
    assertThatExceptionOfType(Exception.class).isThrownBy(() -> ti.invoke(mi)).isSameAs(ex);
    assertThat(th.getCalls()).isEqualTo(0);
  }

  @Test
  public void testCorrectHandlerUsed() throws Throwable {
    MyThrowsHandler th = new MyThrowsHandler();
    ThrowsAdviceInterceptor ti = new ThrowsAdviceInterceptor(th);
    FileNotFoundException ex = new FileNotFoundException();
    MethodInvocation mi = mock(MethodInvocation.class);
    given(mi.getMethod()).willReturn(Object.class.getMethod("hashCode"));
    given(mi.getThis()).willReturn(new Object());
    given(mi.proceed()).willThrow(ex);
    assertThatExceptionOfType(FileNotFoundException.class)
        .isThrownBy(() -> ti.invoke(mi))
        .isSameAs(ex);
    assertThat(th.getCalls()).isEqualTo(1);
    assertThat(th.getCalls("ioException")).isEqualTo(1);
  }

  @Test
  public void testCorrectHandlerUsedForSubclass() throws Throwable {
    MyThrowsHandler th = new MyThrowsHandler();
    ThrowsAdviceInterceptor ti = new ThrowsAdviceInterceptor(th);
    // Extends RemoteException
    ConnectException ex = new ConnectException("");
    MethodInvocation mi = mock(MethodInvocation.class);
    given(mi.proceed()).willThrow(ex);
    assertThatExceptionOfType(ConnectException.class).isThrownBy(() -> ti.invoke(mi)).isSameAs(ex);
    assertThat(th.getCalls()).isEqualTo(1);
    assertThat(th.getCalls("remoteException")).isEqualTo(1);
  }

  @Test
  public void testHandlerMethodThrowsException() throws Throwable {
    final Throwable t = new Throwable();

    @SuppressWarnings("serial")
    MyThrowsHandler th =
        new MyThrowsHandler() {
          @Override
          public void afterThrowing(RemoteException ex) throws Throwable {
            super.afterThrowing(ex);
            throw t;
          }
        };

    ThrowsAdviceInterceptor ti = new ThrowsAdviceInterceptor(th);
    // Extends RemoteException
    ConnectException ex = new ConnectException("");
    MethodInvocation mi = mock(MethodInvocation.class);
    given(mi.proceed()).willThrow(ex);
    assertThatExceptionOfType(Throwable.class).isThrownBy(() -> ti.invoke(mi)).isSameAs(t);
    assertThat(th.getCalls()).isEqualTo(1);
    assertThat(th.getCalls("remoteException")).isEqualTo(1);
  }

  @SuppressWarnings("serial")
  static class MyThrowsHandler extends MethodCounter implements ThrowsAdvice {

    // Full method signature
    public void afterThrowing(Method m, Object[] args, Object target, IOException ex) {
      count("ioException");
    }

    public void afterThrowing(RemoteException ex) throws Throwable {
      count("remoteException");
    }

    /** Not valid, wrong number of arguments */
    public void afterThrowing(Method m, Exception ex) throws Throwable {
      throw new UnsupportedOperationException("Shouldn't be called");
    }
  }
}
