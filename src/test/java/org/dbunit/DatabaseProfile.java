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

package org.dbunit;

import java.util.*;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 20, 2002
 */
public class DatabaseProfile
{
    private static final String[] EMPTY_ARRAY = new String[0];

    public static final String DATABASE_PROFILE = "dbunit.profile";
    private static final String PROFILE_DRIVER_CLASS =
            "dbunit.profile.driverClass";
    private static final String PROFILE_URL = "dbunit.profile.url";
    private static final String PROFILE_SCHEMA = "dbunit.profile.schema";
    private static final String PROFILE_USER = "dbunit.profile.user";
    private static final String PROFILE_PASSWORD = "dbunit.profile.password";
    private static final String PROFILE_UNSUPPORTED_FEATURES =
            "dbunit.profile.unsupportedFeatures";
    private static final String PROFILE_DDL = "dbunit.profile.ddl";
    private static final String PROFILE_MULTILINE_SUPPORT =
            "dbunit.profile.multiLineSupport";

    private final Properties _properties;

    public DatabaseProfile(Properties properties)
    {
        _properties = properties;
        // ArrayList keys = new ArrayList(properties.keySet());
        // for (int i = 0; i < keys.size(); i++) {
        // System.out.println("key = " + keys.get(i) + ", value = " +
        // properties.get(keys.get(i)));
        // }

    }

    public String getActiveProfile()
    {
        return _properties.getProperty(DATABASE_PROFILE);
    }

    public String getDriverClass()
    {
        return _properties.getProperty(PROFILE_DRIVER_CLASS);
    }

    public String getConnectionUrl()
    {
        return _properties.getProperty(PROFILE_URL);
    }

    public String getSchema()
    {
        return _properties.getProperty(PROFILE_SCHEMA, null);
    }

    public String getUser()
    {
        return _properties.getProperty(PROFILE_USER);
    }

    public String getPassword()
    {
        return _properties.getProperty(PROFILE_PASSWORD);
    }

    public String getProfileDdl()
    {
        return _properties.getProperty(PROFILE_DDL);
    }

    public boolean getProfileMultilineSupport()
    {
        return Boolean
                .valueOf(_properties.getProperty(PROFILE_MULTILINE_SUPPORT));
    }

    public String[] getUnsupportedFeatures()
    {
        String property = _properties.getProperty(PROFILE_UNSUPPORTED_FEATURES);

        // If property is not set return an empty array
        if (property == null)
        {
            return EMPTY_ARRAY;
        }

        List<String> stringList = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(property, ",");
        while (tokenizer.hasMoreTokens())
        {
            stringList.add(tokenizer.nextToken().trim());
        }
        return stringList.toArray(new String[stringList.size()]);
    }

}
