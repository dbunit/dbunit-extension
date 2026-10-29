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
package org.dbunit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.junit.jupiter.DbUnitExtension;
import org.dbunit.spring.DbUnitTestExecutionListenerTest.RecordingTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The tests of {@link DbUnitTestExecutionListener} for a test method with a parameter that is
 * annotated with {@code @ExtendWith}. They sit apart from {@link DbUnitTestExecutionListenerTest}
 * because {@code @ExtendWith} on a parameter needs JUnit Jupiter 5.8, newer than the oldest version
 * the listener supports, and the tests of the oldest versions leave this class out.
 */
class DbUnitTestExecutionListenerParameterExtendWithTest
{
    private final DbUnitTestExecutionListener listener = new DbUnitTestExecutionListener();

    @BeforeEach
    void resetRecordingTestCase()
    {
        RecordingTestCase.reset();
    }

    @Test
    void testBeforeTestMethod_testMethodParameterExtendsWithDbUnitExtension_leavesTheTestToDbUnitExtension()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                new FakeTestContext(RegistersDbUnitExtensionOnATestMethodParameter.class)
                        .beginMethod(new RegistersDbUnitExtensionOnATestMethodParameter(tester),
                                testMethod());

        listener.beforeTestMethod(context);

        assertThat(RecordingTestCase.testersAtPreTest)
                .as("JUnit registers an extension that @ExtendWith names on a parameter of the"
                        + " test method, so the listener leaves the test to it.")
                .isEmpty();
    }

    @Test
    void testBeforeTestMethod_olderSpringAndATestMethodParameterExtendingWithDbUnitExtension_doesNothing()
            throws Exception
    {
        final DbUnitTestExecutionListener onOlderSpring =
                new DbUnitTestExecutionListener(() -> false, () -> false);
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                new FakeTestContext(RegistersDbUnitExtensionOnATestMethodParameter.class)
                        .beginMethod(new RegistersDbUnitExtensionOnATestMethodParameter(tester),
                                testMethod());

        assertThatCode(() -> onOlderSpring.beforeTestMethod(context))
                .as("A test that JUnit Jupiter runs with an extension that @ExtendWith names on a"
                        + " parameter of the test method needs no listener, so an older Spring"
                        + " Framework does not fail it.")
                .doesNotThrowAnyException();
        assertThat(RecordingTestCase.testersAtPreTest)
                .as("The listener sets nothing up for it.")
                .isEmpty();
    }

    private static Method testMethod() throws NoSuchMethodException
    {
        return RegistersDbUnitExtensionOnATestMethodParameter.class.getDeclaredMethod(
                "aTestMethod", IDatabaseConnection.class);
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingTestCase.class)
    static class RegistersDbUnitExtensionOnATestMethodParameter
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        RegistersDbUnitExtensionOnATestMethodParameter(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        void aTestMethod(@ExtendWith(DbUnitExtension.class) final IDatabaseConnection connection)
        {
        }
    }
}
