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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestInstanceTesterResolverTest
{
    private static final Callable<IDatabaseTester> NO_UNMARKED_TESTER = () ->
    {
        throw new IllegalStateException("No unmarked tester.");
    };

    private final AnnotatedTestConfiguration noFactoryConfiguration = AnnotatedTestConfiguration
            .from(TestInstanceTesterResolverTest.class, null, null, null, null, null, null);

    @AfterEach
    void resetStaticFixtureState()
    {
        RecordingFactory.next = null;
    }

    // ---- resolve(): marked fields ----

    @Test
    void testResolve_dbUnitTestCaseField_usesInjectedTestCase() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final PrepAndExpectedTestCase testCase = mock(PrepAndExpectedTestCase.class);
        when(testCase.getDatabaseTester()).thenReturn(tester);
        final HasTestCaseField instance = new HasTestCaseField(testCase);

        final TestInstanceTesterResolver.Resolution resolution =
                TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER);

        assertThat(resolution.getTester()).as("The test case's own tester must be used.")
                .isSameAs(tester);
        assertThat(resolution.getTestCase()).as("The injected test case must be returned.")
                .isSameAs(testCase);
    }

    @Test
    void testResolve_dbUnitTesterField_usesMarkedField() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(new HasTesterField(tester)), NO_UNMARKED_TESTER);

        assertThat(resolution.getTester()).as("The marked field's tester must be used.")
                .isSameAs(tester);
        assertThat(resolution.getTestCase()).as("No test case was injected.").isNull();
    }

    @Test
    void testResolve_noMarkedField_usesUnmarkedTesterSupplier() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(new NoFieldsInstance()), () -> tester);

        assertThat(resolution.getTester())
                .as("With no marked field, the binding's own tester source must be used.")
                .isSameAs(tester);
        assertThat(resolution.getTestCase()).as("No test case was injected.").isNull();
    }

    @Test
    void testResolve_markedTesterField_unmarkedTesterSupplierNotAsked() throws Exception
    {
        final AtomicInteger timesAsked = new AtomicInteger();
        final Callable<IDatabaseTester> countingSupplier = () ->
        {
            timesAsked.incrementAndGet();
            return mock(IDatabaseTester.class);
        };

        TestInstanceTesterResolver.resolve(
                instances(new HasTesterField(mock(IDatabaseTester.class))), countingSupplier);

        assertThat(timesAsked.get())
                .as("A marked @DbUnitTester field outranks the binding's own tester source.")
                .isZero();
    }

    @Test
    void testResolve_bothMarkersOnOneInstance_throwsIllegalStateException()
    {
        final HasBothMarkers instance = new HasBothMarkers(mock(PrepAndExpectedTestCase.class),
                mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER))
                        .as("Declaring both @DbUnitTestCase and @DbUnitTester must be rejected.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("DbUnitTestCase")
                        .hasMessageContaining("DbUnitTester");
    }

    @Test
    void testResolve_markedFieldOnSuperclassOnly_usesInheritedField() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final SubclassOfMarkedTesterSuperclass instance =
                new SubclassOfMarkedTesterSuperclass(tester);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(instance), NO_UNMARKED_TESTER);

        assertThat(resolution.getTester())
                .as("A marked field declared only on a superclass must be found through a"
                        + " subclass instance.")
                .isSameAs(tester);
    }

    @Test
    void testResolve_twoMarkedFieldsOnSameClass_throwsIllegalStateException()
    {
        final HasTwoMarkedTesterFields instance = new HasTwoMarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER))
                        .as("Two @DbUnitTester fields declared directly on the same class are"
                                + " ambiguous and must be rejected, not silently resolved to"
                                + " whichever reflection happens to return first.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Multiple @DbUnitTester fields found in")
                        .hasMessageContaining(HasTwoMarkedTesterFields.class.getName());
    }

    @Test
    void testResolve_markedFieldsOnSuperclassAndSubclass_throwsIllegalStateException()
    {
        final SubclassWithOwnMarkedTesterField instance = new SubclassWithOwnMarkedTesterField(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER))
                        .as("A @DbUnitTester field on a superclass and another declared directly"
                                + " on the subclass are ambiguous within the same instance's"
                                + " class hierarchy and must be rejected, not silently resolved"
                                + " to the subclass's.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Multiple @DbUnitTester fields found in")
                        .hasMessageContaining(SubclassWithOwnMarkedTesterField.class.getName());
    }

    @Test
    void testResolve_dbUnitTesterFieldIsNull_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> TestInstanceTesterResolver
                .resolve(instances(new HasNullTesterField()), NO_UNMARKED_TESTER))
                        .as("A null @DbUnitTester field must be rejected with a clear message,"
                                + " not a bare NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("IDatabaseTester field")
                        .hasMessageContaining("'tester'")
                        .hasMessageContaining(HasNullTesterField.class.getName());
    }

    @Test
    void testResolve_dbUnitTesterFieldWrongType_throwsIllegalStateException()
    {
        final HasWrongTypeTesterField instance = new HasWrongTypeTesterField("not a tester");

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER))
                        .as("A @DbUnitTester field whose value does not implement"
                                + " IDatabaseTester must be rejected with a clear message, not a"
                                + " raw ClassCastException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("IDatabaseTester")
                        .hasMessageContaining("'tester'")
                        .hasMessageContaining(HasWrongTypeTesterField.class.getName())
                        .hasMessageContaining(String.class.getName());
    }

    @Test
    void testResolve_dbUnitTestCaseFieldIsNull_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> TestInstanceTesterResolver
                .resolve(instances(new HasNullTestCaseField()), NO_UNMARKED_TESTER))
                        .as("A null @DbUnitTestCase field must be rejected with a clear message,"
                                + " not a bare NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PrepAndExpectedTestCase field")
                        .hasMessageContaining("'testCase'")
                        .hasMessageContaining(HasNullTestCaseField.class.getName());
    }

    @Test
    void testResolve_dbUnitTestCaseFieldWrongType_throwsIllegalStateException()
    {
        final HasWrongTypeTestCaseField instance = new HasWrongTypeTestCaseField("not a test case");

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.resolve(instances(instance), NO_UNMARKED_TESTER))
                        .as("A @DbUnitTestCase field whose value does not implement"
                                + " PrepAndExpectedTestCase must be rejected with a clear message,"
                                + " not a raw ClassCastException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PrepAndExpectedTestCase")
                        .hasMessageContaining("'testCase'")
                        .hasMessageContaining(HasWrongTypeTestCaseField.class.getName())
                        .hasMessageContaining(String.class.getName());
    }

    // ---- resolve(): several test instances ----

    @Test
    void testResolve_markedFieldOnlyOnOuterInstance_usesOuterField() throws Exception
    {
        final IDatabaseTester outerTester = mock(IDatabaseTester.class);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(new NoFieldsInstance(), new HasTesterField(outerTester)),
                        NO_UNMARKED_TESTER);

        assertThat(resolution.getTester())
                .as("An instance without a marked field must fall back to an enclosing"
                        + " instance's.")
                .isSameAs(outerTester);
    }

    @Test
    void testResolve_markedFieldOnInnerAndOuterInstance_innerShadowsOuter() throws Exception
    {
        final IDatabaseTester innerTester = mock(IDatabaseTester.class);
        final IDatabaseTester outerTester = mock(IDatabaseTester.class);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(new HasTesterField(innerTester),
                        new HasTesterField(outerTester)), NO_UNMARKED_TESTER);

        assertThat(resolution.getTester())
                .as("The innermost instance's marked field must shadow an enclosing instance's,"
                        + " not be rejected as ambiguous with it.")
                .isSameAs(innerTester);
    }

    @Test
    void testResolve_testerFieldOnInnerAndTestCaseFieldOnOuter_innerShadowsOuterAcrossMarkers()
            throws Exception
    {
        final IDatabaseTester innerTester = mock(IDatabaseTester.class);
        final PrepAndExpectedTestCase outerTestCase = mock(PrepAndExpectedTestCase.class);

        final TestInstanceTesterResolver.Resolution resolution = TestInstanceTesterResolver
                .resolve(instances(new HasTesterField(innerTester),
                        new HasTestCaseField(outerTestCase)), NO_UNMARKED_TESTER);

        assertThat(resolution.getTester()).as("The inner @DbUnitTester field must win.")
                .isSameAs(innerTester);
        assertThat(resolution.getTestCase())
                .as("The outer @DbUnitTestCase field must be shadowed by the inner instance's"
                        + " marked field even though it uses the other marker.")
                .isNull();
    }

    // ---- fromFactory() ----

    @Test
    void testFromFactory_noFactoryConfigured_returnsNull() throws Exception
    {
        assertThat(TestInstanceTesterResolver.fromFactory(noFactoryConfiguration))
                .as("Without @DbUnitConfig(databaseTesterFactory), there is no factory tester.")
                .isNull();
    }

    @Test
    void testFromFactory_factoryConfigured_returnsFactoryTester() throws Exception
    {
        RecordingFactory.next = mock(IDatabaseTester.class);

        assertThat(TestInstanceTesterResolver.fromFactory(configurationWithFactory()))
                .as("The factory-created tester must be returned.")
                .isSameAs(RecordingFactory.next);
    }

    @Test
    void testFromFactory_factoryReturnsNull_throwsIllegalStateException()
    {
        RecordingFactory.next = null;

        assertThatThrownBy(
                () -> TestInstanceTesterResolver.fromFactory(configurationWithFactory()))
                        .as("A factory returning null must be rejected with a clear message.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(RecordingFactory.class.getName())
                        .hasMessageContaining("getDatabaseTester");
    }

    // ---- fromUnmarkedField() ----

    @Test
    void testFromUnmarkedField_oneUnmarkedTesterField_returnsItsTester() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        assertThat(TestInstanceTesterResolver
                .fromUnmarkedField(instances(new HasUnmarkedTesterField(tester))))
                        .as("The unmarked field's tester must be returned.").isSameAs(tester);
    }

    @Test
    void testFromUnmarkedField_noTesterField_returnsNull() throws Exception
    {
        assertThat(TestInstanceTesterResolver
                .fromUnmarkedField(instances(new NoFieldsInstance())))
                        .as("With no tester field anywhere, the caller must be told so with null"
                                + " so it can try its own tiers.")
                        .isNull();
    }

    @Test
    void testFromUnmarkedField_twoUnmarkedTesterFieldsSameClass_throwsIllegalStateException()
    {
        final HasTwoUnmarkedTesterFields instance = new HasTwoUnmarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));

        assertThatThrownBy(() -> TestInstanceTesterResolver.fromUnmarkedField(instances(instance)))
                .as("Two unmarked IDatabaseTester fields declared directly on the same class are"
                        + " ambiguous and must be rejected, not silently resolved to whichever"
                        + " reflection happens to return first.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple IDatabaseTester fields")
                .hasMessageContaining(HasTwoUnmarkedTesterFields.class.getName());
    }

    @Test
    void testFromUnmarkedField_onlyStaticTesterField_returnsNull() throws Exception
    {
        HasStaticUnmarkedTesterField.tester = mock(IDatabaseTester.class);
        try
        {
            assertThat(TestInstanceTesterResolver
                    .fromUnmarkedField(instances(new HasStaticUnmarkedTesterField())))
                            .as("The plain field auto-scan (unlike the marked-field lookup) must"
                                    + " skip static fields, so a static-only IDatabaseTester"
                                    + " field is not found at all.")
                            .isNull();
        } finally
        {
            HasStaticUnmarkedTesterField.tester = null;
        }
    }

    @Test
    void testFromUnmarkedField_testerFieldIsNull_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> TestInstanceTesterResolver
                .fromUnmarkedField(instances(new HasUnmarkedTesterField(null))))
                        .as("A null, unmarked IDatabaseTester field must be rejected with a clear"
                                + " message, not a bare NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("IDatabaseTester field")
                        .hasMessageContaining("'tester'")
                        .hasMessageContaining(HasUnmarkedTesterField.class.getName());
    }

    @Test
    void testFromUnmarkedField_fieldsOnSuperclassAndSubclass_nearestDeclaringClassWins()
            throws Exception
    {
        final IDatabaseTester superclassTester = mock(IDatabaseTester.class);
        final IDatabaseTester subclassTester = mock(IDatabaseTester.class);

        assertThat(TestInstanceTesterResolver.fromUnmarkedField(instances(
                new SubclassWithOwnUnmarkedTesterField(superclassTester, subclassTester))))
                        .as("The 3.5.0 auto-scan lets the nearest declaring class win, unlike"
                                + " the marked-field lookup.")
                        .isSameAs(subclassTester);
    }

    @Test
    void testFromUnmarkedField_testerFieldOnlyOnOuterInstance_usesOuterField() throws Exception
    {
        final IDatabaseTester outerTester = mock(IDatabaseTester.class);

        assertThat(TestInstanceTesterResolver.fromUnmarkedField(
                instances(new NoFieldsInstance(), new HasUnmarkedTesterField(outerTester))))
                        .as("An instance without a tester field must fall back to an enclosing"
                                + " instance's.")
                        .isSameAs(outerTester);
    }

    private AnnotatedTestConfiguration configurationWithFactory()
    {
        final DbUnitConfig config = WithFactory.class.getAnnotation(DbUnitConfig.class);
        return AnnotatedTestConfiguration.from(TestInstanceTesterResolverTest.class, config,
                null, null, null, null, null);
    }

    private static List<Object> instances(final Object... innermostFirst)
    {
        return Collections.unmodifiableList(Arrays.asList(innermostFirst));
    }

    static class HasTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;

        HasTestCaseField(final PrepAndExpectedTestCase testCase)
        {
            this.testCase = testCase;
        }
    }

    static class HasTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;

        HasTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }
    }

    static class HasBothMarkers
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
        @DbUnitTester
        IDatabaseTester tester;

        HasBothMarkers(final PrepAndExpectedTestCase testCase, final IDatabaseTester tester)
        {
            this.testCase = testCase;
            this.tester = tester;
        }
    }

    static class HasUnmarkedTesterField
    {
        IDatabaseTester tester;

        HasUnmarkedTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }
    }

    static class MarkedTesterSuperclass
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class SubclassOfMarkedTesterSuperclass extends MarkedTesterSuperclass
    {
        SubclassOfMarkedTesterSuperclass(final IDatabaseTester tester)
        {
            this.tester = tester;
        }
    }

    static class NoFieldsInstance
    {
    }

    static class HasNullTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class HasWrongTypeTesterField
    {
        @DbUnitTester
        Object tester;

        HasWrongTypeTesterField(final Object tester)
        {
            this.tester = tester;
        }
    }

    static class HasNullTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
    }

    static class HasWrongTypeTestCaseField
    {
        @DbUnitTestCase
        Object testCase;

        HasWrongTypeTestCaseField(final Object testCase)
        {
            this.testCase = testCase;
        }
    }

    static class HasTwoMarkedTesterFields
    {
        @DbUnitTester
        IDatabaseTester tester;
        @DbUnitTester
        IDatabaseTester anotherTester;

        HasTwoMarkedTesterFields(final IDatabaseTester tester, final IDatabaseTester anotherTester)
        {
            this.tester = tester;
            this.anotherTester = anotherTester;
        }
    }

    static class MarkedTesterFieldSuperclass
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class SubclassWithOwnMarkedTesterField extends MarkedTesterFieldSuperclass
    {
        @DbUnitTester
        IDatabaseTester subclassTester;

        SubclassWithOwnMarkedTesterField(final IDatabaseTester superclassTester,
                final IDatabaseTester subclassTester)
        {
            this.tester = superclassTester;
            this.subclassTester = subclassTester;
        }
    }

    static class UnmarkedTesterFieldSuperclass
    {
        IDatabaseTester tester;
    }

    static class SubclassWithOwnUnmarkedTesterField extends UnmarkedTesterFieldSuperclass
    {
        IDatabaseTester subclassTester;

        SubclassWithOwnUnmarkedTesterField(final IDatabaseTester superclassTester,
                final IDatabaseTester subclassTester)
        {
            this.tester = superclassTester;
            this.subclassTester = subclassTester;
        }
    }

    static class HasTwoUnmarkedTesterFields
    {
        IDatabaseTester tester;
        IDatabaseTester anotherTester;

        HasTwoUnmarkedTesterFields(final IDatabaseTester tester,
                final IDatabaseTester anotherTester)
        {
            this.tester = tester;
            this.anotherTester = anotherTester;
        }
    }

    static class HasStaticUnmarkedTesterField
    {
        static IDatabaseTester tester;
    }

    static class RecordingFactory implements DatabaseTesterFactory
    {
        static IDatabaseTester next;

        @Override
        public IDatabaseTester getDatabaseTester()
        {
            return next;
        }
    }

    @DbUnitConfig(databaseTesterFactory = RecordingFactory.class)
    private static class WithFactory
    {
    }
}
