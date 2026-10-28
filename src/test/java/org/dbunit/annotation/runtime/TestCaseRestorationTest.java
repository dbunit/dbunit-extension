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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TestCaseRestorationTest
{
    private final List<String> stepsRun = new ArrayList<>();

    @Test
    void testUndo_twoStepsThrowTheSameFailure_runsEveryStepAndThrowsItUnsuppressed()
    {
        final RuntimeException storedFailure = new RuntimeException("one failure, thrown twice");
        final TestCaseRestoration restoration = new TestCaseRestoration(
                Arrays.asList(recordingStep("applied first"),
                        throwingStep("applied second", storedFailure),
                        throwingStep("applied third", storedFailure)));

        final Throwable thrown = catchThrowable(restoration::undo);

        assertThat(thrown)
                .as("Two steps that throw the same failure must make undo() throw that failure,"
                        + " not the IllegalArgumentException that adding a throwable to its own"
                        + " suppressed ones throws.")
                .isSameAs(storedFailure);
        assertThat(thrown.getSuppressed()).as("A failure is not suppressed by itself.")
                .isEmpty();
        assertThat(stepsRun)
                .as("Every step must run, most recent first, even after one failed twice over.")
                .containsExactly("applied third", "applied second", "applied first");
    }

    @Test
    void testUndo_laterStepFailsWithAnotherFailure_attachesItToTheFirst()
    {
        final RuntimeException firstFailure = new RuntimeException("the first failure");
        final RuntimeException laterFailure = new RuntimeException("a later failure");
        final TestCaseRestoration restoration = new TestCaseRestoration(
                Arrays.asList(throwingStep("applied first", laterFailure),
                        throwingStep("applied second", firstFailure)));

        final Throwable thrown = catchThrowable(restoration::undo);

        assertThat(thrown).as("The failure of the step that ran first is the one thrown.")
                .isSameAs(firstFailure);
        assertThat(thrown.getSuppressed())
                .as("A failure of another instance still rides along.")
                .containsExactly(laterFailure);
    }

    @Test
    void testUndoSuppressing_stepThrowsThePrimaryItself_throwsNothingAndSuppressesNothing()
    {
        final RuntimeException primary = new RuntimeException("the failure in flight");
        final TestCaseRestoration restoration =
                new TestCaseRestoration(Arrays.asList(throwingStep("only step", primary)));

        assertThatCode(() -> TestCaseRestoration.undoSuppressing(restoration, primary))
                .as("An undo step that rethrows the failure in flight must not make it suppress"
                        + " itself, which throws an IllegalArgumentException in its place.")
                .doesNotThrowAnyException();

        assertThat(primary.getSuppressed()).as("A throwable cannot suppress itself.").isEmpty();
    }

    @Test
    void testUndoSuppressing_stepFailsWithAnotherFailure_attachesItToThePrimary()
    {
        final RuntimeException primary = new RuntimeException("the failure in flight");
        final RuntimeException undoFailure = new RuntimeException("an undo failure");
        final TestCaseRestoration restoration =
                new TestCaseRestoration(Arrays.asList(throwingStep("only step", undoFailure)));

        TestCaseRestoration.undoSuppressing(restoration, primary);

        assertThat(primary.getSuppressed())
                .as("A failure of another instance still rides along, not replaces it.")
                .containsExactly(undoFailure);
    }

    private Runnable recordingStep(final String name)
    {
        return () -> stepsRun.add(name);
    }

    private Runnable throwingStep(final String name, final RuntimeException failure)
    {
        return () ->
        {
            stepsRun.add(name);
            throw failure;
        };
    }
}
