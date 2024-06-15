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

import java.io.FileReader;
import java.io.Reader;

import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.dataset.xml.XmlTableTest;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 27, 2002
 */
class CaseInsensitiveTableTest extends XmlTableTest
{

    @Override
    protected ITable createTable() throws Exception
    {
        return new CaseInsensitiveTable(createDataSet().getTable("TEST_TABLE"));
    }

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/caseInsensitiveTableTest.xml"));
        return new XmlDataSet(in);
    }

    @Override
    @Test
    public void testTableMetaData() throws Exception
    {
        final Column[] columns = createTable().getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(COLUMN_COUNT);
        for (int i = 0; i < columns.length; i++)
        {
            final String expected = "COLUMN" + i;
            final String actual = columns[i].getColumnName();
            if (!actual.equalsIgnoreCase(expected))
            {
                assertThat(actual).as("column name").isEqualTo(expected);
            }
        }
    }
}
