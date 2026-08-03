/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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
package org.dbunit.util.search;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Super-class for ISearchCallback implementations that needs to filter which
 * nodes should be included or excluded from the search.<br>
 * This class implements the <code>searchNode()</code> based on its internal 
 * mode, which could be <code>ALLOW_MODE</code>, <code>DENY_MODE</code> or 
 * <code>NO_MODE</code>:
 * <ul>
 * <li><code>NO_MODE</code> is the default mode and means <code>searchNode()</code>
 * always return true</li>
 * <li><code>ALLOW_MODE</code> is set when <code>setAllowedNodes()</code> is called
 * and it means <code>searchNode()</code> will return true only if the node is
 * contained on the Set (or array) passed to <code>setAllowedNodes()</code>
 * <li><code>DENY_MODE</code> is set when <code>setDeniedNodes()</code> is called
 * and it means <code>searchNode()</code> will return true only if the node is
 * not contained on the Set (or array) passed to <code>setDeniedNodes()</code>
 * </ul>
 * 
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 25, 2005
 * 
 */
public abstract class AbstractNodesFilterSearchCallback implements
    ISearchCallback {
  
  /**
   * Logger for this class.
   */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  // internal modes
  /** No nodes are allowed or denied; {@link #searchNode(Object)} always returns <code>true</code>. */
  protected static final int NO_MODE = 0;
  /** Only nodes set via {@link #setAllowedNodes(Set)} are allowed. */
  protected static final int ALLOW_MODE = 1;
  /** Only nodes set via {@link #setDeniedNodes(Set)} are denied. */
  protected static final int DENY_MODE = 2;
  
  private int filteringMode = NO_MODE;
  
  private Set filteredNodes = new HashSet();
  
  /**
   * Default constructor.
   *
   */
  public AbstractNodesFilterSearchCallback() {    
  }

  /**
   * Get which modes are allowed/denied, depending on the operation mode.
   * @return which modes are allowed/denied, depending on the operation mode.
   */
  protected Set getFilteredNodes() {
    return this.filteredNodes;
  }
  
  /**
   * Get the operation mode
   * @return operation mode
   */
  protected int getFilteringMode() {
    return this.filteringMode;
  }
  
  /**
   * Set which modes are allowed on the search.
   * @param filteredNodes which modes are allowed on the search.
   */  
  protected void setAllowedNodes(Set filteredNodes) {
        logger.debug("setAllowedNodes(filteredNodes=" + filteredNodes + ") - start");

    setFilteredNodes(filteredNodes);
    this.filteringMode = ALLOW_MODE;
  }
  
  /**
   * Set which modes are allowed on the search.
   * @param filteredNodes which modes are allowed on the search.
   */  
  protected void setAllowedNodes(Object[] filteredNodes) {
        logger.debug("setAllowedNodes(filteredNodes=" + filteredNodes + ") - start");

    setFilteredNodes(filteredNodes);
    this.filteringMode = ALLOW_MODE;
  }
  
  /**
   * Set which modes are not allowed on the search.
   * @param filteredNodes which modes are not allowed on the search.
   */  
  protected void setDeniedNodes(Set filteredNodes) {
        logger.debug("setDeniedNodes(filteredNodes=" + filteredNodes + ") - start");

    setFilteredNodes(filteredNodes);
    this.filteringMode = DENY_MODE;
  }

  /**
   * Set which modes are not allowed on the search.
   * @param filteredNodes which modes are not allowed on the search.
   */  
  protected void setDeniedNodes(Object[] filteredNodes) {
        logger.debug("setDeniedNodes(filteredNodes=" + filteredNodes + ") - start");

    setFilteredNodes(filteredNodes);
    this.filteringMode = DENY_MODE;
  }

  /**
   * Do nothing...
   */
  public void nodeAdded(Object fromNode) throws SearchException {
    // do nothing
  }
  
  public boolean searchNode(Object node) throws SearchException {
        logger.debug("searchNode(node=" + node + ") - start");

    switch( this.filteringMode ) {
    case ALLOW_MODE:
      return getFilteredNodes().contains(node); 
    case DENY_MODE:
      return !getFilteredNodes().contains(node);
    default:
        return true;
    }
  }
  
  private void setFilteredNodes(Set filteredNodes) {
        logger.debug("setFilteredNodes(filteredNodes=" + filteredNodes + ") - start");

    this.filteredNodes = new HashSet(filteredNodes);
  }

  private void setFilteredNodes(Object[] filteredNodes) {
        logger.debug("setFilteredNodes(filteredNodes=" + filteredNodes + ") - start");

    this.filteredNodes = new HashSet(filteredNodes.length);
    for (int i = 0; i < filteredNodes.length; i++) {
      this.filteredNodes.add(filteredNodes[i]);
    }
  }
  
}
