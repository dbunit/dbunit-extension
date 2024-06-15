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

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@Suite
@SelectClasses({org.dbunit.dataset.common.handlers.AllTestsSuite.class,
        org.dbunit.dataset.datatype.AllTestsSuite.class,
        org.dbunit.dataset.excel.AllTestsSuite.class,
        org.dbunit.dataset.filter.AllTestsSuite.class,
        org.dbunit.dataset.stream.AllTestsSuite.class,
        org.dbunit.dataset.sqlloader.AllTestsSuite.class,
        org.dbunit.dataset.xml.AllTestsSuite.class,
        org.dbunit.dataset.csv.AllTestsSuite.class,
        CaseInsensitiveDataSetTest.class, CaseInsensitiveTableTest.class,
        ColumnTest.class, ColumnsTest.class, CompositeDataSetTest.class,
        CompositeTableTest.class, DataSetProducerAdapterTest.class,
        DataSetUtilsTest.class, DefaultDataSetTest.class,
        DefaultReverseTableIteratorTest.class, DefaultTableIteratorTest.class,
        DefaultTableMetaDataTest.class, DefaultTableTest.class,
        FilteredDataSetTest.class, FilteredTableMetaDataTest.class,
        ForwardOnlyDataSetTest.class, ForwardOnlyTableTest.class,
        LowerCaseDataSetTest.class, LowerCaseTableMetaDataTest.class,
        ReplacementDataSetTest.class, ReplacementTableTest.class,
        SortedDataSetTest.class, SortedTableTest.class})
public class AllTestsSuite
{

}
