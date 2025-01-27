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

package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.lookup.BeanFactoryDataSourceLookup;
import org.springframework.jdbc.datasource.lookup.IsolationLevelDataSourceRouter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.jta.JtaTransactionObject;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Juergen Hoeller
 * @since 17.10.2005
 */
public class DataSourceJtaTransactionTests {

  private Connection connection;
  private DataSource dataSource;
  private UserTransaction userTransaction;
  private TransactionManager transactionManager;
  private Transaction transaction;

  @BeforeEach
  public void setup() throws Exception {
    connection = mock(Connection.class);
    dataSource = mock(DataSource.class);
    userTransaction = mock(UserTransaction.class);
    transactionManager = mock(TransactionManager.class);
    transaction = mock(Transaction.class);
    given(dataSource.getConnection()).willReturn(connection);
  }

  @AfterEach
  public void verifyTransactionSynchronizationManagerState() {
    assertThat(TransactionSynchronizationManager.getResourceMap().isEmpty()).isTrue();
    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    assertThat(TransactionSynchronizationManager.getCurrentTransactionName()).isNull();
    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
    assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isNull();
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }

  @Test
  public void testJtaTransactionCommit() throws Exception {
    doTestJtaTransaction(false);
  }

  @Test
  public void testJtaTransactionRollback() throws Exception {
    doTestJtaTransaction(true);
  }

  private void doTestJtaTransaction(final boolean rollback) throws Exception {
    if (rollback) {
      given(userTransaction.getStatus())
          .willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE);
    } else {
      given(userTransaction.getStatus())
          .willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);
    }

    JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
    TransactionTemplate tt = new TransactionTemplate(ptm);
    boolean condition3 = !TransactionSynchronizationManager.hasResource(dataSource);
    assertThat(condition3).as("Hasn't thread connection").isTrue();
    boolean condition2 = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition2).as("JTA synchronizations not active").isTrue();

    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            boolean condition = !TransactionSynchronizationManager.hasResource(dataSource);
            assertThat(condition).as("Hasn't thread connection").isTrue();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                .as("JTA synchronizations active")
                .isTrue();
            assertThat(status.isNewTransaction()).as("Is new transaction").isTrue();

            Connection c = DataSourceUtils.getConnection(dataSource);
            assertThat(TransactionSynchronizationManager.hasResource(dataSource))
                .as("Has thread connection")
                .isTrue();
            DataSourceUtils.releaseConnection(c, dataSource);

            c = DataSourceUtils.getConnection(dataSource);
            assertThat(TransactionSynchronizationManager.hasResource(dataSource))
                .as("Has thread connection")
                .isTrue();
            DataSourceUtils.releaseConnection(c, dataSource);

            if (rollback) {
              status.setRollbackOnly();
            }
          }
        });

    boolean condition1 = !TransactionSynchronizationManager.hasResource(dataSource);
    assertThat(condition1).as("Hasn't thread connection").isTrue();
    boolean condition = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition).as("JTA synchronizations not active").isTrue();
    verify(userTransaction).begin();
    if (rollback) {
      verify(userTransaction).rollback();
    }
    verify(connection).close();
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNew() throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(false, false, false, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithAccessAfterResume()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(false, false, true, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnection()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(false, true, false, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAccessed()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(false, true, true, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSource()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(false, false, true, true);
  }

  @Test
  public void testJtaTransactionRollbackWithPropagationRequiresNew() throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(true, false, false, false);
  }

  @Test
  public void testJtaTransactionRollbackWithPropagationRequiresNewWithAccessAfterResume()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(true, false, true, false);
  }

  @Test
  public void testJtaTransactionRollbackWithPropagationRequiresNewWithOpenOuterConnection()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(true, true, false, false);
  }

  @Test
  public void testJtaTransactionRollbackWithPropagationRequiresNewWithOpenOuterConnectionAccessed()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(true, true, true, false);
  }

  @Test
  public void testJtaTransactionRollbackWithPropagationRequiresNewWithTransactionAwareDataSource()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNew(true, false, true, true);
  }

  private void doTestJtaTransactionWithPropagationRequiresNew(
      final boolean rollback,
      final boolean openOuterConnection,
      final boolean accessAfterResume,
      final boolean useTransactionAwareDataSource)
      throws Exception {

    given(transactionManager.suspend()).willReturn(transaction);
    if (rollback) {
      given(userTransaction.getStatus())
          .willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE);
    } else {
      given(userTransaction.getStatus())
          .willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);
    }

    given(connection.isReadOnly()).willReturn(true);

    final DataSource dsToUse =
        useTransactionAwareDataSource
            ? new TransactionAwareDataSourceProxy(dataSource)
            : dataSource;

    JtaTransactionManager ptm = new JtaTransactionManager(userTransaction, transactionManager);
    final TransactionTemplate tt = new TransactionTemplate(ptm);
    tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    boolean condition3 = !TransactionSynchronizationManager.hasResource(dsToUse);
    assertThat(condition3).as("Hasn't thread connection").isTrue();
    boolean condition2 = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition2).as("JTA synchronizations not active").isTrue();

    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            boolean condition = !TransactionSynchronizationManager.hasResource(dsToUse);
            assertThat(condition).as("Hasn't thread connection").isTrue();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                .as("JTA synchronizations active")
                .isTrue();
            assertThat(status.isNewTransaction()).as("Is new transaction").isTrue();

            Connection c = DataSourceUtils.getConnection(dsToUse);
            try {
              assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                  .as("Has thread connection")
                  .isTrue();
              c.isReadOnly();
              DataSourceUtils.releaseConnection(c, dsToUse);

              c = DataSourceUtils.getConnection(dsToUse);
              assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                  .as("Has thread connection")
                  .isTrue();
              if (!openOuterConnection) {
                DataSourceUtils.releaseConnection(c, dsToUse);
              }
            } catch (SQLException ex) {
            }

            for (int i = 0; i < 5; i++) {

              tt.execute(
                  new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status)
                        throws RuntimeException {
                      boolean condition = !TransactionSynchronizationManager.hasResource(dsToUse);
                      assertThat(condition).as("Hasn't thread connection").isTrue();
                      assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                          .as("JTA synchronizations active")
                          .isTrue();
                      assertThat(status.isNewTransaction()).as("Is new transaction").isTrue();

                      try {
                        Connection c = DataSourceUtils.getConnection(dsToUse);
                        c.isReadOnly();
                        assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                            .as("Has thread connection")
                            .isTrue();
                        DataSourceUtils.releaseConnection(c, dsToUse);

                        c = DataSourceUtils.getConnection(dsToUse);
                        assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                            .as("Has thread connection")
                            .isTrue();
                        DataSourceUtils.releaseConnection(c, dsToUse);
                      } catch (SQLException ex) {
                      }
                    }
                  });
            }

            if (rollback) {
              status.setRollbackOnly();
            }

            if (accessAfterResume) {
              try {
                if (!openOuterConnection) {
                  c = DataSourceUtils.getConnection(dsToUse);
                }
                assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                    .as("Has thread connection")
                    .isTrue();
                c.isReadOnly();
                DataSourceUtils.releaseConnection(c, dsToUse);

                c = DataSourceUtils.getConnection(dsToUse);
                assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                    .as("Has thread connection")
                    .isTrue();
                DataSourceUtils.releaseConnection(c, dsToUse);
              } catch (SQLException ex) {
              }
            } else {
              if (openOuterConnection) {
                DataSourceUtils.releaseConnection(c, dsToUse);
              }
            }
          }
        });

    boolean condition1 = !TransactionSynchronizationManager.hasResource(dsToUse);
    assertThat(condition1).as("Hasn't thread connection").isTrue();
    boolean condition = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition).as("JTA synchronizations not active").isTrue();
    verify(userTransaction, times(6)).begin();
    verify(transactionManager, times(5)).resume(transaction);
    if (rollback) {
      verify(userTransaction, times(5)).commit();
      verify(userTransaction).rollback();
    } else {
      verify(userTransaction, times(6)).commit();
    }
    if (accessAfterResume && !openOuterConnection) {
      verify(connection, times(7)).close();
    } else {
      verify(connection, times(6)).close();
    }
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiredWithinSupports() throws Exception {
    doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(false, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiredWithinNotSupported() throws Exception {
    doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(false, true);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithinSupports() throws Exception {
    doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(true, false);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewWithinNotSupported()
      throws Exception {
    doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(true, true);
  }

  private void doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(
      final boolean requiresNew, boolean notSupported) throws Exception {

    if (notSupported) {
      given(userTransaction.getStatus())
          .willReturn(
              Status.STATUS_ACTIVE,
              Status.STATUS_NO_TRANSACTION,
              Status.STATUS_ACTIVE,
              Status.STATUS_ACTIVE);
      given(transactionManager.suspend()).willReturn(transaction);
    } else {
      given(userTransaction.getStatus())
          .willReturn(
              Status.STATUS_NO_TRANSACTION,
              Status.STATUS_NO_TRANSACTION,
              Status.STATUS_ACTIVE,
              Status.STATUS_ACTIVE);
    }

    final DataSource dataSource = mock(DataSource.class);
    final Connection connection1 = mock(Connection.class);
    final Connection connection2 = mock(Connection.class);
    given(dataSource.getConnection()).willReturn(connection1, connection2);

    final JtaTransactionManager ptm =
        new JtaTransactionManager(userTransaction, transactionManager);
    TransactionTemplate tt = new TransactionTemplate(ptm);
    tt.setPropagationBehavior(
        notSupported
            ? TransactionDefinition.PROPAGATION_NOT_SUPPORTED
            : TransactionDefinition.PROPAGATION_SUPPORTS);

    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status) {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);

            TransactionTemplate tt2 = new TransactionTemplate(ptm);
            tt2.setPropagationBehavior(
                requiresNew
                    ? TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    : TransactionDefinition.PROPAGATION_REQUIRED);
            tt2.execute(
                new TransactionCallbackWithoutResult() {
                  @Override
                  protected void doInTransactionWithoutResult(TransactionStatus status) {
                    assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                        .isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                        .isFalse();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .isTrue();
                    assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection2);
                    assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection2);
                  }
                });

            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);
          }
        });
    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    verify(userTransaction).begin();
    verify(userTransaction).commit();
    if (notSupported) {
      verify(transactionManager).resume(transaction);
    }
    verify(connection2).close();
    verify(connection1).close();
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewAndSuspendException()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, false, false);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndSuspendException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, true, false);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSourceAndSuspendException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, false, true);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndTransactionAwareDataSourceAndSuspendException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, true, true);
  }

  @Test
  public void testJtaTransactionCommitWithPropagationRequiresNewAndBeginException()
      throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, false, false);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndBeginException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, true, false);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndTransactionAwareDataSourceAndBeginException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, true, true);
  }

  @Test
  public void
      testJtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSourceAndBeginException()
          throws Exception {
    doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, false, true);
  }

  private void doTestJtaTransactionWithPropagationRequiresNewAndBeginException(
      boolean suspendException,
      final boolean openOuterConnection,
      final boolean useTransactionAwareDataSource)
      throws Exception {

    given(userTransaction.getStatus())
        .willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);
    if (suspendException) {
      given(transactionManager.suspend()).willThrow(new SystemException());
    } else {
      given(transactionManager.suspend()).willReturn(transaction);
      willThrow(new SystemException()).given(userTransaction).begin();
    }

    given(connection.isReadOnly()).willReturn(true);

    final DataSource dsToUse =
        useTransactionAwareDataSource
            ? new TransactionAwareDataSourceProxy(dataSource)
            : dataSource;
    if (dsToUse instanceof TransactionAwareDataSourceProxy) {
      ((TransactionAwareDataSourceProxy) dsToUse).setReobtainTransactionalConnections(true);
    }

    JtaTransactionManager ptm = new JtaTransactionManager(userTransaction, transactionManager);
    final TransactionTemplate tt = new TransactionTemplate(ptm);
    tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    boolean condition3 = !TransactionSynchronizationManager.hasResource(dsToUse);
    assertThat(condition3).as("Hasn't thread connection").isTrue();
    boolean condition2 = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition2).as("JTA synchronizations not active").isTrue();

    assertThatExceptionOfType(TransactionException.class)
        .isThrownBy(
            () ->
                tt.execute(
                    new TransactionCallbackWithoutResult() {

                      @Override
                      protected void doInTransactionWithoutResult(TransactionStatus status)
                          throws RuntimeException {
                        boolean condition = !TransactionSynchronizationManager.hasResource(dsToUse);
                        assertThat(condition).as("Hasn't thread connection").isTrue();
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                            .as("JTA synchronizations active")
                            .isTrue();
                        assertThat(status.isNewTransaction()).as("Is new transaction").isTrue();

                        Connection c = DataSourceUtils.getConnection(dsToUse);
                        try {
                          assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                              .as("Has thread connection")
                              .isTrue();
                          c.isReadOnly();
                          DataSourceUtils.releaseConnection(c, dsToUse);

                          c = DataSourceUtils.getConnection(dsToUse);
                          assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                              .as("Has thread connection")
                              .isTrue();
                          if (!openOuterConnection) {
                            DataSourceUtils.releaseConnection(c, dsToUse);
                          }
                        } catch (SQLException ex) {
                        }

                        try {
                          tt.execute(
                              new TransactionCallbackWithoutResult() {
                                @Override
                                protected void doInTransactionWithoutResult(
                                    TransactionStatus status) throws RuntimeException {
                                  boolean condition =
                                      !TransactionSynchronizationManager.hasResource(dsToUse);
                                  assertThat(condition).as("Hasn't thread connection").isTrue();
                                  assertThat(
                                          TransactionSynchronizationManager
                                              .isSynchronizationActive())
                                      .as("JTA synchronizations active")
                                      .isTrue();
                                  assertThat(status.isNewTransaction())
                                      .as("Is new transaction")
                                      .isTrue();

                                  Connection c = DataSourceUtils.getConnection(dsToUse);
                                  assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                                      .as("Has thread connection")
                                      .isTrue();
                                  DataSourceUtils.releaseConnection(c, dsToUse);

                                  c = DataSourceUtils.getConnection(dsToUse);
                                  assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                                      .as("Has thread connection")
                                      .isTrue();
                                  DataSourceUtils.releaseConnection(c, dsToUse);
                                }
                              });
                        } finally {
                          if (openOuterConnection) {
                            try {
                              c.isReadOnly();
                              DataSourceUtils.releaseConnection(c, dsToUse);
                            } catch (SQLException ex) {
                            }
                          }
                        }
                      }
                    }));

    boolean condition1 = !TransactionSynchronizationManager.hasResource(dsToUse);
    assertThat(condition1).as("Hasn't thread connection").isTrue();
    boolean condition = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition).as("JTA synchronizations not active").isTrue();

    verify(userTransaction).begin();
    if (suspendException) {
      verify(userTransaction).rollback();
    }

    if (suspendException) {
      verify(connection, atLeastOnce()).close();
    } else {
      verify(connection, never()).close();
    }
  }

  @Test
  public void testJtaTransactionWithConnectionHolderStillBound() throws Exception {
    @SuppressWarnings("serial")
    JtaTransactionManager ptm =
        new JtaTransactionManager(userTransaction) {

          @Override
          protected void doRegisterAfterCompletionWithJtaTransaction(
              JtaTransactionObject txObject,
              final List<TransactionSynchronization> synchronizations)
              throws RollbackException, SystemException {
            Thread async =
                new Thread() {
                  @Override
                  public void run() {
                    invokeAfterCompletion(
                        synchronizations, TransactionSynchronization.STATUS_COMMITTED);
                  }
                };
            async.start();
            try {
              async.join();
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
          }
        };
    TransactionTemplate tt = new TransactionTemplate(ptm);
    boolean condition2 = !TransactionSynchronizationManager.hasResource(dataSource);
    assertThat(condition2).as("Hasn't thread connection").isTrue();
    boolean condition1 = !TransactionSynchronizationManager.isSynchronizationActive();
    assertThat(condition1).as("JTA synchronizations not active").isTrue();

    given(userTransaction.getStatus()).willReturn(Status.STATUS_ACTIVE);
    for (int i = 0; i < 3; i++) {
      final boolean releaseCon = (i != 1);

      tt.execute(
          new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status)
                throws RuntimeException {
              assertThat(TransactionSynchronizationManager.isSynchronizationActive())
                  .as("JTA synchronizations active")
                  .isTrue();
              boolean condition = !status.isNewTransaction();
              assertThat(condition).as("Is existing transaction").isTrue();

              Connection c = DataSourceUtils.getConnection(dataSource);
              assertThat(TransactionSynchronizationManager.hasResource(dataSource))
                  .as("Has thread connection")
                  .isTrue();
              DataSourceUtils.releaseConnection(c, dataSource);

              c = DataSourceUtils.getConnection(dataSource);
              assertThat(TransactionSynchronizationManager.hasResource(dataSource))
                  .as("Has thread connection")
                  .isTrue();
              if (releaseCon) {
                DataSourceUtils.releaseConnection(c, dataSource);
              }
            }
          });

      if (!releaseCon) {
        assertThat(TransactionSynchronizationManager.hasResource(dataSource))
            .as("Still has connection holder")
            .isTrue();
      } else {
        boolean condition = !TransactionSynchronizationManager.hasResource(dataSource);
        assertThat(condition).as("Hasn't thread connection").isTrue();
      }
      boolean condition = !TransactionSynchronizationManager.isSynchronizationActive();
      assertThat(condition).as("JTA synchronizations not active").isTrue();
    }
    verify(connection, times(3)).close();
  }

  @Test
  public void testJtaTransactionWithIsolationLevelDataSourceAdapter() throws Exception {
    given(userTransaction.getStatus())
        .willReturn(
            Status.STATUS_NO_TRANSACTION,
            Status.STATUS_ACTIVE,
            Status.STATUS_ACTIVE,
            Status.STATUS_NO_TRANSACTION,
            Status.STATUS_ACTIVE,
            Status.STATUS_ACTIVE);

    final IsolationLevelDataSourceAdapter dsToUse = new IsolationLevelDataSourceAdapter();
    dsToUse.setTargetDataSource(dataSource);
    dsToUse.afterPropertiesSet();

    JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
    ptm.setAllowCustomIsolationLevels(true);

    TransactionTemplate tt = new TransactionTemplate(ptm);
    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            Connection c = DataSourceUtils.getConnection(dsToUse);
            assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                .as("Has thread connection")
                .isTrue();
            assertThat(c).isSameAs(connection);
            DataSourceUtils.releaseConnection(c, dsToUse);
          }
        });

    tt.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    tt.setReadOnly(true);
    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            Connection c = DataSourceUtils.getConnection(dsToUse);
            assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                .as("Has thread connection")
                .isTrue();
            assertThat(c).isSameAs(connection);
            DataSourceUtils.releaseConnection(c, dsToUse);
          }
        });

    verify(userTransaction, times(2)).begin();
    verify(userTransaction, times(2)).commit();
    verify(connection).setReadOnly(true);
    verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    verify(connection, times(2)).close();
  }

  @Test
  public void testJtaTransactionWithIsolationLevelDataSourceRouter() throws Exception {
    doTestJtaTransactionWithIsolationLevelDataSourceRouter(false);
  }

  @Test
  public void testJtaTransactionWithIsolationLevelDataSourceRouterWithDataSourceLookup()
      throws Exception {
    doTestJtaTransactionWithIsolationLevelDataSourceRouter(true);
  }

  private void doTestJtaTransactionWithIsolationLevelDataSourceRouter(boolean dataSourceLookup)
      throws Exception {
    given(userTransaction.getStatus())
        .willReturn(
            Status.STATUS_NO_TRANSACTION,
            Status.STATUS_ACTIVE,
            Status.STATUS_ACTIVE,
            Status.STATUS_NO_TRANSACTION,
            Status.STATUS_ACTIVE,
            Status.STATUS_ACTIVE);

    final DataSource dataSource1 = mock(DataSource.class);
    final Connection connection1 = mock(Connection.class);
    given(dataSource1.getConnection()).willReturn(connection1);

    final DataSource dataSource2 = mock(DataSource.class);
    final Connection connection2 = mock(Connection.class);
    given(dataSource2.getConnection()).willReturn(connection2);

    final IsolationLevelDataSourceRouter dsToUse = new IsolationLevelDataSourceRouter();
    Map<Object, Object> targetDataSources = new HashMap<>();
    if (dataSourceLookup) {
      targetDataSources.put("ISOLATION_REPEATABLE_READ", "ds2");
      dsToUse.setDefaultTargetDataSource("ds1");
      StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
      beanFactory.addBean("ds1", dataSource1);
      beanFactory.addBean("ds2", dataSource2);
      dsToUse.setDataSourceLookup(new BeanFactoryDataSourceLookup(beanFactory));
    } else {
      targetDataSources.put("ISOLATION_REPEATABLE_READ", dataSource2);
      dsToUse.setDefaultTargetDataSource(dataSource1);
    }
    dsToUse.setTargetDataSources(targetDataSources);
    dsToUse.afterPropertiesSet();

    JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
    ptm.setAllowCustomIsolationLevels(true);

    TransactionTemplate tt = new TransactionTemplate(ptm);
    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            Connection c = DataSourceUtils.getConnection(dsToUse);
            assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                .as("Has thread connection")
                .isTrue();
            assertThat(c).isSameAs(connection1);
            DataSourceUtils.releaseConnection(c, dsToUse);
          }
        });

    tt.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    tt.execute(
        new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(TransactionStatus status)
              throws RuntimeException {
            Connection c = DataSourceUtils.getConnection(dsToUse);
            assertThat(TransactionSynchronizationManager.hasResource(dsToUse))
                .as("Has thread connection")
                .isTrue();
            assertThat(c).isSameAs(connection2);
            DataSourceUtils.releaseConnection(c, dsToUse);
          }
        });

    verify(userTransaction, times(2)).begin();
    verify(userTransaction, times(2)).commit();
    verify(connection1).close();
    verify(connection2).close();
  }
}
