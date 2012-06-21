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
 * \file TxnAsyncContext.h
 */

#ifndef qpid_broker_TxnAsyncContext_h_
#define qpid_broker_TxnAsyncContext_h_

#include "AsyncStore.h" // qpid::broker::BrokerAsyncContext
#include "TxnHandle.h"

#include "qpid/asyncStore/AsyncOperation.h"

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class TxnAsyncContext: public BrokerAsyncContext
{
public:
    TxnAsyncContext(TxnBuffer* const tb,
                    TxnHandle& th,
                    const qpid::asyncStore::AsyncOperation::opCode op,
                    qpid::broker::AsyncResultCallback rcb,
                    qpid::broker::AsyncResultQueue* const arq);
    virtual ~TxnAsyncContext();
    TxnBuffer* getTxnBuffer() const;
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    TxnHandle getTransactionContext() const;

    // --- Interface BrokerAsyncContext ---
    AsyncResultQueue* getAsyncResultQueue() const;
    void invokeCallback(const AsyncResultHandle* const) const;

private:
    TxnBuffer* const m_tb;
    TxnHandle m_th;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
    AsyncResultCallback m_rcb;
    AsyncResultQueue* const m_arq;
};

}} // namespace qpid::broker

#endif // qpid_broker_TxnAsyncContext_h_