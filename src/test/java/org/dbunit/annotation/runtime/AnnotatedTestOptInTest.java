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
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AnnotatedTestOptInTest
{
    private static final AnnotationLookup FINDS_NOTHING = new AnnotationLookup()
    {
        @Override
        public <A extends Annotation> A find(final Class<A> annotationType)
        {
            return null;
        }
    };

    static Stream<Class<? extends Annotation>> configuringAnnotations()
    {
        return Stream.of(DbUnitConfig.class, DbUnitPrep.class, DbUnitSetup.class,
                DbUnitExpected.class, DbUnitTearDown.class, DbUnitRowCountCheck.class);
    }

    @ParameterizedTest
    @MethodSource("configuringAnnotations")
    void testIsOptedIn_lookupFindsConfiguringAnnotation_returnsTrue(
            final Class<? extends Annotation> annotationType)
    {
        final AnnotationLookup lookup = findingOnly(annotationType);

        assertThat(AnnotatedTestOptIn.isOptedIn(lookup, classes(NoMarkerFields.class)))
                .as("%s on the test opts it into the annotation family.",
                        annotationType.getSimpleName())
                .isTrue();
    }

    @Test
    void testIsOptedIn_nothingFoundAndNoMarkerField_returnsFalse()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING, classes(NoMarkerFields.class)))
                .as("A test with no configuring annotation and no marker field is not opted in.")
                .isFalse();
    }

    @Test
    void testIsOptedIn_unmarkedTesterFieldOnly_returnsFalse()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING, classes(UnmarkedTesterField.class)))
                .as("A plain, unmarked IDatabaseTester field is the 3.5.0 lifecycle-only style"
                        + " and is not an opt-in.")
                .isFalse();
    }

    @Test
    void testIsOptedIn_dbUnitTesterField_returnsTrue()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING, classes(MarkedTesterField.class)))
                .as("A @DbUnitTester field opts the test in.").isTrue();
    }

    @Test
    void testIsOptedIn_dbUnitTestCaseField_returnsTrue()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING, classes(MarkedTestCaseField.class)))
                .as("A @DbUnitTestCase field opts the test in.").isTrue();
    }

    @Test
    void testIsOptedIn_markedFieldOnSuperclass_returnsTrue()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING,
                classes(SubclassOfMarkedTesterField.class)))
                        .as("A marker field inherited from a superclass opts the test in.")
                        .isTrue();
    }

    @Test
    void testIsOptedIn_markedFieldOnlyOnEnclosingClass_returnsTrue()
    {
        assertThat(AnnotatedTestOptIn.isOptedIn(FINDS_NOTHING,
                classes(NoMarkerFields.class, MarkedTesterField.class)))
                        .as("A marker field on an enclosing class opts a @Nested test in.")
                        .isTrue();
    }

    private static AnnotationLookup findingOnly(final Class<? extends Annotation> wanted)
    {
        return new AnnotationLookup()
        {
            @Override
            public <A extends Annotation> A find(final Class<A> annotationType)
            {
                if (annotationType == wanted)
                {
                    return annotationType.cast(AllConfiguringAnnotations.class
                            .getAnnotation(annotationType));
                }
                return null;
            }
        };
    }

    private static Collection<Class<?>> classes(final Class<?>... testClasses)
    {
        return Collections.unmodifiableList(Arrays.<Class<?>> asList(testClasses));
    }

    @DbUnitConfig
    @DbUnitPrep("prep.xml")
    @DbUnitSetup
    @DbUnitExpected("expected.xml")
    @DbUnitTearDown
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
