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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * TestNG is not a dependency of dbUnit, so {@link ExpectsExceptions} stands in for TestNG's
 * {@code @Test}: it has the same {@code expectedExceptions} attribute, and the check under test
 * finds an annotation by the class name it is given.
 */
class DeclaredExpectedExceptionsTest
{
    private final DeclaredExpectedExceptions declaration = standIn();

    /**
     * Creates the check for the stand-in annotation.
     *
     * @return The check.
     */
    static DeclaredExpectedExceptions standIn()
    {
        return new DeclaredExpectedExceptions(ExpectsExceptions.class.getName(),
                "expectedExceptions");
    }

    @Test
    void testExpects_thrownTypeIsDeclared_returnsTrue() throws Exception
    {
        assertThat(declaration.expects(method("declaresIoException"), new IOException()))
                .as("An exception of a declared type is the expected outcome.").isTrue();
    }

    @Test
    void testExpects_thrownTypeIsSubclassOfDeclared_returnsTrue() throws Exception
    {
        assertThat(declaration.expects(method("declaresIoException"), new FileNotFoundException()))
                .as("A subclass of a declared type is the expected outcome too, as in TestNG.")
                .isTrue();
    }

    @Test
    void testExpects_thrownTypeIsOneOfSeveralDeclared_returnsTrue() throws Exception
    {
        assertThat(declaration.expects(method("declaresSeveral"),
                new IllegalStateException()))
                        .as("Any one of the declared types is the expected outcome.").isTrue();
    }

    @Test
    void testExpects_thrownTypeIsNotDeclared_returnsFalse() throws Exception
    {
        assertThat(declaration.expects(method("declaresIoException"),
                new IllegalStateException()))
                        .as("An exception of another type is not the expected outcome.")
                        .isFalse();
    }

    @Test
    void testExpects_declarationListsNothing_returnsFalse() throws Exception
    {
        assertThat(declaration.expects(method("declaresNothing"), new IOException()))
                .as("The annotation without expected exceptions expects none.").isFalse();
    }

    @Test
    void testExpects_methodHasNoSuchAnnotation_returnsFalse() throws Exception
    {
        assertThat(declaration.expects(method("hasNoAnnotation"), new IOException()))
                .as("A method without the framework's annotation expects nothing.").isFalse();
    }

    @Test
    void testExpects_annotationLacksTheAttribute_returnsFalse() throws Exception
    {
        final DeclaredExpectedExceptions otherAttribute = new DeclaredExpectedExceptions(
                ExpectsExceptions.class.getName(), "noSuchAttribute");

        assertThat(otherAttribute.expects(method("declaresIoException"), new IOException()))
                .as("An annotation without the named attribute must not fail the check.")
                .isFalse();
    }

    @Test
    void testExpects_annotationClassNotOnClasspath_returnsFalse() throws Exception
    {
        assertThat(DeclaredExpectedExceptions.testNg().expects(method("declaresIoException"),
                new IOException()))
                        .as("Without TestNG on the classpath, TestNG's annotation is simply"
                                + " absent from the method, not an error.")
                        .isFalse();
    }

    private static Method method(final String name) throws Exception
    {
        return Samples.class.getDeclaredMethod(name);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExpectsExceptions
    {
        Class<? extends Throwable>[] expectedExceptions() default {};
    }

    static class Samples
    {
        @ExpectsExceptions(expectedExceptions = IOException.class)
        void declaresIoException()
        {
        }

        @ExpectsExceptions(expectedExceptions = {IOException.class, IllegalStateException.class})
        void declaresSeveral()
        {
        }

        @ExpectsExceptions
        void declaresNothing()
        {
        }

        void hasNoAnnotation()
        {
        }
    }
}
