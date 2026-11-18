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
package org.dbunit.database.connection;
import org.dbunit.IDatabaseTester;

/**
 * Remembers, in the JVM, which {@link IDatabaseTester}s a kind of warning has been given about,
 * by identity and without keeping any tester alive. The condition a connection warning reports
 * belongs to the tester, as it is the tester that decides how connections are built and
 * handed out, not to the lifecycle owner that the warning runs for, which an annotation-driven
 * test builds afresh for every test. So each kind of warning keeps one of these, and warns
 * about a tester once, however many tests that tester serves.
 *
 * <p>Thread safe, as the tests that use one tester may run in parallel.
 *
 * @since 3.6.0
 */
class WarnedTesters
{
    private final UsedTesters warnedTesters = new UsedTesters();

    /**
     * Records that a warning is about to be given about the tester.
     *
     * @param tester The tester the warning is about; not {@code null}.
     * @return {@code true} when no warning has been recorded for the tester yet, so the caller
     *         is to warn; {@code false} when it has been warned about already.
     */
    boolean isFirstWarningAbout(final IDatabaseTester tester)
    {
        final int recordedWarnings = warnedTesters.recordUse(tester);
        return recordedWarnings == 1;
    }
}
