/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.systest.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class QpidRestTestCase extends QpidBrokerTestCase
{
    public static final String ANONYMOUS_AUTHENTICATION_PROVIDER = "testAnonymous";
    public static final String TEST1_VIRTUALHOST = "test";
    public static final String TEST2_VIRTUALHOST = "test2";
    public static final String TEST3_VIRTUALHOST = "test3";

    public static final String[] EXPECTED_VIRTUALHOSTS = { TEST1_VIRTUALHOST, TEST2_VIRTUALHOST, TEST3_VIRTUALHOST};
    public static final String[] EXPECTED_QUEUES = { "queue", "ping" };
    public static final String[] EXPECTED_EXCHANGES = { "amq.fanout", "amq.match", "amq.direct","amq.topic" };

    private RestTestHelper _restTestHelper = new RestTestHelper(findFreePort());

    @Override
    public void setUp() throws Exception
    {
        // use webadmin account to perform tests
        getRestTestHelper().setUsernameAndPassword("webadmin", "webadmin");

        //remove the normal 'test' vhost, we will configure the vhosts below
        getBrokerConfiguration(0).removeObjectConfiguration(TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);

        // Set up virtualhost config with queues and bindings to the amq.direct
        for (String virtualhost : EXPECTED_VIRTUALHOSTS)
        {
            createTestVirtualHost(0, virtualhost);

            for (String queue : EXPECTED_QUEUES)
            {
                setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + virtualhost + ".queues.exchange", "amq.direct");
                setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + virtualhost + ".queues.queue(-1).name", queue);
            }
        }

        customizeConfiguration();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            getRestTestHelper().tearDown();
        }
    }

    protected void customizeConfiguration() throws ConfigurationException, IOException
    {
        TestBrokerConfiguration config = getBrokerConfiguration();
        config.addHttpManagementConfiguration();
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.PORT, _restTestHelper.getHttpPort());
        config.removeObjectConfiguration(TestBrokerConfiguration.ENTRY_NAME_JMX_PORT);
        config.removeObjectConfiguration(TestBrokerConfiguration.ENTRY_NAME_RMI_PORT);

        Map<String, Object> anonymousProviderAttributes = new HashMap<String, Object>();
        anonymousProviderAttributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);
        anonymousProviderAttributes.put(AuthenticationProvider.NAME, ANONYMOUS_AUTHENTICATION_PROVIDER);
        config.addAuthenticationProviderConfiguration(anonymousProviderAttributes);

        // set password authentication provider on http port for the tests
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    public RestTestHelper getRestTestHelper()
    {
        return _restTestHelper;
    }
}
