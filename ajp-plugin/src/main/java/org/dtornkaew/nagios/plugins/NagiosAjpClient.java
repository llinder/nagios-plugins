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

package org.dtornkaew.nagios.plugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.coyote.ajp.client.AJPClient;
import org.apache.coyote.ajp.client.Headers;
import org.apache.coyote.ajp.client.Statistics;

/**
 * Nagios AJP plugin.
 * 
 * @author Lance Linder
 */
public final class NagiosAjpClient extends AJPClient
{
	/**
	 * Status code NAGIOS OK.
	 */
	private static final int NAGIOS_OK = 0;
	private static final String NAGIOS_OK_MSG = "AJP OK: ";
	
	/**
	 * Status code NAGIOS WARNING.
	 */
	private static final int NAGIOS_WARNING = 1;
	private static final String NAGIOS_WARNING_MSG = "AJP WARNING: ";
	
	/**
	 * Status code NAGIOS CRITICAL
	 */
	private static final int NAGIOS_CRITICAL = 2;
	private static final String NAGIOS_CRITICAL_MSG = "AJP CRITICAL: ";
	
	/**
	 * Status code NAGIOS UNKNOWN.
	 */
	private static final int NAGIOS_UNKNOWN = 3; // 
	private static final String NAGIOS_UNKNOWN_MSG = "AJP UNKNOWN: ";
	
	/**
	 * Service URL
	 */
	private static final String PROP_URL = "url";
	/**
	 * Connector timeout
	 */
	private static final String PROP_TIMEOUT = "timeout";
	/**
	 * 
	 */
	private static final String PROP_REQUESTS_FILE = "requests-file";
	/**
	 * Request headers
	 */
	private static final String PROP_HEADERS = "headers";
	/**
	 * Http version
	 */
	private static final String PROP_HTTP_VERSION = "http-version";
	/**
	 * POST request body file
	 */
	private static final String PROP_BODY_FILE = "http-body-file";
	/**
	 * Request parameters
	 */
	private static final String PROP_QUERY = "query";
	/**
	 * Verbose output
	 */
	private static final String PROP_VERBOSE = "verbose";
	/**
	 * User agent
	 */
	private static final String PROP_USER_AGENT = "user-agent";
	/**
	 * Request type (GET,POST)
	 */
	private static final String PROP_METHOD = "method";
	/**
	 * Help output
	 */
	private static final String PROP_HELP = "help";
	/**
	 * Critical response threshold
	 */
	private static final String PROP_THRESHOLD_CRITICAL = "threshold-critical";
	/**
	 * Warning response threshold
	 */
	private static final String PROP_THRESHOLD_WARNING = "threshold-warning";
	
	/**
	 * Response time warning threshold in milliseconds
	 */
	private Long thresholdWarning;
	/**
	 * Response time critical threshold in milliseconds
	 */
	private Long thresholdCritical;
	
	@Override
	protected int execute() throws Exception, NagiosAjpClientException
	{
		int exitCode = super.execute();
		
		List<Statistics> stats =
			getContext().getStatistics();
		
		// Nagios AJP client doesn't perform multiple calls... should only have one stat here.
		Iterator<Statistics> i = stats.iterator();
		if( i.hasNext() )
		{
			Statistics stat = i.next();
			if( stat.getReplyCode() != 200 )
			{
				System.out.print( NAGIOS_CRITICAL_MSG + "Expected 200 response code but recieved " + stat.getReplyCode()+" response code for url="+stat.getUrl()+" " );
				exitCode = NAGIOS_CRITICAL;
			}
			else
			{
				if( thresholdCritical != null && stat.getTimeElapsed() >= thresholdCritical )
				{
					System.out.print( NAGIOS_CRITICAL_MSG + "Expected response in less than "+thresholdCritical+"ms but was "+stat.getTimeElapsed()+"ms. " );
					exitCode = NAGIOS_CRITICAL;
				}
				else if( thresholdWarning != null && stat.getTimeElapsed() >= thresholdWarning )
				{
					System.out.print( NAGIOS_WARNING_MSG + "Expected response in less than "+thresholdWarning+"ms but was "+stat.getTimeElapsed()+"ms. " );
					exitCode = NAGIOS_WARNING;
				}
				else
				{
					System.out.print( NAGIOS_OK_MSG );
					exitCode = NAGIOS_OK;
				}
			
			}
			
			System.out.print( getStatusOutput( stat ) );
			System.out.println( getPerformanceDataOutput( stat ) );
		}
		else
		{
			System.out.println( NAGIOS_CRITICAL_MSG + "Expected to recieve one Statistic but did not." );
			exitCode = NAGIOS_CRITICAL;
		}
		
		
		
		return exitCode;
	}

	@Override
	protected void usage()
	{
		try
		{
			outputResource( getClass().getResource("/usage.txt") );
		}
		catch ( NagiosAjpClientException e )
		{
			System.out.println( NAGIOS_CRITICAL_MSG + e.getMessage() );
			if( context.isVerbose() )
				e.printStackTrace( System.out );
			System.exit( NAGIOS_CRITICAL );
		}
	}
	
	private String getStatusOutput( Statistics stat )
	{
		StringBuilder output = new StringBuilder();
		
		output.append( "HTTP/"+context.getHttpVersion() );
		output.append( " " );
		output.append( stat.getReplyCode() );
		output.append( " - " );
		output.append( stat.getTimeElapsed() );
		output.append( " millisecond response time" );
		
		return output.toString();
	}
	
	private String getPerformanceDataOutput( Statistics stat )
	{
		StringBuilder output = new StringBuilder();
		
		output.append(" | ");
		
		output.append("Time=");
		output.append(stat.getTimeElapsed());
		output.append(";");
		if (thresholdWarning != null)
            output.append(thresholdWarning);
        output.append(";");
        if (thresholdCritical != null)
            output.append(thresholdCritical);
		output.append(";;");
		
		return output.toString();
	}
	
	private void setProperties( Properties props )
	{
		// URL
		this.context.setUrls( new String[1] );
		this.context.getUrls()[0] = props.getProperty( PROP_URL );
		
		// Timeout
		this.context.setTimeout(
			Double.parseDouble( props.getProperty( PROP_TIMEOUT, "30" ) ) );
		
		// Request file
		if( props.containsKey( PROP_REQUESTS_FILE ) )
			this.context.setRequestsFile( new File( props.getProperty( PROP_REQUESTS_FILE ) ) ); 
		
		// Headers
		if( props.containsKey( PROP_HEADERS ) )
			this.context.setHeaders( getHeaders( props.getProperty( PROP_HEADERS ) ) );
		
		// Http Version
		this.context.setHttpVersion( props.getProperty( PROP_HTTP_VERSION, "1.1" ) );
		
		// Body file
		if( props.containsKey( PROP_BODY_FILE ) )
			this.context.setBodyFile( new File( props.getProperty( PROP_BODY_FILE ) ) );
		
		// Query params
		if( props.containsKey( PROP_QUERY ) )
			this.context.setQueries( getQueryParams( props.getProperty( PROP_QUERY ) ) );
	
		// Verbose
		if( props.containsKey( PROP_VERBOSE ) )
			this.context.setVerbose( true );
		
		
		// User agent
		this.context.getHeaders().put( Headers.USER_AGENT , props.getProperty( PROP_USER_AGENT, "AJPClient/1.0" ) );
		
		// Request method
		this.context.setMethod( 
			props.getProperty( PROP_METHOD, "GET" ) );
		
		// Critical threshold
		this.thresholdCritical = ( props.containsKey( PROP_THRESHOLD_CRITICAL ) ) ? Long.parseLong( props.getProperty( PROP_THRESHOLD_CRITICAL ) ) : null;
		
		// Warning threshold
		this.thresholdWarning = ( props.containsKey( PROP_THRESHOLD_WARNING ) ) ? Long.parseLong( props.getProperty( PROP_THRESHOLD_WARNING ) ) : null;
	}
	
	private void outputResource(URL url) throws NagiosAjpClientException
	{
        try
        {
            Reader r = new InputStreamReader( url.openStream() );
            StringBuilder sbHelp = new StringBuilder();
            char[] buffer = new char[1024];
            for (int len = r.read(buffer); len != -1; len = r.read(buffer))
            {
                sbHelp.append(buffer, 0, len);
            }
            System.out.println( sbHelp.toString() );
            System.exit(0);
        }
        catch ( IOException e )
        {
            throw new NagiosAjpClientException( e );
        }
    }
	
	
	public static void main( String args[] ) throws Exception
	{
		NagiosAjpClient client = new NagiosAjpClient();
		int exitCode;
		Properties props = parseArguments(args);
		
		// show help
		if( props.containsKey( PROP_HELP ) )
			client.usage();
		
		// set arguments on the client context.
		client.setProperties( props );
		
		try
		{
			exitCode = client.execute();
			
		}
		catch ( NagiosAjpClientException e )
		{
			System.out.println( NAGIOS_CRITICAL_MSG + e.getMessage() );
			if( props.containsKey( PROP_VERBOSE ) )
				e.printStackTrace( System.out );
			exitCode = NAGIOS_CRITICAL;
		}
		catch ( Exception e )
		{
			System.out.println( NAGIOS_UNKNOWN_MSG + e.getMessage() );
			if( props.containsKey( PROP_VERBOSE ) )
				e.printStackTrace( System.out );
			exitCode = NAGIOS_UNKNOWN;
		}
		
		System.exit( exitCode );
	}
	
	private static Properties parseArguments( String args[] )
	{
		Properties props = new Properties();
		for(int i=0; i<args.length; i++)
		{
			// help
			if( "-h".equals( args[i] ) || "--help".equals( args[i] ) )
				props.put( PROP_HELP , "true" );
			// URL
			else if( "-u".equals( args[i] ) || "--url".equals( args[i] ) )
				props.put( PROP_URL , args[++i] );
			// timeout
			else if( "-t".equals( args[i] ) || "--timeout".equals( args[i] ) )
				props.put( PROP_TIMEOUT, args[++i] );
			// request file
			else if( "-r".equals( args[i] ) || "--request-file".equals( args[i] ) )
				props.put( PROP_REQUESTS_FILE, args[++i] );
			// headers
			else if( "-H".equals( args[i] ) || "--headers".equals( args[i] ) )
				props.put( PROP_HEADERS, args[++i] );
			// http-version
			else if( "-V".equals( args[i] ) || "--http-version".equals( args[i] ) )
				props.put( PROP_HTTP_VERSION, args[++i] );
			// post body file
			else if( "-b".equals( args[i] ) || "--body-file".equals( args[i] ) )
				props.put( PROP_BODY_FILE, args[++i] );
			// query parameters
			else if( "-q".equals( args[i] ) || "--query-params".equals( args[i] ) )
				props.put( PROP_QUERY, args[++i] );
			// verbose output
			else if( "-v".equals( args[i] ) || "--verbose".equals( args[i] ) )
				props.put( PROP_VERBOSE, "true" );
			// user agent
			else if( "-U".equals( args[i] ) || "--user-agent".equals( args[i] ) )
				props.put( PROP_USER_AGENT, args[++i] );
			// query method
			else if( "-m".equals( args[i] ) || "--method".equals( args[i] ) )
				props.put( PROP_METHOD, args[++i] );
			// warning threshold
			else if( "-w".equals( args[i] ) || "--warning".equals( args[i] ) )
				props.put( PROP_THRESHOLD_WARNING, args[++i] );
			// query method
			else if( "-c".equals( args[i] ) || "--critical".equals( args[i] ) )
				props.put( PROP_THRESHOLD_CRITICAL, args[++i] );
		}
		
		return props;
	}

}
