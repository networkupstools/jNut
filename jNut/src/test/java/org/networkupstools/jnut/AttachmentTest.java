/* AttachmentTest.java

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.
*/
package org.networkupstools.jnut;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import junit.framework.TestCase;

public class AttachmentTest extends TestCase {
    private static final String NO_REPLY = "<no reply>";

    /** A peer which checks every request, including the absence of a retry. */
    private static class Session implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final Client client = new Client();
        final Device device = new Device("ups", client);
        final Thread peer;
        Throwable failure;

        Session(final String[][] dialog) throws Exception {
            server.setSoTimeout(5000);
            peer = new Thread(new Runnable() {
                public void run() {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5000);
                        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                        OutputStreamWriter output = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
                        for (String[] exchange : dialog) {
                            assertEquals(exchange[0], input.readLine());
                            if (exchange[1] == null) {
                                return;
                            }
                            if (!NO_REPLY.equals(exchange[1])) {
                                output.write(exchange[1] + "\n");
                                output.flush();
                            }
                        }
                        assertNull("Unexpected extra command", input.readLine());
                    } catch (Throwable ex) {
                        failure = ex;
                    }
                }
            });
            peer.start();
            client.connect("127.0.0.1", server.getLocalPort());
            setReadTimeout(3000);
        }

        void setReadTimeout(int millis) throws Exception {
            // Bound a deliberately silent peer without changing the public API.
            Field field = Client.class.getDeclaredField("socket");
            field.setAccessible(true);
            ((StringLineSocket) field.get(client)).socket.setSoTimeout(millis);
        }

        public void close() throws Exception {
            client.disconnect();
            server.close();
            peer.join(6000);
            assertFalse("Peer did not finish", peer.isAlive());
            if (failure != null) {
                throw new AssertionError(failure);
            }
        }
    }

    public void testAttachAndLegacyFallback() throws Exception {
        try (Session s = new Session(new String[][] {{"ATTACH ups", "OK"}})) {
            s.device.attach();
        }
        try (Session s = new Session(new String[][] {
                {"ATTACH ups", "ERR UNKNOWN-COMMAND"}, {"LOGIN ups", "OK"}})) {
            s.device.attach();
        }
        try (Session s = new Session(new String[][] {
                {"ATTACH ups", "ERR UNKNOWN-COMMAND"}, {"LOGIN ups", "ERR ALREADY-LOGGED-IN"}})) {
            try {
                s.device.attach();
                fail("Expected legacy error");
            } catch (NutException ex) {
                assertTrue(ex.is("ALREADY-LOGGED-IN"));
            }
        }
    }

    public void testAttachDoesNotRetryOtherErrors() throws Exception {
        String[] errors = {"ACCESS-DENIED", "PASSWORD-REQUIRED", "USERNAME-REQUIRED",
            "ALREADY-ATTACHED", "ALREADY-LOGGED-IN", "UNKNOWN-UPS", "INVALID-ARGUMENT",
            "UNKNOWN-COMMAND-OTHER"};
        for (String error : errors) {
            try (Session s = new Session(new String[][] {{"ATTACH ups", "ERR " + error}})) {
                try {
                    s.device.attach();
                    fail("Expected " + error);
                } catch (NutException ex) {
                    assertTrue(ex.is(error));
                }
            }
        }
        try (Session s = new Session(new String[][] {{"ATTACH ups", "OKAY"}})) {
            try {
                s.device.attach();
                fail("Expected malformed response error");
            } catch (NutException ex) {
                assertTrue(ex.is(NutException.UnknownResponse));
            }
        }
    }

    public void testAttachDoesNotRetryLostOrDelayedReply() throws Exception {
        try (Session s = new Session(new String[][] {{"ATTACH ups", null}})) {
            try {
                s.device.attach();
                fail("Expected EOF");
            } catch (EOFException expected) {
            }
        }
        try (Session s = new Session(new String[][] {{"ATTACH ups", NO_REPLY}})) {
            s.setReadTimeout(200);
            try {
                s.device.attach();
                fail("Expected timeout");
            } catch (SocketTimeoutException expected) {
            }
        }
    }

    public void testDetachAndLegacyFallbackCloseSession() throws Exception {
        try (Session s = new Session(new String[][] {{"DETACH", "OK Goodbye"}})) {
            s.client.detach();
            assertFalse(s.client.isConnected());
            s.client.detach();
        }
        try (Session s = new Session(new String[][] {
                {"DETACH", "ERR UNKNOWN-COMMAND"}, {"LOGOUT", "OK Goodbye"}})) {
            s.client.detach();
            assertFalse(s.client.isConnected());
        }
    }

    public void testDetachClosesWithoutRetryOnFailure() throws Exception {
        String[] replies = {"ERR ACCESS-DENIED", "ERR INVALID-ARGUMENT", "OKAY"};
        for (String reply : replies) {
            try (Session s = new Session(new String[][] {{"DETACH", reply}})) {
                try {
                    s.client.detach();
                    fail("Expected failure");
                } catch (NutException expected) {
                    assertFalse(s.client.isConnected());
                }
            }
        }
        try (Session s = new Session(new String[][] {{"DETACH", null}})) {
            try {
                s.client.detach();
                fail("Expected EOF");
            } catch (EOFException expected) {
                assertFalse(s.client.isConnected());
            }
        }
        try (Session s = new Session(new String[][] {{"DETACH", NO_REPLY}})) {
            s.setReadTimeout(200);
            try {
                s.client.detach();
                fail("Expected timeout");
            } catch (SocketTimeoutException expected) {
                assertFalse(s.client.isConnected());
            }
        }
    }

    public void testNumberOfAttachmentsAndLegacyFallback() throws Exception {
        try (Session s = new Session(new String[][] {{"GET NUMATTACH ups", "NUMATTACH ups 2"}})) {
            assertEquals(2, s.device.getNumAttach());
        }
        try (Session s = new Session(new String[][] {
                {"GET NUMATTACH ups", "ERR INVALID-ARGUMENT"}, {"GET NUMLOGINS ups", "NUMLOGINS ups 1"}})) {
            assertEquals(1, s.device.getNumAttach());
        }
        try (Session s = new Session(new String[][] {{"GET NUMATTACH ups", "NUMATTACH ups invalid"}})) {
            assertEquals(-1, s.device.getNumAttach());
        }
    }

    public void testNumberOfAttachmentsDoesNotRetryUnrelatedErrors() throws Exception {
        String[] replies = {"ERR UNKNOWN-UPS", "ERR ACCESS-DENIED", "NUMLOGINS ups 2",
            "NUMATTACH other-ups 2"};
        for (String reply : replies) {
            try (Session s = new Session(new String[][] {{"GET NUMATTACH ups", reply}})) {
                try {
                    s.device.getNumAttach();
                    fail("Expected failure for " + reply);
                } catch (NutException ex) {
                    assertTrue(reply.startsWith("ERR ")
                        ? ex.is(reply.substring(4)) : ex.is(NutException.UnknownResponse));
                }
            }
        }
    }

    public void testNumberOfAttachmentsDoesNotRetryLostOrDelayedReply() throws Exception {
        try (Session s = new Session(new String[][] {{"GET NUMATTACH ups", null}})) {
            try {
                s.device.getNumAttach();
                fail("Expected missing response error");
            } catch (NutException ex) {
                assertTrue(ex.is(NutException.UnknownResponse));
            }
        }
        try (Session s = new Session(new String[][] {{"GET NUMATTACH ups", NO_REPLY}})) {
            s.setReadTimeout(200);
            try {
                s.device.getNumAttach();
                fail("Expected timeout");
            } catch (SocketTimeoutException expected) {
            }
        }
    }

    public void testLegacyMethodsKeepLegacyCommands() throws Exception {
        try (Session s = new Session(new String[][] {
                {"LOGIN ups", "OK"}, {"GET NUMLOGINS ups", "NUMLOGINS ups 1"}, {"LOGOUT", NO_REPLY}})) {
            s.device.login();
            assertEquals(1, s.device.getNumLogin());
            s.client.logout();
            assertFalse(s.client.isConnected());
        }
        try (Session s = new Session(new String[][] {{"LOGIN ups", "ERR ALREADY-LOGGED-IN"}})) {
            try {
                s.device.login();
                fail("Expected legacy duplicate error");
            } catch (NutException ex) {
                assertTrue(ex.is("ALREADY-LOGGED-IN"));
            }
        }
    }
}
