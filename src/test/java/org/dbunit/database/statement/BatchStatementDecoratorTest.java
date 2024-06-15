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

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
class BatchStatementDecoratorTest
{

    @Test
    void testAddBatch() throws Exception
    {
        final String template = "START VAL0 = ?, VAL1 = ?, VAL2 = ? END";
        final String expected =
                "START VAL0 = NULL, VAL1 = 'value', VAL2 = 1234 END";
        final Object[] values =
                new Object[] {null, "value", Integer.valueOf(1234)};

        final MockBatchStatement mockStatement = new MockBatchStatement();
        mockStatement.addExpectedBatchString(expected);
        mockStatement.setExpectedExecuteBatchCalls(1);
        mockStatement.setExpectedClearBatchCalls(1);
        mockStatement.setExpectedCloseCalls(1);

        final IPreparedBatchStatement preparedStatement =
                new BatchStatementDecorator(template, mockStatement);

        for (int i = 0; i < values.length; i++)
        {
            final Object value = values[i];
            preparedStatement.addValue(value, DataType.forObject(value));
        }
        preparedStatement.addBatch();
        assertThat(preparedStatement.executeBatch()).as("execute result")
                .isEqualTo(1);
        preparedStatement.clearBatch();
        preparedStatement.close();
        mockStatement.verify();
    }

    @Test
    void testMultipleAddBatch() throws Exception
    {
        final String template = "I am ?";
        final String[] expected =
                {"I am 'Manuel'", "I am 'not here'", "I am 'fine'"};
        final String[] values = {"Manuel", "not here", "fine"};

        final MockBatchStatement mockStatement = new MockBatchStatement();
        mockStatement.addExpectedBatchStrings(expected);
        mockStatement.setExpectedExecuteBatchCalls(1);
        mockStatement.setExpectedClearBatchCalls(1);
        mockStatement.setExpectedCloseCalls(1);

        final IPreparedBatchStatement preparedStatement =
                new BatchStatementDecorator(template, mockStatement);

        for (int i = 0; i < values.length; i++)
        {
            final Object value = values[i];
            preparedStatement.addValue(value, DataType.VARCHAR);
            preparedStatement.addBatch();
        }
        assertThat(preparedStatement.executeBatch()).as("execute result")
                .isEqualTo(values.length);
        mockStatement.clearBatch();
        mockStatement.close();
        mockStatement.verify();
    }

}
