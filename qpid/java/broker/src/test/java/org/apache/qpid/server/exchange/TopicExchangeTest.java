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
 *
 */
package org.apache.qpid.server.exchange;

import java.util.List;
import junit.framework.Assert;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TopicExchangeTest extends QpidTestCase
{

    private TopicExchange _exchange;
    private VirtualHost _vhost;


    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _exchange = new TopicExchange();
        _vhost = BrokerTestHelper.createVirtualHost(getName());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_vhost != null)
            {
                _vhost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testNoRoute() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a*#b", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b",queue, _exchange, null));


        routeMessage("a.b", 0l);

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testDirectMatch() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "ab", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        routeMessage("a.b",0l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        int queueCount = routeMessage("a.c",1l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testStarMatch() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a*", false, null, false, false, false, null);
        _exchange.registerQueue(new Binding(null, "a.*",queue, _exchange, null));


        routeMessage("a.b",0l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        routeMessage("a.c",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        int queueCount = routeMessage("a",2l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testHashMatch() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false, false, null);
        _exchange.registerQueue(new Binding(null, "a.#",queue, _exchange, null));


        routeMessage("a.b.c",0l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a.b",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        routeMessage("a.c",2l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 2l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a",3l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 3l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        int queueCount = routeMessage("b", 4l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testMidHash() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b",queue, _exchange, null));

        routeMessage("a.c.d.b",0l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a.c.b",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMatchafterHash() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b.c",queue, _exchange, null));


        int queueCount = routeMessage("a.c.b.b",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());


        routeMessage("a.a.b.c",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        queueCount = routeMessage("a.b.c.b",2l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a.b.c.b.c",3l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 3l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }


    public void testHashAfterHash() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b.c.#.d",queue, _exchange, null));

        int queueCount = routeMessage("a.c.b.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a.a.b.c.d",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testHashHash() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.#.*.#.d",queue, _exchange, null));

        int queueCount = routeMessage("a.c.b.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

        routeMessage("a.a.b.c.d",1l);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testSubMatchFails() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.b.c.d",queue, _exchange, null));

        int queueCount = routeMessage("a.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private int routeMessage(String routingKey, long messageNumber) throws AMQException
    {
        InboundMessage inboundMessage = mock(InboundMessage.class);
        when(inboundMessage.getRoutingKey()).thenReturn(routingKey);
        when(inboundMessage.getRoutingKeyShortString()).thenReturn(new AMQShortString(routingKey));
        List<? extends BaseQueue> queues = _exchange.route(inboundMessage);
        ServerMessage message = mock(ServerMessage.class);
        MessageReference ref = mock(MessageReference.class);
        when(ref.getMessage()).thenReturn(message);
        when(message.newReference()).thenReturn(ref);
        when(message.getMessageNumber()).thenReturn(messageNumber);
        for(BaseQueue q : queues)
        {
            q.enqueue(message);
        }

        return queues.size();
    }

    public void testMoreRouting() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        int queueCount = routeMessage("a.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMoreQueue() throws AMQException
    {
        AMQQueue queue = _vhost.createQueue(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false,
                false, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        int queueCount = routeMessage("a",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

}
