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

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.dbunit.dataset.MockTableMetaData;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultConsumer}.
 *
 * <p>All methods of {@link DefaultConsumer} are no-ops; the tests verify that
 * each method completes without throwing an exception.
 *
 * @since 3.2.0
 */
class DefaultConsumerTest
{
    @Test
    void testStartDataSet_withNoState_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("startDataSet does not throw.")
                .isThrownBy(consumer::startDataSet);
    }

    @Test
    void testEndDataSet_withNoState_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("endDataSet does not throw.")
                .isThrownBy(consumer::endDataSet);
    }

    @Test
    void testStartTable_withValidMetaData_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();
        final MockTableMetaData metaData =
                new MockTableMetaData("MY_TABLE", new String[]{"ID", "NAME"});

        assertThatNoException().as("startTable does not throw.")
                .isThrownBy(() -> consumer.startTable(metaData));
    }

    @Test
    void testStartTable_withNullMetaData_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("startTable with null does not throw.")
                .isThrownBy(() -> consumer.startTable(null));
    }

    @Test
    void testEndTable_withNoState_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("endTable does not throw.")
                .isThrownBy(consumer::endTable);
    }

    @Test
    void testRow_withNonNullValues_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();
        final Object[] values = new Object[]{"value1", 42, null};

        assertThatNoException().as("row with values does not throw.")
                .isThrownBy(() -> consumer.row(values));
    }

    @Test
    void testRow_withNullArray_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("row with null array does not throw.")
                .isThrownBy(() -> consumer.row(null));
    }

    @Test
    void testRow_withEmptyArray_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();

        assertThatNoException().as("row with empty array does not throw.")
                .isThrownBy(() -> consumer.row(new Object[0]));
    }

    @Test
    void testFullLifecycle_withTypicalSequence_doesNotThrow()
    {
        final DefaultConsumer consumer = new DefaultConsumer();
        final MockTableMetaData metaData =
                new MockTableMetaData("FOO", new String[]{"COL"});

        assertThatNoException().as("full lifecycle does not throw.")
                .isThrownBy(() -> {
                    consumer.startDataSet();
                    consumer.startTable(metaData);
                    consumer.row(new Object[]{"r1"});
                    consumer.row(new Object[]{"r2"});
                    consumer.endTable();
                    consumer.endDataSet();
                });
    }
}
