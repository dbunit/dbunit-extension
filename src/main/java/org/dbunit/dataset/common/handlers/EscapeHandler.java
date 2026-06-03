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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public class EscapeHandler extends AbstractPipelineComponent {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(EscapeHandler.class);

    public static final char DEFAULT_ESCAPE_CHAR = '\\';

    private EscapeHandler() {
    }

    public static final PipelineComponent ACCEPT() {
        logger.debug("ACCEPT() - start");
        return createPipelineComponent(new EscapeHandler(), new ACCEPT());
    }

    // @todo: make sense?
    public static final PipelineComponent IGNORE() {
        logger.debug("IGNORE() - start");
        return createPipelineComponent(new EscapeHandler(), new IGNORE());
    }

    public static final PipelineComponent ESCAPE() {
        logger.debug("ESCAPE() - start");
        return createPipelineComponent(new EscapeHandler(), new ESCAPE());
    }

    public boolean canHandle(char c) throws IllegalInputCharacterException {
        if(logger.isDebugEnabled())
            logger.debug("canHandle(c={}) - start", String.valueOf(c));

        PipelineConfig pipelineConfig = this.getPipelineConfig();
        if (c == pipelineConfig.getEscapeChar()) {
            return true;
        }
        return false;
    }

    static private class ESCAPE extends Helper {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(ESCAPE.class);

        public void helpWith(char c) {
            if(logger.isDebugEnabled())
                logger.debug("helpWith(c={}) - start", String.valueOf(c));

            getHandler().getPipeline().putFront(EnforceHandler.ENFORCE(
                    new PipelineComponent [] {
                        QuoteHandler.ACCEPT(), EscapeHandler.ACCEPT()
                    }
            ));
            // ignore the char
        }
    }

}
