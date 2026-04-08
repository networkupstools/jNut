/* Command.java

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
 * Class representing a command of a device.
 * <p>
 * It can be used to retrieve description and execute commands.
 * A Command object can be retrieved from Device instance and can not be constructed directly.
 *
 * @author <a href="mailto:EmilienKia@eaton.com">Emilien Kia</a>
 */
public class Command {
    /**
     * Device to which command is attached
     */
    Device device = null;

    /**
     * Command name
     */
    String name = null;

    /**
     * Internally create a command.
     * @param name Command name.
     * @param device Device to which the command is attached.
     */
    protected Command(String name, Device device)
    {
        this.device = device;
        this.name   = name;
    }

    /**
     * Return the device to which the command can be executed.
     * @return Attached device.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Return the command name.
     * @return Command name.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieve the command description from UPSD and store it in cache.
     * @return Command description
     * @throws IOException
     */
    public String getDescription() throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params = {device.getName(), name};
            String res = device.getClient().get("CMDDESC", params);
            return res!=null?Client.extractDoublequotedValue(res):null;
        }
        return null;
    }

    /**
     * Execute the instant command without any optional argument.
     * @return Tracking ID if tracking is enabled, or null.
     * @throws IOException
     */
    public TrackingID execute() throws IOException, NutException {
        return execute(null);
    }

    /**
     * Execute the instant command with an optional parameter.
     * @param param Command parameter (may be null to not send it; however an empty string may be a valid value for server side).
     * @return Tracking ID if tracking is enabled, or null.
     * @throws IOException
     */
    public TrackingID execute(String param) throws IOException, NutException {
        if(device!=null && device.getClient()!=null)
        {
            String[] params;
            if (param != null) {
                params = new String[]{device.getName(), name, param};
            } else {
                params = new String[]{device.getName(), name};
            }
            String res = device.getClient().query("INSTCMD", params);
            if(!res.startsWith("OK"))
            {
                // Normaly response should be OK or ERR and nothing else.
                throw new NutException(NutException.UnknownResponse, "Unknown response in Command.execute : " + res);
            }
            return device.getClient().getLastTrackingId();
        }
        return null;
    }
}
