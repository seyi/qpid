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
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class AnonymousAccessRestTest extends QpidRestTestCase
{
    @Override
    public void startBroker()
    {
        // prevent broker from starting in setUp
    }

    public void startBrokerNow() throws Exception
    {
        super.startBroker();
    }

    @Override
    protected void customizeConfiguration() throws ConfigurationException, IOException
    {
        super.customizeConfiguration();
        TestBrokerConfiguration config = getBrokerConfiguration();

        Map<String, Object> anonymousAuthProviderAttributes = new HashMap<String, Object>();
        anonymousAuthProviderAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);
        anonymousAuthProviderAttributes.put(AuthenticationProvider.NAME, TestBrokerConfiguration.ENTRY_NAME_ANONYMOUS_PROVIDER);
        config.addAuthenticationProviderConfiguration(anonymousAuthProviderAttributes);

        // set anonymous authentication provider on http port for the tests
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_ANONYMOUS_PROVIDER);
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, false);

        // reset credentials
        getRestTestHelper().setUsernameAndPassword(null, null);
    }

    public void testGetWithAnonymousProvider() throws Exception
    {
        startBrokerNow();

        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");
        assertNotNull("Unexpected broker attributes", brokerDetails);
        assertNotNull("Unexpected value of attribute " + Broker.ID, brokerDetails.get(Broker.ID));
    }

    public void testPutAnonymousProvider() throws Exception
    {
        startBrokerNow();

        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, TEST3_VIRTUALHOST);

        int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", brokerAttributes);
        assertEquals("Unexpected update response", 200, response);

        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");
        assertNotNull("Unexpected broker attributes", brokerDetails);
        assertNotNull("Unexpected value of attribute " + Broker.ID, brokerDetails.get(Broker.ID));
        assertEquals("Unexpected default virtual host", TEST3_VIRTUALHOST, brokerDetails.get(Broker.DEFAULT_VIRTUAL_HOST));
    }

    public void testGetWithPasswordAuthProvider() throws Exception
    {
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        startBrokerNow();

        int response = getRestTestHelper().submitRequest("/rest/broker", "GET", null);
        assertEquals("Anonymous access should be denied", 401, response);
    }

    public void testPutWithPasswordAuthProvider() throws Exception
    {
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        startBrokerNow();

        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, TEST3_VIRTUALHOST);

        int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", brokerAttributes);
        assertEquals("Anonymous access should be denied", 401, response);
    }
}
