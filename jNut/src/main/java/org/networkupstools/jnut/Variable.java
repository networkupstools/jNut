/* Variable.java

   Copyright (C) 2011 Eaton
   Copyright (C) 2026- Jim Klimov <jimklimov+nut@gmail.com>

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

import java.io.IOException;

/**
 * Class representing a variable of a device.
 * <p>
 * It can be used to get and set its value (if possible).
 * A Variable object can be retrieved from a {@link Device} instance
 * and cannot be constructed directly.
 *
 * @author <a href="mailto:EmilienKia@eaton.com">Emilien Kia</a>
 */
public class Variable {
    /**
     * Device to which this variable is attached
     */
    Device device = null;

    /**
     * Variable name
     */
    String name = null;

    /**
     * Internally create a variable.
     * @param name Variable name.
     * @param device Device to which the variable is attached.
     */
    protected Variable(String name, Device device)
    {
        this.device = device;
        this.name   = name;
    }

    /**
     * Return the device to which the variable is related.
     * @return Attached device.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Return the variable name.
     * @return Command name.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieve the variable value from UPSD and store it in a cache.
     * @return Variable value
     * @throws IOException
     */
    public String getValue() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String res = device.getClient().get("VAR", params);
            return res!=null?Client.extractDoublequotedValue(res):null;
        }
        return null;
    }

    /**
     * Retrieve the variable description from UPSD and store it in a cache.
     * @return Variable description
     * @throws IOException
     */
    public String getDescription() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String res = device.getClient().get("DESC", params);
            return res!=null?Client.extractDoublequotedValue(res):null;
        }
        return null;
    }

    /**
     * Set the variable value.
     * Note the new value can be applied with a little delay,
     * depending on UPSD and connection.
     * @param value New value for the variable
     * @return Tracking ID if tracking is enabled, or null.
     * @throws IOException
     */
    public TrackingID setValue(String value) throws IOException, NutException {
        return setValue(value, -1, -1);
    }

    /**
     * Set the variable value, optionally waiting for completion.
     * @param value New value for the variable.
     * @param waitIntervalSec Interval between checks in seconds (if >= 1).
     * @param waitMaxCount Maximum number of checks (if >= 1).
     * @return Tracking ID if tracking is enabled (and not waiting), or null.
     * @throws IOException
     * @throws NutException
     */
    public TrackingID setValue(String value, int waitIntervalSec, int waitMaxCount) throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            Client client = device.getClient();
            boolean doWait = waitIntervalSec >= 1 && waitMaxCount >= 1;
            if (doWait) {
                client.enableTrackingModeOnce();
            }

            String[] params = {"VAR", device.getName(),
                    name, " \"" + Client.escape(value) + "\""};
            String res = client.query("SET", params);
            if(!res.startsWith("OK"))
            {
                // Normally the response should be OK or ERR and nothing else.
                throw new NutException(NutException.UnknownResponse, "Unknown response in Variable.setValue : " + res);
            }
            TrackingID tid = client.getLastTrackingId();
            if (doWait && tid != null && tid.isValid()) {
                if (client.waitTrackingResult(tid, waitIntervalSec, waitMaxCount)) {
                    return null;
                }
            }
            return tid;
        }
        return null;
    }

    /**
     * Retrieve the variable type from UPSD.
     * @return Variable type string.
     * @throws IOException
     * @throws NutException
     */
    public String getType() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String res = device.getClient().get("TYPE", params);
            // TYPE <ups> <var> <type>
            return res;
        }
        return null;
    }

    /**
     * Retrieve the list of possible values for an ENUM variable.
     * @return List of values.
     * @throws IOException
     * @throws NutException
     */
    public String[] getEnumList() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String[] res = device.getClient().list("ENUM", params);
            if(res == null) return new String[0];
            String[] list = new String[res.length];
            for(int i=0; i<res.length; i++) {
                // ENUM <ups> <var> "<value>"
                list[i] = Client.extractDoublequotedValue(res[i]);
            }
            return list;
        }
        return null;
    }

    /**
     * Retrieve the list of possible ranges for a RANGE variable.
     * @return List of range strings or structured data.
     * @throws IOException
     * @throws NutException
     */
    public String[] getRangeList() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String[] res = device.getClient().list("RANGE", params);
            if(res == null) return new String[0];
            return res; // RANGE <ups> <var> "<min>" "<max>"
        }
        return null;
    }
}
