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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MockStatementFactory}.
 */
class MockStatementFactoryTest
{
    @Test
    void testForSingleBatch_withExpectedSql_verifiesSuccessfullyAfterExecution()
            throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String expectedSql =
                "insert into schema.table (C1) values ('val')";

        final MockStatementFactory factory =
                MockStatementFactory.forSingleBatch(expectedSql);

        final org.dbunit.dataset.Column[] columns = {
                new org.dbunit.dataset.Column("C1",
                        org.dbunit.dataset.datatype.DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow(new Object[] {"val"});
        final IDataSet dataSet = new DefaultDataSet(table);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        DatabaseOperation.INSERT.execute(connection, dataSet);

        factory.verify();
        factory.getBatchStatement().verify();
        connection.verify();
    }

    @Test
    void testGetBatchStatement_afterForSingleBatch_returnsConfiguredStatement()
    {
        final MockStatementFactory factory =
                MockStatementFactory.forSingleBatch("SELECT 1");
        assertThat(factory.getBatchStatement()).as("batch statement").isNotNull();
    }

    @Test
    void testGetBatchStatement_withoutSetup_returnsNull()
    {
        final MockStatementFactory factory = new MockStatementFactory();
        assertThat(factory.getBatchStatement()).as("batch statement").isNull();
    }
}
