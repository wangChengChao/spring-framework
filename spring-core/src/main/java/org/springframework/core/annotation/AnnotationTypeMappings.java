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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Provides {@link AnnotationTypeMapping} information for a single source annotation type. Performs
 * a recursive breadth first crawl of all meta-annotations to ultimately provide a quick way to map
 * the attributes of root {@link Annotation}.
 *
 * <p>Supports convention based merging of meta-annotations as well as implicit and explicit {@link
 * AliasFor @AliasFor} aliases. Also provides information about mirrored attributes.
 *
 * <p>This class is designed to be cached so that meta-annotations only need to be searched once,
 * regardless of how many times they are actually used.
 *
 * @author Phillip Webb
 * @since 5.2
 * @see AnnotationTypeMapping
 */
final class AnnotationTypeMappings {

  private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.DEBUG;

  private static final Map<AnnotationFilter, Cache> cache = new ConcurrentReferenceHashMap<>();

  private final AnnotationFilter filter;

  private final List<AnnotationTypeMapping> mappings;

  private AnnotationTypeMappings(
      AnnotationFilter filter, Class<? extends Annotation> annotationType) {
    this.filter = filter;
    this.mappings = new ArrayList<>();
    addAllMappings(annotationType);
    this.mappings.forEach(AnnotationTypeMapping::afterAllMappingsSet);
  }

  private void addAllMappings(Class<? extends Annotation> annotationType) {
    Deque<AnnotationTypeMapping> queue = new ArrayDeque<>();
    addIfPossible(queue, null, annotationType, null);
    while (!queue.isEmpty()) {
      AnnotationTypeMapping mapping = queue.removeFirst();
      this.mappings.add(mapping);
      addMetaAnnotationsToQueue(queue, mapping);
    }
  }

  private void addMetaAnnotationsToQueue(
      Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source) {
    Annotation[] metaAnnotations =
        AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);
    for (Annotation metaAnnotation : metaAnnotations) {
      if (!isMappable(source, metaAnnotation)) {
        continue;
      }
      Annotation[] repeatedAnnotations =
          RepeatableContainers.standardRepeatables().findRepeatedAnnotations(metaAnnotation);
      if (repeatedAnnotations != null) {
        for (Annotation repeatedAnnotation : repeatedAnnotations) {
          if (!isMappable(source, metaAnnotation)) {
            continue;
          }
          addIfPossible(queue, source, repeatedAnnotation);
        }
      } else {
        addIfPossible(queue, source, metaAnnotation);
      }
    }
  }

  private void addIfPossible(
      Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source, Annotation ann) {

    addIfPossible(queue, source, ann.annotationType(), ann);
  }

  private void addIfPossible(
      Deque<AnnotationTypeMapping> queue,
      @Nullable AnnotationTypeMapping source,
      Class<? extends Annotation> annotationType,
      @Nullable Annotation ann) {

    try {
      queue.addLast(new AnnotationTypeMapping(source, annotationType, ann));
    } catch (Exception ex) {
      if (ex instanceof AnnotationConfigurationException) {
        throw (AnnotationConfigurationException) ex;
      }
      if (failureLogger.isEnabled()) {
        failureLogger.log(
            "Failed to introspect meta-annotation " + annotationType.getName(),
            (source != null ? source.getAnnotationType() : null),
            ex);
      }
    }
  }

  private boolean isMappable(AnnotationTypeMapping source, @Nullable Annotation metaAnnotation) {
    return (metaAnnotation != null
        && !this.filter.matches(metaAnnotation)
        && !AnnotationFilter.PLAIN.matches(source.getAnnotationType())
        && !isAlreadyMapped(source, metaAnnotation));
  }

  private boolean isAlreadyMapped(AnnotationTypeMapping source, Annotation metaAnnotation) {
    Class<? extends Annotation> annotationType = metaAnnotation.annotationType();
    AnnotationTypeMapping mapping = source;
    while (mapping != null) {
      if (mapping.getAnnotationType() == annotationType) {
        return true;
      }
      mapping = mapping.getSource();
    }
    return false;
  }

  /**
   * Get the total number of contained mappings.
   *
   * @return the total number of mappings
   */
  int size() {
    return this.mappings.size();
  }

  /**
   * Get an individual mapping from this instance.
   *
   * <p>Index {@code 0} will always return the root mapping; higher indexes will return
   * meta-annotation mappings.
   *
   * @param index the index to return
   * @return the {@link AnnotationTypeMapping}
   * @throws IndexOutOfBoundsException if the index is out of range (<tt>index &lt; 0 || index &gt;=
   *     size()</tt>)
   */
  AnnotationTypeMapping get(int index) {
    return this.mappings.get(index);
  }

  /**
   * Create {@link AnnotationTypeMappings} for the specified annotation type.
   *
   * @param annotationType the source annotation type
   * @return type mappings for the annotation type
   */
  static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType) {
    return forAnnotationType(annotationType, AnnotationFilter.PLAIN);
  }

  /**
   * Create {@link AnnotationTypeMappings} for the specified annotation type.
   *
   * @param annotationType the source annotation type
   * @param annotationFilter the annotation filter used to limit which annotations are considered
   * @return type mappings for the annotation type
   */
  static AnnotationTypeMappings forAnnotationType(
      Class<? extends Annotation> annotationType, AnnotationFilter annotationFilter) {

    return cache.computeIfAbsent(annotationFilter, Cache::new).get(annotationType);
  }

  static void clearCache() {
    cache.clear();
  }

  /** Cache created per {@link AnnotationFilter}. */
  private static class Cache {

    private final AnnotationFilter filter;

    private final Map<Class<? extends Annotation>, AnnotationTypeMappings> mappings;

    /**
     * Create a cache instance with the specified filter.
     *
     * @param filter the annotation filter
     */
    Cache(AnnotationFilter filter) {
      this.filter = filter;
      this.mappings = new ConcurrentReferenceHashMap<>();
    }

    /**
     * Return or create {@link AnnotationTypeMappings} for the specified annotation type.
     *
     * @param annotationType the annotation type
     * @return a new or existing {@link AnnotationTypeMapping} instance
     */
    AnnotationTypeMappings get(Class<? extends Annotation> annotationType) {
      return this.mappings.computeIfAbsent(annotationType, this::createMappings);
    }

    AnnotationTypeMappings createMappings(Class<? extends Annotation> annotationType) {
      return new AnnotationTypeMappings(this.filter, annotationType);
    }
  }
}
