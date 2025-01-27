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

package org.springframework.beans.support;

import java.util.Comparator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PropertyComparator}.
 *
 * @author Keith Donald
 * @author Chris Beams
 */
public class PropertyComparatorTests {

  @Test
  public void testPropertyComparator() {
    Dog dog = new Dog();
    dog.setNickName("mace");

    Dog dog2 = new Dog();
    dog2.setNickName("biscy");

    PropertyComparator<Dog> c = new PropertyComparator<>("nickName", false, true);
    assertThat(c.compare(dog, dog2) > 0).isTrue();
    assertThat(c.compare(dog, dog) == 0).isTrue();
    assertThat(c.compare(dog2, dog) < 0).isTrue();
  }

  @Test
  public void testPropertyComparatorNulls() {
    Dog dog = new Dog();
    Dog dog2 = new Dog();
    PropertyComparator<Dog> c = new PropertyComparator<>("nickName", false, true);
    assertThat(c.compare(dog, dog2) == 0).isTrue();
  }

  @Test
  public void testChainedComparators() {
    Comparator<Dog> c = new PropertyComparator<>("lastName", false, true);

    Dog dog1 = new Dog();
    dog1.setFirstName("macy");
    dog1.setLastName("grayspots");

    Dog dog2 = new Dog();
    dog2.setFirstName("biscuit");
    dog2.setLastName("grayspots");

    assertThat(c.compare(dog1, dog2) == 0).isTrue();

    c = c.thenComparing(new PropertyComparator<>("firstName", false, true));
    assertThat(c.compare(dog1, dog2) > 0).isTrue();

    dog2.setLastName("konikk dog");
    assertThat(c.compare(dog2, dog1) > 0).isTrue();
  }

  @Test
  public void testChainedComparatorsReversed() {
    Comparator<Dog> c =
        (new PropertyComparator<Dog>("lastName", false, true))
            .thenComparing(new PropertyComparator<>("firstName", false, true));

    Dog dog1 = new Dog();
    dog1.setFirstName("macy");
    dog1.setLastName("grayspots");

    Dog dog2 = new Dog();
    dog2.setFirstName("biscuit");
    dog2.setLastName("grayspots");

    assertThat(c.compare(dog1, dog2) > 0).isTrue();
    c = c.reversed();
    assertThat(c.compare(dog1, dog2) < 0).isTrue();
  }

  @SuppressWarnings("unused")
  private static class Dog implements Comparable<Object> {

    private String nickName;

    private String firstName;

    private String lastName;

    public String getNickName() {
      return nickName;
    }

    public void setNickName(String nickName) {
      this.nickName = nickName;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    @Override
    public int compareTo(Object o) {
      return this.nickName.compareTo(((Dog) o).nickName);
    }
  }
}
