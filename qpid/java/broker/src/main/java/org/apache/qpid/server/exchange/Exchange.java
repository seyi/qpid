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
package org.apache.qpid.server.exchange;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Exchange extends ExchangeReferrer
{
    void initialise(UUID id, VirtualHost host, AMQShortString name, boolean durable, boolean autoDelete)
            throws AMQException;


    UUID getId();

    String getName();

    AMQShortString getNameShortString();

    ExchangeType getType();

    AMQShortString getTypeShortString();

    boolean isDurable();

    /**
     * @return true if the exchange will be deleted after all queues have been detached
     */
    boolean isAutoDelete();

    Exchange getAlternateExchange();

    void setAlternateExchange(Exchange exchange);

    long getBindingCount();

    long getByteDrops();

    long getByteReceives();

    long getMsgDrops();

    long getMsgReceives();


    boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException;

    boolean replaceBinding(UUID id, String bindingKey,
                           AMQQueue queue,
                           Map<String, Object> arguments)
                    throws AMQSecurityException, AMQInternalException;

    void restoreBinding(UUID id, String bindingKey, AMQQueue queue,
                        Map<String, Object> argumentMap)
                    throws AMQSecurityException, AMQInternalException;

    void removeBinding(Binding b) throws AMQSecurityException, AMQInternalException;

    Binding removeBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
                    throws AMQSecurityException, AMQInternalException;

    Binding getBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments);

    void close() throws AMQException;

    /**
     * Returns a list of queues to which to route this message.   If there are
     * no queues the empty list must be returned.
     *
     * @return list of queues to which to route the message.
     */
    List<? extends BaseQueue> route(InboundMessage message);


    /**
     * Determines whether a message would be isBound to a particular queue using a specific routing key and arguments
     * @param routingKey
     * @param arguments
     * @param queue
     * @return
     * @throws AMQException
     */
    boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue);

    /**
     * Determines whether a message would be isBound to a particular queue using a specific routing key
     * @param routingKey
     * @param queue
     * @return
     * @throws AMQException
     */
    boolean isBound(AMQShortString routingKey, AMQQueue queue);

    /**
     * Determines whether a message is routing to any queue using a specific _routing key
     * @param routingKey
     * @return
     * @throws AMQException
     */
    boolean isBound(AMQShortString routingKey);

    /**
     * Returns true if this exchange has at least one binding associated with it.
     * @return
     * @throws AMQException
     */
    boolean hasBindings();

    Collection<Binding> getBindings();

    boolean isBound(String bindingKey);

    boolean isBound(AMQQueue queue);

    boolean isBound(Map<String, Object> arguments);

    boolean isBound(String bindingKey, AMQQueue queue);

    boolean isBound(String bindingKey, Map<String, Object> arguments);

    boolean isBound(Map<String, Object> arguments, AMQQueue queue);

    boolean isBound(String bindingKey, Map<String,Object> arguments, AMQQueue queue);

    void removeReference(ExchangeReferrer exchange);

    void addReference(ExchangeReferrer exchange);

    boolean hasReferrers();



    public interface BindingListener
    {
        void bindingAdded(Exchange exchange, Binding binding);
        void bindingRemoved(Exchange exchange, Binding binding);
    }

    public void addBindingListener(BindingListener listener);

    public void removeBindingListener(BindingListener listener);

}
