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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.DataSetBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DataSetProducerAdapter}.
 *
 * @since 3.2.0
 */
class DataSetProducerAdapterTest
{
    @Test
    void testProduce_withSingleTableDataSet_emitsCorrectEvents() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("CUSTOMER")
                .columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        final Column[] columns = new Column[]{
                new Column("ID", DataType.UNKNOWN),
                new Column("NAME", DataType.UNKNOWN)
        };
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable("CUSTOMER", columns);
        consumer.addExpectedRow("CUSTOMER", new Object[]{1, "Alice"});
        consumer.addExpectedRow("CUSTOMER", new Object[]{2, "Bob"});
        consumer.addExpectedEndTable("CUSTOMER");
        consumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        adapter.setConsumer(consumer);
        adapter.produce();

        consumer.verify();
    }

    @Test
    void testProduce_withMultipleTablesDataSet_emitsEventsForAllTables() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("ORDER")
                .columns("ORDER_ID")
                .row(100)
                .table("ITEM")
                .columns("ITEM_ID", "ORDER_ID")
                .row(1, 100)
                .build();
        final Column[] orderCols = {new Column("ORDER_ID", DataType.UNKNOWN)};
        final Column[] itemCols = {new Column("ITEM_ID", DataType.UNKNOWN),
                new Column("ORDER_ID", DataType.UNKNOWN)};
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable("ORDER", orderCols);
        consumer.addExpectedRow("ORDER", new Object[]{100});
        consumer.addExpectedEndTable("ORDER");
        consumer.addExpectedStartTable("ITEM", itemCols);
        consumer.addExpectedRow("ITEM", new Object[]{1, 100});
        consumer.addExpectedEndTable("ITEM");
        consumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        adapter.setConsumer(consumer);
        adapter.produce();

        consumer.verify();
    }

    @Test
    void testProduce_withEmptyTable_emitsStartAndEndTableWithNoRows() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("EMPTY_TABLE")
                .columns("ID")
                .build();
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        final Column[] columns = new Column[]{new Column("ID", DataType.UNKNOWN)};
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEmptyTable("EMPTY_TABLE", columns);
        consumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        adapter.setConsumer(consumer);
        adapter.produce();

        consumer.verify();
    }

    @Test
    void testProduce_withTableHavingNoColumns_emitsStartAndEndTableWithNoRows() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("NO_COLS")
                .build();
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEmptyTableIgnoreColumns("NO_COLS");
        consumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        adapter.setConsumer(consumer);
        adapter.produce();

        consumer.verify();
    }

    @Test
    void testProduce_withDefaultConsumerBeforeSet_doesNotThrow() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("FOO")
                .columns("X")
                .row(99)
                .build();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        // produce() before setConsumer uses the built-in DefaultConsumer - must not throw
        adapter.produce();
    }

    @Test
    void testConstructorWithIterator_withTableIterator_producesEventsFromIterator() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("T1")
                .columns("A")
                .row("val1")
                .build();
        final ITableIterator iterator = dataSet.iterator();
        final Column[] cols = {new Column("A", DataType.UNKNOWN)};
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable("T1", cols);
        consumer.addExpectedRow("T1", new Object[]{"val1"});
        consumer.addExpectedEndTable("T1");
        consumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(iterator);
        adapter.setConsumer(consumer);
        adapter.produce();

        consumer.verify();
    }

    @Test
    void testSetConsumer_withNewConsumer_replacesExistingConsumer() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("T")
                .columns("C")
                .row("v")
                .build();

        final MockDataSetConsumer firstConsumer = new MockDataSetConsumer();
        final MockDataSetConsumer secondConsumer = new MockDataSetConsumer();

        final Column[] cols = {new Column("C", DataType.UNKNOWN)};
        secondConsumer.addExpectedStartDataSet();
        secondConsumer.addExpectedStartTable("T", cols);
        secondConsumer.addExpectedRow("T", new Object[]{"v"});
        secondConsumer.addExpectedEndTable("T");
        secondConsumer.addExpectedEndDataSet();

        final DataSetProducerAdapter adapter = new DataSetProducerAdapter(dataSet);
        adapter.setConsumer(firstConsumer);
        adapter.setConsumer(secondConsumer);
        adapter.produce();

        // Only the second consumer should have received events
        secondConsumer.verify();
        assertThat(firstConsumer).as("first consumer is not null (just not used).").isNotNull();
    }
}
