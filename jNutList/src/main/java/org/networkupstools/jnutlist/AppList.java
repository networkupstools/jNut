/* AppList.java

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
package org.networkupstools.jnutlist;

import java.io.IOException;
import java.net.UnknownHostException;
import org.networkupstools.jnut.*;


public class AppList
{

    public static void main( String[] args )
    {
        int    count = 0;
        String host  = "localhost";
        int    port  = 3493;
        String login = "";
        String pass  = "";

        String jks_path   = ""; //args.length>=5?args[4]:"";
        String jks_pass   = ""; //args.length>=6?args[5]:"";
        int    forceSSL   = 0;
        int    certVerify = 0;

        Boolean tracking   = false;

        String setName  = "";
        String setValue = "";
        Boolean setApplied = null;

        String cmdName  = "";
        String cmdValue = "";
        Boolean cmdApplied = null;

        String optName  = "";
        String optValue = "";

        try {
            try {
                for (count = 0; count < args.length; count++) {
                    optName = args[count];

                    if (optName.equals("-w")) {
                        tracking = true;
                        continue;
                    }

                    if (optName.equals("--setvar")) {
                        if (count+2 < args.length) {
                            setName  = args[++count];
                            setValue = args[++count];
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--instcmd2")) {
                        if (count+2 < args.length) {
                            cmdName  = args[++count];
                            cmdValue = args[++count];
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--instcmd")) {
                        if (count+1 < args.length) {
                            cmdName  = args[++count];
                            cmdValue = "";
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--ssl-jks-path")) {
                        if (count+1 < args.length) {
                            jks_path = args[++count];
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--ssl-jks-pass")) {
                        if (count+1 < args.length) {
                            jks_pass = args[++count];
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--ssl-forceSSL")) {
                        if (count+1 < args.length) {
                            optValue = args[++count];
                            forceSSL = Integer.valueOf(optValue);
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    if (optName.equals("--ssl-certVerify")) {
                        if (count+1 < args.length) {
                            optValue = args[++count];
                            certVerify = Integer.valueOf(optValue);
                            continue;
                        } else {
                            throw new IllegalArgumentException("Missing parameter for " + optName);
                        }
                    }

                    // Unsupported argument - go to basic four
                    break;
                }

                // Default zero to four toggles:
                //System.err.println("[DEBUG] Got to std params; count=" + count + " len=" + args.length);
                optName = "host";
                if (args.length > count) {
                    host = args[count++];
                }

                optName = "port";
                if (args.length > count) {
                    optValue = args[count++];
                    port = Integer.valueOf(optValue).intValue();
                }

                optName = "login";
                if (args.length > count) {
                    login = args[count++];
                }

                optName = "pass";
                if (args.length > count) {
                    pass = args[count++];
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric argument '" + optValue + "' for " + optName);
            }
        } catch (IllegalArgumentException iae) {
            System.err.println(iae.toString() + ".\nUsage: AppList [--ssl-jks-path PATH] [--ssl-jks-pass PWD] [--ssl-forceSSL NUT] [--ssl-certVerify NUM] [host] [port] [login] [password]");
            System.exit(1);
        }

        SSLConfig sslConfig = null;

        if (!jks_path.isEmpty() && !jks_pass.isEmpty()) {
            sslConfig = new SSLConfig_JKS(
                forceSSL > 0,
                certVerify > 0,
                jks_path, jks_pass,
                jks_path, jks_pass
                );
        }

        System.out.println( "jNutList connecting to " + login+":"+pass+"@"+host+":"+port
            + (sslConfig == null ? "" : ", with STARTTLS mode")
            + ", with" + (tracking ? "" : "out" ) + " TRACKING for SET VAR/INSTCMD"
            );

        Client client = new Client();
        try {
            client.setSslConfig(sslConfig);
            if (tracking)
                client.setTracking(true);
            client.connect(host, port, login, pass);

            Device[] devs = client.getDeviceList();
            if(devs!=null)
            {
                for(int d=0; d<devs.length; d++)
                {
                    Device dev = devs[d];
                    String desc = "";
                    try {
                        desc = " : " + dev.getDescription();
                    } catch(NutException e) {
                        e.printStackTrace();
                    }
                    System.out.println("DEV " + dev.getName() + desc);

                    try {
                        Variable[] vars = dev.getVariableList();
                        if(vars!=null)
                        {
                            if(vars.length==0)
                                System.out.println("  NO VAR");
                            for(int v=0; v<vars.length; v++)
                            {
                                Variable var = vars[v];
                                String res = "";
                                try {
                                    res = " = " + var.getValue() + " (" + var.getDescription() + ")";
                                } catch(NutException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("  VAR " + var.getName() + res );

                                try {
                                    if (!setName.isEmpty() && setName.equals(var.getValue())) {
                                        if (tracking)
                                            var.setValue(setValue, 1, 10);
                                        else
                                            var.setValue(setValue);
                                        setApplied = true;
                                    }
                                } catch(NutException e) {
                                    e.printStackTrace();
                                    if (setApplied == null)
                                        setApplied = false;
                                }

                                try {
                                    if (!setName.isEmpty() && setName.equals(var.getValue())) {
                                        var = dev.getVariable(setName);
                                        res = " = " + var.getValue() + " (" + var.getDescription() + ")";
                                        System.out.println("  UPDATED: VAR " + var.getName() + res );
                                    }
                                } catch(NutException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        else
                            System.out.println("  NULL VAR");
                    } catch(NutException e) {
                        e.printStackTrace();
                    }

                    try {
                        Command[] cmds = dev.getCommandList();
                        if(cmds!=null)
                        {
                            if(cmds.length==0)
                                System.out.println("  NO CMD");
                            for(int c=0; c<cmds.length; c++)
                            {
                                Command cmd = cmds[c];
                                String res = "";
                                try {
                                    res = " : " + cmd.getDescription();
                                } catch(NutException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("  CMD " + cmd.getName() + res);

                                try {
                                    if (!cmdName.isEmpty() && cmdName.equals(cmd.getName())) {
                                        if (tracking)
                                            cmd.execute(cmdValue, 1, 10);
                                        else
                                            cmd.execute(cmdValue);
                                        cmdApplied = true;
                                    }
                                } catch(NutException e) {
                                    if (cmdApplied == null)
                                        cmdApplied = false;
                                    e.printStackTrace();
                                }
                            }
                        }
                        else
                            System.out.println("  NULL CMD");
                    } catch(NutException e) {
                        e.printStackTrace();
                    }
                }
            }

            client.disconnect();

        }catch(Exception e){
            e.printStackTrace();
        }

        if (cmdApplied != null)
            System.out.println("  Called CMD '" + cmdName + "', succeeded at least once: " + cmdApplied.toString());

        if (setApplied != null)
            System.out.println("  Assigned VAR '" + setName + "', succeeded at least once: " + setApplied.toString());
    }
}
