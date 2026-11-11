/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.ext.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.Map;
import java.sql.Connection;
import java.sql.Statement;

import org.dbunit.H2Environment;
import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.assertion.DbUnitValueComparerAssert;
import org.dbunit.assertion.comparer.value.builder.ColumnValueComparerMapBuilder;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.assertion.comparer.value.IsActualEqualToExpectedJsonValueComparer;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * Proves {@link H2DataTypeFactory} handles an H2 {@code JSON} column end-to-end through a live
 * in-memory H2 connection: the column is part of the database dataset, loads from a flat XML
 * dataset as the document and not as a JSON string, and is verified with the JSON value comparer.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class H2JsonTypeIT
{
    private static final String TABLE = "H2_JSON_TYPE_TABLE";

    private final DbUnitValueComparerAssert sut = new DbUnitValueComparerAssert();
    private Connection jdbcConnection;
    private IDatabaseConnection connection;

    @BeforeEach
    void setUp() throws Exception
    {
        jdbcConnection = H2Environment.createJdbcConnection("h2jsontypeit");
        try (Statement statement = jdbcConnection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            statement.execute("CREATE TABLE " + TABLE + " (ID INT NOT NULL PRIMARY KEY, DOC JSON)");
        }
        connection = new H2Connection(jdbcConnection, null);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        try (Statement statement = jdbcConnection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        }
        connection.close();
    }

    @Test
    void testCreateDataSet_tableWithJsonColumn_keepsTheColumnAsH2JsonType() throws Exception
    {
        final IDataSet dataSet = connection.createDataSet();

        final Column[] columns = dataSet.getTableMetaData(TABLE).getColumns();
        assertThat(columns).as("The JSON column is not dropped from the table.").hasSize(2);
        assertThat(columns[1].getDataType()).as("The type of the JSON column.")
                .isInstanceOf(H2JsonType.class);
    }

    @Test
    void testInsert_jsonFromFlatXml_storesTheDocumentAndReadsItBack() throws Exception
    {
        final IDataSet dataSet = flatXml("<dataset><" + TABLE
                + " ID=\"1\" DOC='{\"a\":1,\"b\":[true,null]}'/></dataset>");

        DatabaseOperation.INSERT.execute(connection, dataSet);

        try (Statement statement = jdbcConnection.createStatement())
        {
            final java.sql.ResultSet resultSet = statement
                    .executeQuery("SELECT DOC IS JSON OBJECT AS IS_OBJECT FROM " + TABLE);
            resultSet.next();
            assertThat(resultSet.getBoolean("IS_OBJECT"))
                    .as("H2 stores the document, not a JSON string.").isTrue();
        }
        final ITable table = connection.createDataSet().getTable(TABLE);
        assertThat(table.getRowCount()).as("The row is read back.").isEqualTo(1);
    }

    @Test
    void testAssertWithValueComparer_jsonColumnAgainstReorderedDocument_passes() throws Exception
    {
        final IDataSet loaded = flatXml("<dataset><" + TABLE
                + " ID=\"1\" DOC='{\"a\":1,\"b\":2}'/></dataset>");
        DatabaseOperation.INSERT.execute(connection, loaded);
        final IDataSet expected = flatXml("<dataset><" + TABLE
                + " ID=\"1\" DOC='{ \"b\": 2, \"a\": 1 }'/></dataset>");
        final ITable actualTable = connection.createDataSet().getTable(TABLE);
        final ITable expectedTable = expected.getTable(TABLE);

        final Map<String, ValueComparer> columnComparers = new ColumnValueComparerMapBuilder()
                .add("DOC", new IsActualEqualToExpectedJsonValueComparer()).build();

        assertDoesNotThrow(() -> sut.assertWithValueComparer(expectedTable, actualTable,
                ValueComparers.isActualEqualToExpected, columnComparers));
    }

    @Test
    void testAssertWithValueComparer_jsonColumnAgainstDifferentDocument_fails() throws Exception
    {
        final IDataSet loaded = flatXml("<dataset><" + TABLE
                + " ID=\"1\" DOC='{\"a\":1,\"b\":2}'/></dataset>");
        DatabaseOperation.INSERT.execute(connection, loaded);
        final IDataSet expected = flatXml("<dataset><" + TABLE
                + " ID=\"1\" DOC='{\"a\":1,\"b\":3}'/></dataset>");
        final ITable actualTable = connection.createDataSet().getTable(TABLE);
        final ITable expectedTable = expected.getTable(TABLE);
        final Map<String, ValueComparer> columnComparers = new ColumnValueComparerMapBuilder()
                .add("DOC", new IsActualEqualToExpectedJsonValueComparer()).build();

        assertThrows(DbComparisonFailure.class, () -> sut.assertWithValueComparer(expectedTable,
                actualTable, ValueComparers.isActualEqualToExpected, columnComparers));
    }

    private static IDataSet flatXml(final String xml) throws Exception
    {
        return new FlatXmlDataSetBuilder().build(new InputSource(new StringReader(xml)));
    }
}
