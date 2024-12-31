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
package org.dbunit.dataset.datatype;

import java.math.BigDecimal;

import org.dbunit.dataset.datatype.ToleratedDeltaMap.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extended version of the {@link NumberDataType}. Extends the
 * {@link #compare(Object, Object)} method in order to respect precision
 * tolerance. This is comparable to the JUnit method
 * <code>assert(double val1, double val2, double toleratedDelta)</code>.
 * 
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class NumberTolerantDataType extends NumberDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(NumberTolerantDataType.class);

    private static final BigDecimal C_100 = new BigDecimal("100");

    /**
     * The allowed/tolerated difference
     */
    private Precision toleratedDelta;

    /**
     * Creates a new number tolerant datatype
     * 
     * @param name
     * @param sqlType
     * @param delta
     *            The tolerated delta to be used for the comparison
     */
    NumberTolerantDataType(String name, int sqlType, Precision delta)
    {
        super(name, sqlType);

        if (delta == null)
        {
            throw new NullPointerException(
                    "The parameter 'delta' must not be null");
        }
        this.toleratedDelta = delta;
    }

    public Precision getToleratedDelta()
    {
        return toleratedDelta;
    }

    /**
     * The only method overwritten from the base implementation to compare
     * numbers allowing a tolerance
     * 
     * @see org.dbunit.dataset.datatype.AbstractDataType#compareNonNulls(java.lang.Object,
     *      java.lang.Object)
     */
    protected int compareNonNulls(Object value1cast, Object value2cast)
            throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start",
                value1cast, value2cast);

        try
        {
            // Start of special handling
            if (value1cast instanceof BigDecimal
                    && value2cast instanceof BigDecimal)
            {
                BigDecimal bdValue1 = (BigDecimal) value1cast;
                BigDecimal bdValue2 = (BigDecimal) value2cast;
                BigDecimal diff = bdValue1.subtract(bdValue2);
                // Exact match
                if (isZero(diff))
                {
                    return 0;
                }

                BigDecimal toleratedDeltaValue = this.toleratedDelta.getDelta();
                if (!this.toleratedDelta.isPercentage())
                {
                    if (diff.abs().compareTo(toleratedDeltaValue) <= 0)
                    {
                        // within tolerance delta, so accept
                        if (logger.isDebugEnabled())
                            logger.debug(
                                    "Values val1={}, val2={} differ but are within tolerated delta {}",
                                    new Object[] {bdValue1, bdValue2,
                                            toleratedDeltaValue});
                        return 0;
                    } else
                    {
                        // TODO it would be beautiful to report a precise
                        // description about difference and tolerated delta
                        // values in the assertion
                        // Therefore think about introducing a method
                        // "DataType.getCompareInfo()"
                        return diff.signum();
                    }
                } else
                {
                    // percentage comparison
                    int scale = toleratedDeltaValue.scale() + 2;
                    BigDecimal toleratedValue =
                            bdValue1.multiply(toleratedDeltaValue.divide(C_100,
                                    scale, BigDecimal.ROUND_HALF_UP));
                    if (diff.abs().compareTo(toleratedValue) <= 0)
                    {
                        // within tolerance delta, so accept
                        if (logger.isDebugEnabled())
                            logger.debug(
                                    "Values val1={}, val2={} differ but are within tolerated delta {}",
                                    new Object[] {bdValue1, bdValue2,
                                            toleratedValue});
                        return 0;
                    } else
                    {
                        // TODO it would be beautiful to report a precise
                        // description about difference and tolerated delta
                        // values in the assertion
                        // Therefore think about introducing a method
                        // "DataType.getCompareInfo()"
                        return diff.signum();
                    }
                }

            } else
            {
                Comparable value1 = (Comparable) value1cast;
                Comparable value2 = (Comparable) value2cast;
                return value1.compareTo(value2);
            }
        } catch (ClassCastException e)
        {
            throw new TypeCastException(e);
        }
    }

    /**
     * Checks if the given value is zero.
     * 
     * @param value
     * @return <code>true</code> if and only if the given value is zero.
     */
    public static final boolean isZero(BigDecimal value)
    {
        return value.signum() == 0;
    }

}
