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
package org.dbunit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;

/**
 * DatabaseTester that uses a {@link DataSource} to create connections.
 *
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public class DataSourceDatabaseTester extends AbstractDatabaseTester
{

	/**
	 * Logger for this class
	 */
	private static final Logger logger = LoggerFactory.getLogger(DataSourceDatabaseTester.class);

	private final CachingConnectionProvider connectionProvider;
	private DataSource dataSource;

	/**
	 * Creates a new DataSourceDatabaseTester with the specified DataSource.
	 *
	 * @param dataSource the DataSource to pull connections from
	 */
	public DataSourceDatabaseTester( DataSource dataSource )
	{
		this(dataSource, null, null);
	}

	/**
     * Creates a new DataSourceDatabaseTester with the specified DataSource and schema name.
     * @param dataSource the DataSource to pull connections from
	 * @param schema The schema name to be used for new dbunit connections
	 * @since 2.4.5
	 */
	public DataSourceDatabaseTester(DataSource dataSource, String schema)
	{
        this(dataSource, schema, null);
    }

    /**
     * Creates a new DataSourceDatabaseTester with the specified DataSource and schema name,
     * reusing one {@link IDatabaseConnection} across calls via the given
     * {@link CachingConnectionProvider} instead of creating a new one on every call.<br>
     * Share the same <code>connectionProvider</code> instance across the testers created for
     * each test to get reuse across test methods; pair it with
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} (or an equivalent non-closing
     * listener) so the cached connection is not closed after every {@link #onSetup()}/
     * {@link #onTearDown()} call.
     *
     * @param dataSource the DataSource to pull connections from
     * @param schema The schema name to be used for new dbunit connections - can be <code>null</code>
     * @param connectionProvider caches and validates the connection across calls - can be
     *            <code>null</code>, in which case a new connection is created on every call as before
     * @since 3.4.0
     */
    public DataSourceDatabaseTester(DataSource dataSource, String schema,
            CachingConnectionProvider connectionProvider)
    {
        super(schema);

        if (dataSource == null) {
            throw new NullPointerException(
                    "The parameter 'dataSource' must not be null");
        }
        this.dataSource = dataSource;
        this.connectionProvider = connectionProvider;
    }

    public IDatabaseConnection getConnection() throws Exception
	{
		logger.debug("getConnection() - start");

		assertTrue( "DataSource is not set", dataSource!=null );
		if (connectionProvider != null)
		{
			return connectionProvider.getConnection(this::createConnection);
		}
		return createConnection();
	}

	private IDatabaseConnection createConnection() throws Exception
	{
		return new DatabaseConnection( dataSource.getConnection(), getSchema() );
	}
}
