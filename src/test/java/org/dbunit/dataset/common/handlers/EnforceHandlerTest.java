/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

package org.dbunit.dataset.common.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * author: fede 4-set-2003 11.42.06 $Revision$
 */
@ExtendWith(MockitoExtension.class)
class EnforceHandlerTest
{
    Pipeline pipeline;

    @Test
    void testOwnAnEnforcedHandler()
    {
        final PipelineComponent enforced = AllHandler.ACCEPT();
        final EnforceHandler enforceHandler =
                (EnforceHandler) EnforceHandler.ENFORCE(enforced);
        pipeline.putFront(enforceHandler);
        assertThat(enforceHandler.getEnforcedComponents()[0])
                .isSameAs(enforced);
        assertThat(enforceHandler.getPipeline())
                .as("enforced pipeline should be the same of the enforcing one")
                .isSameAs(enforced.getPipeline());
    }

    @Test
    void testThrowExceptionWhenEnforcedDoesNotHandle(
            @Mock final PipelineComponent component)
            throws PipelineException, IllegalInputCharacterException
    {
        final PipelineComponent enforceHandler =
                EnforceHandler.ENFORCE(component);
        pipeline.putFront(enforceHandler);
        doThrow(new IllegalInputCharacterException("")).when(component)
                .canHandle(anyChar());
        assertThrows(IllegalInputCharacterException.class,
                () -> enforceHandler.handle('x'),
                "Enforce handler should have thrown an exception");
        verify(component, times(1)).setPipeline(any(Pipeline.class));
        verify(component, times(1)).canHandle(anyChar());

    }

    @Test
    void testDontRemoveItselfOnException(
            @Mock final PipelineComponent component)
            throws PipelineException, IllegalInputCharacterException
    {
        final PipelineComponent enforceHandler =
                EnforceHandler.ENFORCE(component);
        pipeline.putFront(enforceHandler);
        doThrow(new IllegalInputCharacterException("")).when(component)
                .canHandle(anyChar());
        assertThrows(IllegalInputCharacterException.class,
                () -> pipeline.handle('x'),
                "Enforce handler should have thrown an exception");

        assertThat(enforceHandler)
                .as("enforced pipeline should be the same of the enforcing one")
                .isSameAs(pipeline.removeFront());
        verify(component, times(1)).setPipeline(any(Pipeline.class));
        verify(component, times(1)).canHandle(anyChar());
    }

    @Test
    void testRemoveItselfAfterEnforcing()
            throws PipelineException, IllegalInputCharacterException
    {
        final PipelineComponent enforceHandler =
                EnforceHandler.ENFORCE(AllHandler.ACCEPT());
        pipeline.putFront(enforceHandler);
        pipeline.handle('\"');
        pipeline.thePieceIsDone();
        assertThat(enforceHandler).isNotSameAs(pipeline.removeFront());
        assertThat(pipeline.getProducts()).hasSize(1);
        assertThat(pipeline.getProducts().get(0)).isEqualTo("\"");
    }

    @Test
    void testEnforceOneBetweenMany()
            throws PipelineException, IllegalInputCharacterException
    {
        final PipelineComponent pass = SeparatorHandler.ACCEPT();
        final PipelineComponent accept = AllHandler.ACCEPT();
        final EnforceHandler enforceHandler = (EnforceHandler) EnforceHandler
                .ENFORCE(new PipelineComponent[] {pass, accept});
        pipeline.putFront(enforceHandler);

        pipeline.handle('\"');
        pipeline.thePieceIsDone();

        assertThat(enforceHandler).isNotSameAs(pipeline.removeFront());
        assertThat(pipeline.getProducts()).hasSize(1);
        assertThat(pipeline.getProducts().get(0)).isEqualTo("\"");
    }

    @BeforeEach
    protected void setUp() throws Exception
    {
        pipeline = new Pipeline();
    }

}
