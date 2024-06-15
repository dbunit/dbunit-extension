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

package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Lenny Marks (lenny@aps.org)
 * @author dIon gillard (diongillard@users.sourceforge.net)
 * @version $Revision$
 * @since 2.1.0
 */
class CsvURLDataSetTest
{

    @Test
    void testNullColumns() throws DataSetException, MalformedURLException
    {
        final URL csvDir = TestUtils.getFile("csv/orders/").toURL();
        final CsvURLDataSet dataSet = new CsvURLDataSet(csvDir);

        final ITable table = dataSet.getTable("orders");
        assertThat(table.getValue(4, "description")).isNull();
    }

    @Test
    void testSpacesInColumns() throws DataSetException, MalformedURLException
    {
        final URL csvDir = TestUtils.getFile("csv/accounts/").toURL();
        final CsvURLDataSet dataSet = new CsvURLDataSet(csvDir);

        final ITable table = dataSet.getTable("accounts");
        assertThat(table.getValue(0, "acctid")).isEqualTo("   123");
        assertThat(table.getValue(1, "acctid")).isEqualTo("  2");
        assertThat(table.getValue(2, "acctid")).isEqualTo("   3spaces");
        assertThat(table.getValue(3, "acctid")).isEqualTo("    -4");
        assertThat(table.getValue(4, "acctid")).isEqualTo("     5     ");
    }

}
