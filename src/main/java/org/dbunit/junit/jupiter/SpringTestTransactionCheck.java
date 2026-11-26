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

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells whether Spring's test context framework has a test-managed transaction active on the
 * calling thread, which is the case for a test that Spring runs with {@code @Transactional}.
 * It asks Spring's {@code TestTransaction} by reflection, so that {@link DbUnitExtension}, which
 * needs no Spring, does not need it on its class path or load any class of it: without Spring
 * the answer is {@code false}.
 *
 * <p>This type is internal to dbUnit's bindings and may change in a minor release.
 *
 * @since 3.6.0
 */
class SpringTestTransactionCheck implements BooleanSupplier
{
    private static final Logger log = LoggerFactory.getLogger(SpringTestTransactionCheck.class);

    private static final String TEST_TRANSACTION_CLASS_NAME =
            "org.springframework.test.context.transaction.TestTransaction";

    private final Method isActive;

    /**
     * Creates the check.
     *
     * @param classLoader The class loader of the test class, which finds Spring when the test
     *            uses it.
     */
    SpringTestTransactionCheck(final ClassLoader classLoader)
    {
        this.isActive = findIsActive(classLoader);
    }

    private static Method findIsActive(final ClassLoader classLoader)
    {
        try
        {
            final Class<?> testTransaction =
                    Class.forName(TEST_TRANSACTION_CLASS_NAME, false, classLoader);
            return testTransaction.getMethod("isActive");
        } catch (final ClassNotFoundException | NoSuchMethodException | LinkageError e)
        {
            log.debug("Spring's TestTransaction is not available, so no Spring test"
                    + " transaction is detected", e);
            return null;
        }
    }

    /**
     * Returns whether a test-managed transaction is active on the calling thread.
     *
     * @return {@code true} when Spring is present and its test transaction is active;
     *         {@code false} otherwise, also when Spring fails to answer.
     */
    @Override
    public boolean getAsBoolean()
    {
        if (isActive == null)
        {
            return false;
        }

        try
        {
            return Boolean.TRUE.equals(isActive.invoke(null));
        } catch (final ReflectiveOperationException | RuntimeException e)
        {
            log.debug("Spring's TestTransaction.isActive() failed, so no Spring test transaction"
                    + " is assumed", e);
            return false;
        }
    }
}
