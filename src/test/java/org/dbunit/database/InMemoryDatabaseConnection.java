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

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test utility that creates an isolated H2 in-memory {@link IDatabaseConnection}
 * for unit tests that need a real connection without the full
 * {@link org.dbunit.DatabaseEnvironment} bootstrap.
 *
 * <p>Each call to {@link #create()} or {@link #create(String)} produces a
 * connection to a distinct in-memory H2 database, ensuring test isolation.
 * The caller is responsible for closing the returned connection.
 *
 * @since 3.2.0
 */
public final class InMemoryDatabaseConnection
{
    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private InMemoryDatabaseConnection()
    {
    }

    /**
     * Creates an {@link IDatabaseConnection} backed by a fresh H2 in-memory database.
     *
     * @return a new connection; caller is responsible for closing it
     * @throws Exception if the connection cannot be created
     */
    public static IDatabaseConnection create() throws Exception
    {
        final Connection conn = DriverManager.getConnection(nextUrl());
        return new DatabaseConnection(conn);
    }

    /**
     * Creates an {@link IDatabaseConnection} backed by a fresh H2 in-memory database
     * using the given schema name.
     *
     * @param schema the schema name
     * @return a new connection; caller is responsible for closing it
     * @throws Exception if the connection cannot be created
     */
    public static IDatabaseConnection create(final String schema) throws Exception
    {
        final Connection conn = DriverManager.getConnection(nextUrl());
        return new DatabaseConnection(conn, schema);
    }

    private static String nextUrl()
    {
        return "jdbc:h2:mem:dbunit_" + DB_COUNTER.incrementAndGet();
    }
}
