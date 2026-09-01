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
package org.dbunit.database.rowcount;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class UnexpectedRowCountExceptionTest
{
    @Test
    void testGetMessage_singleDifference_namesTheTableAndBothCounts()
    {
        final List<RowCountDifference> differences =
                Collections.singletonList(new RowCountDifference("ACCOUNT_AUDIT", 0, 3));

        final UnexpectedRowCountException exception =
                new UnexpectedRowCountException(differences);

        assertThat(exception.getMessage())
                .as("The message must name the affected table and both its counts.")
                .contains("ACCOUNT_AUDIT").contains("0 -> 3").contains("1 table differs");
    }

    @Test
    void testGetMessage_positiveAndNegativeDeltas_describesEachDirectionDistinctly()
    {
        final List<RowCountDifference> differences = Arrays.asList(
                new RowCountDifference("ACCOUNT_AUDIT", 0, 3),
                new RowCountDifference("COUNTRY_CODE", 12, 0));

        final UnexpectedRowCountException exception =
                new UnexpectedRowCountException(differences);

        assertThat(exception.getMessage())
                .as("The message must report both tables, each with wording specific to its"
                        + " own delta direction, and the overall count of affected tables.")
                .contains("2 tables differ")
                .contains("ACCOUNT_AUDIT").contains("rows left behind")
                .contains("COUNTRY_CODE").contains("rows removed that should remain");
    }

    @Test
    void testGetDifferences_afterConstruction_returnsSuppliedDifferences()
    {
        final List<RowCountDifference> differences =
                Collections.singletonList(new RowCountDifference("ACCOUNT_AUDIT", 0, 3));

        final UnexpectedRowCountException exception =
                new UnexpectedRowCountException(differences);

        assertThat(exception.getDifferences())
                .as("getDifferences() must return the differences the exception was built from.")
                .isEqualTo(differences);
    }

    @Test
    void testGetMessage_withAdditionalAdvice_appendsItAfterThePerTableDetail()
    {
        final List<RowCountDifference> differences =
                Collections.singletonList(new RowCountDifference("ACCOUNT_AUDIT", 0, 3));

        final UnexpectedRowCountException exception = new UnexpectedRowCountException(
                differences, "Add @DbUnitTearDown(operation = DELETE_ALL).");

        assertThat(exception.getMessage())
                .as("The standard per-table detail must still be present, with the extra advice"
                        + " appended after it.")
                .contains("ACCOUNT_AUDIT").contains("0 -> 3")
                .contains("Add @DbUnitTearDown(operation = DELETE_ALL).");
        assertThat(exception.getDifferences())
                .as("The two-arg constructor must keep getDifferences() working.")
                .isEqualTo(differences);
    }
}
