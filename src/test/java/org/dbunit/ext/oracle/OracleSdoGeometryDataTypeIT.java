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

package org.dbunit.ext.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link OracleSdoGeometryDataType#getSqlValue} against a live Oracle database.
 *
 * <p>Skipped automatically when the active database profile does not list
 * {@link TestFeature#SDO_GEOMETRY} as supported, or when the SDO_GEOMETRY table column is
 * not accessible via JDBC metadata in the current Oracle environment (e.g. some Docker images).
 *
 * @author Jeff Jensen
 * @since 2.8.0
 */
public class OracleSdoGeometryDataTypeIT
{
    private static final String TABLE_NAME = "SDO_GEOMETRY_TABLE";
    private static final DataType SDO_GEOMETRY_TYPE =
            OracleDataTypeFactory.ORACLE_SDO_GEOMETRY_TYPE;

    private IDatabaseConnection connection;

    /**
     * Guards every test: skips when SDO_GEOMETRY is not supported by the active profile or when
     * the SDO_GEOMETRY table column is not accessible via JDBC metadata in the current Oracle environment.
     * Opens the database connection for use by tests.
     *
     * @throws Exception if the connection cannot be obtained.
     */
    @BeforeEach
    void setUp() throws Exception
    {
        assumeTrue(AbstractDatabaseIT.environmentHasFeature(TestFeature.SDO_GEOMETRY),
                "Skipping: SDO_GEOMETRY not supported by current database profile.");
        connection = DatabaseEnvironment.getInstance().getConnection();
        connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new OracleDataTypeFactory());
        assumeSdoGeometryColumnAccessible();
    }

    /**
     * Removes all rows from {@value #TABLE_NAME} and closes the connection.
     *
     * @throws Exception if teardown fails.
     */
    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            try
            {
                DatabaseOperation.DELETE_ALL.execute(connection,
                        connection.createDataSet(new String[] {TABLE_NAME}));
            }
            catch (final DataSetException e)
            {
                // Table may not exist if SDO_GEOMETRY is not fully supported in this Oracle environment.
            }
            finally
            {
                connection.close();
                connection = null;
            }
        }
    }

    private void assumeSdoGeometryColumnAccessible() throws Exception
    {
        try
        {
            final ITableMetaData tableMetaData =
                    connection.createDataSet().getTableMetaData(TABLE_NAME);
            final Column[] columns = tableMetaData.getColumns();
            boolean valColumnFound = false;
            for (final Column column : columns)
            {
                if ("VAL".equalsIgnoreCase(column.getColumnName()))
                {
                    valColumnFound = true;
                    break;
                }
            }
            assumeTrue(valColumnFound,
                    "Skipping: " + TABLE_NAME
                            + ".VAL (SDO_GEOMETRY) column not accessible via JDBC metadata"
                            + " in this Oracle environment.");
        }
        catch (final DataSetException e)
        {
            assumeTrue(false,
                    "Skipping: " + TABLE_NAME + " table not accessible in this Oracle environment: "
                            + e.getMessage());
        }
    }

    /**
     * Verifies that reading a SQL-NULL SDO_GEOMETRY value returns the sentinel string {@code "NULL"}.
     *
     * @throws Exception if the test fails.
     */
    @Test
    void testGetSqlValue_withNullGeometry_returnsNullSentinelString() throws Exception
    {
        final IDataSet dataSet = new FlatXmlDataSetBuilder()
                .build(TestUtils.getFile("xml/sdoGeometryUpdateTest.xml"));
        DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);

        final Connection jdbcConnection = connection.getConnection();
        try (PreparedStatement stmt = jdbcConnection.prepareStatement(
                "SELECT VAL FROM " + TABLE_NAME + " WHERE PK = ?"))
        {
            stmt.setInt(1, 2);
            try (ResultSet rs = stmt.executeQuery())
            {
                assertThat(rs.next()).as("row with PK=2 exists.").isTrue();
                final Object actual = SDO_GEOMETRY_TYPE.getSqlValue(1, rs);
                assertThat(actual).as("null SDO_GEOMETRY returns NULL sentinel string.").isEqualTo("NULL");
            }
        }
    }

    /**
     * Verifies that reading a non-null SDO_GEOMETRY value returns a string parseable
     * back to an {@link OracleSdoGeometry} by {@link OracleSdoGeometryDataType#typeCast}.
     *
     * @throws Exception if the test fails.
     */
    @Test
    void testGetSqlValue_withNonNullGeometry_returnsParseableGeometryString() throws Exception
    {
        final IDataSet dataSet = new FlatXmlDataSetBuilder()
                .build(TestUtils.getFile("xml/sdoGeometryInsertTest.xml"));
        DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);

        final Connection jdbcConnection = connection.getConnection();
        try (PreparedStatement stmt = jdbcConnection.prepareStatement(
                "SELECT VAL FROM " + TABLE_NAME + " WHERE PK = ?"))
        {
            stmt.setInt(1, 0);
            try (ResultSet rs = stmt.executeQuery())
            {
                assertThat(rs.next()).as("row with PK=0 exists.").isTrue();
                final Object actual = SDO_GEOMETRY_TYPE.getSqlValue(1, rs);
                assertThat(actual).as("non-null SDO_GEOMETRY returns non-null result.").isNotNull();
                assertThat(actual).as("non-null SDO_GEOMETRY does not return NULL sentinel.").isNotEqualTo("NULL");
                final OracleSdoGeometry parsed = (OracleSdoGeometry) SDO_GEOMETRY_TYPE.typeCast(actual);
                assertThat(parsed).as("result string is parseable to OracleSdoGeometry.").isNotNull();
            }
        }
    }
}
