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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.util.fileloader.CachingFileExtensionDataFileLoader;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataFileLoaderInstancesTest
{
    @BeforeEach
    void setUp()
    {
        CountedLoader.constructed.set(0);
        PlainCountedLoader.constructed.set(0);
        FailingFirstLoader.attempts.set(0);
    }

    @Test
    void testGet_sameCachingLoaderClassTwice_returnsTheSameInstance()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        final DataFileLoader first = instances.get(CachingFileExtensionDataFileLoader.class);
        final DataFileLoader second = instances.get(CachingFileExtensionDataFileLoader.class);

        assertThat(first).as("A caching loader class is built once for the scope.")
                .isExactlyInstanceOf(CachingFileExtensionDataFileLoader.class);
        assertThat(second).as("The second request gets the instance the first built.")
                .isSameAs(first);
    }

    @Test
    void testGet_loaderClassThatIsNotMadeToBeShared_buildsANewInstanceForEveryRequest()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        final DataFileLoader first = instances.get(PlainCountedLoader.class);
        final DataFileLoader second = instances.get(PlainCountedLoader.class);

        assertThat(second).as("A loader that does not cache is not shared: a test case that"
                + " changes the replacement objects of the loader it is given changes only its"
                + " own.").isNotSameAs(first);
        assertThat(PlainCountedLoader.constructed.get())
                .as("The class is constructed for every request.").isEqualTo(2);
    }

    @Test
    void testGet_twoLoaderClasses_returnsOneInstanceOfEach()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        final DataFileLoader flatXml = instances.get(FlatXmlDataFileLoader.class);
        final DataFileLoader byExtension = instances.get(FileExtensionDataFileLoader.class);

        assertThat(flatXml).as("Each loader class has an instance of its own.")
                .isExactlyInstanceOf(FlatXmlDataFileLoader.class);
        assertThat(byExtension).as("Each loader class has an instance of its own.")
                .isExactlyInstanceOf(FileExtensionDataFileLoader.class);
    }

    @Test
    void testGet_subclassOfALoaderClass_isNotTheInstanceOfItsParent()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        final DataFileLoader parent = instances.get(CachingFileExtensionDataFileLoader.class);
        final DataFileLoader subclass = instances.get(CachingSubclass.class);

        assertThat(subclass).as("A subclass is a loader class of its own.")
                .isExactlyInstanceOf(CachingSubclass.class).isNotSameAs(parent);
    }

    @Test
    void testGet_twoInstancesOfTheStore_doNotShareLoaders()
    {
        final DataFileLoaderInstances first = new DataFileLoaderInstances();
        final DataFileLoaderInstances second = new DataFileLoaderInstances();

        final DataFileLoader fromFirst = first.get(CachingFileExtensionDataFileLoader.class);
        final DataFileLoader fromSecond = second.get(CachingFileExtensionDataFileLoader.class);

        assertThat(fromSecond).as("The scope of the store is the scope of its loaders.")
                .isNotSameAs(fromFirst);
    }

    @Test
    void testGet_loaderWithoutAccessibleNoArgConstructor_throwsNamingTheAttribute()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        assertThatThrownBy(() -> instances.get(NoNoArgConstructorLoader.class))
                .as("The message must name the attribute and the class.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbUnitConfig.dataFileLoader")
                .hasMessageContaining(NoNoArgConstructorLoader.class.getName());
    }

    @Test
    void testGet_constructorThrows_failureIsNotKeptSoALaterRequestTriesAgain()
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();

        assertThatThrownBy(() -> instances.get(FailingFirstLoader.class))
                .as("The constructor's failure must reach the caller.")
                .isInstanceOf(IllegalStateException.class);
        final DataFileLoader second = instances.get(FailingFirstLoader.class);

        assertThat(second).as("A failed construction must not be remembered.")
                .isExactlyInstanceOf(FailingFirstLoader.class);
        assertThat(FailingFirstLoader.attempts.get()).as("The class was constructed twice.")
                .isEqualTo(2);
    }

    @Test
    void testGet_concurrentFirstRequests_buildOneInstanceAndAllGetIt() throws Exception
    {
        final DataFileLoaderInstances instances = new DataFileLoaderInstances();
        final int threadCount = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<CompletableFuture<DataFileLoader>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++)
        {
            futures.add(CompletableFuture.supplyAsync(() ->
            {
                try
                {
                    start.await();
                    return instances.get(CountedLoader.class);
                } catch (final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
        }

        start.countDown();
        final DataFileLoader first = futures.get(0).get();
        for (final CompletableFuture<DataFileLoader> future : futures)
        {
            assertThat(future.get()).as("Every thread must get the one instance.")
                    .isSameAs(first);
        }

        assertThat(CountedLoader.constructed.get())
                .as("The loader class must be constructed once, however many threads ask.")
                .isEqualTo(1);
    }

    public static class CachingSubclass extends CachingFileExtensionDataFileLoader
    {
    }

    public static class NoNoArgConstructorLoader extends FlatXmlDataFileLoader
    {
        public NoNoArgConstructorLoader(final String ignored)
        {
        }
    }

    public static class CountedLoader extends CachingFileExtensionDataFileLoader
    {
        static final AtomicInteger constructed = new AtomicInteger();

        public CountedLoader()
        {
            constructed.incrementAndGet();
        }
    }

    public static class PlainCountedLoader extends FlatXmlDataFileLoader
    {
        static final AtomicInteger constructed = new AtomicInteger();

        public PlainCountedLoader()
        {
            constructed.incrementAndGet();
        }
    }

    public static class FailingFirstLoader extends CachingFileExtensionDataFileLoader
    {
        static final AtomicInteger attempts = new AtomicInteger();

        public FailingFirstLoader()
        {
            if (attempts.incrementAndGet() == 1)
            {
                throw new IllegalArgumentException("first construction fails");
            }
        }
    }
}
