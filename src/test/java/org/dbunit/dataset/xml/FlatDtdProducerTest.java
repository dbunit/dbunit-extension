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
package org.dbunit.dataset.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.stream.AbstractProducerTest;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.dataset.stream.MockDataSetConsumer;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @since Apr 29, 2003
 * @version $Revision$
 */
class FlatDtdProducerTest extends AbstractProducerTest
{
    private static final File DTD_FILE =
            TestUtils.getFile("dtd/flatDtdProducerTest.dtd");

    @Override
    protected IDataSetProducer createProducer() throws Exception
    {
        final InputSource source =
                new InputSource(new FileInputStream(DTD_FILE));
        return new FlatDtdProducer(source);
    }

    @Override
    protected int[] getExpectedRowCount() throws Exception
    {
        return new int[] {0, 0, 0, 0, 0, 0};
    }

    @Test
    void testSequenceModel() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEmptyTableIgnoreColumns("DUPLICATE_TABLE");
        consumer.addExpectedEmptyTableIgnoreColumns("TEST_TABLE");
        consumer.addExpectedEmptyTableIgnoreColumns("DUPLICATE_TABLE");
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content =
                "<!ELEMENT dataset (DUPLICATE_TABLE*,TEST_TABLE+,DUPLICATE_TABLE?)>"
                        + "<!ELEMENT TEST_TABLE EMPTY>"
                        + "<!ELEMENT DUPLICATE_TABLE EMPTY>";
        final InputSource source = new InputSource(new StringReader(content));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testChoicesModel() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEmptyTableIgnoreColumns("TEST_TABLE");
        consumer.addExpectedEmptyTableIgnoreColumns("SECOND_TABLE");
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<!ELEMENT dataset (TEST_TABLE|SECOND_TABLE)>"
                + "<!ELEMENT TEST_TABLE EMPTY>"
                + "<!ELEMENT SECOND_TABLE EMPTY>";
        final InputSource source = new InputSource(new StringReader(content));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testChoicesModel_ElementDeclarationForTableMissing() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        // consumer.addExpectedStartDataSet();
        // consumer.addExpectedEmptyTableIgnoreColumns("TEST_TABLE");
        // consumer.addExpectedEmptyTableIgnoreColumns("SECOND_TABLE");
        // consumer.addExpectedEndDataSet();

        // Setup producer
        final String dtdChoice =
                "<!ELEMENT dataset ( (TEST_TABLE|SECOND_TABLE)* )><!ELEMENT TEST_TABLE EMPTY>";
        final InputSource source = new InputSource(new StringReader(dtdChoice));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        final DataSetException expected = assertThrows(DataSetException.class,
                () -> producer.produce(),
                "Should not be able to produce the dataset from an incomplete DTD");

        final String expectedStartsWith = "ELEMENT/ATTRIBUTE declaration for '"
                + "SECOND_TABLE" + "' is missing. ";
        assertThat(expected).hasMessageStartingWith(expectedStartsWith);
        // consumer.verify();
    }

    @Test
    void testAttrListBeforeParentElement() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        final Column[] expectedColumns = createExpectedColumns(Column.NULLABLE);
        consumer.addExpectedEmptyTable("TEST_TABLE", expectedColumns);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<!ELEMENT dataset (TEST_TABLE)>"
                + "<!ATTLIST TEST_TABLE " + "COLUMN0 CDATA #IMPLIED "
                + "COLUMN1 CDATA #IMPLIED " + "COLUMN2 CDATA #IMPLIED "
                + "COLUMN3 CDATA #IMPLIED>" + "<!ELEMENT TEST_TABLE EMPTY>";

        final InputSource source = new InputSource(new StringReader(content));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testCleanupTableName() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEmptyTableIgnoreColumns("TABLE_1");
        consumer.addExpectedEmptyTableIgnoreColumns("TABLE_2");
        consumer.addExpectedEmptyTableIgnoreColumns("TABLE_3");
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content =
                "<!ELEMENT dataset (TABLE_1,(TABLE_2,TABLE_3+)?)+>"
                        + "<!ELEMENT TABLE_1 EMPTY>"
                        + "<!ELEMENT TABLE_2 EMPTY>"
                        + "<!ELEMENT TABLE_3 EMPTY>";

        final InputSource source = new InputSource(new StringReader(content));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testANYModel() throws Exception
    {
        // Setup consumer
        final FlatDtdDataSet consumer = new FlatDtdDataSet();

        // Setup producer
        final String content =
                "<!ELEMENT dataset ANY>" + "<!ELEMENT TEST_TABLE EMPTY>"
                        + "<!ELEMENT SECOND_TABLE EMPTY>";
        final InputSource source = new InputSource(new StringReader(content));
        final FlatDtdProducer producer = new FlatDtdProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        assertThat(consumer.getTables()).hasSize(2);
        assertThat(consumer.getTable("TEST_TABLE")).isNotNull();
        assertThat(consumer.getTable("SECOND_TABLE")).isNotNull();
    }

}
