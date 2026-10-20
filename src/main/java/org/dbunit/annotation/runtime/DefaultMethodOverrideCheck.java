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
 * Checks whether a type provides its own body for a method a known base class declares
 * ({@link #declaresBelow}), for a call site that reacts when a subclass redeclares a hook
 * instead of silently doing nothing.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class DefaultMethodOverrideCheck
{
    /**
     * Returns whether {@code type}, or any class between it and {@code ceiling} (exclusive),
     * declares the named method - the "does a concrete class redeclare this method below a
     * known base class" question. Walks {@code type}'s superclass chain, stopping before
     * {@code ceiling} and before {@link Object}.
     *
     * <p>A {@link NoSuchMethodException} for one class in the range is not an error: that
     * class simply does not declare the method, and the walk continues to its superclass.
     *
     * @param type The concrete type to start the walk from.
     * @param ceiling The base class to stop before; a declaration on {@code ceiling} itself
     *            does not count.
     * @param methodName The method's name.
     * @param parameterTypes The method's parameter types.
     * @return True when some class in {@code [type, ceiling)} declares the method.
     */
    boolean declaresBelow(final Class<?> type, final Class<?> ceiling,
            final String methodName, final Class<?>... parameterTypes)
    {
        Class<?> current = type;
        while (current != null && current != ceiling && current != Object.class)
        {
            try
            {
                current.getDeclaredMethod(methodName, parameterTypes);
                return true;
            } catch (final NoSuchMethodException e)
            {
                current = current.getSuperclass();
            }
        }
        return false;
    }
}
