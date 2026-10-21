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
import java.lang.reflect.Modifier;

/**
 * Reflectively instantiates, via its no-arg constructor, a class an {@code org.dbunit.annotation}
 * attribute names by {@link Class} - shared by every such attribute
 * ({@code DbUnitConfig.dataFileLoader()}, {@code failureHandler()}, a
 * {@code DatabaseTesterFactory}, and so on) so an interface, an abstract class, and a missing or
 * throwing constructor are reported consistently everywhere.
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
     * @throws IllegalStateException If the class is an interface or abstract, or its no-arg
     *             constructor is missing, inaccessible, or itself throws.
     */
    <T> T instantiate(final Class<? extends T> implementationClass,
            final String attributeDescription)
    {
        return instantiate(implementationClass, attributeDescription, "");
    }

    /**
     * Instantiates {@code implementationClass} as
     * {@link #instantiate(Class, String)} does, appending {@code missingConstructorHint} to the
     * message of the failure that says the no-arg constructor is missing or inaccessible, for an
     * attribute whose users need to be told what to do about it. The message of a constructor
     * that itself throws does not get it, nor does the message about an interface or an abstract
     * class, which has no instance to create whatever its constructors.
     *
     * @param <T> The type the instance is used as.
     * @param implementationClass The class to instantiate.
     * @param attributeDescription The annotation attribute that named this class, used only for
     *            the exception message.
     * @param missingConstructorHint The sentence that says what to do about a class without an
     *            accessible no-arg constructor; empty for none.
     * @return The new instance.
     * @throws IllegalStateException If the class is an interface or abstract, or its no-arg
     *             constructor is missing, inaccessible, or itself throws.
     */
    <T> T instantiate(final Class<? extends T> implementationClass,
            final String attributeDescription, final String missingConstructorHint)
    {
        requireInstantiable(implementationClass, attributeDescription);
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
            final String message = attributeDescription + " class "
                    + implementationClass.getName() + " has no accessible no-arg constructor.";
            if (missingConstructorHint.isEmpty())
            {
                throw new IllegalStateException(message, e);
            }
            throw new IllegalStateException(message + " " + missingConstructorHint, e);
        }
    }

    /**
     * Rejects an interface or an abstract class before this class looks up its constructor, so
     * the message says what the class is instead of blaming a constructor. An interface counts
     * as abstract too, so this checks for one first.
     */
    private static void requireInstantiable(final Class<?> implementationClass,
            final String attributeDescription)
    {
        final String named = attributeDescription + " class " + implementationClass.getName();
        if (implementationClass.isInterface())
        {
            throw new IllegalStateException(named + " is an interface. Name a class that"
                    + " implements it and has a no-arg constructor.");
        }
        if (Modifier.isAbstract(implementationClass.getModifiers()))
        {
            throw new IllegalStateException(named + " is abstract. Name a concrete subclass"
                    + " that has a no-arg constructor.");
        }
    }
}
