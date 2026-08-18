/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link IDatabaseTester#getOperationListener()}'s default implementation directly,
 * since every implementation shipped in this codebase (e.g. {@link AbstractDatabaseTester})
 * overrides it - only a pre-3.6.0, third-party implementation predating this method would ever
 * actually run the interface's own default body.
 *
 * @since 3.6.0
 */
class IDatabaseTesterTest {
    @Test
    void testGetOperationListener_implementationDoesNotOverrideIt_returnsNullFromTheDefault() {
        final IDatabaseTester tester = new IDatabaseTester() {
            @Override
            public void onSetup() {
            }

            @Override
            public void onTearDown() {
            }

            @Override
            public IDatabaseConnection getConnection() {
                return null;
            }

            @Override
            public IDataSet getDataSet() {
                return null;
            }

            @Override
            public void setDataSet(final IDataSet dataSet) {
            }

            @Override
            public DatabaseOperation getSetUpOperation() {
                return null;
            }

            @Override
            public DatabaseOperation getTearDownOperation() {
                return null;
            }

            @Override
            public void setSetUpOperation(final DatabaseOperation setUpOperation) {
            }

            @Override
            public void setTearDownOperation(final DatabaseOperation tearDownOperation) {
            }

            @Override
            public void setOperationListener(final IOperationListener operationListener) {
            }

            @Override
            @Deprecated
            public void closeConnection(final IDatabaseConnection connection) {
            }

            @Override
            @Deprecated
            public void setSchema(final String schema) {
            }
        };

        assertThat(tester.getOperationListener())
                .as("An IDatabaseTester implementation predating getOperationListener() - so it"
                        + " does not override it - must report no listener via the default"
                        + " method rather than failing to compile.")
                .isNull();
    }
}
