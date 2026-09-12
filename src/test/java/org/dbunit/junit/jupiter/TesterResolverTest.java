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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;

/**
 * Unit tests of what {@link TesterResolver} adds to the field rules it shares with other test
 * bindings - those are covered by {@code TestInstanceTesterResolverTest}: the test instances JUnit
 * hands over, the order of the tiers, and its error message.
 */
class TesterResolverTest
{
    private final TesterResolver resolver = new TesterResolver();
    private final AnnotatedTestConfiguration noFactoryConfiguration = AnnotatedTestConfiguration
            .from(TesterResolverTest.class, null, null, null, null, null, null);

    @AfterEach
    void resetStaticFixtureState()
    {
        RecordingFactory.next = null;
    }

    @Test
    void testResolve_nestedInstancesWithUnmarkedTesters_innermostInstanceWins() throws Exception
    {
        final IDatabaseTester innerTester = mock(IDatabaseTester.class);
        final IDatabaseTester outerTester = mock(IDatabaseTester.class);
        // JUnit lists the instances outermost first: the enclosing instance, then the @Nested one.
        final ExtensionContext context = contextWithInstances(
                new HasUnmarkedTesterField(outerTester), new HasUnmarkedTesterField(innerTester));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("The innermost instance's tester must win, so JUnit's outermost-first list"
                        + " must be handed to the field rules innermost first.")
                .isSameAs(innerTester);
    }

    @Test
    void testResolve_factoryConfiguredAndUnmarkedTesterField_factoryWins() throws Exception
    {
        RecordingFactory.next = mock(IDatabaseTester.class);
        final ExtensionContext context = contextWithInstances(
                new HasUnmarkedTesterField(mock(IDatabaseTester.class)));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, configurationWithFactory());

        assertThat(resolution.getTester())
                .as("An explicit @DbUnitConfig(databaseTesterFactory) outranks the plain field"
                        + " auto-scan.")
                .isSameAs(RecordingFactory.next);
    }

    @Test
    void testResolve_noTesterAnywhere_throwsNamingTheTestClassAndTheWaysToDeclareOne()
    {
        final ExtensionContext context = contextWithInstances(new NoTesterAnywhere());

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("With no field and no factory, resolution must fail with a message saying"
                        + " where the tester was looked for and how to declare one.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found")
                .hasMessageContaining(NoTesterAnywhere.class.getName())
                .hasMessageContaining("@DbUnitTester")
                .hasMessageContaining("databaseTesterFactory");
    }

    private static ExtensionContext contextWithInstances(final Object... outermostFirst)
    {
        final Object innermost = outermostFirst[outermostFirst.length - 1];
        final ExtensionContext context = mock(ExtensionContext.class);
        doReturn(innermost.getClass()).when(context).getRequiredTestClass();
        final TestInstances instances = mock(TestInstances.class);
        when(instances.getAllInstances()).thenReturn(Arrays.asList(outermostFirst));
        when(context.getRequiredTestInstances()).thenReturn(instances);
        return context;
    }

    private AnnotatedTestConfiguration configurationWithFactory()
    {
        final DbUnitConfig config = WithFactory.class.getAnnotation(DbUnitConfig.class);
        return AnnotatedTestConfiguration.from(TesterResolverTest.class, config, null, null, null,
                null, null);
    }

    static class HasUnmarkedTesterField
    {
        IDatabaseTester tester;

        HasUnmarkedTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }
    }

    static class NoTesterAnywhere
    {
    }

    static class RecordingFactory implements DatabaseTesterFactory
    {
        static IDatabaseTester next;

        @Override
        public IDatabaseTester getDatabaseTester()
        {
            return next;
        }
    }

    @DbUnitConfig(databaseTesterFactory = RecordingFactory.class)
    static class WithFactory
    {
    }
}
