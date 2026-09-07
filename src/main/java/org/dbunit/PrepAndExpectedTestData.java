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

import java.util.Arrays;
import java.util.Objects;

/**
 * The table verification definitions, prep data files, and expected data files
 * for one {@link PrepAndExpectedTestCase} run, bundled as a single immutable
 * value.
 * <p>
 * {@link PrepAndExpectedTestCase#configureTest(PrepAndExpectedTestData)},
 * {@link PrepAndExpectedTestCase#preTest(PrepAndExpectedTestData)}, and
 * {@link PrepAndExpectedTestCase#runTest(PrepAndExpectedTestData, PrepAndExpectedTestCaseSteps)}
 * each accept one of these in place of the three separate array arguments their
 * older overloads take. The three values describe one test scenario and are
 * meaningless apart, so passing them as one argument keeps a data-driven test's
 * {@code @MethodSource} rows, parameter lists, and any row-factory methods from
 * carrying the same triple through every layer, and collapses the three
 * per-scenario "empty" constants a suite would otherwise declare into the shared
 * {@link #NONE}.
 * <p>
 * An instance carries no mutable state of its own: the constructor copies each
 * array in, every getter copies its array out, and a {@code null} array is
 * normalized to an empty one. The {@link VerifyTableDefinition} elements are
 * referenced as given rather than deep-copied, so a caller must not mutate a
 * {@link VerifyTableDefinition} through its setters once it has been passed to
 * this constructor; doing so retroactively changes the scenario an
 * already-built instance describes. This matches how a suite is expected to
 * supply them - as shared {@code static final} constants.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class PrepAndExpectedTestData
{
    private static final VerifyTableDefinition[] NO_VERIFY_TABLE_DEFINITIONS = {};

    private static final String[] NO_DATA_FILES = {};

    /**
     * Verifies no tables, loads no prep data, and expects no data. The shared
     * constant for a scenario that needs none of the three, replacing a separate
     * empty constant per type.
     */
    public static final PrepAndExpectedTestData NONE = new PrepAndExpectedTestData(
            NO_VERIFY_TABLE_DEFINITIONS, NO_DATA_FILES, NO_DATA_FILES);

    private final VerifyTableDefinition[] verifyTableDefinitions;

    private final String[] prepDataFiles;

    private final String[] expectedDataFiles;

    /**
     * Creates an instance carrying the given verification definitions and prep
     * and expected data file paths.
     *
     * @param verifyTableDefinitions The table definitions to verify after the
     *            test runs; {@code null} or empty verifies no tables.
     * @param prepDataFiles The prep data files (as classpath resources) to load
     *            and insert as seed data; {@code null} or empty loads none.
     * @param expectedDataFiles The expected data files (as classpath resources)
     *            to verify actual data against after the test runs; {@code null}
     *            or empty expects none.
     */
    public PrepAndExpectedTestData(
            final VerifyTableDefinition[] verifyTableDefinitions,
            final String[] prepDataFiles, final String[] expectedDataFiles)
    {
        this.verifyTableDefinitions = copyOrEmpty(verifyTableDefinitions);
        this.prepDataFiles = copyOrEmpty(prepDataFiles);
        this.expectedDataFiles = copyOrEmpty(expectedDataFiles);
    }

    /**
     * Creates an instance that loads the given prep data files and does nothing
     * else - verifies no tables and expects no data.
     *
     * @param prepDataFiles The prep data files (as classpath resources) to load
     *            and insert as seed data.
     * @return The instance.
     */
    public static PrepAndExpectedTestData prepOnly(final String... prepDataFiles)
    {
        return new PrepAndExpectedTestData(NO_VERIFY_TABLE_DEFINITIONS,
                prepDataFiles, NO_DATA_FILES);
    }

    private static VerifyTableDefinition[] copyOrEmpty(
            final VerifyTableDefinition[] source)
    {
        return source == null ? NO_VERIFY_TABLE_DEFINITIONS : source.clone();
    }

    private static String[] copyOrEmpty(final String[] source)
    {
        return source == null ? NO_DATA_FILES : source.clone();
    }

    /**
     * Returns the table definitions to verify after the test runs.
     *
     * @return A copy of the verify table definitions; empty if none.
     */
    public VerifyTableDefinition[] getVerifyTableDefinitions()
    {
        return verifyTableDefinitions.clone();
    }

    /**
     * Returns the prep data file paths.
     *
     * @return A copy of the prep data file paths; empty if none.
     */
    public String[] getPrepDataFiles()
    {
        return prepDataFiles.clone();
    }

    /**
     * Returns the expected data file paths.
     *
     * @return A copy of the expected data file paths; empty if none.
     */
    public String[] getExpectedDataFiles()
    {
        return expectedDataFiles.clone();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Two instances are equal when their prep and expected file paths match by
     * content and their verify table definitions match by array position.
     * {@link VerifyTableDefinition} does not define value equality, so that part
     * of the comparison is by instance identity - which is what the intended
     * usage produces: sharing {@code static final} {@link VerifyTableDefinition}
     * constants across scenarios.
     */
    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof PrepAndExpectedTestData))
        {
            return false;
        }
        final PrepAndExpectedTestData other = (PrepAndExpectedTestData) o;
        return Arrays.equals(verifyTableDefinitions,
                other.verifyTableDefinitions)
                && Arrays.equals(prepDataFiles, other.prepDataFiles)
                && Arrays.equals(expectedDataFiles, other.expectedDataFiles);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(Arrays.hashCode(verifyTableDefinitions),
                Arrays.hashCode(prepDataFiles),
                Arrays.hashCode(expectedDataFiles));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return "PrepAndExpectedTestData[verifyTableDefinitions="
                + Arrays.toString(verifyTableDefinitions) + ", prepDataFiles="
                + Arrays.toString(prepDataFiles) + ", expectedDataFiles="
                + Arrays.toString(expectedDataFiles) + "]";
    }
}
