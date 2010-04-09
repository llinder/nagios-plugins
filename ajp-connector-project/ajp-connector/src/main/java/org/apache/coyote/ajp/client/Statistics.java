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

import java.net.URL;


public class Statistics
{
	
	private URL url;
	private boolean timedout;
	private String dateTime;
	private long time_elapsed;
	private int reply_code;
	
	void setUrl(URL url) {
		this.url = url;
	}
	
	public URL getUrl() {
		return url;
	}
	
	void setTimeout(boolean timedout) {
		this.timedout = timedout;
	}
	
	public boolean isTimeout() {
		return timedout;
	}

	void setTimeElapsed(long time_elapsed) {
		this.time_elapsed = time_elapsed;
	}

	public long getTimeElapsed() {
		return time_elapsed;
	}

	void setReplyCode(int reply_code) {
		this.reply_code = reply_code;
	}

	public int getReplyCode() {
		return reply_code;
	}

	void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}

	public String getDateTime() {
		return dateTime;
	}

}
