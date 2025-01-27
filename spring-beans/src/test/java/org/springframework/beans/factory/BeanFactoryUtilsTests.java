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

package org.springframework.beans.factory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.core.io.Resource;
import org.springframework.tests.sample.beans.AnnotatedBean;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.IndexedTestBean;
import org.springframework.tests.sample.beans.TestAnnotation;
import org.springframework.tests.sample.beans.TestBean;
import org.springframework.tests.sample.beans.factory.DummyFactory;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.tests.TestResourceUtils.qualifiedResource;

/**
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 04.07.2003
 */
public class BeanFactoryUtilsTests {

  private static final Class<?> CLASS = BeanFactoryUtilsTests.class;
  private static final Resource ROOT_CONTEXT = qualifiedResource(CLASS, "root.xml");
  private static final Resource MIDDLE_CONTEXT = qualifiedResource(CLASS, "middle.xml");
  private static final Resource LEAF_CONTEXT = qualifiedResource(CLASS, "leaf.xml");
  private static final Resource DEPENDENT_BEANS_CONTEXT =
      qualifiedResource(CLASS, "dependentBeans.xml");

  private DefaultListableBeanFactory listableBeanFactory;

  private DefaultListableBeanFactory dependentBeansFactory;

  @BeforeEach
  public void setUp() {
    // Interesting hierarchical factory to test counts.
    // Slow to read so we cache it.

    DefaultListableBeanFactory grandParent = new DefaultListableBeanFactory();
    new XmlBeanDefinitionReader(grandParent).loadBeanDefinitions(ROOT_CONTEXT);
    DefaultListableBeanFactory parent = new DefaultListableBeanFactory(grandParent);
    new XmlBeanDefinitionReader(parent).loadBeanDefinitions(MIDDLE_CONTEXT);
    DefaultListableBeanFactory child = new DefaultListableBeanFactory(parent);
    new XmlBeanDefinitionReader(child).loadBeanDefinitions(LEAF_CONTEXT);

    this.dependentBeansFactory = new DefaultListableBeanFactory();
    new XmlBeanDefinitionReader(this.dependentBeansFactory)
        .loadBeanDefinitions(DEPENDENT_BEANS_CONTEXT);
    dependentBeansFactory.preInstantiateSingletons();
    this.listableBeanFactory = child;
  }

  @Test
  public void testHierarchicalCountBeansWithNonHierarchicalFactory() {
    StaticListableBeanFactory lbf = new StaticListableBeanFactory();
    lbf.addBean("t1", new TestBean());
    lbf.addBean("t2", new TestBean());
    assertThat(BeanFactoryUtils.countBeansIncludingAncestors(lbf) == 2).isTrue();
  }

  /** Check that override doesn't count as two separate beans. */
  @Test
  public void testHierarchicalCountBeansWithOverride() throws Exception {
    // Leaf count
    assertThat(this.listableBeanFactory.getBeanDefinitionCount() == 1).isTrue();
    // Count minus duplicate
    assertThat(BeanFactoryUtils.countBeansIncludingAncestors(this.listableBeanFactory) == 8)
        .as(
            "Should count 8 beans, not "
                + BeanFactoryUtils.countBeansIncludingAncestors(this.listableBeanFactory))
        .isTrue();
  }

  @Test
  public void testHierarchicalNamesWithNoMatch() throws Exception {
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                this.listableBeanFactory, NoOp.class));
    assertThat(names.size()).isEqualTo(0);
  }

  @Test
  public void testHierarchicalNamesWithMatchOnlyInRoot() throws Exception {
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                this.listableBeanFactory, IndexedTestBean.class));
    assertThat(names.size()).isEqualTo(1);
    assertThat(names.contains("indexedBean")).isTrue();
    // Distinguish from default ListableBeanFactory behavior
    assertThat(listableBeanFactory.getBeanNamesForType(IndexedTestBean.class).length == 0).isTrue();
  }

  @Test
  public void testGetBeanNamesForTypeWithOverride() throws Exception {
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                this.listableBeanFactory, ITestBean.class));
    // includes 2 TestBeans from FactoryBeans (DummyFactory definitions)
    assertThat(names.size()).isEqualTo(4);
    assertThat(names.contains("test")).isTrue();
    assertThat(names.contains("test3")).isTrue();
    assertThat(names.contains("testFactory1")).isTrue();
    assertThat(names.contains("testFactory2")).isTrue();
  }

  @Test
  public void testNoBeansOfType() {
    StaticListableBeanFactory lbf = new StaticListableBeanFactory();
    lbf.addBean("foo", new Object());
    Map<String, ?> beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, ITestBean.class, true, false);
    assertThat(beans.isEmpty()).isTrue();
  }

  @Test
  public void testFindsBeansOfTypeWithStaticFactory() {
    StaticListableBeanFactory lbf = new StaticListableBeanFactory();
    TestBean t1 = new TestBean();
    TestBean t2 = new TestBean();
    DummyFactory t3 = new DummyFactory();
    DummyFactory t4 = new DummyFactory();
    t4.setSingleton(false);
    lbf.addBean("t1", t1);
    lbf.addBean("t2", t2);
    lbf.addBean("t3", t3);
    lbf.addBean("t4", t4);

    Map<String, ?> beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, ITestBean.class, true, true);
    assertThat(beans.size()).isEqualTo(4);
    assertThat(beans.get("t1")).isEqualTo(t1);
    assertThat(beans.get("t2")).isEqualTo(t2);
    assertThat(beans.get("t3")).isEqualTo(t3.getObject());
    boolean condition = beans.get("t4") instanceof TestBean;
    assertThat(condition).isTrue();

    beans = BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, DummyFactory.class, true, true);
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("&t3")).isEqualTo(t3);
    assertThat(beans.get("&t4")).isEqualTo(t4);

    beans = BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, FactoryBean.class, true, true);
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("&t3")).isEqualTo(t3);
    assertThat(beans.get("&t4")).isEqualTo(t4);
  }

  @Test
  public void testFindsBeansOfTypeWithDefaultFactory() {
    Object test3 = this.listableBeanFactory.getBean("test3");
    Object test = this.listableBeanFactory.getBean("test");

    TestBean t1 = new TestBean();
    TestBean t2 = new TestBean();
    DummyFactory t3 = new DummyFactory();
    DummyFactory t4 = new DummyFactory();
    t4.setSingleton(false);
    this.listableBeanFactory.registerSingleton("t1", t1);
    this.listableBeanFactory.registerSingleton("t2", t2);
    this.listableBeanFactory.registerSingleton("t3", t3);
    this.listableBeanFactory.registerSingleton("t4", t4);

    Map<String, ?> beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, true, false);
    assertThat(beans.size()).isEqualTo(6);
    assertThat(beans.get("test3")).isEqualTo(test3);
    assertThat(beans.get("test")).isEqualTo(test);
    assertThat(beans.get("t1")).isEqualTo(t1);
    assertThat(beans.get("t2")).isEqualTo(t2);
    assertThat(beans.get("t3")).isEqualTo(t3.getObject());
    boolean condition2 = beans.get("t4") instanceof TestBean;
    assertThat(condition2).isTrue();
    // t3 and t4 are found here as of Spring 2.0, since they are pre-registered
    // singleton instances, while testFactory1 and testFactory are *not* found
    // because they are FactoryBean definitions that haven't been initialized yet.

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, false, true);
    Object testFactory1 = this.listableBeanFactory.getBean("testFactory1");
    assertThat(beans.size()).isEqualTo(5);
    assertThat(beans.get("test")).isEqualTo(test);
    assertThat(beans.get("testFactory1")).isEqualTo(testFactory1);
    assertThat(beans.get("t1")).isEqualTo(t1);
    assertThat(beans.get("t2")).isEqualTo(t2);
    assertThat(beans.get("t3")).isEqualTo(t3.getObject());

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, true, true);
    assertThat(beans.size()).isEqualTo(8);
    assertThat(beans.get("test3")).isEqualTo(test3);
    assertThat(beans.get("test")).isEqualTo(test);
    assertThat(beans.get("testFactory1")).isEqualTo(testFactory1);
    boolean condition1 = beans.get("testFactory2") instanceof TestBean;
    assertThat(condition1).isTrue();
    assertThat(beans.get("t1")).isEqualTo(t1);
    assertThat(beans.get("t2")).isEqualTo(t2);
    assertThat(beans.get("t3")).isEqualTo(t3.getObject());
    boolean condition = beans.get("t4") instanceof TestBean;
    assertThat(condition).isTrue();

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, DummyFactory.class, true, true);
    assertThat(beans.size()).isEqualTo(4);
    assertThat(beans.get("&testFactory1"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory1"));
    assertThat(beans.get("&testFactory2"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory2"));
    assertThat(beans.get("&t3")).isEqualTo(t3);
    assertThat(beans.get("&t4")).isEqualTo(t4);

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, FactoryBean.class, true, true);
    assertThat(beans.size()).isEqualTo(4);
    assertThat(beans.get("&testFactory1"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory1"));
    assertThat(beans.get("&testFactory2"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory2"));
    assertThat(beans.get("&t3")).isEqualTo(t3);
    assertThat(beans.get("&t4")).isEqualTo(t4);
  }

  @Test
  public void testHierarchicalResolutionWithOverride() throws Exception {
    Object test3 = this.listableBeanFactory.getBean("test3");
    Object test = this.listableBeanFactory.getBean("test");

    Map<String, ?> beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, true, false);
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("test3")).isEqualTo(test3);
    assertThat(beans.get("test")).isEqualTo(test);

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, false, false);
    assertThat(beans.size()).isEqualTo(1);
    assertThat(beans.get("test")).isEqualTo(test);

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, false, true);
    Object testFactory1 = this.listableBeanFactory.getBean("testFactory1");
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("test")).isEqualTo(test);
    assertThat(beans.get("testFactory1")).isEqualTo(testFactory1);

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, ITestBean.class, true, true);
    assertThat(beans.size()).isEqualTo(4);
    assertThat(beans.get("test3")).isEqualTo(test3);
    assertThat(beans.get("test")).isEqualTo(test);
    assertThat(beans.get("testFactory1")).isEqualTo(testFactory1);
    boolean condition = beans.get("testFactory2") instanceof TestBean;
    assertThat(condition).isTrue();

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, DummyFactory.class, true, true);
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("&testFactory1"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory1"));
    assertThat(beans.get("&testFactory2"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory2"));

    beans =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.listableBeanFactory, FactoryBean.class, true, true);
    assertThat(beans.size()).isEqualTo(2);
    assertThat(beans.get("&testFactory1"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory1"));
    assertThat(beans.get("&testFactory2"))
        .isEqualTo(this.listableBeanFactory.getBean("&testFactory2"));
  }

  @Test
  public void testHierarchicalNamesForAnnotationWithNoMatch() throws Exception {
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(
                this.listableBeanFactory, Override.class));
    assertThat(names.size()).isEqualTo(0);
  }

  @Test
  public void testHierarchicalNamesForAnnotationWithMatchOnlyInRoot() throws Exception {
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(
                this.listableBeanFactory, TestAnnotation.class));
    assertThat(names.size()).isEqualTo(1);
    assertThat(names.contains("annotatedBean")).isTrue();
    // Distinguish from default ListableBeanFactory behavior
    assertThat(listableBeanFactory.getBeanNamesForAnnotation(TestAnnotation.class).length == 0)
        .isTrue();
  }

  @Test
  public void testGetBeanNamesForAnnotationWithOverride() throws Exception {
    AnnotatedBean annotatedBean = new AnnotatedBean();
    this.listableBeanFactory.registerSingleton("anotherAnnotatedBean", annotatedBean);
    List<String> names =
        Arrays.asList(
            BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(
                this.listableBeanFactory, TestAnnotation.class));
    assertThat(names.size()).isEqualTo(2);
    assertThat(names.contains("annotatedBean")).isTrue();
    assertThat(names.contains("anotherAnnotatedBean")).isTrue();
  }

  @Test
  public void testADependencies() {
    String[] deps = this.dependentBeansFactory.getDependentBeans("a");
    assertThat(ObjectUtils.isEmpty(deps)).isTrue();
  }

  @Test
  public void testBDependencies() {
    String[] deps = this.dependentBeansFactory.getDependentBeans("b");
    assertThat(Arrays.equals(new String[] {"c"}, deps)).isTrue();
  }

  @Test
  public void testCDependencies() {
    String[] deps = this.dependentBeansFactory.getDependentBeans("c");
    assertThat(Arrays.equals(new String[] {"int", "long"}, deps)).isTrue();
  }

  @Test
  public void testIntDependencies() {
    String[] deps = this.dependentBeansFactory.getDependentBeans("int");
    assertThat(Arrays.equals(new String[] {"buffer"}, deps)).isTrue();
  }
}
