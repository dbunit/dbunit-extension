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

package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.dbunit.DatabaseUnitException;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.ant.AbstractStep;
import org.dbunit.ant.Export;
import org.dbunit.ant.Operation;
import org.dbunit.ant.Query;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvProducerTest
{
    private String driverClass;
    private String url;
    private String user;
    private String password;
    private IDatabaseConnection connection;
    private static final int ORDERS_ROWS_NUMBER = 5;
    private static final int ORDERS_ROW_ROWS_NUMBER = 3;
    private static final String THE_DIRECTORY =
            TestUtils.getFileName("csv/orders");

    @Test
    void testProduceFromFolder() throws DataSetException
    {
        final CsvProducer producer = new CsvProducer(THE_DIRECTORY);
        final CachedDataSet consumer = new CachedDataSet();
        // producer.setConsumer(new CsvDataSetWriter("src/csv/orders-out"));

        producer.setConsumer(consumer);
        producer.produce();
        final ITable[] tables = consumer.getTables();
        assertThat(tables).as("expected 2 tables").hasSize(2);

        final ITable orders = consumer.getTable("orders");
        assertThat(orders).as("orders table not found").isNotNull();
        assertThat(orders.getRowCount()).as("wrong number of rows")
                .isEqualTo(ORDERS_ROWS_NUMBER);
        assertThat(orders.getTableMetaData().getColumns().length)
                .as("wrong number of columns").isEqualTo(2);

        final ITable ordersRow = consumer.getTable("orders_row");
        assertThat(ordersRow).as("orders_row table not found").isNotNull();
        assertThat(ordersRow.getRowCount()).as("wrong number of rows")
                .isEqualTo(ORDERS_ROW_ROWS_NUMBER);
        assertThat(ordersRow.getTableMetaData().getColumns())
                .as("wrong number of columns").hasSize(ORDERS_ROW_ROWS_NUMBER);
    }

    @Test
    void testProduceAndInsertFromFolder()
            throws DatabaseUnitException, ClassNotFoundException, SQLException
    {
        produceAndInsertToDatabase();
        final Statement statement =
                connection.getConnection().createStatement();
        final ResultSet resultSet =
                statement.executeQuery("select count(*) from orders");
        resultSet.next();
        final int count = resultSet.getInt(1);
        assertThat(count).isEqualTo(ORDERS_ROWS_NUMBER);
        resultSet.close();
        statement.close();
    }

    private void produceAndInsertToDatabase()
            throws DatabaseUnitException, SQLException
    {
        final CsvProducer producer = new CsvProducer(THE_DIRECTORY);
        final CachedDataSet consumer = new CachedDataSet();
        producer.setConsumer(consumer);
        producer.produce();
        final DatabaseOperation operation = DatabaseOperation.INSERT;
        operation.execute(connection, consumer);
    }

    @Test
    void testInsertOperationWithCsvFormat()
            throws SQLException, DatabaseUnitException
    {
        final Operation operation = new Operation();
        operation.setFormat(AbstractStep.FORMAT_CSV);
        operation.setSrc(new File(THE_DIRECTORY));
        operation.setType("INSERT");
        operation.execute(connection);
        final Statement statement =
                connection.getConnection().createStatement();
        final ResultSet resultSet =
                statement.executeQuery("select count(*) from orders");
        resultSet.next();
        final int count = resultSet.getInt(1);
        assertThat(count).as("wrong number of row in orders table")
                .isEqualTo(ORDERS_ROWS_NUMBER);
        resultSet.close();
        statement.close();
    }

    @Test
    void testExportTaskWithCsvFormat()
            throws DatabaseUnitException, SQLException
    {
        produceAndInsertToDatabase();

        final String fromAnt = "target/csv/from-ant";
        final File dir = new File(fromAnt);
        FileHelper.deleteDirectory(dir);

        try
        {
            final Export export = new Export();
            export.setFormat(AbstractStep.FORMAT_CSV);
            export.setDest(dir);

            final Query query = new Query();
            query.setName("orders");
            query.setSql("select * from orders");
            export.addQuery(query);

            final Query query2 = new Query();
            query2.setName("orders_row");
            query2.setSql("select * from orders_row");
            export.addQuery(query2);

            export.execute(getConnection());

            final File ordersFile = new File(fromAnt + "/orders.csv");
            assertThat(ordersFile).as("file '" + ordersFile.getAbsolutePath()
                    + "' does not exists").exists();
            final File ordersRowFile = new File(fromAnt + "/orders_row.csv");
            assertThat(ordersRowFile)
                    .as("file " + ordersRowFile + " does not exists").exists();
        } finally
        {
            FileHelper.deleteDirectory(dir);
        }
    }

    private IDatabaseConnection getConnection()
            throws SQLException, DatabaseUnitException
    {
        final DatabaseConnection connection = new DatabaseConnection(
                DriverManager.getConnection(url, user, password));
        final DatabaseConfig config = connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new HsqldbDataTypeFactory());
        return connection;
    }

    @BeforeEach
    protected void setUp() throws Exception
    {
        final Properties properties = new Properties();
        final FileInputStream inStream =
                TestUtils.getFileInputStream("csv/cvs-tests.properties");
        properties.load(inStream);
        inStream.close();
        driverClass = properties.getProperty("cvs-tests.driver.class");
        url = properties.getProperty("cvs-tests.url");
        user = properties.getProperty("cvs-tests.user");
        password = properties.getProperty("cvs-tests.password");
        assertThat(driverClass).isNotEqualTo("");
        assertThat(url).isNotEqualTo("");
        assertThat(user).isNotEqualTo("");
        Class.forName(driverClass);
        connection = getConnection();
        final Statement statement =
                connection.getConnection().createStatement();
        try
        {
            statement.execute("DROP TABLE ORDERS");
            statement.execute("DROP TABLE ORDERS_ROW");
        } catch (final Exception ignored)
        {
        }
        statement.execute(
                "CREATE TABLE ORDERS (ID INTEGER, DESCRIPTION VARCHAR)");
        statement.execute(
                "CREATE TABLE ORDERS_ROW (ID INTEGER, DESCRIPTION VARCHAR, QUANTITY INTEGER)");
        // statement.execute("delete from orders");
        // statement.execute("delete from orders_row");
        statement.close();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        HypersonicEnvironment.shutdown(connection.getConnection());
        connection.close();
    }
}
