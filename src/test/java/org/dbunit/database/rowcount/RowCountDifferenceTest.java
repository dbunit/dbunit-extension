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

import org.junit.jupiter.api.Test;

class RowCountDifferenceTest
{
    @Test
    void testGetDelta_currentGreaterThanBaseline_returnsPositiveDelta()
    {
        final RowCountDifference difference = new RowCountDifference("ACCOUNT_AUDIT", 0, 3);

        assertThat(difference.getDelta())
                .as("Delta must be current minus baseline.").isEqualTo(3);
    }

    @Test
    void testGetDelta_currentLessThanBaseline_returnsNegativeDelta()
    {
        final RowCountDifference difference = new RowCountDifference("COUNTRY_CODE", 12, 0);

        assertThat(difference.getDelta())
                .as("Delta must be current minus baseline.").isEqualTo(-12);
    }

    @Test
    void testEquals_sameTableNameAndCounts_returnsTrue()
    {
        final RowCountDifference first = new RowCountDifference("ACCOUNT_AUDIT", 0, 3);
        final RowCountDifference second = new RowCountDifference("ACCOUNT_AUDIT", 0, 3);

        assertThat(first).as("Differences with identical field values must be equal.")
                .isEqualTo(second);
        assertThat(first.hashCode())
                .as("Equal differences must have equal hash codes.")
                .isEqualTo(second.hashCode());
    }

    @Test
    void testEquals_differentCurrentCount_returnsFalse()
    {
        final RowCountDifference first = new RowCountDifference("ACCOUNT_AUDIT", 0, 3);
        final RowCountDifference second = new RowCountDifference("ACCOUNT_AUDIT", 0, 4);

        assertThat(first).as("Differences with a different current count must not be equal.")
                .isNotEqualTo(second);
    }

    @Test
    void testToString_positiveDelta_namesTableBothCountsAndRowsLeftBehindAdvice()
    {
        final RowCountDifference difference = new RowCountDifference("ACCOUNT_AUDIT", 0, 3);

        assertThat(difference.toString())
                .as("A positive delta must be reported as rows left behind, with a fix"
                        + " pointing at the expected dataset or the exclude list.")
                .contains("ACCOUNT_AUDIT").contains("0 -> 3").contains("(+3)")
                .contains("rows left behind");
    }

    @Test
    void testToString_negativeDelta_namesTableBothCountsAndRowsRemovedAdvice()
    {
        final RowCountDifference difference = new RowCountDifference("COUNTRY_CODE", 12, 0);

        assertThat(difference.toString())
                .as("A negative delta must be reported as rows removed that should remain,"
                        + " with a fix pointing at the prep/expected dataset or the exclude list.")
                .contains("COUNTRY_CODE").contains("12 -> 0").contains("(-12)")
                .contains("rows removed that should remain");
    }
}
