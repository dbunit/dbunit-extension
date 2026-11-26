/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class DiffCollectingFailureHandlerTest
{
    private DbUnitAssert assertion = new DbUnitAssert();

    private IDataSet getDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader(DbUnitAssertIT.FILE_PATH));
    }

    @Test
    void testAssertEquals_withTablesHavingDifferentValues_collectsDifferences() throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final DiffCollectingFailureHandler myHandler =
                new DiffCollectingFailureHandler();

        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), myHandler);

        final List<?> diffList = myHandler.getDiffList();
        assertThat(diffList).hasSize(1);
        final Difference diff = (Difference) diffList.get(0);
        assertThat(diff.getColumnName()).isEqualTo("COLUMN2");
        assertThat(diff.getExpectedValue()).isEqualTo("row 1 col 2");
        assertThat(diff.getActualValue()).isEqualTo("wrong value");
    }

    @Test
    void testCreateFailureForCollectedDifferences_oneDifference_isTheFailureTheDefaultHandlerThrows()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), collecting);
        final Throwable thrownByDefaultHandler = catchThrowable(
                () -> assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                        dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"),
                        new DefaultFailureHandler()));

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(failure).as("One difference gives the failure of the default handler.")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessage(thrownByDefaultHandler.getMessage());
    }

    @Test
    void testCreateFailureForCollectedDifferences_severalDifferences_listsEachOfThem()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE_ALL_ROWS"), collecting);
        final int collected = collecting.getDiffList().size();

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(collected).as("The precondition: the tables differ in more than one value.")
                .isGreaterThan(1);
        assertThat(failure.getMessage())
                .as("The message names the count, and each difference with its table, row and"
                        + " column.")
                .startsWith(collected + " differences were collected:")
                .contains("1. value (table=TEST_TABLE, row=0, col=COLUMN2")
                .contains("2. value (table=TEST_TABLE, row=1, col=COLUMN2");
    }

    @Test
    void testCreateFailureForCollectedDifferences_twoDifferences_returnsAComparisonFailureWithBothValues()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE_ALL_ROWS"), collecting);
        final String lineSeparator = System.lineSeparator();
        final String expectedText = "TEST_TABLE row 0 COLUMN2: row 0 col 2" + lineSeparator
                + "TEST_TABLE row 1 COLUMN2: row 1 col 2";
        final String actualText = "TEST_TABLE row 0 COLUMN2: wrong value" + lineSeparator
                + "TEST_TABLE row 1 COLUMN2: wrong value";
        final String expectedMessage = "2 differences were collected:" + lineSeparator
                + "1. value (table=TEST_TABLE, row=0, col=COLUMN2)" + lineSeparator
                + "2. value (table=TEST_TABLE, row=1, col=COLUMN2) expected:<" + expectedText
                + "> but was:<" + actualText + ">";

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(failure).as("Several differences give a comparison failure, as one does.")
                .isInstanceOf(DbComparisonFailure.class).isInstanceOf(AssertionError.class)
                .hasMessage(expectedMessage);
        final DbComparisonFailure comparisonFailure = (DbComparisonFailure) failure;
        assertThat(comparisonFailure.getExpected())
                .as("The expected text names each cell and its expected value, one per line.")
                .isEqualTo(expectedText);
        assertThat(comparisonFailure.getActual())
                .as("The actual text names the same cells in the same order with their actual"
                        + " values.")
                .isEqualTo(actualText);
    }

    @Test
    void testCreateFailureForCollectedDifferences_oneDifferenceAndAHint_carriesTheHintAsSuppressed()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), collecting);
        final IllegalStateException hint = new IllegalStateException("the sort hint");
        collecting.addHint(hint);

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(failure.getSuppressed())
                .as("The failure of one difference carries the hint, as a failure that a"
                        + " throwing handler gives does.")
                .containsExactly(hint);
    }

    @Test
    void testCreateFailureForCollectedDifferences_severalDifferencesAndHints_carriesEachHintInOrder()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE_ALL_ROWS"), collecting);
        final IllegalStateException first = new IllegalStateException("first hint");
        final IllegalStateException second = new IllegalStateException("second hint");
        collecting.addHint(first);
        collecting.addHint(second);

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(failure.getSuppressed())
                .as("The failure of several differences carries every hint, in the order they"
                        + " were added.")
                .containsExactly(first, second);
        assertThat(collecting.getHints()).as("The handler lists the hints it was given.")
                .containsExactly(first, second);
    }

    @Test
    void testCreateFailureForCollectedDifferences_noHint_carriesNoSuppressedException()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), collecting);

        final Error failure = collecting.createFailureForCollectedDifferences();

        assertThat(failure.getSuppressed()).as("Without a hint nothing is suppressed.")
                .isEmpty();
    }

    @Test
    void testClear_differencesAndHints_forgetsBoth() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"), collecting);
        collecting.addHint(new IllegalStateException("the sort hint"));

        collecting.clear();

        assertThat(collecting.getDiffList()).as("The differences are forgotten.").isEmpty();
        assertThat(collecting.getHints()).as("The hints are forgotten.").isEmpty();
    }

    @Test
    void testAddHint_nullHint_throwsNullPointerExceptionAndKeepsNoHint()
    {
        final DiffCollectingFailureHandler collecting = new DiffCollectingFailureHandler();

        final Throwable thrown = catchThrowable(() -> collecting.addHint(null));

        assertThat(thrown)
                .as("A null hint is rejected when it is added, not when the failure for the"
                        + " collected differences suppresses it, where the NullPointerException"
                        + " would replace the failure that explains the test.")
                .isInstanceOf(NullPointerException.class).hasMessageContaining("hint");
        assertThat(collecting.getHints()).as("The rejected hint is not kept.").isEmpty();
    }

}
