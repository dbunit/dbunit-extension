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
package org.dbunit.database.statement;

import java.sql.Connection;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Abstract base for statement unit tests that require an in-memory database
 * connection with a pre-created test table.
 *
 * <p>Subclasses implement {@link #createTestTableDdl()} to supply the
 * {@code CREATE TABLE} statement for the table their tests exercise.
 *
 * @author DbUnit.org
 */
abstract class AbstractStatementTest
{
    protected IDatabaseConnection dbConn;
    protected Connection conn;

    @BeforeEach
    void setUp() throws Exception
    {
        dbConn = InMemoryDatabaseConnection.create();
        conn = dbConn.getConnection();
        conn.createStatement().execute(createTestTableDdl());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        dbConn.close();
    }

    /**
     * Returns the {@code CREATE TABLE} DDL statement for the test table.
     *
     * @return The DDL string executed during {@link #setUp()}.
     */
    protected abstract String createTestTableDdl();
}
