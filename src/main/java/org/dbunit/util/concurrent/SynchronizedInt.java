/*
  File: SynchronizedInt.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  19Jun1998  dl               Create public version
*/

package org.dbunit.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class useful for offloading synch for int instance variables.
 *
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 * 
 * @author Doug Lea
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since ? (pre 2.1)
 */
public class SynchronizedInt extends SynchronizedVariable implements Comparable, Cloneable {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SynchronizedInt.class);

  /** The current value. */
  protected int value_;

  /**
   * Make a new SynchronizedInt with the given initial value,
   * and using its own internal lock.
   *
   * @param initialValue the initial value.
   **/
  public SynchronizedInt(int initialValue) {
    super();
    value_ = initialValue;
  }

  /**
   * Make a new SynchronizedInt with the given initial value,
   * and using the supplied lock.
   *
   * @param initialValue the initial value.
   * @param lock the synchronization lock to use.
   **/
  public SynchronizedInt(int initialValue, Object lock) {
    super(lock);
    value_ = initialValue;
  }

  /**
   * Return the current value
   *
   * @return the current value.
   **/
  public final int get() {
 synchronized(lock_) { return value_; } }

  /**
   * Set to newValue.
   * @param newValue the new value.
   * @return the old value
   **/

  public int set(int newValue) {
        logger.debug("set(newValue={}) - start", String.valueOf(newValue));
 
    synchronized (lock_) {
      int old = value_;
      value_ = newValue; 
      return old;
    }
  }

  /**
   * Set value to newValue only if it is currently assumedValue.
   * @param assumedValue the value the current value must equal for the update to happen.
   * @param newValue the new value.
   * @return true if successful
   **/
  public boolean commit(int assumedValue, int newValue) {
        logger.debug("commit(assumedValue={}, newValue={}) - start", String.valueOf(assumedValue), String.valueOf(newValue));

    synchronized(lock_) {
      boolean success = (assumedValue == value_);
      if (success) value_ = newValue;
      return success;
    }
  }

  /** 
   * Atomically swap values with another SynchronizedInt.
   * Uses identityHashCode to avoid deadlock when
   * two SynchronizedInts attempt to simultaneously swap with each other.
   * (Note: Ordering via identyHashCode is not strictly guaranteed
   * by the language specification to return unique, orderable
   * values, but in practice JVMs rely on them being unique.)
   * @param other the SynchronizedInt to swap values with.
   * @return the new value
   **/

  public int swap(SynchronizedInt other) {
        logger.debug("swap(other={}) - start", other);

    if (other == this) return get();
    SynchronizedInt fst = this;
    SynchronizedInt snd = other;
    if (System.identityHashCode(fst) > System.identityHashCode(snd)) {
      fst = other;
      snd = this;
    }
    synchronized(fst.lock_) {
      synchronized(snd.lock_) {
        fst.set(snd.set(fst.get()));
        return get();
      }
    }
  }

  /** 
   * Increment the value.
   * @return the new value 
   **/
  public int increment() {
    synchronized (lock_) {
      return ++value_; 
    }
  }

  /** 
   * Decrement the value.
   * @return the new value 
   **/
  public int decrement() {
    synchronized (lock_) {
      return --value_; 
    }
  }

  /** 
   * Add amount to value (i.e., set value += amount)
   * @param amount the amount to add.
   * @return the new value
   **/
  public int add(int amount) {
    synchronized (lock_) {
      return value_ += amount; 
    }
  }

  /** 
   * Subtract amount from value (i.e., set value -= amount)
   * @param amount the amount to subtract.
   * @return the new value
   **/
  public int subtract(int amount) {
    synchronized (lock_) {
      return value_ -= amount; 
    }
  }

  /** 
   * Multiply value by factor (i.e., set value *= factor)
   * @param factor the factor to multiply by.
   * @return the new value
   **/
  public synchronized int multiply(int factor) {
    synchronized (lock_) {
      return value_ *= factor; 
    }
  }

  /** 
   * Divide value by factor (i.e., set value /= factor)
   * @param factor the factor to divide by.
   * @return the new value
   **/
  public int divide(int factor) {
    synchronized (lock_) {
      return value_ /= factor; 
    }
  }

  /** 
   * Set the value to the negative of its old value
   * @return the new value 
   **/
  public int negate() {
    synchronized (lock_) {
      value_ = -value_;
      return value_;
    }
  }

  /** 
   * Set the value to its complement
   * @return the new value 
   **/
  public  int complement() {
    synchronized (lock_) {
      value_ = ~value_;
      return value_;
    }
  }

  /** 
   * Set value to value &amp; b.
   * @param b the value to AND with.
   * @return the new value
   **/
  public  int and(int b) {
    synchronized (lock_) {
      value_ = value_ & b;
      return value_;
    }
  }

  /** 
   * Set value to value | b.
   * @param b the value to OR with.
   * @return the new value
   **/
  public  int or(int b) {
    synchronized (lock_) {
      value_ = value_ | b;
      return value_;
    }
  }


  /** 
   * Set value to value ^ b.
   * @param b the value to XOR with.
   * @return the new value
   **/
  public  int xor(int b) {
    synchronized (lock_) {
      value_ = value_ ^ b;
      return value_;
    }
  }

  /**
   * Compares the current value to the given int.
   *
   * @param other the value to compare against.
   * @return a negative, zero, or positive integer as the current value is less than, equal to,
   *         or greater than <code>other</code>.
   */
  public int compareTo(int other) {
    logger.debug("compareTo(other={}) - start", String.valueOf(other));
    int val = get();
    return (val < other)? -1 : (val == other)? 0 : 1;
  }

  /**
   * Compares the current value to another {@code SynchronizedInt}'s value.
   *
   * @param other the instance to compare against.
   * @return a negative, zero, or positive integer as the current value is less than, equal to,
   *         or greater than <code>other</code>'s value.
   */
  public int compareTo(SynchronizedInt other) {
     logger.debug("compareTo(other={}) - start", other);
    return compareTo(other.get());
  }

  public int compareTo(Object other) {
    logger.debug("compareTo(other={}) - start", other);
    return compareTo((SynchronizedInt)other);
  }

  public boolean equals(Object other) {
    logger.debug("equals(other={}) - start", other);
    if (other != null &&
        other instanceof SynchronizedInt)
      return get() == ((SynchronizedInt)other).get();
    else
      return false;
  }

  public int hashCode() {
    return get();
  }

  public String toString() {
    return String.valueOf(get());
  }

}

