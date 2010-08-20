/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly.cometd.bayeux;

/**
 * Bayeux Reconnect implementation. 
 * See http://svn.xantus.org/shortbus/trunk/bayeux/protocol.txt for the technical
 * details.
 *
 * Upon connection, clients are implicitly subscribed to a connection-specific
 * channel located at:
 * 
 * 	/meta/clients/[SOME_UNIQUE_CLIENT_ID]
 * 
 * The client ID is used in reconnection. The reconnect verb allows occasionally
 * connected clients and the posting of messages for pre-authenticated systems.
 * 
 * 	// reconnection is very similar to initial connection:
 * 
 * 	//-----------------
 * 	// CLIENT -> SERVER
 * 	//-----------------
 * 
 * 	[
 * 		{
 * 			"channel":		"/meta/reconnect",
 * 			"clientId":		"SOME_UNIQUE_CLIENT_ID",
 * 			"timestamp":	"LastReceivedTimeAtServer",
 * 			"id":			"LastReceivedMessageId"
 * 			"connectionId":	"/meta/connections/26",
 * 			"connectionType": "iframe", // FIXME: is this necessaray?
 * 			// optional
 * 			"authToken":	"SOME_NONCE_PREVIOUSLY_PROVIDED_BY_SERVER"
 * 		}
 * 		// , ...
 * 	]
 * 
 * 
 * 	// generally, the first message in the array of responded messages will
 * 	// begin with:
 * 
 * 	//-----------------
 * 	// SERVER -> CLIENT
 * 	//-----------------
 * 
 * 	[
 * 		{
 * 			"channel":		"/meta/reconnect",
 * 			"connectionId":	"/meta/connections/26",
 * 			"successful":	true,
 * 			// optional
 * 			"authToken":	"SOME_NONCE_THAT_NEEDS_TO_BE_PROVIDED_SUBSEQUENTLY"
 * 		}
 * 		// , ...
 * 	]
 * @author Jeanfrancois Arcand
 */
abstract class Reconnect extends Connect{

    public final static String META_RECONNECT ="/meta/reconnect";

    public Reconnect() {
        type = Verb.Type.RECONNECT;
        metaChannel = META_RECONNECT;
    }

    public String toJSON() {
        return toJSON(null);
    }

    public String toJSON(String timestamp) {            
        StringBuilder sb = new StringBuilder(
                getJSONPrefix() + "{" 
                + "\"successful\":" + successful + ","                
                + "\"channel\":\"" + channel + "\""
                );
        if (error != null) {
            sb.append(",\"error\":\"").append(error).append("\"");
        }
        if (timestamp != null) {
            sb.append(",\"timestamp\":\"").append(timestamp).append("\"");
        }
        sb.append("}").append(getJSONPostfix());   
        return sb.toString();
    }
}
