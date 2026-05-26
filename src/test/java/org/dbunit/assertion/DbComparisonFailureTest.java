/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbComparisonFailure}.
 */
class DbComparisonFailureTest
{
    private static final String REASON = "value mismatch";
    private static final String EXPECTED = "expectedVal";
    private static final String ACTUAL = "actualVal";

    @Test
    void testConstructor_withAllArgs_storesAllFields()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, EXPECTED, ACTUAL);

        assertThat(failure.getReason()).as("reason.").isEqualTo(REASON);
        assertThat(failure.getExpected()).as("expected.").isEqualTo(EXPECTED);
        assertThat(failure.getActual()).as("actual.").isEqualTo(ACTUAL);
    }

    @Test
    void testGetMessage_withAllArgs_containsExpectedAndActual()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, EXPECTED, ACTUAL);

        final String message = failure.getMessage();

        assertThat(message).as("message contains reason.").contains(REASON);
        assertThat(message).as("message contains expected.").contains(EXPECTED);
        assertThat(message).as("message contains actual.").contains(ACTUAL);
    }

    @Test
    void testGetMessage_withAllArgs_hasCorrectFormat()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, EXPECTED, ACTUAL);

        final String expectedMessage = REASON + " expected:<" + EXPECTED + "> but was:<" + ACTUAL + ">";

        assertThat(failure.getMessage()).as("message format.").isEqualTo(expectedMessage);
    }

    @Test
    void testToString_withAllArgs_containsClassNameAndValues()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, EXPECTED, ACTUAL);

        final String result = failure.toString();

        assertThat(result).as("toString class name.").contains(DbComparisonFailure.class.getName());
        assertThat(result).as("toString expected.").contains(EXPECTED);
        assertThat(result).as("toString actual.").contains(ACTUAL);
        assertThat(result).as("toString reason.").contains(REASON);
    }

    @Test
    void testIsAssertionError_always_isSubclassOfAssertionError()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, EXPECTED, ACTUAL);

        assertThat(failure).as("isAssertionError.").isInstanceOf(AssertionError.class);
    }

    @Test
    void testBuildMessage_staticMethod_producesCorrectFormat()
    {
        final String message = DbComparisonFailure.buildMessage(REASON, EXPECTED, ACTUAL);

        final String expectedMessage = REASON + " expected:<" + EXPECTED + "> but was:<" + ACTUAL + ">";

        assertThat(message).as("buildMessage format.").isEqualTo(expectedMessage);
    }

    @Test
    void testBuildMessage_withEmptyReason_producesMessageWithoutLeadingText()
    {
        final String message = DbComparisonFailure.buildMessage("", EXPECTED, ACTUAL);

        assertThat(message).as("empty reason message.").startsWith(" expected:<");
        assertThat(message).as("contains expected.").contains(EXPECTED);
        assertThat(message).as("contains actual.").contains(ACTUAL);
    }

    @Test
    void testGetMessage_withEmptyExpectedAndActual_containsBrackets()
    {
        final DbComparisonFailure failure = new DbComparisonFailure(REASON, "", "");

        final String message = failure.getMessage();

        assertThat(message).as("empty values message.").isEqualTo(REASON + " expected:<> but was:<>");
    }
}
