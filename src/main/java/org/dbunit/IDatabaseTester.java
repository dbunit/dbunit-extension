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

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;

/**
 * This interface defines the behavior of a DatabaseTester, which is responsible
 * for adding DBUnit features as composition on existing test cases (instead of
 * extending DBTestCase directly).
 *
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public interface IDatabaseTester
{
    /**
     * Close the specified connection.
     *
     * @param connection the connection to close.
     * @throws Exception if the connection cannot be closed.
     * @deprecated since 2.4.4 define a user defined
     *             {@link #setOperationListener(IOperationListener)} in advance
     */
    @Deprecated
    void closeConnection(IDatabaseConnection connection) throws Exception;

    /**
     * Returns the test database connection.
     *
     * @return the test database connection.
     * @throws Exception if the connection cannot be retrieved or created.
     */
    IDatabaseConnection getConnection() throws Exception;

    /**
     * Returns the test dataset.
     *
     * @return the test dataset.
     */
    IDataSet getDataSet();

    /**
     * Gets the DatabaseOperation to call when starting the test.
     *
     * @return the setup {@link DatabaseOperation}.
     */
    DatabaseOperation getSetUpOperation();

    /**
     * Gets the DatabaseOperation to call when ending the test.
     *
     * @return the tear-down {@link DatabaseOperation}.
     */
    DatabaseOperation getTearDownOperation();

    /**
     * Sets the test dataset to use.
     *
     * @param dataSet the test dataset to use.
     */
    void setDataSet(IDataSet dataSet);

    /**
     * Sets the schema value.
     *
     * @param schema the schema name.
     * @deprecated since 2.4.3 Should not be used anymore. Every concrete
     *             {@link IDatabaseTester} implementation that needs a schema
     *             has the possibility to set it somehow in the constructor
     */
    @Deprecated
    void setSchema(String schema);

    /**
     * Sets the DatabaseOperation to call when starting the test.
     *
     * @param setUpOperation the setup {@link DatabaseOperation}.
     */
    void setSetUpOperation(DatabaseOperation setUpOperation);

    /**
     * Sets the DatabaseOperation to call when ending the test.
     *
     * @param tearDownOperation the tear-down {@link DatabaseOperation}.
     */
    void setTearDownOperation(DatabaseOperation tearDownOperation);

    /**
     * TestCases must call this method inside setUp()
     *
     * @throws Exception if the setup operation fails.
     */
    void onSetup() throws Exception;

    /**
     * TestCases must call this method inside tearDown()
     *
     * @throws Exception if the tear-down operation fails.
     */
    void onTearDown() throws Exception;

    /**
     * Sets the listener notified of connection-retrieval and setup/tear-down events.
     *
     * @param operationListener
     *            The operation listener that is invoked on specific events in
     *            the {@link IDatabaseTester}.
     * @since 2.4.4
     */
    void setOperationListener(IOperationListener operationListener);

    /**
     * Returns the listener currently notified of connection-retrieval and setup/tear-down
     * events, so a caller composing a new listener can wrap it instead of discarding it.
     *
     * <p>Default method for binary compatibility with implementations predating this method;
     * they report no listener rather than failing to compile. Implementations backed by
     * {@link #setOperationListener(IOperationListener)} should override this to return the
     * value that method was last called with.
     *
     * @return the current {@link IOperationListener}, or {@code null} if
     *         {@link #setOperationListener(IOperationListener)} has never been called.
     * @since 3.6.0
     */
    default IOperationListener getOperationListener()
    {
        return null;
    }
}