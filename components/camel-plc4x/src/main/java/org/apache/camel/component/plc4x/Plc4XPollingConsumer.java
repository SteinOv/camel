/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.plc4x;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcIncompatibleDatatypeException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Plc4XPollingConsumer extends EventDrivenPollingConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Plc4XPollingConsumer.class);

    private PlcConnection plcConnection;
    private final Map<String, Object> tags;
    private final Plc4XEndpoint plc4XEndpoint;

    public Plc4XPollingConsumer(Plc4XEndpoint endpoint) {
        super(endpoint);
        this.plc4XEndpoint = endpoint;
        this.tags = endpoint.getTags();
    }

    @Override
    public String toString() {
        return "Plc4XPollingConsumer[" + plc4XEndpoint + "]";
    }

    @Override
    public Endpoint getEndpoint() {
        return plc4XEndpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.plcConnection = plc4XEndpoint.getConnection();
        if (!plcConnection.isConnected()) {
            plc4XEndpoint.reconnect();
        }
    }


    @Override
    public Exchange receive() {
        return doReceive(-1);
    }

    @Override
    public Exchange receiveNoWait() {
        return doReceive(0);
    }

    @Override
    public Exchange receive(long timeout) {
        return doReceive(timeout);
    }

    protected Exchange doReceive(long timeout) {
        PlcReadRequest.Builder builder = plcConnection.readRequestBuilder();
        for (Map.Entry<String, Object> tag : tags.entrySet()) {
            try {
                builder.addItem(tag.getKey(), (String) tag.getValue());
            } catch (PlcIncompatibleDatatypeException e) {
                LOGGER.error("For consumer, please use Map<String,String>, currently using {}", tags.getClass().getSimpleName());
            }
        }
        Exchange exchange = plc4XEndpoint.createExchange();
        PlcReadRequest request = builder.build();
        try {
            CompletableFuture<? extends PlcReadResponse> future = request.execute().whenComplete((plcReadResponse, throwable) -> {});
            PlcReadResponse response;
            if (timeout >= 0) {
                response = future.get(timeout, TimeUnit.MILLISECONDS);
            } else {
                response = future.get();
            }

            Map<String, Object> rsp = new HashMap<>();
            for (String field : response.getFieldNames()) {
                rsp.put(field, response.getObject(field));
            }
            exchange.getIn().setBody(rsp);
        } catch (ExecutionException | TimeoutException e) {
            getExceptionHandler().handleException(e);
            exchange.getIn().setBody(new HashMap<>());
        } catch (InterruptedException e) {
            getExceptionHandler().handleException(e);
            Thread.currentThread().interrupt();
        }
        return exchange;
    }

}