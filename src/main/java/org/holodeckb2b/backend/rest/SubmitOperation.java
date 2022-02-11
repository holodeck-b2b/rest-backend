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


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.UUID;

import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.receivers.AbstractMessageReceiver;
import org.apache.commons.fileupload.util.Streams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.common.axis2.NOPMessageBuilder;
import org.holodeckb2b.common.messagemodel.CollaborationInfo;
import org.holodeckb2b.common.messagemodel.Payload;
import org.holodeckb2b.common.messagemodel.SchemaReference;
import org.holodeckb2b.common.messagemodel.TradingPartner;
import org.holodeckb2b.common.messagemodel.UserMessage;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.general.IService;
import org.holodeckb2b.interfaces.messagemodel.IPayload.Containment;
import org.holodeckb2b.interfaces.submit.MessageSubmitException;

/**
 * Implements a REST service for submission of a single payload that should be send to the receiver.  
 * <p>The message meta-data must be supplied through HTTP headers, see {@link HTTPHeaders} for an overview of available
 * headers. As message meta-data and P-Mode settings are combined to create a complete set of meta-data the required
 * data to include in the submission is limited to the <i>PMode identifier</i>, while the rest depends on the P-Mode.
 * <p>The result of the submission is returned to the back-end using the HTTP status codes with 202 indicating the 
 * document was submitted successfully and HTTP 400 or 500 indicating that there was an issue caused by an error in
 * the submission respectively in processing the submission. An additional HTTP header <i>X-Error</i> is added to the
 * response that includes a (short) description of the error.
 * <p>NOTE 1: Although allowed by <a href="https://tools.ietf.org/html/rfc7230#section-3.2.2">HTTP 1.1 (RFC7230)</a> 
 * this implementation does not support the use of multiple occurrence of a header for comma separated list values. 
 * <p>NOTE 2: The <i>X-Error</i> header is automatically included in the response by the <a href=
 * "https://github.com/holodeck-b2b/axis2-rest-status-only-error">"rest-no-error-content" Axis2 module</a>.  
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class SubmitOperation extends AbstractMessageReceiver {

	private Logger	log = LogManager.getLogger(SubmitOperation.class);
	
	@SuppressWarnings("unchecked")
	@Override
	public void invokeBusinessLogic(final MessageContext msgCtx) throws AxisFault {
		
		boolean submitted = false;
		String	payloadFile = null;
		try {
			log.debug("Received submission request");
			final HTTPHeaders headers = new HTTPHeaders((HashMap<String, String>) 
																msgCtx.getProperty(MessageContext.TRANSPORT_HEADERS));
			log.trace("Read basic message meta-data from HTTP headers");
			UserMessage mmd = createBaseSubmission(headers);
			
			log.trace("Read payload meta-data from HTTP headers");
			Payload payload = createPayloadData(headers);
						
			log.trace("Saving the payload data to file");
			payloadFile = createPayloadFile(msgCtx);
			payload.setContentLocation(payloadFile);
			mmd.addPayload(payload);
			
			log.debug("Submitting the message to the Holodeck B2B Core");
			String messageId = HolodeckB2BCoreInterface.getMessageSubmitter().submitMessage(mmd, true);
			submitted = true;
			log.info("Successfully submitted message to Holodeck B2B Core, messageId={}", messageId);			
		} catch (MessageSubmitException submissionError) {
			log.error("Error in Submission: {}", submissionError.getMessage());
			throw new AxisFault(submissionError.getMessage(), SOAP12Constants.QNAME_SENDER_FAULTCODE);
		} catch (IOException payloadSaveFailure) {
			throw new AxisFault("Internal Error", SOAP12Constants.QNAME_RECEIVER_FAULTCODE);
		} finally {
			if (!submitted && payloadFile != null) {
				log.trace("Removing temporary file with payload data");
				try {
					Files.deleteIfExists(Paths.get(payloadFile));
				} catch (IOException e) {
					log.warn("Could not delete created temp file ({}), remove manually!", payloadFile);
				}
			}
		}
	}

	/**
	 * Creates a {@link UserMessage} for submission to the Holodeck B2B Core filled with the basic meta-data as provided
	 * in the HTTP headers. 
	 *  
	 * @param headers	The HTTP headers from the request
	 * @return			UserMessage object filled with basic message meta-data
	 * @throws MessageSubmitException	When a required meta-data element is not included in the submission or is 
	 * 									incorrectly formatted
	 */
	private UserMessage createBaseSubmission(final HTTPHeaders headers) throws MessageSubmitException {
		final UserMessage mmd = new UserMessage();
		
		final String pmodeId = headers.getHeader(HTTPHeaders.PMODE_ID);
		if (Utils.isNullOrEmpty(pmodeId)) 
			throw new MessageSubmitException("Missing required PMode.id");
		mmd.setPModeId(pmodeId);
		
		mmd.setMessageId(headers.getHeader(HTTPHeaders.MESSAGE_ID));
		try {
			mmd.setTimestamp(Utils.fromXMLDateTime(headers.getHeader(HTTPHeaders.TIMESTAMP)));
		} catch (ParseException e) {
			throw new MessageSubmitException("Invalid timestamp specified");  
		}
		mmd.setRefToMessageId(headers.getHeader(HTTPHeaders.REF_TO_MESSAGE_ID));
		
		final IPartyId senderId = headers.getPartydId(HTTPHeaders.SENDER_PARTY_ID);
		final String   senderRole = headers.getHeader(HTTPHeaders.SENDER_ROLE);
		if (senderId != null || !Utils.isNullOrEmpty(senderRole)) {
			final TradingPartner sender = new TradingPartner();
			sender.addPartyId(senderId);
			sender.setRole(senderRole);
			mmd.setSender(sender);
		}
		final IPartyId receiverId = headers.getPartydId(HTTPHeaders.RECEIVER_PARTY_ID);
		final String receiverRole = headers.getHeader(HTTPHeaders.RECEIVER_ROLE);
		if (receiverId != null || !Utils.isNullOrEmpty(receiverRole)) {
			final TradingPartner receiver = new TradingPartner();
			receiver.addPartyId(receiverId);
			receiver.setRole(receiverRole);
			mmd.setReceiver(receiver);
		}
		
		mmd.setMessageProperties(headers.getProperties(HTTPHeaders.MESSAGE_PROPS));
		
		final String   conversationId = headers.getHeader(HTTPHeaders.CONVERSATION_ID);
		final IService service = headers.getService();
		final String   action = headers.getHeader(HTTPHeaders.ACTION);
		if (!Utils.isNullOrEmpty(conversationId) || service != null || !Utils.isNullOrEmpty(action)) {
			CollaborationInfo cInfo = new CollaborationInfo();
			cInfo.setConversationId(conversationId);
			cInfo.setService(service);
			cInfo.setAction(action);
			mmd.setCollaborationInfo(cInfo);
		}		
		
		return mmd;
	}
	
	/**
	 * Creates a {@link Paylaod} filled with the meta-data about the paylaod as provided in the HTTP headers. 
	 *  
	 * @param headers	The HTTP headers from the request
	 * @return			Payload object filled with the payload's meta-data
	 * @throws MessageSubmitException	When a required meta-data element is not included in the submission or is 
	 * 									incorrectly formatted
	 */	
	private Payload createPayloadData(final HTTPHeaders headers) throws MessageSubmitException {
		final Payload payload = new Payload();
		
		final String containmentHdrVal = headers.getHeader(HTTPHeaders.CONTAINMENT);		
		Containment containment = null;
		if (!Utils.isNullOrEmpty(containmentHdrVal)) {
			try {
				containment = Containment.valueOf(containmentHdrVal.toUpperCase());
			} catch (IllegalArgumentException invalidContainment) {
				throw new MessageSubmitException("Unknown containment value: " + containmentHdrVal);
			}
			if (containment == Containment.EXTERNAL)
				throw new MessageSubmitException("External payload reference not supported");
		} else
			containment = Containment.ATTACHMENT;
		
		payload.setContainment(containment);		
		payload.setMimeType(headers.getHeader(HTTPHeaders.MIME_TYPE));
		payload.setProperties(headers.getProperties(HTTPHeaders.PART_PROPS));
		
		final String schemaNS = headers.getHeader(HTTPHeaders.SCHEMA_NS);
		final String schemaVersion = headers.getHeader(HTTPHeaders.SCHEMA_VERSION);
		final String schemaLocation = headers.getHeader(HTTPHeaders.SCHEMA_LOCATION);
		if (!Utils.isNullOrEmpty(schemaLocation) || !Utils.isNullOrEmpty(schemaNS) 
			 || !Utils.isNullOrEmpty(schemaVersion)) {
			if (Utils.isNullOrEmpty(schemaLocation))
				throw new MessageSubmitException("Missing required location of the payload's schema");
			SchemaReference schemaInfo = new SchemaReference(schemaNS, schemaLocation);
			schemaInfo.setVersion(schemaVersion);
			payload.setSchemaReference(schemaInfo);
		}
		
		final String payloadURI = headers.getHeader(HTTPHeaders.PAYLOAD_URI);
		if (!Utils.isNullOrEmpty(payloadURI))
			payload.setPayloadURI(payloadURI);

		return payload;
	}

	/**
	 * Helper method to save the submitted payload to a temp file so it can be submitted to the Holodeck B2B Core. This
	 * REST Service uses the {@link NOPMessageBuilder} so it can directly access the request input stream. This way the
	 * payload data is saved as is.  
	 * <p>The file is saved in the <i>hb2b-rest-submit</i> sub directory of the Holodeck B2B <i>temp</i> directory and 
	 * is created if it doesn't exist yet. 
	 *  
	 * @param msgCtx	The Axis2 message context 
	 * @return			The path to the temporary file in which the payload data is saved
	 * @throws IOException	If the payload data cannot be saved to file.
	 */
	private String createPayloadFile(final MessageContext msgCtx) throws IOException {
		final Path tmpStorageDir =  HolodeckB2BCoreInterface.getConfiguration().getTempDirectory()
																						.resolve("hb2b-rest-submit");
		log.trace("Check {} is available for temporary storage of submitted payloads.", tmpStorageDir); 
		if (!Files.exists(tmpStorageDir)) {
			log.debug("Temp directory for storing submitted payloads does not exist, creating it now");
			try {
				Files.createDirectories(tmpStorageDir);
			} catch (IOException e) {
				log.fatal("Could not create the temp directory for storing REST submissions ({})! Error: {}", 
							tmpStorageDir.toString(), e.getMessage());
				throw e;
			}
		} else if (!Files.isDirectory(tmpStorageDir) || !Files.isWritable(tmpStorageDir)) {
			log.fatal("Temp directory used for REST submissions ({}) is not a (writable) directory!", 
						tmpStorageDir.toString());
			throw new IOException(tmpStorageDir.toString() + "is not a (writable) directory!");
		}
		
		final Path filePath = tmpStorageDir.resolve(UUID.randomUUID().toString());
		try {
			log.trace("Saving binary payload");
			try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
				Streams.copy((InputStream) msgCtx.getProperty(NOPMessageBuilder.REQUEST_INPUTSTREAM), fos, false);
			} 
		} catch (Throwable saveFailure) {
        	log.error("An error occurred saving the XML to temp file ({}). Error: {}", filePath.toString(),
    				saveFailure.getMessage());
        	throw new IOException("Could not save payload to temp file", saveFailure);
		}
		
		log.debug("Saved payload in temp file ({})", filePath.toAbsolutePath().toString());
		return filePath.toAbsolutePath().toString();
	}	
}
