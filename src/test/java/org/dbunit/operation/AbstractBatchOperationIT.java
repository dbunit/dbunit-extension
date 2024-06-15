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
package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since May 7, 2002
 */
class AbstractBatchOperationIT extends AbstractDatabaseIT
{

    @Test
    void testGetOperationMetaDataAndMissingColumns() throws Exception
    {
        final Reader in = TestUtils.getFileReader("xml/missingColumnTest.xml");
        final IDataSet xmlDataSet = new XmlDataSet(in);

        final ITable[] xmlTables = DataSetUtils.getTables(xmlDataSet);
        for (int i = 0; i < xmlTables.length; i++)
        {
            final ITable xmlTable = xmlTables[i];
            final ITableMetaData xmlMetaData = xmlTable.getTableMetaData();
            final String tableName = xmlMetaData.getTableName();

            final ITable databaseTable =
                    _connection.createDataSet().getTable(tableName);
            final ITableMetaData databaseMetaData =
                    databaseTable.getTableMetaData();

            // ensure xml table is missing some columns present in database
            // table
            assertThat(xmlMetaData.getColumns())
                    .as(tableName + " missing columns")
                    .hasSizeLessThan(databaseMetaData.getColumns().length);

            final ITableMetaData resultMetaData = AbstractBatchOperation
                    .getOperationMetaData(_connection, xmlMetaData);

            // result metadata must contains database columns matching the xml
            // columns
            final Column[] resultColumns = resultMetaData.getColumns();
            assertThat(resultColumns).as("result columns count")
                    .hasSameSizeAs(xmlMetaData.getColumns());
            for (int j = 0; j < resultColumns.length; j++)
            {
                final Column resultColumn = resultColumns[j];
                final Column databaseColumn =
                        Columns.getColumn(resultColumn.getColumnName(),
                                databaseMetaData.getColumns());
                final Column xmlColumn = xmlMetaData.getColumns()[j];

                assertThat(resultColumn.getColumnName()).as("column name")
                        .isEqualTo(convertString(xmlColumn.getColumnName()));
                assertThat(databaseColumn).as("column instance")
                        .isSameAs(resultColumn);
            }

            // result metadata must contains database primary keys
            final Column[] resultPrimaryKeys = resultMetaData.getPrimaryKeys();
            assertThat(resultPrimaryKeys).as("key count")
                    .hasSameSizeAs(databaseMetaData.getPrimaryKeys());
            for (int j = 0; j < resultPrimaryKeys.length; j++)
            {
                final Column resultPrimaryKey = resultPrimaryKeys[j];
                final Column databasePrimaryKey =
                        databaseMetaData.getPrimaryKeys()[j];
                assertThat(resultPrimaryKey).as("key instance")
                        .isSameAs(databasePrimaryKey);
            }
        }
    }

    @Test
    void testGetOperationMetaDataAndUnknownColumns() throws Exception
    {
        final String tableName = "PK_TABLE";
        final Reader in = TestUtils.getFileReader("xml/unknownColumnTest.xml");
        final IDataSet xmlDataSet = new XmlDataSet(in);

        final ITable xmlTable = xmlDataSet.getTable(tableName);

        assertThrows(NoSuchColumnException.class,
                () -> AbstractBatchOperation.getOperationMetaData(_connection,
                        xmlTable.getTableMetaData()),
                "Should throw a NoSuchColumnException");
    }

}
