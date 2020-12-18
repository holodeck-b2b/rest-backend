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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Property;
import org.holodeckb2b.common.messagemodel.Service;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.general.IService;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;
import org.holodeckb2b.interfaces.submit.MessageSubmitException;

/**
 * Defines and handles the HTTP headers used in the REST back-end integration. 
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class HTTPHeaders {
	/**
	 * The identifier of the P-Mode that governs the processing of the message. Used in all operations.
	 */
	public static final String	PMODE_ID = "X-HolodeckB2B-PModeId";
	/**
	 * The messageId of the message. Used in all operations.
	 */
	public static final String	MESSAGE_ID = "X-HolodeckB2B-MessageId";	
	/**
	 * The time stamp of the message. Used in all operations.
	 */
	public static final String	TIMESTAMP = "X-HolodeckB2B-Timestamp";
	/**
	 * The refToMessageId contained in the message. Used in all operations.
	 */
	public static final String	REF_TO_MESSAGE_ID = "X-HolodeckB2B-RefToMessageId";	
	/**
	 * The PartyId of the Sender of the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	SENDER_PARTY_ID = "X-HolodeckB2B-SenderId";
	/**
	 * The Role of the Sender of the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	SENDER_ROLE = "X-HolodeckB2B-SenderRole";
	/**
	 * The PartyId of the Receiver of the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	RECEIVER_PARTY_ID = "X-HolodeckB2B-ReceiverId";
	/**
	 * The Role of the Receiver of the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	RECEIVER_ROLE = "X-HolodeckB2B-ReceiverRole";
	/**
	 * Comma separated list of the <i>Message Properties</i> of the User Message. Used in both the <i>Submit</i> and 
	 * <i>Delivery</i> operations.
	 */
	public static final String MESSAGE_PROPS = "X-HolodeckB2B-MessageProperties";
	/**
	 * The ConversationId (to be) contained in the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> 
	 * operations.
	 */
	public static final String	CONVERSATION_ID = "X-HolodeckB2B-ConversationId";
	/**
	 * The Service used by the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	SERVICE = "X-HolodeckB2B-Service";
	/**
	 * The Action used by the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String	ACTION = "X-HolodeckB2B-Action";
	/**
	 * The MIME Type of the payload contained in the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> 
	 * operations.
	 */
	public static final String	MIME_TYPE = "Content-Type";
	/**
	 * The way the payload should be contained in the User Message. Used in both the <i>Submit</i> and <i>Delivery</i> 
	 * operations.
	 */
	public static final String CONTAINMENT = "X-HolodeckB2B-Containment";
	/**
	 * Comma separated list of the <i>Part Properties</i> of the User Message. Used in both the <i>Submit</i> and 
	 * <i>Delivery</i> operations.
	 */
	public static final String PART_PROPS = "X-HolodeckB2B-PayloadProperties";
	/**
	 * The name space URI of the schema that defines the content of the payload contained in the User Message. Used in 
	 * both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String SCHEMA_NS = "X-HolodeckB2B-SchemaNamespace";	
	/**
	 * The version of the schema that defines the content of the payload contained in the User Message. Used in 
	 * both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String SCHEMA_VERSION = "X-HolodeckB2B-SchemaVersion";
	/**
	 * The location of the schema that defines the content of the payload contained in the User Message. Used in 
	 * both the <i>Submit</i> and <i>Delivery</i> operations.
	 */
	public static final String SCHEMA_LOCATION = "X-HolodeckB2B-SchemaLocation";
	/**
	 * Includes a comma separated list describing the received ebMS Errors. Only used in <i>Notify</i> operation for
	 * ebMS Errors.   
	 */
	public static final String ERROR_MESSAGE = "X-HolodeckB2B-Errors";
	
	
	private final HashMap<String, String> headers = new HashMap<>();
	
	/**
	 * Creates a new instance with an empty set of HTTP headers.
	 */
	public HTTPHeaders() {}
	
	/**
	 * Creates a new instance initialised with the given set of HTTP headers which names are converted to lower case
	 * because HTTP header names are case insensitive. 
	 * 
	 * @param hdrs	The HTTP from the submission request
	 */
	public HTTPHeaders(final Map<String, String> hdrs) {
		if (!Utils.isNullOrEmpty(hdrs)) 
			hdrs.forEach((k, v) -> this.headers.put(k.toLowerCase(), v));		
	}
	
	/**
	 * Set the value of an HTTP header.
	 * 
	 * @param name		Name of the header
	 * @param value		The value of the header
	 */
	public void setHeader(final String name, final String value) {
		if (!Utils.isNullOrEmpty(value))
			headers.put(name.toLowerCase(), value);
	}

	/**
	 * Gets the string value of the requested header.
	 * 
	 * @param name	The name of the header to get
	 * @return		The string value of the header, <code>null</code> if no such header exists
	 */
	public String getHeader(final String name) {
		return headers.get(name.toLowerCase());
	}
	
	/**
	 * Gets all HTTP headers.
	 * 
	 * @return	Map containing the name, value pairs of all headers
	 */
	public Map<String, String> getAllHeaders() {
		return headers;
	}
	/**
	 * Formats the given <i>PartyId</i> and sets it as value of the specified header. The PartyId is formatted as 
	 * "[" + <i>PartyId.type</i> + "]" + <i>PartuId.value</i> with the type being optional.
	 * 
	 * @param header	The header to set
	 * @param partyId	The PartyId to include
	 */
	public void setPartyId(final String header, final IPartyId partyId) {
		if (partyId != null && !Utils.isNullOrEmpty(partyId.getId()))
			setHeader(header, createTypedValue(partyId.getId(), partyId.getType()));
	}
	
	/**
	 * Parse a PartyId from the specified header. The PartyId should be formatted as "[" + <i>PartyId.type</i> + "]" 
	 * + <i>PartuId.value</i> with the type being optional.
	 *  
	 * @param name		The name of the header that should contain the PartyId
	 * @return			The PartyId parsed from the provided header value
	 * @throws MessageSubmitException When the header does not contain a correctly formatted PartyId
	 */
	public IPartyId getPartydId(final String name) throws MessageSubmitException {
		final String hdrVal = headers.get(name.toLowerCase());		
		if (Utils.isNullOrEmpty(hdrVal))
			return null;
		
		try {
			final String[] valueAndType = parseTypedValue(hdrVal);		
			return new PartyId(valueAndType[0], valueAndType[1]);
		} catch (MessageSubmitException invalid) {
			throw new MessageSubmitException("Invalid PartyId specified in " + name);
		}
	}	

	/**
	 * Formats the given <i>Service</i> and sets it as value of the "Service" header. The Service is formatted as 
	 * "[" + <i>Service.type</i> + "]" + <i>Service.name</i> with the type being optional.
	 * 
	 * @param service	The Service to include in the headers
	 */
	public void setServiceHeader(final IService service) {
		if (service != null && !Utils.isNullOrEmpty(service.getName()))
			setHeader(SERVICE, createTypedValue(service.getName(), service.getType()));		
	}
	
	/**
	 * Checks if there is a header specifying the Service to use and if parses the String value into a {@link IService}
	 * instance. The Service should be formatted as "[" + <i>Service.type</i> + "]" + <i>Service.name</i> with the type 
	 * being optional.
	 *  
	 * @return			The Service parsed from the provided header value
	 * @throws MessageSubmitException When the header does not contain a correctly formatted Service value
	 */
	public IService getService() throws MessageSubmitException {
		final String hdrVal = headers.get(SERVICE.toLowerCase());
		if (Utils.isNullOrEmpty(hdrVal))
			return null;
		
		try {
			final String[] valueAndType = parseTypedValue(hdrVal);		
			return new Service(valueAndType[0], valueAndType[1]);
		} catch (MessageSubmitException invalid) {
			throw new MessageSubmitException("Invalid Service value specified");
		}
	}	
	
	/**
	 * Helper method to crate a header value representing a typed value, like PartyId or Service.
	 * 
	 * @param value		The actual value
	 * @param type		The type of the value
	 * @return	The correctly formatted header value
	 */
	private static String createTypedValue(final String value, final String type) {
		StringBuilder hdrVal = new StringBuilder();		
		if (!Utils.isNullOrEmpty(type)) 
			hdrVal.append('[').append(type).append(']');
		hdrVal.append(value);
		
		return hdrVal.toString();		
	}	

	/**
	 * Helper method to parse a header value containing a typed value, like PartyId or Service.
	 * 
	 * @param hdrVal	The header value
	 * @return			2 element String array containing the parsed value and type
	 * @throws MessageSubmitException	When the given value is not in correct format 
	 */
	private static String[] parseTypedValue(final String hdrVal) throws MessageSubmitException {
		String type = null;
		int te = -1;
		if (hdrVal.startsWith("[")) {
			te = hdrVal.indexOf(']');
			if (te < 0 || te >= hdrVal.length() - 1)
				throw new MessageSubmitException();
			type = te == 1 ? null : hdrVal.substring(1, te);
		}
		String value = hdrVal.substring(te + 1);
		
		return new String[] {value, type};
	}	

	/**
	 * Formats the given colleciton of <i>Properties</i> and sets it as value of the specified header. The properties
	 * are formatted as comma separated of <i>name</i> + "=" + "[" + <i>type</i> + "]" + <i>value</i> with the type 
	 * part being optional.
	 *  
	 * @param header	The header to set
	 * @param props		The collection of properties
	 */
	public void setProperties(final String header, final Collection<IProperty> props) {
		if (Utils.isNullOrEmpty(props)) 
			return;
		
		StringBuilder hdrVal = new StringBuilder();
		for(Iterator<IProperty> it = props.iterator(); it.hasNext();) {
			IProperty p = it.next();
			hdrVal.append(p.getName()).append('=');
			if (!Utils.isNullOrEmpty(p.getType()))
				hdrVal.append('[').append(p.getType()).append(']');
			hdrVal.append(p.getValue());
			if (it.hasNext())
				hdrVal.append(',');
		}
		
		setHeader(header, hdrVal.toString());
	}
	
    // Lazily initialize since Pattern.compile() is heavy (and only needed when Message or Part properties are used)
    private static final class PropertyPatternHolder {
        /*
         * Entries are name=[type]value separated by ,
         * Allow for prepended/appended whitespace around '=' and between entries.
         *
         * Capture groups:
         *     1 - name
         *     2 - [type] (optional)
         *     3 - type (optional)
         *     4 - value
         *     5 - ,nextEntry (optional)
         *     6 - nextEntry (optional)
         */
        private static Pattern pattern =
        					Pattern.compile("\\s*([\\S&&[^=\\s]]*)\\s*=\\s*(\\[([\\S&&[^,]]*)])?([^\\[ ,][\\S&&[^,]]*)\\s*(\\,(.*))?");
    }
    
    /**
     * Parses a collection of properties from the specified header. Properties should be comma separated and formatted 
     * as <i>name</i> + "=" + "[" + <i>type</i> + "]" + <i>value</i> with the type part being optional.
     *  
     * @param header	The header name 
     * @return			The collection of properties parsed from the provided header value
     * @throws MessageSubmitException	When the header does not contain a correctly formatted list of properties
     */
    public Collection<IProperty> getProperties(final String header) throws MessageSubmitException {
        Collection<IProperty> properties = new ArrayList<>();
    	String remainder = headers.get(header.toLowerCase());
        while (remainder != null) {
            Matcher m;
            if ((m = PropertyPatternHolder.pattern.matcher(remainder)).matches()) {
            	final String name = m.group(1);
            	final String value = m.group(4);
            	final String type = Utils.isNullOrEmpty(m.group(3)) ? null : m.group(3);
            	if (Utils.isNullOrEmpty(name) || Utils.isNullOrEmpty(value))
            		throw new MessageSubmitException(header + " not in correct format");
                properties.add(new Property(name, value, type));                
                remainder = m.group(6);
            } else 
                throw new MessageSubmitException(header + " not in correct format");
            
        }        
        return properties;
    }
    
    /**
	 * Formats the given colleciton of <i>ebMS Errors</i> and sets it as value of the "Error" header. The errors
	 * are formatted as comma separated of "[" + <i>severity</i> + "]" + <i>error code</i> + "-" + <i>error detail</i>.
	 *  
	 * @param errors 	The collection of ebMS errors to include in the header
     */
    public void setErrorMessage(final Collection<IEbmsError> errors) {
    	if (Utils.isNullOrEmpty(errors))
    		return;
    	
    	StringBuilder hdrVal = new StringBuilder();
		for(Iterator<IEbmsError> it = errors.iterator(); it.hasNext();) {
			IEbmsError e = it.next();
			hdrVal.append('[').append(e.getSeverity().name()).append(']')
				  .append(e.getErrorCode());
			if (!Utils.isNullOrEmpty(e.getErrorDetail()))
				hdrVal.append('-').append(e.getErrorDetail());
			if (it.hasNext())
				hdrVal.append(',');
		}		
		setHeader(ERROR_MESSAGE, hdrVal.toString());    	
    }
}

