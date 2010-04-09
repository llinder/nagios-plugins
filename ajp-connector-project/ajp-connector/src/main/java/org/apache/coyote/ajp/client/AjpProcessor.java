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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * AJP protocol handler class.
 * For the initial implementation see 
 * http://svn.apache.org/repos/asf/jakarta/jmeter/trunk/src/protocol/http/org/apache/jmeter/protocol/http/sampler/AjpSampler.java 
 * Currently does support GET and POST requests with form data content type application/x-www-form-urlencoded. multipart/form-data  
 * is not currently supported.   
 */
public class AjpProcessor implements Runnable
{
	private static final int AJP13_SEND_BODY_CHUNK = 3;
	private static final int AJP13_SEND_HEADERS = 4;
	private static final int AJP13_END_RESPONSE = 5;
	private static final int AJP13_GET_BODY_CHUNK = 6;
	
	
	RequestContext ctx;
	
	ClientContext cc;
	
	private String responseHeader;
	
	private Statistics statistics;
 
    /**
     *  Translates integer codes to request header names    
     */
    private static final String []headerTransArray = {
        "accept",               //$NON-NLS-1$
        "accept-charset",       //$NON-NLS-1$
        "accept-encoding",      //$NON-NLS-1$
        "accept-language",      //$NON-NLS-1$
        "authorization",        //$NON-NLS-1$
        "connection",           //$NON-NLS-1$
        "content-type",         //$NON-NLS-1$
        "content-length",       //$NON-NLS-1$
        "cookie",               //$NON-NLS-1$
        "cookie2",              //$NON-NLS-1$
        "host",                 //$NON-NLS-1$
        "pragma",               //$NON-NLS-1$
        "referer",              //$NON-NLS-1$
        "user-agent"            //$NON-NLS-1$
    };
    
    // Translates integer codes to response header names
    public static final String []responseTransArray = {
            "Content-Type",
            "Content-Language",
            "Content-Length",
            "Date",
            "Last-Modified",
            "Location",
            "Set-Cookie",
            "Set-Cookie2",
            "Servlet-Engine",
            "Status",
            "WWW-Authenticate"
    };

    /**
     * Base value for translated headers
     */

    private transient Socket channel = null;
    private int lastPort = -1;
    private String lastHost = null;
    private String localName = null;
    private String localAddress = null;
    private byte [] inbuf = new byte[8*1024];
    private byte [] outbuf = new byte[8*1024];
    private transient ByteArrayOutputStream responseData = new ByteArrayOutputStream();
    private int inpos = 0;
    private int outpos = 0;
    private transient String stringBody = null;
    private transient InputStream body = null;

	
	public AjpProcessor(RequestContext ctx){
		this.ctx=ctx;
		this.cc=ctx.getClientContext();
	}
	
	public void run(){
		process();
	}
	
	public void process(){
        try {
        	int rounds=ctx.getRounds();
        	
        	while(rounds-- > 0){
	            setupConnection();
	            execute();
	            cleanup();
	            cc.setStatistics(statistics);
        	}
        } catch(IOException iex) {
        	if(iex instanceof SocketTimeoutException){
        		statistics.setTimeout(true);
        	}
            lastPort = -1; // force reopen on next sample
            channel = null;
            //return err;
        } catch(ProtocolException e){
            lastPort = -1; // force reopen on next sample
            channel = null;
        }
        
        cc.decrementProcessorCount();
	}
	
	public String getResponseHeader(){
		return responseHeader;
	}
	
	public String getResponseData(){
		return responseData.toString();
	}
	
	private void setupConnection() throws IOException {
		URL url=ctx.getUrl();
		statistics=new Statistics();
		statistics.setUrl(url);
		
		if(ctx.getQueryParams().size() > 0 && ctx.getMethod().equals(Constants.GET) && url.getQuery() != null){
			StringBuffer sb=new StringBuffer(url.toString());
			sb.append("?");
			
			Map<String,String> params=ctx.getQueryParams();
			boolean first=true;
			
			for(String param:params.keySet()){	
				if(first){
					first=false;
				}else{
					sb.append("&");
				}
				sb.append(param);
				sb.append("=");
				sb.append(params.get(param));
			}
			
			url=new URL(sb.toString());
			ctx.setUrl(url);
		}
		
		String host = url.getHost();
		int port = url.getPort();
		if(port <= 0 || port == url.getDefaultPort()) {
			port = 8009;
		}
		String scheme = url.getProtocol();
		if(channel == null || !host.equals(lastHost) || port != lastPort) {
			if(channel != null) {
				channel.close();
			}
			channel = new Socket(host, port);
			double timeout = cc.getTimeout();
			if(timeout > 0) {
				channel.setSoTimeout((int)timeout*1000);
			}
			localAddress = channel.getLocalAddress().getHostAddress();
			localName = channel.getLocalAddress().getHostName();
			lastHost = host;
			lastPort = port;
		}
		log("Connected to "+host+" at port "+port);
		
		outpos = 4;
		setByte((byte)2);
		if(ctx.getMethod() != null && ctx.getMethod().equals(Constants.POST)) {
			setByte((byte)4);
		} else {
			setByte((byte)2);
		}
		if(cc.getHttpVersion() != null && (cc.getHttpVersion().equals("1.0") || cc.getHttpVersion().equals("1"))) {//$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
			setString("HTTP/1.0");//$NON-NLS-1$
		} else {
			setString("HTTP/1.1");
		}
	   setString(url.getPath());
	   setString(localAddress);
	   setString(localName);
	   setString(host);
	   setInt(url.getDefaultPort());
	   setByte(Constants.PROTOCOL_HTTPS.equalsIgnoreCase(scheme) ? (byte)1 : (byte)0);
	   setInt(getHeaderSize());
	   setConnectionHeaders(host);
	   String query = url.getQuery();
	   if (query != null) {
	       setByte((byte)0x05); // Marker for query string attribute
	       setString(query);
	   }
	   setByte((byte)0xff); // More general attributes not supported
	}

	private int getHeaderSize() {
	/*   HeaderManager headers = getHeaderManager();
	   CookieManager cookies = getCookieManager();
	   AuthManager auth = getAuthManager();
	   int hsz = 1; // Host always
	   if(method.equals(POST)) {
	       HTTPFileArg[] hfa = getHTTPFiles();
	       if(hfa.length > 0) {
	           hsz += 3;
	       } else {
	           hsz += 2;
	       }
	   }
	   if(headers != null) {
	       hsz += headers.size();
	   }
	   if(cookies != null) {
	       hsz += cookies.getCookieCount();
	   }
	   if(auth != null) {
	           String authHeader = auth.getAuthHeaderForURL(url);
	       if(authHeader != null) {
	       ++hsz;
	       }
	   }*/
	   //return hsz;
		int size=1;			// For host header which is compulsory.
		if(ctx.getMethod().endsWith(Constants.POST)){
			size+=2;		// For content-type and content-length headers.
		}
		
		if(ctx.getHeaders().get("host") != null){
			size=size+ctx.getHeaders().size() - 1; // Prevent host header being counted twice
		}else{
			size += ctx.getHeaders().size();
		}
	   return size; 	// For now return size of the header map. Additional header for host header.
	}


	private void setConnectionHeaders(String host) throws IOException {
	/*   HeaderManager headers = getHeaderManager();
	   AuthManager auth = getAuthManager();*/
	   //StringBuilder hbuf = new StringBuilder();
	   // Allow Headers to override Host setting
	   //hbuf.append("Host").append(COLON_SPACE).append(host).append(NEWLINE);//$NON-NLS-1$
	   setInt(0xA00b); //Host 
	   setString(host);
	   /*if(headers != null) {
	       CollectionProperty coll = headers.getHeaders();
	       PropertyIterator i = coll.iterator();
	       while(i.hasNext()) {
	           Header header = (Header)i.next().getObjectValue();
	           String n = header.getName();
	           String v = header.getValue();
	           //hbuf.append(n).append(COLON_SPACE).append(v).append(NEWLINE);
	           int hc = translateHeader(n);
	           if(hc > 0) {
	               setInt(hc+AJP_HEADER_BASE);
	           } else {
	               setString(n);
	           }
	           setString(v);
	       }
	   }*/
	   
	   Map<String,String> headers=ctx.getHeaders();
	   headers.remove("host"); // Remove any repeating header for host. We already set host header.
	   for(String name:headers.keySet()){
		   String value=headers.get(name);
		   int code=translateHeader(name);
		   if(code > 0){
			   setInt(code+Constants.AJP_HEADER_BASE);
		   }else{
			   setString(name);
		   }
		   
		   setString(value);
	   }
	   
	   if(ctx.getMethod().equals(Constants.POST)) {
	       int cl = -1;
	       //HTTPFileArg[] hfa = getHTTPFiles();
	       if(ctx.getBodyFile() != null) {	
/*	    	   File input=ctx.getBodyFile();
	           cl = (int)input.length();
	           body = new FileInputStream(input);
	           setString(HEADER_CONTENT_DISPOSITION);
	           setString("form-data; name=\""+encode(fa.getParamName())+
	                 "\"; filename=\"" + encode(fn) +"\""); //$NON-NLS-1$ //$NON-NLS-2$
	           String mt = fa.getMimeType();
	           hbuf.append(HEADER_CONTENT_TYPE).append(COLON_SPACE).append(mt).append(NEWLINE);
	           setInt(0xA007); // content-type
	           setString(mt);*/
	       } else {
	           //hbuf.append(HEADER_CONTENT_TYPE).append(COLON_SPACE).append(APPLICATION_X_WWW_FORM_URLENCODED).append(NEWLINE);
	           setInt(0xA007); // content-type
	           setString(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
	           StringBuilder sb = new StringBuilder();
	           
	           boolean first = true;
	           Map<String,String> params=ctx.getQueryParams();
	           
	           for(String param:params.keySet()){
	               if(first) {
	                   first = false;
	                   sb.append(param);
	                   sb.append("=");
	               } else {
	                   sb.append('&');
	               }
	               sb.append(params.get(param));
	           }
	           stringBody = sb.toString();
	           byte [] sbody = stringBody.getBytes(); //FIXME - encoding
	           cl = sbody.length;
	           body = new ByteArrayInputStream(sbody);
	       }
	       //hbuf.append(HEADER_CONTENT_LENGTH).append(COLON_SPACE).append(String.valueOf(cl)).append(NEWLINE);
	       setInt(0xA008); // Content-length
	       setString(String.valueOf(cl));
	   }
/*	   if(auth != null) {
	       String authHeader = auth.getAuthHeaderForURL(url);
	       if(authHeader != null) {
	           setInt(0xA005); // Authorization
	           setString(authHeader);
	           hbuf.append(HEADER_AUTHORIZATION).append(COLON_SPACE).append(authHeader).append(NEWLINE);
	       }
	   }
	   return hbuf.toString();*/
	}
	
	private String encode(String value)  {
	   StringBuilder newValue = new StringBuilder();
	   char[] chars = value.toCharArray();
	   for (int i = 0; i < chars.length; i++)
	   {
	       if (chars[i] == '\\')//$NON-NLS-1$
	       {
	           newValue.append("\\\\");//$NON-NLS-1$
	       }
	       else
	       {
	           newValue.append(chars[i]);
	       }
	   }
	   return newValue.toString();
	}
	
	/*private String setConnectionCookies(URL url, CookieManager cookies) {
	   String cookieHeader = null;
	   if(cookies != null) {
	       cookieHeader = cookies.getCookieHeaderForURL(url);
	       CollectionProperty coll = cookies.getCookies();
	       PropertyIterator i = coll.iterator();
	       while(i.hasNext()) {
	           Cookie cookie = (Cookie)(i.next().getObjectValue());
	           setInt(0xA009); // Cookie
	           setString(cookie.getName()+"="+cookie.getValue());//$NON-NLS-1$
	       }
	   }
	   return cookieHeader;
	}*/
	
	private int translateHeader(String n) {
	   for(int i=0; i < headerTransArray.length; i++) {
	       if(headerTransArray[i].equalsIgnoreCase(n)) {
	           return i+1;
	       }
	   }
	   return -1;
	}
	
	private void setByte(byte b) {
	   outbuf[outpos++] = b;
	}
	
	private void setInt(int n) {
	   outbuf[outpos++] = (byte)((n >> 8)&0xff);
	   outbuf[outpos++] = (byte) (n&0xff);
	}
	
	private void setString(String s) {
	   if( s == null ) {
	       setInt(0xFFFF);
	   } else {
	       int len = s.length();
	       setInt(len);
	       for(int i=0; i < len; i++) {
	           setByte((byte)s.charAt(i));
	       }
	       setByte((byte)0);
	   }
	}
	
	private void send() throws IOException {
	   OutputStream os = channel.getOutputStream();
	   int len = outpos;
	   outpos = 0;
	   setInt(0x1234);
	   setInt(len-4);
	   os.write(outbuf, 0, len);
	}
	
	private void execute() throws IOException, ProtocolException {
	   String dateTime=new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date());
	   statistics.setDateTime(dateTime);
	   long start=System.currentTimeMillis();
	   send();
	   if(ctx.getMethod() != null && ctx.getMethod().equals(Constants.POST)) {
	       sendPostBody();
	   }
	   handshake();
	   long end=System.currentTimeMillis();
	   statistics.setTimeElapsed(end-start);
	}
	
	private void handshake() throws IOException, ProtocolException {
	   responseData.reset();
	   int msg = getMessage();
	   while(msg != AJP13_END_RESPONSE) {
	       if(msg == AJP13_SEND_BODY_CHUNK) {
	    	   int len = getInt();
	           responseData.write(inbuf, inpos, len); 
	       } else if(msg == AJP13_SEND_HEADERS) {
	           responseHeader=parseHeaders();
	       } else if(msg == AJP13_GET_BODY_CHUNK) {
	           setNextBodyChunk();
	           send();
	       }
	       msg = getMessage();
	   }
	}
	
	private void sendPostBody() throws IOException {
	   setNextBodyChunk();
	   send();
	}
	
	private void setNextBodyChunk() throws IOException {
		int len = (body == null) ? 0 : body.available();
		if(len < 0) {
			len = 0;
		} else if(len > Constants.MAX_SEND_SIZE) {
			len = Constants.MAX_SEND_SIZE;
		}
		outpos = 4;
		int nr = 0;
		if(len > 0) {
			nr = body.read(outbuf, outpos+2, len);
		}
		setInt(nr);
		outpos += nr;
	}
	
	
	private String parseHeaders() throws IOException {
		int status = getInt();
		statistics.setReplyCode(status);
	   
	   String msg = getString(null);
	   int nh = getInt();
	   StringBuilder sb = new StringBuilder();
	   sb.append(Constants.HTTP_1_1 ).append(status).append(" ").append(msg).append(Constants.NEWLINE);//$NON-NLS-1$//$NON-NLS-2$
	   for(int i=0; i < nh; i++) {
	       // Currently, no Tomcat version sends translated headers
	       String name;
	       int thn = peekInt();
	       if((thn & 0xff00) == Constants.AJP_HEADER_BASE) {
	           name = responseTransArray[(thn&0xff)-1];
	           getInt();
	       } else {
	           name = getString(sb);
	       }
	       String value = getString(sb);
	/*       if(HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
	           res.setContentType(value);
	           res.setEncodingAndType(value);
	       } else if(HEADER_SET_COOKIE.equalsIgnoreCase(name)) {
	           CookieManager cookies = getCookieManager();
	           if(cookies != null) {
	               cookies.addCookieFromHeader(value, res.getURL());
	           }
	       }*/
	       sb.append(name).append(Constants.COLON_SPACE).append(value).append(Constants.NEWLINE);
	   }
	   
	   return sb.toString();
	}
	
	
	private int getMessage() throws  ProtocolException, IOException {
	   InputStream is = channel.getInputStream();
	   inpos = 0;
	   int nr=0;
	   
	   try{
		   nr = is.read(inbuf, inpos, 4);
	   }catch(InterruptedIOException e){
		   throw new SocketTimeoutException(e.getMessage());
	   }
	   
	   if(nr != 4) {
	       channel.close();
	       channel = null;
	       throw new ProtocolException("Protocol Error. Unexpected response.");
	   }
	//int mark = 
	   getInt();
	   int len = getInt();
	   int toRead = len;
	   int cpos = inpos;
	   while(toRead > 0) {
		   try {
			   nr = is.read(inbuf, cpos, toRead);
		   } catch( ArrayIndexOutOfBoundsException e) {
			   throw new ProtocolException("Protocol Error. Unexpected response.");
		   }
	       cpos += nr;
	       toRead -= nr;
	   }
	   return getByte();
	}
	
	private byte getByte() {
	   return inbuf[inpos++];
	}
	
	private int getInt() {
	   int res = (inbuf[inpos++]<<8)&0xff00;
	   res += inbuf[inpos++]&0xff;
	   return res;
	}
	
	private int peekInt() {
	   int res = (inbuf[inpos]<<8)&0xff00;
	   res += inbuf[inpos+1]&0xff;
	   return res;
	}
	
	private String getString(StringBuilder sb) throws IOException {
	   int len = getInt();
	   String s = new String(inbuf, inpos, len, "iso-8859-1");//$NON-NLS-1$
	   inpos+= len+1;
	   return s;
	}
	
	private void cleanup(){
		responseHeader=null;
		responseData.reset();
	}
	
	private void log(String message){
		if(cc.isVerbose()){
			cc.getOutput().print(message);
		}
	}
	
	class ProtocolException extends Exception{
		
		private static final long serialVersionUID = 149146185282537074L;

		public ProtocolException(String message){
			super(message);
		}
		
	}
}
