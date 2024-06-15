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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
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
    void testGetTableNames() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableNames(),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    void testGetTable() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTable(tableNames[0]),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    void testGetTableMetaData() throws Exception
    {
        final String[] tableNames = getExpectedNames();
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.getTableMetaData(tableNames[0]),
                "Should have throw UnsupportedOperationException");

    }

    @Override
    @Test
    public void testReverseIterator() throws Exception
    {
        final IDataSet ds = createDataSet();
        assertThrows(UnsupportedOperationException.class,
                () -> ds.reverseIterator(),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetTableNamesDefensiveCopy() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetUnknownTable() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetUnknownTableMetaData() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetTablesDefensiveCopy() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetCaseInsensitiveTable() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("Cannot test! Unsupported feature.")
    public void testGetCaseInsensitiveTableMetaData() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    @Disabled("No op. This dataSet is only a wrapper for another dataSet which is why duplicates cannot occur.")
    public void testCreateDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

    @Override
    @Disabled("No op. This dataSet is only a wrapper for another dataSet which is why duplicates cannot occur.")
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

}
