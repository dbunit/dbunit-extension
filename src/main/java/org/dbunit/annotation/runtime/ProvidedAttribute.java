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
 * The shared rules, and the one message shape, for two checks on an {@code org.dbunit.annotation}
 * attribute: setting two mutually exclusive attributes - {@code @DbUnitExpected}
 * {@code verify()}/{@code verifyDefinitions()}/{@code verifyTables()} - is an error, and a
 * factory class returning {@code null} - {@code @DbUnitConfig} {@code databaseTesterFactory()} -
 * is an error. Routing every site's diagnostic through here keeps a user who trips two of them
 * across different attributes from getting two differently shaped messages for the same class of
 * mistake.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension}, for {@code databaseTesterFactory()} - can reach it too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class ProvidedAttribute
{
    private ProvidedAttribute()
    {
    }

    /**
     * Throws an {@link IllegalStateException} when two mutually exclusive ways of setting the
     * same attribute were both used.
     *
     * @param bothSet Whether both were given a non-default value.
     * @param owner The annotation carrying both attributes, e.g. {@code "@DbUnitExpected"}.
     * @param attributeA One attribute, e.g. {@code "verify()"}.
     * @param attributeB The other, e.g. {@code "verifyDefinitions()"}.
     * @param note An extra sentence appended after {@code "set only one."}, or {@code null} for
     *            none.
     */
    public static void rejectBothSet(final boolean bothSet, final String owner,
            final String attributeA, final String attributeB, final String note)
    {
        if (!bothSet)
        {
            return;
        }
        final String tail = note == null ? "" : " " + note;
        throw new IllegalStateException(owner + " sets both " + attributeA + " and "
                + attributeB + "; set only one." + tail);
    }

    /**
     * Returns {@code provided} unless the provider returned {@code null}, in which case it
     * throws an {@link IllegalStateException} naming the provider, its class, the attribute
     * that named it, and the method called.
     *
     * @param <T> The provider's value type.
     * @param provided The value the provider returned.
     * @param providerInterface The provider interface's simple name, e.g.
     *            {@code "DatabaseTesterFactory"}.
     * @param providerClass The provider implementation class.
     * @param namedBy The attribute that named the provider, e.g.
     *            {@code "DbUnitConfig.databaseTesterFactory"}.
     * @param getter The provider method called, without parentheses, e.g.
     *            {@code "getDatabaseTester"}.
     * @return {@code provided}, when non-{@code null}.
     */
    public static <T> T requireProvided(final T provided, final String providerInterface,
            final Class<?> providerClass, final String namedBy, final String getter)
    {
        if (provided == null)
        {
            throw new IllegalStateException(describe(providerInterface, providerClass, namedBy)
                    + " returned null from " + getter + "().");
        }
        return provided;
    }

    private static String describe(final String providerInterface, final Class<?> providerClass,
            final String namedBy)
    {
        return providerInterface + " " + providerClass.getName() + ", named by " + namedBy + ",";
    }
}
