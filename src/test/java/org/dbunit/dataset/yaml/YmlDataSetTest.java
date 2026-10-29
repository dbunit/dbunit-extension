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
package org.dbunit.dataset.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetBuilder;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */
class YmlDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final InputStream in =
                Files.newInputStream(TestUtils.getFile("yaml/dataSetTest.yml").toPath());
        return new YamlDataSet(in);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        final InputStream in = Files.newInputStream(
                TestUtils.getFile("yaml/yamlDataSetDuplicateTest.yml").toPath());
        return new YamlDataSet(in);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException(
                "Yaml is always case-sensitive");
    }

    @Override
    @Disabled("Not applicable, Yaml is always case-sensitive")
    @Test
    public void testCreateMultipleCaseDuplicateDataSet_withDuplicateCaseVariantNames_throwsAmbiguousTableNameException() throws Exception
    {
        // Not applicable, Yaml is always case-sensitive
    }

    @Override
    @Disabled("Not applicable, Yaml is always case-sensitive")
    @Test
    public void testGetCaseInsensitiveTable_withLowercaseTableName_returnsTable() throws Exception
    {
        // Not applicable, Yaml is always case-sensitive
    }

    @Override
    @Disabled("Not applicable, Yaml is always case-sensitive")
    @Test
    public void testGetCaseInsensitiveTableMetaData_withLowercaseTableName_returnsMetaData() throws Exception
    {
        // Not applicable, Yaml is always case-sensitive
    }

    @Test
    void testWrite_withValidDataSet_writesAndReadsBackEquivalentData() throws Exception
    {
        final IDataSet expectedDataSet = createDataSet();
        final File tempFile = Files.createTempFile("dataSetTest", ".yml").toFile();
        try
        {
            final OutputStream out = Files.newOutputStream(tempFile.toPath());

            try
            {
                // write dataset in temp file
                YamlDataSet.write(expectedDataSet, out);

                // load new dataset from temp file
                final IDataSet actualDataSet =
                        new YamlDataSet(Files.newInputStream(tempFile.toPath()));

                // verify table count
                assertThat(actualDataSet.getTableNames()).as("table count")
                        .hasSameSizeAs(expectedDataSet.getTableNames());

                // verify each table
                final ITable[] expected =
                        DataSetUtils.getTables(expectedDataSet);
                final ITable[] actual = DataSetUtils.getTables(actualDataSet);
                assertThat(actual).as("table count").hasSameSizeAs(expected);
                for (int i = 0; i < expected.length; i++)
                {
                    final String expectedName =
                            expected[i].getTableMetaData().getTableName();
                    final String actualName =
                            actual[i].getTableMetaData().getTableName();
                    assertThat(actualName).as("table name")
                            .isEqualTo(expectedName);

                    assertThat(actual[i]).as("not same instance")
                            .isNotSameAs(expected[i]);
                    Assertion.assertEquals(expected[i], actual[i]);
                }
            } finally
            {
                out.close();
            }
        } finally
        {
            tempFile.delete();
        }
    }

    @Test
    void testWrite_dateTimeAndDecimalValues_readsThemBackAsTheirText() throws Exception
    {
        final Column[] columns = {new Column("EVENT_DAY", DataType.DATE),
                new Column("STARTS_AT", DataType.TIMESTAMP),
                new Column("AMOUNT", DataType.DECIMAL)};
        final DefaultTable table = new DefaultTable("EVENT", columns);
        table.addRow(new Object[] {Date.valueOf("2024-03-01"),
                Timestamp.valueOf("2024-03-01 10:15:00"),
                new BigDecimal("12345678901234567890.123")});
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        YamlDataSet.write(new DefaultDataSet(table), out);
        final IDataSet readBack =
                new YamlDataSet(new ByteArrayInputStream(out.toByteArray()));

        final ITable readBackTable = readBack.getTable("EVENT");
        final Object[] readBackRow = {readBackTable.getValue(0, "EVENT_DAY"),
                readBackTable.getValue(0, "STARTS_AT"), readBackTable.getValue(0, "AMOUNT")};
        assertThat(readBackRow)
                .as("A date, a timestamp and a decimal are written in the text a column type"
                        + " parses, not as an instant in UTC or a double, so they read back"
                        + " unchanged in any time zone.")
                .containsExactly("2024-03-01", "2024-03-01 10:15:00.0",
                        "12345678901234567890.123");
    }

    @Test
    void testWrite_javaTimeValues_readsThemBackAsTheSameColumnValues() throws Exception
    {
        final Column[] columns = {new Column("EVENT_DAY", DataType.DATE),
                new Column("STARTS_AT", DataType.TIMESTAMP),
                new Column("STARTS", DataType.TIME),
                new Column("RECORDED_AT", DataType.TIMESTAMP)};
        final DefaultTable table = new DefaultTable("EVENT", columns);
        final LocalDate eventDay = LocalDate.of(2024, 3, 1);
        final LocalDateTime startsAt = LocalDateTime.of(2024, 3, 1, 10, 15);
        final LocalTime starts = LocalTime.of(10, 15);
        final Instant recordedAt = Instant.parse("2024-03-01T16:15:30Z");
        table.addRow(new Object[] {eventDay, startsAt, starts, recordedAt});
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        YamlDataSet.write(new DefaultDataSet(table), out);
        final IDataSet readBack =
                new YamlDataSet(new ByteArrayInputStream(out.toByteArray()));

        final ITable readBackTable = readBack.getTable("EVENT");
        final Object[] readBackRow = {
                DataType.DATE.typeCast(readBackTable.getValue(0, "EVENT_DAY")),
                DataType.TIMESTAMP.typeCast(readBackTable.getValue(0, "STARTS_AT")),
                DataType.TIME.typeCast(readBackTable.getValue(0, "STARTS")),
                DataType.TIMESTAMP.typeCast(readBackTable.getValue(0, "RECORDED_AT"))};
        assertThat(readBackRow)
                .as("A java.time date, date and time, time or instant is written in the text a"
                        + " column type parses, so the column type reads back the same value.")
                .containsExactly(Date.valueOf(eventDay), Timestamp.valueOf(startsAt),
                        Time.valueOf(starts), Timestamp.from(recordedAt));
    }

    @Test
    void testWrite_largeDataSetToMemoryStream_writesCompleteOutput() throws Exception
    {
        final int rowCount = 2000;
        final DataSetBuilder.TableBuilder builder =
                new DataSetBuilder().table("MANY_ROWS").columns("ID", "NAME");
        for (int i = 0; i < rowCount; i++)
        {
            builder.row(i, "name-" + i);
        }
        final IDataSet expectedDataSet = builder.build();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        YamlDataSet.write(expectedDataSet, out);

        final IDataSet actualDataSet =
                new YamlDataSet(new ByteArrayInputStream(out.toByteArray()));

        assertThat(actualDataSet.getTable("MANY_ROWS").getRowCount())
                .as("All rows should be present in the in-memory stream after write() returns.")
                .isEqualTo(rowCount);
        Assertion.assertEquals(expectedDataSet, actualDataSet);
    }

    @Test
    void testWrite_withNonAsciiValue_roundTripsAsUtf8() throws Exception
    {
        final String nonAsciiValue = "café";
        final IDataSet expectedDataSet = new DataSetBuilder()
                .table("NON_ASCII_TABLE").columns("VALUE").row(nonAsciiValue)
                .build();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        YamlDataSet.write(expectedDataSet, out);

        final IDataSet actualDataSet =
                new YamlDataSet(new ByteArrayInputStream(out.toByteArray()));

        assertThat(actualDataSet.getTable("NON_ASCII_TABLE").getValue(0,
                "VALUE"))
                        .as("A non-ASCII value must round-trip through"
                                + " UTF-8-encoded YAML regardless of the"
                                + " platform default charset.")
                        .isEqualTo(nonAsciiValue);
    }

}
