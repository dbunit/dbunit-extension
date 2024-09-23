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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public class Pipeline implements Handler {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Pipeline.class);


    private LinkedList components;
    private List products;
    private StringBuilder currentProduct;
    private PipelineComponent noHandler;
    private PipelineConfig pipelineConfig = new PipelineConfig();

    public Pipeline() {
        setComponents(new LinkedList());
        setProducts(new ArrayList());


        // add a no handler as the last handler
        setNoHandler(NoHandler.IGNORE());
        getNoHandler().setSuccessor(null);
        getComponents().addFirst(getNoHandler());

        // add a transparent handler as placeholder
        //getComponents().addFirst(TransparentHandler.IGNORE);

        //prepareNewPiece();
        setCurrentProduct(new StringBuilder());
        putFront(TransparentHandler.IGNORE());
    }

    public StringBuilder getCurrentProduct() {
        logger.debug("getCurrentProduct() - start");

        return currentProduct;
    }

    public void setCurrentProduct(StringBuilder currentProduct) {
        logger.debug("setCurrentProduct(currentProduct={}) - start", currentProduct);

        this.currentProduct = currentProduct;
    }

    private void prepareNewPiece() {
        logger.debug("prepareNewPiece() - start");

        setCurrentProduct(new StringBuilder());

        // remove all the components down to a TrasparentHandler
        try {
            while (!(getComponents().getFirst() instanceof TransparentHandler)) {
                removeFront();
            }
        } catch (PipelineException e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    public void thePieceIsDone() {
        logger.debug("thePieceIsDone() - start");

        getProducts().add(getCurrentProduct().toString());
        prepareNewPiece();
    }

    public List getProducts() {
        logger.debug("getProducts() - start");

        return products;
    }

    protected void setProducts(List products) {
        logger.debug("setProducts(products={}) - start", products);

        this.products = products;
    }

    private LinkedList getComponents() {
        logger.debug("getComponents() - start");

        return components;
    }

    private void setComponents(LinkedList components) {
        logger.debug("setComponents(components={}) - start", components);

        this.components = components;
    }

    public void putFront(PipelineComponent component) {
        logger.debug("putFront(component={}) - start", component);

        component.setSuccessor((PipelineComponent) getComponents().getFirst());
        component.setPipeline(this);
        getComponents().addFirst(component);
    }

    public PipelineComponent removeFront() throws PipelineException {
        logger.debug("removeFront() - start");

        PipelineComponent first = (PipelineComponent) getComponents().getFirst();
        remove(first);
        return first;
    }

    public void remove(PipelineComponent component) throws PipelineException {
        logger.debug("remove(component={}) - start", component);

        if (component == getNoHandler()) {
            throw new PipelineException("Cannot remove the last handler");
        }

        if (!getComponents().remove(component)) {
            throw new PipelineException("Cannot remove a non existent component from a pipeline");
        }
    }

    public boolean canHandle(char c) throws IllegalInputCharacterException {
        if(logger.isDebugEnabled())
            logger.debug("canHandle(c={}) - start", String.valueOf(c));

        return true;
    }

    public void handle(char c) throws IllegalInputCharacterException, PipelineException {
        if(logger.isDebugEnabled())
            logger.debug("handle(c={}) - start", String.valueOf(c));

        ((Handler) getComponents().getFirst()).handle(c);
    }

    public boolean allowForNoMoreInput() {
        logger.debug("allowForNoMoreInput() - start");

        throw new IllegalStateException("you cannot call Pipeline.allowForNoMoreInput");
    }

    private PipelineComponent getNoHandler() {
        logger.debug("getNoHandler() - start");

        return noHandler;
    }

    private void setNoHandler(PipelineComponent noHandler) {
        logger.debug("setNoHandler(noHandler={}) - start", noHandler);

        this.noHandler = noHandler;
    }

    public void resetProducts() {
        logger.debug("resetProducts() - start");

        setProducts(new ArrayList());
    }

    public void noMoreInput() {
        logger.debug("noMoreInput() - start");

        ((Handler) getComponents().getFirst()).noMoreInput();
        //thePieceIsDone();
    }

    public PipelineConfig getPipelineConfig() {
        return pipelineConfig;
    }

    public void setPipelineConfig(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig;
    }

}
