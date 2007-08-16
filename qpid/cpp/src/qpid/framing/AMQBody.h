#ifndef QPID_FRAMING_AMQBODY_H
#define QPID_FRAMING_AMQBODY_H

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
#include "qpid/framing/amqp_types.h"
#include "qpid/shared_ptr.h"

#include <ostream>

namespace qpid {
namespace framing {

class Buffer;

class AMQMethodBody;
class AMQHeaderBody;
class AMQContentBody;
class AMQHeartbeatBody;

struct AMQBodyConstVisitor {
    virtual ~AMQBodyConstVisitor() {}
    virtual void visit(const AMQHeaderBody&) = 0;
    virtual void visit(const AMQContentBody&) = 0;
    virtual void visit(const AMQHeartbeatBody&) = 0;
    virtual void visit(const AMQMethodBody&) = 0;
};

class AMQBody
{
  public:
    typedef shared_ptr<AMQBody> shared_ptr;

    virtual ~AMQBody();

    virtual uint8_t type() const = 0;

    virtual void encode(Buffer& buffer) const = 0;
    virtual void decode(Buffer& buffer, uint32_t=0) = 0;
    virtual uint32_t size() const = 0;

    virtual void print(std::ostream& out) const = 0;
    virtual void accept(AMQBodyConstVisitor&) const = 0;

    virtual AMQMethodBody* getMethod() { return 0; }
    virtual const AMQMethodBody* getMethod() const { return 0; }
};

std::ostream& operator<<(std::ostream& out, const AMQBody& body) ;

enum BodyTypes {
    METHOD_BODY = 1,
    HEADER_BODY = 2,
    CONTENT_BODY = 3,
    HEARTBEAT_BODY = 8
};

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_AMQBODY_H*/
