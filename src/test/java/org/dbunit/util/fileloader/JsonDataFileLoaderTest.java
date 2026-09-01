/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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

package org.dbunit.util.fileloader;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Jeff Jensen
 * @since 3.6.0
 */
class JsonDataFileLoaderTest
{
    JsonDataFileLoader loader = null;

    @BeforeEach
    protected void setUp() throws Exception
    {
        loader = new JsonDataFileLoader();
    }

    @Test
    void testLoad_withValidJsonFile_returnsDataSetWithExpectedTablesAndRows()
            throws DataSetException
    {
        final String filename = "/json/dataSetTest.json";
        final IDataSet ds = loader.load(filename);

        assertThat(ds.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactlyInAnyOrder("TEST_TABLE", "SECOND_TABLE",
                        "EMPTY_TABLE", "PK_TABLE", "ONLY_PK_TABLE",
                        "EMPTY_MULTITYPE_TABLE");
        assertThat(ds.getTable("TEST_TABLE").getRowCount())
                .as("TEST_TABLE must have 6 rows.").isEqualTo(6);
    }
}
