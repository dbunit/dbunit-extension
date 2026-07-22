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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.x (Apr 11, 2003)
 */
public class ForwardOnlyDataSetTest extends DefaultDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new ForwardOnlyDataSet(super.createDataSet());
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    @Test
    void testGetTableNames_withPopulatedDataSet_returnsAllTableNames() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableNames(),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    void testGetTable_withKnownTableName_returnsTable() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTable(tableNames[0]),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    void testGetTableMetaData_withKnownTableName_returnsMetaData() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableMetaData(tableNames[0]),
                "Should have throw UnsupportedOperationException");

    }

    @Override
    @Test
    public void testReverseIterator_withPopulatedDataSet_iteratesAllTablesInReverseOrder() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.reverseIterator(),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    public void testGetTableNamesDefensiveCopy_onMultipleCalls_returnsNewArrayEachTime() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableNames(),
                "ForwardOnlyDataSet does not support getTableNames().");
    }

    @Override
    @Test
    public void testGetUnknownTable_withUnknownTableName_throwsNoSuchTableException() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTable("UNKNOWN_TABLE"),
                "ForwardOnlyDataSet does not support getTable(String), regardless of table name.");
    }

    @Override
    @Test
    public void testGetUnknownTableMetaData_withUnknownTableName_throwsNoSuchTableException() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableMetaData("UNKNOWN_TABLE"),
                "ForwardOnlyDataSet does not support getTableMetaData(String), regardless of table name.");
    }

    @Override
    @Test
    public void testGetTablesDefensiveCopy_onMultipleCalls_returnsNewArrayEachTime() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThat(ds.getTables()).as("Should not be same instance.")
                .isNotSameAs(ds.getTables());
    }

    @Override
    @Test
    public void testGetCaseInsensitiveTable_withLowercaseTableName_returnsTable() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTable(tableNames[0].toLowerCase()),
                "ForwardOnlyDataSet does not support case-insensitive lookup.");
    }

    @Override
    @Test
    public void testGetCaseInsensitiveTableMetaData_withLowercaseTableName_returnsMetaData() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableMetaData(tableNames[0].toLowerCase()),
                "ForwardOnlyDataSet does not support case-insensitive lookup.");
    }

    @Override
    @Test
    public void testCreateDuplicateDataSet_withDuplicateTableNames_throwsAmbiguousTableNameException() throws Exception
    {
        assertThrows(UnsupportedOperationException.class,
                () -> createDuplicateDataSet(),
                "ForwardOnlyDataSet only wraps another dataset, so duplicate table names cannot occur here.");
    }

    @Override
    @Test
    public void testCreateMultipleCaseDuplicateDataSet_withDuplicateCaseVariantNames_throwsAmbiguousTableNameException() throws Exception
    {
        assertThrows(UnsupportedOperationException.class,
                () -> createMultipleCaseDuplicateDataSet(),
                "ForwardOnlyDataSet only wraps another dataset, so duplicate table names cannot occur here.");
    }

}
