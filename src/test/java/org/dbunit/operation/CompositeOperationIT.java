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

import java.io.FileReader;
import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
class CompositeOperationIT extends AbstractDatabaseIT
{

    @Test
    void testExecute() throws Exception
    {
        final String tableName = "PK_TABLE";
        final String columnName = "PK0";
        final Reader in = new FileReader(
                TestUtils.getFile("xml/compositeOperationTest.xml"));
        final IDataSet xmlDataSet = new XmlDataSet(in);

        // verify table before
        final ITable tableBefore = createOrderedTable(tableName, columnName);
        assertThat(tableBefore.getRowCount()).as("row count before")
                .isEqualTo(3);
        assertThat(tableBefore.getValue(0, columnName)).as("before")
                .hasToString("0");
        assertThat(tableBefore.getValue(1, columnName)).as("before")
                .hasToString("1");
        assertThat(tableBefore.getValue(2, columnName)).as("before")
                .hasToString("2");

        final DatabaseOperation operation = new CompositeOperation(
                DatabaseOperation.DELETE_ALL, DatabaseOperation.INSERT);
        operation.execute(_connection, xmlDataSet);

        final ITable tableAfter = createOrderedTable(tableName, columnName);
        assertThat(tableAfter.getRowCount()).as("row count after").isEqualTo(2);
        assertThat(tableAfter.getValue(0, columnName)).as("after")
                .hasToString("1");
        assertThat(tableAfter.getValue(1, columnName)).as("after")
                .hasToString("3");
    }

}
