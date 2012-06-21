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
 * \file DeliveryRecord.h
 */

#ifndef tests_storePerftools_asyncPerf_DeliveryRecord_h_
#define tests_storePerftools_asyncPerf_DeliveryRecord_h_

#include "QueuedMessage.h"

namespace qpid  {
namespace broker {
class TxnHandle;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class DeliveryRecord {
public:
    DeliveryRecord(const QueuedMessage& qm,
                   bool accepted);
    virtual ~DeliveryRecord();
    bool accept(qpid::broker::TxnHandle* txn);
    bool isAccepted() const;
    bool setEnded();
    bool isEnded() const;
    bool isRedundant() const;
private:
    QueuedMessage m_queuedMessage;
    bool m_accepted : 1;
    bool m_ended : 1;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_DeliveryRecord_h_