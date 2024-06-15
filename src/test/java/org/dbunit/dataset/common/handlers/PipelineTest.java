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
 * author: fede 29-lug-2003 16.14.18 $Revision$
 */
@ExtendWith(MockitoExtension.class)
class PipelineTest
{
    Pipeline line;

    @Test
    void testRemovingTheLastHandlerThrowsException()
    {
        line.removeFront();
        assertThrows(PipelineException.class, () -> {
            line.removeFront();
        }, "Removing from an ampty pipeline should throw an exception");
    }

    @Test
    void testAnHandlerCanBeAddedInFront() throws PipelineException
    {
        final PipelineComponent handler = SeparatorHandler.ACCEPT();
        line.putFront(handler);
        assertThat(line.removeFront()).isSameAs(handler);
        assertThat(handler.getPipeline()).isSameAs(line);
    }

    @Test
    void testTheFrontHandlerIsThereAfterAddingAndRemovingAnother()
            throws PipelineException
    {
        final PipelineComponent handler = SeparatorHandler.ACCEPT();
        final PipelineComponent handler2 = SeparatorHandler.ACCEPT();
        line.putFront(handler);
        line.putFront(handler2);
        assertThat(line.removeFront()).isSameAs(handler2);
        assertThat(line.removeFront()).isSameAs(handler);
    }

    @Test
    void testEachHandlerIsCalled(@Mock final PipelineComponent component,
            @Mock final PipelineComponent component2)
            throws IllegalInputCharacterException, PipelineException
    {
        line.putFront(component);
        line.putFront(component2);
        doThrow(new IllegalInputCharacterException("")).when(component2)
                .handle(anyChar());
        // the last handler will throw an exception
        assertThrows(IllegalInputCharacterException.class,
                () -> line.handle('x'), "Exception expected");

        verify(component, times(1)).setSuccessor(any(TransparentHandler.class));
        verify(component, times(1)).setPipeline(any(Pipeline.class));
        verify(component2, times(1)).handle(anyChar());

        // component.verify();
        // component2.verify();
    }

    @Test
    void testWhenAPieceIsDoneIsAddedToProducts()
            throws IllegalInputCharacterException, PipelineException
    {
        final PipelineComponent c = AllHandler.ACCEPT();
        line.putFront(c);
        line.handle('x');
        line.thePieceIsDone();
        assertThat(line.getProducts()).hasSize(1);
        assertThat(line.getProducts().get(0)).isEqualTo("x");
    }

    @Test
    void testWhetAPieceIsDoneANewOneIsCreated()
            throws IllegalInputCharacterException, PipelineException
    {
        final PipelineComponent c = AllHandler.ACCEPT();
        line.putFront(c);
        line.handle('x');
        line.thePieceIsDone();
        assertThat(line.getCurrentProduct()).hasToString("");
    }

    @BeforeEach
    protected void setUp() throws Exception
    {
        line = new Pipeline();
    }
}
