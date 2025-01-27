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

package org.springframework.web.reactive.result.view.freemarker;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.web.test.server.MockServerWebExchange;
import org.springframework.tests.sample.beans.TestBean;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.result.view.BindStatus;
import org.springframework.web.reactive.result.view.DummyMacroRequestContext;
import org.springframework.web.reactive.result.view.RequestContext;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Darren Davison
 * @author Juergen Hoeller
 * @author Issam El-atif
 * @author Sam Brannen
 * @since 5.2
 */
public class FreeMarkerMacroTests {

  private static final String TEMPLATE_FILE = "test-macro.ftl";

  private final MockServerWebExchange exchange =
      MockServerWebExchange.from(MockServerHttpRequest.get("/path"));

  private final GenericApplicationContext applicationContext = new GenericApplicationContext();

  private Configuration freeMarkerConfig;

  @BeforeEach
  public void setUp() throws Exception {
    this.applicationContext.refresh();

    FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
    configurer.setTemplateLoaderPaths(
        "classpath:/", "file://" + System.getProperty("java.io.tmpdir"));
    this.freeMarkerConfig = configurer.createConfiguration();
  }

  @Test
  public void springMacroRequestContextIsAutomaticallyExposedAsModelAttribute() throws Exception {
    storeTemplateInTempDir("<@spring.bind \"testBean.name\"/>\nHi ${spring.status.value}");

    FreeMarkerView view =
        new FreeMarkerView() {

          @Override
          protected Mono<Void> renderInternal(
              Map<String, Object> renderAttributes,
              MediaType contentType,
              ServerWebExchange exchange) {

            Object value = renderAttributes.get(SPRING_MACRO_REQUEST_CONTEXT_ATTRIBUTE);
            assertThat(value).isInstanceOf(RequestContext.class);
            BindStatus status = ((RequestContext) value).getBindStatus("testBean.name");
            assertThat(status.getExpression()).isEqualTo("name");
            assertThat(status.getValue()).isEqualTo("Dilbert");

            return super.renderInternal(renderAttributes, contentType, exchange);
          }
        };

    view.setApplicationContext(this.applicationContext);
    view.setBeanName("myView");
    view.setUrl("tmp.ftl");
    view.setConfiguration(this.freeMarkerConfig);

    view.render(singletonMap("testBean", new TestBean("Dilbert", 99)), null, this.exchange)
        .subscribe();

    assertThat(getOutput()).containsExactly("Hi Dilbert");
  }

  @Test
  public void name() throws Exception {
    assertThat(getMacroOutput("NAME")).containsExactly("Darren");
  }

  @Test
  public void age() throws Exception {
    assertThat(getMacroOutput("AGE")).containsExactly("99");
  }

  @Test
  public void message() throws Exception {
    assertThat(getMacroOutput("MESSAGE")).containsExactly("Howdy Mundo");
  }

  @Test
  public void defaultMessage() throws Exception {
    assertThat(getMacroOutput("DEFAULTMESSAGE")).containsExactly("hi planet");
  }

  @Test
  public void messageArgs() throws Exception {
    assertThat(getMacroOutput("MESSAGEARGS")).containsExactly("Howdy[World]");
  }

  @Test
  public void messageArgsWithDefaultMessage() throws Exception {
    assertThat(getMacroOutput("MESSAGEARGSWITHDEFAULTMESSAGE")).containsExactly("Hi");
  }

  @Test
  public void url() throws Exception {
    assertThat(getMacroOutput("URL")).containsExactly("/springtest/aftercontext.html");
  }

  @Test
  public void urlParams() throws Exception {
    assertThat(getMacroOutput("URLPARAMS"))
        .containsExactly("/springtest/aftercontext/bar?spam=bucket");
  }

  @Test
  public void formInput() throws Exception {
    assertThat(getMacroOutput("FORM1"))
        .containsExactly("<input type=\"text\" id=\"name\" name=\"name\" value=\"Darren\" >");
  }

  @Test
  public void formInputWithCss() throws Exception {
    assertThat(getMacroOutput("FORM2"))
        .containsExactly(
            "<input type=\"text\" id=\"name\" name=\"name\" value=\"Darren\" class=\"myCssClass\" >");
  }

  @Test
  public void formTextarea() throws Exception {
    assertThat(getMacroOutput("FORM3"))
        .containsExactly("<textarea id=\"name\" name=\"name\" >Darren</textarea>");
  }

  @Test
  public void formTextareaWithCustomRowsAndColumns() throws Exception {
    assertThat(getMacroOutput("FORM4"))
        .containsExactly("<textarea id=\"name\" name=\"name\" rows=10 cols=30>Darren</textarea>");
  }

  @Test
  public void formSingleSelectFromMap() throws Exception {
    assertThat(getMacroOutput("FORM5"))
        .containsExactly(
            "<select id=\"name\" name=\"name\" >", //
            "<option value=\"Rob&amp;Harrop\">Rob Harrop</option>", //
            "<option value=\"John\">John Doe</option>", //
            "<option value=\"Fred\">Fred Bloggs</option>", //
            "<option value=\"Darren\" selected=\"selected\">Darren Davison</option>", //
            "</select>");
  }

  @Test
  public void formSingleSelectFromList() throws Exception {
    assertThat(getMacroOutput("FORM14"))
        .containsExactly(
            "<select id=\"name\" name=\"name\" >", //
            "<option value=\"Rob Harrop\">Rob Harrop</option>", //
            "<option value=\"Darren Davison\">Darren Davison</option>", //
            "<option value=\"John Doe\">John Doe</option>", //
            "<option value=\"Fred Bloggs\">Fred Bloggs</option>", //
            "</select>");
  }

  @Test
  public void formMultiSelect() throws Exception {
    assertThat(getMacroOutput("FORM6"))
        .containsExactly(
            "<select multiple=\"multiple\" id=\"spouses\" name=\"spouses\" >", //
            "<option value=\"Rob&amp;Harrop\">Rob Harrop</option>", //
            "<option value=\"John\">John Doe</option>", //
            "<option value=\"Fred\" selected=\"selected\">Fred Bloggs</option>", //
            "<option value=\"Darren\">Darren Davison</option>", //
            "</select>");
  }

  @Test
  public void formRadioButtons() throws Exception {
    assertThat(getMacroOutput("FORM7"))
        .containsExactly(
            "<input type=\"radio\" id=\"name0\" name=\"name\" value=\"Rob&amp;Harrop\" >", //
            "<label for=\"name0\">Rob Harrop</label>", //
            "<input type=\"radio\" id=\"name1\" name=\"name\" value=\"John\" >", //
            "<label for=\"name1\">John Doe</label>", //
            "<input type=\"radio\" id=\"name2\" name=\"name\" value=\"Fred\" >", //
            "<label for=\"name2\">Fred Bloggs</label>", //
            "<input type=\"radio\" id=\"name3\" name=\"name\" value=\"Darren\" checked=\"checked\" >", //
            "<label for=\"name3\">Darren Davison</label>");
  }

  @Test
  public void formCheckboxForStringProperty() throws Exception {
    assertThat(getMacroOutput("FORM15"))
        .containsExactly(
            "<input type=\"hidden\" name=\"_name\" value=\"on\"/>",
            "<input type=\"checkbox\" id=\"name\" name=\"name\" />");
  }

  @Test
  public void formCheckboxForBooleanProperty() throws Exception {
    assertThat(getMacroOutput("FORM16"))
        .containsExactly(
            "<input type=\"hidden\" name=\"_jedi\" value=\"on\"/>",
            "<input type=\"checkbox\" id=\"jedi\" name=\"jedi\" checked=\"checked\" />");
  }

  @Test
  public void formCheckboxForNestedPath() throws Exception {
    assertThat(getMacroOutput("FORM18"))
        .containsExactly(
            "<input type=\"hidden\" name=\"_spouses[0].jedi\" value=\"on\"/>",
            "<input type=\"checkbox\" id=\"spouses0.jedi\" name=\"spouses[0].jedi\" checked=\"checked\" />");
  }

  @Test
  public void formCheckboxForStringArray() throws Exception {
    assertThat(getMacroOutput("FORM8"))
        .containsExactly(
            "<input type=\"checkbox\" id=\"stringArray0\" name=\"stringArray\" value=\"Rob&amp;Harrop\" >", //
            "<label for=\"stringArray0\">Rob Harrop</label>", //
            "<input type=\"checkbox\" id=\"stringArray1\" name=\"stringArray\" value=\"John\" checked=\"checked\" >", //
            "<label for=\"stringArray1\">John Doe</label>", //
            "<input type=\"checkbox\" id=\"stringArray2\" name=\"stringArray\" value=\"Fred\" checked=\"checked\" >", //
            "<label for=\"stringArray2\">Fred Bloggs</label>", //
            "<input type=\"checkbox\" id=\"stringArray3\" name=\"stringArray\" value=\"Darren\" >", //
            "<label for=\"stringArray3\">Darren Davison</label>", //
            "<input type=\"hidden\" name=\"_stringArray\" value=\"on\"/>");
  }

  @Test
  public void formPasswordInput() throws Exception {
    assertThat(getMacroOutput("FORM9"))
        .containsExactly("<input type=\"password\" id=\"name\" name=\"name\" value=\"\" >");
  }

  @Test
  public void formHiddenInput() throws Exception {
    assertThat(getMacroOutput("FORM10"))
        .containsExactly("<input type=\"hidden\" id=\"name\" name=\"name\" value=\"Darren\" >");
  }

  @Test
  public void formInputText() throws Exception {
    assertThat(getMacroOutput("FORM11"))
        .containsExactly("<input type=\"text\" id=\"name\" name=\"name\" value=\"Darren\" >");
  }

  @Test
  public void formInputHidden() throws Exception {
    assertThat(getMacroOutput("FORM12"))
        .containsExactly("<input type=\"hidden\" id=\"name\" name=\"name\" value=\"Darren\" >");
  }

  @Test
  public void formInputPassword() throws Exception {
    assertThat(getMacroOutput("FORM13"))
        .containsExactly("<input type=\"password\" id=\"name\" name=\"name\" value=\"\" >");
  }

  @Test
  public void forInputWithNestedPath() throws Exception {
    assertThat(getMacroOutput("FORM17"))
        .containsExactly(
            "<input type=\"text\" id=\"spouses0.name\" name=\"spouses[0].name\" value=\"Fred\" >");
  }

  private List<String> getMacroOutput(String name) throws Exception {
    String macro = fetchMacro(name);
    assertThat(macro).isNotNull();
    storeTemplateInTempDir(macro);

    Map<String, String> msgMap = new HashMap<>();
    msgMap.put("hello", "Howdy");
    msgMap.put("world", "Mundo");

    TestBean darren = new TestBean("Darren", 99);
    TestBean fred = new TestBean("Fred");
    fred.setJedi(true);
    darren.setSpouse(fred);
    darren.setJedi(true);
    darren.setStringArray(new String[] {"John", "Fred"});

    Map<String, String> names = new HashMap<>();
    names.put("Darren", "Darren Davison");
    names.put("John", "John Doe");
    names.put("Fred", "Fred Bloggs");
    names.put("Rob&Harrop", "Rob Harrop");

    ModelMap model = new ExtendedModelMap();
    DummyMacroRequestContext rc =
        new DummyMacroRequestContext(this.exchange, model, this.applicationContext);
    rc.setMessageMap(msgMap);
    rc.setContextPath("/springtest");

    model.put("command", darren);
    model.put(FreeMarkerView.SPRING_MACRO_REQUEST_CONTEXT_ATTRIBUTE, rc);
    model.put("msgArgs", new Object[] {"World"});
    model.put("nameOptionMap", names);
    model.put("options", names.values());

    FreeMarkerView view = new FreeMarkerView();
    view.setApplicationContext(this.applicationContext);
    view.setBeanName("myView");
    view.setUrl("tmp.ftl");
    view.setExposeSpringMacroHelpers(false);
    view.setConfiguration(freeMarkerConfig);

    view.render(model, null, this.exchange).subscribe();

    return getOutput();
  }

  private String fetchMacro(String name) throws Exception {
    ClassPathResource resource = new ClassPathResource(TEMPLATE_FILE, getClass());
    assertThat(resource.exists()).isTrue();
    String all = FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
    all = all.replace("\r\n", "\n");
    String[] macros = StringUtils.delimitedListToStringArray(all, "\n\n");
    for (String macro : macros) {
      if (macro.startsWith(name)) {
        return macro.substring(macro.indexOf("\n")).trim();
      }
    }
    return null;
  }

  private void storeTemplateInTempDir(String macro) throws IOException {
    FileSystemResource resource =
        new FileSystemResource(System.getProperty("java.io.tmpdir") + "/tmp.ftl");
    FileCopyUtils.copy(
        "<#import \"spring.ftl\" as spring />\n" + macro, new FileWriter(resource.getPath()));
  }

  private List<String> getOutput() {
    String output = this.exchange.getResponse().getBodyAsString().block();
    String[] lines = output.replace("\r\n", "\n").replaceAll(" +", " ").split("\n");
    return Arrays.stream(lines).map(String::trim).filter(line -> !line.isEmpty()).collect(toList());
  }
}
