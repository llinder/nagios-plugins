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
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ClientContext
{
	
	double timeout;
	int rounds;
	int processorCount;
	boolean verbose;
	PrintStream output;
	String bind_address;
	File requests_file;
	String user_agent;
	String[] urls;
	Map<String,String> headers;
	Map<String,String> defaultHeaders;
	Map<String,String> queries;
	Set<RequestContext> ctxs;
	String http_version;
	ArrayList<Statistics> stats;
	String method;
	File bodyFile;
	
	public ClientContext()
	{
		headers=new HashMap<String,String>();
		defaultHeaders=new HashMap<String,String>();
		queries=new HashMap<String,String>();
		ctxs=new HashSet<RequestContext>();
		stats=new ArrayList<Statistics>();
		urls=new String[0];
		rounds=1;
		output = System.out;
		populateDefaultHeaders();
	}
	
	public void init() throws Exception
	{
		
		// Set requests from configuration file.
		try{
			if(requests_file!= null){
		       parse();
			}
		}catch(Exception e){
			throw e;
		}
		
		// Set requests from command line.
		for(int i=0;i<urls.length;i++){
			RequestContext ctx=new RequestContext(this);
			ctx.setUrl(new URL(urls[i]));
			
			if(method != null && method.equals("POST")){
				ctx.setMethod(method);
				if(bodyFile != null){
					ctx.setBodyFile(bodyFile);
				}
			}else{
				ctx.setMethod("GET");
			}
			
			for(String name:headers.keySet()){
				ctx.setHeader(name, headers.get(name));
			}
			
			for(String key:queries.keySet()){
				ctx.setQueryParam(key, queries.get(key));
			}
			
			ctx.setRounds(rounds);
			
			if(ctxs.contains(ctx)){
				ctxs.remove(ctx);
			}
			
			ctxs.add(ctx);
		}
		
		// Add default headers for generic header types. Doesn't override already set headers. Useful for situations when no headers are explicitly set.
		for(RequestContext ctx:ctxs){
			for(String header:defaultHeaders.keySet()){
				ctx.setHeaderIfNotPresent(header, defaultHeaders.get(header));
			}
		}
		
		synchronized(this){
			processorCount=ctxs.size();
		}
	}
	
	public Set<RequestContext> getRequestContexts(){
		return ctxs;
	}
	
	public double getTimeout(){
		return timeout;
	}
	
	public void setTimeout(double value)
	{
		timeout = value;
	}
	
	public String getHttpVersion(){
		return http_version;
	}
	
	public boolean isVerbose(){
		return verbose;
	}
	
	public String[] getUrls()
	{
		return urls;
	}
	
	public void setUrls(String[] urls)
	{
		this.urls = urls;
	}
	
	public File getRequestsFile()
	{
		return requests_file;
	}
	
	public void setRequestsFile( File file )
	{
		requests_file = file;
	}
	
	public Map<String,String> getHeaders()
	{
		return headers;
	}
	
	public void setHeaders( Map<String, String> headers )
	{
		this.headers = headers;
	}
	
	public void setHttpVersion( String version )
	{
		http_version = version;
	}
	
	public void setBodyFile( File file )
	{
		bodyFile = file;
	}
	
	public void setQueries( Map<String,String> queries )
	{
		this.queries = queries;
	}
	
	public void setVerbose( boolean verbose )
	{
		this.verbose = verbose;
	}
	
	public void setMethod( String method )
	{
		this.method = method;
	}
	
	public void setStatistics(Statistics statistics){
		synchronized(stats){
			stats.add(statistics);
		}
	}
	
	public ArrayList<Statistics> getStatistics(){
		return stats;
	}
	
	public PrintStream getOutput(){
		return output;
	}
	
	private void populateDefaultHeaders(){
		defaultHeaders.put("From","ajp@test.org");
		defaultHeaders.put("User-Agent","AJPClient/1.0");
		defaultHeaders.put("Accept-Language","en");
	}
	
	public synchronized void decrementProcessorCount(){
		processorCount--;
		notifyAll();
	}
	
	public int getProcessorCount(){
		return processorCount;
	}
	
	private void parse(){
		try {
			  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			  DocumentBuilder db = dbf.newDocumentBuilder();
			  Document doc = db.parse(requests_file);
			  doc.getDocumentElement().normalize();
			  
			  NodeList reqLst = doc.getElementsByTagName("request");	
			  NodeList commonLst=doc.getElementsByTagName("common");
			  NodeList commonHeaders=null;
			  
			  if(commonLst.getLength() > 0){
				  Node common=commonLst.item(0);
				  if(common.getNodeType() == Node.ELEMENT_NODE){
					  Element commonElmnt=(Element)common;
					  commonHeaders=commonElmnt.getElementsByTagName("header");
				  }
			  }
			  
			  for(int i=0;i< reqLst.getLength();i++){
				  Node request=reqLst.item(i);
				  if (request.getNodeType() == Node.ELEMENT_NODE) {
					  
					  RequestContext ctx=new RequestContext(this);
					  
					  //Process request URL
			          Element reqElmnt = (Element) request;
			          Node url = reqElmnt.getElementsByTagName("url").item(0);
			          ctx.setUrl(new URL(url.getFirstChild().getTextContent()));
			          
			          //Process common headers. May be overridden later.
			          if(commonHeaders != null){
				          for(int j=0;j<commonHeaders.getLength();j++){
				        	  Node header=commonHeaders.item(j);
				        	  if(header.getNodeType() == Node.ELEMENT_NODE){
				        		  Element headerElmnt=(Element)header;
				        		  String headerName=headerElmnt.getElementsByTagName("name").item(0).getFirstChild().getTextContent();
				        		  String value=headerElmnt.getElementsByTagName("value").item(0).getFirstChild().getTextContent();
				        		  ctx.setHeader(headerName, value);
				        	  }
				          }
			          }
			          
			          //Process headers
			          NodeList headersLst=reqElmnt.getElementsByTagName("headers");
			          if(headersLst.getLength() > 0){
			        	  Node headers=headersLst.item(0);
			        	  if(headers.getNodeType() == Node.ELEMENT_NODE){
			        		  Element headersElmnt=(Element)headers;
			        		  NodeList headerLst=headersElmnt.getElementsByTagName("header");
			        		   
			        		  for(int j=0;j< headerLst.getLength();j++){
			     				 Node header=reqLst.item(i);
			    				  if (header.getNodeType() == Node.ELEMENT_NODE) {
			    					 Element headerElmnt=(Element)header;
			    					 String headerName=headerElmnt.getElementsByTagName("name").item(0).getFirstChild().getTextContent();
			    					 String value=headerElmnt.getElementsByTagName("value").item(0).getFirstChild().getTextContent();
			    					  
			    					 ctx.setHeader(headerName, value);
			    				 }
			        		  }
			        	  }
			          }
			          
			          //Process post if present
			          boolean postPresent=false;
			          NodeList postLst=reqElmnt.getElementsByTagName("post");
			          if(postLst.getLength() > 0){
			        	  Node post=postLst.item(0);
			        	  postPresent=true;
			        	  ctx.setMethod("POST");
			        	  
			        	  if(post.getNodeType() == Node.ELEMENT_NODE){
			        		  Element postElmnt=(Element)post;
			        		  
			        		  //Process bodyfile
			        		  boolean filePresent=false;
			        		  NodeList fileLst=postElmnt.getElementsByTagName("bodyfile");
			        		  if(fileLst.getLength() > 0){
			        			  String bodyFile=fileLst.item(0).getFirstChild().getTextContent();
			        			  ctx.setBodyFile(new File(bodyFile));
			        			  filePresent=true;
			        		  }
			        		  
			        		  //Process query
			        		  if(!filePresent){	// Gives the precedence to the file if both <bodyfile> and <query> tags are present.
			        			  NodeList queryLst=postElmnt.getElementsByTagName("query");
			        			  if(queryLst.getLength() > 0){
			        				  Node query=queryLst.item(0);
			        				  if(query.getNodeType() == Node.ELEMENT_NODE){
			        					  Element queryElmnt=(Element)query;
			        					  NodeList paramLst=queryElmnt.getElementsByTagName("param");
			        					  
			        					  for(int k=0;k<paramLst.getLength();k++){
			        						  Node param=paramLst.item(k);
			        						  if(param.getNodeType() == Node.ELEMENT_NODE){
			        							  Element paramElmnt=(Element)param;
			        							  String paramName=paramElmnt.getElementsByTagName("name").item(0).getFirstChild().getTextContent();
			        							  String value=paramElmnt.getElementsByTagName("value").item(0).getFirstChild().getTextContent();
			        							  ctx.setQueryParam(paramName, value);
			        						  }
			        					  }
			        				  }
			        			  }
			        		  }
			        	  }
			          }
			          
			          //Process get if present. Precedence is given to post if both are present.
			          if(!postPresent){
				          NodeList getLst=reqElmnt.getElementsByTagName("get");
				          if(getLst.getLength() > 0){
				        	  Node get=getLst.item(0);
				        	  ctx.setMethod("GET");
				        	  
				        	  if(get.getNodeType() == Node.ELEMENT_NODE){
				        		  Element getElmnt=(Element)get;
				        		  NodeList queryLst=getElmnt.getElementsByTagName("query");
				        		  
			        			  if(queryLst.getLength() > 0){
			        				  Node query=queryLst.item(0);
			        				  if(query.getNodeType() == Node.ELEMENT_NODE){
			        					  Element queryElmnt=(Element)query;
			        					  NodeList paramLst=queryElmnt.getElementsByTagName("param");
			        					  
			        					  for(int k=0;k<paramLst.getLength();k++){
			        						  Node param=paramLst.item(k);
			        						  
			        						  if(param.getNodeType() == Node.ELEMENT_NODE){
			        							  Element paramElmnt=(Element)param;
			        							  String paramName=paramElmnt.getElementsByTagName("name").item(0).getFirstChild().getTextContent();
			        							  String value=paramElmnt.getElementsByTagName("value").item(0).getFirstChild().getTextContent();
			        							  ctx.setQueryParam(paramName, value);
			        						  }
			        					  }
			        				  }
			        			  }
				        	  }
				          }
			          }
			          
			          //Process rounds
			          NodeList roundsLst=reqElmnt.getElementsByTagName("rounds");
			          if(roundsLst.getLength() > 0 ){
			        	  Node rounds=roundsLst.item(0);
			        	  String roundsStr=rounds.getFirstChild().getTextContent();
			        	  ctx.setRounds(Integer.parseInt(roundsStr));
			          }
			          
			          ctxs.add(ctx);
			          
			          //Default method is GET if no specific method is set
			          if(ctx.getMethod() == null){
			        	  ctx.setMethod(Constants.GET);
			          }
				  }
			  }
			  
		} catch (Exception e) {
		    e.printStackTrace();
		}
	}
}
