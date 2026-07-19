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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatabaseSequenceFilter}, run against a real H2 in-memory database
 * (rather than mocked {@link java.sql.ResultSet} rows) so that the foreign key metadata
 * driving the sort is genuine. A {@link org.mockito.Mockito#spy(Object)} wraps the real
 * {@link DatabaseMetaData} to count JDBC calls for the caching perf-guard.
 *
 * @since 3.2.1
 */
class DatabaseSequenceFilterTest
{
    private IDatabaseConnection connection;

    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            connection.close();
        }
    }

    @Test
    void testSort_multipleDependentTables_ordersChildrenAfterParents() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE PARENT (ID INT PRIMARY KEY)");
        stmt.execute(
                "CREATE TABLE CHILD (ID INT PRIMARY KEY, PARENT_ID INT REFERENCES PARENT(ID))");
        stmt.close();

        final String[] sorted = DatabaseSequenceFilter.sortTableNames(connection,
                new String[] {"CHILD", "PARENT"});

        assertThat(Arrays.asList(sorted))
                .as("PARENT must come before CHILD, since CHILD has a FK referencing PARENT.")
                .containsExactly("PARENT", "CHILD");
    }

    @Test
    void testSort_multipleTables_fetchesImportedKeysOncePerTable() throws Exception
    {
        final Connection realConnection = InMemoryDatabaseConnection.create().getConnection();
        final Statement stmt = realConnection.createStatement();
        stmt.execute("CREATE TABLE A (ID INT PRIMARY KEY)");
        stmt.execute("CREATE TABLE B (ID INT PRIMARY KEY, A_ID INT REFERENCES A(ID))");
        stmt.execute("CREATE TABLE C (ID INT PRIMARY KEY, B_ID INT REFERENCES B(ID))");
        stmt.close();

        final DatabaseMetaData spyMetaData = spy(realConnection.getMetaData());
        final Connection spyConnection = spy(realConnection);
        when(spyConnection.getMetaData()).thenReturn(spyMetaData);
        connection = new DatabaseConnection(spyConnection);

        DatabaseSequenceFilter.sortTableNames(connection, new String[] {"C", "B", "A"});

        verify(spyMetaData, times(1)).getImportedKeys(any(), any(), eq("A"));
        verify(spyMetaData, times(1)).getImportedKeys(any(), any(), eq("B"));
        verify(spyMetaData, times(1)).getImportedKeys(any(), any(), eq("C"));
        verify(spyMetaData, times(1)).getExportedKeys(any(), any(), eq("A"));
        verify(spyMetaData, times(1)).getExportedKeys(any(), any(), eq("B"));
        verify(spyMetaData, times(1)).getExportedKeys(any(), any(), eq("C"));
    }

    @Test
    void testSort_diamondDependencies_returnsValidTopologicalOrder() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE A (ID INT PRIMARY KEY)");
        stmt.execute("CREATE TABLE B (ID INT PRIMARY KEY, A_ID INT REFERENCES A(ID))");
        stmt.execute("CREATE TABLE C (ID INT PRIMARY KEY, A_ID INT REFERENCES A(ID))");
        stmt.execute("CREATE TABLE D (ID INT PRIMARY KEY, B_ID INT REFERENCES B(ID), "
                + "C_ID INT REFERENCES C(ID))");
        stmt.close();

        final String[] sorted = DatabaseSequenceFilter.sortTableNames(connection,
                new String[] {"D", "C", "B", "A"});
        final List<String> order = Arrays.asList(sorted);

        assertThat(order.indexOf("A")).as("A must precede B, its dependent.")
                .isLessThan(order.indexOf("B"));
        assertThat(order.indexOf("A")).as("A must precede C, its dependent.")
                .isLessThan(order.indexOf("C"));
        assertThat(order.indexOf("B")).as("B must precede D, its dependent.")
                .isLessThan(order.indexOf("D"));
        assertThat(order.indexOf("C")).as("C must precede D, its dependent.")
                .isLessThan(order.indexOf("D"));
    }

    @Test
    void testSort_independentTables_preservesOriginalOrder() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE X (ID INT PRIMARY KEY)");
        stmt.execute("CREATE TABLE Y (ID INT PRIMARY KEY)");
        stmt.execute("CREATE TABLE Z (ID INT PRIMARY KEY)");
        stmt.close();

        final String[] sorted = DatabaseSequenceFilter.sortTableNames(connection,
                new String[] {"Z", "X", "Y"});

        assertThat(Arrays.asList(sorted))
                .as("Tables with no FK relationship between them should keep their input order.")
                .containsExactly("Z", "X", "Y");
    }

    @Test
    void testSort_chainReversedInput_reordersChain() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE A (ID INT PRIMARY KEY)");
        stmt.execute("CREATE TABLE B (ID INT PRIMARY KEY, A_ID INT REFERENCES A(ID))");
        stmt.execute("CREATE TABLE C (ID INT PRIMARY KEY, B_ID INT REFERENCES B(ID))");
        stmt.close();

        final String[] sorted = DatabaseSequenceFilter.sortTableNames(connection,
                new String[] {"C", "B", "A"});

        assertThat(Arrays.asList(sorted))
                .as("A reverse-order chain A<-B<-C must be fully reordered to A, B, C.")
                .containsExactly("A", "B", "C");
    }

    @Test
    void testSort_lowerCaseFoldingDatabaseAndMismatchedInputCase_doesNotThrowSpuriousCycleException()
            throws Exception
    {
        final Connection realConnection = DriverManager.getConnection(
                "jdbc:h2:mem:dbunit_seqfilter_lowercase;DATABASE_TO_LOWER=TRUE");
        final Statement stmt = realConnection.createStatement();
        stmt.execute("CREATE TABLE PARENT (ID INT PRIMARY KEY)");
        stmt.execute(
                "CREATE TABLE CHILD (ID INT PRIMARY KEY, PARENT_ID INT REFERENCES PARENT(ID))");
        stmt.close();
        connection = new DatabaseConnection(realConnection);

        final String[] sorted = DatabaseSequenceFilter.sortTableNames(connection,
                new String[] {"CHILD", "PARENT"});

        assertThat(Arrays.asList(sorted))
                .as("PARENT must precede CHILD without a spurious CyclicTablesDependencyException, "
                        + "even though the database folds unquoted identifiers to lowercase and the "
                        + "caller-supplied names ('CHILD', 'PARENT') do not match that stored case.")
                .containsExactly("PARENT", "CHILD");
    }

}
