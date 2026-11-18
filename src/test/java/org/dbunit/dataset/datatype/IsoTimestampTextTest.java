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

package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IsoTimestampTextTest
{
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "2024-01-15T10:00:00                 | 2024-01-15 10:00:00",
            "2024-01-15t10:00:00.5               | 2024-01-15 10:00:00.5",
            "2024-01-15T10:00:00.                | 2024-01-15 10:00:00",
            "2024-01-15T10:00:00Z                | 2024-01-15 10:00:00 +0000",
            "2024-01-15 10:00:00.25 Z            | 2024-01-15 10:00:00.25 +0000",
            "2024-01-15T10:00:00+01:00           | 2024-01-15 10:00:00 +0100",
            "2024-01-15t10:00:00.1-05:00         | 2024-01-15 10:00:00.1 -0500",
            "2024-01-15T10:00:00+0530            | 2024-01-15 10:00:00 +0530",
            "2024-01-15T10:00:00-05              | 2024-01-15 10:00:00 -0500",
            "2024-01-15 10:00:00.10 -5           | 2024-01-15 10:00:00.10 -0500",
            "2024-1-5T9:05:00                    | 2024-1-5 9:05:00",
            "'2024-03-01  10:15:30'              | 2024-03-01 10:15:30",
            "'2024-03-01\t10:15:30'              | 2024-03-01 10:15:30",
            "'2024-3-1 \t 1:15:30.5'             | 2024-3-1 1:15:30.5",
            "2024-03-01 10:15:30.                | 2024-03-01 10:15:30",
            "'2024-03-01  10:15:30 +01:00'       | 2024-03-01 10:15:30 +0100"})
    void testToJdbcTimestampText_isoForm_returnsTheJdbcForm(final String text,
            final String expected)
    {
        final String actual = IsoTimestampText.toJdbcTimestampText(text);

        assertThat(actual).as("The JDBC form of %s.", text).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "2024-01-15",
            "2024-01-15 10:00:00",
            "2024-01-15 10:00:00.123456789",
            "2024-1-5 9:05:00",
            "2024-01-15 10:00:00 +0100",
            "2024-01-15 10:00:00.5 -0500",
            "[now-1d]",
            "not a timestamp",
            "2024-01-15T10:00",
            "2024-01-15T10:00:00 UTC"})
    void testToJdbcTimestampText_otherText_returnsTheTextItself(final String text)
    {
        final String actual = IsoTimestampText.toJdbcTimestampText(text);

        assertThat(actual).as("Text that is not in the ISO form stays as it is.")
                .isSameAs(text);
    }
}
