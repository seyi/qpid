#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Runs the perftests using a typical configuration.

BASE_DIR=`dirname $0`
DURATION=${1:-5000}
AMQP_VERSION=${2:-0-91}

echo Will run perftests using a maximum duration of ${DURATION}ms and AMQP protocol version ${AMQP_VERSION}.
echo

java -cp "${BASE_DIR}:${BASE_DIR}/../../build/lib/*" \
  -Dqpid.amqp.version=${AMQP_VERSION} -Dqpid.dest_syntax=BURL \
  -Dqpid.disttest.duration=$DURATION \
  org.apache.qpid.disttest.ControllerRunner \
  jndi-config=${BASE_DIR}/perftests-jndi.properties \
  test-config=${BASE_DIR}/testdefs/VaryingNumberOfParticipants.js \
  distributed=false \
  writeToDb=true
