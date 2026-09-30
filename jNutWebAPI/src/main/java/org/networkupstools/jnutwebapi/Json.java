/* Json.java

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.
*/
package org.networkupstools.jnutwebapi;

import org.apache.commons.lang.StringEscapeUtils;

final class Json {
    private Json() {
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        // Java string escapes are valid JSON; JavaScript's \' is not.
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }
}
