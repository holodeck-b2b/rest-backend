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
package org.holodeckb2b.backend.rest;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.fileupload.util.Streams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.common.util.MessageUnitUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.delivery.IMessageDeliverer;
import org.holodeckb2b.interfaces.delivery.IMessageDelivererFactory;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.general.ISchemaReference;
import org.holodeckb2b.interfaces.messagemodel.ICollaborationInfo;
import org.holodeckb2b.interfaces.messagemodel.IErrorMessage;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;

/**
 * Is a Holodeck B2B <i>delivery method</i> that implements a REST client for the delivery and notification of message 
 * units to the back-end system. 
 * <p>The message meta-data of the received message unit is provided through HTTP headers, see {@link HTTPHeaders} for 
 * an overview of available headers. A <i>User Message</i> is delivered using the POST method with the payload data 
 * (only one is supported) contained in the HTTP entity body. As <i>Signal Message</i>s do not contain payloads their
 * meta-data is notified using the POST method without an entity body.
 * <p>This delivery method takes two parameters:ol>
 * <li><b>URL</b> [REQUIRED] : the URL where the REST service is hosted by the business application. "/deliver" will 
 * be added to this URL when delivering <i>User Messages</i> and for "/notify/receipt" and "/notify/error" for 
 * notification of <i>Receipt</i> respectively </i>Error</i> Signals.</li>
 * <li><b>TIMEOUT</b> [OPTIONAL] : the time (in milliseconds) the delivery method should wait for the back-end system
 * to accept the delivery and notification.</li></ol> 
 * <p>The back-end MUST respond only with an HTTP status code and use a code in the 2xx range to indicate that it 
 * accepted the delivery or notification. Any order code is interpreted as failure and reported as such to the 
 * Holodeck B2B Core.
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class NotifyAndDeliverOperation implements IMessageDelivererFactory {
	private static final Logger log = LogManager.getLogger(NotifyAndDeliverOperation.class);
	
	/**
	 * Name of the parameter that holds the back-end URL
	 */
	public static final String P_BACKEND_URL = "URL";
	/**
	 * Name of the parameter that contains the timeout value  
	 */
	public static final String P_TIMEOUT = "TIMEOUT";

	/**
	 * The default timeout is 10 seconds
	 */
	private static final int DEFAULT_TIMEOUT = 10000;
	
	/**
	 * The base URL where the back-end will receive deliveries of User Messages and notifications of Signals
	 */
	private String	baseURL;
	/**
	 * The time to wait for getting connection or response from the back-end.  
	 */
	private int timeout;
	
	@Override
	public void init(Map<String, ?> settings) throws MessageDeliveryException {
        // Check that a well formed URL is given
        try {
        	new URL(baseURL = (String) settings.get(P_BACKEND_URL));
        	if (!baseURL.endsWith("/"))
        		baseURL += "/";        	
        } catch (MalformedURLException | ClassCastException ex) {
            log.fatal("Invalid back-end URL specified: " + settings.get(P_BACKEND_URL));
            throw new MessageDeliveryException("Invalid back-end URL specified!");
        }
        try {
        	timeout = Integer.parseInt((String) settings.get(P_TIMEOUT));
        } catch (NumberFormatException nan) {
        	timeout = DEFAULT_TIMEOUT;
        }
        log.info("Initialised REST delivery method.\n\tBase URL = {}\n\tTimeout  = {}", baseURL, timeout);
	}

	@Override
	public IMessageDeliverer createMessageDeliverer() throws MessageDeliveryException {
		return new NandDDeliverer();
	}
	
	/**
	 * Is the {@link IMessageDeliverer} implementation that does all the work :-) 
	 */
	class NandDDeliverer implements IMessageDeliverer {

		@Override
		public void deliver(IMessageUnit rcvdMsgUnit) throws MessageDeliveryException {
			if (rcvdMsgUnit instanceof IUserMessage) 
				deliverUserMessage((IUserMessage) rcvdMsgUnit);
			else 
				notifySignalMessage((ISignalMessage) rcvdMsgUnit);
		}
		
		
		private void deliverUserMessage(IUserMessage userMsg) throws MessageDeliveryException {		
			final Collection<IPayload> payloads = userMsg.getPayloads();
			if (payloads != null && payloads.size() > 1) {
				log.fatal("User Message [msgId={}] cannot be delivered because it contains more than 1 payload!", 
						  userMsg.getMessageId());
				throw new MessageDeliveryException("Too many payloads!", true);
			} 
			final IPayload payload = Utils.isNullOrEmpty(payloads) ? null : payloads.iterator().next();
				
	        log.debug("Preparing User Message for delivery to back-end");	        
            HTTPHeaders headers = new HTTPHeaders();
    		headers.setHeader(HTTPHeaders.PMODE_ID, userMsg.getPModeId());
    		headers.setHeader(HTTPHeaders.MESSAGE_ID, userMsg.getMessageId());
    		headers.setHeader(HTTPHeaders.TIMESTAMP, Utils.toXMLDateTime(userMsg.getTimestamp()));
    		headers.setHeader(HTTPHeaders.REF_TO_MESSAGE_ID, userMsg.getRefToMessageId());
    		headers.setPartyId(HTTPHeaders.SENDER_PARTY_ID, userMsg.getSender().getPartyIds().iterator().next());
    		headers.setHeader(HTTPHeaders.SENDER_ROLE, userMsg.getSender().getRole());
    		headers.setPartyId(HTTPHeaders.RECEIVER_PARTY_ID, userMsg.getReceiver().getPartyIds().iterator().next());
    		headers.setHeader(HTTPHeaders.RECEIVER_ROLE, userMsg.getReceiver().getRole());
    		ICollaborationInfo ci = userMsg.getCollaborationInfo();
    		if (ci != null) {
    		   headers.setHeader(HTTPHeaders.CONVERSATION_ID, ci.getConversationId());
    		   headers.setServiceHeader(ci.getService());
    		   headers.setHeader(HTTPHeaders.ACTION, ci.getAction());
    		}    		
    		if (payload != null) {	    				    
	    		headers.setHeader(HTTPHeaders.MIME_TYPE, payload.getMimeType());
	    		headers.setProperties(HTTPHeaders.PART_PROPS, payload.getProperties());            
	            ISchemaReference plSchemaInfo = payload.getSchemaReference();
	            if (plSchemaInfo != null) {
		    		headers.setHeader(HTTPHeaders.SCHEMA_LOCATION, plSchemaInfo.getLocation());
		    		headers.setHeader(HTTPHeaders.SCHEMA_VERSION, plSchemaInfo.getVersion());
		    		headers.setHeader(HTTPHeaders.SCHEMA_NS, plSchemaInfo.getNamespace());
	            }
    		}

            try {
	            log.debug("Preparing connection to back-end");	       
	            final String targetURL = baseURL + "deliver";
	            HttpURLConnection con = (HttpURLConnection) new URL(targetURL).openConnection();
	            con.setRequestMethod("POST");
	            con.setDoOutput(true);
	            con.setDoInput(true);
	            con.setConnectTimeout(timeout); 
	            con.setReadTimeout(timeout); 
	            headers.getAllHeaders().forEach((n, v) -> con.setRequestProperty(n, v));
	            
	            if (payload != null) {
		            log.debug("Adding payload as HTTP entity body");
		            try (FileInputStream fis = new FileInputStream(payload.getContentLocation())) {
						Streams.copy(fis, con.getOutputStream(), true);
					}
	            } else {
	            	log.debug("User Message does not have payload, send empty entity body");
	            	con.setFixedLengthStreamingMode(0);
	            }
	            
	            log.debug("Sending User Message to back-end at {}", baseURL);
	        	con.connect();
	        	int responseCode = con.getResponseCode();
	        	String responseMsg = con.getResponseMessage();
	        	con.disconnect();
	            if (responseCode / 200 != 1)
	            	throw new IOException("Back-end refused delivery! HTTP error= " + responseCode + "/" + responseMsg);
	            else 
	            	log.info("Successful delivered payload of message [msgId={}] to back-end", userMsg.getMessageId());
            } catch (IOException conError) {
            	log.error("Error in delivery of User Message [msgId={}]. Error details: {}", userMsg.getMessageId(),
            				conError.getMessage());
            	throw new MessageDeliveryException("Error in delivery to back-end", conError);
            }
		}

		private void notifySignalMessage(ISignalMessage signal) throws MessageDeliveryException {
			if (Utils.isNullOrEmpty(signal.getRefToMessageId())) {
				log.fatal("Signal message [msgId={}] does not include reference to other message unit",
						  signal.getMessageId());
				throw new MessageDeliveryException("Missing reference to other message unit", true);
			}
			
	        log.debug("Preparing {} for notification to back-end", MessageUnitUtils.getMessageUnitName(signal));	        
            HTTPHeaders headers = new HTTPHeaders();
    		headers.setHeader(HTTPHeaders.PMODE_ID, signal.getPModeId());
    		headers.setHeader(HTTPHeaders.MESSAGE_ID, signal.getMessageId());
    		headers.setHeader(HTTPHeaders.TIMESTAMP, Utils.toXMLDateTime(signal.getTimestamp()));
    		headers.setHeader(HTTPHeaders.REF_TO_MESSAGE_ID, signal.getRefToMessageId());
			
    		if(signal instanceof IErrorMessage)
    			headers.setErrorMessage(((IErrorMessage) signal).getErrors());
    		
            try {
	            log.debug("Preparing connection to back-end");	       
	            final String targetURL = baseURL + "notify/" + (signal instanceof IReceipt ? "receipt" : "error");
	            HttpURLConnection con = (HttpURLConnection) new URL(targetURL).openConnection();
	            con.setRequestMethod("POST");
	            con.setDoOutput(true);
	            con.setFixedLengthStreamingMode(0);
	            con.setDoInput(true);
	            con.setConnectTimeout(timeout); 
	            con.setReadTimeout(timeout);
	            headers.getAllHeaders().forEach((n, v) -> con.setRequestProperty(n, v));
	            
	            log.debug("Sending {} to back-end at {}", MessageUnitUtils.getMessageUnitName(signal) ,targetURL);
	        	con.connect();
	        	int responseCode = con.getResponseCode();
	        	String responseMsg = con.getResponseMessage();
	        	con.disconnect();
	            if (responseCode / 200 != 1)
	            	throw new IOException("Back-end refused notification! HTTP error= " + responseCode 
	            																		+ "/" + responseMsg);
	            else 
	            	log.info("Successful notified {} [msgId={}] to back-end", 
	            				MessageUnitUtils.getMessageUnitName(signal), signal.getMessageId());	            
            } catch (IOException conError) {
            	log.error("Error in notification of {} [msgId={}]. Error details: {}", 
            				MessageUnitUtils.getMessageUnitName(signal), signal.getMessageId(), conError.getMessage());
            	throw new MessageDeliveryException("Error in notification to back-end", conError);
            }	
		}
	}
}
