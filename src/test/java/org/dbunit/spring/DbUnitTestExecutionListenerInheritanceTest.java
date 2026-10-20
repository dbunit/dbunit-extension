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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.spring.inheritance.ClassLevelPrepBase;
import org.dbunit.spring.inheritance.MethodLevelPrepBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
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
        final SetUpRecordingTester tester = new SetUpRecordingTester();
        InheritedClassLevelPrepSample.tester = tester;

        runSuccessfully(InheritedClassLevelPrepSample.class);

        assertThat(tester.getTableNamesOfTheFirstDataSetSetUp())
                .as("A relative @DbUnitPrep path inherited from a base class in another package"
                        + " names a file next to that base class, not next to the subclass.")
                .containsExactly("SPRING_BASE_CLASS_TABLE");
    }

    @Test
    void testBeforeTestMethod_methodLevelPrepOnInheritedTestMethod_loadsFileNextToTheDeclaringClass()
            throws Exception
    {
        final SetUpRecordingTester tester = new SetUpRecordingTester();
        InheritedMethodLevelPrepSample.tester = tester;

        runSuccessfully(InheritedMethodLevelPrepSample.class);

        assertThat(tester.getTableNamesOfTheFirstDataSetSetUp())
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
     * A tester over a fresh in-memory database whose setup operation records the tables of the
     * dataset it is run on and changes nothing.
     */
    private static class SetUpRecordingTester extends AbstractDatabaseTester
    {
        private final List<List<String>> setUpDataSets = new ArrayList<>();

        SetUpRecordingTester()
        {
            setSetUpOperation(new DatabaseOperation()
            {
                @Override
                public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                        throws DatabaseUnitException
                {
                    setUpDataSets.add(Arrays.asList(dataSet.getTableNames()));
                }
            });
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception
        {
            return InMemoryDatabaseConnection.create();
        }

        /**
         * Returns the tables of the first dataset the setup operation ran on.
         */
        List<String> getTableNamesOfTheFirstDataSetSetUp()
        {
            return setUpDataSets.get(0);
        }
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
