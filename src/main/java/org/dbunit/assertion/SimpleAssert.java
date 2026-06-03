/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dbunit's own small assertion utility, independent from the testing framework
 * that is used.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class SimpleAssert 
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SimpleAssert.class);

    private FailureHandler failureHandler;
    
    public SimpleAssert(FailureHandler failureHandler)
    {
        if (failureHandler == null) {
            throw new NullPointerException(
                    "The parameter 'failureHandler' must not be null");
        }
        this.failureHandler = failureHandler;
    }
    
    /**
     * Asserts that propertyName is not a null String and has a length greater
     * than zero.
     */
    protected void assertNotNullNorEmpty( String propertyName, String property )
    {
        logger.debug("assertNotNullNorEmpty(propertyName={}, property={}) - start", propertyName, property);

        assertTrue( propertyName + " is null", property != null );
        assertTrue( "Invalid " + propertyName, property.trim()
                .length() > 0 );
    }

    public void assertTrue(boolean condition) {
        assertTrue(null, condition);
    }
    
    /**
     * Evaluate if the given condition is <code>true</code> or not.
     * @param message message displayed if assertion is false
     * @param condition condition to be tested
     */
    public void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail( message );
        }
    }

    public void assertNotNull(Object object) {
        assertTrue(null, object!=null);
    }

    public void assertNotNull(String message, Object object) {
        assertTrue(message, object!=null);
    }
    
    public void fail(String message) {
        throw failureHandler.createFailure(message);
    }

}
