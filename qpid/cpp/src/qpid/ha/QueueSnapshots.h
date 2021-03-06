#ifndef QPID_HA_QUEUESNAPSHOTS_H
#define QPID_HA_QUEUESNAPSHOTS_H

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


#include "QueueSnapshot.h"
#include "hash.h"

#include "qpid/assert.h"
#include "qpid/broker/BrokerObserver.h"
#include "qpid/broker/Queue.h"
#include "qpid/sys/Mutex.h"

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace ha {

/**
 * BrokerObserver that maintains a map of the QueueSnapshot for each queue.
 * THREAD SAFE.
 */
class QueueSnapshots : public broker::BrokerObserver
{
  public:
    boost::shared_ptr<QueueSnapshot> get(const boost::shared_ptr<broker::Queue>& q) const {
        sys::Mutex::ScopedLock l(lock);
        SnapshotMap::const_iterator i = snapshots.find(q);
        return i != snapshots.end() ? i->second : boost::shared_ptr<QueueSnapshot>();
    }

    // BrokerObserver overrides.
    void queueCreate(const boost::shared_ptr<broker::Queue>& q) {
        sys::Mutex::ScopedLock l(lock);
        boost::shared_ptr<QueueSnapshot> observer(new QueueSnapshot);
        snapshots[q] = observer;
        q->addObserver(observer);
    }

    void queueDestroy(const boost::shared_ptr<broker::Queue>& q) {
        sys::Mutex::ScopedLock l(lock);
        SnapshotMap::iterator i = snapshots.find(q);
        if (i != snapshots.end()) {
            q->removeObserver(i->second);
            snapshots.erase(i);
        }
    }

  private:
    typedef qpid::sys::unordered_map<boost::shared_ptr<broker::Queue>,
                                     boost::shared_ptr<QueueSnapshot>,
                                     Hasher<boost::shared_ptr<broker::Queue> >
                                     > SnapshotMap;
    SnapshotMap snapshots;
    mutable sys::Mutex lock;
};


}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUESNAPSHOTS_H*/
