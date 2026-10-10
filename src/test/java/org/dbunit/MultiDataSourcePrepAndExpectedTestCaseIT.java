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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test of {@link MultiDataSourcePrepAndExpectedTestCase} against three independent
 * in-memory HSQLDB databases - a {@code catalog}, an {@code orders}, and an {@code inventory}
 * data source - each with one table, standing in for an application spanning multiple databases.
 * Inline SQL statements stand in for the application's own service layer, writing to all three
 * databases in one {@link PrepAndExpectedTestCaseSteps#run()} call.
 * <p>
 * {@link MultiDataSourcePrepAndExpectedTestCaseTest} covers this class's orchestration logic
 * (wiring, fan-out, ordering, failure aggregation) in isolation via recording/stub delegates; this
 * class instead proves the same wrapper end to end against real connections, real cleanup, and
 * real assertion failures.
 *
 * @since 3.6.0
 */
class MultiDataSourcePrepAndExpectedTestCaseIT
{
    private static final String CATALOG = "catalog";

    private static final String ORDERS = "orders";

    private static final String INVENTORY = "inventory";

    private static final String CATALOG_PREP = "/xml/multiDataSourceCatalogPrep.xml";

    private static final String CATALOG_EXPECTED = "/xml/multiDataSourceCatalogExpected.xml";

    private static final String ORDERS_PREP = "/xml/multiDataSourceOrdersPrep.xml";

    private static final String ORDERS_EXPECTED = "/xml/multiDataSourceOrdersExpected.xml";

    private static final String ORDERS_EXPECTED_MISMATCH =
            "/xml/multiDataSourceOrdersExpectedMismatch.xml";

    private static final String INVENTORY_PREP = "/xml/multiDataSourceInventoryPrep.xml";

    private static final String INVENTORY_EXPECTED = "/xml/multiDataSourceInventoryExpected.xml";

    private static final String INVENTORY_EXPECTED_MISMATCH =
            "/xml/multiDataSourceInventoryExpectedMismatch.xml";

    private static final VerifyTableDefinition PRODUCT_TABLE =
            new VerifyTableDefinition("PRODUCT", new String[0]);

    // ID is database-generated: exclude it from comparison and sort on only the remaining
    // (filtered) columns, so an unpredictable insertion/identity order can't misalign otherwise
    // equal rows
    private static final VerifyTableDefinition PURCHASE_ORDER_TABLE = new VerifyTableDefinition(
            "PURCHASE_ORDER", new String[] {"ID"}, null, null, true);

    private static final VerifyTableDefinition STOCK_TABLE =
            new VerifyTableDefinition("STOCK", new String[0]);

    private static final AtomicInteger RUN_COUNTER = new AtomicInteger();

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();

    private String catalogDbName;

    private String ordersDbName;

    private String inventoryDbName;

    private Connection catalogJdbcConnection;

    private Connection ordersJdbcConnection;

    private Connection inventoryJdbcConnection;

    private MultiDataSourcePrepAndExpectedTestCase testCase;

    @BeforeEach
    void setUp() throws Exception
    {
        final int run = RUN_COUNTER.incrementAndGet();
        catalogDbName = "mem:multiDsCatalog" + run;
        ordersDbName = "mem:multiDsOrders" + run;
        inventoryDbName = "mem:multiDsInventory" + run;

        catalogJdbcConnection = HypersonicEnvironment.createJdbcConnection(catalogDbName);
        ordersJdbcConnection = HypersonicEnvironment.createJdbcConnection(ordersDbName);
        inventoryJdbcConnection = HypersonicEnvironment.createJdbcConnection(inventoryDbName);

        createSchema(catalogJdbcConnection,
                "CREATE TABLE PRODUCT (SKU VARCHAR(20) PRIMARY KEY, NAME VARCHAR(60),"
                        + " UNIT_PRICE DECIMAL(10,2), UNITS_SOLD INTEGER)");
        createSchema(ordersJdbcConnection,
                "CREATE TABLE PURCHASE_ORDER (ID INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY"
                        + " KEY, CUSTOMER_ID BIGINT, SKU VARCHAR(20), QUANTITY INTEGER, TOTAL"
                        + " DECIMAL(10,2))");
        createSchema(inventoryJdbcConnection,
                "CREATE TABLE STOCK (SKU VARCHAR(20) PRIMARY KEY, QTY_ON_HAND INTEGER)");

        testCase = MultiDataSourcePrepAndExpectedTestCase.forTesters(dataFileLoader)
                .add(CATALOG, testerFor(catalogJdbcConnection))
                .add(ORDERS, testerFor(ordersJdbcConnection))
                .add(INVENTORY, testerFor(inventoryJdbcConnection));
    }

    @AfterEach
    void tearDown()
    {
        closeQuietly(catalogJdbcConnection);
        closeQuietly(ordersJdbcConnection);
        closeQuietly(inventoryJdbcConnection);
    }

    @Test
    void testRunTest_matchingPrepAndExpectedInEveryDataSource_doesNotThrow()
    {
        final Map<String, PrepAndExpectedTestData> data = new LinkedHashMap<>();
        data.put(CATALOG, catalogData(CATALOG_EXPECTED));
        data.put(ORDERS, ordersData(ORDERS_EXPECTED));
        data.put(INVENTORY, inventoryData(INVENTORY_EXPECTED));

        assertThatCode(() -> testCase.runTest(data, this::placeOrder))
                .as("Matching prep and expected data in every data source must not throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRunTest_expectedMismatchInTwoDataSources_throwsAssertionErrorNamingBoth()
    {
        final Map<String, PrepAndExpectedTestData> data = new LinkedHashMap<>();
        data.put(CATALOG, catalogData(CATALOG_EXPECTED));
        data.put(ORDERS, ordersData(ORDERS_EXPECTED_MISMATCH));
        data.put(INVENTORY, inventoryData(INVENTORY_EXPECTED_MISMATCH));

        final Throwable thrown = catchThrowable(() -> testCase.runTest(data, this::placeOrder));

        assertThat(thrown)
                .as("A mismatch in two data sources must surface as one"
                        + " MultiDataSourceAssertionError, not stop at the first.")
                .isInstanceOf(MultiDataSourceAssertionError.class);
        assertThat(thrown.getMessage())
                .as("The aggregate message must name both mismatched data sources.")
                .contains(ORDERS).contains(INVENTORY);
    }

    @Test
    void testRunTest_dataSourceOmittedFromTheRow_itsConnectionIsNeverOpened() throws Exception
    {
        testCase = MultiDataSourcePrepAndExpectedTestCase.forTesters(dataFileLoader)
                .add(CATALOG, testerFor(catalogJdbcConnection))
                .add(ORDERS, testerFor(ordersJdbcConnection))
                .add(INVENTORY, new ThrowingDatabaseTester());

        final Map<String, PrepAndExpectedTestData> data = new LinkedHashMap<>();
        data.put(CATALOG, catalogData(CATALOG_EXPECTED));
        data.put(ORDERS, ordersData(ORDERS_EXPECTED));
        // inventory intentionally omitted: its tester throws if its connection is ever opened

        assertThatCode(() -> testCase.runTest(data, this::placeOrder))
                .as("A data source omitted from the data map must never have its connection"
                        + " opened, even when opening it would throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRunTest_success_cleansAndClosesEveryConnection() throws Exception
    {
        final Map<String, PrepAndExpectedTestData> data = new LinkedHashMap<>();
        data.put(CATALOG, catalogData(CATALOG_EXPECTED));
        data.put(ORDERS, ordersData(ORDERS_EXPECTED));
        data.put(INVENTORY, inventoryData(INVENTORY_EXPECTED));

        testCase.runTest(data, this::placeOrder);

        assertThat(catalogJdbcConnection.isClosed())
                .as("catalog's connection must be closed after a successful runTest(...).")
                .isTrue();
        assertThat(ordersJdbcConnection.isClosed())
                .as("orders' connection must be closed after a successful runTest(...).")
                .isTrue();
        assertThat(inventoryJdbcConnection.isClosed())
                .as("inventory's connection must be closed after a successful runTest(...).")
                .isTrue();

        assertRowCount(catalogDbName, "PRODUCT", 0);
        assertRowCount(ordersDbName, "PURCHASE_ORDER", 0);
        assertRowCount(inventoryDbName, "STOCK", 0);
    }

    /**
     * Stands in for the application's service layer: one call writing to all three databases,
     * via plain JDBC rather than any dbUnit type.
     */
    private Object placeOrder() throws Exception
    {
        try (Statement statement = catalogJdbcConnection.createStatement())
        {
            statement.executeUpdate(
                    "UPDATE PRODUCT SET UNITS_SOLD = UNITS_SOLD + 3 WHERE SKU = 'WIDGET-1'");
        }
        try (Statement statement = ordersJdbcConnection.createStatement())
        {
            statement.executeUpdate("INSERT INTO PURCHASE_ORDER (CUSTOMER_ID, SKU, QUANTITY,"
                    + " TOTAL) VALUES (42, 'WIDGET-1', 3, 29.97)");
        }
        try (Statement statement = inventoryJdbcConnection.createStatement())
        {
            statement.executeUpdate(
                    "UPDATE STOCK SET QTY_ON_HAND = QTY_ON_HAND - 3 WHERE SKU = 'WIDGET-1'");
        }
        return null;
    }

    private static PrepAndExpectedTestData catalogData(final String expectedFile)
    {
        return new PrepAndExpectedTestData(new VerifyTableDefinition[] {PRODUCT_TABLE},
                new String[] {CATALOG_PREP}, new String[] {expectedFile});
    }

    private static PrepAndExpectedTestData ordersData(final String expectedFile)
    {
        return new PrepAndExpectedTestData(new VerifyTableDefinition[] {PURCHASE_ORDER_TABLE},
                new String[] {ORDERS_PREP}, new String[] {expectedFile});
    }

    private static PrepAndExpectedTestData inventoryData(final String expectedFile)
    {
        return new PrepAndExpectedTestData(new VerifyTableDefinition[] {STOCK_TABLE},
                new String[] {INVENTORY_PREP}, new String[] {expectedFile});
    }

    private static void createSchema(final Connection connection, final String ddl)
            throws Exception
    {
        DdlExecutor.executeSql(connection, ddl);
    }

    private static IDatabaseTester testerFor(final Connection connection) throws Exception
    {
        final IDatabaseConnection databaseConnection = new DatabaseConnection(connection);
        final IDatabaseTester tester = new DefaultDatabaseTester(databaseConnection);
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        return tester;
    }

    private static void assertRowCount(final String dbName, final String tableName,
            final int expectedRows) throws Exception
    {
        try (Connection connection = HypersonicEnvironment.createJdbcConnection(dbName);
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + tableName))
        {
            resultSet.next();
            assertThat(resultSet.getInt(1))
                    .as("Table " + tableName + " must be empty; a successful runTest(...) must"
                            + " have cleaned it up.")
                    .isEqualTo(expectedRows);
        }
    }

    private static void closeQuietly(final Connection connection)
    {
        try
        {
            if (connection != null && !connection.isClosed())
            {
                connection.close();
            }
        } catch (final SQLException e)
        {
            // best-effort cleanup between tests; a connection cleanupData() already closed is
            // expected to land here
        }
    }

    /**
     * An {@link IDatabaseTester} that throws if its connection is ever requested, for proving a
     * data source omitted from a {@code runTest(...)} data map is never touched.
     */
    private static final class ThrowingDatabaseTester extends AbstractDatabaseTester
    {
        @Override
        public IDatabaseConnection getConnection()
        {
            throw new AssertionError("This data source's connection must never be opened when"
                    + " it is omitted from the runTest(...) data map.");
        }
    }
}
