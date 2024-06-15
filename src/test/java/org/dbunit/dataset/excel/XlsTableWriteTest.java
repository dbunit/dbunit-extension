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
package org.dbunit.dataset.excel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.dbunit.Assertion;
import org.dbunit.dataset.CompositeTable;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Feb 22, 2003
 * @version $Revision$
 */
public class XlsTableWriteTest extends XlsTableTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final File tempFile = File.createTempFile("tableWriteTest", ".xls");
        // System.out.println(tempFile.getAbsoluteFile());
        final OutputStream out = new FileOutputStream(tempFile);
        try
        {
            // write source dataset in temp file
            try
            {
                XlsDataSet.write(super.createDataSet(), out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final InputStream in = new FileInputStream(tempFile);
            try
            {
                return new XlsDataSet(in);
            } finally
            {
                in.close();
            }
        } finally
        {
            tempFile.delete();
        }
    }

    @Override
    @Test
    protected void testGetValue() throws Exception
    {
        assertDoesNotThrow(() -> super.testGetValue());
    }

    @Test
    void testWriteMultipleTable() throws Exception
    {
        final int tableCount = 5;
        final ITable sourceTable = super.createTable();

        final ITable[] tables = new ITable[tableCount];
        for (int i = 0; i < tables.length; i++)
        {
            final ITableMetaData metaData = new DefaultTableMetaData(
                    "table" + i, sourceTable.getTableMetaData().getColumns());
            tables[i] = new CompositeTable(metaData, sourceTable);
        }

        final IDataSet dataSet = new DefaultDataSet(tables);
        final File tempFile = File.createTempFile("tableWriteTest", ".xls");
        final OutputStream out = new FileOutputStream(tempFile);
        try
        {
            // write DefaultTable in temp file
            try
            {
                XlsDataSet.write(dataSet, out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final FileInputStream in = new FileInputStream(tempFile);
            try
            {
                final XlsDataSet dataSet2 = new XlsDataSet(in);

                // verify each table
                for (int i = 0; i < tables.length; i++)
                {
                    final ITable table = tables[i];
                    Assertion.assertEquals(table,
                            dataSet2.getTable(dataSet2.getTableNames()[i]));
                }
            } finally
            {
                in.close();
            }

        } finally
        {
            tempFile.delete();
        }
    }

}
