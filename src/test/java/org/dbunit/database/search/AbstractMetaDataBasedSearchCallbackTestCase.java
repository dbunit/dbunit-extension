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

import java.sql.SQLException;
import java.util.Set;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DdlExecutor;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.CollectionsHelper;
import org.dbunit.util.search.DepthFirstSearch;
import org.dbunit.util.search.ISearchCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 28, 2005
 */
public abstract class AbstractMetaDataBasedSearchCallbackTestCase
        extends AbstractDatabaseIT
{

    private String sqlFile;

    public void setSqlFile(final String sqlFile)
    {
        this.sqlFile = sqlFile;
    }

    @BeforeEach
    public void setUpConnectionWithFile() throws Exception
    {
        this.sqlFile = getSqlFile();
        DdlExecutor.dropTables(_connection.getConnection(), getTestTableNames());
        DdlExecutor.executeDdlFile(TestUtils.getFile("sql/" + this.sqlFile),
                _connection.getConnection(), false);
    }

    @AfterEach
    protected void tearDownTables() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), getTestTableNames());
        refreshConnection();
    }

    /**
     * Replaces {@code _connection} with a fresh connection after test tables
     * have been dropped, so that {@code AbstractDatabaseIT.tearDown()} uses an
     * up-to-date dataset cache that does not include the dropped tables.
     *
     * @throws Exception
     */
    protected void refreshConnection() throws Exception
    {
        _connection.close();
        _connection = getDatabaseTester().getConnection();
        setUpDatabaseConfig(_connection.getConfig());
    }

    protected abstract String getSqlFile();

    protected abstract String[] getTestTableNames();

    protected IDatabaseConnection getConnection()
    {
        return _connection;
    }

    protected abstract String[][] getInput();

    protected abstract String[][] getExpectedOutput();

    protected abstract AbstractMetaDataBasedSearchCallback getCallback(
            IDatabaseConnection connection2);

    @Test
    void testAllInput() throws Exception
    {
        final IDatabaseConnection connection = getConnection();
        final boolean storesLowerCase = connection.getConnection().getMetaData()
                .storesLowerCaseIdentifiers();

        final String[][] allInput = getInput();
        final String[][] allExpectedOutput = getExpectedOutput();
        final ISearchCallback callback = getCallback(connection);
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] normalizedInput;
            if (storesLowerCase)
            {
                normalizedInput = new String[input.length];
                for (int j = 0; j < input.length; j++)
                {
                    normalizedInput[j] = input[j].toLowerCase();
                }
            }
            else
            {
                normalizedInput = input;
            }
            final String[] expectedOutput = allExpectedOutput[i];
            final DepthFirstSearch search = new DepthFirstSearch();
            final Set result = search.search(normalizedInput, callback);
            final String[] actualOutput =
                    CollectionsHelper.setToStrings(result);
            assertThat(actualOutput).as("output didn't match for i=" + i + ".")
                    .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                    .containsExactly(expectedOutput);
        }
    }

}
