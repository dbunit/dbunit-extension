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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * The {@code @DbUnitConfig} that applies to one test: every declaration in scope, nearest first,
 * merged attribute by attribute. For each attribute the nearest declaration that sets it wins,
 * so a subclass declaring only {@code failureHandler} keeps the {@code dataFileLoader} and the
 * {@code properties} its base class declares. An attribute is set when it differs from its
 * default, which is "not set" for every attribute; an array attribute such as
 * {@code properties} is taken whole from the nearest declaration that sets it, never combined
 * with a farther one's, so a nearer declaration names a value to override an inherited one and
 * cannot clear it.
 *
 * <p>Not intended for direct use by test code; machinery consumed by
 * {@link AnnotatedTestConfiguration}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class MergedDbUnitConfig
{
    private final List<DbUnitConfig> nearestFirst;

    private MergedDbUnitConfig(final List<DbUnitConfig> nearestFirst)
    {
        this.nearestFirst = nearestFirst;
    }

    /**
     * Merges the declarations that apply to a test.
     *
     * @param nearestFirst The {@code @DbUnitConfig} declarations in scope, nearest first; empty
     *            when the test has none.
     * @return The merged configuration.
     */
    static MergedDbUnitConfig of(final List<DbUnitConfig> nearestFirst)
    {
        return new MergedDbUnitConfig(
                Collections.unmodifiableList(new ArrayList<>(nearestFirst)));
    }

    /**
     * Returns the merged configuration of a test with one declaration, or none.
     *
     * @param declaration The one declaration, or {@code null} when the test has none.
     * @return The merged configuration.
     */
    static MergedDbUnitConfig ofOne(final DbUnitConfig declaration)
    {
        if (declaration == null)
        {
            return of(Collections.<DbUnitConfig>emptyList());
        }
        return of(Collections.singletonList(declaration));
    }

    /**
     * Returns the class path directory prefix of the nearest declaration that sets one.
     *
     * @return The base directory; empty when no declaration sets one.
     */
    String getDataSetBaseDir()
    {
        return nearestSet(DbUnitConfig::dataSetBaseDir, baseDir -> !baseDir.isEmpty(), "");
    }

    /**
     * Returns the data file loader class of the nearest declaration that sets one.
     *
     * @return The loader class; {@code null} when no declaration sets one.
     */
    Class<? extends DataFileLoader> getDataFileLoader()
    {
        return nearestSet(DbUnitConfig::dataFileLoader, loader -> loader != DataFileLoader.class,
                null);
    }

    /**
     * Returns the tester factory class of the nearest declaration that sets one.
     *
     * @return The factory class; {@code null} when no declaration sets one.
     */
    Class<? extends DatabaseTesterFactory> getDatabaseTesterFactory()
    {
        return nearestSet(DbUnitConfig::databaseTesterFactory,
                factory -> factory != DatabaseTesterFactory.class, null);
    }

    /**
     * Returns the test case class of the nearest declaration that sets one.
     *
     * @return The test case class; {@link DefaultPrepAndExpectedTestCase} when no declaration
     *         sets one.
     */
    Class<? extends DefaultPrepAndExpectedTestCase> getPrepAndExpectedTestCase()
    {
        return nearestSet(DbUnitConfig::prepAndExpectedTestCase,
                testCase -> testCase != DefaultPrepAndExpectedTestCase.class,
                DefaultPrepAndExpectedTestCase.class);
    }

    /**
     * Returns the failure handler class of the nearest declaration that sets one.
     *
     * @return The failure handler class; {@code null} when no declaration sets one.
     */
    Class<? extends FailureHandler> getFailureHandler()
    {
        return nearestSet(DbUnitConfig::failureHandler,
                handler -> handler != FailureHandler.class, null);
    }

    /**
     * Returns the {@code DatabaseConfig} properties of the nearest declaration that sets any.
     *
     * @return The properties; empty when no declaration sets any.
     */
    DbUnitProperty[] getProperties()
    {
        return nearestSet(DbUnitConfig::properties, properties -> properties.length > 0,
                new DbUnitProperty[0]).clone();
    }

    /**
     * Returns the catalog classes of the nearest declaration that sets any.
     *
     * @return The catalog classes; empty when no declaration sets any.
     */
    Class<?>[] getVerifyDefinitions()
    {
        return nearestSet(DbUnitConfig::verifyDefinitions, catalogs -> catalogs.length > 0,
                new Class<?>[0]).clone();
    }

    private <T> T nearestSet(final Function<DbUnitConfig, T> attribute,
            final Predicate<T> isSet, final T whenNoneSets)
    {
        for (final DbUnitConfig declaration : nearestFirst)
        {
            final T value = attribute.apply(declaration);
            if (isSet.test(value))
            {
                return value;
            }
        }
        return whenNoneSets;
    }
}
