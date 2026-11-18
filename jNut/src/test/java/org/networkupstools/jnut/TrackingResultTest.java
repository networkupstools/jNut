/* TrackingResultTest.java
 *
 * SPDX-License-Identifier: GPL-2.0-or-later WITH Classpath-exception-2.0
 */
package org.networkupstools.jnut;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TrackingResultTest {
    private Client client = new Client();
    private final TrackingID id = new TrackingID("1bd31808-cb49-4aec-9d75-d056e6f018d2");
    private Socket peer;
    private BufferedReader requests;

    private static class ObservingClient extends Client {
        int waits;

        @Override
        public boolean waitTrackingResult(TrackingID id, int interval, int count)
                throws IOException, NutException {
            waits++;
            // Client code may reconstruct an ID from its wire value.
            return super.waitTrackingResult(new TrackingID(id.getId()), interval, count);
        }
    }

    // Queue the replies and end the stream so an extra poll cannot hang.
    private void reply(String lines) throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            server.setSoTimeout(3000);
            client.connect(loopback.getHostAddress(), server.getLocalPort());
            peer = server.accept();
            peer.setSoTimeout(3000);
            requests = new BufferedReader(new InputStreamReader(
                peer.getInputStream(), StandardCharsets.UTF_8));
        }
        peer.getOutputStream().write(lines.getBytes(StandardCharsets.UTF_8));
        peer.shutdownOutput();
    }

    @After
    public void disconnect() throws IOException {
        try {
            client.disconnect();
        } finally {
            if (peer != null) peer.close();
        }
    }

    private void assertPoll() throws IOException {
        assertEquals("GET TRACKING " + id.getId(), requests.readLine());
    }

    @Test
    public void succeedsImmediately() throws Exception {
        reply("SUCCESS\n");
        assertTrue(client.waitTrackingResult(id, 1, 1));
        assertPoll();
    }

    @Test
    public void pollsAgainWhilePending() throws Exception {
        reply("PENDING\nSUCCESS\n");
        assertTrue(client.waitTrackingResult(id, 1, 2));
        assertPoll();
        assertPoll();
    }

    @Test
    public void reportsTerminalTrackingFailures() throws Exception {
        reply("ERR FAILED\nERR INVALID-ARGUMENT\nERR UNKNOWN\n");
        for (String code : new String[] { "FAILED", "INVALID-ARGUMENT", "UNKNOWN" }) {
            assertFalse(code, client.waitTrackingResult(id, 1, 2));
            assertPoll();
        }
    }

    @Test
    public void preservesFailureDetailsForSinglePoll() throws Exception {
        reply("ERR FAILED\n");
        NutException failure = assertThrows(NutException.class, () -> client.getTrackingResult(id));
        assertTrue(failure.is("FAILED"));
        assertPoll();
    }

    @Test
    public void propagatesTrackingQueryErrors() throws Exception {
        reply("ERR FEATURE-NOT-CONFIGURED\nOFF\n");
        NutException disabled = assertThrows(NutException.class,
            () -> client.waitTrackingResult(id, 1, 1));
        assertTrue(disabled.is("FEATURE-NOT-CONFIGURED"));
        assertPoll();
        NutException unexpected = assertThrows(NutException.class,
            () -> client.waitTrackingResult(id, 1, 1));
        assertTrue(unexpected.is(NutException.UnknownResponse));
        assertPoll();
    }

    @Test
    public void reportsDisconnectedClient() throws Exception {
        reply("");
        client.disconnect();
        NutException failure = assertThrows(NutException.class,
            () -> client.waitTrackingResult(id, 1, 1));
        assertTrue(failure.is(NutException.ServerNotConnected));
    }

    @Test
    public void trackedWriteReturnsIdIfStillPending() throws Exception {
        reply("OK\nOK TRACKING " + id + "\nPENDING\nOK TRACKING " + id + "\nSUCCESS\n");
        Command command = new Command("test.command", new Device("ups", client));
        assertEquals(id, command.execute(null, 1, 1));
        assertNull(command.execute(null, 1, 1));
    }

    @Test
    public void trackedWritesPreserveFailuresWhenOverrideCopiesId() throws Exception {
        ObservingClient observer = new ObservingClient();
        client = observer;
        reply("OK\nOK TRACKING " + id + "\nERR INVALID-ARGUMENT invalid setting\nOK TRACKING " + id + "\nERR FAILED\nERR FAILED\n");
        Device device = new Device("ups", client);
        Command command = new Command("test.command", device);
        Variable variable = new Variable("driver.debug", device);
        NutException cmdError = assertThrows(NutException.class, () -> command.execute(null, 1, 1));
        assertTrue(cmdError.is("INVALID-ARGUMENT"));
        assertEquals("invalid setting", cmdError.getExtra());
        NutException setError = assertThrows(NutException.class, () -> variable.setValue("1", 1, 1));
        assertTrue(setError.is("FAILED"));
        assertEquals(2, observer.waits);

        // A subsequent direct wait on the same object has Boolean semantics.
        assertFalse(client.waitTrackingResult(client.getLastTrackingId(), 1, 1));
    }

    @Test
    public void nestedWriteHonorsOverrideAndRestoresOuterWait() throws Exception {
        client = new Client() {
            @Override
            public boolean waitTrackingResult(TrackingID token, int interval, int count)
                    throws IOException, NutException {
                if ("inner".equals(token.getId())) {
                    return false; // The override leaves this operation pending.
                }
                assertFalse(super.waitTrackingResult(new TrackingID("other"), interval, count));
                Command inner = new Command("inner.command", new Device("ups", this));
                assertEquals(new TrackingID("inner"), inner.execute(null, interval, count));
                return super.waitTrackingResult(token, interval, count);
            }
        };
        reply("OK\nOK TRACKING outer\nERR UNKNOWN\nOK TRACKING inner\nERR FAILED\n");
        Command outer = new Command("outer.command", new Device("ups", client));
        NutException failure = assertThrows(NutException.class, () -> outer.execute(null, 1, 1));
        assertTrue(failure.is("FAILED"));
        assertEquals("SET TRACKING ON", requests.readLine());
        assertEquals("INSTCMD ups outer.command", requests.readLine());
        assertEquals("GET TRACKING other", requests.readLine());
        assertEquals("INSTCMD ups inner.command", requests.readLine());
        assertEquals("GET TRACKING outer", requests.readLine());
    }
}
