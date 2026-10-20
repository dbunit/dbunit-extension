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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AnnotatedTestOptInTest
{
    private final AnnotatedTestOptIn optIn = new AnnotatedTestOptIn(new TesterFields());

    static Stream<Class<? extends Annotation>> configuringAnnotations()
    {
        return Stream.of(DbUnitConfig.class, DbUnitPrep.class, DbUnitSetUpOperation.class,
                DbUnitExpected.class, DbUnitTearDownOperation.class, DbUnitRowCountCheck.class);
    }

    @ParameterizedTest
    @MethodSource("configuringAnnotations")
    void testIsOptedIn_lookupFindsConfiguringAnnotation_returnsTrue(
            final Class<? extends Annotation> annotationType)
    {
        final AnnotationLookup lookup = findingOnly(annotationType);

        assertThat(optIn.isOptedIn(lookup, classes(NoMarkerFields.class)))
                .as("%s on the test opts it into the annotation family.",
                        annotationType.getSimpleName())
                .isTrue();
    }

    @Test
    void testIsOptedIn_nothingFoundAndNoMarkerField_returnsFalse()
    {
        assertThat(optIn.isOptedIn(findsNothing(), classes(NoMarkerFields.class)))
                .as("A test with no configuring annotation and no marker field is not opted in.")
                .isFalse();
    }

    @Test
    void testIsOptedIn_unmarkedTesterFieldOnly_returnsFalse()
    {
        assertThat(optIn.isOptedIn(findsNothing(), classes(UnmarkedTesterField.class)))
                .as("A plain, unmarked IDatabaseTester field is the 3.5.0 lifecycle-only style"
                        + " and is not an opt-in.")
                .isFalse();
    }

    @Test
    void testIsOptedIn_dbUnitTesterField_returnsTrue()
    {
        assertThat(optIn.isOptedIn(findsNothing(), classes(MarkedTesterField.class)))
                .as("A @DbUnitTester field opts the test in.").isTrue();
    }

    @Test
    void testIsOptedIn_dbUnitTestCaseField_returnsTrue()
    {
        assertThat(optIn.isOptedIn(findsNothing(), classes(MarkedTestCaseField.class)))
                .as("A @DbUnitTestCase field opts the test in.").isTrue();
    }

    @Test
    void testIsOptedIn_markedFieldOnSuperclass_returnsTrue()
    {
        assertThat(optIn.isOptedIn(findsNothing(),
                classes(SubclassOfMarkedTesterField.class)))
                        .as("A marker field inherited from a superclass opts the test in.")
                        .isTrue();
    }

    @Test
    void testIsOptedIn_markedFieldOnlyOnEnclosingClass_returnsTrue()
    {
        assertThat(optIn.isOptedIn(findsNothing(),
                classes(NoMarkerFields.class, MarkedTesterField.class)))
                        .as("A marker field on an enclosing class opts a @Nested test in.")
                        .isTrue();
    }

    private static AnnotationLookup findsNothing()
    {
        return new FakeAnnotationLookup(NoMarkerFields.class);
    }

    private static AnnotationLookup findingOnly(final Class<? extends Annotation> wanted)
    {
        final Annotation annotation = AllConfiguringAnnotations.class.getAnnotation(wanted);
        return new FakeAnnotationLookup(NoMarkerFields.class).finding(annotation);
    }

    private static Collection<Class<?>> classes(final Class<?>... testClasses)
    {
        return Collections.unmodifiableList(Arrays.<Class<?>> asList(testClasses));
    }

    @DbUnitConfig
    @DbUnitPrep("prep.xml")
    @DbUnitSetUpOperation
    @DbUnitExpected("expected.xml")
    @DbUnitTearDownOperation
    @DbUnitRowCountCheck
    static class AllConfiguringAnnotations
    {
    }

    static class NoMarkerFields
    {
    }

    static class UnmarkedTesterField
    {
        IDatabaseTester tester;
    }

    static class MarkedTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class MarkedTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
    }

    static class SubclassOfMarkedTesterField extends MarkedTesterField
    {
    }
}
