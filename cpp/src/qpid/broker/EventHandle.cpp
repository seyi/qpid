/*
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
 */

/**
 * \file EventHandle.cpp
 */

#include "EventHandle.h"

#include "qpid/messaging/PrivateImplRef.h"

namespace qpid {
namespace broker {

typedef qpid::messaging::PrivateImplRef<EventHandle> PrivateImpl;

EventHandle::EventHandle(qpid::asyncStore::EventHandleImpl* p) :
        qpid::messaging::Handle<qpid::asyncStore::EventHandleImpl>(),
        IdHandle()
{
    PrivateImpl::ctor(*this, p);
}

EventHandle::EventHandle(const EventHandle& r) :
        qpid::messaging::Handle<qpid::asyncStore::EventHandleImpl>(),
        IdHandle()
{
    PrivateImpl::copy(*this, r);
}

EventHandle::~EventHandle()
{
    PrivateImpl::dtor(*this);
}

EventHandle&
EventHandle::operator=(const EventHandle& r)
{
    return PrivateImpl::assign(*this, r);
}

// --- EventHandleImpl methods ---

const std::string&
EventHandle::getKey() const
{
    return impl->getKey();
}

}} // namespace qpid::broker