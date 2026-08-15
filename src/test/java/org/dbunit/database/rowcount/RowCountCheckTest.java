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
package org.dbunit.database.rowcount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

@ClearRowCountCheckSystemProperties
class RowCountCheckTest
{
    @Test
    void testCapture_disabled_returnsNullAndDoesNotQueryTheConnection() throws Exception
    {
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(new DatabaseConfig()));
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);

        final RowCountSnapshot result = check.capture(connection);

        assertThat(result).as("A disabled check must not capture a baseline.").isNull();
        verifyNoInteractions(connection);
    }

    @Test
    void testVerify_disabled_doesNotQueryTheConnection() throws Exception
    {
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(new DatabaseConfig()));
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot baseline = new RowCountSnapshot(mapOf("ACCOUNT", 5));

        assertThatCode(() -> check.verify(baseline, connection))
                .as("A disabled check must not verify, even with a real baseline.")
                .doesNotThrowAnyException();
        verifyNoInteractions(connection);
    }

    @Test
    void testVerify_nullBaseline_doesNotThrow() throws Exception
    {
        final RowCountCheck check =
                new RowCountCheck(new RowCountCheckConfiguration(enabledDatabaseConfig()));
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);

        assertThatCode(() -> check.verify(null, connection))
                .as("A null baseline means no baseline was captured; the check must skip"
                        + " silently rather than fail.")
                .doesNotThrowAnyException();
        verifyNoInteractions(connection);
    }

    @Test
    void testVerify_countsUnchanged_doesNotThrow() throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(rowCounter)));
        final IDatabaseConnection connection = connectionWithTables("ACCOUNT", "ACCOUNT_AUDIT");
        when(rowCounter.countRows(any(), any()))
                .thenReturn(mapOf("ACCOUNT", 5, "ACCOUNT_AUDIT", 0));
        final RowCountSnapshot baseline = new RowCountSnapshot(mapOf("ACCOUNT", 5, "ACCOUNT_AUDIT", 0));

        assertThatCode(() -> check.verify(baseline, connection))
                .as("Matching current counts must not be reported as a failure.")
                .doesNotThrowAnyException();
    }

    @Test
    void testVerify_countsChanged_throwsUnexpectedRowCountExceptionNamingEveryTable()
            throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(rowCounter)));
        final IDatabaseConnection connection =
                connectionWithTables("ACCOUNT_AUDIT", "COUNTRY_CODE");
        when(rowCounter.countRows(any(), any()))
                .thenReturn(mapOf("ACCOUNT_AUDIT", 3, "COUNTRY_CODE", 0));
        final RowCountSnapshot baseline =
                new RowCountSnapshot(mapOf("ACCOUNT_AUDIT", 0, "COUNTRY_CODE", 12));

        assertThatThrownBy(() -> check.verify(baseline, connection))
                .as("Every table whose count changed must be named in the failure, regardless"
                        + " of which direction it changed.")
                .isInstanceOf(UnexpectedRowCountException.class)
                .hasMessageContaining("ACCOUNT_AUDIT").hasMessageContaining("COUNTRY_CODE");
    }

    @Test
    void testCaptureThenVerify_tableDroppedBeforeVerify_reportsItWithZeroCurrentCount()
            throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(rowCounter)));
        final IDatabaseConnection connection =
                connectionWithSequentialTables(new String[] {"ACCOUNT", "COUNTRY_CODE"},
                        new String[] {"ACCOUNT"});
        when(rowCounter.countRows(any(), any()))
                .thenReturn(mapOf("ACCOUNT", 5, "COUNTRY_CODE", 12))
                .thenReturn(mapOf("ACCOUNT", 5));

        final RowCountSnapshot baseline = check.capture(connection);

        assertThatThrownBy(() -> check.verify(baseline, connection))
                .as("A table the connection no longer enumerates at verify time - dropped, or"
                        + " a differently-scoped connection - must be reported with a"
                        + " negative delta to zero, not throw a NullPointerException.")
                .isInstanceOf(UnexpectedRowCountException.class)
                .hasMessageContaining("COUNTRY_CODE").hasMessageContaining("12 -> 0");
    }

    @Test
    void testCaptureThenVerify_tableAddedBeforeVerify_reportsItWithZeroBaselineCount()
            throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(rowCounter)));
        final IDatabaseConnection connection = connectionWithSequentialTables(
                new String[] {"ACCOUNT"}, new String[] {"ACCOUNT", "COUNTRY_CODE"});
        when(rowCounter.countRows(any(), any()))
                .thenReturn(mapOf("ACCOUNT", 5))
                .thenReturn(mapOf("ACCOUNT", 5, "COUNTRY_CODE", 12));

        final RowCountSnapshot baseline = check.capture(connection);

        assertThatThrownBy(() -> check.verify(baseline, connection))
                .as("A table the connection newly enumerates at verify time - created since"
                        + " the baseline, or a differently-scoped connection - must be"
                        + " reported with a positive delta from zero, naming it even though"
                        + " the baseline never saw it.")
                .isInstanceOf(UnexpectedRowCountException.class)
                .hasMessageContaining("COUNTRY_CODE").hasMessageContaining("0 -> 12");
    }

    @Test
    void testCapture_configuredCounter_delegatesToItWithTheFilteredTableNames() throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(rowCounter)));
        final IDatabaseConnection connection = connectionWithTables("ACCOUNT", "ACCOUNT_AUDIT");
        when(rowCounter.countRows(any(), any()))
                .thenReturn(mapOf("ACCOUNT", 5, "ACCOUNT_AUDIT", 0));

        check.capture(connection);

        verify(rowCounter).countRows(connection, Arrays.asList("ACCOUNT", "ACCOUNT_AUDIT"));
    }

    @Test
    void testCapture_excludedTables_areNotPassedToTheCounter() throws Exception
    {
        final RowCounter rowCounter = mock(RowCounter.class);
        final DatabaseConfig databaseConfig = enabledDatabaseConfig(rowCounter);
        databaseConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                new String[] {"AUDIT_*"});
        final RowCountCheck check = new RowCountCheck(new RowCountCheckConfiguration(databaseConfig));
        final IDatabaseConnection connection = connectionWithTables("ACCOUNT", "AUDIT_LOG");
        when(rowCounter.countRows(any(), any())).thenReturn(mapOf("ACCOUNT", 5));

        check.capture(connection);

        verify(rowCounter).countRows(connection, Collections.singletonList("ACCOUNT"));
    }

    @Test
    void testCapture_customCounter_usesItInsteadOfTheDefault() throws Exception
    {
        final RowCounter customRowCounter = mock(RowCounter.class);
        final RowCountCheck check = new RowCountCheck(
                new RowCountCheckConfiguration(enabledDatabaseConfig(customRowCounter)));
        final IDatabaseConnection connection = connectionWithTables("ACCOUNT");
        when(customRowCounter.countRows(any(), any())).thenReturn(mapOf("ACCOUNT", 42));

        final RowCountSnapshot snapshot = check.capture(connection);

        assertThat(snapshot.getRowCounts())
                .as("The configured custom counter's result must be used, not the default"
                        + " QueryPerTableRowCounter's.")
                .containsEntry("ACCOUNT", 42);
    }

    private static DatabaseConfig enabledDatabaseConfig()
    {
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        return databaseConfig;
    }

    private static DatabaseConfig enabledDatabaseConfig(final RowCounter rowCounter)
    {
        final DatabaseConfig databaseConfig = enabledDatabaseConfig();
        databaseConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNTER, rowCounter);
        return databaseConfig;
    }

    private static IDatabaseConnection connectionWithTables(final String... tableNames)
            throws Exception
    {
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(tableNames);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.createDataSet()).thenReturn(dataSet);
        return connection;
    }

    /**
     * A connection whose enumerated tables differ between its first {@code createDataSet()}
     * call and every call after, simulating a table dropped or created between a baseline
     * capture and a later verify - or two calls that simply reach different connections.
     */
    private static IDatabaseConnection connectionWithSequentialTables(
            final String[] firstCallTableNames, final String[] laterCallsTableNames)
            throws Exception
    {
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(firstCallTableNames, laterCallsTableNames);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.createDataSet()).thenReturn(dataSet);
        return connection;
    }

    private static Map<String, Integer> mapOf(final Object... tableNameAndCountPairs)
    {
        final Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (int i = 0; i < tableNameAndCountPairs.length; i += 2)
        {
            rowCounts.put((String) tableNameAndCountPairs[i],
                    (Integer) tableNameAndCountPairs[i + 1]);
        }
        return rowCounts;
    }
}
