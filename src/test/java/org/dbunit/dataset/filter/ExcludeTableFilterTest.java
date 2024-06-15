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

import java.util.Arrays;

import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Mar 18, 2003
 * @version $Revision$
 */
class ExcludeTableFilterTest extends AbstractTableFilterTest
{
    static final String MATCHING_NAME = "aBcDe";
    static final String[] MATCHING_PATTERNS =
            IncludeTableFilterTest.MATCHING_PATTERNS;
    static final String[] NONMATCHING_PATTERNS =
            IncludeTableFilterTest.NONMATCHING_PATTERNS;

    @Override
    @Test
    public void testAccept() throws Exception
    {
        final String[] validNames = getExpectedNames();
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

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
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

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
        final ITableFilter filter = new ExcludeTableFilter(invalidNames);

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
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

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
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());
        filter.excludeTable("UNKNOWN_TABLE");

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
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

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
    @Disabled("Cannot test!")
    public void testGetReverseTableNames() throws Exception
    {
        // Cannot test!
    }

    @Override
    @Test
    public void testIterator() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

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
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());

        final String[] expectedNames = getExpectedLowerNames();
        final IDataSet dataSet = new LowerCaseDataSet(createDataSet());
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
    @Disabled("Cannot test!")
    public void testReverseIterator() throws Exception
    {
        // Cannot test!
    }

    @Override
    @Test
    public void testIteratorAndTableNotInDecoratedDataSet() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final ExcludeTableFilter filter = new ExcludeTableFilter();
        filter.excludeTable(getExtraTableName());
        filter.excludeTable("UNKNOWN_TABLE");

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
    void testIsValidNameWithPatterns() throws Exception
    {
        final String validName = MATCHING_NAME;

        final String[] patterns = NONMATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);
            assertThat(filter.accept(validName)).as(pattern).isTrue();
        }
    }

    @Test
    void testIsValidNameInvalidWithPatterns() throws Exception
    {
        final String validName = MATCHING_NAME;

        final String[] patterns = MATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);
            assertThat(filter.accept(validName)).as(pattern).isFalse();
        }
    }

    @Test
    void testGetTableNamesWithPatterns() throws Exception
    {
        final String nonMatchingName = "toto titi tata";
        final String[] expectedNames = new String[] {nonMatchingName};
        final IDataSet dataSet = new DefaultDataSet(
                new ITable[] {new DefaultTable(MATCHING_NAME),
                        new DefaultTable(nonMatchingName),});
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] patterns = MATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);

            // this pattern match everything, so ensure everything filtered
            if (pattern.equals("*"))
            {
                final String[] actualNames = filter.getTableNames(dataSet);
                assertThat(actualNames.length).as("name count - " + pattern)
                        .isEqualTo(0);
            } else
            {
                final String[] actualNames = filter.getTableNames(dataSet);
                assertThat(actualNames).as("name count - " + pattern)
                        .hasSameSizeAs(expectedNames);
                assertThat(Arrays.asList(actualNames)).as("names - " + pattern)
                        .isEqualTo(Arrays.asList(expectedNames));
            }
        }
    }

    @Test
    void testGetTableNamesWithNonMatchingPatterns() throws Exception
    {
        final String[] expectedNames = new String[] {MATCHING_NAME};
        final IDataSet dataSet = new DefaultDataSet(
                new ITable[] {new DefaultTable(MATCHING_NAME),});

        final String[] patterns = NONMATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);

            final String[] actualNames = filter.getTableNames(dataSet);
            assertThat(actualNames).as("name count - " + pattern)
                    .hasSameSizeAs(expectedNames);
            assertThat(Arrays.asList(actualNames)).as("names - " + pattern)
                    .isEqualTo(Arrays.asList(expectedNames));
        }
    }

    @Test
    void testGetTablesWithPatterns() throws Exception
    {
        final String nonMatchingName = "toto titi tata";
        final String[] expectedNames = new String[] {nonMatchingName};
        final IDataSet dataSet = new DefaultDataSet(
                new ITable[] {new DefaultTable(MATCHING_NAME),
                        new DefaultTable(nonMatchingName),});
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(expectedNames.length);

        final String[] patterns = MATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);

            // this pattern match everything, so ensure everything is filtered
            if (pattern.equals("*"))
            {
                final ITable[] actualTables =
                        DataSetUtils.getTables(filter.iterator(dataSet, false));
                final String[] actualNames =
                        new DefaultDataSet(actualTables).getTableNames();
                assertThat(actualNames.length).as("table count - " + pattern)
                        .isZero();
            } else
            {
                final ITable[] actualTables =
                        DataSetUtils.getTables(filter.iterator(dataSet, false));
                final String[] actualNames =
                        new DefaultDataSet(actualTables).getTableNames();
                assertThat(actualTables).as("table count - " + pattern)
                        .hasSameSizeAs(expectedNames);
                assertThat(Arrays.asList(actualNames))
                        .as("table names - " + pattern)
                        .isEqualTo(Arrays.asList(expectedNames));
            }
        }
    }

    @Test
    void testGetTablesWithNonMatchingPatterns() throws Exception
    {
        final String[] expectedNames = new String[] {MATCHING_NAME};
        final IDataSet dataSet = new DefaultDataSet(
                new ITable[] {new DefaultTable(MATCHING_NAME),});
        assertThat(dataSet.getTableNames()).as("dataset names count")
                .hasSizeGreaterThan(0);

        final String[] patterns = NONMATCHING_PATTERNS;
        for (int i = 0; i < patterns.length; i++)
        {
            final String pattern = patterns[i];
            final ExcludeTableFilter filter = new ExcludeTableFilter();
            filter.excludeTable(pattern);

            final ITable[] actualTables =
                    DataSetUtils.getTables(filter.iterator(dataSet, false));
            final String[] actualNames =
                    new DefaultDataSet(actualTables).getTableNames();
            assertThat(actualTables).as("table count - " + pattern)
                    .hasSameSizeAs(expectedNames);
            assertThat(Arrays.asList(actualNames))
                    .as("table names - " + pattern)
                    .isEqualTo(Arrays.asList(expectedNames));
        }
    }

}
