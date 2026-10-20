/* NutRestProviderTest.java

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
 */
package org.networkupstools.jnutwebapi;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import junit.framework.TestCase;
import org.apache.wink.common.internal.lifecycle.JSR250LifecycleManager;
import org.apache.wink.common.internal.lifecycle.LifecycleManagersRegistry;
import org.apache.wink.common.internal.lifecycle.ObjectFactory;

public class NutRestProviderTest extends TestCase {

    public void testRequestCleanupAndIsolation() throws Exception {
        LifecycleManagersRegistry registry = new LifecycleManagersRegistry();
        registry.addFactoryFactory(
                new JSR250LifecycleManager<NutRestProvider>());
        ObjectFactory<NutRestProvider> factory =
                registry.getObjectFactory(NutRestProvider.class);
        NutRestProvider first = factory.getInstance(null);
        NutRestProvider second = factory.getInstance(null);
        assertNotSame(first, second);

        try (ServerSocket listener = new ServerSocket(
                0, 2, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(2000);
            String address = "127.0.0.1:" + listener.getLocalPort();
            assertNotNull(first.getServers(address));
            assertNotNull(second.getServers(address));
            try (Socket firstPeer = listener.accept();
                    Socket secondPeer = listener.accept()) {
                firstPeer.setSoTimeout(2000);
                secondPeer.setSoTimeout(2000);
                factory.releaseInstance(first, null);
                assertEquals(-1, firstPeer.getInputStream().read());

                // Releasing one request must not close another's client.
                try {
                    secondPeer.getInputStream().read();
                    fail("The other request's connection was closed");
                } catch (SocketTimeoutException expected) {
                    // The second connection is still open and idle.
                }

                factory.releaseInstance(second, null);
                assertEquals(-1, secondPeer.getInputStream().read());
            }
        } finally {
            factory.releaseInstance(first, null);
            factory.releaseInstance(second, null);
        }

        // A root request need not open a connection.
        factory.releaseInstance(factory.getInstance(null), null);
    }
}
