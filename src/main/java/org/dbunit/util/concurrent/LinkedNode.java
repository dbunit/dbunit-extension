/*
  File: LinkedNode.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
  25may2000  dl               Change class access to public
  26nov2001  dl               Added no-arg constructor, all public access.
*/

package org.dbunit.util.concurrent;

/** 
 * A standard linked list node used in various queue classes
 * 
 * @author Doug Lea
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since ? (pre 2.1)
 */
public class LinkedNode {
  /** The value held by this node. */
  public Object value;
  /** The next node in the list, or {@code null} if this is the last node. */
  public LinkedNode next;

  /**
   * Default constructor.
   */
  public LinkedNode() {}

  /**
   * Constructs a node holding the given value.
   *
   * @param x the value held by this node.
   */
  public LinkedNode(Object x) { value = x; }

  /**
   * Constructs a node holding the given value and linked to the given next node.
   *
   * @param x the value held by this node.
   * @param n the next node in the list.
   */
  public LinkedNode(Object x, LinkedNode n) { value = x; next = n; }
}
