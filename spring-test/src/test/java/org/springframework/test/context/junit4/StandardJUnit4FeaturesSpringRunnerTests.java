/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.test.context.junit4;

import org.junit.runner.RunWith;

import org.springframework.test.context.TestExecutionListeners;

/**
 * Simple unit test to verify that {@link SpringRunner} does not hinder correct functionality of
 * standard JUnit 4.4+ testing features.
 *
 * <p>Note that {@link TestExecutionListeners @TestExecutionListeners} is explicitly configured with
 * an empty list, thus disabling all default listeners.
 *
 * @author Sam Brannen
 * @since 2.5
 * @see StandardJUnit4FeaturesTests
 */
@RunWith(SpringRunner.class)
@TestExecutionListeners({})
public class StandardJUnit4FeaturesSpringRunnerTests extends StandardJUnit4FeaturesTests {

  /* All tests are in the parent class... */

}
