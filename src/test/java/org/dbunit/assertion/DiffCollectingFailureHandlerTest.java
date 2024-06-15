/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class DiffCollectingFailureHandlerTest
{
    private DbUnitAssert assertion = new DbUnitAssert();

    private IDataSet getDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader(DbUnitAssertIT.FILE_PATH));
    }

    @Test
    void testAssertTablesWithDifferentValues() throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final DiffCollectingFailureHandler myHandler =
                new DiffCollectingFailureHandler();

        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), myHandler);

        final List<?> diffList = myHandler.getDiffList();
        assertThat(diffList).hasSize(1);
        final Difference diff = (Difference) diffList.get(0);
        assertThat(diff.getColumnName()).isEqualTo("COLUMN2");
        assertThat(diff.getExpectedValue()).isEqualTo("row 1 col 2");
        assertThat(diff.getActualValue()).isEqualTo("wrong value");
    }

}
