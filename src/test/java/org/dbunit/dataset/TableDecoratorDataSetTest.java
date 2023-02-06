/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2019, DbUnit.org
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

import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;

public class TableDecoratorDataSetTest extends AbstractDataSetDecoratorTest
{
    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new TableDecoratorDataSet(
                new FlatXmlDataSetBuilder()
                        .build(new FileReader(FlatXmlDataSetTest.DATASET_FILE)),
                t -> new ColumnFilterTable(t, (table, column) -> true));
    }

    @SuppressWarnings("deprecation")
    public void testTableDecoration() throws Exception
    {
        final IDataSet dataset = createDataSet();

        assertThat(dataset.getTable("TEST_TABLE"))
                .isInstanceOf(ColumnFilterTable.class);
        assertThat(dataset.getTableMetaData("TEST_TABLE"))
                .isInstanceOf(FilteredTableMetaData.class);
        assertThat(dataset.getTables()[0])
                .isInstanceOf(ColumnFilterTable.class);

        ITableIterator iterator = dataset.iterator();
        iterator.next();
        assertThat(iterator.getTable()).isInstanceOf(ColumnFilterTable.class);
        assertThat(iterator.getTableMetaData())
                .isInstanceOf(FilteredTableMetaData.class);

        iterator = dataset.reverseIterator();
        iterator.next();
        assertThat(iterator.getTable()).isInstanceOf(ColumnFilterTable.class);
        assertThat(iterator.getTableMetaData())
                .isInstanceOf(FilteredTableMetaData.class);
    }
}
