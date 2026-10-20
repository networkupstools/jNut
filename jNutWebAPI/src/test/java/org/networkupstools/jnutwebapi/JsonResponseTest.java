/* JsonResponseTest.java

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.
*/
package org.networkupstools.jnutwebapi;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import junit.framework.TestCase;
import org.networkupstools.jnut.Client;
import org.networkupstools.jnut.Device;
import org.networkupstools.jnut.Scanner;
import org.networkupstools.jnut.Variable;

public class JsonResponseTest extends TestCase {
    private static final String TEXT = "\"quote\"\\\b\t\n\f\r\u0001'/"
        + "\u00e9\u96ea\ud83d\udd0c";
    private static final String JSON = "\"\\\"quote\\\"\\\\\\b\\t\\n\\f\\r"
        + "\\u0001'/\\u00E9\\u96EA\\uD83D\\uDD0C\"";

    public void testQuote() {
        assertEquals(JSON, Json.quote(TEXT));
        assertEquals("\"\"", Json.quote(""));
        assertEquals("null", Json.quote(null));
        assertEquals("\"null\"", Json.quote("null"));
        assertEquals("\"\\\\n\"", Json.quote("\\n"));
    }

    private static class Fixture extends Client {
        boolean fail;
        boolean empty;
        boolean noDescription;
        String value = TEXT;
        final Variable variable = new Variable(TEXT, null) {
            @Override
            public String getValue() throws IOException {
                check();
                return value;
            }
            @Override
            public String getDescription() throws IOException {
                check();
                return noDescription ? null : TEXT;
            }
        };
        final Device device = new Device(TEXT, null) {
            @Override
            public String getDescription() throws IOException {
                check();
                return noDescription ? null : "\""
                    + TEXT.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            @Override
            public Variable[] getVariableList() throws IOException {
                check();
                return empty ? new Variable[0] : new Variable[] {variable};
            }
            @Override
            public Variable getVariable(String name) throws IOException {
                check();
                return variable;
            }
        };
        void check() throws IOException {
            if (fail) {
                throw new IOException("Synthetic backend error");
            }
        }
        @Override
        public Device[] getDeviceList() throws IOException {
            check();
            return empty ? new Device[0] : new Device[] {device};
        }
        @Override
        public Device getDevice(String name) throws IOException {
            check();
            return device;
        }
    }

    public void testRestStringsAndErrors() throws Exception {
        // Only satisfy Server's connection constructor; no backend daemon.
        try (ServerSocket listener = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            NutRestProvider.Server server = new NutRestProvider().new Server(
                "127.0.0.1:" + listener.getLocalPort());
            server.client.disconnect();
            Fixture fixture = new Fixture();
            server.client = fixture;
            NutRestProvider.Server.Dev device = server.getDev(TEXT);
            NutRestProvider.Server.Dev.Var variable = device.getVar(TEXT);
            assertEquals("[\n" + JSON + "\n]", server.getDeviceList());
            assertEquals(JSON, device.getDescription());
            assertEquals(JSON, device.getDescriptionShortcut());
            assertEquals("[\n" + JSON + "\n]", device.getVars());
            assertEquals(JSON, variable.getValue());
            assertEquals(JSON, variable.getDescription());
            fixture.value = null;
            assertEquals("null", variable.getValue());
            fixture.value = "null";
            assertEquals("\"null\"", variable.getValue());
            fixture.value = "";
            assertEquals("\"\"", variable.getValue());
            fixture.empty = true;
            assertEquals("[\n]", server.getDeviceList());
            assertEquals("[\n]", device.getVars());
            fixture.noDescription = true;
            assertNull(device.getDescription());
            assertNull(variable.getDescription());
            fixture.fail = true;
            assertNull(server.getDeviceList());
            assertNull(server.getDev(TEXT));
            assertNull(device.getDescription());
            assertNull(device.getVars());
            assertNull(device.getVar(TEXT));
            assertNull(variable.getValue());
            assertNull(variable.getDescription());
        }
    }

    public void testScannerStrings() throws Exception {
        Method parse = Scanner.class.getDeclaredMethod(
            "scanLine", String.class);
        parse.setAccessible(true);
        Scanner.DiscoveredDevice device =
            (Scanner.DiscoveredDevice) parse.invoke(null,
                "SNMP:driver=\"snmp-ups\",port=\"path\\\\to\\\"ups\""
                    + ",desc=\"ignored\"");
        assertNotNull(device);
        assertEquals("[\n{ \"driver\":\"snmp-ups\", "
            + "\"port\":\"path\\\\to\\\"ups\" }\n]",
            ScannerProvider.toJson(new Scanner.DiscoveredDevice[] {device}));
        assertEquals("[\n]",
            ScannerProvider.toJson(new Scanner.DiscoveredDevice[0]));
        Scanner.DiscoveredDevice missing =
            (Scanner.DiscoveredDevice) parse.invoke(
                null, "USB:vendor=\"control\"");
        assertEquals("[\n{ \"driver\":null, \"port\":null }\n]",
            ScannerProvider.toJson(new Scanner.DiscoveredDevice[] {missing}));
    }
}
