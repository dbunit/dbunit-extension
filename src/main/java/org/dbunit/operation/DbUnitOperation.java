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

import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;

/**
 * Enumerates the database operations available for use with {@link DbUnitSetup} and
 * {@link DbUnitTearDown} annotations.
 *
 * <p>Each constant maps to the corresponding {@link DatabaseOperation} constant.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSetup
 * @see DbUnitTearDown
 */
public enum DbUnitOperation {
    /** Performs no operation. */
    NONE(DatabaseOperation.NONE),

    /** Inserts dataset rows. Fails if a row already exists. */
    INSERT(DatabaseOperation.INSERT),

    /** Updates existing rows in the dataset. */
    UPDATE(DatabaseOperation.UPDATE),

    /** Inserts or updates rows (upsert). */
    REFRESH(DatabaseOperation.REFRESH),

    /** Deletes rows matching the dataset. */
    DELETE(DatabaseOperation.DELETE),

    /** Deletes all rows from each table in the dataset. */
    DELETE_ALL(DatabaseOperation.DELETE_ALL),

    /** Truncates each table in the dataset. */
    TRUNCATE_TABLE(DatabaseOperation.TRUNCATE_TABLE),

    /** Deletes all rows then inserts the dataset rows. The default setup operation. */
    CLEAN_INSERT(DatabaseOperation.CLEAN_INSERT);

    private final DatabaseOperation databaseOperation;

    DbUnitOperation(final DatabaseOperation databaseOperation) {
        this.databaseOperation = databaseOperation;
    }

    /**
     * Returns the corresponding {@link DatabaseOperation} constant.
     *
     * @return The {@link DatabaseOperation} for this enum value.
     */
    public DatabaseOperation toDatabaseOperation() {
        return databaseOperation;
    }
}
