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
#include "MessagingFixture.h"
#include "unit_test.h"
#include "test_tools.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/ExchangeQueryResult.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Time.h"
#include <boost/assign.hpp>
#include <boost/format.hpp>
#include <string>
#include <vector>

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(MessagingSessionTests)

using namespace qpid::messaging;
using namespace qpid::types;
using namespace qpid;
using qpid::broker::Broker;
using qpid::framing::Uuid;


QPID_AUTO_TEST_CASE(testSimpleSendReceive)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    out.setSubject("test-subject");
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(Duration::SECOND * 5);
    fix.session.acknowledge();
    BOOST_CHECK_EQUAL(in.getContent(), out.getContent());
    BOOST_CHECK_EQUAL(in.getSubject(), out.getSubject());
}

QPID_AUTO_TEST_CASE(testSyncSendReceive)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    sender.send(out, true);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(Duration::IMMEDIATE);
    fix.session.acknowledge(true);
    BOOST_CHECK_EQUAL(in.getContent(), out.getContent());
}

QPID_AUTO_TEST_CASE(testSendReceiveHeaders)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    for (uint i = 0; i < 10; ++i) {
        out.getProperties()["a"] = i;
        sender.send(out);
    }
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in;
    for (uint i = 0; i < 10; ++i) {
        BOOST_CHECK(receiver.fetch(in, Duration::SECOND * 5));
        BOOST_CHECK_EQUAL(in.getContent(), out.getContent());
        BOOST_CHECK_EQUAL(in.getProperties()["a"].asUint32(), i);
        fix.session.acknowledge();
    }
}

QPID_AUTO_TEST_CASE(testSenderError)
{
    MessagingFixture fix;
    ScopedSuppressLogging sl;
    BOOST_CHECK_THROW(fix.session.createSender("NonExistentAddress"), qpid::messaging::NotFound);
    fix.session = fix.connection.createSession();
    BOOST_CHECK_THROW(fix.session.createSender("NonExistentAddress; {create:receiver}"),
                      qpid::messaging::NotFound);
}

QPID_AUTO_TEST_CASE(testReceiverError)
{
    MessagingFixture fix;
    ScopedSuppressLogging sl;
    BOOST_CHECK_THROW(fix.session.createReceiver("NonExistentAddress"), qpid::messaging::NotFound);
    fix.session = fix.connection.createSession();
    BOOST_CHECK_THROW(fix.session.createReceiver("NonExistentAddress; {create:sender}"),
                      qpid::messaging::NotFound);
}

QPID_AUTO_TEST_CASE(testSimpleTopic)
{
    TopicFixture fix;

    Sender sender = fix.session.createSender(fix.topic);
    Message msg("one");
    sender.send(msg);
    Receiver sub1 = fix.session.createReceiver(fix.topic);
    sub1.setCapacity(10u);
    msg.setContent("two");
    sender.send(msg);
    Receiver sub2 = fix.session.createReceiver(fix.topic);
    sub2.setCapacity(10u);
    msg.setContent("three");
    sender.send(msg);
    Receiver sub3 = fix.session.createReceiver(fix.topic);
    sub3.setCapacity(10u);
    msg.setContent("four");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub2, 2), boost::assign::list_of<std::string>("three")("four"));
    sub2.close();

    msg.setContent("five");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub1, 4), boost::assign::list_of<std::string>("two")("three")("four")("five"));
    BOOST_CHECK_EQUAL(fetch(sub3, 2), boost::assign::list_of<std::string>("four")("five"));
    Message in;
    BOOST_CHECK(!sub2.fetch(in, Duration::IMMEDIATE));//TODO: or should this raise an error?


    //TODO: check pending messages...
}

QPID_AUTO_TEST_CASE(testNextReceiver)
{
    MultiQueueFixture fix;

    for (uint i = 0; i < fix.queues.size(); i++) {
        Receiver r = fix.session.createReceiver(fix.queues[i]);
        r.setCapacity(10u);
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Sender s = fix.session.createSender(fix.queues[i]);
        Message msg((boost::format("Message_%1%") % (i+1)).str());
        s.send(msg);
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Message msg;
        BOOST_CHECK(fix.session.nextReceiver().fetch(msg, Duration::SECOND));
        BOOST_CHECK_EQUAL(msg.getContent(), (boost::format("Message_%1%") % (i+1)).str());
    }
}

QPID_AUTO_TEST_CASE(testMapMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    Variant::Map content;
    content["abc"] = "def";
    content["pi"] = 3.14f;
    Variant utf8("A utf 8 string");
    utf8.setEncoding("utf8");
    content["utf8"] = utf8;
    Variant utf16("\x00\x61\x00\x62\x00\x63");
    utf16.setEncoding("utf16");
    content["utf16"] = utf16;
    encode(content, out);
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * Duration::SECOND);
    Variant::Map view;
    decode(in, view);
    BOOST_CHECK_EQUAL(view["abc"].asString(), "def");
    BOOST_CHECK_EQUAL(view["pi"].asFloat(), 3.14f);
    BOOST_CHECK_EQUAL(view["utf8"].asString(), utf8.asString());
    BOOST_CHECK_EQUAL(view["utf8"].getEncoding(), utf8.getEncoding());
    BOOST_CHECK_EQUAL(view["utf16"].asString(), utf16.asString());
    BOOST_CHECK_EQUAL(view["utf16"].getEncoding(), utf16.getEncoding());
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testMapMessageWithInitial)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    Variant::Map imap;
    imap["abc"] = "def";
    imap["pi"] = 3.14f;
    encode(imap, out);
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * Duration::SECOND);
    Variant::Map view;
    decode(in, view);
    BOOST_CHECK_EQUAL(view["abc"].asString(), "def");
    BOOST_CHECK_EQUAL(view["pi"].asFloat(), 3.14f);
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testListMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    Variant::List content;
    content.push_back(Variant("abc"));
    content.push_back(Variant(1234));
    content.push_back(Variant("def"));
    content.push_back(Variant(56.789));
    encode(content, out);
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * Duration::SECOND);
    Variant::List view;
    decode(in, view);
    BOOST_CHECK_EQUAL(view.size(), content.size());
    BOOST_CHECK_EQUAL(view.front().asString(), "abc");
    BOOST_CHECK_EQUAL(view.back().asDouble(), 56.789);

    Variant::List::const_iterator i = view.begin();
    BOOST_CHECK(i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "abc");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asInt64(), 1234);
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "def");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asDouble(), 56.789);
    BOOST_CHECK(++i == view.end());

    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testListMessageWithInitial)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    Variant::List ilist;
    ilist.push_back(Variant("abc"));
    ilist.push_back(Variant(1234));
    ilist.push_back(Variant("def"));
    ilist.push_back(Variant(56.789));
    encode(ilist, out);
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * Duration::SECOND);
    Variant::List view;
    decode(in, view);
    BOOST_CHECK_EQUAL(view.size(), ilist.size());
    BOOST_CHECK_EQUAL(view.front().asString(), "abc");
    BOOST_CHECK_EQUAL(view.back().asDouble(), 56.789);

    Variant::List::const_iterator i = view.begin();
    BOOST_CHECK(i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "abc");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asInt64(), 1234);
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "def");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asDouble(), 56.789);
    BOOST_CHECK(++i == view.end());

    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testReject)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message m1("reject-me");
    sender.send(m1);
    Message m2("accept-me");
    sender.send(m2);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * Duration::SECOND);
    BOOST_CHECK_EQUAL(in.getContent(), m1.getContent());
    fix.session.reject(in);
    in = receiver.fetch(5 * Duration::SECOND);
    BOOST_CHECK_EQUAL(in.getContent(), m2.getContent());
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testAvailable)
{
    MultiQueueFixture fix;

    Receiver r1 = fix.session.createReceiver(fix.queues[0]);
    r1.setCapacity(100);

    Receiver r2 = fix.session.createReceiver(fix.queues[1]);
    r2.setCapacity(100);

    Sender s1 = fix.session.createSender(fix.queues[0]);
    Sender s2 = fix.session.createSender(fix.queues[1]);

    for (uint i = 0; i < 10; ++i) {
        s1.send(Message((boost::format("A_%1%") % (i+1)).str()));
    }
    for (uint i = 0; i < 5; ++i) {
        s2.send(Message((boost::format("B_%1%") % (i+1)).str()));
    }
    qpid::sys::sleep(1);//is there any avoid an arbitrary sleep while waiting for messages to be dispatched?
    for (uint i = 0; i < 5; ++i) {
        BOOST_CHECK_EQUAL(fix.session.getReceivable(), 15u - 2*i);
        BOOST_CHECK_EQUAL(r1.getAvailable(), 10u - i);
        BOOST_CHECK_EQUAL(r1.fetch().getContent(), (boost::format("A_%1%") % (i+1)).str());
        BOOST_CHECK_EQUAL(r2.getAvailable(), 5u - i);
        BOOST_CHECK_EQUAL(r2.fetch().getContent(), (boost::format("B_%1%") % (i+1)).str());
        fix.session.acknowledge();
    }
    for (uint i = 5; i < 10; ++i) {
        BOOST_CHECK_EQUAL(fix.session.getReceivable(), 10u - i);
        BOOST_CHECK_EQUAL(r1.getAvailable(), 10u - i);
        BOOST_CHECK_EQUAL(r1.fetch().getContent(), (boost::format("A_%1%") % (i+1)).str());
    }
}

QPID_AUTO_TEST_CASE(testUnsettledAcks)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        sender.send(Message((boost::format("Message_%1%") % (i+1)).str()));
    }
    Receiver receiver = fix.session.createReceiver(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        BOOST_CHECK_EQUAL(receiver.fetch().getContent(), (boost::format("Message_%1%") % (i+1)).str());
    }
    BOOST_CHECK_EQUAL(fix.session.getUnsettledAcks(), 0u);
    fix.session.acknowledge();
    BOOST_CHECK_EQUAL(fix.session.getUnsettledAcks(), 10u);
    fix.session.sync();
    BOOST_CHECK_EQUAL(fix.session.getUnsettledAcks(), 0u);
}

QPID_AUTO_TEST_CASE(testUnsettledSend)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    send(sender, 10);
    //Note: this test relies on 'inside knowledge' of the sender
    //implementation and the fact that the simple test case makes it
    //possible to predict when completion information will be sent to
    //the client. TODO: is there a better way of testing this?
    BOOST_CHECK_EQUAL(sender.getUnsettled(), 10u);
    fix.session.sync();
    BOOST_CHECK_EQUAL(sender.getUnsettled(), 0u);

    Receiver receiver = fix.session.createReceiver(fix.queue);
    receive(receiver, 10);
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testBrowse)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    send(sender, 10);
    Receiver browser1 = fix.session.createReceiver(fix.queue + "; {mode:browse}");
    receive(browser1, 10);
    Receiver browser2 = fix.session.createReceiver(fix.queue + "; {mode:browse}");
    receive(browser2, 10);
    Receiver consumer = fix.session.createReceiver(fix.queue);
    receive(consumer, 10);
    fix.session.acknowledge();
}

struct QueueCreatePolicyFixture : public MessagingFixture
{
    qpid::messaging::Address address;

    QueueCreatePolicyFixture(const std::string& a) : address(a) {}

    void test()
    {
        ping(address);
        BOOST_CHECK(admin.checkQueueExists(address.getName()));
    }

    ~QueueCreatePolicyFixture()
    {
        admin.deleteQueue(address.getName());    
    }
};

QPID_AUTO_TEST_CASE(testCreatePolicyQueueAlways)
{
    QueueCreatePolicyFixture fix("#; {create:always, node:{type:queue}}");
    fix.test();
}

QPID_AUTO_TEST_CASE(testCreatePolicyQueueReceiver)
{
    QueueCreatePolicyFixture fix("#; {create:receiver, node:{type:queue}}");
    Receiver r = fix.session.createReceiver(fix.address);
    fix.test();
    r.close();
}

QPID_AUTO_TEST_CASE(testCreatePolicyQueueSender)
{
    QueueCreatePolicyFixture fix("#; {create:sender, node:{type:queue}}");
    Sender s = fix.session.createSender(fix.address);
    fix.test();
    s.close();
}

struct ExchangeCreatePolicyFixture : public MessagingFixture
{
    qpid::messaging::Address address;
    const std::string exchangeType;

    ExchangeCreatePolicyFixture(const std::string& a, const std::string& t) :
        address(a), exchangeType(t) {}

    void test()
    {
        ping(address);
        std::string actualType;
        BOOST_CHECK(admin.checkExchangeExists(address.getName(), actualType));
        BOOST_CHECK_EQUAL(exchangeType, actualType);
    }

    ~ExchangeCreatePolicyFixture()
    {
        admin.deleteExchange(address.getName());    
    }
};

QPID_AUTO_TEST_CASE(testCreatePolicyTopic)
{
    ExchangeCreatePolicyFixture fix("#; {create:always, node:{type:topic}}",
                                  "topic");
    fix.test();
}

QPID_AUTO_TEST_CASE(testCreatePolicyTopicReceiverFanout)
{
    ExchangeCreatePolicyFixture fix("#/my-subject; {create:receiver, node:{type:topic, x-declare:{type:fanout}}}", "fanout");
    Receiver r = fix.session.createReceiver(fix.address);
    fix.test();
    r.close();
}

QPID_AUTO_TEST_CASE(testCreatePolicyTopicSenderDirect)
{
    ExchangeCreatePolicyFixture fix("#/my-subject; {create:sender, node:{type:topic, x-declare:{type:direct}}}", "direct");
    Sender s = fix.session.createSender(fix.address);
    fix.test();
    s.close();
}

struct DeletePolicyFixture : public MessagingFixture
{
    enum Mode {RECEIVER, SENDER, ALWAYS, NEVER};

    std::string getPolicy(Mode mode)
    {
        switch (mode) {
          case SENDER:
            return "{delete:sender}";
          case RECEIVER:
            return "{delete:receiver}";
          case ALWAYS:
            return "{delete:always}";
          case NEVER:
            return "{delete:never}";
        }
        return "";
    }

    void testAll()
    {
        test(RECEIVER);
        test(SENDER);
        test(ALWAYS);
        test(NEVER);
    }

    virtual ~DeletePolicyFixture() {}
    virtual void create(const qpid::messaging::Address&) = 0;
    virtual void destroy(const qpid::messaging::Address&) = 0;
    virtual bool exists(const qpid::messaging::Address&) = 0;

    void test(Mode mode)
    {
        qpid::messaging::Address address("#; " + getPolicy(mode));
        create(address);

        Sender s = session.createSender(address);
        Receiver r = session.createReceiver(address);
        switch (mode) {
          case RECEIVER:
            s.close();
            BOOST_CHECK(exists(address));
            r.close();
            BOOST_CHECK(!exists(address));
            break;
          case SENDER:
            r.close();
            BOOST_CHECK(exists(address));
            s.close();
            BOOST_CHECK(!exists(address));
            break;
          case ALWAYS:
            s.close();
            BOOST_CHECK(!exists(address));
            break;
          case NEVER:
            r.close();
            BOOST_CHECK(exists(address));
            s.close();
            BOOST_CHECK(exists(address));
            destroy(address);
        }
    }
};

struct QueueDeletePolicyFixture : DeletePolicyFixture
{
    void create(const qpid::messaging::Address& address)
    {
        admin.createQueue(address.getName());
    }
    void destroy(const qpid::messaging::Address& address)
    {
        admin.deleteQueue(address.getName());
    }
    bool exists(const qpid::messaging::Address& address)
    {
        return admin.checkQueueExists(address.getName());
    }
};

struct ExchangeDeletePolicyFixture : DeletePolicyFixture
{
    const std::string exchangeType;
    ExchangeDeletePolicyFixture(const std::string type = "topic") : exchangeType(type) {}

    void create(const qpid::messaging::Address& address)
    {
        admin.createExchange(address.getName(), exchangeType);
    }
    void destroy(const qpid::messaging::Address& address)
    {
        admin.deleteExchange(address.getName());
    }
    bool exists(const qpid::messaging::Address& address)
    {
        std::string actualType;
        return admin.checkExchangeExists(address.getName(), actualType) && actualType == exchangeType;
    }
};

QPID_AUTO_TEST_CASE(testDeletePolicyQueue)
{
    QueueDeletePolicyFixture fix;
    fix.testAll();
}

QPID_AUTO_TEST_CASE(testDeletePolicyExchange)
{
    ExchangeDeletePolicyFixture fix;
    fix.testAll();
}

QPID_AUTO_TEST_CASE(testAssertPolicyQueue)
{
    MessagingFixture fix;
    std::string a1 = "q; {create:always, assert:always, node:{type:queue, durable:false, x-declare:{arguments:{qpid.max-count:100}}}}";
    Sender s1 = fix.session.createSender(a1);
    s1.close();
    Receiver r1 = fix.session.createReceiver(a1);
    r1.close();
    
    std::string a2 = "q; {assert:receiver, node:{durable:true, x-declare:{arguments:{qpid.max-count:100}}}}";
    Sender s2 = fix.session.createSender(a2);
    s2.close();
    BOOST_CHECK_THROW(fix.session.createReceiver(a2), qpid::messaging::AssertionFailed);

    std::string a3 = "q; {assert:sender, node:{x-declare:{arguments:{qpid.max-count:99}}}}";
    BOOST_CHECK_THROW(fix.session.createSender(a3), qpid::messaging::AssertionFailed);
    Receiver r3 = fix.session.createReceiver(a3);
    r3.close();

    fix.admin.deleteQueue("q");
}

QPID_AUTO_TEST_CASE(testGetSender)
{
    QueueFixture fix;
    std::string name = fix.session.createSender(fix.queue).getName();
    Sender sender = fix.session.getSender(name);
    BOOST_CHECK_EQUAL(name, sender.getName());
    Message out(Uuid(true).str());
    sender.send(out);
    Message in;
    BOOST_CHECK(fix.session.createReceiver(fix.queue).fetch(in));
    BOOST_CHECK_EQUAL(out.getContent(), in.getContent());
    BOOST_CHECK_THROW(fix.session.getSender("UnknownSender"), qpid::messaging::KeyError);
}

QPID_AUTO_TEST_CASE(testGetReceiver)
{
    QueueFixture fix;
    std::string name = fix.session.createReceiver(fix.queue).getName();
    Receiver receiver = fix.session.getReceiver(name);
    BOOST_CHECK_EQUAL(name, receiver.getName());
    Message out(Uuid(true).str());
    fix.session.createSender(fix.queue).send(out);
    Message in;
    BOOST_CHECK(receiver.fetch(in));
    BOOST_CHECK_EQUAL(out.getContent(), in.getContent());
    BOOST_CHECK_THROW(fix.session.getReceiver("UnknownReceiver"), qpid::messaging::KeyError);
}

QPID_AUTO_TEST_CASE(testGetSessionFromConnection)
{
    QueueFixture fix;
    fix.connection.createSession("my-session");
    Session session = fix.connection.getSession("my-session");
    Message out(Uuid(true).str());
    session.createSender(fix.queue).send(out);
    Message in;
    BOOST_CHECK(session.createReceiver(fix.queue).fetch(in));
    BOOST_CHECK_EQUAL(out.getContent(), in.getContent());
    BOOST_CHECK_THROW(fix.connection.getSession("UnknownSession"), qpid::messaging::KeyError);
}

QPID_AUTO_TEST_CASE(testGetConnectionFromSession)
{
    QueueFixture fix;
    Message out(Uuid(true).str());
    Sender sender = fix.session.createSender(fix.queue);
    sender.send(out);
    Message in;
    sender.getSession().getConnection().createSession("incoming");
    BOOST_CHECK(fix.connection.getSession("incoming").createReceiver(fix.queue).fetch(in));
    BOOST_CHECK_EQUAL(out.getContent(), in.getContent());
}

QPID_AUTO_TEST_CASE(testTx)
{
    QueueFixture fix;
    Session ssn1 = fix.connection.createTransactionalSession();
    Session ssn2 = fix.connection.createTransactionalSession();
    Sender sender1 = ssn1.createSender(fix.queue);
    Sender sender2 = ssn2.createSender(fix.queue);
    Receiver receiver1 = ssn1.createReceiver(fix.queue);
    Receiver receiver2 = ssn2.createReceiver(fix.queue);
    Message in;

    send(sender1, 5, 1, "A");
    send(sender2, 5, 1, "B");
    ssn2.commit();
    receive(receiver1, 5, 1, "B");//(only those from sender2 should be received)
    BOOST_CHECK(!receiver1.fetch(in, Duration::IMMEDIATE));//check there are no more messages
    ssn1.rollback();
    receive(receiver2, 5, 1, "B");
    BOOST_CHECK(!receiver2.fetch(in, Duration::IMMEDIATE));//check there are no more messages
    ssn2.rollback();
    receive(receiver1, 5, 1, "B");
    BOOST_CHECK(!receiver1.fetch(in, Duration::IMMEDIATE));//check there are no more messages
    ssn1.commit();
    //check neither receiver gets any more messages:
    BOOST_CHECK(!receiver1.fetch(in, Duration::IMMEDIATE));
    BOOST_CHECK(!receiver2.fetch(in, Duration::IMMEDIATE));
}

QPID_AUTO_TEST_CASE(testRelease)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    sender.send(out, true);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message m1 = receiver.fetch(Duration::IMMEDIATE);
    fix.session.release(m1);
    Message m2 = receiver.fetch(Duration::SECOND * 1);
    BOOST_CHECK_EQUAL(m1.getContent(), out.getContent());
    BOOST_CHECK_EQUAL(m1.getContent(), m2.getContent());
    fix.session.acknowledge(true);
}

QPID_AUTO_TEST_CASE(testOptionVerification)
{
    MessagingFixture fix;
    fix.session.createReceiver("my-queue; {create: always, assert: always, delete: always, node: {type: queue, durable: false, x-declare: {arguments: {a: b}}, x-bindings: [{exchange: amq.fanout}]}, link: {name: abc, durable: false, reliability: exactly-once, x-subscribe: {arguments:{a:b}}, x-bindings:[{exchange: amq.fanout}]}, mode: browse}");
    BOOST_CHECK_THROW(fix.session.createReceiver("my-queue; {invalid-option:blah}"), qpid::messaging::AddressError);    
}

QPID_AUTO_TEST_CASE(testReceiveSpecialProperties)
{
    QueueFixture fix;

    qpid::client::Message out;
    out.getDeliveryProperties().setRoutingKey(fix.queue);
    out.getMessageProperties().setAppId("my-app-id");
    out.getMessageProperties().setMessageId(qpid::framing::Uuid(true));
    out.getMessageProperties().setContentEncoding("my-content-encoding");
    fix.admin.send(out);

    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(Duration::SECOND * 5);
    BOOST_CHECK_EQUAL(in.getProperties()["x-amqp-0-10.routing-key"].asString(), out.getDeliveryProperties().getRoutingKey());
    BOOST_CHECK_EQUAL(in.getProperties()["x-amqp-0-10.app-id"].asString(), out.getMessageProperties().getAppId());
    BOOST_CHECK_EQUAL(in.getProperties()["x-amqp-0-10.content-encoding"].asString(), out.getMessageProperties().getContentEncoding());
    BOOST_CHECK_EQUAL(in.getMessageId(), out.getMessageProperties().getMessageId().str());
    fix.session.acknowledge(true);
}

QPID_AUTO_TEST_CASE(testSendSpecialProperties)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    std::string appId = "my-app-id";
    std::string contentEncoding = "my-content-encoding";
    out.getProperties()["x-amqp-0-10.app-id"] = appId;
    out.getProperties()["x-amqp-0-10.content-encoding"] = contentEncoding;
    out.setMessageId(qpid::framing::Uuid(true).str());
    sender.send(out, true);

    qpid::client::LocalQueue q;
    qpid::client::SubscriptionManager subs(fix.admin.session);
    qpid::client::Subscription s = subs.subscribe(q, fix.queue);
    qpid::client::Message in = q.get();
    s.cancel();
    fix.admin.session.sync();

    BOOST_CHECK_EQUAL(in.getMessageProperties().getAppId(), appId);
    BOOST_CHECK_EQUAL(in.getMessageProperties().getContentEncoding(), contentEncoding);
    BOOST_CHECK_EQUAL(in.getMessageProperties().getMessageId().str(), out.getMessageId());
}

QPID_AUTO_TEST_CASE(testExclusiveSubscriber)
{
    QueueFixture fix;
    std::string address = (boost::format("%1%; { link: { x-subscribe : { exclusive:true } } }") % fix.queue).str();
    Receiver receiver = fix.session.createReceiver(address);
    ScopedSuppressLogging sl;
    try {
        fix.session.createReceiver(address);
        fix.session.sync();
        BOOST_FAIL("Expected exception.");
    } catch (const MessagingException& e) {}
}


QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
