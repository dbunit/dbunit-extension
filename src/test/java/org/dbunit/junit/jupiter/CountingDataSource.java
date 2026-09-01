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

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.dbunit.DatabaseProfile;

/**
 * A test {@link DataSource} that opens real {@link DriverManager} connections against a
 * {@link DatabaseProfile} and accounts for every one: how many are open at once (peak), and
 * whether they all get closed. Each connection it hands back is a proxy whose {@code close()}
 * closes the real connection and frees its slot.
 *
 * <p>When constructed with a {@code maxConcurrent} cap, {@code getConnection()} throws rather
 * than block once that many are already open, so a runtime that tries to hold more connections
 * than expected fails fast instead of deadlocking the test.
 *
 * <p>Sequential use only - no synchronization beyond the atomic counters.
 */
final class CountingDataSource implements DataSource
{
    private final DatabaseProfile profile;
    private final int maxConcurrent;
    private final AtomicInteger inUse = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();

    CountingDataSource(final DatabaseProfile profile)
    {
        this(profile, 0);
    }

    CountingDataSource(final DatabaseProfile profile, final int maxConcurrent)
    {
        this.profile = profile;
        this.maxConcurrent = maxConcurrent;
    }

    int peakConcurrent()
    {
        return peak.get();
    }

    int leaked()
    {
        return opened.get() - closed.get();
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        final int now = inUse.incrementAndGet();
        if (maxConcurrent > 0 && now > maxConcurrent)
        {
            inUse.decrementAndGet();
            throw new SQLException("CountingDataSource capped at " + maxConcurrent
                    + " concurrent connections; connection " + now + " was requested while "
                    + (now - 1) + " were still open");
        }
        peak.accumulateAndGet(now, Math::max);
        final Connection real;
        try
        {
            real = DriverManager.getConnection(profile.getConnectionUrl(), profile.getUser(),
                    profile.getPassword());
        }
        catch (final SQLException e)
        {
            inUse.decrementAndGet();
            throw e;
        }
        opened.incrementAndGet();
        return slotFreeingProxy(real);
    }

    private Connection slotFreeingProxy(final Connection real)
    {
        final InvocationHandler handler = (proxy, method, args) ->
        {
            if ("close".equals(method.getName()) && (args == null || args.length == 0))
            {
                if (!real.isClosed())
                {
                    real.close();
                    closed.incrementAndGet();
                    inUse.decrementAndGet();
                }
                return null;
            }
            try
            {
                return method.invoke(real, args);
            }
            catch (final InvocationTargetException e)
            {
                throw e.getCause();
            }
        };
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, handler);
    }

    @Override
    public Connection getConnection(final String username, final String password)
            throws SQLException
    {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter()
    {
        return null;
    }

    @Override
    public void setLogWriter(final PrintWriter out)
    {
    }

    @Override
    public void setLoginTimeout(final int seconds)
    {
    }

    @Override
    public int getLoginTimeout()
    {
        return 0;
    }

    @Override
    public Logger getParentLogger()
    {
        return Logger.getLogger("");
    }

    @Override
    public <T> T unwrap(final Class<T> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface)
    {
        return false;
    }
}
