/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.coyote.ajp.client;

public class Constants {
	
    public static final char NEWLINE = '\n';
    public static final String COLON_SPACE = ": ";//$NON-NLS-1$
    public static final String POST="POST";
    public static final String GET="GET";
    public static final String PROTOCOL_HTTPS="https";
    public static final String HTTP_1_1="HTTP/1.1";
    
    static final int AJP_HEADER_BASE = 0xA000;

    static final int MAX_SEND_SIZE = 8*1024 - 4 - 4;
    
    static final String APPLICATION_X_WWW_FORM_URLENCODED="application/x-www-form-urlencoded ";

}
