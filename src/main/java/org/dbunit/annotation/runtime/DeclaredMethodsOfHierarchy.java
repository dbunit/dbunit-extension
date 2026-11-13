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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists the methods that each class and interface of a class hierarchy declares, an overridden
 * method of a superclass or an interface included.
 *
 * <p>A binding uses it to find the lifecycle methods of a test class for
 * {@link LifecycleMethodAnnotationCheck}. A binding that dropped the overridden methods instead,
 * or that searched for an annotation on the methods a method overrides, would not agree with
 * another binding on which method carries a misplaced annotation, and would name the wrong one.
 *
 * <p>This type is internal to dbUnit's bindings; it is public only for use across
 * dbUnit's packages and may change in a minor release.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class DeclaredMethodsOfHierarchy
{
    private DeclaredMethodsOfHierarchy()
    {
    }

    /**
     * Lists the methods that a class, each of its superclasses and each interface any of them
     * implements, directly or through another interface, declare. The list holds a method that a
     * subclass overrides once for each class or interface that declares it, and leaves out the
     * synthetic methods, such as the bridge methods a compiler adds.
     *
     * @param type The class to start from.
     * @return The declared methods, those of {@code type} first and then those of its
     *         interfaces and its superclass, and so on up the hierarchy.
     */
    public static List<Method> of(final Class<?> type)
    {
        final Set<Class<?>> typesToSearch = new LinkedHashSet<>();
        Class<?> superclass = type;
        while (superclass != null)
        {
            addWithInterfaces(typesToSearch, superclass);
            superclass = superclass.getSuperclass();
        }

        final List<Method> declaredMethods = new ArrayList<>();
        for (final Class<?> typeToSearch : typesToSearch)
        {
            for (final Method method : typeToSearch.getDeclaredMethods())
            {
                if (!method.isSynthetic())
                {
                    declaredMethods.add(method);
                }
            }
        }
        return declaredMethods;
    }

    private static void addWithInterfaces(final Set<Class<?>> typesToSearch, final Class<?> type)
    {
        final boolean added = typesToSearch.add(type);
        if (added)
        {
            for (final Class<?> implemented : type.getInterfaces())
            {
                addWithInterfaces(typesToSearch, implemented);
            }
        }
    }
}
