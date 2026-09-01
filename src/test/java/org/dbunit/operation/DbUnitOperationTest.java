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
package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DbUnitOperationTest
{
    @ParameterizedTest
    @MethodSource("provideOperationPairs")
    void testToDatabaseOperation_everyConstant_mapsToCorrespondingDatabaseOperation(
            final DbUnitOperation dbUnitOperation, final DatabaseOperation expected)
    {
        assertThat(dbUnitOperation.toDatabaseOperation())
                .as("%s must map to DatabaseOperation.%s.", dbUnitOperation, expected)
                .isSameAs(expected);
    }

    private static Stream<Arguments> provideOperationPairs()
    {
        return Stream.of(
                Arguments.of(DbUnitOperation.NONE, DatabaseOperation.NONE),
                Arguments.of(DbUnitOperation.INSERT, DatabaseOperation.INSERT),
                Arguments.of(DbUnitOperation.UPDATE, DatabaseOperation.UPDATE),
                Arguments.of(DbUnitOperation.REFRESH, DatabaseOperation.REFRESH),
                Arguments.of(DbUnitOperation.DELETE, DatabaseOperation.DELETE),
                Arguments.of(DbUnitOperation.DELETE_ALL, DatabaseOperation.DELETE_ALL),
                Arguments.of(DbUnitOperation.TRUNCATE_TABLE, DatabaseOperation.TRUNCATE_TABLE),
                Arguments.of(DbUnitOperation.CLEAN_INSERT, DatabaseOperation.CLEAN_INSERT));
    }
}
