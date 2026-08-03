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


/**
 * Base contract for an object that decides whether and how to handle a single
 * character during CSV parsing.
 *
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public interface Handler {
    /**
     * Handles the given character.
     *
     * @param c the character to handle.
     * @throws IllegalInputCharacterException if the character is not valid in the current context.
     * @throws PipelineException if the character cannot be processed by the pipeline.
     */
    public void handle(char c) throws IllegalInputCharacterException, PipelineException;

    /**
     * Determines whether this handler can handle the given character.
     *
     * @param c the character to check.
     * @return <code>true</code> if this handler can handle the character.
     * @throws IllegalInputCharacterException if the character is not valid in the current context.
     */
    public boolean canHandle(char c) throws IllegalInputCharacterException;

    /**
     * Notifies this handler that no more input will be provided.
     *
     * @throws IllegalStateException if this handler is not in a state where input can end.
     */
    public void noMoreInput() throws IllegalStateException;

    /**
     * Determines whether this handler allows input to end at this point.
     *
     * @return <code>true</code> if ending input now is allowed.
     * @throws IllegalStateException if the handler's state cannot be evaluated.
     */
    public boolean allowForNoMoreInput() throws IllegalStateException;
}
