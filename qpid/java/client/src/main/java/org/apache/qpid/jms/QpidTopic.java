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
package org.apache.qpid.jms;

import javax.jms.JMSException;
import javax.jms.Topic;

public class QpidTopic extends QpidDestination implements Topic 
{
    public QpidTopic()
    {
    }

    public QpidTopic(String str) throws JMSException
    {
        setDestinationString(str);
    }

    @Override
    public DestinationType getType()
    {
        return DestinationType.TOPIC;
    }

    @Override
    public String getTopicName() throws JMSException
    {
        return _address.getSubject() == null ? "" : _address.getSubject();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if(obj.getClass() != getClass())
        {
            return false;
        }

        QpidTopic topic = (QpidTopic)obj;

        try
        {
            if (!_address.getName().equals(topic.getAddress().getName()))
            {
                return false;
            }

            // The subject being the topic name
            if (!_address.getSubject().equals(topic.getAddress().getSubject()))
            {
                return false;
            }
        }
        catch (Exception e)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int hash = 55;
        String name = _address == null ? "" : _address.getName();
        String subject = _address == null ? "" : _address.getSubject();
        hash = hash * 25 + name.hashCode();
        hash = hash * 35 + subject.hashCode();
        return hash;
    }
}