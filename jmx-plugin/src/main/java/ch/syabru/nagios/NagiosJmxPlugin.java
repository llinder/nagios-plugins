/*
 *  Copyright 2009-2010 Felix Roethenbacher
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at 
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package ch.syabru.nagios;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.ConnectException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.InvalidKeyException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Nagios JMX plugin.
 *
 * @author Felix Roethenbacher
 *
 */
public class NagiosJmxPlugin {

    /**
     * Status code NAGIOS OK.
     */
    public static final int NAGIOS_OK = 0;
    public static final String NAGIOS_OK_MSG = "JMX OK - ";
    /**
     * Status code NAGIOS WARNING.
     */
    public static final int NAGIOS_WARNING = 1; // 
    public static final String NAGIOS_WARNING_MSG = "JMX WARNING - ";
    /**
     * Status code NAGIOS CRITICAL.
     */
    public static final int NAGIOS_CRITICAL = 2; //
    public static final String NAGIOS_CRITICAL_MSG = "JMX CRITICAL - ";
    /**
     * Status code NAGIOS UNKNOWN.
     */
    public static final int NAGIOS_UNKNOWN = 3; // 
    public static final String NAGIOS_UNKNOWN_MSG = "JMX UNKNOWN - ";

    /**
     * Username system property.
     */
    public static final String PROP_USERNAME = "username";
    /**
     * Password system property.
     */
    public static final String PROP_PASSWORD = "password";
    /**
     * Object name system property.
     */
    public static final String PROP_OBJECT_NAME = "objectName";
    /**
     * Attribute name system property.
     */
    public static final String PROP_ATTRIBUTE_NAME = "attributeName";
    /**
     * Attribute key system property.
     */
    public static final String PROP_ATTRIBUTE_KEY = "attributeKey";
    /**
     * Service URL system property.
     */
    public static final String PROP_SERVICE_URL = "serviceUrl";
    /**
     * Threshold warning level system property.
     * The number format of this property has to correspond to the type of
     * the attribute object.
     */
    public static final String PROP_THRESHOLD_WARNING = "thresholdWarning";
    /**
     * Threshold critical level system property.
     * The number format of this property has to correspond the type of
     * the attribute object.
     */
    public static final String PROP_THRESHOLD_CRITICAL = "thresholdCritical";
    /**
     * Units system property.
     */
    public static final String PROP_UNITS = "units";
    /**
     * Operation to invoke on MBean.
     */
    public static final String PROP_OPERATION = "operation";
    /**
     * Verbose output.
     */
    public static final String PROP_VERBOSE = "verbose";
    /**
     * Help output.
     */
    public static final String PROP_HELP = "help";

    /**
     * Unit bytes.
     */
    public static final String UNIT_B = "B";
    /**
     * Unit kilobytes.
     */
    public static final String UNIT_KB = "KB";
    /**
     * Unit megabytes.
     */
    public static final String UNIT_MB = "MB";
    /**
     * Unit terabytes.
     */
    public static final String UNIT_TB = "TB";
    /**
     * Unit seconds.
     */
    public static final String UNIT_S = "s";
    /**
     * Unit microseconds.
     */
    public static final String UNIT_US = "us";
    /**
     * Unit milliseconds.
     */
    public static final String UNIT_MS = "ms";
    /**
     * Unit counter.
     */
    public static final String UNIT_COUNTER = "c";
    /**
     * Unit percent.
     */
    public static final String UNIT_PERCENT = "%";
    /**
     * List of valid units.
     */
    public static final List<String> UNITS = Arrays.asList(
            UNIT_B, UNIT_KB, UNIT_MB, UNIT_TB, UNIT_S, UNIT_US, UNIT_MS,
            UNIT_COUNTER, UNIT_PERCENT);

    private HashMap<MBeanServerConnection, JMXConnector> connections =
        new HashMap<MBeanServerConnection, JMXConnector>();

        /**
     * Open a connection to a MBean server.
     * @param serviceUrl Service URL, e.g. service:jmx:rmi://HOST:PORT/jndi/rmi://HOST:PORT/jmxrmi
     * @param username Username
     * @param password Password
     * @return MBeanServerConnection if succesfull.
     * @throws IOException
     */
    public MBeanServerConnection openConnection(
            JMXServiceURL serviceUrl, String username, String password)
    throws IOException, SecurityException
    {
        JMXConnector connector;
        HashMap<String, Object> environment = new HashMap<String, Object>();
        // Add environment variable to check for dead connections.
        environment.put("jmx.remote.x.client.connection.check.period", 5000);
        if (username != null && password != null) {
            environment = new HashMap<String, Object>();
            environment.put(JMXConnector.CREDENTIALS,
                    new String[] { username, password });
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        } else {
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        }
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        connections.put(connection, connector);
        return connection;
    }

    /**
     * Close JMX connection.
     * @param connection Connection.
     * @throws IOException XX.
     */
    public void closeConnection(MBeanServerConnection connection)
    throws IOException
    {
        JMXConnector connector = connections.remove(connection);
        if (connector != null)
            connector.close();
    }

    public Object query(MBeanServerConnection connection, String objectName, String attributeName,
            String attributeKey)
    throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException,
    AttributeNotFoundException, MBeanException, MalformedObjectNameException, NullPointerException
    {
        Object value = null;
        ObjectName objName = new ObjectName(objectName);
        Object attribute = connection.getAttribute(objName, attributeName);
        if (attribute instanceof CompositeDataSupport) {
            CompositeDataSupport compositeAttr = (CompositeDataSupport) attribute;
            value = compositeAttr.get(attributeKey);
        } else {
            value = attribute;
        }
        return value;
    }

    public void invoke(MBeanServerConnection connection, String objectName,
            String operationName)
    throws InstanceNotFoundException, IOException, MalformedObjectNameException,
    NullPointerException, MBeanException, ReflectionException
    {
        ObjectName objName = new ObjectName(objectName);
        connection.invoke(objName, operationName, null, null);
    }

    /**
     * Get system properties and execute query.
     * @return Nagios exit code.
     * @throws NagiosJmxPluginException XX 
     */
    public int execute(Properties args) throws NagiosJmxPluginException {
        String username = args.getProperty(PROP_USERNAME);
        String password = args.getProperty(PROP_PASSWORD);
        String objectName = args.getProperty(PROP_OBJECT_NAME);
        String attributeName = args.getProperty(PROP_ATTRIBUTE_NAME);
        String attributeKey = args.getProperty(PROP_ATTRIBUTE_KEY);
        String serviceUrl = args.getProperty(PROP_SERVICE_URL);
        String thresholdWarning = args.getProperty(PROP_THRESHOLD_WARNING);
        String thresholdCritical = args.getProperty(PROP_THRESHOLD_CRITICAL);
        String operation = args.getProperty(PROP_OPERATION);
        String units = args.getProperty(PROP_UNITS);
        String help = args.getProperty(PROP_HELP);

        if (help != null) {
            showHelp();
            return 0;
        }
        
        if (objectName == null || attributeName == null || serviceUrl == null)
        {
            showUsage();
            return 0;
        }
        
        if ( units.equals(UNIT_PERCENT) &&
        		( ( attributeKey != null && !attributeKey.contains(":") ) || ( attributeKey == null && !attributeName.contains(":") ) ) )
        {
        	showUsage();
        	return 0;
        }
        
        if (units != null && !UNITS.contains(units))
            throw new NagiosJmxPluginException("Unknown unit [" + units + "]");
            
        JMXServiceURL url = null;
        try {
            url = new JMXServiceURL(serviceUrl);
        } catch (MalformedURLException e) {
            throw new NagiosJmxPluginException("Malformed service URL [" + serviceUrl + "]", e);
        }
        // Connect to MBean server.
        MBeanServerConnection connection = null;
        Number value = null;
        try {
            try {
                connection = openConnection(url, username, password);
            } catch (ConnectException ce) {
                throw new NagiosJmxPluginException("Error opening RMI connection: " + ce.getMessage(), ce);
            } catch (Exception e) {
                throw new NagiosJmxPluginException("Error opening connection: " + e.getMessage(), e);
            }
            // Query attribute.
            try {
            	
            	if( UNIT_PERCENT.equals( units ) && attributeKey != null && attributeKey.contains(":") )
            	{
            		String[] attributeKeys = attributeKey.split(":");
            		String attributeKeyWhole = attributeKeys[0];
            		String attributeKeyPart	= attributeKeys[1];
            		Number valueWhole = (Number) query(connection, objectName, attributeName, attributeKeyWhole);
                	Number valuePart = (Number) query(connection, objectName, attributeName, attributeKeyPart);
                	value = Math.round( valuePart.doubleValue() * 100 / valueWhole.doubleValue() );
            	}
            	else if( UNIT_PERCENT.equals( units ) && attributeName != null && attributeName.contains(":") )
            	{
            		String[] attributeNames = attributeName.split(":");
            		String attributeNameWhole = attributeNames[0];
            		String attributeNamePart = attributeNames[1];
            		Number valueWhole = (Number) query(connection, objectName, attributeNameWhole, attributeKey);
                	Number valuePart = (Number) query(connection, objectName, attributeNamePart, attributeKey);
                	value = Math.round( valuePart.doubleValue() * 100 / valueWhole.doubleValue() );
            	}
            	else
            	{
            		value = (Number) query(connection, objectName, attributeName, attributeKey);
            	}
            	
            } catch (MalformedObjectNameException e) {
                throw new NagiosJmxPluginException("Malformed objectName [" + objectName + "]", e);
            } catch (InstanceNotFoundException e) {
                throw new NagiosJmxPluginException("objectName not found [" + objectName + "]", e);
            } catch (AttributeNotFoundException e) {
                throw new NagiosJmxPluginException("attributeName not found [" + attributeName + "]", e);
            } catch (InvalidKeyException e) {
                throw new NagiosJmxPluginException("attributeKey not found [" + attributeKey + "]", e);
            } catch (ClassCastException e) {
                throw new NagiosJmxPluginException(
                        "Type of value is not a number [" + value.getClass().getName() + "]");
            } catch (Exception e) {
                throw new NagiosJmxPluginException("Error querying server: " + e.getMessage(), e);
            }
            // Invoke operation if defined.
            if (operation != null) {
                try {
                    invoke(connection, objectName, operation);
                } catch (Exception e) {
                    throw new NagiosJmxPluginException("Error invoking operation [" +
                            operation + "]: " + e.getMessage(), e);
                }
            }
        } finally {
            if (connection != null) {
                try {
                    closeConnection(connection);
                } catch (Exception e) {
                    throw new NagiosJmxPluginException(
                            "Error closing JMX connection", e);
                }
            }
        }
        int exitCode;
        if (value != null) {
            if (isOverThreshold(value, thresholdCritical)) {
                System.out.print(NAGIOS_CRITICAL_MSG);
                exitCode = NAGIOS_CRITICAL;
            } else if (isOverThreshold(value, thresholdWarning)) {
                System.out.print(NAGIOS_WARNING_MSG);
                exitCode = NAGIOS_WARNING;
            } else {
                System.out.print(NAGIOS_OK_MSG);
                exitCode = NAGIOS_OK;
            }
            System.out.print(getStatusOutput(objectName, attributeName,
                    attributeKey, value, units));
            System.out.println(getPerformanceDataOutput(objectName, attributeName,
                    attributeKey, value, thresholdWarning, thresholdCritical,
                    units));
        } else {
            System.out.print(NAGIOS_WARNING_MSG);
            System.out.println("Value not set. JMX query returned null value.");
            exitCode = NAGIOS_WARNING;
        }
        return exitCode;
    }

    /**
     * Get status output.
     * @param objectName Object name.
     * @param attributeName Attribute name.
     * @param attributeKey Attribute key, or null
     * @param value Value
     * @return Formatted string for user output.
     */
    private String getStatusOutput(String objectName, String attributeName,
            String attributeKey, Number value, String units)
    {
        StringBuilder output = new StringBuilder();
        output.append(attributeName);
        if (attributeKey != null) {
            output.append(".").append(attributeKey);
        }
        output.append(" = ").append(value);
        if (units != null)
            output.append(units);
        return output.toString();
    }

    /**
     * Get performance data output.
     * @param objectName Object name.
     * @param attributeName Attribute name.
     * @param attributeKey Attribute key, or null
     * @param value Value
     * @param units Units, null if not defined.
     * @return Formatted string for user output.
     */
    private String getPerformanceDataOutput(String objectName,
            String attributeName, String attributeKey, Number value,
            String thresholdWarning, String thresholdCritical,
            String units)
    {
        StringBuilder output = new StringBuilder();
        output.append(" | '");
        output.append(attributeName);
        if (attributeKey != null) {
            output.append(" ").append(attributeKey);
        }
        output.append("'=").append(value);
        if (units != null)
            output.append(units);
        output.append(";");
        if (thresholdWarning != null)
            output.append(thresholdWarning);
        output.append(";");
        if (thresholdCritical != null)
            output.append(thresholdCritical);
        output.append(";;");
        return output.toString();
    }

    /**
     * Check if value is over threshold.
     * @param value Value, which is either Double, Long, Integer, Short, Byte,
     *        or Float.
     * @param threshold Threshold, which must be parseable in same number
     *        format as value, can be null
     * @return true if value is over threshold, false otherwise.
     * @throws NagiosJmxPluginException If number format is not parseable.
     */
    private boolean isOverThreshold(Number value, String threshold)
    throws NagiosJmxPluginException
    {
        boolean overThreshold = false;
        try {
            if (threshold == null) {
                overThreshold = false;
            } else if (value instanceof Double) {
                overThreshold = value.doubleValue() > Double.valueOf(threshold);
            } else if (value instanceof Integer) {
                overThreshold = value.intValue() > Integer.valueOf(threshold);
            } else if (value instanceof Long) {
                overThreshold = value.longValue() > Long.valueOf(threshold);
            } else if (value instanceof Short) {
                overThreshold = value.shortValue() > Short.valueOf(threshold);
            } else if (value instanceof Byte) {
                overThreshold = value.byteValue() > Byte.valueOf(threshold);
            } else if (value instanceof Float) {
                overThreshold = value.floatValue() > Float.valueOf(threshold);
            } else {
                throw new NumberFormatException(
                        "Unknown number format [" + value.getClass().getName() + "]");
            }
        } catch (NumberFormatException e) {
            throw new NagiosJmxPluginException("Error parsing value of threshold [" +
                    threshold + "]. Expected [" + value.getClass().getName() + "]", e);
        }
        return overThreshold;
    }

    /**
     * Main method.
     * @param args
     */
    public static void main(String[] args) {

        NagiosJmxPlugin plugin = new NagiosJmxPlugin();
        int exitCode;
        Properties props = parseArguments(args);
        String verbose = props.getProperty(PROP_VERBOSE);
        try {
            exitCode = plugin.execute(props);
        } catch (NagiosJmxPluginException e) {
            System.out.println(NAGIOS_CRITICAL_MSG + e.getMessage());
            if (verbose != null)
                e.printStackTrace(System.out);
            exitCode = NAGIOS_CRITICAL;
        } catch (Exception e) {
            System.out.println(NAGIOS_UNKNOWN_MSG + e.getMessage());
            if (verbose != null)
                e.printStackTrace(System.out);
            exitCode = NAGIOS_UNKNOWN;
        }
        System.exit(exitCode);
    }

    private void showUsage() throws NagiosJmxPluginException {
        outputResource(getClass().getResource("usage.txt"));
    }
    
    private void showHelp() throws NagiosJmxPluginException {
        outputResource(getClass().getResource("help.txt"));
    }
    
    private void outputResource(URL url) throws NagiosJmxPluginException {
        try {
            Reader r = new InputStreamReader(url.openStream());
            StringBuilder sbHelp = new StringBuilder();
            char[] buffer = new char[1024];
            for (int len = r.read(buffer); len != -1; len = r.read(buffer)) {
                sbHelp.append(buffer, 0, len);
            }
            System.out.println(sbHelp.toString());
            System.exit(0);
        } catch (IOException e) {
            throw new NagiosJmxPluginException(e);
        }
    }
    
    private static Properties parseArguments (String[] args) {
        Properties props = new Properties();
        for(int i=0; i<args.length; i++) {
            if("-h".equals(args[i]))
                props.put(PROP_HELP, "");
            else if ("-U".equals(args[i]))
                props.put(PROP_SERVICE_URL, args[++i]);
            else if("-O".equals(args[i]))
                props.put(PROP_OBJECT_NAME, args[++i]);
            else if ("-A".equals(args[i]))
                props.put(PROP_ATTRIBUTE_NAME, args[++i]);
            else if ("-K".equals(args[i]))
                props.put(PROP_ATTRIBUTE_KEY, args[++i]);
            else if("-v".equals(args[i]))
                props.put(PROP_VERBOSE, "true");
            else if ("-w".equals(args[i]))
                props.put(PROP_THRESHOLD_WARNING, args[++i]);
            else if ("-c".equals(args[i]))
                props.put(PROP_THRESHOLD_CRITICAL, args[++i]);
            else if ("--username".equals(args[i]))
                props.put(PROP_USERNAME, args[++i]);
            else if ("--password".equals(args[i]))
                props.put(PROP_PASSWORD, args[++i]);
            else if ("-u".equals(args[i]))
                props.put(PROP_UNITS, args[++i]);
            else if ("-o".equals(args[i]))
                props.put(PROP_OPERATION, args[++i]);
        }
        return props;
    }
}
