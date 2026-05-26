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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SimpleAssert}.
 */
class SimpleAssertTest
{
    private final FailureHandler failureHandler = new DefaultFailureHandler();

    @Test
    void testConstructor_withNullFailureHandler_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new SimpleAssert(null))
                .as("null handler throws NPE.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAssertTrue_withTrueCondition_doesNotThrow()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        simpleAssert.assertTrue(true);
    }

    @Test
    void testAssertTrue_withFalseCondition_throwsError()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        assertThatThrownBy(() -> simpleAssert.assertTrue(false))
                .as("false condition throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertTrue_withMessageAndFalseCondition_throwsErrorWithMessage()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);
        final String message = "condition was false";

        assertThatThrownBy(() -> simpleAssert.assertTrue(message, false))
                .as("message in thrown error.")
                .isInstanceOf(Error.class)
                .hasMessageContaining(message);
    }

    @Test
    void testAssertTrue_withMessageAndTrueCondition_doesNotThrow()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        simpleAssert.assertTrue("should pass", true);
    }

    @Test
    void testAssertNotNull_withNonNullObject_doesNotThrow()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        simpleAssert.assertNotNull(new Object());
    }

    @Test
    void testAssertNotNull_withNullObject_throwsError()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        assertThatThrownBy(() -> simpleAssert.assertNotNull(null))
                .as("null object throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertNotNull_withMessageAndNullObject_throwsError()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);
        final String message = "object must not be null";

        assertThatThrownBy(() -> simpleAssert.assertNotNull(message, null))
                .as("message in thrown error.")
                .isInstanceOf(Error.class)
                .hasMessageContaining(message);
    }

    @Test
    void testAssertNotNull_withMessageAndNonNullObject_doesNotThrow()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        simpleAssert.assertNotNull("should pass", "not null");
    }

    @Test
    void testFail_withMessage_throwsErrorWithMessage()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);
        final String message = "explicit fail";

        assertThatThrownBy(() -> simpleAssert.fail(message))
                .as("fail throws error with message.")
                .isInstanceOf(Error.class)
                .hasMessageContaining(message);
    }

    @Test
    void testAssertTrue_withCustomFailureHandler_usesHandlerToCreateError()
    {
        final JUnitFailureFactory factory = new JUnitFailureFactory();
        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.setFailureFactory(factory);
        final SimpleAssert simpleAssert = new SimpleAssert(handler);

        assertThatThrownBy(() -> simpleAssert.assertTrue("custom handler failure", false))
                .as("custom handler error.")
                .isInstanceOf(Error.class);
    }

    /**
     * Helper subclass to expose the protected assertNotNullNorEmpty method.
     */
    private static class TestableSimpleAssert extends SimpleAssert
    {
        TestableSimpleAssert(final FailureHandler failureHandler)
        {
            super(failureHandler);
        }

        void callAssertNotNullNorEmpty(final String propName, final String prop)
        {
            assertNotNullNorEmpty(propName, prop);
        }
    }

    @Test
    void testAssertNotNullNorEmpty_withNonEmptyString_doesNotThrow()
    {
        final TestableSimpleAssert simpleAssert = new TestableSimpleAssert(failureHandler);

        simpleAssert.callAssertNotNullNorEmpty("myProperty", "validValue");
    }

    @Test
    void testAssertNotNullNorEmpty_withNullString_throwsError()
    {
        final TestableSimpleAssert simpleAssert = new TestableSimpleAssert(failureHandler);

        assertThatThrownBy(() -> simpleAssert.callAssertNotNullNorEmpty("myProperty", null))
                .as("null string throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertNotNullNorEmpty_withEmptyString_throwsError()
    {
        final TestableSimpleAssert simpleAssert = new TestableSimpleAssert(failureHandler);

        assertThatThrownBy(() -> simpleAssert.callAssertNotNullNorEmpty("myProperty", ""))
                .as("empty string throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertNotNullNorEmpty_withBlankString_throwsError()
    {
        final TestableSimpleAssert simpleAssert = new TestableSimpleAssert(failureHandler);

        assertThatThrownBy(() -> simpleAssert.callAssertNotNullNorEmpty("myProperty", "   "))
                .as("blank string throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertTrue_withNullMessageAndFalseCondition_throwsError()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        // null message: the handler is invoked with null, should still throw
        assertThatThrownBy(() -> simpleAssert.assertTrue((String) null, false))
                .as("null message false condition throws.")
                .isInstanceOf(Error.class);
    }

    @Test
    void testAssertTrue_withNullMessageAndTrueCondition_doesNotThrow()
    {
        final SimpleAssert simpleAssert = new SimpleAssert(failureHandler);

        simpleAssert.assertTrue((String) null, true);
    }
}
