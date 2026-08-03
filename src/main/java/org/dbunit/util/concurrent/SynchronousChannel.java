/*
  File: SynchronousChannel.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
  17Jul1998  dl               Disabled direct semaphore permit check
  31Jul1998  dl               Replaced main algorithm with one with
                              better scaling and fairness properties.
  25aug1998  dl               added peek
  24Nov2001  dl               Replaced main algorithm with faster one.
*/

package org.dbunit.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rendezvous channel, similar to those used in CSP and Ada. Each
 * put must wait for a take, and vice versa. Synchronous channels
 * are well suited for handoff designs, in which an object running in
 * one thread must synch up with an object running in another thread
 * in order to hand it some information, event, or task. 
 * <p> If you only need threads to synch up without
 * exchanging information, consider using a Barrier. If you need
 * bidirectional exchanges, consider using a Rendezvous.
 *
 * <p>Read the
 * <a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html">introduction to this package</a> 
 * for more details.
 * 
 * @author Doug Lea
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since ? (pre 2.1)
 * @see <a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/Rendezvous.html">Rendezvous</a>
 * @see <a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/CyclicBarrier.html">CyclicBarrier</a>
 */
public class SynchronousChannel implements BoundedChannel {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SynchronousChannel.class);

  /*
    This implementation divides actions into two cases for puts:

    * An arriving putter that does not already have a waiting taker 
      creates a node holding item, and then waits for a taker to take it.
    * An arriving putter that does already have a waiting taker fills
      the slot node created by the taker, and notifies it to continue.

   And symmetrically, two for takes:

    * An arriving taker that does not already have a waiting putter
      creates an empty slot node, and then waits for a putter to fill it.
    * An arriving taker that does already have a waiting putter takes
      item from the node created by the putter, and notifies it to continue.

   This requires keeping two simple queues: waitingPuts and waitingTakes.
   
   When a put or take waiting for the actions of its counterpart
   aborts due to interruption or timeout, it marks the node
   it created as "CANCELLED", which causes its counterpart to retry
   the entire put or take sequence.
  */

  /** 
   * Special marker used in queue nodes to indicate that
   * the thread waiting for a change in the node has timed out
   * or been interrupted.
   **/
  protected static final Object CANCELLED = new Object();
  
  /**
   * Simple FIFO queue class to hold waiting puts/takes.
   **/
  protected static class Queue {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(Queue.class);

    /** The first node in the queue, or {@code null} if empty. */
    protected LinkedNode head;
    /** The last node in the queue, or {@code null} if empty. */
    protected LinkedNode last;

    /**
     * Appends the given node to the end of the queue.
     *
     * @param p the node to append.
     */
    protected void enq(LinkedNode p) {
            logger.debug("enq(p={}) - start", p);

      if (last == null)
        last = head = p;
      else
        last = last.next = p;
    }

    /**
     * Removes and returns the node at the front of the queue.
     *
     * @return the node that was at the front of the queue, or {@code null} if empty.
     */
    protected LinkedNode deq() {
            logger.debug("deq() - start");

      LinkedNode p = head;
      if (p != null && (head = p.next) == null)
        last = null;
      return p;
    }
  }

  /** Queue of nodes for puts waiting for a taker. */
  protected final Queue waitingPuts = new Queue();
  /** Queue of nodes for takes waiting for a putter. */
  protected final Queue waitingTakes = new Queue();

  /**
   * @return zero --
   * Synchronous channels have no internal capacity.
   **/
  public int capacity() {
        logger.debug("capacity() - start");
 return 0; }

  /**
   * @return null --
   * Synchronous channels do not hold contents unless actively taken
   **/
  public Object peek() {
        logger.debug("peek() - start");
  return null;  }

  public void put(Object x) throws InterruptedException {
        logger.debug("put(x={}) - start", x);

    if (x == null) throw new IllegalArgumentException();

    // This code is conceptually straightforward, but messy
    // because we need to intertwine handling of put-arrives first
    // vs take-arrives first cases.

    // Outer loop is to handle retry due to canceled waiting taker
    for (;;) { 

      // Get out now if we are interrupted
      if (Thread.interrupted()) throw new InterruptedException();

      // Exactly one of item or slot will be not-null at end of
      // synchronized block, depending on whether a put or a take
      // arrived first. 
      LinkedNode slot;
      LinkedNode item = null;

      synchronized(this) {
        // Try to match up with a waiting taker; fill and signal it below
        slot = waitingTakes.deq();

        // If no takers yet, create a node and wait below
        if (slot == null) 
          waitingPuts.enq(item = new LinkedNode(x));
      }

      if (slot != null) { // There is a waiting taker.
        // Fill in the slot created by the taker and signal taker to
        // continue.
        synchronized(slot) {
          if (slot.value != CANCELLED) {
            slot.value = x;
            slot.notify();
            return;
          }
          // else the taker has canceled, so retry outer loop
        }
      }

      else { 
        // Wait for a taker to arrive and take the item.
        synchronized(item) {
          try {
            while (item.value != null)
              item.wait();
            return;
          }
          catch (InterruptedException ie) {
            // If item was taken, return normally but set interrupt status
            if (item.value == null) {
              Thread.currentThread().interrupt();
              return;
            }
            else {
              item.value = CANCELLED;
              throw ie;
            }
          }
        }
      }
    }
  }

  public Object take() throws InterruptedException {
        logger.debug("take() - start");

    // Entirely symmetric to put()

    for (;;) {
      if (Thread.interrupted()) throw new InterruptedException();

      LinkedNode item;
      LinkedNode slot = null;

      synchronized(this) {
        item = waitingPuts.deq();
        if (item == null) 
          waitingTakes.enq(slot = new LinkedNode());
      }

      if (item != null) {
        synchronized(item) {
          Object x = item.value;
          if (x != CANCELLED) {
            item.value = null;
            item.next = null;
            item.notify();
            return x;
          }
        }
      }

      else {
        synchronized(slot) {
          try {
            for (;;) {
              Object x = slot.value;
              if (x != null) {
                slot.value = null;
                slot.next = null;
                return x;
              }
              else
                slot.wait();
            }
          }
          catch(InterruptedException ie) {
            Object x = slot.value;
            if (x != null) {
              slot.value = null;
              slot.next = null;
              Thread.currentThread().interrupt();
              return x;
            }
            else {
              slot.value = CANCELLED;
              throw ie;
            }
          }
        }
      }
    }
  }

  /*
    Offer and poll are just like put and take, except even messier.
   */

  public boolean offer(Object x, long msecs) throws InterruptedException {
	  if(logger.isDebugEnabled())
        logger.debug("offer(x={}, msecs={}) - start", x, String.valueOf(msecs));

    if (x == null) throw new IllegalArgumentException();
    long waitTime = msecs;
    long startTime = 0; // lazily initialize below if needed
    
    for (;;) {
      if (Thread.interrupted()) throw new InterruptedException();

      LinkedNode slot;
      LinkedNode item = null;

      synchronized(this) {
        slot = waitingTakes.deq();
        if (slot == null) {
          if (waitTime <= 0) 
            return false;
          else 
            waitingPuts.enq(item = new LinkedNode(x));
        }
      }

      if (slot != null) {
        synchronized(slot) {
          if (slot.value != CANCELLED) {
            slot.value = x;
            slot.notify();
            return true;
          }
        }
      }

      long now = System.currentTimeMillis();
      if (startTime == 0) 
        startTime = now;
      else 
        waitTime = msecs - (now - startTime);

      if (item != null) {
        synchronized(item) {
          try {
            for (;;) {
              if (item.value == null) 
                return true;
              if (waitTime <= 0) {
                item.value = CANCELLED;
                return false;
              }
              item.wait(waitTime);
              waitTime = msecs - (System.currentTimeMillis() - startTime);
            }
          }
          catch (InterruptedException ie) {
            if (item.value == null) {
              Thread.currentThread().interrupt();
              return true;
            }
            else {
              item.value = CANCELLED;
              throw ie;
            }
          }
        }
      }
    }
  }

  public Object poll(long msecs) throws InterruptedException {
	  if(logger.isDebugEnabled())
        logger.debug("poll(msecs={}) - start", String.valueOf(msecs));

    long waitTime = msecs;
    long startTime = 0;

    for (;;) {
      if (Thread.interrupted()) throw new InterruptedException();

      LinkedNode item;
      LinkedNode slot = null;

      synchronized(this) {
        item = waitingPuts.deq();
        if (item == null) {
          if (waitTime <= 0) 
            return null;
          else 
            waitingTakes.enq(slot = new LinkedNode());
        }
      }

      if (item != null) {
        synchronized(item) {
          Object x = item.value;
          if (x != CANCELLED) {
            item.value = null;
            item.next = null;
            item.notify();
            return x;
          }
        }
      }

      long now = System.currentTimeMillis();
      if (startTime == 0) 
        startTime = now;
      else 
        waitTime = msecs - (now - startTime);

      if (slot != null) {
        synchronized(slot) {
          try {
            for (;;) {
              Object x = slot.value;
              if (x != null) {
                slot.value = null;
                slot.next = null;
                return x;
              }
              if (waitTime <= 0) {
                slot.value = CANCELLED;
                return null;
              }
              slot.wait(waitTime);
              waitTime = msecs - (System.currentTimeMillis() - startTime);
            }
          }
          catch(InterruptedException ie) {
            Object x = slot.value;
            if (x != null) {
              slot.value = null;
              slot.next = null;
              Thread.currentThread().interrupt();
              return x;
            }
            else {
              slot.value = CANCELLED;
              throw ie;
            }
          }
        }
      }
    }
  }

}
