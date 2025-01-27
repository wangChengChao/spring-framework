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
package org.springframework.web.servlet.handler;

import java.util.Comparator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Test fixture for {@link MappedInterceptor} tests.
 *
 * @author Rossen Stoyanchev
 */
public class MappedInterceptorTests {

  private LocaleChangeInterceptor interceptor;

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @BeforeEach
  public void setup() {
    this.interceptor = new LocaleChangeInterceptor();
  }

  @Test
  public void noPatterns() {
    MappedInterceptor mappedInterceptor = new MappedInterceptor(null, null, this.interceptor);
    assertThat(mappedInterceptor.matches("/foo", pathMatcher)).isTrue();
  }

  @Test
  public void includePattern() {
    MappedInterceptor mappedInterceptor =
        new MappedInterceptor(new String[] {"/foo/*"}, this.interceptor);

    assertThat(mappedInterceptor.matches("/foo/bar", pathMatcher)).isTrue();
    assertThat(mappedInterceptor.matches("/bar/foo", pathMatcher)).isFalse();
  }

  @Test
  public void includePatternWithMatrixVariables() {
    MappedInterceptor mappedInterceptor =
        new MappedInterceptor(new String[] {"/foo*/*"}, this.interceptor);
    assertThat(mappedInterceptor.matches("/foo;q=1/bar;s=2", pathMatcher)).isTrue();
  }

  @Test
  public void excludePattern() {
    MappedInterceptor mappedInterceptor =
        new MappedInterceptor(null, new String[] {"/admin/**"}, this.interceptor);

    assertThat(mappedInterceptor.matches("/foo", pathMatcher)).isTrue();
    assertThat(mappedInterceptor.matches("/admin/foo", pathMatcher)).isFalse();
  }

  @Test
  public void includeAndExcludePatterns() {
    MappedInterceptor mappedInterceptor =
        new MappedInterceptor(new String[] {"/**"}, new String[] {"/admin/**"}, this.interceptor);

    assertThat(mappedInterceptor.matches("/foo", pathMatcher)).isTrue();
    assertThat(mappedInterceptor.matches("/admin/foo", pathMatcher)).isFalse();
  }

  @Test
  public void customPathMatcher() {
    MappedInterceptor mappedInterceptor =
        new MappedInterceptor(new String[] {"/foo/[0-9]*"}, this.interceptor);
    mappedInterceptor.setPathMatcher(new TestPathMatcher());

    assertThat(mappedInterceptor.matches("/foo/123", pathMatcher)).isTrue();
    assertThat(mappedInterceptor.matches("/foo/bar", pathMatcher)).isFalse();
  }

  @Test
  public void preHandle() throws Exception {
    HandlerInterceptor interceptor = mock(HandlerInterceptor.class);
    MappedInterceptor mappedInterceptor = new MappedInterceptor(new String[] {"/**"}, interceptor);
    mappedInterceptor.preHandle(
        mock(HttpServletRequest.class), mock(HttpServletResponse.class), null);

    then(interceptor)
        .should()
        .preHandle(any(HttpServletRequest.class), any(HttpServletResponse.class), any());
  }

  @Test
  public void postHandle() throws Exception {
    HandlerInterceptor interceptor = mock(HandlerInterceptor.class);
    MappedInterceptor mappedInterceptor = new MappedInterceptor(new String[] {"/**"}, interceptor);
    mappedInterceptor.postHandle(
        mock(HttpServletRequest.class),
        mock(HttpServletResponse.class),
        null,
        mock(ModelAndView.class));

    then(interceptor).should().postHandle(any(), any(), any(), any());
  }

  @Test
  public void afterCompletion() throws Exception {
    HandlerInterceptor interceptor = mock(HandlerInterceptor.class);
    MappedInterceptor mappedInterceptor = new MappedInterceptor(new String[] {"/**"}, interceptor);
    mappedInterceptor.afterCompletion(
        mock(HttpServletRequest.class),
        mock(HttpServletResponse.class),
        null,
        mock(Exception.class));

    then(interceptor).should().afterCompletion(any(), any(), any(), any());
  }

  public static class TestPathMatcher implements PathMatcher {

    @Override
    public boolean isPattern(String path) {
      return false;
    }

    @Override
    public boolean match(String pattern, String path) {
      return path.matches(pattern);
    }

    @Override
    public boolean matchStart(String pattern, String path) {
      return false;
    }

    @Override
    public String extractPathWithinPattern(String pattern, String path) {
      return null;
    }

    @Override
    public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
      return null;
    }

    @Override
    public Comparator<String> getPatternComparator(String path) {
      return null;
    }

    @Override
    public String combine(String pattern1, String pattern2) {
      return null;
    }
  }
}
