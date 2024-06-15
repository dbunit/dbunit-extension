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

package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 22, 2002
 */
class DefaultDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final IDataSet dataSet =
                new XmlDataSet(TestUtils.getFileReader("xml/dataSetTest.xml"));
        final ITable[] tables = DataSetUtils.getTables(dataSet);

        return new DefaultDataSet(tables);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return createDuplicateDataSet(false);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        return createDuplicateDataSet(true);
    }

    private IDataSet createDuplicateDataSet(final boolean multipleCase)
            throws AmbiguousTableNameException
    {
        final ITable[] tables = super.createDuplicateTables(multipleCase);
        return new DefaultDataSet(tables);
    }

    @Test
    void testAddTableThenReadBackAndDoItAgainDataSet() throws Exception
    {
        final String tableName1 = "TEST_TABLE";
        final String tableName2 = "SECOND_TABLE";
        final DefaultDataSet dataSet = new DefaultDataSet();

        final DefaultTable table1 = new DefaultTable(tableName1);
        dataSet.addTable(table1);
        assertThat(dataSet.getTable(tableName1)).isEqualTo(table1);

        final DefaultTable table2 = new DefaultTable(tableName2);
        dataSet.addTable(table2);
        assertThat(dataSet.getTable(tableName2)).isEqualTo(table2);
    }

}
