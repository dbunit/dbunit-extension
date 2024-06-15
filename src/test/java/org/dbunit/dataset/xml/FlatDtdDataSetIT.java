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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.testutil.FileAsserts;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Apr 4, 2002
 */
class FlatDtdDataSetIT extends AbstractDataSetTest
{
    private static final String DTD_FILE = "dtd/flatDtdDataSetTest.dtd";
    private static final String DUPLICATE_FILE =
            "dtd/flatDtdDataSetDuplicateTest.dtd";
    private static final String DUPLICATE_MULTIPLE_CASE_FILE =
            "dtd/flatDtdDataSetDuplicateMultipleCaseTest.dtd";

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSetTest class

    private File getFile(final String fileName) throws Exception
    {
        return TestUtils
                .getFileForDatabaseEnvironment(TestUtils.getFileName(fileName));
    }

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new FlatDtdDataSet(TestUtils.getFileReader(DTD_FILE));
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return new FlatDtdDataSet(TestUtils.getFileReader(DUPLICATE_FILE));
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        return new FlatDtdDataSet(
                TestUtils.getFileReader(DUPLICATE_MULTIPLE_CASE_FILE));
    }

    @Override
    protected int[] getExpectedDuplicateRows()
    {
        return new int[] {0, 0, 0};
    }

    ////////////////////////////////////////////////////////////////////////////
    // Test methods

    @Test
    void testWriteFromDtd() throws Exception
    {
        final IDataSet dataSet =
                new FlatDtdDataSet(TestUtils.getFileReader(DTD_FILE));

        final File tempFile = File.createTempFile("flatXmlDocType", ".dtd");

        try
        {
            final Writer out = new FileWriter(tempFile);

            try
            {
                // write DTD in temp file
                FlatDtdDataSet.write(dataSet, out);
            } finally
            {
                out.close();
            }

            FileAsserts.assertEquals(
                    new BufferedReader(TestUtils.getFileReader(DTD_FILE)),
                    new BufferedReader(new FileReader(tempFile)));
        } finally
        {
            tempFile.delete();
        }

    }

    @Test
    void testWriteFromDatabase() throws Exception
    {
        final IDatabaseConnection connection =
                DatabaseEnvironment.getInstance().getConnection();
        final IDataSet dataSet = connection.createDataSet();

        final File tempFile = File.createTempFile("flatXmlDocType", ".dtd");

        try
        {
            final Writer out = new FileWriter(tempFile);

            try
            {
                // write DTD in temp file
                final String[] tableNames = getExpectedNames();
                FlatDtdDataSet.write(new FilteredDataSet(tableNames, dataSet),
                        out);
            } finally
            {
                out.close();
            }

            FileAsserts.assertEquals(
                    new BufferedReader(new FileReader(getFile(DTD_FILE))),
                    new BufferedReader(new FileReader(tempFile)));
        } finally
        {
            tempFile.delete();
        }
    }
}