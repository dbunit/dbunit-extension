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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;

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
    public void testValidator() throws Exception
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

}
