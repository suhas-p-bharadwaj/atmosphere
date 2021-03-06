/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.client;

import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsyncIOInterceptor;
import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AsyncIOWriterAdapter;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereInterceptorAdapter;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketResponseFilter;

import java.io.IOException;

/**
 * An {@link AtmosphereInterceptor} that add a special String "|" at the end of a message, allowing the
 * atmosphere.js to detect if one or several messages where aggregated in one write operations.
 * <p/>
 * The special String is configurable using {@link ApplicationConfig#MESSAGE_DELIMITER}
 *
 * @author Jeanfrancois Arcand
 */
public class MessageLengthInterceptor extends AtmosphereInterceptorAdapter {

    private final static byte[] END = "|".getBytes();

    private byte[] end = END;
    private String endString = "|";

    @Override
    public void configure(AtmosphereConfig config) {
        String s = config.getInitParameter(ApplicationConfig.MESSAGE_DELIMITER);
        if (s != null) {
            end = s.getBytes();
            endString = s;
        }
    }

    @Override
    public Action inspect(final AtmosphereResource r) {
        final AtmosphereResponse response = r.getResponse();

        if (r.transport() != AtmosphereResource.TRANSPORT.WEBSOCKET) {
            super.inspect(r);

            AsyncIOWriter writer = response.getAsyncIOWriter();
            if (AtmosphereInterceptorWriter.class.isAssignableFrom(writer.getClass())) {
                AtmosphereInterceptorWriter.class.cast(writer).interceptor(new AsyncIOInterceptor() {

                    @Override
                    public void intercept(AtmosphereResponse response, String data) {
                        response.write(data + endString);
                    }

                    @Override
                    public void intercept(AtmosphereResponse response, byte[] data) {
                        response.write(data).write(end);
                    }

                    @Override
                    public void intercept(AtmosphereResponse response, byte[] data, int offset, int length) {
                        response.write(data, offset, length).write(end);

                    }
                });
            } else {
                throw new IllegalStateException("AsyncIOWriter must be an instance of " + AsyncIOWriter.class.getName());
            }
        } else {
            ((WebSocket) response.getAsyncIOWriter()).webSocketResponseFilter(new WebSocketResponseFilter() {

                @Override
                public String filter(AtmosphereResponse r, String message) {
                    return message + endString;
                }

                @Override
                public byte[] filter(AtmosphereResponse r, byte[] message) {

                    byte[] nb = new byte[message.length + end.length];
                    System.arraycopy(message, 0, nb, 0, message.length);
                    System.arraycopy(end, 0, nb, message.length, nb.length);

                    return nb;
                }

                @Override
                public byte[] filter(AtmosphereResponse r, byte[] message, int offset, int length) {
                    byte[] nb = new byte[length + end.length];
                    System.arraycopy(message, offset, nb, 0, length);
                    System.arraycopy(end, 0, nb, length, nb.length);

                    return nb;
                }
            });
        }
        return Action.CONTINUE;
    }

    @Override
    public String toString() {
        return endString + " End Message Interceptor";
    }
}
