/*
 * DatabaseConnectionTest.java   Mar 26, 2002
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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.Locale;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 26, 2002
 */
class DatabaseConnectionIT extends AbstractDatabaseConnectionIT
{

    @Override
    protected String convertString(final String str) throws Exception
    {
        return getEnvironment().convertString(str);
    }

    @Test
    void testCreateNullConnection() throws Exception
    {
        assertThrows(NullPointerException.class,
                () -> new DatabaseConnection(null),
                "Should not be able to create a database connection without a JDBC connection");
    }

    @Test
    void testCreateConnectionWithNonExistingSchemaAndStrictValidation()
            throws Exception
    {
        final DatabaseEnvironment environment = getEnvironment();
        final String schema =
                environment.convertString("XYZ_INVALID_SCHEMA_1642344539");
        final IDatabaseConnection validConnection = super.getConnection();
        final String expectedMsg = "The given schema '" + convertString(schema)
                + "' does not exist.";
        // Try to create a database connection with an invalid schema

        final boolean validate = true;
        final DatabaseUnitException expected = assertThrows(
                DatabaseUnitException.class,
                () -> new DatabaseConnection(validConnection.getConnection(),
                        schema, validate),
                "Should not be able to create a database connection object with an unknown schema.");
        assertThat(expected).hasMessageContaining(expectedMsg);
    }

    @Test
    void testCreateConnectionWithNonExistingSchemaAndLenientValidation()
            throws Exception
    {
        final DatabaseEnvironment environment = getEnvironment();
        final String schema =
                environment.convertString("XYZ_INVALID_SCHEMA_1642344539");
        final IDatabaseConnection validConnection = super.getConnection();
        // Try to create a database connection with an invalid schema
        final boolean validate = false;
        final DatabaseConnection dbConnection = new DatabaseConnection(
                validConnection.getConnection(), schema, validate);
        assertThat(dbConnection).isNotNull();
    }

    @Test
    void testCreateConnectionWithSchemaDbStoresUpperCaseIdentifiers()
            throws Exception
    {
        final IDatabaseConnection validConnection = super.getConnection();
        final String schema = validConnection.getSchema();
        assertThat(schema)
                .as("Precondition: schema of connection must not be null")
                .isNotNull();

        final DatabaseMetaData metaData =
                validConnection.getConnection().getMetaData();
        if (metaData.storesUpperCaseIdentifiers())
        {
            final boolean validate = true;
            final DatabaseConnection dbConnection =
                    new DatabaseConnection(validConnection.getConnection(),
                            schema.toLowerCase(Locale.ENGLISH), validate);
            assertThat(dbConnection).isNotNull();
            assertThat(dbConnection.getSchema())
                    .isEqualTo(schema.toUpperCase(Locale.ENGLISH));
        } else
        {
            // skip this test
            assertThat(true).isTrue();
        }
    }

    @Test
    void testCreateConnectionWithSchemaDbStoresLowerCaseIdentifiers()
            throws Exception
    {
        final IDatabaseConnection validConnection = super.getConnection();
        final String schema = validConnection.getSchema();
        assertThat(schema)
                .as("Precondition: schema of connection must not be null")
                .isNotNull();

        final DatabaseMetaData metaData =
                validConnection.getConnection().getMetaData();
        if (metaData.storesLowerCaseIdentifiers())
        {
            final boolean validate = true;
            final DatabaseConnection dbConnection =
                    new DatabaseConnection(validConnection.getConnection(),
                            schema.toUpperCase(Locale.ENGLISH), validate);
            assertThat(dbConnection).isNotNull();
            assertThat(dbConnection.getSchema())
                    .isEqualTo(schema.toLowerCase(Locale.ENGLISH));
        } else
        {
            // skip this test
            assertThat(true).isTrue();
        }
    }

    @Test
    void testCreateQueryWithPreparedStatement() throws Exception
    {
        final IDatabaseConnection connection = super.getConnection();
        final PreparedStatement pstmt = connection.getConnection()
                .prepareStatement("select * from TEST_TABLE where COLUMN0=?");

        try
        {
            pstmt.setString(1, "row 1 col 0");
            final ITable table = connection.createTable("MY_TABLE", pstmt);
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getTableMetaData().getColumns()).hasSize(4);
            assertThat(table.getValue(0, "COLUMN1")).isEqualTo("row 1 col 1");

            // Now reuse the prepared statement
            pstmt.setString(1, "row 2 col 0");
            final ITable table2 = connection.createTable("MY_TABLE", pstmt);
            assertThat(table2.getRowCount()).isEqualTo(1);
            assertThat(table2.getTableMetaData().getColumns()).hasSize(4);
            assertThat(table2.getValue(0, "COLUMN1")).isEqualTo("row 2 col 1");
        } finally
        {
            pstmt.close();
        }
    }

}
