/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.mssql.MsSqlDataTypeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author: $
 * @version $Revision: $ $Date: $
 * @since 2.4.6
 */
@ExtendWith(MockitoExtension.class)
public class AbstractTableMetaDataTest
{

    @Mock
    private DatabaseMetaData mockDatabaseMetData;

    @Test
    public void testValidator_withSupportedDbProduct_returnsNullMessage() throws Exception
    {
        final AbstractTableMetaData metaData = new AbstractTableMetaData()
        {
            @Override
            public Column[] getColumns() throws DataSetException
            {
                return null;
            }

            @Override
            public Column[] getPrimaryKeys() throws DataSetException
            {
                return null;
            }

            @Override
            public String getTableName()
            {
                return null;
            }
        };

        // DataTypeFactoryValidator validator = new DataTypeFactoryValidator();
        final IDataTypeFactory dataTypeFactory = new MsSqlDataTypeFactory();
        when(mockDatabaseMetData.getDatabaseProductName())
                .thenReturn("Microsoft SQL Server");

        final String validationMessage = metaData
                .validateDataTypeFactory(dataTypeFactory, mockDatabaseMetData);
        assertThat(validationMessage).as(
                "Validation message should be null because DB product should be supported")
                .isNull();
        verify(mockDatabaseMetData, times(1)).getDatabaseProductName();
    }

    @Test
    void testGetColumnIndex_exactCaseName_returnsIndex() throws Exception
    {
        final Column[] columns = new Column[] {new Column("Name", DataType.VARCHAR),
                new Column("Age", DataType.VARCHAR)};
        final AbstractTableMetaData metaData = newMetaDataWithColumns(columns);

        final int index = metaData.getColumnIndex("Age");

        assertThat(index).as("Exact-case column name should resolve to its index.").isEqualTo(1);
    }

    @Test
    void testGetColumnIndex_differentCaseName_returnsIndex() throws Exception
    {
        final Column[] columns = new Column[] {new Column("Name", DataType.VARCHAR),
                new Column("Age", DataType.VARCHAR)};
        final AbstractTableMetaData metaData = newMetaDataWithColumns(columns);

        final int index = metaData.getColumnIndex("age");

        assertThat(index)
                .as("Different-case column name should fall through to the uppercase lookup.")
                .isEqualTo(1);
    }

    @Test
    void testGetColumnIndex_unknownName_throwsNoSuchColumnException() throws Exception
    {
        final Column[] columns = new Column[] {new Column("Name", DataType.VARCHAR)};
        final AbstractTableMetaData metaData = newMetaDataWithColumns(columns);

        assertThatExceptionOfType(NoSuchColumnException.class)
                .as("Unknown column name should throw NoSuchColumnException.")
                .isThrownBy(() -> metaData.getColumnIndex("UNKNOWN"));
    }

    @Test
    void testGetColumnIndex_duplicateCaseInsensitiveNames_matchesUppercaseBehavior() throws Exception
    {
        final Column[] columns = new Column[] {new Column("a", DataType.VARCHAR),
                new Column("A", DataType.VARCHAR)};
        final AbstractTableMetaData metaData = newMetaDataWithColumns(columns);

        final int exactCaseIndex = metaData.getColumnIndex("A");
        final int otherCaseIndex = metaData.getColumnIndex("a");

        assertThat(exactCaseIndex)
                .as("Exact-case lookup must match the uppercase-path index for duplicate case-insensitive names.")
                .isEqualTo(otherCaseIndex);
    }

    private AbstractTableMetaData newMetaDataWithColumns(final Column[] columns)
    {
        return new AbstractTableMetaData()
        {
            @Override
            public Column[] getColumns() throws DataSetException
            {
                return columns;
            }

            @Override
            public Column[] getPrimaryKeys() throws DataSetException
            {
                return new Column[0];
            }

            @Override
            public String getTableName()
            {
                return "TEST_TABLE";
            }
        };
    }

}
