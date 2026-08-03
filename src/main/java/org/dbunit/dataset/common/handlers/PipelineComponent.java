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
 * A {@link Handler} that can be chained into a {@link Pipeline} of successors
 * for character-by-character CSV field parsing.
 *
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public interface PipelineComponent extends Handler {
    /**
     * Sets the next component to which unhandled characters are delegated.
     *
     * @param successor the successor component.
     */
    void setSuccessor(PipelineComponent successor);

    /**
     * Accepts the given character as part of the current field value.
     *
     * @param c the character to accept.
     */
    void accept(char c);

    /**
     * Sets the pipeline this component belongs to.
     *
     * @param line the owning pipeline.
     */
    void setPipeline (Pipeline line);

    /**
     * Returns the pipeline this component belongs to.
     *
     * @return the owning pipeline.
     */
    Pipeline getPipeline();
}
