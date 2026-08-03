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
 * Pipeline component that delegates to the first wrapped {@link PipelineComponent} able to
 * handle a character, throwing {@link IllegalInputCharacterException} if none of them can.
 *
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public class EnforceHandler extends AbstractPipelineComponent {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(EnforceHandler.class);

    private PipelineComponent [] enforcedComponents;
    private PipelineComponent theHandlerComponent;

    private EnforceHandler(PipelineComponent [] components) {
        setEnforcedComponents(components);
    }


    /**
     * Creates a handler that enforces the given component to handle the character.
     *
     * @param component the component that must handle the character.
     * @return the new pipeline component.
     */
    public static final PipelineComponent ENFORCE(PipelineComponent component) {
        logger.debug("ENFORCE(component={}) - start", component);

        return EnforceHandler.ENFORCE(new PipelineComponent [] {component});
    }

    /**
     * Creates a handler that enforces the first of the given components able to handle the character.
     *
     * @param components the components, tried in order, one of which must handle the character.
     * @return the new pipeline component.
     */
    public static final PipelineComponent ENFORCE(PipelineComponent [] components) {
        logger.debug("ENFORCE(components={}) - start", (Object) components);

        return createPipelineComponent(new EnforceHandler(components), new ENFORCE());
    }

    public boolean canHandle(char c) throws IllegalInputCharacterException {
        if(logger.isDebugEnabled())
            logger.debug("canHandle(c={}) - start", String.valueOf(c));

        for (int i = 0; i < getEnforcedComponents().length; i++) {
            if (getEnforcedComponents()[i].canHandle(c)) {
                setTheHandlerComponent(getEnforcedComponents()[i]);
                return true;
            }
        }
        throw new IllegalInputCharacterException("(working on piece #" + getPipeline().getProducts().size() + ")"
                + getPipeline().getCurrentProduct().toString() + ": " + "Character '" + c + "' cannot be handled");
    }

    public void setPipeline(Pipeline pipeline) {
        logger.debug("setPipeline(pipeline={}) - start", pipeline);

        for (int i = 0; i < getEnforcedComponents().length; i++) {
            getEnforcedComponents()[i].setPipeline(pipeline);
        }
        super.setPipeline(pipeline);
    }

    /**
     * Returns the components tried, in order, to handle a character.
     *
     * @return the components tried, in order, to handle a character.
     */
    protected PipelineComponent[] getEnforcedComponents() {
        logger.debug("getEnforcedComponents() - start");

        return enforcedComponents;
    }

    /**
     * Sets the components tried, in order, to handle a character.
     *
     * @param enforcedComponents the components tried, in order, to handle a character.
     */
    protected void setEnforcedComponents(PipelineComponent[] enforcedComponents) {
        logger.debug("setEnforcedComponents(enforcedComponents={}) - start", (Object) enforcedComponents);

        this.enforcedComponents = enforcedComponents;
    }

    PipelineComponent getTheHandlerComponent() {
        logger.debug("getTheHandlerComponent() - start");

        return theHandlerComponent;
    }

    void setTheHandlerComponent(PipelineComponent theHandlerComponent) {
        logger.debug("setTheHandlerComponent(theHandlerComponent={}) - start",
                theHandlerComponent);

        this.theHandlerComponent = theHandlerComponent;
    }

    static private class ENFORCE extends Helper {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(ENFORCE.class);

        public void helpWith(char c) {
            if(logger.isDebugEnabled())
                logger.debug("helpWith(c={}) - start", String.valueOf(c));

            try {
                EnforceHandler handler = (EnforceHandler) getHandler();
                handler.getTheHandlerComponent().handle(c);
                getHandler().getPipeline().removeFront();
            } catch (PipelineException e) {
                throw new RuntimeException(e.getMessage());
            } catch (IllegalInputCharacterException e) {
                throw new RuntimeException(e.getMessage());
            }
            // ignore the char
        }
    }
}
