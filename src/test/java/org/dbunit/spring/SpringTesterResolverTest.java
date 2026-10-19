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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

/**
 * Unit tests of what {@link SpringTesterResolver} adds to the field rules it shares with the
 * JUnit 5 binding - those are covered by {@code TestInstanceTesterResolverTest}: the order of the
 * tiers, and the {@code ApplicationContext} bean tier.
 */
class SpringTesterResolverTest
{
    private final SpringTesterResolver resolver = new SpringTesterResolver();
    private final AnnotatedTestConfiguration noFactoryConfiguration = AnnotatedTestConfiguration
            .from(SpringTesterResolverTest.class, null, null, null, null, null, null);

    @AfterEach
    void resetStaticFixtureState()
    {
        RecordingFactory.next = null;
    }

    @Test
    void testResolve_factoryAndApplicationContextBean_factoryWins() throws Exception
    {
        RecordingFactory.next = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(new NoFieldsTestInstance(),
                applicationContextWithTester(mock(IDatabaseTester.class)));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, configurationWithFactory());

        assertThat(resolution.getTester())
                .as("An explicit @DbUnitConfig(databaseTesterFactory) outranks the"
                        + " ApplicationContext bean.")
                .isSameAs(RecordingFactory.next);
    }

    @Test
    void testResolve_applicationContextHasUniqueBean_usesBean() throws Exception
    {
        final NoFieldsTestInstance testInstance = new NoFieldsTestInstance();
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                fakeTestContext(testInstance, applicationContextWithTester(beanTester));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("The sole IDatabaseTester bean in the ApplicationContext must be used when"
                        + " no field or factory resolves one.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_dbUnitTestCaseWithoutTesterAndApplicationContextBean_failsSayingToGiveItATester()
            throws Exception
    {
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);
        final FakeTestContext context = fakeTestContext(new HasTestCaseField(testCase),
                applicationContextWithTester(beanTester));

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("A @DbUnitTestCase field's test case is used as it was built, so one with no"
                        + " tester is not given the ApplicationContext bean.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no IDatabaseTester")
                .hasMessageContaining("give it a tester where it is built");
        verify(testCase, never()).setDatabaseTester(any());
    }

    @Test
    void testResolve_unmarkedTesterFieldAndUniqueBean_fieldWins() throws Exception
    {
        final IDatabaseTester fieldTester = mock(IDatabaseTester.class);
        final HasUnmarkedTesterField testInstance = new HasUnmarkedTesterField(fieldTester);
        final FakeTestContext context = fakeTestContext(testInstance,
                applicationContextWithTester(mock(IDatabaseTester.class)));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A tester field the test class declares itself must outrank an"
                        + " ApplicationContext bean, as under the JUnit 5 binding, which has no"
                        + " bean to prefer.")
                .isSameAs(fieldTester);
    }

    @Test
    void testResolve_markedTesterFieldAndUniqueBean_fieldWins() throws Exception
    {
        final IDatabaseTester fieldTester = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(new HasTesterField(fieldTester),
                applicationContextWithTester(mock(IDatabaseTester.class)));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A @DbUnitTester field must outrank an ApplicationContext bean.")
                .isSameAs(fieldTester);
    }

    @Test
    void testResolve_noFieldAndNoUniqueBean_throwsIllegalStateException() throws Exception
    {
        final FakeTestContext context = fakeTestContext(new NoFieldsTestInstance(),
                applicationContextWithTester(null));

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("An ApplicationContext with no unique IDatabaseTester bean resolves nothing.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field or bean found")
                .hasMessageNotContaining("was not loaded");
    }

    @Test
    void testResolve_severalTesterBeansOneMarkedPrimary_usesPrimaryBean() throws Exception
    {
        try (AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext(PrimaryAndOtherTesterBeans.class))
        {
            final FakeTestContext context =
                    fakeTestContext(new NoFieldsTestInstance(), applicationContext);

            final TestInstanceTesterResolver.Resolution resolution =
                    resolver.resolve(context, noFactoryConfiguration);

            assertThat(resolution.getTester())
                    .as("Of several IDatabaseTester beans, the one marked @Primary must be used.")
                    .isSameAs(PrimaryAndOtherTesterBeans.PRIMARY);
        }
    }

    @Test
    void testResolve_severalTesterBeansOneNotMarkedFallback_usesTheNonFallbackBean()
            throws Exception
    {
        try (AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext(FallbackAndOtherTesterBeans.class))
        {
            final FakeTestContext context =
                    fakeTestContext(new NoFieldsTestInstance(), applicationContext);

            final TestInstanceTesterResolver.Resolution resolution =
                    resolver.resolve(context, noFactoryConfiguration);

            assertThat(resolution.getTester())
                    .as("Of several IDatabaseTester beans, the only one not marked @Fallback"
                            + " must be used.")
                    .isSameAs(FallbackAndOtherTesterBeans.REGULAR);
        }
    }

    @Test
    void testResolve_severalTesterBeansNoneMarkedPrimary_throwsIllegalStateException()
            throws Exception
    {
        try (AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext(TwoTesterBeans.class))
        {
            final FakeTestContext context =
                    fakeTestContext(new NoFieldsTestInstance(), applicationContext);

            assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                    .as("Several IDatabaseTester beans with none marked @Primary are ambiguous"
                            + " and must not be resolved to either.")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No IDatabaseTester field or bean found");
        }
    }

    @Test
    void testResolve_contextNotLoadedYetButTestClassDeclaresContextConfiguration_loadsContextAndUsesBean()
            throws Exception
    {
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final HasContextConfiguration testInstance = new HasContextConfiguration();
        final FakeTestContext context = new FakeTestContext(testInstance.getClass(),
                applicationContextWithTester(beanTester), false)
                        .beginMethod(testInstance, "aTestMethod");

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A context nothing has loaded yet - e.g. because the"
                        + " DependencyInjectionTestExecutionListener is absent - must be loaded"
                        + " for the bean tier when the test class declares a configuration to"
                        + " load it from, not skipped silently.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_contextNotLoadedYetButTestClassDeclaresContextHierarchy_loadsContextAndUsesBean()
            throws Exception
    {
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final HasContextHierarchy testInstance = new HasContextHierarchy();
        final FakeTestContext context = new FakeTestContext(testInstance.getClass(),
                applicationContextWithTester(beanTester), false)
                        .beginMethod(testInstance, "aTestMethod");

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A @ContextHierarchy declares a configuration to load the context from, as"
                        + " a @ContextConfiguration does.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_contextNotLoadedAndNoContextConfiguration_messageExplainsContextWasNotLoaded()
            throws Exception
    {
        final NoFieldsTestInstance testInstance = new NoFieldsTestInstance();
        final FakeTestContext context = new FakeTestContext(testInstance.getClass(),
                applicationContextWithTester(mock(IDatabaseTester.class)), false)
                        .beginMethod(testInstance, "aTestMethod");

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("When the bean tier was skipped because no ApplicationContext was loaded, the"
                        + " message must say so instead of asking for a bean that may exist.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field or bean found")
                .hasMessageContaining(NoFieldsTestInstance.class.getName())
                .hasMessageContaining("The ApplicationContext was not loaded")
                .hasMessageContaining("MERGE_WITH_DEFAULTS");
    }

    @Test
    void testResolve_nestedInstanceWithoutOwnTester_usesEnclosingInstancesMarkedField()
            throws Exception
    {
        final IDatabaseTester enclosingTester = mock(IDatabaseTester.class);
        final EnclosingWithMarkedTester enclosing = new EnclosingWithMarkedTester(enclosingTester);
        final EnclosingWithMarkedTester.NestedWithoutTester nested = enclosing.new NestedWithoutTester();

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(nested), noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A @Nested class without a tester field of its own must use the one"
                        + " declared on its enclosing class, as under DbUnitExtension.")
                .isSameAs(enclosingTester);
    }

    @Test
    void testResolve_nestedInstanceWithOwnMarkedField_shadowsEnclosingInstancesField()
            throws Exception
    {
        final IDatabaseTester nestedTester = mock(IDatabaseTester.class);
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithOwnMarkedTester nested =
                enclosing.new NestedWithOwnMarkedTester(nestedTester);

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(nested), noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("The @Nested class's own marked field must shadow the enclosing class's,"
                        + " not be rejected as ambiguous with it.")
                .isSameAs(nestedTester);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachable_throwsIllegalStateException()
            throws Exception
    {
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithoutTester nested = enclosing.new NestedWithoutTester();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                fakeTestContext(nested, applicationContextWithTester(beanTester));

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("A tester field the enclosing class declares but this resolver cannot reach"
                        + " must be reported, not silently replaced by the ApplicationContext's"
                        + " bean.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EnclosingWithMarkedTester.class.getName())
                .hasMessageContaining("'tester'")
                .hasMessageContaining("cannot be reached")
                .hasMessageContaining(EnclosingWithMarkedTester.NestedWithoutTester.class.getName());
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndEnclosingDeclaresTestCaseField_throwsIllegalStateException()
            throws Exception
    {
        final EnclosingWithMarkedTestCase enclosing = new EnclosingWithMarkedTestCase();
        final EnclosingWithMarkedTestCase.Nested nested = enclosing.new Nested();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested,
                applicationContextWithTester(mock(IDatabaseTester.class)));

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("A @DbUnitTestCase field on an enclosing class whose instance cannot be"
                        + " reached must be reported too, not only a @DbUnitTester field.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EnclosingWithMarkedTestCase.class.getName())
                .hasMessageContaining("'testCase'")
                .hasMessageContaining("cannot be reached");
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndEnclosingDeclaresUnmarkedTesterField_throwsIllegalStateException()
            throws Exception
    {
        final EnclosingWithUnmarkedTester enclosing = new EnclosingWithUnmarkedTester();
        final EnclosingWithUnmarkedTester.Nested nested = enclosing.new Nested();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested,
                applicationContextWithTester(mock(IDatabaseTester.class)));

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("A plain IDatabaseTester field on an enclosing class whose instance cannot be"
                        + " reached would have been the tester, so it must be reported rather"
                        + " than replaced by the ApplicationContext's bean.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EnclosingWithUnmarkedTester.class.getName())
                .hasMessageContaining("'tester'")
                .hasMessageContaining("cannot be reached");
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndEnclosingHasOnlyStaticUnmarkedTesterField_usesBean()
            throws Exception
    {
        final EnclosingWithStaticUnmarkedTester enclosing = new EnclosingWithStaticUnmarkedTester();
        final EnclosingWithStaticUnmarkedTester.Nested nested = enclosing.new Nested();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                fakeTestContext(nested, applicationContextWithTester(beanTester));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A static, unmarked IDatabaseTester field is not one the field rules would"
                        + " have used, so the unreachable enclosing instance loses nothing.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableButNoTesterFieldThere_usesBean()
            throws Exception
    {
        final EnclosingWithoutTester enclosing = new EnclosingWithoutTester();
        final EnclosingWithoutTester.Nested nested = enclosing.new Nested();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final FakeTestContext context =
                fakeTestContext(nested, applicationContextWithTester(beanTester));

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("An unreachable enclosing instance that declares no tester field loses"
                        + " nothing, so resolution carries on to the ApplicationContext bean.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndNestedHasOwnMarkedTester_usesNestedTester()
            throws Exception
    {
        final IDatabaseTester nestedTester = mock(IDatabaseTester.class);
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithOwnMarkedTester nested =
                enclosing.new NestedWithOwnMarkedTester(nestedTester);
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("A marked field the @Nested class declares itself outranks every tester"
                        + " field of the enclosing class, so the unreachable enclosing instance"
                        + " must not be rejected.")
                .isSameAs(nestedTester);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndNestedHasOwnMarkedTestCase_usesItsTester()
            throws Exception
    {
        final IDatabaseTester testCaseTester = mock(IDatabaseTester.class);
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);
        when(testCase.getDatabaseTester()).thenReturn(testCaseTester);
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithOwnMarkedTestCase nested =
                enclosing.new NestedWithOwnMarkedTestCase(testCase);
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTestCase())
                .as("A @DbUnitTestCase field the @Nested class declares itself is a marked field"
                        + " too, so the unreachable enclosing instance must not be rejected.")
                .isSameAs(testCase);
        assertThat(resolution.getTester()).as("The test case's own tester must be used.")
                .isSameAs(testCaseTester);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndNestedHasOwnUnmarkedTesterButEnclosingDeclaresMarkedTester_throwsIllegalStateException()
            throws Exception
    {
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithOwnUnmarkedTester nested =
                enclosing.new NestedWithOwnUnmarkedTester(mock(IDatabaseTester.class));
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        assertThatThrownBy(() -> resolver.resolve(context, noFactoryConfiguration))
                .as("A marked field on the enclosing class outranks an unmarked field of the"
                        + " @Nested class, so an unreachable enclosing instance must be reported.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EnclosingWithMarkedTester.class.getName())
                .hasMessageContaining("cannot be reached");
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndEnclosingDeclaresMarkedTesterAndFactoryConfigured_throwsIllegalStateException()
            throws Exception
    {
        RecordingFactory.next = mock(IDatabaseTester.class);
        final EnclosingWithMarkedTester enclosing =
                new EnclosingWithMarkedTester(mock(IDatabaseTester.class));
        final EnclosingWithMarkedTester.NestedWithoutTester nested =
                enclosing.new NestedWithoutTester();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        assertThatThrownBy(() -> resolver.resolve(context, configurationWithFactory()))
                .as("A marked field on the enclosing class outranks the configured factory, so"
                        + " an unreachable enclosing instance must be reported.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EnclosingWithMarkedTester.class.getName())
                .hasMessageContaining("cannot be reached");
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndEnclosingDeclaresUnmarkedTesterAndFactoryConfigured_usesFactoryTester()
            throws Exception
    {
        RecordingFactory.next = mock(IDatabaseTester.class);
        final EnclosingWithUnmarkedTester enclosing = new EnclosingWithUnmarkedTester();
        final EnclosingWithUnmarkedTester.Nested nested = enclosing.new Nested();
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, configurationWithFactory());

        assertThat(resolution.getTester())
                .as("The configured factory outranks an unmarked field of the enclosing class,"
                        + " so the unreachable enclosing instance must not be rejected.")
                .isSameAs(RecordingFactory.next);
    }

    @Test
    void testResolve_nestedInstanceEnclosingInstanceUnreachableAndNestedHasOwnUnmarkedTester_usesNestedTester()
            throws Exception
    {
        final IDatabaseTester nestedTester = mock(IDatabaseTester.class);
        final EnclosingWithUnmarkedTester enclosing = new EnclosingWithUnmarkedTester();
        final EnclosingWithUnmarkedTester.NestedWithOwnUnmarkedTester nested =
                enclosing.new NestedWithOwnUnmarkedTester(nestedTester);
        TestInstanceChainTest.clearEnclosingReference(nested);
        final FakeTestContext context = fakeTestContext(nested);

        final TestInstanceTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.getTester())
                .as("An unmarked field the @Nested class declares itself is searched before the"
                        + " enclosing class's, so the unreachable enclosing instance must not be"
                        + " rejected.")
                .isSameAs(nestedTester);
    }

    private AnnotatedTestConfiguration configurationWithFactory()
    {
        final DbUnitConfig config = WithFactory.class.getAnnotation(DbUnitConfig.class);
        return AnnotatedTestConfiguration.from(SpringTesterResolverTest.class, config, null, null,
                null, null, null);
    }

    private static ApplicationContext applicationContextWithTester(final IDatabaseTester tester)
    {
        final ApplicationContext applicationContext = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        final ObjectProvider<IDatabaseTester> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(tester);
        when(applicationContext.getBeanProvider(IDatabaseTester.class)).thenReturn(provider);
        return applicationContext;
    }

    private static FakeTestContext fakeTestContext(final Object testInstance) throws Exception
    {
        return fakeTestContext(testInstance, null);
    }

    private static FakeTestContext fakeTestContext(final Object testInstance,
            final ApplicationContext applicationContext) throws Exception
    {
        return new FakeTestContext(testInstance.getClass(), applicationContext)
                .beginMethod(testInstance, "aTestMethod");
    }

    static class HasTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;

        HasTestCaseField(final PrepAndExpectedTestCase testCase)
        {
            this.testCase = testCase;
        }

        void aTestMethod()
        {
        }
    }

    static class HasTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;

        HasTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasUnmarkedTesterField
    {
        IDatabaseTester tester;

        HasUnmarkedTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class NoFieldsTestInstance
    {
        void aTestMethod()
        {
        }
    }

    @ContextConfiguration
    static class HasContextConfiguration
    {
        void aTestMethod()
        {
        }
    }

    @ContextHierarchy(@ContextConfiguration)
    static class HasContextHierarchy
    {
        void aTestMethod()
        {
        }
    }

    @Configuration
    static class PrimaryAndOtherTesterBeans
    {
        static final IDatabaseTester PRIMARY = mock(IDatabaseTester.class);
        static final IDatabaseTester OTHER = mock(IDatabaseTester.class);

        @Bean
        @Primary
        IDatabaseTester primaryTester()
        {
            return PRIMARY;
        }

        @Bean
        IDatabaseTester otherTester()
        {
            return OTHER;
        }
    }

    @Configuration
    static class FallbackAndOtherTesterBeans
    {
        static final IDatabaseTester REGULAR = mock(IDatabaseTester.class);
        static final IDatabaseTester FALLBACK = mock(IDatabaseTester.class);

        @Bean
        IDatabaseTester regularTester()
        {
            return REGULAR;
        }

        @Bean
        @Fallback
        IDatabaseTester fallbackTester()
        {
            return FALLBACK;
        }
    }

    @Configuration
    static class TwoTesterBeans
    {
        @Bean
        IDatabaseTester firstTester()
        {
            return mock(IDatabaseTester.class);
        }

        @Bean
        IDatabaseTester secondTester()
        {
            return mock(IDatabaseTester.class);
        }
    }

    /**
     * Each inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    static class EnclosingWithMarkedTester
    {
        @DbUnitTester
        IDatabaseTester tester;

        EnclosingWithMarkedTester(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        class NestedWithoutTester
        {
            Object enclosing()
            {
                return EnclosingWithMarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }

        class NestedWithOwnMarkedTester
        {
            @DbUnitTester
            IDatabaseTester nestedTester;

            NestedWithOwnMarkedTester(final IDatabaseTester nestedTester)
            {
                this.nestedTester = nestedTester;
            }

            Object enclosing()
            {
                return EnclosingWithMarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }

        class NestedWithOwnMarkedTestCase
        {
            @DbUnitTestCase
            PrepAndExpectedTestCase nestedTestCase;

            NestedWithOwnMarkedTestCase(final PrepAndExpectedTestCase nestedTestCase)
            {
                this.nestedTestCase = nestedTestCase;
            }

            Object enclosing()
            {
                return EnclosingWithMarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }

        class NestedWithOwnUnmarkedTester
        {
            IDatabaseTester nestedTester;

            NestedWithOwnUnmarkedTester(final IDatabaseTester nestedTester)
            {
                this.nestedTester = nestedTester;
            }

            Object enclosing()
            {
                return EnclosingWithMarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    /**
     * Each inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    static class EnclosingWithMarkedTestCase
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithMarkedTestCase.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class EnclosingWithUnmarkedTester
    {
        IDatabaseTester tester;

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithUnmarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }

        class NestedWithOwnUnmarkedTester
        {
            IDatabaseTester nestedTester;

            NestedWithOwnUnmarkedTester(final IDatabaseTester nestedTester)
            {
                this.nestedTester = nestedTester;
            }

            Object enclosing()
            {
                return EnclosingWithUnmarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class EnclosingWithStaticUnmarkedTester
    {
        static IDatabaseTester tester;

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithStaticUnmarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class EnclosingWithoutTester
    {
        String unrelatedField = "not a tester";

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithoutTester.this;
            }

            void aTestMethod()
            {
            }
        }
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
    private static class WithFactory
    {
    }
}
