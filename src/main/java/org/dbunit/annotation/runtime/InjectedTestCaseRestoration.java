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

import java.util.ArrayList;
import java.util.List;

/**
 * Puts back what {@link InjectedTestCaseConfigurer} changed on an injected
 * {@code @DbUnitTestCase} instance, in reverse order, so a test case shared across test methods -
 * a {@code static} field, or any field under {@code @TestInstance(Lifecycle.PER_CLASS)} -
 * carries one method's {@code @DbUnitConfig} values onto neither the next method nor the settings
 * its owner built into it. The counterpart of {@link TesterStateSnapshot} for the tester.
 *
 * <p>Holds one undo step per value the configurer actually applied; a value it left alone has
 * none, so undoing never touches a setting that was never changed.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class InjectedTestCaseRestoration
{
    private final List<Runnable> undoSteps;

    /**
     * Creates a restoration from the undo steps, in the order the changes were applied.
     *
     * @param undoSteps One step per applied change; copied, not retained.
     */
    InjectedTestCaseRestoration(final List<Runnable> undoSteps)
    {
        this.undoSteps = new ArrayList<>(undoSteps);
    }

    /**
     * Undoes every applied change, most recent first. Every step runs even if an earlier one
     * fails, so one broken setter cannot leave the rest of the instance changed.
     *
     * @throws RuntimeException The first failure from an undo step, with any later ones attached
     *             as suppressed.
     */
    void undo()
    {
        RuntimeException firstFailure = null;
        for (int i = undoSteps.size() - 1; i >= 0; i--)
        {
            try
            {
                undoSteps.get(i).run();
            } catch (final RuntimeException failure)
            {
                if (firstFailure == null)
                {
                    firstFailure = failure;
                } else
                {
                    firstFailure.addSuppressed(failure);
                }
            }
        }

        if (firstFailure != null)
        {
            throw firstFailure;
        }
    }

    /**
     * Undoes {@code restoration} while {@code primary} is already being thrown, attaching any
     * failure from the undo itself to it via {@link Throwable#addSuppressed(Throwable)} rather
     * than letting that failure replace the one already in flight. A {@code null} restoration is
     * a no-op.
     *
     * @param restoration The restoration to undo, or {@code null} to do nothing.
     * @param primary The failure already being thrown, to attach an undo failure to.
     */
    static void undoSuppressing(final InjectedTestCaseRestoration restoration,
            final Throwable primary)
    {
        if (restoration == null)
        {
            return;
        }

        try
        {
            restoration.undo();
        } catch (final RuntimeException undoFailure)
        {
            primary.addSuppressed(undoFailure);
        }
    }

    /**
     * Undoes {@code restoration}, or does nothing when it is {@code null} - the case when the
     * configurer never ran.
     *
     * @param restoration The restoration to undo, or {@code null} to do nothing.
     */
    static void undoIfPresent(final InjectedTestCaseRestoration restoration)
    {
        if (restoration != null)
        {
            restoration.undo();
        }
    }
}
