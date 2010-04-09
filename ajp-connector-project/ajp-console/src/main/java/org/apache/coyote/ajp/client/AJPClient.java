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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

public class AJPClient {
	
	int timeout;
	
	File statistics;
	
	String bind_address;
	
	File header_file;
	
	String headers[];
	
	String user_agent;
	
	String urls[];
	
	ClientContext cc=new ClientContext();
	
	AjpProcessor[] processors;
	
	public static void main(String args[]) throws Exception{
		AJPClient client=new AJPClient();
		client.parseArguments(args);
		client.init();
		client.run();
		client.output();
	}
	
	private void parseArguments(String args[]){
		int index = 0;
        String arg;
        ArrayList<String> urlArray=new ArrayList<String>();
        
        while (index < args.length) {
            arg = args[index++];
            String[] tokens=arg.split("=");
            
            String name=null;
            String value=null;
            boolean isSwitch=false;
            if(tokens.length == 2){
            	name=tokens[0];
            	value=tokens[1];
            	
            	if (name.equals("-T") || name.equals("--timeout")) {
                    cc.timeout = Double.parseDouble(value); 
                    isSwitch=true;
                } else if (name.equals("-o") || name.equals("--output")) {
                    if(value.equals("-")){
                    	cc.output=System.out;
                    }else{
                    	try{
                    	FileOutputStream fs=new FileOutputStream(value);
                    	cc.output= new PrintStream(fs);
                    	}catch(FileNotFoundException e){
                    		cc.output=System.out;
                    		System.out.println("Failed loading out put file. Output redirected to console...");
                    	}
                    }
                    isSwitch=true;
                } else if (name.equals("-r") || name.equals("--requests-file")) {
                    cc.requests_file=new File(value);
                    isSwitch=true;
                } else if (name.equals("-H") || name.equals("--headers")) {
                	String[] tmp=null;
                	if(value.contains("\\")){
                		tmp=value.split("\\\\");
                	}else{
                		tmp=new String[1];
                		tmp[0]=value;
                	}
                	
                    for(String header:tmp){
                    	String[] tuple=header.split(":");
                    	cc.headers.put(tuple[0], tuple[1]); // Header name and header value
                    }
                    isSwitch=true;
                } else if (name.equals("--rounds")) {
                    cc.rounds=Integer.parseInt(value);
                    isSwitch=true;
                } else if (name.equals("--http-version")) {
                    cc.http_version=value;
                    isSwitch=true;
                } else if (name.equals("--body-file")) { // This switch is not used currently.
                    cc.bodyFile=new File(value);
                    isSwitch=true;
                } else if (name.equals("--query")) {
                	String[] tmp=null;
                	if(value.contains("\\")){
                		tmp=value.split("\\\\");
                	}else{
                		tmp=new String[1];
                		tmp[0]=value;
                	}
                	
                    for(String param:tmp){
                    	String[] tuple=param.split(":");
                    	cc.queries.put(tuple[0], tuple[1]); // Parameter name and parameter value
                    }
                    isSwitch=true;
                } else if (name.equals("-v") || name.equals("--verbose")) {
                    cc.verbose=true;
                } else if (name.equals("-u") || name.equals("--user-agent")) {
                    cc.headers.put("User-Agent", arg);
                    isSwitch=true;
                } else if (name.equals("-m") || name.equals("--method")) {
                    String method=value;
                    if(method.trim().equalsIgnoreCase(Constants.POST)){
                    	cc.method=Constants.POST;
                    }else{
                    	cc.method=Constants.GET;
                    }
                    isSwitch=true;
                } else if (name.equals("-h") || name.equals("--help")) {
                    printUsage();
                    System.exit(0);
                }
            }

            if(!(arg.startsWith("-")|| arg.startsWith("--"))){	// We have come to the URL section.
            	urlArray.add(arg);
            } else if(!isSwitch) {
                printUsage();
                System.exit(-1);
            }
        }
        
        if(cc.method == null){
        	cc.method=Constants.GET;
        }
        
        if(cc.method.equals(Constants.GET) && cc.bodyFile != null){	// GET cannot have a body
        	error("-m","--bodyFile");
        	System.exit(-1);
        }
        
        cc.urls=urlArray.toArray(cc.urls);
        urlArray=null;
	}
	
	private void printUsage(){
		System.out.println("java -jar AJPClient.jar [Options] url[1-n]\n");
		System.out.println("Options\n");
		System.out.println("\t-t\n\t--timeout=seconds\n\t\tSet the read timeout to seconds seconds.\n");
		System.out.println("\t-m\n\t--method=method\n\t\tSets the HTTP method." +
				" Can be either GET or POST. No other methods are supported at present.\n");
		System.out.println("\t--rounds=number\n\t\tSets the number of times url[s] given in the command are repeatedly fetched.\n");
		System.out.println("\t--http-version=version\n\t\tSets the HTTP version. Can be either 1.0 or 1.1.\n");
		System.out.println("\t--query=param_1:paramValue|...|param_n:paramValue\n\t\t" +
				"Sets the parameters in the query string for the url[s] specified in the command line.\n");
		System.out.println("\t-r\n\t--requests-file=file\n\t\tSets the requests.xml configuration file.\n");
		System.out.println("\t-o\n\t--output=file\n\t\tSets the output file location. If - is specified then outputs to the console.\n");
		System.out.println("\t-H\n\t--headers=header_1:value|....|header_2:value\n\t\t" +
				"Sets headers to be included in HTTP requests for url[s] specified in the command line.\n");
	}
	
	private void error(String... switches){
		System.out.println("ERROR: Invalid combination of switches. ");
		for(String ss:switches){
			System.out.print(ss+" ");
		}
		System.out.println("cannot be used together.");
		System.out.println();
		printUsage();
	}
	
	private void init() throws Exception{
		cc.init();
		Set<RequestContext> ctxs=cc.getRequestContexts();
		processors=new AjpProcessor[ctxs.size()];
		
		int index=0;
		for(Iterator<RequestContext> it=ctxs.iterator();it.hasNext();){
			AjpProcessor processor=new AjpProcessor(it.next());
			processors[index++]=processor;
		}
	}
	
	private void run(){
		for(AjpProcessor processor:processors){
			new Thread(processor).start();
		}
		
		synchronized(cc){
			while(cc.getProcessorCount() > 0){
				try {
					cc.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void output() throws IOException{
		ArrayList<Statistics> stats=cc.getStatistics();
		PrintStream ps=cc.getOutput();
        
        String format = "|%1$-15s|%2$-20s|%3$-10s|%4$-15s|%5$-10s|%6$-75s\n";
        ps.println("\nRun: "+new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date()));
        ps.println();
        ps.format(format, "Connection","StartTime","TimedOut","TimeElapsed(ms)","ReplyCode","URL");
        ps.println();
        
        int counter=0;
        for(Iterator<Statistics> it=stats.iterator();it.hasNext();){
        	Statistics statistics=it.next();
        	ps.format(format, counter++,statistics.getDateTime(),statistics.isTimeout(),statistics.getTimeElapsed(),
        			statistics.getReplyCode(),statistics.getUrl());
        }
        
        ps.flush();
        ps.close();
	}

}
