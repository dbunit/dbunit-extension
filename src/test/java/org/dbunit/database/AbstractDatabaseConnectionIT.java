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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseProfile;
import org.dbunit.IDatabaseTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 26, 2002
 */
abstract class AbstractDatabaseConnectionIT extends AbstractDatabaseIT
{
    private String schema;
    private DatabaseProfile profile;

    @Override
    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUp();
        this.profile = super.getEnvironment().getProfile();
        this.schema = this.profile.getSchema();
    }

    @Test
    final void testGetRowCount() throws Exception
    {
        assertThat(_connection.getRowCount("EMPTY_TABLE", null))
                .as("EMPTY_TABLE").isZero();
        assertThat(_connection.getRowCount("EMPTY_TABLE")).as("EMPTY_TABLE")
                .isZero();

        assertThat(_connection.getRowCount("TEST_TABLE", null)).as("TEST_TABLE")
                .isEqualTo(6);
        assertThat(_connection.getRowCount("TEST_TABLE")).as("TEST_TABLE")
                .isEqualTo(6);

        assertThat(_connection.getRowCount("PK_TABLE", "where PK0 = 0"))
                .as("PK_TABLE").isEqualTo(1);
    }

    @Test
    final void testGetRowCount_NonexistingSchema() throws Exception
    {
        final DatabaseProfile profile = super.getEnvironment().getProfile();
        final String nonexistingSchema = profile.getSchema() + "_444_XYZ_TEST";
        this.schema = nonexistingSchema;

        final IDatabaseTester dbTester =
                this.newDatabaseTester(nonexistingSchema);
        try
        {
            final IDatabaseConnection dbConnection = dbTester.getConnection();

            assertThat(dbConnection.getSchema())
                    .isEqualTo(convertString(nonexistingSchema));
            assertThatThrownBy(() -> dbConnection.getRowCount("TEST_TABLE")).as(
                    "Should not be able to retrieve row count for non-existing schema "
                            + nonexistingSchema)
                    .isInstanceOf(SQLException.class);

        } finally
        {
            // Reset the testers schema for subsequent tests
            // (environment.dbTester is a
            // singleton)
            dbTester.setSchema(profile.getSchema());
        }
    }

    @Test
    final void testGetRowCount_NoSchemaSpecified() throws Exception
    {
        final DatabaseProfile profile = super.getEnvironment().getProfile();
        this.schema = null;
        final IDatabaseTester dbTester = this.newDatabaseTester(this.schema);
        try
        {
            final IDatabaseConnection dbConnection = dbTester.getConnection();

            assertThat(dbConnection.getSchema()).isNull();
            assertThat(_connection.getRowCount("TEST_TABLE", null))
                    .as("TEST_TABLE").isEqualTo(6);
        } finally
        {
            // Reset the testers schema for subsequent tests
            // (environment.dbTester is a
            // singleton)
            dbTester.setSchema(profile.getSchema());
        }
    }

    private IDatabaseTester newDatabaseTester(final String schema)
            throws Exception
    {
        final IDatabaseTester tester = super.newDatabaseTester();
        tester.setSchema(schema);
        return tester;
    }

    @Override
    protected IDatabaseConnection getConnection() throws Exception
    {
        final String name = profile.getDriverClass();
        Class.forName(name);
        final Connection connection =
                DriverManager.getConnection(profile.getConnectionUrl(),
                        profile.getUser(), profile.getPassword());
        _connection = new DatabaseConnection(connection, profile.getSchema());

        final IDatabaseConnection dbunitConnection =
                new DatabaseConnection(connection, this.schema);
        return dbunitConnection;
    }

}
