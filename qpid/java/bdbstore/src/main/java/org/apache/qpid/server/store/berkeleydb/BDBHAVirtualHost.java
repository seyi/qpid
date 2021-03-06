package org.apache.qpid.server.store.berkeleydb;
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

import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.OperationalLoggingListener;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.DefaultUpgraderProvider;
import org.apache.qpid.server.virtualhost.State;
import org.apache.qpid.server.virtualhost.VirtualHostConfigRecoveryHandler;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BDBHAVirtualHost extends AbstractVirtualHost
{
    private BDBHAMessageStore _messageStore;

    private boolean _inVhostInitiatedClose;

    BDBHAVirtualHost(VirtualHostRegistry virtualHostRegistry,
                     StatisticsGatherer brokerStatisticsGatherer,
                     org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                     VirtualHostConfiguration hostConfig,
                     VirtualHost virtualHost)
            throws Exception
    {
        super(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, hostConfig, virtualHost);
    }



    protected void initialiseStorage(VirtualHostConfiguration hostConfig, VirtualHost virtualHost) throws Exception
    {
        _messageStore = new BDBHAMessageStore();

        final MessageStoreLogSubject storeLogSubject =
                new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());
        OperationalLoggingListener.listen(_messageStore, storeLogSubject);

        _messageStore.addEventListener(new BeforeActivationListener(), Event.BEFORE_ACTIVATE);
        _messageStore.addEventListener(new AfterActivationListener(), Event.AFTER_ACTIVATE);
        _messageStore.addEventListener(new BeforeCloseListener(), Event.BEFORE_CLOSE);



        _messageStore.addEventListener(new AfterInitialisationListener(), Event.AFTER_INIT);
        _messageStore.addEventListener(new BeforePassivationListener(), Event.BEFORE_PASSIVATE);

        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this, getExchangeRegistry(), getExchangeFactory());
        DurableConfigurationRecoverer configRecoverer =
                new DurableConfigurationRecoverer(getName(), getDurableConfigurationRecoverers(),
                                                  new DefaultUpgraderProvider(this, getExchangeRegistry()));

        _messageStore.configureConfigStore(getName(),
                configRecoverer,
                virtualHost);

        _messageStore.configureMessageStore(getName(),
                recoveryHandler,
                recoveryHandler
        );
    }


    protected void closeStorage()
    {
        //Close MessageStore
        if (_messageStore != null)
        {
            //Remove MessageStore Interface should not throw Exception
            try
            {
                _inVhostInitiatedClose = true;
                getMessageStore().close();
            }
            catch (Exception e)
            {
                getLogger().error("Failed to close message store", e);
            }
            finally
            {
                _inVhostInitiatedClose = false;
            }
        }
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _messageStore;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    private final class AfterInitialisationListener implements EventListener
    {
        public void event(Event event)
        {
            setState(State.PASSIVE);
        }

    }

    private final class BeforePassivationListener implements EventListener
    {
        public void event(Event event)
        {
            State finalState = State.ERRORED;

            try
            {
                    /* the approach here is not ideal as there is a race condition where a
                     * queue etc could be created while the virtual host is on the way to
                     * the passivated state.  However the store state change from MASTER to UNKNOWN
                     * is documented as exceptionally rare..
                     */

                getConnectionRegistry().close(IConnectionRegistry.VHOST_PASSIVATE_REPLY_TEXT);
                removeHouseKeepingTasks();

                getQueueRegistry().stopAllAndUnregisterMBeans();
                getExchangeRegistry().clearAndUnregisterMbeans();
                getDtxRegistry().close();

                finalState = State.PASSIVE;
            }
            finally
            {
                setState(finalState);
                reportIfError(getState());
            }
        }

    }


    private final class BeforeActivationListener implements EventListener
    {
        @Override
        public void event(Event event)
        {
            try
            {
                initialiseModel(getConfiguration());
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to initialise virtual host after state change", e);
            }
        }
    }

    private final class AfterActivationListener implements EventListener
    {
        @Override
        public void event(Event event)
        {
            attainActivation();
        }
    }

    private final class BeforeCloseListener implements EventListener
    {
        @Override
        public void event(Event event)
        {
            if(!_inVhostInitiatedClose)
            {
                shutdownHouseKeeping();
            }

        }
    }

}
