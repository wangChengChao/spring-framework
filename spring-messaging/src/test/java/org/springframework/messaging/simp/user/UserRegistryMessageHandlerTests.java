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

package org.springframework.messaging.simp.user;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * User tests for {@link UserRegistryMessageHandler}.
 *
 * @author Rossen Stoyanchev
 */
@ExtendWith(MockitoExtension.class)
public class UserRegistryMessageHandlerTests {

  private UserRegistryMessageHandler handler;

  private SimpUserRegistry localRegistry;

  private MultiServerUserRegistry multiServerRegistry;

  private MessageConverter converter;

  @Mock private MessageChannel brokerChannel;

  @Mock private TaskScheduler taskScheduler;

  @BeforeEach
  public void setUp() throws Exception {
    this.converter = new MappingJackson2MessageConverter();

    SimpMessagingTemplate brokerTemplate = new SimpMessagingTemplate(this.brokerChannel);
    brokerTemplate.setMessageConverter(this.converter);

    this.localRegistry = mock(SimpUserRegistry.class);
    this.multiServerRegistry = new MultiServerUserRegistry(this.localRegistry);

    this.handler =
        new UserRegistryMessageHandler(
            this.multiServerRegistry,
            brokerTemplate,
            "/topic/simp-user-registry",
            this.taskScheduler);
  }

  @Test
  public void brokerAvailableEvent() throws Exception {
    Runnable runnable = getUserRegistryTask();
    assertThat(runnable).isNotNull();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void brokerUnavailableEvent() throws Exception {

    ScheduledFuture future = mock(ScheduledFuture.class);
    given(this.taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Long.class)))
        .willReturn(future);

    BrokerAvailabilityEvent event = new BrokerAvailabilityEvent(true, this);
    this.handler.onApplicationEvent(event);
    verifyNoMoreInteractions(future);

    event = new BrokerAvailabilityEvent(false, this);
    this.handler.onApplicationEvent(event);
    verify(future).cancel(true);
  }

  @Test
  public void broadcastRegistry() throws Exception {
    given(this.brokerChannel.send(any())).willReturn(true);

    TestSimpUser simpUser1 = new TestSimpUser("joe");
    TestSimpUser simpUser2 = new TestSimpUser("jane");

    simpUser1.addSessions(new TestSimpSession("123"));
    simpUser1.addSessions(new TestSimpSession("456"));

    HashSet<SimpUser> simpUsers = new HashSet<>(Arrays.asList(simpUser1, simpUser2));
    given(this.localRegistry.getUsers()).willReturn(simpUsers);

    getUserRegistryTask().run();

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(this.brokerChannel).send(captor.capture());

    Message<?> message = captor.getValue();
    assertThat(message).isNotNull();
    MessageHeaders headers = message.getHeaders();
    assertThat(SimpMessageHeaderAccessor.getDestination(headers))
        .isEqualTo("/topic/simp-user-registry");

    MultiServerUserRegistry remoteRegistry =
        new MultiServerUserRegistry(mock(SimpUserRegistry.class));
    remoteRegistry.addRemoteRegistryDto(message, this.converter, 20000);
    assertThat(remoteRegistry.getUserCount()).isEqualTo(2);
    assertThat(remoteRegistry.getUser("joe")).isNotNull();
    assertThat(remoteRegistry.getUser("jane")).isNotNull();
  }

  @Test
  public void handleMessage() throws Exception {

    TestSimpUser simpUser1 = new TestSimpUser("joe");
    TestSimpUser simpUser2 = new TestSimpUser("jane");

    simpUser1.addSessions(new TestSimpSession("123"));
    simpUser2.addSessions(new TestSimpSession("456"));

    HashSet<SimpUser> simpUsers = new HashSet<>(Arrays.asList(simpUser1, simpUser2));
    SimpUserRegistry remoteUserRegistry = mock(SimpUserRegistry.class);
    given(remoteUserRegistry.getUserCount()).willReturn(2);
    given(remoteUserRegistry.getUsers()).willReturn(simpUsers);

    MultiServerUserRegistry remoteRegistry = new MultiServerUserRegistry(remoteUserRegistry);
    Message<?> message = this.converter.toMessage(remoteRegistry.getLocalRegistryDto(), null);

    this.handler.handleMessage(message);

    assertThat(remoteRegistry.getUserCount()).isEqualTo(2);
    assertThat(this.multiServerRegistry.getUser("joe")).isNotNull();
    assertThat(this.multiServerRegistry.getUser("jane")).isNotNull();
  }

  @Test
  public void handleMessageFromOwnBroadcast() throws Exception {

    TestSimpUser simpUser = new TestSimpUser("joe");
    simpUser.addSessions(new TestSimpSession("123"));
    given(this.localRegistry.getUserCount()).willReturn(1);
    given(this.localRegistry.getUsers()).willReturn(Collections.singleton(simpUser));

    assertThat(this.multiServerRegistry.getUserCount()).isEqualTo(1);

    Message<?> message =
        this.converter.toMessage(this.multiServerRegistry.getLocalRegistryDto(), null);
    this.multiServerRegistry.addRemoteRegistryDto(message, this.converter, 20000);
    assertThat(this.multiServerRegistry.getUserCount()).isEqualTo(1);
  }

  private Runnable getUserRegistryTask() {
    BrokerAvailabilityEvent event = new BrokerAvailabilityEvent(true, this);
    this.handler.onApplicationEvent(event);

    ArgumentCaptor<? extends Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(this.taskScheduler).scheduleWithFixedDelay(captor.capture(), eq(10000L));

    return captor.getValue();
  }
}
