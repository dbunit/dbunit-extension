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
package org.dbunit;

import java.util.List;

/**
 * Aggregates the {@link PrepAndExpectedTestCase#postTest(boolean)} failures a
 * {@link MultiDataSourcePrepAndExpectedTestCase} collects across every involved data source,
 * instead of stopping at the first one.
 * <p>
 * Extends {@link AssertionError} - not a checked exception, and not
 * {@code org.opentest4j.MultipleFailuresError} - so a caller that already
 * {@code assertThrows(AssertionError.class, ...)} around a single data source's
 * {@link org.dbunit.assertion.DbComparisonFailure} keeps working whether one data source fails or
 * several, and this class stays usable from a non-JUnit {@link PrepAndExpectedTestCase} delegate.
 * <p>
 * The first failure - in the wired data sources' declared order, not necessarily the reverse
 * order they were torn down in - becomes this instance's {@linkplain #getCause() cause}, so it
 * still surfaces one {@code Caused by:} hop below this instance, exactly as thrown by its
 * delegate. Every other failure is {@linkplain #addSuppressed(Throwable) added as suppressed},
 * labelled with its data source name and lifecycle phase so it reads without unwrapping.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class MultiDataSourceAssertionError extends AssertionError
{
    private static final long serialVersionUID = 1L;

    private MultiDataSourceAssertionError(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Builds the aggregate from one or more {@code postTest} failures: names every failing data
     * source and its phase in the message, uses the first failure as this instance's cause, and
     * attaches the rest as labelled suppressed exceptions.
     *
     * @param namedFailures
     *            The failures to aggregate, in the order they should be named and reported; not
     *            empty.
     * @return The aggregate error.
     */
    static MultiDataSourceAssertionError aggregate(final List<NamedFailure> namedFailures)
    {
        final int failureCount = namedFailures.size();
        final String message = makeMessage(failureCount, namedFailures);
        return makeAggregateError(failureCount, message, namedFailures);
    }

    private static String makeMessage(final int failureCount, final List<NamedFailure> namedFailures)
    {
        final StringBuilder message = new StringBuilder();
        appendCountPrefix(failureCount, message);
        appendFailureDetails(failureCount, message, namedFailures);
        return message.toString();
    }

    private static void appendCountPrefix(final int failureCount, final StringBuilder message)
    {
        final String plurality = failureCount == 1 ? "" : "s";
        message.append(failureCount);
        message.append(" data source");
        message.append(plurality);
        message.append(" failed in postTest: ");
    }

    private static void appendFailureDetails(final int failureCount, final StringBuilder message, final List<NamedFailure> namedFailures)
    {
        for (int i = 0; i < failureCount; i++)
        {
            if (i > 0)
            {
                message.append(", ");
            }
            final NamedFailure namedFailure = namedFailures.get(i);
            message.append(namedFailure.dataSourceName);
            message.append(" (");
            message.append(namedFailure.phase);
            message.append(')');
        }
    }

    private static MultiDataSourceAssertionError makeAggregateError(final int failureCount, final String message, final List<NamedFailure> namedFailures)
    {
        final NamedFailure first = namedFailures.get(0);
        final MultiDataSourceAssertionError aggregateError =
                new MultiDataSourceAssertionError(message, first.failure);
        for (int i = 1; i < failureCount; i++)
        {
            final NamedFailure other = namedFailures.get(i);
            final DataSourceFailure dataSourceFailure =
                    new DataSourceFailure(other.dataSourceName, other.phase, other.failure);
            aggregateError.addSuppressed(dataSourceFailure);
        }
        return aggregateError;
    }

    /**
     * One data source's {@link PrepAndExpectedTestCase#postTest(boolean)} failure, paired with
     * the data source name and lifecycle phase {@link #aggregate(List)} reports it under.
     */
    static class NamedFailure
    {
        private final String dataSourceName;
        private final String phase;
        private final Throwable failure;

        NamedFailure(final String dataSourceName, final String phase, final Throwable failure)
        {
            this.dataSourceName = dataSourceName;
            this.phase = phase;
            this.failure = failure;
        }
    }

    /**
     * Labels a data source's failure with its name and lifecycle phase, so an entry this class
     * adds via {@link Throwable#addSuppressed(Throwable)} reads without unwrapping.
     */
    static class DataSourceFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        DataSourceFailure(final String dataSourceName, final String phase, final Throwable cause)
        {
            super(dataSourceName + " failed in " + phase + ".", cause);
        }
    }
}
