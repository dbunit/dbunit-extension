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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.StringReader;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.AbstractProducerTest;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.dataset.stream.MockDataSetConsumer;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @since Apr 30, 2003
 * @version $Revision$
 */
public class XmlProducerTest extends AbstractProducerTest
{
    private static final File DATASET_FILE =
            TestUtils.getFile("xml/xmlProducerTest.xml");

    @Override
    protected IDataSetProducer createProducer() throws Exception
    {
        final String uri = DATASET_FILE.getAbsoluteFile().toURL().toString();
        final InputSource source = new InputSource(uri);

        final XmlProducer producer = new XmlProducer(source);
        producer.setValidating(true);
        return producer;
    }

    @Override
    protected Column[] createExpectedColumns(final Column.Nullable nullable)
            throws Exception
    {
        return super.createExpectedColumns(Column.NULLABLE_UNKNOWN);
    }

    @Test
    void testProduceEmptyDataSet() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset/>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new XmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceNullValue() throws Exception
    {
        final String tableName = "TEST_TABLE";
        final Column[] expectedColumns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final Object[] expectedValues = new Object[] {null, "", "value"};

        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable(tableName, expectedColumns);
        consumer.addExpectedRow(tableName, expectedValues);
        consumer.addExpectedEndTable(tableName);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                + "   <table name='TEST_TABLE'>" + "       <column>c1</column>"
                + "       <column>c2</column>" + "       <column>c3</column>"
                + "       <row>" + "           <null/>"
                + "           <value></value>"
                + "           <value>value</value>" + "       </row>"
                + "   </table>" + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new XmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceMissingColumn() throws Exception
    {
        final String tableName = "TEST_TABLE";
        final Column[] expectedColumns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final Object[] expectedValues =
                new Object[] {null, "", "value", "extra"};

        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable(tableName, expectedColumns);
        consumer.addExpectedRow(tableName, expectedValues);
        consumer.addExpectedEndTable(tableName);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                + "   <table name='TEST_TABLE'>" + "       <column>c1</column>"
                + "       <column>c2</column>" + "       <column>c3</column>"
                + "       <row>" + "           <null/>"
                + "           <value></value>"
                + "           <value>value</value>"
                + "           <value>extra</value>" + "       </row>"
                + "   </table>" + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new XmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceNotWellFormedXml() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new XmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        assertThrows(DataSetException.class, () -> producer.produce(),
                "Should not be here!");

        consumer.verify();
    }

    @Test
    void testProduceInvalidXml() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE dataset SYSTEM \"dataset.dtd\" >" + "<invalid/>";
        final InputSource source = new InputSource(new StringReader(content));
        source.setSystemId("http:/nowhere.to.go");
        final XmlProducer producer = new XmlProducer(source);
        producer.setValidating(true);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        assertThrows(DataSetException.class, () -> producer.produce(),
                "Should not be here!");

        consumer.verify();
    }

}
