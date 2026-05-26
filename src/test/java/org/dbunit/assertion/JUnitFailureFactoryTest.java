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
import org.opentest4j.AssertionFailedError;

/**
 * Unit tests for {@link JUnitFailureFactory}, which also exercises the
 * {@link FailureFactory} contract.
 */
class JUnitFailureFactoryTest
{
    private final JUnitFailureFactory factory = new JUnitFailureFactory();

    @Test
    void testCreateFailure_withMessageOnly_returnsAssertionFailedError()
    {
        final String message = "simple failure";

        final Error error = factory.createFailure(message);

        assertThat(error).as("error type.").isInstanceOf(AssertionFailedError.class);
        assertThat(error.getMessage()).as("message.").isEqualTo(message);
    }

    @Test
    void testCreateFailure_withMessageExpectedActual_returnsDbComparisonFailure()
    {
        final String message = "comparison failed";
        final String expected = "expectedValue";
        final String actual = "actualValue";

        final Error error = factory.createFailure(message, expected, actual);

        assertThat(error).as("error type.").isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testCreateFailure_withMessageExpectedActual_messageContainsAllParts()
    {
        final String message = "mismatch";
        final String expected = "exp";
        final String actual = "act";

        final Error error = factory.createFailure(message, expected, actual);

        assertThat(error.getMessage()).as("message contains reason.").contains(message);
        assertThat(error.getMessage()).as("message contains expected.").contains(expected);
        assertThat(error.getMessage()).as("message contains actual.").contains(actual);
    }

    @Test
    void testCreateFailure_withNullMessage_doesNotThrow()
    {
        final Error error = factory.createFailure(null);

        assertThat(error).as("error not null.").isNotNull();
    }

    @Test
    void testCreateFailure_implementsFailureFactory_satisfiesInterface()
    {
        final FailureFactory failureFactory = factory;

        final Error error = failureFactory.createFailure("test", "exp", "act");

        assertThat(error).as("error from interface.").isNotNull();
    }
}
