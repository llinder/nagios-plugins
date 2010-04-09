package org.apache.coyote.ajp.client;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class AJPClient
{
	protected int timeout;
	protected File statistics;
	protected String bind_address;
	protected File header_file;
	protected String headers[];
	protected String user_agent;
	protected String urls[];
	protected ClientContext context = new ClientContext();
	protected AjpProcessor[] processors;
	
	
	abstract protected void usage();
	
	protected ClientContext getContext()
	{
		return context;
	}
	
	protected Map<String,String> getHeaders( String headers )
	{
		HashMap<String, String> map = new HashMap<String, String>();
		
		String[] tmp=null;
    	if( headers.contains( "\\" ) )
    	{
    		tmp=headers.split( "\\\\" );
    	}
    	else
    	{
    		tmp=new String[1];
    		tmp[0]=headers;
    	}
    	
        for( String header:tmp )
        {
        	String[] tuple=header.split( ":" );
        	map.put( tuple[0], tuple[1] ); // Header name and header value
        }
        
        return map;
	}
	
	protected Map<String,String> getQueryParams( String params )
	{
		HashMap<String, String> map = new HashMap<String, String>();
		
		String[] tmp=null;
    	if( params.contains( "\\" ) )
    	{
    		tmp=params.split( "\\\\" );
    	}
    	else
    	{
    		tmp=new String[1];
    		tmp[0]=params;
    	}
    	
        for( String param:tmp )
        {
        	String[] tuple=param.split( ":" );
        	map.put( tuple[0], tuple[1] ); // Parameter name and parameter value
        }
        
        return map;
	}
	
	protected void error(String... switches){
		System.out.println("ERROR: Invalid combination of switches. ");
		for(String ss:switches){
			System.out.print(ss+" ");
		}
		System.out.println("cannot be used together.");
		System.out.println();
		usage();
	}
	
	protected void init() throws Exception{
		context.init();
		Set<RequestContext> ctxs=context.getRequestContexts();
		processors=new AjpProcessor[ctxs.size()];
		
		int index=0;
		for(Iterator<RequestContext> it=ctxs.iterator();it.hasNext();){
			AjpProcessor processor=new AjpProcessor(it.next());
			processors[index++]=processor;
		}
	}
	
	protected void run()
	{
		for( AjpProcessor processor:processors )
		{
			new Thread(processor).start();
		}
		
		synchronized(context)
		{
			while(context.getProcessorCount() > 0)
			{
				try
				{
					context.wait();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	protected int execute() throws Exception
	{
		init();
		run();
		return 0;
	}
}
