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
package org.dbunit.junit.jupiter;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Builds {@link ExtensionContext} stubs whose parent chain has the shape the JUnit Jupiter engine
 * gives a test in a {@code @Nested} class: the context of the test method, the context of its
 * class, the contexts of each enclosing class out to the outermost, and the engine's root context,
 * which has no test class.
 *
 * <p>The stubs fail {@link ExtensionContext#getEnclosingTestClasses()}, which the JUnit Jupiter
 * 5.7 floor lacks, the way a runtime without it does. A test that uses them therefore
 * proves that the code under test finds the enclosing classes without that method.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class NestedContextChain
{
    private static final String ENCLOSING_TEST_CLASSES_MISSING =
            "ExtensionContext.getEnclosingTestClasses() is not available in the JUnit Jupiter"
                    + " 5.7 floor.";

    private NestedContextChain()
    {
    }

    /**
     * Creates the context of a test method of {@code testClass}.
     *
     * @param testClass The class declaring or inheriting the test method.
     * @param enclosingClassesOutermostFirst The classes enclosing {@code testClass}, outermost
     *            first; none for a class that is not {@code @Nested}.
     * @return The method context stub, whose ancestors are the class contexts and the root.
     */
    static ExtensionContext methodContextOf(final Class<?> testClass,
            final Class<?>... enclosingClassesOutermostFirst)
    {
        ExtensionContext parent = rootContext();
        for (final Class<?> enclosing : enclosingClassesOutermostFirst)
        {
            parent = contextOf(enclosing, parent);
        }
        final ExtensionContext classContext = contextOf(testClass, parent);
        return contextOf(testClass, classContext);
    }

    /**
     * Creates the engine's root context.
     *
     * @return A context with no test class and no parent.
     */
    static ExtensionContext rootContext()
    {
        final ExtensionContext root = mock(ExtensionContext.class);
        lenient().when(root.getEnclosingTestClasses())
                .thenThrow(new NoSuchMethodError(ENCLOSING_TEST_CLASSES_MISSING));
        return root;
    }

    /**
     * Creates the context of one test class or of one of its test methods.
     *
     * @param testClass The context's test class.
     * @param parent The context's parent.
     * @return The context stub.
     */
    static ExtensionContext contextOf(final Class<?> testClass, final ExtensionContext parent)
    {
        final ExtensionContext context = mock(ExtensionContext.class);
        lenient().doReturn(testClass).when(context).getRequiredTestClass();
        lenient().doReturn(Optional.of(testClass)).when(context).getTestClass();
        lenient().doReturn(Optional.of(parent)).when(context).getParent();
        lenient().when(context.getEnclosingTestClasses())
                .thenThrow(new NoSuchMethodError(ENCLOSING_TEST_CLASSES_MISSING));
        return context;
    }
}
