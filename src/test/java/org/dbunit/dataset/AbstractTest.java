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

import java.util.Arrays;
import java.util.List;

/**
 * @author Manuel Laflamme
 * @since Apr 6, 2003
 * @version $Revision$
 */
public abstract class AbstractTest
{
    private static final String[] TABLE_NAMES =
            {"TEST_TABLE", "SECOND_TABLE", "EMPTY_TABLE", "PK_TABLE",
                    "ONLY_PK_TABLE", "EMPTY_MULTITYPE_TABLE",};
    private static final String[] DUPLICATE_TABLE_NAMES =
            {"DUPLICATE_TABLE", "EMPTY_TABLE", "DUPLICATE_TABLE",};
    private static final String EXTRA_TABLE_NAME = "EXTRA_TABLE";

    /**
     * Returns the string converted as an identifier according to the metadata
     * rules of the database environment. Most databases convert all metadata
     * identifiers to uppercase. PostgreSQL converts identifiers to lowercase.
     * MySQL preserves case.
     * 
     * @param str
     *            The identifier.
     * @return The identifier converted according to database rules.
     */
    protected String convertString(final String str) throws Exception
    {
        return str;
    }

    protected String[] getExpectedNames() throws Exception
    {
        return (String[]) AbstractTest.TABLE_NAMES.clone();
    }

    protected String[] getExpectedLowerNames() throws Exception
    {
        String[] names = (String[]) AbstractTest.TABLE_NAMES.clone();
        for (int i = 0; i < names.length; i++)
        {
            names[i] = names[i].toLowerCase();
        }

        return names;
    }

    protected String[] getExpectedDuplicateNames()
    {
        return (String[]) AbstractTest.DUPLICATE_TABLE_NAMES.clone();
    }

    protected String getDuplicateTableName()
    {
        return "DUPLICATE_TABLE";
    }

    public String getExtraTableName()
    {
        return AbstractTest.EXTRA_TABLE_NAME;
    }

    public void assertEqualsIgnoreCase(final String message,
            final String expected, final String actual)
    {
        if (!expected.equalsIgnoreCase(actual))
        {
            assertThat(actual).as(message).isEqualTo(expected);
        }
    }

    public void assertContains(final String message, final Object[] expected,
            final Object[] actual)
    {
        final List<Object> expectedList = Arrays.asList(expected);
        final List<Object> actualList = Arrays.asList(actual);

        assertThat(actualList)
                .as(message + " expected contains:<" + expectedList
                        + "> but was:<" + actualList + ">")
                .containsAll(expectedList);
    }

    public void assertContainsIgnoreCase(final String message,
            final String[] expected, final String[] actual)
    {
        final String[] expectedLowerCase = new String[expected.length];
        for (int i = 0; i < expected.length; i++)
        {
            expectedLowerCase[i] = expected[i].toLowerCase();
        }

        final String[] actualLowerCase = new String[actual.length];
        for (int i = 0; i < actual.length; i++)
        {
            actualLowerCase[i] = actual[i].toLowerCase();
        }

        assertContains(message, expectedLowerCase, actualLowerCase);
    }

}
