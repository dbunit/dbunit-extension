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
package org.dbunit.database.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Set;

import org.dbunit.DdlExecutor;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.CollectionsHelper;
import org.dbunit.util.search.DepthFirstSearch;
import org.dbunit.util.search.ISearchCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 28, 2005
 */
public abstract class AbstractMetaDataBasedSearchCallbackTestCase
{

    private String sqlFile;

    private Connection jdbcConnection;

    private IDatabaseConnection connection;

    public void setSqlFile(final String sqlFile)
    {
        this.sqlFile = sqlFile;
    }

    public void setUpConnectionWithFile(final String sqlFile) throws Exception
    {
        this.sqlFile = sqlFile;
        this.setUpConnection();
    }

    protected void setUpConnection() throws Exception
    {
        this.jdbcConnection =
                HypersonicEnvironment.createJdbcConnection("mem:tempdb");
        DdlExecutor.executeDdlFile(TestUtils.getFile("sql/" + this.sqlFile),
                this.jdbcConnection);
        this.connection = new DatabaseConnection(jdbcConnection);
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        HypersonicEnvironment.shutdown(this.jdbcConnection);
        this.jdbcConnection.close();
        // HypersonicEnvironment.deleteFiles( "tempdb" );
    }

    protected IDatabaseConnection getConnection()
    {
        return this.connection;
    }

    protected abstract String[][] getInput();

    protected abstract String[][] getExpectedOutput();

    protected abstract AbstractMetaDataBasedSearchCallback getCallback(
            IDatabaseConnection connection2);

    @Test
    void testAllInput() throws Exception
    {
        final IDatabaseConnection connection = getConnection();

        final String[][] allInput = getInput();
        final String[][] allExpectedOutput = getExpectedOutput();
        final ISearchCallback callback = getCallback(connection);
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final DepthFirstSearch search = new DepthFirstSearch();
            final Set result = search.search(input, callback);
            final String[] actualOutput =
                    CollectionsHelper.setToStrings(result);
            assertThat(actualOutput).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

}
