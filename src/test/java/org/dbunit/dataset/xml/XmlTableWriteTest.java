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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;

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
 * @version $Revision$
 * @since Feb 18, 2002
 */
class XmlTableWriteTest extends XmlTableTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final File tempFile =
                File.createTempFile("xmlDataSetWriteTest", ".xml");
        final Writer out = new FileWriter(tempFile);
        try
        {
            // write DefaultTable in temp file
            try
            {
                XmlDataSet.write(super.createDataSet(), out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final FileReader in = new FileReader(tempFile);
            try
            {
                return new XmlDataSet(in);
            } finally
            {
                in.close();
            }
        } finally
        {
            tempFile.delete();
        }

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
        final File tempFile = File.createTempFile("xmlDataSetWriteTest", "xml");
        final Writer out = new FileWriter(tempFile);
        try
        {
            // write DefaultTable in temp file
            try
            {
                XmlDataSet.write(dataSet, out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final FileReader in = new FileReader(tempFile);
            try
            {
                final XmlDataSet xmlDataSet2 = new XmlDataSet(in);

                // verify each table
                for (int i = 0; i < tables.length; i++)
                {
                    final ITable table = tables[i];
                    Assertion.assertEquals(table, xmlDataSet2
                            .getTable(xmlDataSet2.getTableNames()[i]));
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