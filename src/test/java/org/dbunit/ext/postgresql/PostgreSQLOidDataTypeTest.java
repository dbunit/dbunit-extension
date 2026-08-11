/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.postgresql.util.PSQLState;

/**
 * Unit tests for {@link PostgreSQLOidDataType}.
 *
 * @author DbUnit.org
 */
@ExtendWith(MockitoExtension.class)
class PostgreSQLOidDataTypeTest
{
    private static final int COLUMN = 1;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Statement statement;

    @Mock
    private Connection connection;

    @Mock
    private PGConnection pgConnection;

    @Mock
    private LargeObjectManager largeObjectManager;

    @Mock
    private LargeObject largeObject;

    @Mock
    private Savepoint savepoint;

    private final PostgreSQLOidDataType type = new PostgreSQLOidDataType();

    private void mockConnectionChain() throws SQLException
    {
        mockConnectionChain(true);
    }

    private void mockConnectionChain(final boolean ambientAutoCommit) throws SQLException
    {
        when(resultSet.getStatement()).thenReturn(statement);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(ambientAutoCommit);
        when(connection.unwrap(PGConnection.class)).thenReturn(pgConnection);
        when(pgConnection.getLargeObjectAPI()).thenReturn(largeObjectManager);
    }

    @Test
    void testGetSqlType_onNewInstance_returnsTypesBigint()
    {
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.BIGINT for OID type.")
                .isEqualTo(Types.BIGINT);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        assertThat(type.isNumber())
                .as("isNumber() should return false for OID type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsByteArrayClass()
    {
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return byte[].class for OID type.")
                .isEqualTo(byte[].class);
    }

    @Test
    void testInstantiation_withNoArgs_createsNonNullInstance()
    {
        assertThat(type)
                .as("PostgreSQLOidDataType should be instantiable with no arguments.")
                .isNotNull();
    }

    @Test
    void testGetSqlValue_withZeroOid_returnsNullWithoutOpeningLargeObject() throws Exception
    {
        mockConnectionChain();
        when(resultSet.getLong(COLUMN)).thenReturn(0L);

        final Object result = type.getSqlValue(COLUMN, resultSet);

        assertThat(result)
                .as("getSqlValue() should return null for a zero oid.")
                .isNull();
        verify(largeObjectManager, never()).open(anyLong(), anyInt());
        verify(connection, never()).setSavepoint();
        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);
    }

    /**
     * Issue 693: a live large object should still round-trip exactly as before.
     */
    @Test
    void testGetSqlValue_whenOidIsALargeObject_returnsItsBytes() throws Exception
    {
        mockConnectionChain();
        final byte[] data = {1, 2, 3, 4, 5};
        when(resultSet.getLong(COLUMN)).thenReturn(42L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        when(largeObjectManager.open(42L, LargeObjectManager.READ)).thenReturn(largeObject);
        when(largeObject.size()).thenReturn(data.length);
        when(largeObject.read(any(byte[].class), eq(0), eq(data.length))).thenAnswer(invocation -> {
            final byte[] buf = invocation.getArgument(0);
            System.arraycopy(data, 0, buf, 0, data.length);
            return data.length;
        });

        final Object result = type.getSqlValue(COLUMN, resultSet);

        assertThat(result)
                .as("getSqlValue() should return the large object's bytes.")
                .isEqualTo(data);
        verify(connection, never()).rollback(any(Savepoint.class));
        verify(largeObject).close();
    }

    /**
     * Issue 693: an oid that is not a large object (e.g. a row oid from
     * <code>'table'::regclass</code>) must not fail the whole read - PostgreSQL reports this as
     * SQLState 42704 (undefined_object).
     */
    @Test
    void testGetSqlValue_whenOidIsNotALargeObject_returnsNullAndRecoversTransaction() throws Exception
    {
        mockConnectionChain();
        when(resultSet.getLong(COLUMN)).thenReturn(16548L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        final SQLException notALargeObject = new SQLException("large object 16548 does not exist",
                PSQLState.UNDEFINED_OBJECT.getState());
        when(largeObjectManager.open(16548L, LargeObjectManager.READ)).thenThrow(notALargeObject);

        final Object result = type.getSqlValue(COLUMN, resultSet);

        assertThat(result)
                .as("getSqlValue() should return null when the oid is not a large object.")
                .isNull();
        verify(connection).rollback(savepoint);
        verify(connection).setAutoCommit(true);
    }

    /**
     * Issue 693: only the "not a large object" SQLState is swallowed - any other failure (e.g. a
     * permission error) must still propagate, matching the original bug report's requirement that
     * hiding real errors would be the wrong fix.
     */
    @Test
    void testGetSqlValue_whenOpenFailsForAnotherReason_rethrowsAndRecoversTransaction() throws Exception
    {
        mockConnectionChain();
        when(resultSet.getLong(COLUMN)).thenReturn(16548L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        final SQLException permissionDenied =
                new SQLException("permission denied for large object 16548", "42501");
        when(largeObjectManager.open(16548L, LargeObjectManager.READ)).thenThrow(permissionDenied);

        assertThatThrownBy(() -> type.getSqlValue(COLUMN, resultSet))
                .as("getSqlValue() should propagate a SQLException unrelated to a missing large object.")
                .isSameAs(permissionDenied);
        verify(connection).rollback(savepoint);
        verify(connection).setAutoCommit(true);
    }

    /**
     * Issue 693: a failure reading a large object that did open (e.g. its catalog row is deleted
     * mid-transaction) is not "not a large object" and must not be swallowed as null, even
     * though PostgreSQL aborts the transaction the same way an open() failure does.
     */
    @Test
    void testGetSqlValue_whenOpenSucceedsButSizeFails_rethrowsAndRecoversTransaction() throws Exception
    {
        mockConnectionChain();
        when(resultSet.getLong(COLUMN)).thenReturn(42L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        when(largeObjectManager.open(42L, LargeObjectManager.READ)).thenReturn(largeObject);
        final SQLException undefinedObjectAfterOpen = new SQLException("large object 42 does not exist",
                PSQLState.UNDEFINED_OBJECT.getState());
        when(largeObject.size()).thenThrow(undefinedObjectAfterOpen);

        assertThatThrownBy(() -> type.getSqlValue(COLUMN, resultSet))
                .as("getSqlValue() must not treat a post-open failure as \"not a large object\", "
                        + "even with the same SQLState an open() failure would have.")
                .isSameAs(undefinedObjectAfterOpen);
        verify(connection).rollback(savepoint);
        verify(largeObject, never()).close();
    }

    /**
     * Issue 693: same as the size()-failure case, but failing on close() after a successful read
     * - the bytes were already read successfully, so the failure must still propagate rather
     * than silently discarding a value that was in fact read.
     */
    @Test
    void testGetSqlValue_whenReadSucceedsButCloseFails_rethrowsAndRecoversTransaction() throws Exception
    {
        mockConnectionChain();
        final byte[] data = {1, 2, 3};
        when(resultSet.getLong(COLUMN)).thenReturn(42L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        when(largeObjectManager.open(42L, LargeObjectManager.READ)).thenReturn(largeObject);
        when(largeObject.size()).thenReturn(data.length);
        when(largeObject.read(any(byte[].class), eq(0), eq(data.length))).thenAnswer(invocation -> {
            final byte[] buf = invocation.getArgument(0);
            System.arraycopy(data, 0, buf, 0, data.length);
            return data.length;
        });
        final SQLException closeFailed = new SQLException("connection reset", "08006");
        doThrow(closeFailed).when(largeObject).close();

        assertThatThrownBy(() -> type.getSqlValue(COLUMN, resultSet))
                .as("getSqlValue() must propagate a close() failure instead of masking it.")
                .isSameAs(closeFailed);
        verify(connection).rollback(savepoint);
    }

    /**
     * Issue 693: when the caller already has auto-commit disabled (e.g. an externally managed
     * transaction), getSqlValue() must not enable it, and the savepoint/rollback recovery must
     * still work identically to the auto-commit-enabled case.
     */
    @Test
    void testGetSqlValue_whenAmbientAutoCommitIsAlreadyFalse_leavesItFalseAndStillRecovers() throws Exception
    {
        mockConnectionChain(false);
        when(resultSet.getLong(COLUMN)).thenReturn(16548L);
        when(connection.setSavepoint()).thenReturn(savepoint);
        final SQLException notALargeObject = new SQLException("large object 16548 does not exist",
                PSQLState.UNDEFINED_OBJECT.getState());
        when(largeObjectManager.open(16548L, LargeObjectManager.READ)).thenThrow(notALargeObject);

        final Object result = type.getSqlValue(COLUMN, resultSet);

        assertThat(result)
                .as("getSqlValue() should still return null for a non-large-object oid when "
                        + "auto-commit was already disabled.")
                .isNull();
        verify(connection, never()).setAutoCommit(true);
        verify(connection).rollback(savepoint);
    }
}
