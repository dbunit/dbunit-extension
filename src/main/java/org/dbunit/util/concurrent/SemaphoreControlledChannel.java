/*
  File: SemaphoreControlledChannel.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  16Jun1998  dl               Create public version
   5Aug1998  dl               replaced int counters with longs
  08dec2001  dl               reflective constructor now uses longs too.
*/

package org.dbunit.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Abstract class for channels that use Semaphores to
 * control puts and takes.
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 * 
 * @author Doug Lea
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since ? (pre 2.1)
 */
public abstract class SemaphoreControlledChannel implements BoundedChannel {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SemaphoreControlledChannel.class);

  protected final Semaphore putGuard_;
  protected final Semaphore takeGuard_;
  protected int capacity_;

  /**
   * Create a channel with the given capacity and default
   * semaphore implementation
   * @exception IllegalArgumentException if capacity less or equal to zero
   **/

  public SemaphoreControlledChannel(int capacity) 
   throws IllegalArgumentException {
    if (capacity <= 0) throw new IllegalArgumentException();
    capacity_ = capacity;
    putGuard_ = new Semaphore(capacity);
    takeGuard_ = new Semaphore(0);
  }


  /**
   * Create a channel with the given capacity and 
   * semaphore implementations instantiated from the supplied class
   * @exception IllegalArgumentException if capacity less or equal to zero.
   * @exception NoSuchMethodException If class does not have constructor 
   * that intializes permits
   * @exception SecurityException if constructor information 
   * not accessible
   * @exception InstantiationException if semaphore class is abstract
   * @exception IllegalAccessException if constructor cannot be called
   * @exception InvocationTargetException if semaphore constructor throws an
   * exception
   **/
  public SemaphoreControlledChannel(int capacity, Class semaphoreClass) 
   throws IllegalArgumentException, 
          NoSuchMethodException, 
          SecurityException, 
          InstantiationException, 
          IllegalAccessException, 
          InvocationTargetException {
    if (capacity <= 0) throw new IllegalArgumentException();
    capacity_ = capacity;
    Class[] longarg = { Long.TYPE };
    Constructor ctor = semaphoreClass.getDeclaredConstructor(longarg);
    Long[] cap = {Long.valueOf(capacity)};
    putGuard_ = (Semaphore)(ctor.newInstance(cap));
    Long[] zero = {0L};
    takeGuard_ = (Semaphore)(ctor.newInstance(zero));
  }



  public int  capacity() {
        logger.debug("capacity() - start");
 return capacity_; }

  /** 
   * Return the number of elements in the buffer.
   * This is only a snapshot value, that may change
   * immediately after returning.
   **/

  public int size() {
        logger.debug("size() - start");
 return (int)(takeGuard_.permits());  }

  /**
   * Internal mechanics of put.
   **/
  protected abstract void insert(Object x);

  /**
   * Internal mechanics of take.
   **/
  protected abstract Object extract();

  public void put(Object x) throws InterruptedException {
        logger.debug("put(x=" + x + ") - start");

    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    putGuard_.acquire();
    try {
      insert(x);
      takeGuard_.release();
    }
    catch (ClassCastException ex) {
      putGuard_.release();
      throw ex;
    }
  }

  public boolean offer(Object x, long msecs) throws InterruptedException {
        logger.debug("offer(x=" + x + ", msecs=" + msecs + ") - start");

    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    if (!putGuard_.attempt(msecs)) 
      return false;
    else {
      try {
        insert(x);
        takeGuard_.release();
        return true;
      }
      catch (ClassCastException ex) {
        putGuard_.release();
        throw ex;
      }
    }
  }

  public Object take() throws InterruptedException {
        logger.debug("take() - start");

    if (Thread.interrupted()) throw new InterruptedException();
    takeGuard_.acquire();
    try {
      Object x = extract();
      putGuard_.release();
      return x;
    }
    catch (ClassCastException ex) {
      takeGuard_.release();
      throw ex;
    }
  }

  public Object poll(long msecs) throws InterruptedException {
        logger.debug("poll(msecs=" + msecs + ") - start");

    if (Thread.interrupted()) throw new InterruptedException();
    if (!takeGuard_.attempt(msecs))
      return null;
    else {
      try {
        Object x = extract();
        putGuard_.release();
        return x;
      }
      catch (ClassCastException ex) {
        takeGuard_.release();
        throw ex;
      }
    }
  }

}
