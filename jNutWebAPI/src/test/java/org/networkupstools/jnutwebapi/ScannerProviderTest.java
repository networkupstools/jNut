/* ScannerProviderTest.java

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.
*/
package org.networkupstools.jnutwebapi;

import java.lang.reflect.Method;
import junit.framework.TestCase;

public class ScannerProviderTest extends TestCase {
    private boolean accepts(String methodName, String value) throws Exception {
        Method method = ScannerProvider.class.getDeclaredMethod(
                methodName, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, value);
    }

    public void testEveryOctet() throws Exception {
        for (int position = 0; position < 4; position++) {
            for (int value = 0; value <= 255; value++) {
                int[] octets = {192, 168, 1, 1};
                octets[position] = value;
                String address = octets[0] + "." + octets[1] + "."
                        + octets[2] + "." + octets[3];
                assertTrue(address, accepts("isValidIPv4", address));
                assertTrue(address, accepts("isValidMask", address));
            }
        }
    }

    public void testEveryPrefix() throws Exception {
        for (String address : new String[] {"0.0.0.0", "255.255.255.255"}) {
            for (int prefix = 0; prefix <= 32; prefix++) {
                String mask = address + "/" + prefix;
                assertTrue(mask, accepts("isValidMask", mask));
                assertFalse(mask, accepts("isValidIPv4", mask));
            }
        }
    }

    public void testInvalidAddresses() throws Exception {
        String[] invalid = {null, "", "127.0.0", "127.0.0.1.2",
            "127..0.1", "127x0x0x1", "localhost", "::1", "0x7f000001",
            "127.0.0.1 ", " 127.0.0.1", "127.0.0.1\n", "127.0.0.1\r\n",
            "127.0.0.1\t", "127.0.0.1\u0000", "127.0.0.\u0661",
            "127.0.0.\uff11", "127.0.0.1\u2028", "127.0.0.1\\",
            "--help", "127.0.0.1 -C", "127.0.0.1;id", "$(id)", "`id`"};
        for (String address : invalid) {
            assertFalse(address, accepts("isValidIPv4", address));
            assertFalse(address, accepts("isValidMask", address));
            assertFalse(address, accepts("isValidMask", address + "/24"));
        }
        for (int position = 0; position < 4; position++) {
            for (String value : new String[] {
                    "-1", "256", "999", "01", "00", "001", "+1", "1e2"}) {
                String[] octets = {"192", "168", "1", "1"};
                octets[position] = value;
                String address = octets[0] + "." + octets[1] + "."
                        + octets[2] + "." + octets[3];
                assertFalse(address, accepts("isValidIPv4", address));
                assertFalse(address, accepts("isValidMask", address + "/24"));
            }
        }
    }

    public void testInvalidPrefixes() throws Exception {
        for (String prefix : new String[] {"", "-1", "33", "9999999999",
                "00", "01", "+1", "1.0", "24/1", "24 ", " 24", "24\n",
                "24\r\n", "\u0661", "\uff11", "24;id", "24 -C"}) {
            String mask = "127.0.0.1/" + prefix;
            assertFalse(mask, accepts("isValidMask", mask));
        }
        assertFalse(accepts("isValidMask", "/24"));
        assertFalse(accepts("isValidMask", "127.0.0.1//24"));
    }
}
