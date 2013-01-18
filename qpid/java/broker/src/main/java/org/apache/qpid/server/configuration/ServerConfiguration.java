/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.apache.qpid.server.configuration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.AbstractConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

public class ServerConfiguration extends AbstractConfiguration
{
    protected static final Logger _logger = Logger.getLogger(ServerConfiguration.class);

    // Default Configuration values
    public static final int DEFAULT_BUFFER_SIZE = 262144;
    public static final String SECURITY_CONFIG_RELOADED = "SECURITY CONFIGURATION RELOADED";

    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_SSL_PORT = 5671;
    public static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;
    public static final int DEFAULT_JMXPORT_REGISTRYSERVER = 8999;
    public static final int JMXPORT_CONNECTORSERVER_OFFSET = 100;
    public static final int DEFAULT_HTTP_MANAGEMENT_PORT = 8080;
    public static final int DEFAULT_HTTPS_MANAGEMENT_PORT = 8443;
    public static final long DEFAULT_MINIMUM_ALERT_REPEAT_GAP = 30000l;

    public static final String USE_CUSTOM_RMI_SOCKET_FACTORY = BrokerProperties.PROPERTY_USE_CUSTOM_RMI_SOCKET_FACTORY;

    public static final String QPID_HOME = "QPID_HOME";
    public static final String QPID_WORK = "QPID_WORK";
    public static final String LIB_DIR = "lib";

    private Map<String, Configuration> _virtualHosts = new HashMap<String, Configuration>();

    private File _configFile;
    private File _vhostsFile;

    // Map of environment variables to config items
    private static final Map<String, String> envVarMap = new HashMap<String, String>();

    // Configuration values to be read from the configuration file
    //todo Move all properties to static values to ensure system testing can be performed.
    public static final String MGMT_JMXPORT_REGISTRYSERVER = "management.jmxport.registryServer";
    public static final String MGMT_JMXPORT_CONNECTORSERVER = "management.jmxport.connectorServer";
    public static final String SECURITY_DEFAULT_AUTH_MANAGER = "security.default-auth-manager";
    public static final String SECURITY_PORT_MAPPINGS_PORT_MAPPING_AUTH_MANAGER = "security.port-mappings.port-mapping.auth-manager";
    public static final String SECURITY_PORT_MAPPINGS_PORT_MAPPING_PORT = "security.port-mappings.port-mapping.port";
    public static final String CONNECTOR_AMQP10ENABLED = "connector.amqp10enabled";
    public static final String CONNECTOR_AMQP010ENABLED = "connector.amqp010enabled";
    public static final String CONNECTOR_AMQP091ENABLED = "connector.amqp091enabled";
    public static final String CONNECTOR_AMQP09ENABLED = "connector.amqp09enabled";
    public static final String CONNECTOR_AMQP08ENABLED = "connector.amqp08enabled";
    public static final String CONNECTOR_INCLUDE_10 = "connector.include10";
    public static final String CONNECTOR_INCLUDE_010 = "connector.include010";
    public static final String CONNECTOR_INCLUDE_091 = "connector.include091";
    public static final String CONNECTOR_INCLUDE_09 = "connector.include09";
    public static final String CONNECTOR_INCLUDE_08 = "connector.include08";

    {
        envVarMap.put(BrokerProperties.PROPERTY_MSG_AUTH, "security.msg-auth");
    }

    /**
     * Loads the given file and sets up the HUP signal handler.
     *
     * This will load the file and present the root level properties but will
     * not perform any virtualhost configuration.
     * <p>
     * To perform this {@link #initialise()} must be called.
     * <p>
     * This has been made a two step process to allow the Plugin Manager and
     * Configuration Manager to be initialised in the Application Registry.
     * <p>
     * If using this ServerConfiguration via an ApplicationRegistry there is no
     * need to explicitly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param configurationURL
     * @throws org.apache.commons.configuration.ConfigurationException
     */
    public ServerConfiguration(File configurationURL) throws ConfigurationException
    {
        this(XmlConfigurationUtilities.parseConfig(configurationURL, envVarMap));
        _configFile = configurationURL;
    }

    /**
     * Wraps the given Commons Configuration as a ServerConfiguration.
     *
     * Mainly used during testing and in locations where configuration is not
     * desired but the interface requires configuration.
     * <p>
     * If the given configuration has VirtualHost configuration then
     * {@link #initialise()} must be called to perform the required setup.
     * <p>
     * This has been made a two step process to allow the Plugin Manager and
     * Configuration Manager to be initialised in the Application Registry.
     * <p>
     * If using this ServerConfiguration via an ApplicationRegistry there is no
     * need to explicitly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param conf
     */
    public ServerConfiguration(Configuration conf)
    {
        setConfig(conf);
    }

    /**
     * Processes this configuration and setups any VirtualHosts defined in the
     * configuration.
     *
     * <p>
     * Called by {@link ApplicationRegistry#initialise()}.
     * <p>
     * NOTE: A DEFAULT ApplicationRegistry must exist when using this method
     * or a new ApplicationRegistry will be created.
     *
     * @throws ConfigurationException
     */
    public void initialise() throws ConfigurationException
    {
        setConfiguration("", getConfig());
        setupVirtualHosts(getConfig());
    }

    public String[] getElementsProcessed()
    {
        return new String[] { "" };
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        // Support for security.jmx.access was removed when JMX access rights were incorporated into the main ACL.
        // This ensure that users remove the element from their configuration file.

        if (getListValue("security.jmx.access").size() > 0)
        {
            String message = "Validation error : security/jmx/access is no longer a supported element within the configuration xml."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        if (getListValue("security.jmx.principal-database").size() > 0)
        {
            String message = "Validation error : security/jmx/principal-database is no longer a supported element within the configuration xml."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        if (getListValue("security.principal-databases.principal-database(0).class").size() > 0)
        {
            String message = "Validation error : security/principal-databases is no longer supported within the configuration xml."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        // QPID-3266.  Tidy up housekeeping configuration option for scheduling frequency
        if (contains("housekeeping.expiredMessageCheckPeriod"))
        {
            String message = "Validation error : housekeeping/expiredMessageCheckPeriod must be replaced by housekeeping/checkPeriod."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        String[] ports = getConfig().getStringArray(SECURITY_PORT_MAPPINGS_PORT_MAPPING_PORT);
        String[] authManagers = getConfig().getStringArray(SECURITY_PORT_MAPPINGS_PORT_MAPPING_AUTH_MANAGER);
        if (ports.length != authManagers.length)
        {
            throw new ConfigurationException("Validation error: Each port-mapping must have exactly one port and exactly one auth-manager.");
        }

        // QPID-3517: Inconsistency in capitalisation in the SSL configuration keys used within the connector and management configuration
        // sections. For the moment, continue to understand both but generate a deprecated warning if the less preferred keystore is used.
        for (String key : new String[] {"management.ssl.keystorePath",
                "management.ssl.keystorePassword," +
                "connector.ssl.keystorePath",
                "connector.ssl.keystorePassword"})
        {
            if (contains(key))
            {
                final String deprecatedXpath = key.replaceAll("\\.", "/");
                final String preferredXpath = deprecatedXpath.replaceAll("keystore", "keyStore");
                _logger.warn("Validation warning: " + deprecatedXpath + " is deprecated and must be replaced by " + preferredXpath
                        + (_configFile == null ? "" : " Configuration file : " + _configFile));
            }
        }

        // QPID-3739 certType was a misleading name.
        if (contains("connector.ssl.certType"))
        {
            _logger.warn("Validation warning: connector/ssl/certType is deprecated and must be replaced by connector/ssl/keyManagerFactoryAlgorithm"
                    + (_configFile == null ? "" : " Configuration file : " + _configFile));
        }
    }

    /*
     * Modified to enforce virtualhosts configuration in external file or main file, but not
     * both, as a fix for QPID-2360 and QPID-2361.
     */
    @SuppressWarnings("unchecked")
    protected void setupVirtualHosts(Configuration conf) throws ConfigurationException
    {
        List<String> vhostFiles = (List) conf.getList("virtualhosts");
        Configuration vhostConfig = conf.subset("virtualhosts");

        // Only one configuration mechanism allowed
        if (!vhostFiles.isEmpty() && !vhostConfig.subset("virtualhost").isEmpty())
        {
            throw new ConfigurationException("Only one of external or embedded virtualhosts configuration allowed.");
        }

        // We can only have one vhosts XML file included
        if (vhostFiles.size() > 1)
        {
            throw new ConfigurationException("Only one external virtualhosts configuration file allowed, multiple filenames found.");
        }

        // Virtualhost configuration object
        Configuration vhostConfiguration = new HierarchicalConfiguration();

        // Load from embedded configuration if possible
        if (!vhostConfig.subset("virtualhost").isEmpty())
        {
            vhostConfiguration = vhostConfig;
        }
        else
        {
	    	// Load from the external configuration if possible
	    	for (String fileName : vhostFiles)
	        {
	            // Open the vhosts XML file and copy values from it to our config
                _vhostsFile = new File(fileName);
                if (!_vhostsFile.exists())
                {
                    throw new ConfigurationException("Virtualhosts file does not exist");
                }
                vhostConfiguration = XmlConfigurationUtilities.parseConfig(new File(fileName), envVarMap);

                // save the default virtualhost name
                String defaultVirtualHost = vhostConfiguration.getString("default");
                getConfig().setProperty("virtualhosts.default", defaultVirtualHost);
            }
        }

        // Now extract the virtual host names from the configuration object
        List hosts = vhostConfiguration.getList("virtualhost.name");
        for (int j = 0; j < hosts.size(); j++)
        {
            String name = (String) hosts.get(j);

            // Add the virtual hosts to the server configuration
            _virtualHosts.put(name, vhostConfiguration.subset("virtualhost." + escapeTagName(name)));
        }
    }

    public String getConfigurationURL()
    {
        return _configFile == null ? "" : _configFile.getAbsolutePath();
    }

    public void setJMXPortRegistryServer(int registryServerPort)
    {
        getConfig().setProperty(MGMT_JMXPORT_REGISTRYSERVER, registryServerPort);
    }

    public int getJMXPortRegistryServer()
    {
        return getIntValue(MGMT_JMXPORT_REGISTRYSERVER, DEFAULT_JMXPORT_REGISTRYSERVER);
    }

    public void setJMXPortConnectorServer(int connectorServerPort)
    {
        getConfig().setProperty(MGMT_JMXPORT_CONNECTORSERVER, connectorServerPort);
    }

    public int getJMXConnectorServerPort()
    {
        return getIntValue(MGMT_JMXPORT_CONNECTORSERVER, getJMXPortRegistryServer() + JMXPORT_CONNECTORSERVER_OFFSET);
    }

    public boolean getPlatformMbeanserver()
    {
        return getBooleanValue("management.platform-mbeanserver", true);
    }

    public boolean getHTTPManagementEnabled()
    {
        return getBooleanValue("management.http.enabled", true);
    }

    public int getHTTPManagementPort()
    {
        return getIntValue("management.http.port", DEFAULT_HTTP_MANAGEMENT_PORT);
    }

    public boolean getHTTPManagementBasicAuth()
    {
        return getBooleanValue("management.http.basic-auth", false);
    }

    /**
     * @return value in seconds
     */
    public int getHTTPManagementSessionTimeout()
    {
        return getIntValue("management.http.session-timeout", 60 * 15);
    }

    public boolean getHTTPSManagementEnabled()
    {
        return getBooleanValue("management.https.enabled", false);
    }

    public int getHTTPSManagementPort()
    {
        return getIntValue("management.https.port", DEFAULT_HTTPS_MANAGEMENT_PORT);
    }

    public boolean getHTTPSManagementBasicAuth()
    {
        return getBooleanValue("management.https.basic-auth", true);
    }

    public boolean getHTTPManagementSaslAuthEnabled()
    {
        return getBooleanValue("management.http.sasl-auth", true);
    }

    public boolean getHTTPSManagementSaslAuthEnabled()
    {
        return getBooleanValue("management.https.sasl-auth", true);
    }

    public String[] getVirtualHostsNames()
    {
        return _virtualHosts.keySet().toArray(new String[_virtualHosts.size()]);
    }

    public Configuration getVirtualHostConfig(String name)
    {
        return _virtualHosts.get(name);
    }

    public void setVirtualHostConfig(String name, Configuration config)
    {
        _virtualHosts.put(name, config);
    }

    public String getDefaultAuthenticationManager()
    {
        return getStringValue(SECURITY_DEFAULT_AUTH_MANAGER);
    }

    public Map<Integer, String> getPortAuthenticationMappings()
    {
        String[] ports = getConfig().getStringArray(SECURITY_PORT_MAPPINGS_PORT_MAPPING_PORT);
        String[] authManagers = getConfig().getStringArray(SECURITY_PORT_MAPPINGS_PORT_MAPPING_AUTH_MANAGER);

        Map<Integer,String> portMappings = new HashMap<Integer, String>();
        for(int i = 0; i < ports.length; i++)
        {
            portMappings.put(Integer.valueOf(ports[i]), authManagers[i]);
        }

        return portMappings;
    }


    public String getManagementKeyStorePath()
    {
        // note difference in capitalisation used in fallback
        final String fallback = getStringValue("management.ssl.keystorePath");
        return getStringValue("management.ssl.keyStorePath", fallback);
    }

    public boolean getManagementSSLEnabled()
    {
        return getBooleanValue("management.ssl.enabled", false);
    }

    public String getManagementKeyStorePassword()
    {
        // note difference in capitalisation used in fallback
        final String fallback = getStringValue("management.ssl.keystorePassword");
        return getStringValue("management.ssl.keyStorePassword", fallback);
    }

    public boolean getJMXManagementEnabled()
    {
        return getBooleanValue("management.enabled", true);
    }

    public int getHeartBeatDelay()
    {
        return getIntValue("heartbeat.delay", 5);
    }

    @Deprecated
    public long getMaximumMessageAge()
    {
        return getLongValue("maximumMessageAge");
    }

    @Deprecated
    public long getMaximumMessageCount()
    {
        return getLongValue("maximumMessageCount");
    }

    @Deprecated
    public long getMaximumQueueDepth()
    {
        return getLongValue("maximumQueueDepth");
    }

    @Deprecated
    public long getMaximumMessageSize()
    {
        return getLongValue("maximumMessageSize");
    }

    @Deprecated
    public long getMinimumAlertRepeatGap()
    {
        return getLongValue("minimumAlertRepeatGap", DEFAULT_MINIMUM_ALERT_REPEAT_GAP);
    }

    @Deprecated
    public long getCapacity()
    {
        return getLongValue("capacity");
    }

    @Deprecated
    public long getFlowResumeCapacity()
    {
        return getLongValue("flowResumeCapacity", getCapacity());
    }

    public List getPorts()
    {
        return getListValue("connector.port", Collections.<Integer>singletonList(DEFAULT_PORT));
    }

    public List getPortExclude10()
    {
        return getListValue("connector.non10port");
    }

    public List getPortExclude010()
    {
        return getListValue("connector.non010port");
    }

    public List getPortExclude091()
    {
        return getListValue("connector.non091port");
    }

    public List getPortExclude09()
    {
        return getListValue("connector.non09port");
    }

    public List getPortExclude08()
    {
        return getListValue("connector.non08port");
    }

    public List getPortInclude08()
    {
        return getListValue(CONNECTOR_INCLUDE_08);
    }

    public List getPortInclude09()
    {
        return getListValue(CONNECTOR_INCLUDE_09);
    }

    public List getPortInclude091()
    {
        return getListValue(CONNECTOR_INCLUDE_091);
    }

    public List getPortInclude010()
    {
        return getListValue(CONNECTOR_INCLUDE_010);
    }

    public List getPortInclude10()
    {
        return getListValue(CONNECTOR_INCLUDE_10);
    }

    public String getBind()
    {
        return getStringValue("connector.bind", WILDCARD_ADDRESS);
    }

    public int getReceiveBufferSize()
    {
        return getIntValue("connector.socketReceiveBuffer", DEFAULT_BUFFER_SIZE);
    }

    public int getWriteBufferSize()
    {
        return getIntValue("connector.socketWriteBuffer", DEFAULT_BUFFER_SIZE);
    }

    public boolean getTcpNoDelay()
    {
        return getBooleanValue("connector.tcpNoDelay", true);
    }

    public boolean getEnableSSL()
    {
        return getBooleanValue("connector.ssl.enabled");
    }

    public boolean getSSLOnly()
    {
        return getBooleanValue("connector.ssl.sslOnly");
    }

    public List getSSLPorts()
    {
        return getListValue("connector.ssl.port", Collections.<Integer>singletonList(DEFAULT_SSL_PORT));
    }

    public String getConnectorKeyStorePath()
    {
        final String fallback = getStringValue("connector.ssl.keystorePath"); // pre-0.13 broker supported this name.
        return getStringValue("connector.ssl.keyStorePath", fallback);
    }

    public String getConnectorKeyStorePassword()
    {
        final String fallback = getStringValue("connector.ssl.keystorePassword"); // pre-0.13 brokers supported this name.
        return getStringValue("connector.ssl.keyStorePassword", fallback);
    }

    public String getConnectorKeyStoreType()
    {
        return getStringValue("connector.ssl.keyStoreType", "JKS");
    }

    public String getConnectorKeyManagerFactoryAlgorithm()
    {
        final String systemFallback = KeyManagerFactory.getDefaultAlgorithm();
        // deprecated, pre-0.17 brokers supported this name.
        final String fallback = getStringValue("connector.ssl.certType", systemFallback);
        return getStringValue("connector.ssl.keyManagerFactoryAlgorithm", fallback);
    }

    public String getConnectorTrustStorePath()
    {
        return getStringValue("connector.ssl.trustStorePath", null);
    }

    public String getConnectorTrustStorePassword()
    {
        return getStringValue("connector.ssl.trustStorePassword", null);
    }

    public String getConnectorTrustStoreType()
    {
        return getStringValue("connector.ssl.trustStoreType", "JKS");
    }

    public String getConnectorTrustManagerFactoryAlgorithm()
    {
        return getStringValue("connector.ssl.trustManagerFactoryAlgorithm", TrustManagerFactory.getDefaultAlgorithm());
    }

    public String getCertAlias()
    {
        return getStringValue("connector.ssl.certAlias", null);
    }

    public boolean needClientAuth()
    {
        return getConfig().getBoolean("connector.ssl.needClientAuth", false);
    }

    public boolean wantClientAuth()
    {
        return getConfig().getBoolean("connector.ssl.wantClientAuth", false);
    }

    public String getDefaultVirtualHost()
    {
        return getStringValue("virtualhosts.default");
    }

    public void setDefaultVirtualHost(String vhost)
    {
         getConfig().setProperty("virtualhosts.default", vhost);
    }

    public void setHousekeepingCheckPeriod(long value)
    {
        getConfig().setProperty("housekeeping.checkPeriod", value);
    }

    @Deprecated
    public long getHousekeepingCheckPeriod()
    {
        return getLongValue("housekeeping.checkPeriod", DEFAULT_HOUSEKEEPING_PERIOD);
    }

    public long getStatisticsReportingPeriod()
    {
        return getConfig().getLong("statistics.reporting.period", 0L);
    }

    public boolean isStatisticsReportResetEnabled()
    {
        return getConfig().getBoolean("statistics.reporting.reset", false);
    }

    public int getMaxChannelCount()
    {
        return getIntValue("maximumChannelCount", 256);
    }

    public boolean getManagementRightsInferAllAccess()
    {
        return getBooleanValue("management.managementRightsInferAllAccess", true);
    }

    @Deprecated
    public int getMaxDeliveryCount()
    {
        return getConfig().getInt("maximumDeliveryCount", 0);
    }

    /**
     * Check if dead letter queue delivery is enabled, defaults to disabled if not set.
     */
    @Deprecated
    public boolean isDeadLetterQueueEnabled()
    {
        return getConfig().getBoolean("deadLetterQueues", false);
    }

    /**
     * String to affix to end of queue name when generating an alternate exchange for DLQ purposes.
     */
    @Deprecated
    public String getDeadLetterExchangeSuffix()
    {
        return getConfig().getString("deadLetterExchangeSuffix", DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
    }

    /**
     * String to affix to end of queue name when generating a queue for DLQ purposes.
     */
    @Deprecated
    public String getDeadLetterQueueSuffix()
    {
        return getConfig().getString("deadLetterQueueSuffix", AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
    }

    public boolean isAmqp10enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP10ENABLED, true);
    }

    public boolean isAmqp010enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP010ENABLED, true);
    }

    public boolean isAmqp091enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP091ENABLED, true);
    }

    public boolean isAmqp09enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP09ENABLED, true);
    }

    public boolean isAmqp08enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP08ENABLED, true);
    }

    public File getVirtualHostsFile()
    {
        return _vhostsFile;
    }

}
