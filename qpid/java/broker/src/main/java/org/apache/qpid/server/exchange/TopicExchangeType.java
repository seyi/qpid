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

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TopicExchangeType implements ExchangeType<TopicExchange>
{
    @Override
    public String getType()
    {
        return getName().toString();
    }

    public AMQShortString getName()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
    }

    public TopicExchange newInstance(UUID id, VirtualHost host,
                                     AMQShortString name,
                                     boolean durable,
                                     boolean autoDelete) throws AMQException
    {
        TopicExchange exch = new TopicExchange();
        exch.initialise(id, host, name, durable, autoDelete);
        return exch;
    }

    public AMQShortString getDefaultExchangeName()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_NAME;
    }
}
