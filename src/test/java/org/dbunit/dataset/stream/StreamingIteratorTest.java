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
package org.dbunit.dataset.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @since 3.4.0
 */
class StreamingIteratorTest
{
    private static final ITableMetaData TABLE_META_DATA =
            new DefaultTableMetaData("TABLE",
                    new Column[] {new Column("COLUMN", DataType.VARCHAR)});

    private static final int ITERATIONS = 20;

    /**
     * The producer's failure races the consumer thread's interruption. Depending on
     * scheduling, the channel's entry-point interruption check can observe the
     * interrupt on either the constructor's first read or the subsequent row read,
     * so the whole sequence is wrapped in one assertion; the scenario is repeated
     * several times since the field-visibility bug this guards against is not
     * guaranteed to reproduce on every single run.
     * <p>
     * This is a best-effort, probabilistic guard, not a proof: strongly-ordered
     * architectures (x86/x64, which is what CI runs on) rarely surface the stale
     * read this test targets even without the volatile fix, so a regression here
     * could pass silently on this hardware. Reliably verifying the happens-before
     * edge instead of relying on scheduling luck would need a tool like jcstress,
     * which is out of scope for this project.
     */
    @Test
    void testGetValue_producerThrowsAfterStartTable_reportsProducerExceptionAsCause()
    {
        for (int i = 0; i < ITERATIONS; i++)
        {
            final DataSetException producerException = new DataSetException(
                    "Producer failed after starting the table, iteration " + i
                            + ".");

            assertThatThrownBy(() -> {
                final StreamingIterator iterator = new StreamingIterator(
                        new FailAfterStartTableProducer(producerException));
                iterator.next();
                final ITable table = iterator.getTable();
                table.getValue(0, "COLUMN");
            }).as("The consumer must surface the producer's real failure instead of a bare interruption message, regardless of which blocking read observes the interrupt.")
                    .isInstanceOf(DataSetException.class)
                    .hasCause(producerException);
        }
    }

    private static final class FailAfterStartTableProducer
            implements IDataSetProducer
    {
        private final DataSetException exceptionToThrow;
        private IDataSetConsumer consumer;

        FailAfterStartTableProducer(final DataSetException exceptionToThrow)
        {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public void setConsumer(final IDataSetConsumer consumer)
        {
            this.consumer = consumer;
        }

        @Override
        public void produce() throws DataSetException
        {
            consumer.startDataSet();
            consumer.startTable(TABLE_META_DATA);
            throw exceptionToThrow;
        }
    }
}
