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
package org.apache.qpid.server.model.adapter;

import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.login.AccountNotFoundException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.MapValueConverter;

public abstract class AuthenticationProviderAdapter<T extends AuthenticationManager> extends AbstractAdapter implements AuthenticationProvider
{
    private static final Logger LOGGER = Logger.getLogger(AuthenticationProviderAdapter.class);

    protected T _authManager;
    protected final Broker _broker;

    protected Collection<String> _supportedAttributes;
    protected Map<String, AuthenticationManagerFactory> _factories;
    private AtomicReference<State> _state;

    private AuthenticationProviderAdapter(UUID id, Broker broker, final T authManager, Map<String, Object> attributes, Collection<String> attributeNames)
    {
        super(id, null, null, broker.getTaskExecutor());
        _authManager = authManager;
        _broker = broker;
        _supportedAttributes = createSupportedAttributes(attributeNames);
        _factories = getAuthenticationManagerFactories();

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
        addParent(Broker.class, broker);

        // set attributes now after all attribute names are known
        if (attributes != null)
        {
            for (String name : _supportedAttributes)
            {
                if (attributes.containsKey(name))
                {
                    changeAttribute(name, null, attributes.get(name));
                }
            }
        }
    }

    T getAuthManager()
    {
        return _authManager;
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostPortBindings()
    {
        return Collections.emptyList();
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(AuthenticationProvider.NAME);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getActualState()
    {
        return _state.get();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return 0;
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return _supportedAttributes;
    }

    @Override
    public Object getAttribute(String name)
    {
        if(CREATED.equals(name))
        {
            // TODO
        }
        else if(DURABLE.equals(name))
        {
            return true;
        }
        else if(ID.equals(name))
        {
            return getId();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(STATE.equals(name))
        {
            return getActualState();
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if(UPDATED.equals(name))
        {
            // TODO
        }
        return super.getAttribute(name);
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public boolean setState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        State state = _state.get();
        if(desiredState == State.DELETED)
        {
            String providerName = getName();

            // verify that provider is not in use
            Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
            for (Port port : ports)
            {
                if (providerName.equals(port.getAttribute(Port.AUTHENTICATION_PROVIDER)))
                {
                    throw new IntegrityViolationException("Authentication provider '" + providerName + "' is set on port " + port.getName());
                }
            }

            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED  || state == State.ERRORED)
                    && _state.compareAndSet(state, State.DELETED))
            {
                _authManager.close();
                _authManager.onDelete();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot delete authentication provider in state: " + state);
            }
        }
        else if(desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED || state == State.STOPPED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    _authManager.initialise();
                    return true;
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate authentication provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Cannot activate authentication provider in state: " + state);
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        else if(desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                _authManager.close();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop authentication provider in state: " + state);
            }
        }

        return false;
    }

    @Override
    public SubjectCreator getSubjectCreator()
    {
        return new SubjectCreator(_authManager, _broker.getGroupProviders());
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> effectiveAttributes = super.generateEffectiveAttributes(attributes);
        AuthenticationManager manager = validateAttributes(effectiveAttributes);
        manager.initialise();
        super.changeAttributes(attributes);
        _authManager = (T)manager;

        // if provider was previously in ERRORED state then set its state to ACTIVE
        _state.compareAndSet(State.ERRORED, State.ACTIVE);
    }

    private Map<String, AuthenticationManagerFactory> getAuthenticationManagerFactories()
    {
        QpidServiceLoader<AuthenticationManagerFactory> loader = new QpidServiceLoader<AuthenticationManagerFactory>();
        Iterable<AuthenticationManagerFactory> factories = loader.atLeastOneInstanceOf(AuthenticationManagerFactory.class);
        Map<String, AuthenticationManagerFactory> factoryMap = new HashMap<String, AuthenticationManagerFactory>();
        for (AuthenticationManagerFactory factory : factories)
        {
            factoryMap.put(factory.getType(), factory);
        }
        return factoryMap;
    }

    protected Collection<String> createSupportedAttributes(Collection<String> factoryAttributes)
    {
        List<String> attributesNames = new ArrayList<String>(AVAILABLE_ATTRIBUTES);
        if (factoryAttributes != null)
        {
            attributesNames.addAll(factoryAttributes);
        }
        return Collections.unmodifiableCollection(attributesNames);
    }

    protected AuthenticationManager validateAttributes(Map<String, Object> attributes)
    {
        super.validateChangeAttributes(attributes);

        String newName = (String)attributes.get(NAME);
        String currentName = getName();
        if (!currentName.equals(newName))
        {
            throw new IllegalConfigurationException("Changing the name of authentication provider is not supported");
        }
        String newType = (String)attributes.get(AuthenticationManagerFactory.ATTRIBUTE_TYPE);
        String currentType = (String)getAttribute(AuthenticationManagerFactory.ATTRIBUTE_TYPE);
        if (!currentType.equals(newType))
        {
            throw new IllegalConfigurationException("Changing the type of authentication provider is not supported");
        }
        AuthenticationManagerFactory managerFactory = _factories.get(newType);
        if (managerFactory == null)
        {
            throw new IllegalConfigurationException("Cannot find authentication provider factory for type " + newType);
        }
        AuthenticationManager manager = managerFactory.createInstance(attributes);
        if (manager == null)
        {
            throw new IllegalConfigurationException("Cannot change authentication provider " + newName + " of type " + newType + " with the given attributes");
        }
        return manager;
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of authentication provider is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of authentication provider attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of authentication provider attributes is denied");
        }
    }

    public static class SimpleAuthenticationProviderAdapter extends AuthenticationProviderAdapter<AuthenticationManager>
    {

        public SimpleAuthenticationProviderAdapter(
                UUID id, Broker broker, AuthenticationManager authManager, Map<String, Object> attributes, Collection<String> attributeNames)
        {
            super(id, broker,authManager, attributes, attributeNames);
        }

        @Override
        public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            throw new UnsupportedOperationException();
        }


    }

    public static class PrincipalDatabaseAuthenticationManagerAdapter
            extends AuthenticationProviderAdapter<PrincipalDatabaseAuthenticationManager>
            implements PasswordCredentialManagingAuthenticationProvider
    {
        public PrincipalDatabaseAuthenticationManagerAdapter(
                UUID id, Broker broker, PrincipalDatabaseAuthenticationManager authManager, Map<String, Object> attributes, Collection<String> attributeNames)
        {
            super(id, broker, authManager, attributes, attributeNames);
        }

        @Override
        public boolean createUser(String username, String password, Map<String, String> attributes)
        {
            if(getSecurityManager().authoriseUserOperation(Operation.CREATE, username))
            {
                return getPrincipalDatabase().createPrincipal(new UsernamePrincipal(username), password.toCharArray());
            }
            else
            {
                throw new AccessControlException("Do not have permission to create new user");
            }
        }

        @Override
        public void deleteUser(String username) throws AccountNotFoundException
        {
            if(getSecurityManager().authoriseUserOperation(Operation.DELETE, username))
            {
                getPrincipalDatabase().deletePrincipal(new UsernamePrincipal(username));
            }
            else
            {
                throw new AccessControlException("Cannot delete user " + username);
            }
        }

        private SecurityManager getSecurityManager()
        {
            return _broker.getSecurityManager();
        }

        private PrincipalDatabase getPrincipalDatabase()
        {
            return getAuthManager().getPrincipalDatabase();
        }

        @Override
        public void setPassword(String username, String password) throws AccountNotFoundException
        {
            if(getSecurityManager().authoriseUserOperation(Operation.UPDATE, username))
            {
                getPrincipalDatabase().updatePassword(new UsernamePrincipal(username), password.toCharArray());
            }
            else
            {
                throw new AccessControlException("Do not have permission to set password");
            }
        }

        @Override
        public Map<String, Map<String, String>> getUsers()
        {

            Map<String, Map<String,String>> users = new HashMap<String, Map<String, String>>();
            for(Principal principal : getPrincipalDatabase().getUsers())
            {
                users.put(principal.getName(), Collections.<String, String>emptyMap());
            }
            return users;
        }

        public void reload() throws IOException
        {
            getPrincipalDatabase().reload();
        }

        @Override
        public <C extends ConfiguredObject> C addChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            if(childClass == User.class)
            {
                String username = (String) attributes.get("name");
                String password = (String) attributes.get("password");
                Principal p = new UsernamePrincipal(username);

                if(createUser(username, password,null))
                {
                    @SuppressWarnings("unchecked")
                    C pricipalAdapter = (C) new PrincipalAdapter(p, getTaskExecutor());
                    return pricipalAdapter;
                }
                else
                {
                    //TODO? Silly interface on the PrincipalDatabase at fault
                    throw new RuntimeException("Failed to create user");
                }
            }

            return super.addChild(childClass, attributes, otherParents);
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
        {
            if(clazz == User.class)
            {
                List<Principal> users = getPrincipalDatabase().getUsers();
                Collection<User> principals = new ArrayList<User>(users.size());
                for(Principal user : users)
                {
                    principals.add(new PrincipalAdapter(user, getTaskExecutor()));
                }
                @SuppressWarnings("unchecked")
                Collection<C> unmodifiablePrincipals = (Collection<C>) Collections.unmodifiableCollection(principals);
                return unmodifiablePrincipals;
            }
            else
            {
                return super.getChildren(clazz);
            }
        }

        @Override
        protected void childAdded(ConfiguredObject child)
        {
            // no-op, prevent storing users in the broker store
        }

        @Override
        protected void childRemoved(ConfiguredObject child)
        {
            // no-op, as per above, users are not in the store
        }

        private class PrincipalAdapter extends AbstractAdapter implements User
        {
            private final Principal _user;

            public PrincipalAdapter(Principal user, TaskExecutor taskExecutor)
            {
                super(UUIDGenerator.generateUserUUID(PrincipalDatabaseAuthenticationManagerAdapter.this.getName(), user.getName()), taskExecutor);
                _user = user;

            }

            @Override
            public void setPassword(String password)
            {
                try
                {
                    PrincipalDatabaseAuthenticationManagerAdapter.this.setPassword(_user.getName(), password);
                }
                catch (AccountNotFoundException e)
                {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public String getName()
            {
                return _user.getName();
            }

            @Override
            public String setName(String currentName, String desiredName)
                    throws IllegalStateException, AccessControlException
            {
                throw new IllegalStateException("Names cannot be updated");
            }

            @Override
            public State getActualState()
            {
                return State.ACTIVE;
            }

            @Override
            public boolean isDurable()
            {
                return true;
            }

            @Override
            public void setDurable(boolean durable)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("Durability cannot be updated");
            }

            @Override
            public LifetimePolicy getLifetimePolicy()
            {
                return LifetimePolicy.PERMANENT;
            }

            @Override
            public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("LifetimePolicy cannot be updated");
            }

            @Override
            public long getTimeToLive()
            {
                return 0;
            }

            @Override
            public long setTimeToLive(long expected, long desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("ttl cannot be updated");
            }

            @Override
            public Statistics getStatistics()
            {
                return NoStatistics.getInstance();
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
            {
                return null;
            }

            @Override
            public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                              Map<String, Object> attributes,
                                                              ConfiguredObject... otherParents)
            {
                return null;
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return User.AVAILABLE_ATTRIBUTES;
            }

            @Override
            public Object getAttribute(String name)
            {
                if(ID.equals(name))
                {
                    return getId();
                }
                else if(PASSWORD.equals(name))
                {
                    return null; // for security reasons we don't expose the password
                }
                else if(NAME.equals(name))
                {
                    return getName();
                }
                return super.getAttribute(name);
            }

            @Override
            public boolean changeAttribute(String name, Object expected, Object desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                if(name.equals(PASSWORD))
                {
                    setPassword((String)desired);
                    return true;
                }
                return super.changeAttribute(name, expected, desired);
            }

            @Override
            protected boolean setState(State currentState, State desiredState)
                    throws IllegalStateTransitionException, AccessControlException
            {
                if(desiredState == State.DELETED)
                {
                    try
                    {
                        deleteUser(_user.getName());
                    }
                    catch (AccountNotFoundException e)
                    {
                        LOGGER.warn("Failed to delete user " + _user, e);
                    }
                    return true;
                }
                return false;
            }
        }
    }
}
