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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base testCase for database testing.<br>
 * Subclasses may override {@link #newDatabaseTester()} to plug-in a different implementation
 * of IDatabaseTester.<br> Default implementation uses a {@link PropertiesBasedJdbcDatabaseTester}.
 *
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public abstract class DBTestCase extends DatabaseTestCase {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DBTestCase.class);

  public DBTestCase() {
    super();
  }

  public DBTestCase(String name) {
    super(name);
  }

  protected final IDatabaseConnection getConnection() throws Exception {
        logger.debug("getConnection() - start");

        final IDatabaseTester databaseTester = getDatabaseTester();
        assertNotNull(databaseTester, "DatabaseTester is not set");
        final IDatabaseConnection connection = databaseTester.getConnection();
        // Ensure that users have the possibility to configure the connection's
        // configuration
        setUpDatabaseConfig(connection.getConfig());
        return connection;
    }

    /**
     * Creates a new IDatabaseTester. Default implementation returns a
     * {@link PropertiesBasedJdbcDatabaseTester}.
     */
    @Override
    protected IDatabaseTester newDatabaseTester() throws Exception {
        return new PropertiesBasedJdbcDatabaseTester();
    }

}
