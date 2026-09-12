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
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.spring.inheritance.ClassLevelPrepBase;
import org.dbunit.spring.inheritance.MethodLevelPrepBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests that a dataset path written relative to a package resolves against the package of the
 * class that declares the annotation naming it, when {@link DbUnitTestExecutionListener} runs
 * under Spring's own {@code SpringExtension}: the test method and class Spring hands the listener
 * are those of a real run, where an inherited test method is the superclass's own. The sample
 * test classes inherit from base classes in another package, which hold the datasets.
 */
class DbUnitTestExecutionListenerInheritanceTest
{
    @AfterEach
    void resetStaticFixtureState()
    {
        InheritedClassLevelPrepSample.tester = null;
        InheritedMethodLevelPrepSample.tester = null;
    }

    @Test
    void testBeforeTestMethod_classLevelPrepInheritedFromAnotherPackage_loadsFileNextToTheDeclaringClass()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        InheritedClassLevelPrepSample.tester = tester;

        runSuccessfully(InheritedClassLevelPrepSample.class);

        assertThat(tableNamesOfFirstDataSetSetOn(tester))
                .as("A relative @DbUnitPrep path inherited from a base class in another package"
                        + " names a file next to that base class, not next to the subclass.")
                .containsExactly("SPRING_BASE_CLASS_TABLE");
    }

    @Test
    void testBeforeTestMethod_methodLevelPrepOnInheritedTestMethod_loadsFileNextToTheDeclaringClass()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        InheritedMethodLevelPrepSample.tester = tester;

        runSuccessfully(InheritedMethodLevelPrepSample.class);

        assertThat(tableNamesOfFirstDataSetSetOn(tester))
                .as("A relative @DbUnitPrep path on an inherited test method names a file next"
                        + " to the class that declares the method.")
                .containsExactly("SPRING_BASE_METHOD_TABLE");
    }

    private static void runSuccessfully(final Class<?> sampleTestClass)
    {
        EngineTestKit.engine("junit-jupiter").selectors(selectClass(sampleTestClass)).execute()
                .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    /**
     * Returns the tables of the first data set the listener set on {@code tester}; it sets the
     * tester's own data set back afterwards, which is a later call.
     */
    private static List<String> tableNamesOfFirstDataSetSetOn(final IDatabaseTester tester)
            throws Exception
    {
        final ArgumentCaptor<IDataSet> dataSets = ArgumentCaptor.forClass(IDataSet.class);
        verify(tester, atLeastOnce()).setDataSet(dataSets.capture());
        final IDataSet first = dataSets.getAllValues().get(0);
        return Arrays.asList(first.getTableNames());
    }

    @Configuration
    static class EmptyConfig
    {
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @DbUnitSpringTest
    static class InheritedClassLevelPrepSample extends ClassLevelPrepBase
    {
        @DbUnitTester
        static IDatabaseTester tester;

        @Test
        void testInheritedClassLevelPrep()
        {
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @DbUnitSpringTest
    static class InheritedMethodLevelPrepSample extends MethodLevelPrepBase
    {
        @DbUnitTester
        static IDatabaseTester tester;
    }
}
