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
public class IsAlnumHandler extends AbstractPipelineComponent {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(IsAlnumHandler.class);

    private IsAlnumHandler() 
    {
    }

    public static final PipelineComponent ACCEPT () {
        logger.debug("ACCEPT() - start");
        return createPipelineComponent(new IsAlnumHandler(), new ACCEPT());
    }

    public static final PipelineComponent IGNORE () {
        logger.debug("IGNORE() - start");
        return createPipelineComponent(new IsAlnumHandler(), new IGNORE());
    }

    public static final PipelineComponent QUOTE () {
        logger.debug("QUOTE() - start");
        return createPipelineComponent(new IsAlnumHandler(), new QUOTE());
    }

/*
    public static final PipelineComponent UNQUOTE () {
        return createPipelineComponent(new IsAlnumHandler(), new UNQUOTE());
    }
*/


    public boolean canHandle(char c) throws IllegalInputCharacterException {
        if(logger.isDebugEnabled())
            logger.debug("canHandle(c={}) - start", String.valueOf(c));

        PipelineConfig pipelineConfig = this.getPipelineConfig();
        if (c != pipelineConfig.getSeparatorChar()
                && !Character.isWhitespace(c)
                && c != pipelineConfig.getEscapeChar()) {
            return true;
        }
        return false;
    }


    static protected class QUOTE extends Helper {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(QUOTE.class);

//        private boolean add = true;

        public void helpWith(char c) {
            if(logger.isDebugEnabled())
                logger.debug("helpWith(c={}) - start", String.valueOf(c));
            
            getHandler().getPipeline().putFront(SeparatorHandler.ENDPIECE());
            getHandler().getPipeline().putFront(IsAlnumHandler.ACCEPT());
            getHandler().getPipeline().putFront(WhitespacesHandler.ACCEPT());
            //getHandler().getPipeline().putFront(IsAlnumHandler.UNQUOTE());

            getHandler().accept(c);
        }
    }

    static protected class UNQUOTE extends Helper {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(UNQUOTE.class);


        public void helpWith(char c) {
            if(logger.isDebugEnabled())
                logger.debug("helpWith(c={}) - start", String.valueOf(c));

            try {
                getHandler().getPipeline().removeFront();
                getHandler().getPipeline().removeFront();
                getHandler().getPipeline().removeFront();
                getHandler().getPipeline().removeFront();
            } catch (PipelineException e) {
                throw new RuntimeException(e.getMessage());
            }
            // ignore the char
        }
    }

}
