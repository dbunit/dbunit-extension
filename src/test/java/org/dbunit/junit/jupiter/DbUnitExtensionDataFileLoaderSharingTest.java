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
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.util.fileloader.CachingFileExtensionDataFileLoader;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * The data file loader of a caching class named in {@code @DbUnitConfig(dataFileLoader = ...)} is
 * built once for the run of {@link DbUnitExtension}, and shared by every test of the run, whatever
 * its test class. A loader of any other class, the default one included, is built for every test,
 * so a test case that changes the loader it is given changes no other test's.
 */
class DbUnitExtensionDataFileLoaderSharingTest
{
    @BeforeEach
    void setUp()
    {
        CountedLoader.constructed.set(0);
        PlainCountedLoader.constructed.set(0);
        LoaderRecordingTestCase.dataFileLoaders.clear();
    }

    @Test
    void testBeforeTestExecution_severalClassesNamingAPlainLoaderClass_buildsALoaderForEachTest()
    {
        run(PlainFirstSample.class, PlainSecondSample.class);

        assertThat(PlainCountedLoader.constructed.get())
                .as("A loader that does not cache is built for every test, not once for the run.")
                .isEqualTo(4);
        assertThat(LoaderRecordingTestCase.dataFileLoaders)
                .as("Each of the four tests gets a loader of its own, so a replacement object"
                        + " one test case adds reaches no other test.")
                .hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    void testBeforeTestExecution_noLoaderNamedInTheClasses_buildsTheDefaultLoaderForEachTest()
    {
        run(DefaultLoaderSample.class);

        assertThat(LoaderRecordingTestCase.dataFileLoaders)
                .as("The default loader does not cache, so each of the two tests builds its own.")
                .hasSize(2).doesNotHaveDuplicates()
                .allSatisfy(loader -> assertThat(loader)
                        .isExactlyInstanceOf(FileExtensionDataFileLoader.class));
    }

    @Test
    void testBeforeTestExecution_severalClassesAndMethodsNamingOneLoaderClass_buildsOneLoaderForTheRun()
    {
        run(FirstSample.class, SecondSample.class);

        assertThat(CountedLoader.constructed.get())
                .as("The loader class is built once for the run, not once per test.")
                .isEqualTo(1);
        assertThat(LoaderRecordingTestCase.dataFileLoaders)
                .as("Each of the four tests builds its test case with a loader.").hasSize(4);
        final DataFileLoader first = LoaderRecordingTestCase.dataFileLoaders.get(0);
        for (final DataFileLoader loader : LoaderRecordingTestCase.dataFileLoaders)
        {
            assertThat(loader).as("Every test of the run gets the one loader.").isSameAs(first);
        }
    }

    @Test
    void testBeforeTestExecution_twoSeparateRuns_eachRunBuildsItsOwnLoader()
    {
        run(FirstSample.class);
        run(FirstSample.class);

        assertThat(CountedLoader.constructed.get())
                .as("The scope is the run, so a second run does not reuse the first run's loader.")
                .isEqualTo(2);
    }

    @Test
    void testBeforeTestExecution_classesNamingDifferentLoaderClasses_buildsOneLoaderOfEachClass()
    {
        run(FirstSample.class, OtherLoaderSample.class);

        assertThat(CountedLoader.constructed.get())
                .as("Each loader class is built once for the run.").isEqualTo(2);
        assertThat(LoaderRecordingTestCase.dataFileLoaders)
                .as("The test cases of the two classes get a loader of the class they name.")
                .hasSize(3)
                .extracting(loader -> loader.getClass().getName())
                .containsOnly(CountedLoader.class.getName(), OtherCountedLoader.class.getName());
    }

    private static void run(final Class<?>... testClasses)
    {
        final DiscoverySelector[] selectors = new DiscoverySelector[testClasses.length];
        for (int i = 0; i < testClasses.length; i++)
        {
            selectors[i] = selectClass(testClasses[i]);
        }
        EngineTestKit.engine("junit-jupiter").selectors(selectors).execute().testEvents()
                .assertStatistics(stats -> stats.failed(0));
    }

    private static IDatabaseTester newTester()
    {
        try
        {
            final DefaultDatabaseTester tester =
                    new DefaultDatabaseTester(InMemoryDatabaseConnection.create());
            tester.setDataSet(new DefaultDataSet());
            return tester;
        } catch (final Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** A caching loader that counts how often it is built. */
    public static class CountedLoader extends CachingFileExtensionDataFileLoader
    {
        static final AtomicInteger constructed = new AtomicInteger();

        public CountedLoader()
        {
            constructed.incrementAndGet();
        }
    }

    /** A loader that does not cache, which counts how often it is built. */
    public static class PlainCountedLoader extends FlatXmlDataFileLoader
    {
        static final AtomicInteger constructed = new AtomicInteger();

        public PlainCountedLoader()
        {
            constructed.incrementAndGet();
        }
    }

    /** A second loader class, counted with the first. */
    public static class OtherCountedLoader extends CountedLoader
    {
        public OtherCountedLoader()
        {
        }
    }

    /** The test case the extension builds for the samples: it records the loader it is given. */
    public static class LoaderRecordingTestCase extends DefaultPrepAndExpectedTestCase
    {
        static final List<DataFileLoader> dataFileLoaders =
                Collections.synchronizedList(new ArrayList<>());

        public LoaderRecordingTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester)
        {
            super(dataFileLoader, tester);
            dataFileLoaders.add(dataFileLoader);
        }
    }

    @DbUnitTest
    @DbUnitConfig(dataFileLoader = CountedLoader.class,
            prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class FirstSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testOne()
        {
        }

        @Test
        void testTwo()
        {
        }
    }

    @DbUnitTest
    @DbUnitConfig(dataFileLoader = CountedLoader.class,
            prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class SecondSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testThree()
        {
        }

        @Test
        void testFour()
        {
        }
    }

    @DbUnitTest
    @DbUnitConfig(dataFileLoader = PlainCountedLoader.class,
            prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class PlainFirstSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testOne()
        {
        }

        @Test
        void testTwo()
        {
        }
    }

    @DbUnitTest
    @DbUnitConfig(dataFileLoader = PlainCountedLoader.class,
            prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class PlainSecondSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testThree()
        {
        }

        @Test
        void testFour()
        {
        }
    }

    @DbUnitTest
    @DbUnitConfig(prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class DefaultLoaderSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testOne()
        {
        }

        @Test
        void testTwo()
        {
        }
    }

    @DbUnitTest
    @DbUnitConfig(dataFileLoader = OtherCountedLoader.class,
            prepAndExpectedTestCase = LoaderRecordingTestCase.class)
    static class OtherLoaderSample
    {
        @DbUnitTester
        IDatabaseTester databaseTester = newTester();

        @Test
        void testOther()
        {
        }
    }
}
