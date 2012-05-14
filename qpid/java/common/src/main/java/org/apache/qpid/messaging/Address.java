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
package org.apache.qpid.messaging;

import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Node;
import org.apache.qpid.messaging.util.AddressParser;

/**
 * Address
 *
 */
public class Address
{
    private final String _name;
    private final String _subject;
    private final Node _node;
    private final Link _link;
    private final String _toString;

    public static AddressRaw parse(String address)
    {
        return new AddressParser(address).parse();
    }

    public Address (String name, String subject, Node node, Link link)
    {
        this._name = name;
        this._subject = subject;
        this._node = node;
        this._link = link;
        this._toString = null; // TODO
    }

    public String getName()
    {
        return _name;
    }

    public String getSubject()
    {
        return _subject;
    }

    public String toString()
    {
        return _toString;
    }

    public Node getNode()
    {
        return _node;
    }

    public Link getLink()
    {
        return _link;
    }
}
