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
 * \file AsyncResultQueueImpl.cpp
 */

#include "AsyncResultHandle.h"
#include "AsyncResultQueueImpl.h"

namespace qpid {
namespace broker {

AsyncResultQueueImpl::AsyncResultQueueImpl(const boost::shared_ptr<qpid::sys::Poller>& poller) :
        m_resQueue(boost::bind(&AsyncResultQueueImpl::handle, this, _1), poller)
{
    m_resQueue.start();
}

AsyncResultQueueImpl::~AsyncResultQueueImpl()
{
    m_resQueue.stop();
}

void
AsyncResultQueueImpl::submit(boost::shared_ptr<AsyncResultHandle> arh)
{
//std::cout << "==> AsyncResultQueueImpl::submit() errNo=" << arh->getErrNo() << " errMsg=\"" << arh->getErrMsg() << "\"" << std::endl << std::flush;
    m_resQueue.push(arh);
}

// private
AsyncResultQueueImpl::ResultQueue::Batch::const_iterator
AsyncResultQueueImpl::handle(const ResultQueue::Batch& e)
{
    try {
        for (ResultQueue::Batch::const_iterator i = e.begin(); i != e.end(); ++i) {
//std::cout << "<== AsyncResultQueueImpl::handle() errNo=" << (*i)->getErrNo() << " errMsg=\"" << (*i)->getErrMsg() << "\"" << std::endl << std::flush;
            if ((*i)->isValid()) {
                (*i)->invokeAsyncResultCallback();
            }
        }
    } catch (const std::exception& e) {
        std::cerr << "qpid::broker::AsyncResultQueueImpl: Exception thrown processing async result: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "qpid::broker::AsyncResultQueueImpl: Unknown exception thrown processing async result" << std::endl;
    }
    return e.end();
}

}} // namespace qpid::broker