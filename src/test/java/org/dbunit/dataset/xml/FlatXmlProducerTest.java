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
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.AbstractProducerTest;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.dataset.stream.MockDataSetConsumer;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Manuel Laflamme
 * @since Apr 28, 2003
 * @version $Revision$
 */
class FlatXmlProducerTest extends AbstractProducerTest
{
    private static final File DATASET_FILE =
            TestUtils.getFile("xml/flatXmlProducerTest.xml");

    @Override
    protected IDataSetProducer createProducer() throws Exception
    {
        final String uri = DATASET_FILE.getAbsoluteFile().toURL().toString();
        final InputSource source = new InputSource(uri);

        return new FlatXmlProducer(source);
    }

    @Test
    void testProduceEmptyDataSet_withEmptyDatasetXml_producesStartAndEndDataSetOnly() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset/>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new FlatXmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceNoDtd_withXmlHavingNoColumnsInDtd_producesEmptyTableWithNoColumns() throws Exception
    {
        // Setup consumer
        final String tableName = "EMPTY_TABLE";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        final Column[] expectedColumns = new Column[0];
        consumer.addExpectedEmptyTable(tableName, expectedColumns);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                + "<EMPTY_TABLE/>" + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new FlatXmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceIgnoreDtd_withDtdInXmlButIgnoreDtdFalse_producesEmptyTableWithNoColumns() throws Exception
    {
        // Setup consumer
        final String tableName = "EMPTY_TABLE";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        final Column[] expectedColumns = new Column[0];
        consumer.addExpectedEmptyTable(tableName, expectedColumns);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE dataset SYSTEM \"uri:/dummy.dtd\">" + "<dataset>"
                + "<EMPTY_TABLE/>" + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new FlatXmlProducer(source, false);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceMetaDataSet_withMetaDataSetProvided_usesMetaDataSetColumnsForEmptyTable() throws Exception
    {
        // Setup consumer
        final String tableName = "EMPTY_TABLE";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        final Column[] expectedColumns = createExpectedColumns(Column.NULLABLE);
        consumer.addExpectedEmptyTable(tableName, expectedColumns);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE dataset SYSTEM \"urn:/dummy.dtd\">" + "<dataset>"
                + "<EMPTY_TABLE/>" + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final DefaultDataSet metaDataSet = new DefaultDataSet();
        metaDataSet.addTable(new DefaultTable(tableName, expectedColumns));
        final IDataSetProducer producer =
                new FlatXmlProducer(source, metaDataSet);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceCustomEntityResolver_withCustomEntityResolver_usesResolverToLoadDtd() throws Exception
    {
        // Setup consumer
        final String tableName = "EMPTY_TABLE";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        final Column[] expectedColumns = createExpectedColumns(Column.NULLABLE);
        consumer.addExpectedEmptyTable(tableName, expectedColumns);
        consumer.addExpectedEndDataSet();

        // Setup producer
        final String dtdContent = "<!ELEMENT dataset (EMPTY_TABLE)>"
                + "<!ATTLIST EMPTY_TABLE " + "COLUMN0 CDATA #IMPLIED "
                + "COLUMN1 CDATA #IMPLIED " + "COLUMN2 CDATA #IMPLIED "
                + "COLUMN3 CDATA #IMPLIED>" + "<!ELEMENT TEST_TABLE EMPTY>";
        final InputSource dtdSource =
                new InputSource(new StringReader(dtdContent));

        final String xmlContent = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE dataset SYSTEM \"urn:/dummy.dtd\">" + "<dataset>"
                + "<EMPTY_TABLE/>" + "</dataset>";
        final InputSource xmlSource =
                new InputSource(new StringReader(xmlContent));
        final IDataSetProducer producer =
                new FlatXmlProducer(xmlSource, new EntityResolver()
                {
                    @Override
                    public InputSource resolveEntity(final String s,
                            final String s1) throws SAXException, IOException
                    {
                        return dtdSource;
                    }
                });
        producer.setConsumer(consumer);

        // Produce and verify consumer
        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduceNotWellFormedXml_withUnclosedDatasetTag_throwsDataSetException() throws Exception
    {
        // Setup consumer
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();

        // Setup producer
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer = new FlatXmlProducer(source);
        producer.setConsumer(consumer);

        // Produce and verify consumer
        assertThrows(DataSetException.class, () -> producer.produce(),
                "Should not be here!");

        consumer.verify();
    }

    @Test
    void testProduce_newColumnAppearsMidTable_columnSensed() throws Exception
    {
        final String tableName = "TABLE_NAME";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        // Column sensing runs through a BufferedConsumer, which replays startTable/row
        // once with the final, fully-merged metadata rather than forwarding the
        // intermediate per-row startTable calls FlatXmlProducer issues internally.
        consumer.addExpectedStartTable(tableName,
                new Column[] {new Column("COL0", DataType.UNKNOWN),
                        new Column("COL1", DataType.UNKNOWN)});
        consumer.addExpectedRow(tableName, new Object[] {"value0", null});
        consumer.addExpectedRow(tableName,
                new Object[] {"value0b", "value1b"});
        consumer.addExpectedEndTable(tableName);
        consumer.addExpectedEndDataSet();

        // No DTD, column sensing enabled: COL1 first appears on the second row.
        final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                + "<TABLE_NAME COL0=\"value0\"/>"
                + "<TABLE_NAME COL0=\"value0b\" COL1=\"value1b\"/>"
                + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer =
                new FlatXmlProducer(source, false, true);
        producer.setConsumer(consumer);

        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduce_columnSensingDisabled_missingColumnIgnored() throws Exception
    {
        final String tableName = "TABLE_NAME";
        final MockDataSetConsumer consumer = new MockDataSetConsumer();
        consumer.addExpectedStartDataSet();
        consumer.addExpectedStartTable(tableName,
                new Column[] {new Column("COL0", DataType.UNKNOWN)});
        consumer.addExpectedRow(tableName, new Object[] {"value0"});
        // COL1 on the second row is not part of the metadata and column sensing is
        // disabled, so it must be ignored rather than merged or thrown.
        consumer.addExpectedRow(tableName, new Object[] {"value0b"});
        consumer.addExpectedEndTable(tableName);
        consumer.addExpectedEndDataSet();

        final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                + "<TABLE_NAME COL0=\"value0\"/>"
                + "<TABLE_NAME COL0=\"value0b\" COL1=\"value1b\"/>"
                + "</dataset>";
        final InputSource source = new InputSource(new StringReader(content));
        final IDataSetProducer producer =
                new FlatXmlProducer(source, false, false);
        producer.setConsumer(consumer);

        producer.produce();
        consumer.verify();
    }

    @Test
    void testProduce_turkishLocaleAndCaseVariantColumnName_recognizesSameColumn() throws Exception
    {
        final Locale originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try
        {
            final String tableName = "TABLE_NAME";
            final MockDataSetConsumer consumer = new MockDataSetConsumer();
            consumer.addExpectedStartDataSet();
            consumer.addExpectedStartTable(tableName,
                    new Column[] {new Column("id", DataType.UNKNOWN)});
            consumer.addExpectedRow(tableName, new Object[] {"1"});
            consumer.addExpectedRow(tableName, new Object[] {"2"});
            consumer.addExpectedEndTable(tableName);
            consumer.addExpectedEndDataSet();

            // Row 2's "ID" is the same logical column as row 1's "id", just differently
            // cased. Under the Turkish default locale, "id".toUpperCase() produces a
            // dotted capital I ("İD") that does not equal "ID".toUpperCase() ("ID"),
            // so a locale-sensitive comparison would wrongly treat row 2's ID as a new,
            // unrelated column instead of recognizing it as "id".
            final String content = "<?xml version=\"1.0\"?>" + "<dataset>"
                    + "<TABLE_NAME id=\"1\"/>" + "<TABLE_NAME ID=\"2\"/>"
                    + "</dataset>";
            final InputSource source = new InputSource(new StringReader(content));
            final IDataSetProducer producer =
                    new FlatXmlProducer(source, false, true);
            producer.setConsumer(consumer);

            producer.produce();
            consumer.verify();
        }
        finally
        {
            Locale.setDefault(originalLocale);
        }
    }

}
