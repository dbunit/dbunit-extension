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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link QuotedColumnsTableMetaData} and the select statement that
 * {@link DatabaseDataSet#getSelectStatement(String, ITableMetaData, String, boolean)}
 * builds for it.
 *
 * @since 3.6.0
 */
class QuotedColumnsTableMetaDataTest
{
    private static final Column ID = new Column("ID", DataType.INTEGER);
    private static final Column VALUE = new Column("VALUE", DataType.INTEGER);
    private static final Column MIXED_CASE =
            new Column("mixedCase", DataType.VARCHAR);

    private final ITableMetaData wrapped = new DefaultTableMetaData("Q1",
            new Column[] {ID, VALUE, MIXED_CASE}, new Column[] {ID});

    @Test
    void testQuote_nameWithoutQuoteString_isBetweenTheQuoteString() throws Exception
    {
        final QuotedColumnsTableMetaData metaData =
                new QuotedColumnsTableMetaData(wrapped, "\"");

        final String actual = metaData.quote("mixedCase");

        assertThat(actual).as("The name must be written between the quote string.")
                .isEqualTo("\"mixedCase\"");
    }

    @Test
    void testQuote_nameWithQuoteString_doublesTheQuoteStringInTheName() throws Exception
    {
        final QuotedColumnsTableMetaData metaData =
                new QuotedColumnsTableMetaData(wrapped, "`");

        final String actual = metaData.quote("a`b");

        assertThat(actual).as("A quote string inside the name must be doubled.")
                .isEqualTo("`a``b`");
    }

    @Test
    void testQuote_nameWithDot_isNotSplit() throws Exception
    {
        final QuotedColumnsTableMetaData metaData =
                new QuotedColumnsTableMetaData(wrapped, "\"");

        final String actual = metaData.quote("a.b");

        assertThat(actual).as("A dot inside a column name is part of the name.")
                .isEqualTo("\"a.b\"");
    }

    @Test
    void testConstructor_nullMetaData_throwsNullPointerException() throws Exception
    {
        assertThatThrownBy(() -> new QuotedColumnsTableMetaData(null, "\""))
                .as("The wrapped metadata is required.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructor_nullQuoteString_throwsNullPointerException() throws Exception
    {
        assertThatThrownBy(() -> new QuotedColumnsTableMetaData(wrapped, null))
                .as("The quote string is required.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructor_blankQuoteString_throwsIllegalArgumentException() throws Exception
    {
        assertThatThrownBy(() -> new QuotedColumnsTableMetaData(wrapped, " "))
                .as("A blank quote string quotes nothing.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetters_always_answerThoseOfTheWrappedMetaData() throws Exception
    {
        final QuotedColumnsTableMetaData metaData =
                new QuotedColumnsTableMetaData(wrapped, "\"");

        assertThat(metaData.getTableName()).as("The table name is the wrapped one.")
                .isEqualTo("Q1");
        assertThat(metaData.getColumns()).as("The columns are the wrapped ones.")
                .containsExactly(ID, VALUE, MIXED_CASE);
        assertThat(metaData.getPrimaryKeys()).as("The primary keys are the wrapped ones.")
                .containsExactly(ID);
        assertThat(metaData.getColumnIndex("VALUE"))
                .as("The column index is the wrapped one.").isEqualTo(1);
    }

    @Test
    void testGetSelectStatement_quotedColumnsAndNoEscapePattern_quotesColumnsAndOrderByNotTable()
            throws Exception
    {
        final ITableMetaData metaData = new QuotedColumnsTableMetaData(wrapped, "\"");

        final String actual =
                DatabaseDataSet.getSelectStatement("PUBLIC", metaData, null, false);

        assertThat(actual)
                .as("Each column and the order by key are quoted, the table is as it is.")
                .isEqualTo("select \"ID\", \"VALUE\", \"mixedCase\" from PUBLIC.Q1"
                        + " order by \"ID\"");
    }

    @Test
    void testGetSelectStatement_quotedColumnsWithoutPrimaryKeyAndSortAll_quotesTheSortColumns()
            throws Exception
    {
        final ITableMetaData noKey = new DefaultTableMetaData("Q1",
                new Column[] {ID, VALUE}, new Column[0]);
        final ITableMetaData metaData = new QuotedColumnsTableMetaData(noKey, "`");

        final String actual =
                DatabaseDataSet.getSelectStatement(null, metaData, null, true);

        assertThat(actual).as("The sort columns of the order by are quoted too.")
                .isEqualTo("select `ID`, `VALUE` from Q1 order by `ID`, `VALUE`");
    }

    @Test
    void testGetSelectStatement_quotedColumnsAndEscapePattern_theEscapePatternWins()
            throws Exception
    {
        final ITableMetaData metaData = new QuotedColumnsTableMetaData(wrapped, "`");

        final String actual =
                DatabaseDataSet.getSelectStatement("PUBLIC", metaData, "\"?\"", false);

        assertThat(actual)
                .as("The escape pattern escapes the names as it always has, the table too.")
                .isEqualTo("select \"ID\", \"VALUE\", \"mixedCase\" from \"PUBLIC\".\"Q1\""
                        + " order by \"ID\"");
    }

    @Test
    void testGetSelectStatement_plainMetaData_writesTheNamesAsTheyAre() throws Exception
    {
        final String actual =
                DatabaseDataSet.getSelectStatement("PUBLIC", wrapped, null, false);

        assertThat(actual).as("The default behavior writes the names unquoted.")
                .isEqualTo("select ID, VALUE, mixedCase from PUBLIC.Q1 order by ID");
    }
}
