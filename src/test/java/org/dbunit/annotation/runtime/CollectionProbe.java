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

import java.lang.ref.WeakReference;

/**
 * Asks the garbage collector whether an object a test has let go of is really gone. The
 * collector is never obliged to run when asked, so it is asked repeatedly, for a bounded time.
 */
final class CollectionProbe
{
    private static final int ATTEMPTS = 40;
    private static final long PAUSE_MILLIS = 50;

    private CollectionProbe()
    {
    }

    /**
     * Returns whether the referent of {@code reference} has been garbage collected, requesting
     * collection until it is or the attempts run out.
     *
     * @param reference A weak reference to an object nothing in the test refers to any more.
     * @return {@code true} once the referent is gone.
     * @throws InterruptedException If the wait between collection requests is interrupted.
     */
    static boolean isCollected(final WeakReference<?> reference) throws InterruptedException
    {
        for (int attempt = 0; attempt < ATTEMPTS && reference.get() != null; attempt++)
        {
            System.gc();
            Thread.sleep(PAUSE_MILLIS);
        }
        return reference.get() == null;
    }
}
