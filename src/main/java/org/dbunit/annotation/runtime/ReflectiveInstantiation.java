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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Reflectively instantiates, via its no-arg constructor, a class an {@code org.dbunit.annotation}
 * attribute names by {@link Class} - shared by every such attribute
 * ({@code DbUnitConfig.dataFileLoader()}, {@code failureHandler()}, a
 * {@code DatabaseTesterFactory}, and so on) so a missing or throwing constructor is reported
 * consistently everywhere.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class ReflectiveInstantiation
{
    /**
     * Instantiates {@code implementationClass} via its no-arg constructor, wrapping any failure
     * in an {@link IllegalStateException} that names {@code attributeDescription}.
     *
     * @param <T> The type the instance is used as.
     * @param implementationClass The class to instantiate.
     * @param attributeDescription The annotation attribute that named this class, used only for
     *            the exception message.
     * @return The new instance.
     * @throws IllegalStateException If the no-arg constructor is missing, inaccessible, or
     *             itself throws.
     */
    <T> T instantiate(final Class<? extends T> implementationClass,
            final String attributeDescription)
    {
        try
        {
            final Constructor<? extends T> constructor =
                    implementationClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (final InvocationTargetException e)
        {
            throw new IllegalStateException(attributeDescription + " class "
                    + implementationClass.getName()
                    + " threw from its no-arg constructor.", e.getCause());
        } catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(attributeDescription + " class "
                    + implementationClass.getName()
                    + " has no accessible no-arg constructor.", e);
        }
    }
}
