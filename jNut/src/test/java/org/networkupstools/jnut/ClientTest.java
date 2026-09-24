/* ClientTest.java

   Copyright (C) 2011 Eaton

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
*/
package org.networkupstools.jnut;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class ClientTest extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ClientTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ClientTest.class );
    }

    public void testTrackingOnEachConnection() throws Exception
    {
        Client client = new Client();
        try (ServerSocket server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3000);
            client.connect("127.0.0.1", server.getLocalPort());
            for (int session = 0; session < 4; session++) {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(3000);
                    BufferedReader input = new BufferedReader(
                        new InputStreamReader(peer.getInputStream(), "UTF-8"));
                    OutputStreamWriter output = new OutputStreamWriter(
                        peer.getOutputStream(), "UTF-8");
                    assertFalse("New connection must enable tracking again",
                        client.isTrackingEnabled());

                    // Queue replies so no background server thread is needed.
                    output.write("OK\nOK TRACKING id\nSUCCESS\n"
                        + "OK TRACKING id\nSUCCESS\nOK\n"
                        + "ERR ACCESS-DENIED\nOK\n");
                    output.flush();
                    peer.shutdownOutput();
                    Device device = new Device("ups", client);
                    Variable variable = new Variable("driver.debug", device);
                    assertNull(variable.setValue("1", 1, 1));
                    assertTrue(client.isTrackingEnabled());
                    assertEquals("SET TRACKING ON", input.readLine());
                    assertEquals("SET VAR ups driver.debug  \"1\"",
                        input.readLine());
                    assertEquals("GET TRACKING id", input.readLine());

                    // An active session must not enable tracking a second time.
                    Command command = new Command("test.command", device);
                    assertNull(command.execute(null, 1, 1));
                    assertEquals("INSTCMD ups test.command", input.readLine());
                    assertEquals("GET TRACKING id", input.readLine());
                    client.setTracking(false);
                    assertFalse(client.isTrackingEnabled());
                    assertEquals("SET TRACKING OFF", input.readLine());
                    try {
                        client.setTracking(true);
                        fail("Expected rejected tracking request");
                    } catch (NutException expected) {
                        assertTrue(expected.is("ACCESS-DENIED"));
                    }
                    assertFalse(client.isTrackingEnabled());
                    assertEquals("SET TRACKING ON", input.readLine());
                    assertTrue(client.enableTrackingModeOnce());
                    assertEquals("SET TRACKING ON", input.readLine());

                    if (session == 0) {
                        client.disconnect();
                        client.connect("127.0.0.1", server.getLocalPort());
                    } else if (session == 1) {
                        client.logout();
                        assertEquals("LOGOUT", input.readLine());
                        client.connect("127.0.0.1", server.getLocalPort(),
                            null, null);
                    } else if (session == 2) {
                        client.connect();
                    } else {
                        server.close();
                        try {
                            client.connect();
                            fail("Expected refused replacement connection");
                        } catch (ConnectException expected) {
                            assertFalse(client.isConnected());
                            assertFalse(client.isTrackingEnabled());
                        }
                    }
                    assertNull("Previous connection must close",
                        input.readLine());
                }
            }
        } finally {
            client.disconnect();
        }
    }

    /**
     * Escape function test.
     */
    public void testEscape()
    {
        assertEquals("Empty string", "", Client.escape(""));
        assertEquals("Simple string", "hello", Client.escape("hello"));
        assertEquals("Internal doublequote", "he\\\"llo", Client.escape("he\"llo"));
        assertEquals("Internal backslash", "he\\\\llo", Client.escape("he\\llo"));
        assertEquals("Internal backslash and doublequote", "he\\\\\\\"llo", Client.escape("he\\\"llo"));
        assertEquals("Initial and final doublequote", "\\\"hello\\\"", Client.escape("\"hello\""));
    }

    /**
     * Unescape function test.
     */
    public void testUnescape()
    {
        assertEquals("Empty string", "", Client.unescape(""));
        assertEquals("Simple string", "hello", Client.unescape("hello"));
        assertEquals("Internal doublequote", "he\"llo", Client.unescape("he\\\"llo"));
        assertEquals("Internal backslash", "he\\llo", Client.unescape("he\\\\llo"));
        assertEquals("Internal backslash and doublequote", "he\\\"llo", Client.unescape("he\\\\\\\"llo"));
        assertEquals("Initial and final doublequote", "\"hello\"", Client.unescape("\\\"hello\\\""));
    }

    /**
     * extractDoublequotedValue function test.
     */
    public void testExtractDoublequotedValue()
    {
        assertNull("Empty string", Client.extractDoublequotedValue(""));
        assertNull("Non doublequoted string", Client.extractDoublequotedValue("hello"));
        assertNull("No begining doublequote", Client.extractDoublequotedValue("hello\""));
        assertNull("No ending doublequote", Client.extractDoublequotedValue("\"hello"));
        assertEquals("Simple string", "hello", Client.extractDoublequotedValue("\"hello\""));
        assertEquals("String with doublequote", "he\"llo", Client.extractDoublequotedValue("\"he\\\"llo\""));
        assertEquals("String with backslash", "he\\llo", Client.extractDoublequotedValue("\"he\\\\llo\""));
        assertEquals("String with backslash and doublequote", "he\\\"llo", Client.extractDoublequotedValue("\"he\\\\\\\"llo\""));
    }

    /**
     * splitNameValueString function test.
     */
    public void testSplitNameValueString()
    {
        String[] res;
        assertNull("Empty string", Client.splitNameValueString(""));
        assertNull("One word string", Client.splitNameValueString("name"));
        assertNull("Non doublequoted string", Client.extractDoublequotedValue("name value"));
        assertNull("No begining doublequote", Client.extractDoublequotedValue("name value\""));
        assertNull("No ending doublequote", Client.extractDoublequotedValue("name \"value"));
        res = Client.splitNameValueString("name \"value\"");
        assertEquals("Simple name/value (name)", "name", res[0]);
        assertEquals("Simple name/value (value)", "value", res[1]);
        res = Client.splitNameValueString("name \"complex value\"");
        assertEquals("Simple name / complex value (name)", "name", res[0]);
        assertEquals("Simple name / complex value (value)", "complex value", res[1]);
        res = Client.splitNameValueString("name \"complex\\\\value\"");
        assertEquals("Simple name / backslash value (name)", "name", res[0]);
        assertEquals("Simple name / backslash value (value)", "complex\\value", res[1]);
        res = Client.splitNameValueString("name \"complex\\\"value\"");
        assertEquals("Simple name / doublequote value (name)", "name", res[0]);
        assertEquals("Simple name / doublequote value (value)", "complex\"value", res[1]);
    }
}
