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

/**
 * Checks whether a type overrides a named default method, rather than inheriting its
 * declaring interface's own default body - shared by every {@code org.dbunit.annotation}/
 * {@code org.dbunit.junit.jupiter} call site that needs to fail fast (or otherwise react)
 * when a configured attribute cannot actually reach a non-overriding implementation, instead
 * of silently doing nothing.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} - can reach it too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class DefaultMethodOverrideCheck
{
    private DefaultMethodOverrideCheck()
    {
    }

    /**
     * Returns whether {@code implementationType} overrides the named default method
     * {@code declaringInterface} declares, rather than inheriting that interface's own
     * default body.
     *
     * <p>Known limitation: this check only asks which class declares the method, so it
     * cannot distinguish a real override from a dynamic proxy or a mocking-framework-generated
     * subclass - an unstubbed Mockito mock, for instance - that mechanically redeclares every
     * interface method, default ones included, no matter what its generated body actually
     * does. Such an instance reports {@code true} here for every method, silently defeating a
     * fail-fast check built on it. Narrow-audience: only reachable when a test injects such an
     * instance directly, rather than a real implementation.
     *
     * @param implementationType The runtime type to check.
     * @param declaringInterface The interface whose default method {@code methodName} names.
     * @param methodName The method's name.
     * @param parameterTypes The method's parameter types.
     * @return True when {@code implementationType} overrides the method.
     */
    public static boolean overridesDefaultMethod(final Class<?> implementationType,
            final Class<?> declaringInterface, final String methodName,
            final Class<?>... parameterTypes)
    {
        try
        {
            return implementationType.getMethod(methodName, parameterTypes)
                    .getDeclaringClass() != declaringInterface;
        } catch (final NoSuchMethodException e)
        {
            throw new IllegalStateException(declaringInterface.getName()
                    + " guarantees a public " + methodName
                    + " method; this should be unreachable.", e);
        }
    }
}
