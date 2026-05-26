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
package org.dbunit.database.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AutomaticPreparedBatchStatement}.
 *
 * <p>{@link AutomaticPreparedBatchStatement} wraps another {@link IPreparedBatchStatement}
 * and automatically flushes the underlying batch whenever the number of accumulated rows
 * reaches the configured threshold.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticPreparedBatchStatementTest
{
    @Mock
    private IPreparedBatchStatement delegate;

    @Test
    void testAddBatch_belowThreshold_doesNotCallExecuteBatch() throws Exception
    {
        final int threshold = 5;
        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.addBatch();
        statement.addBatch();
        statement.addBatch();

        verify(delegate, times(3)).addBatch();
        verify(delegate, times(0)).executeBatch();
    }

    @Test
    void testAddBatch_atThreshold_triggersExecuteBatch() throws Exception
    {
        final int threshold = 3;
        when(delegate.executeBatch()).thenReturn(3);

        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.addBatch();
        statement.addBatch();
        statement.addBatch();

        verify(delegate, times(1)).executeBatch();
    }

    @Test
    void testAddBatch_atMultiplesOfThreshold_triggersExecuteBatchEachTime() throws Exception
    {
        final int threshold = 2;
        when(delegate.executeBatch()).thenReturn(2);

        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.addBatch();
        statement.addBatch();
        statement.addBatch();
        statement.addBatch();

        verify(delegate, times(2)).executeBatch();
    }

    @Test
    void testExecuteBatch_whenAutoFlushPreceded_returnsAccumulatedCount() throws Exception
    {
        final int threshold = 2;
        when(delegate.executeBatch()).thenReturn(2);

        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.addBatch();
        statement.addBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("accumulated result includes auto-flush.").isEqualTo(4);
    }

    @Test
    void testExecuteBatch_withNoAutoFlush_returnsRemainingCount() throws Exception
    {
        final int threshold = 10;
        when(delegate.executeBatch()).thenReturn(3);

        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.addBatch();
        statement.addBatch();
        statement.addBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("result from explicit execute.").isEqualTo(3);
    }

    @Test
    void testClearBatch_resetsBatchCount_soThresholdIsRelativeToAfterClear() throws Exception
    {
        final int threshold = 3;
        when(delegate.executeBatch()).thenReturn(3);

        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        // Add 2 batches — not at threshold yet
        statement.addBatch();
        statement.addBatch();
        // Clear resets count to 0
        statement.clearBatch();

        // After clear, add 2 more — still not at threshold (need 3)
        statement.addBatch();
        statement.addBatch();

        verify(delegate, times(0)).executeBatch();

        // Third addBatch after clear hits threshold=3
        statement.addBatch();

        verify(delegate, times(1)).executeBatch();
    }

    @Test
    void testAddValue_withValueAndDataType_delegatesToUnderlyingStatement() throws Exception
    {
        final int threshold = 5;
        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);
        final Object value = "hello";
        final DataType dataType = DataType.VARCHAR;

        statement.addValue(value, dataType);

        verify(delegate, times(1)).addValue(value, dataType);
    }

    @Test
    void testClose_whenCalled_delegatesToUnderlyingStatement() throws Exception
    {
        final int threshold = 5;
        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.close();

        verify(delegate, times(1)).close();
    }

    @Test
    void testClearBatch_whenCalled_delegatesToUnderlyingStatement() throws Exception
    {
        final int threshold = 5;
        final AutomaticPreparedBatchStatement statement =
                new AutomaticPreparedBatchStatement(delegate, threshold);

        statement.clearBatch();

        verify(delegate, times(1)).clearBatch();
    }
}
