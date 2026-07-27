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

import org.dbunit.TurkishDefaultLocale;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Mar 27, 2002)
 */
class CaseInsensitiveDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new CaseInsensitiveDataSet(new XmlDataSet(
                TestUtils.getFileReader("xml/caseInsensitiveDataSetTest.xml")));
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
    protected void assertEqualsTableName(final String message,
            final String expected, final String actual)
    {
        assertEqualsIgnoreCase(message, expected, actual);
    }

    @Override
    @Test
    public void testCreateDuplicateDataSet_withDuplicateTableNames_throwsAmbiguousTableNameException() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

    @Override
    @Test
    public void testCreateMultipleCaseDuplicateDataSet_withDuplicateCaseVariantNames_throwsAmbiguousTableNameException() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

    @Test
    @TurkishDefaultLocale
    void testGetTable_withTurkishDefaultLocaleAndAlreadyUpperCaseQuery_findsTable()
            throws Exception
    {
        // The real table name is lower-case (needs folding); the query
        // is already upper-case (a no-op fold) - this asymmetry is what
        // surfaces the bug: a Turkish default locale folds the real
        // name's 'i' to a dotted capital 'İ', which then no longer
        // equals the query's plain ASCII 'I'.
        final Column[] columns = {new Column("ID", DataType.VARCHAR)};
        final DefaultTable table =
                new DefaultTable("products_id", columns);
        final IDataSet wrapped = new DefaultDataSet(table);
        final IDataSet caseInsensitiveDataSet =
                new CaseInsensitiveDataSet(wrapped);

        final ITable actual =
                caseInsensitiveDataSet.getTable("PRODUCTS_ID");

        assertThat(actual.getTableMetaData().getTableName())
                .as("getTable() must use Locale.ENGLISH for both the"
                        + " stored and queried table name so a Turkish"
                        + " default locale does not break the"
                        + " case-insensitive lookup.")
                .isEqualTo("products_id");
    }

}
