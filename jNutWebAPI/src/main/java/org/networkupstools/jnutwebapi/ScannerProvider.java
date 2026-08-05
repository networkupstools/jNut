/* ScannerProvider.java

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
package org.networkupstools.jnutwebapi;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import org.networkupstools.jnut.Scanner;
import org.networkupstools.jnut.Scanner.DiscoveredDevice;


/**
 * Apache Wink WS REST provider for nut-scanner.
 * @author <a href="mailto:EmilienKia@eaton.com">Emilien Kia</a>
 */
@Path("/scan")
public class ScannerProvider {
    static final String execName = "/usr/local/ups/bin/nut-scanner";
    static final String execPath = "";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\\\d|1\\\\d\\\\d|[1-9]?\\\\d)(\\\\.(25[0-5]|2[0-4]\\\\d|1\\\\d\\\\d|[1-9]?\\\\d)){3}$");
    private static final Pattern CIDR_PATTERN = Pattern.compile("^([0-9]|[1-2][0-9]|3[0-2])$");

    private static boolean isValidIPv4(String value) {
        return value != null && IPV4_PATTERN.matcher(value).matches();
    }

    private static boolean isValidMask(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.split("/", -1);
        if (parts.length == 2) {
            return isValidIPv4(parts[0]) && CIDR_PATTERN.matcher(parts[1]).matches();
        }
        return isValidIPv4(value);
    }

    @GET
    @Produces("application/json")
    public String get(@QueryParam("start") String start,
                      @QueryParam("end") String end,
                      @QueryParam("mask") String mask
                     ) throws WebApplicationException{
        Scanner scan = new Scanner();

        if(execName!=null && execName.length()>0)
            scan.setExecName(execName);
        if(execPath!=null && execPath.length()>0)
            scan.setExecPath(execPath);
        if(start!=null && start.length()>0) {
            if (!isValidIPv4(start)) {
                throw new WebApplicationException(400);
            }
            scan.setParam(Scanner.OPTION_START_IP, start);
        }
        if(end!=null && end.length()>0) {
            if (!isValidIPv4(end)) {
                throw new WebApplicationException(400);
            }
            scan.setParam(Scanner.OPTION_END_IP, end);
        }
        if(mask!=null && mask.length()>0) {
            if (!isValidMask(mask)) {
                throw new WebApplicationException(400);
            }
            scan.setParam(Scanner.OPTION_MASK_CIDR, mask);
        }

        try {
            DiscoveredDevice[] devs = scan.scan();
            if(devs==null){
                throw new WebApplicationException(503);
            }else{
                String str = "[\n";
                for(int i=0; i<devs.length; i++){
                    DiscoveredDevice dev = devs[i];
                    str += "{ driver:\"" + dev.getProperty("driver") + "\", port:\"" + dev.getProperty("port") +"\" }";
                    if(i<devs.length-1)
                        str += ",\n";
                    else
                        str += "\n";
                }
                str += "]";
                return str;
            }
        } catch (IOException ex) {
            Logger.getLogger(ScannerProvider.class.getName()).log(Level.SEVERE, null, ex);
            throw new WebApplicationException();
        }
    }
}
