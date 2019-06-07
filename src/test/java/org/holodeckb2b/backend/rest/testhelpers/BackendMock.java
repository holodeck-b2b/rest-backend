/**
 * Copyright (C) 2019 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.backend.rest.testhelpers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.util.Streams;
import org.holodeckb2b.backend.rest.HTTPHeaders;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulates a back-end system that accepts REST delivery and notifications. Has three paths to simulate acceptance,
 * rejection and timeouts.
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class BackendMock {
	
	private HttpServer server;
	
	private URI			requestURL;
	private HTTPHeaders headers;	
	private byte[]		entityBody;
	
    public BackendMock(final int timeout) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/accept", new AcceptHandler());
        server.createContext("/reject", new RejectHandler());
        server.createContext("/timeout", new TimeoutHandler(timeout));
        server.setExecutor(null);         
    }
    
    public void start() {
        server.start();
    }
    
    public int getPort() {
        return server.getAddress().getPort();
    }
    
    public void stop() {
        server.stop(0);
    }
    
    public URI getRequestURL() {
    	return requestURL;
    }
    
    public HTTPHeaders getRcvdHeaders() {
    	return headers;
    }
    
    public byte[] getRcvdData() {
    	return entityBody;
    }

    class BaseHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange t) throws IOException {
			requestURL = t.getRequestURI();
			headers = null;
			entityBody = null;
			
			Map<String, String> hdrs = new HashMap<>();
			for(Map.Entry<String, List<String>> h : t.getRequestHeaders().entrySet()) {
				String hv = "";
				for(int i = 0; i < h.getValue().size() - 1; i++)
					hv += h.getValue().get(i) + ",";
				hv += h.getValue().get(h.getValue().size() - 1);
				hdrs.put(h.getKey(), hv);
			}			
			headers = new HTTPHeaders(hdrs);	
			
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				Streams.copy(t.getRequestBody(), bos, false);
				entityBody = bos.toByteArray();
			}
			
			if (entityBody.length == 0)
				entityBody = null;
		}    	
    }
    
    class AcceptHandler extends BaseHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
        	super.handle(t);
            
        	t.sendResponseHeaders(202, 0);
        	t.close();
        }
    }

    class RejectHandler extends BaseHandler {
    	@Override
    	public void handle(HttpExchange t) throws IOException {
    		super.handle(t);    		    		
    		
    		t.sendResponseHeaders(500, 0);
    		t.close();
    	}
    }
 
    class TimeoutHandler extends BaseHandler {
    	private int timeout;
    	
    	TimeoutHandler(int t) {
    		timeout = t;
    	}
    	
    	@Override
    	public void handle(HttpExchange t) throws IOException {
    		super.handle(t);
    		
    		try {
				Thread.sleep(timeout);
			} catch (InterruptedException e) {
			}

    		t.sendResponseHeaders(202, 0);
    		t.close();
    	}
    }
    
}
