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

package org.springframework.web.servlet.view.xml;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

import org.springframework.http.MediaType;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.View;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/** @author Sebastien Deleuze */
public class MappingJackson2XmlViewTests {

  private MappingJackson2XmlView view;

  private MockHttpServletRequest request;

  private MockHttpServletResponse response;

  private Context jsContext;

  private ScriptableObject jsScope;

  @BeforeEach
  public void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();

    jsContext = ContextFactory.getGlobal().enterContext();
    jsScope = jsContext.initStandardObjects();

    view = new MappingJackson2XmlView();
  }

  @Test
  public void isExposePathVars() {
    assertThat(view.isExposePathVariables()).as("Must not expose path variables").isEqualTo(false);
  }

  @Test
  public void renderSimpleMap() throws Exception {
    Map<String, Object> model = new HashMap<>();
    model.put("bindingResult", mock(BindingResult.class, "binding_result"));
    model.put("foo", "bar");

    view.setUpdateContentLength(true);
    view.render(model, request, response);

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

    assertThat(response.getContentType()).isEqualTo(MappingJackson2XmlView.DEFAULT_CONTENT_TYPE);

    String jsonResult = response.getContentAsString();
    assertThat(jsonResult.length() > 0).isTrue();
    assertThat(response.getContentLength()).isEqualTo(jsonResult.length());

    validateResult();
  }

  @Test
  public void renderWithSelectedContentType() throws Exception {
    Map<String, Object> model = new HashMap<>();
    model.put("foo", "bar");

    view.render(model, request, response);
    assertThat(response.getContentType()).isEqualTo("application/xml");

    request.setAttribute(
        View.SELECTED_CONTENT_TYPE, new MediaType("application", "vnd.example-v2+xml"));
    view.render(model, request, response);

    assertThat(response.getContentType()).isEqualTo("application/vnd.example-v2+xml");
  }

  @Test
  public void renderCaching() throws Exception {
    view.setDisableCaching(false);

    Map<String, Object> model = new HashMap<>();
    model.put("bindingResult", mock(BindingResult.class, "binding_result"));
    model.put("foo", "bar");

    view.render(model, request, response);

    assertThat(response.getHeader("Cache-Control")).isNull();
  }

  @Test
  public void renderSimpleBean() throws Exception {
    Object bean = new TestBeanSimple();
    Map<String, Object> model = new HashMap<>();
    model.put("bindingResult", mock(BindingResult.class, "binding_result"));
    model.put("foo", bean);

    view.setUpdateContentLength(true);
    view.render(model, request, response);

    assertThat(response.getContentAsString().length() > 0).isTrue();
    assertThat(response.getContentLength()).isEqualTo(response.getContentAsString().length());

    validateResult();
  }

  @Test
  public void renderWithCustomSerializerLocatedByAnnotation() throws Exception {
    Object bean = new TestBeanSimpleAnnotated();
    Map<String, Object> model = new HashMap<>();
    model.put("foo", bean);

    view.render(model, request, response);

    assertThat(response.getContentAsString().length() > 0).isTrue();
    assertThat(response.getContentAsString().contains("<testBeanSimple>custom</testBeanSimple>"))
        .isTrue();

    validateResult();
  }

  @Test
  public void renderWithCustomSerializerLocatedByFactory() throws Exception {
    SerializerFactory factory = new DelegatingSerializerFactory(null);
    XmlMapper mapper = new XmlMapper();
    mapper.setSerializerFactory(factory);
    view.setObjectMapper(mapper);

    Object bean = new TestBeanSimple();
    Map<String, Object> model = new HashMap<>();
    model.put("foo", bean);

    view.render(model, request, response);

    String result = response.getContentAsString();
    assertThat(result.length() > 0).isTrue();
    assertThat(result.contains("custom</testBeanSimple>")).isTrue();

    validateResult();
  }

  @Test
  public void renderOnlySpecifiedModelKey() throws Exception {

    view.setModelKey("bar");
    Map<String, Object> model = new HashMap<>();
    model.put("foo", "foo");
    model.put("bar", "bar");
    model.put("baz", "baz");

    view.render(model, request, response);

    String result = response.getContentAsString();
    assertThat(result.length() > 0).isTrue();
    assertThat(result.contains("foo")).isFalse();
    assertThat(result.contains("bar")).isTrue();
    assertThat(result.contains("baz")).isFalse();

    validateResult();
  }

  @Test
  public void renderModelWithMultipleKeys() throws Exception {
    Map<String, Object> model = new TreeMap<>();
    model.put("foo", "foo");
    model.put("bar", "bar");

    assertThatIllegalStateException().isThrownBy(() -> view.render(model, request, response));
  }

  @Test
  public void renderSimpleBeanWithJsonView() throws Exception {
    Object bean = new TestBeanSimple();
    Map<String, Object> model = new HashMap<>();
    model.put("bindingResult", mock(BindingResult.class, "binding_result"));
    model.put("foo", bean);
    model.put(JsonView.class.getName(), MyJacksonView1.class);

    view.setUpdateContentLength(true);
    view.render(model, request, response);

    String content = response.getContentAsString();
    assertThat(content.length() > 0).isTrue();
    assertThat(response.getContentLength()).isEqualTo(content.length());
    assertThat(content.contains("foo")).isTrue();
    assertThat(content.contains("boo")).isFalse();
    assertThat(content.contains(JsonView.class.getName())).isFalse();
  }

  private void validateResult() throws Exception {
    Object xmlResult =
        jsContext.evaluateString(
            jsScope, "(" + response.getContentAsString() + ")", "XML Stream", 1, null);
    assertThat(xmlResult).as("XML Result did not eval as valid JavaScript").isNotNull();
    assertThat(response.getContentType()).isEqualTo("application/xml");
  }

  public interface MyJacksonView1 {}

  public interface MyJacksonView2 {}

  @SuppressWarnings("unused")
  public static class TestBeanSimple {

    @JsonView(MyJacksonView1.class)
    private String property1 = "foo";

    private boolean test = false;

    @JsonView(MyJacksonView2.class)
    private String property2 = "boo";

    private TestChildBean child = new TestChildBean();

    public String getProperty1() {
      return property1;
    }

    public boolean getTest() {
      return test;
    }

    public String getProperty2() {
      return property2;
    }

    public Date getNow() {
      return new Date();
    }

    public TestChildBean getChild() {
      return child;
    }
  }

  @JsonSerialize(using = TestBeanSimpleSerializer.class)
  public static class TestBeanSimpleAnnotated extends TestBeanSimple {}

  public static class TestChildBean {

    private String value = "bar";

    private String baz = null;

    private TestBeanSimple parent = null;

    public String getValue() {
      return value;
    }

    public String getBaz() {
      return baz;
    }

    public TestBeanSimple getParent() {
      return parent;
    }

    public void setParent(TestBeanSimple parent) {
      this.parent = parent;
    }
  }

  public static class TestBeanSimpleSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeFieldName("testBeanSimple");
      jgen.writeString("custom");
      jgen.writeEndObject();
    }
  }

  @SuppressWarnings("serial")
  public static class DelegatingSerializerFactory extends BeanSerializerFactory {

    protected DelegatingSerializerFactory(SerializerFactoryConfig config) {
      super(config);
    }

    @Override
    public JsonSerializer<Object> createSerializer(SerializerProvider prov, JavaType type)
        throws JsonMappingException {
      if (type.getRawClass() == TestBeanSimple.class) {
        return new TestBeanSimpleSerializer();
      } else {
        return super.createSerializer(prov, type);
      }
    }
  }
}
