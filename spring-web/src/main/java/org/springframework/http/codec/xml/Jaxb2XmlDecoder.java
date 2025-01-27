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

package org.springframework.http.codec.xml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.reactivestreams.Publisher;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.xml.StaxUtils;

/**
 * Decode from a bytes stream containing XML elements to a stream of {@code Object}s (POJOs).
 *
 * @author Sebastien Deleuze
 * @author Arjen Poutsma
 * @since 5.0
 * @see Jaxb2XmlEncoder
 */
public class Jaxb2XmlDecoder extends AbstractDecoder<Object> {

  /**
   * The default value for JAXB annotations.
   *
   * @see XmlRootElement#name()
   * @see XmlRootElement#namespace()
   * @see XmlType#name()
   * @see XmlType#namespace()
   */
  private static final String JAXB_DEFAULT_ANNOTATION_VALUE = "##default";

  private static final XMLInputFactory inputFactory = StaxUtils.createDefensiveInputFactory();

  private final XmlEventDecoder xmlEventDecoder = new XmlEventDecoder();

  private final JaxbContextContainer jaxbContexts = new JaxbContextContainer();

  private Function<Unmarshaller, Unmarshaller> unmarshallerProcessor = Function.identity();

  public Jaxb2XmlDecoder() {
    super(MimeTypeUtils.APPLICATION_XML, MimeTypeUtils.TEXT_XML);
  }

  /**
   * Create a {@code Jaxb2XmlDecoder} with the specified MIME types.
   *
   * @param supportedMimeTypes supported MIME types
   * @since 5.1.9
   */
  public Jaxb2XmlDecoder(MimeType... supportedMimeTypes) {
    super(supportedMimeTypes);
  }

  /**
   * Configure a processor function to customize Unmarshaller instances.
   *
   * @param processor the function to use
   * @since 5.1.3
   */
  public void setUnmarshallerProcessor(Function<Unmarshaller, Unmarshaller> processor) {
    this.unmarshallerProcessor = this.unmarshallerProcessor.andThen(processor);
  }

  /**
   * Return the configured processor for customizing Unmarshaller instances.
   *
   * @since 5.1.3
   */
  public Function<Unmarshaller, Unmarshaller> getUnmarshallerProcessor() {
    return this.unmarshallerProcessor;
  }

  @Override
  public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
    Class<?> outputClass = elementType.toClass();
    return (outputClass.isAnnotationPresent(XmlRootElement.class)
            || outputClass.isAnnotationPresent(XmlType.class))
        && super.canDecode(elementType, mimeType);
  }

  @Override
  public Flux<Object> decode(
      Publisher<DataBuffer> inputStream,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {

    Flux<XMLEvent> xmlEventFlux =
        this.xmlEventDecoder.decode(
            inputStream, ResolvableType.forClass(XMLEvent.class), mimeType, hints);

    Class<?> outputClass = elementType.toClass();
    QName typeName = toQName(outputClass);
    Flux<List<XMLEvent>> splitEvents = split(xmlEventFlux, typeName);

    return splitEvents.map(
        events -> {
          Object value = unmarshal(events, outputClass);
          LogFormatUtils.traceDebug(
              logger,
              traceOn -> {
                String formatted = LogFormatUtils.formatValue(value, !traceOn);
                return Hints.getLogPrefix(hints) + "Decoded [" + formatted + "]";
              });
          return value;
        });
  }

  @Override
  @SuppressWarnings({
    "rawtypes",
    "unchecked",
    "cast"
  }) // XMLEventReader is Iterator<Object> on JDK 9
  public Mono<Object> decodeToMono(
      Publisher<DataBuffer> input,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints) {

    return DataBufferUtils.join(input)
        .map(dataBuffer -> decode(dataBuffer, elementType, mimeType, hints));
  }

  @Override
  @SuppressWarnings({
    "rawtypes",
    "unchecked",
    "cast"
  }) // XMLEventReader is Iterator<Object> on JDK 9
  public Object decode(
      DataBuffer dataBuffer,
      ResolvableType targetType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints)
      throws DecodingException {

    try {
      Iterator eventReader = inputFactory.createXMLEventReader(dataBuffer.asInputStream());
      List<XMLEvent> events = new ArrayList<>();
      eventReader.forEachRemaining(event -> events.add((XMLEvent) event));
      return unmarshal(events, targetType.toClass());
    } catch (XMLStreamException ex) {
      throw Exceptions.propagate(ex);
    } finally {
      DataBufferUtils.release(dataBuffer);
    }
  }

  private Object unmarshal(List<XMLEvent> events, Class<?> outputClass) {
    try {
      Unmarshaller unmarshaller = initUnmarshaller(outputClass);
      XMLEventReader eventReader = StaxUtils.createXMLEventReader(events);
      if (outputClass.isAnnotationPresent(XmlRootElement.class)) {
        return unmarshaller.unmarshal(eventReader);
      } else {
        JAXBElement<?> jaxbElement = unmarshaller.unmarshal(eventReader, outputClass);
        return jaxbElement.getValue();
      }
    } catch (UnmarshalException ex) {
      throw new DecodingException("Could not unmarshal XML to " + outputClass, ex);
    } catch (JAXBException ex) {
      throw new CodecException("Invalid JAXB configuration", ex);
    }
  }

  private Unmarshaller initUnmarshaller(Class<?> outputClass) throws JAXBException {
    Unmarshaller unmarshaller = this.jaxbContexts.createUnmarshaller(outputClass);
    return this.unmarshallerProcessor.apply(unmarshaller);
  }

  /**
   * Returns the qualified name for the given class, according to the mapping rules in the JAXB
   * specification.
   */
  QName toQName(Class<?> outputClass) {
    String localPart;
    String namespaceUri;

    if (outputClass.isAnnotationPresent(XmlRootElement.class)) {
      XmlRootElement annotation = outputClass.getAnnotation(XmlRootElement.class);
      localPart = annotation.name();
      namespaceUri = annotation.namespace();
    } else if (outputClass.isAnnotationPresent(XmlType.class)) {
      XmlType annotation = outputClass.getAnnotation(XmlType.class);
      localPart = annotation.name();
      namespaceUri = annotation.namespace();
    } else {
      throw new IllegalArgumentException(
          "Output class ["
              + outputClass.getName()
              + "] is neither annotated with @XmlRootElement nor @XmlType");
    }

    if (JAXB_DEFAULT_ANNOTATION_VALUE.equals(localPart)) {
      localPart = ClassUtils.getShortNameAsProperty(outputClass);
    }
    if (JAXB_DEFAULT_ANNOTATION_VALUE.equals(namespaceUri)) {
      Package outputClassPackage = outputClass.getPackage();
      if (outputClassPackage != null && outputClassPackage.isAnnotationPresent(XmlSchema.class)) {
        XmlSchema annotation = outputClassPackage.getAnnotation(XmlSchema.class);
        namespaceUri = annotation.namespace();
      } else {
        namespaceUri = XMLConstants.NULL_NS_URI;
      }
    }
    return new QName(namespaceUri, localPart);
  }

  /**
   * Split a flux of {@link XMLEvent XMLEvents} into a flux of XMLEvent lists, one list for each
   * branch of the tree that starts with the given qualified name. That is, given the XMLEvents
   * shown {@linkplain XmlEventDecoder here}, and the {@code desiredName} "{@code child}", this
   * method returns a flux of two lists, each of which containing the events of a particular branch
   * of the tree that starts with "{@code child}".
   *
   * <ol>
   *   <li>The first list, dealing with the first branch of the tree:
   *       <ol>
   *         <li>{@link javax.xml.stream.events.StartElement} {@code child}
   *         <li>{@link javax.xml.stream.events.Characters} {@code foo}
   *         <li>{@link javax.xml.stream.events.EndElement} {@code child}
   *       </ol>
   *   <li>The second list, dealing with the second branch of the tree:
   *       <ol>
   *         <li>{@link javax.xml.stream.events.StartElement} {@code child}
   *         <li>{@link javax.xml.stream.events.Characters} {@code bar}
   *         <li>{@link javax.xml.stream.events.EndElement} {@code child}
   *       </ol>
   * </ol>
   */
  Flux<List<XMLEvent>> split(Flux<XMLEvent> xmlEventFlux, QName desiredName) {
    return xmlEventFlux.handle(new SplitHandler(desiredName));
  }

  private static class SplitHandler
      implements BiConsumer<XMLEvent, SynchronousSink<List<XMLEvent>>> {

    private final QName desiredName;

    @Nullable private List<XMLEvent> events;

    private int elementDepth = 0;

    private int barrier = Integer.MAX_VALUE;

    public SplitHandler(QName desiredName) {
      this.desiredName = desiredName;
    }

    @Override
    public void accept(XMLEvent event, SynchronousSink<List<XMLEvent>> sink) {
      if (event.isStartElement()) {
        if (this.barrier == Integer.MAX_VALUE) {
          QName startElementName = event.asStartElement().getName();
          if (this.desiredName.equals(startElementName)) {
            this.events = new ArrayList<>();
            this.barrier = this.elementDepth;
          }
        }
        this.elementDepth++;
      }
      if (this.elementDepth > this.barrier) {
        Assert.state(this.events != null, "No XMLEvent List");
        this.events.add(event);
      }
      if (event.isEndElement()) {
        this.elementDepth--;
        if (this.elementDepth == this.barrier) {
          this.barrier = Integer.MAX_VALUE;
          Assert.state(this.events != null, "No XMLEvent List");
          sink.next(this.events);
        }
      }
    }
  }
}
