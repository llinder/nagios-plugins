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

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RequestContext {

	private URL url;
	private Map<String,String> headers=new HashMap<String,String>();
	private Map<String,String> queries=new HashMap<String,String>();
	String method;
	String body;
	File bodyFile;
	ClientContext cc;
	int rounds;
	
	public RequestContext(ClientContext cc){
		this.cc = cc;
		rounds=1;
	}
	
	void setUrl(URL url) {
		this.url = url;
	}
	
	URL getUrl() {
		return url;
	}
	
	
	void setHeader(String name,String value){
		headers.put(name, value);
	}
	
	void setHeaderIfNotPresent(String name,String value){
		if(headers.get(name) == null){
			headers.put(name, value);
		}
	}
	
	void setQueryParam(String name, String value){
		queries.put(name, value);
	}
	
	void setBodyFile(File file){
		this.bodyFile=file;
	}
	
	void setMethod(String method){
		this.method=method;
	}
	
	void setRounds(int rounds){
		this.rounds=rounds;
	}
	
	Map<String,String> getHeaders() {
		return headers;
	}
	
	ClientContext getClientContext(){
		return cc;
	}
	
	String getMethod(){
		return method;
	}
	
	int getRounds(){
		return rounds;
	}
	
	File getBodyFile(){
		return bodyFile;
	}
	
	public Map<String,String> getQueryParams(){
		return queries;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RequestContext other = (RequestContext) obj;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}
	
	
	
}
