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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * Whether a test method declares, through its test framework's own annotation, that it is
 * expected to throw the exception it threw - in which case the test passed, and
 * {@code DbUnitTestExecutionListener} must verify it like any other passing test instead of
 * skipping verification as it does after a failure.
 *
 * <p>Spring hands the exception to the listener as the test's exception whether or not the test
 * framework counts it as the expected, passing outcome. JUnit 4's {@code @Test(expected = ...)}
 * needs no help: its runner consumes the expected exception before Spring reports the outcome.
 * TestNG's {@code @Test(expectedExceptions = ...)} does: Spring reports the thrown exception
 * and TestNG turns it into a pass only afterwards. The annotation is found by its class name, and
 * its attribute by name, so TestNG need not be on the classpath.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class DeclaredExpectedExceptions
{
    private static final String TESTNG_TEST_ANNOTATION = "org.testng.annotations.Test";
    private static final String TESTNG_EXPECTED_EXCEPTIONS_ATTRIBUTE = "expectedExceptions";

    private final String annotationName;
    private final String attributeName;

    /**
     * Creates the check for an annotation named {@code annotationName} whose
     * {@code attributeName} attribute lists the expected exception classes.
     *
     * @param annotationName The fully qualified class name of the annotation.
     * @param attributeName The name of its attribute holding an array of exception classes.
     */
    DeclaredExpectedExceptions(final String annotationName, final String attributeName)
    {
        this.annotationName = annotationName;
        this.attributeName = attributeName;
    }

    /**
     * Creates the check for TestNG's {@code @Test(expectedExceptions = ...)}.
     *
     * @return The check.
     */
    static DeclaredExpectedExceptions testNg()
    {
        return new DeclaredExpectedExceptions(TESTNG_TEST_ANNOTATION,
                TESTNG_EXPECTED_EXCEPTIONS_ATTRIBUTE);
    }

    /**
     * Returns whether {@code testMethod} declares {@code thrown}, or a superclass of it, among
     * its expected exceptions. The method is searched on its own, meta-annotations included.
     *
     * @param testMethod The test method that threw.
     * @param thrown The exception it threw.
     * @return {@code true} when the method's declaration makes {@code thrown} the expected
     *         outcome; {@code false} when it declares nothing, or a different exception.
     */
    boolean expects(final Method testMethod, final Throwable thrown)
    {
        final MergedAnnotations annotations =
                MergedAnnotations.from(testMethod, SearchStrategy.DIRECT);
        final MergedAnnotation<Annotation> declaration = annotations.get(annotationName);
        if (!declaration.isPresent())
        {
            return false;
        }
        if (!declaration.getValue(attributeName).isPresent())
        {
            return false;
        }
        for (final Class<?> expectedClass : declaration.getClassArray(attributeName))
        {
            if (expectedClass.isInstance(thrown))
            {
                return true;
            }
        }
        return false;
    }
}
