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
package org.dbunit.dataset.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public class SequenceTableFilterTest extends AbstractTableFilterTest
{

    @Override
    @Test
    public void testAccept() throws Exception
    {
        final String[] validNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(validNames);

        for (int i = 0; i < validNames.length; i++)
        {
            final String validName = validNames[i];
            assertThat(filter.accept(validName)).as(validName).isTrue();
        }
    }

    @Override
    @Test
    public void testIsCaseInsensitiveValidName() throws Exception
    {
        final String[] validNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(validNames);

        for (int i = 0; i < validNames.length; i++)
        {
            final String validName = validNames[i];
            assertThat(filter.accept(validName)).as(validName).isTrue();
        }
    }

    @Override
    @Test
    public void testIsValidNameAndInvalid() throws Exception
    {
        final String[] invalidNames =
                new String[] {"INVALID_TABLE", "UNKNOWN_TABLE",};
        final String[] validNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(validNames);

        for (int i = 0; i < invalidNames.length; i++)
        {
            final String invalidName = invalidNames[i];
            assertThat(filter.accept(invalidName)).as(invalidName).isFalse();
        }
    }

    @Override
    @Test
    public void testGetTableNames() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(expectedNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] actualNames = filter.getTableNames(dataSet);
        assertThat(actualNames).as("name count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testGetTableNamesAndTableNotInDecoratedDataSet()
            throws Exception
    {
        final String[] expectedNames = getExpectedNames();

        final List filterNameList = new ArrayList(Arrays.asList(expectedNames));
        filterNameList.add("UNKNOWN_TABLE");
        final String[] filterNames =
                (String[]) filterNameList.toArray(new String[0]);
        final ITableFilter filter = new SequenceTableFilter(filterNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] actualNames = filter.getTableNames(dataSet);
        assertThat(actualNames).as("name count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testGetCaseInsensitiveTableNames() throws Exception
    {
        final String[] filterNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(filterNames);

        final String[] expectedNames = getExpectedLowerNames();
        final IDataSet dataSet = new LowerCaseDataSet(createDataSet());
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] actualNames = filter.getTableNames(dataSet);
        assertThat(actualNames).as("name count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testGetReverseTableNames() throws Exception
    {
        final String[] expectedNames =
                DataSetUtils.reverseStringArray(getExpectedNames());
        final ITableFilter filter = new SequenceTableFilter(expectedNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] actualNames = filter.getTableNames(dataSet);
        assertThat(actualNames).as("name count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testIterator() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final ITableFilter filter = new SequenceTableFilter(expectedNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final ITable[] actualTables =
                DataSetUtils.getTables(filter.iterator(dataSet, false));
        final String[] actualNames =
                new DefaultDataSet(actualTables).getTableNames();
        assertThat(actualTables).as("table count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("table names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testCaseInsensitiveIterator() throws Exception
    {
        final ITableFilter filter = new SequenceTableFilter(getExpectedNames());
        final String[] lowerNames = getExpectedLowerNames();

        final IDataSet dataSet = new LowerCaseDataSet(createDataSet());
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(lowerNames.length);

        final ITable[] actualTables =
                DataSetUtils.getTables(filter.iterator(dataSet, false));
        final String[] actualNames =
                new DefaultDataSet(actualTables).getTableNames();
        assertThat(actualTables).as("table count").hasSameSizeAs(lowerNames);
        assertThat(Arrays.asList(actualNames)).as("table names")
                .isEqualTo(Arrays.asList(lowerNames));
    }

    @Override
    @Test
    public void testReverseIterator() throws Exception
    {
        final String[] filterNames = getExpectedNames();
        final String[] expectedNames =
                DataSetUtils.reverseStringArray(filterNames);
        final ITableFilter filter = new SequenceTableFilter(filterNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final ITable[] actualTables =
                DataSetUtils.getTables(filter.iterator(dataSet, true));
        final String[] actualNames =
                new DefaultDataSet(actualTables).getTableNames();
        assertThat(actualTables).as("table count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("table names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    @Override
    @Test
    public void testIteratorAndTableNotInDecoratedDataSet() throws Exception
    {
        final String[] expectedNames = getExpectedNames();

        final List filterNameList = new ArrayList(Arrays.asList(expectedNames));
        filterNameList.add("UNKNOWN_TABLE");
        final String[] filterNames =
                (String[]) filterNameList.toArray(new String[0]);
        final ITableFilter filter = new SequenceTableFilter(filterNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final ITable[] actualTables =
                DataSetUtils.getTables(filter.iterator(dataSet, false));
        final String[] actualNames =
                new DefaultDataSet(actualTables).getTableNames();
        assertThat(actualTables).as("table count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("table names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

    ////////////////////////////////////////////////////////////////////////////
    @Test
    void testIteratorWithDifferentSequence() throws Exception
    {
        final String[] expectedNames =
                DataSetUtils.reverseStringArray(getExpectedNames());
        final ITableFilter filter = new SequenceTableFilter(expectedNames);

        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final ITable[] actualTables =
                DataSetUtils.getTables(filter.iterator(dataSet, false));
        final String[] actualNames =
                new DefaultDataSet(actualTables).getTableNames();
        assertThat(actualTables).as("table count").hasSameSizeAs(expectedNames);
        assertThat(Arrays.asList(actualNames)).as("table names")
                .isEqualTo(Arrays.asList(expectedNames));
    }

}
