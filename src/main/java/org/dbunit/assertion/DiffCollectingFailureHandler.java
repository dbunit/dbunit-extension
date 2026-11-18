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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.dbunit.dataset.ITable;

/**
 * A {@link FailureHandler} that collects the {@link Difference}s that
 * were found without throwing an exception.
 * <p>
 * You can use it as follows:
 * <pre><code>
 * IDataSet dataSet = getDataSet();
 * DiffCollectingFailureHandler myHandler = new DiffCollectingFailureHandler();
 * //invoke the assertion with the custom handler
 * assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
 *                        dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"),
 *                        myHandler);
 * // Evaluate the results
 * List diffList = myHandler.getDiffList();
 * Difference diff = (Difference)diffList.get(0);
 * ...
 * </code></pre>
 * <p>
 * Nothing fails while this handler collects, so whoever drives the comparison
 * has to read the collected differences afterwards, for example with
 * {@link #createFailureForCollectedDifferences()}. The annotation-driven
 * lifecycle ({@code DbUnitExtension}, {@code DbUnitTestExecutionListener}) does
 * so after the test's verification and fails the test with it. A comparison
 * that nothing reads afterwards cannot fail on a value that differs. Only a
 * difference in the number of rows still fails at once, as it does with the
 * {@link DefaultFailureHandler}.
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class DiffCollectingFailureHandler extends DefaultFailureHandler 
{
    private final List<Difference> diffList = new ArrayList<Difference>();

    private final List<Throwable> hints = new ArrayList<Throwable>();

    public void handle(Difference diff)
    {
        // Simply collect the difference without throwing an exception
        this.diffList.add(diff);
    }

    /**
     * Returns the list of collected {@link Difference}s.
     *
     * @return The list of collected {@link Difference}s
     */
    public List getDiffList()
    {
        return diffList;
    }

    /**
     * Adds a hint that explains the collected differences, for example that
     * rows were lined up against the wrong counterparts by the sort. A
     * handler that throws can carry such a hint on its failure, but this
     * handler throws nothing, so whoever finds the differences adds it here,
     * and {@link #createFailureForCollectedDifferences()} attaches it to the
     * failure as a suppressed exception.
     *
     * @param hint The hint.
     * @throws NullPointerException If the hint is null.
     * @since 3.6.0
     */
    public void addHint(final Throwable hint)
    {
        Objects.requireNonNull(hint, "The parameter 'hint' must not be null");
        hints.add(hint);
    }

    /**
     * Returns the hints that were added to explain the collected differences.
     *
     * @return The hints, in the order they were added, as a list that cannot
     *         be modified.
     * @since 3.6.0
     */
    public List<Throwable> getHints()
    {
        return Collections.unmodifiableList(hints);
    }

    /**
     * Forgets the collected {@link Difference}s and the hints, so that the
     * handler can serve another comparison.
     *
     * @since 3.6.0
     */
    public void clear()
    {
        diffList.clear();
        hints.clear();
    }

    /**
     * Creates the failure that reports every collected {@link Difference}, for
     * a caller that finds the list not empty after the comparison. One
     * difference gives the very failure that the {@link DefaultFailureHandler}
     * throws for it; several give one failure of the same kind, whose message
     * lists the table, the row and the column of each, and whose expected and
     * actual texts hold one line for each difference, in the same order, that
     * names the cell and its value. The failure factory of this handler decides
     * the type of the failure: the default factory gives a
     * {@link DbComparisonFailure}, an {@link AssertionError}, for one
     * difference and for several. The failure carries each hint that was added
     * with {@link #addHint(Throwable)} as a suppressed exception.
     *
     * @return The failure to throw. When the list is empty, a failure that
     *         lists no difference and has no expected and actual text.
     * @since 3.6.0
     */
    public Error createFailureForCollectedDifferences()
    {
        final Error failure = createFailureForDifferences();
        for (final Throwable hint : hints)
        {
            failure.addSuppressed(hint);
        }
        return failure;
    }

    private Error createFailureForDifferences()
    {
        if (diffList.size() == 1)
        {
            return createFailureFor(diffList.get(0));
        }

        final String heading = diffList.size() + " differences were collected:";
        if (diffList.isEmpty())
        {
            return createFailure(heading);
        }

        final String lineSeparator = System.lineSeparator();
        final StringBuilder reason = new StringBuilder(heading);
        final StringBuilder expected = new StringBuilder();
        final StringBuilder actual = new StringBuilder();
        for (int i = 0; i < diffList.size(); i++)
        {
            final Difference diff = diffList.get(i);
            reason.append(lineSeparator);
            reason.append(i + 1).append(". ").append(buildMessage(diff));
            if (i > 0)
            {
                expected.append(lineSeparator);
                actual.append(lineSeparator);
            }
            expected.append(describeCell(diff, diff.getExpectedValue()));
            actual.append(describeCell(diff, diff.getActualValue()));
        }
        return createFailure(reason.toString(), expected.toString(), actual.toString());
    }

    private String describeCell(final Difference diff, final Object value)
    {
        final ITable expectedTable = diff.getExpectedTable();
        final String tableName = expectedTable.getTableMetaData().getTableName();
        return tableName + " row " + diff.getRowIndex() + " " + diff.getColumnName() + ": "
                + value;
    }

    private Error createFailureFor(final Difference diff)
    {
        final String expected = String.valueOf(diff.getExpectedValue());
        final String actual = String.valueOf(diff.getActualValue());
        return createFailure(buildMessage(diff), expected, actual);
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(DiffCollectingFailureHandler.class.getName()).append("[");
        sb.append("diffList=").append(diffList);
        sb.append("]");
        return sb.toString();
    }
}
