package org.holodeckb2b.backend.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.common.axis2.NOPMessageBuilder;
import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Property;
import org.holodeckb2b.common.messagemodel.Service;
import org.holodeckb2b.common.testhelpers.HolodeckB2BTestCore;
import org.holodeckb2b.common.testhelpers.TestMessageSubmitter;
import org.holodeckb2b.common.util.CompareUtils;
import org.holodeckb2b.commons.testing.TestUtils;
import org.holodeckb2b.commons.util.FileUtils;
import org.holodeckb2b.commons.util.MessageIdUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.general.ISchemaReference;
import org.holodeckb2b.interfaces.messagemodel.ICollaborationInfo;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.IPayload.Containment;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubmitOperationTest {
	
	static HolodeckB2BTestCore	testCore;
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		final Path hb2bHome = TestUtils.getTestResource("hb2b-home");
		if (Files.exists(hb2bHome))
				FileUtils.removeDirectory(hb2bHome);		
		Files.createDirectories(hb2bHome);
		
		testCore = new HolodeckB2BTestCore(hb2bHome.toString());
		HolodeckB2BCoreInterface.setImplementation(testCore);
	}

	@BeforeEach
	void clearSubmissions() {
		((TestMessageSubmitter) testCore.getMessageSubmitter()).clear();
	}
	
	@ParameterizedTest
	@ValueSource(strings = { "test.xml", "logo.png", "random.bin" })
	void testPayloadSave(String payloadFile) {
		final String pmodeId = "pm-test-rest";

		final String submittedPayload = TestUtils.getTestResource("payloads/").resolve(payloadFile).toString();
		String mimeType;
		try {
			mimeType = FileUtils.detectMimeType(new File(submittedPayload));
		} catch (IOException e1) {
			mimeType = "application/octet-stream";			
		}
		
		final HashMap<String, String> headers = new HashMap<>();
		headers.put(HTTPHeaders.PMODE_ID, pmodeId);		
		headers.put(HTTPHeaders.MIME_TYPE, mimeType);
		
		MessageContext msgCtx = new MessageContext();
		msgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, headers);
		
		try (FileInputStream fis = new FileInputStream(submittedPayload)) {
			msgCtx.setEnvelope((SOAPEnvelope) new NOPMessageBuilder().processDocument(fis, mimeType, msgCtx));
			new SubmitOperation().invokeBusinessLogic(msgCtx);			
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
		
		final Collection<IMessageUnit> submissions = ((TestMessageSubmitter) testCore.getMessageSubmitter()).getAllSubmitted();
		assertEquals(1, submissions.size());
		final IMessageUnit submitted = submissions.iterator().next();
		assertTrue(submitted instanceof IUserMessage);
		assertEquals(pmodeId, submitted.getPModeId());
		final IUserMessage usrMsg = (IUserMessage) submitted;
		assertEquals(1, usrMsg.getPayloads().size());
		final IPayload payload = usrMsg.getPayloads().iterator().next();
		assertEquals(Containment.ATTACHMENT, payload.getContainment());
		assertEquals(mimeType, payload.getMimeType());		
		assertNotNull(payload.getContentLocation());
		final Path saved = Paths.get(payload.getContentLocation());
		assertTrue(Files.exists(saved));
		
		boolean equal = false;
		try (FileInputStream org = new FileInputStream(submittedPayload); 
			 FileInputStream sav = new FileInputStream(saved.toFile())) {
			int o, s;
			do {
				o = org.read(); s = sav.read();
				equal = o == s;
			} while (equal && o > 0 && s > 0);
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
		assertTrue(equal);
		
		assertNull(payload.getPayloadURI());
		assertTrue(Utils.isNullOrEmpty(payload.getProperties()));
		assertNull(payload.getSchemaReference());
	}

	@Test
	void testNoPModeId() {	
		final HashMap<String, String> headers = new HashMap<>();		
		MessageContext msgCtx = new MessageContext();
		msgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, headers);
		
		AxisFault af = assertThrows(AxisFault.class, () -> new SubmitOperation().invokeBusinessLogic(msgCtx));
		assertTrue(af.getMessage().contains("PMode"));
		assertEquals(SOAP12Constants.QNAME_SENDER_FAULTCODE, af.getFaultCode());
	}
	
	@Test
	void testStreamClosure() {	
		final String pmodeId = "pm-test-rest";

		final String submittedPayload = TestUtils.getTestResource("payloads/test.xml").toString();
		final String mimeType = "application/xml";			
		
		final HashMap<String, String> headers = new HashMap<>();
		headers.put(HTTPHeaders.PMODE_ID, pmodeId);		
		headers.put(HTTPHeaders.MIME_TYPE, mimeType);
		
		MessageContext msgCtx = new MessageContext();
		msgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, headers);
		
		try (FileInputStream fis = new FileInputStream(submittedPayload)) {
			msgCtx.setEnvelope((SOAPEnvelope) new NOPMessageBuilder().processDocument(fis, mimeType, msgCtx));
			fis.close();
			AxisFault af = assertThrows(AxisFault.class, () -> new SubmitOperation().invokeBusinessLogic(msgCtx));
			assertEquals(SOAP12Constants.QNAME_RECEIVER_FAULTCODE, af.getFaultCode());
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	void testFullSet() {
		final String pmodeId = "pm-test-rest";		
		
		final String msgId = UUID.randomUUID().toString();
		final Date   timestamp = new Date();
		final String refToMsgId = UUID.randomUUID().toString();
		
		final PartyId senderId = new PartyId("senderId", "urn:org:holodeckb2b:test:partyids");
		final String senderRole = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator";		
		final PartyId receiverId = new PartyId("urn:org:holodeckb2b:test:partyids:receiverId", null);
		final String receiverRole = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder";
		
		final String conversationId = "pm-test-conversation";
		final Service service = new Service("Test", "urn:org:holodeckb2b:test");
		final String action = "SendMessage";
		
		final Collection<IProperty> msgProps = Arrays.asList(new Property[] { new Property("mp1", "val1"),
																			  new Property("mp2", "val2", "t1")																				
																			});
		
		final String plData = TestUtils.getTestResource("payloads/test.xml").toString();
		final String plMimeType = "application/xml";		
		final Containment plContainment = Containment.ATTACHMENT;
		final String plCid = MessageIdUtils.createContentId(msgId);		
		
		final String plSchemLoc = "http://test.holodeck-b2b.org/test.xsd";
		final String plSchemaVersion = "1.0";
		final String plSchemaNS = "http://test.holodeck-b2b.org/test";

		final Collection<IProperty> plProps = Arrays.asList(new Property[] { new Property("pp1", "val1", "t1"),
																			 new Property("mp2", "val2")
																		   });

		HTTPHeaders headers = new HTTPHeaders();
		headers.setHeader(HTTPHeaders.PMODE_ID, pmodeId);
		headers.setHeader(HTTPHeaders.MESSAGE_ID, msgId);
		headers.setHeader(HTTPHeaders.TIMESTAMP, Utils.toXMLDateTime(timestamp));
		headers.setHeader(HTTPHeaders.REF_TO_MESSAGE_ID, refToMsgId);
		headers.setPartyId(HTTPHeaders.SENDER_PARTY_ID, senderId);
		headers.setHeader(HTTPHeaders.SENDER_ROLE, senderRole);
		headers.setPartyId(HTTPHeaders.RECEIVER_PARTY_ID, receiverId);
		headers.setHeader(HTTPHeaders.RECEIVER_ROLE, receiverRole);
		headers.setHeader(HTTPHeaders.CONVERSATION_ID, conversationId);
		headers.setServiceHeader(service);
		headers.setHeader(HTTPHeaders.ACTION, action);
		headers.setProperties(HTTPHeaders.MESSAGE_PROPS, msgProps);
		headers.setHeader(HTTPHeaders.MIME_TYPE, plMimeType);
		headers.setHeader(HTTPHeaders.CONTAINMENT, plContainment.name());
		headers.setHeader(HTTPHeaders.CONTENT_ID, plCid);
		headers.setHeader(HTTPHeaders.SCHEMA_LOCATION, plSchemLoc);
		headers.setHeader(HTTPHeaders.SCHEMA_VERSION, plSchemaVersion);
		headers.setHeader(HTTPHeaders.SCHEMA_NS, plSchemaNS);
		headers.setProperties(HTTPHeaders.PART_PROPS, plProps);

		MessageContext msgCtx = new MessageContext();
		msgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, headers.getAllHeaders());
		
		try (FileInputStream fis = new FileInputStream(plData)) {
			msgCtx.setEnvelope((SOAPEnvelope) new NOPMessageBuilder().processDocument(fis, plMimeType, msgCtx));
			new SubmitOperation().invokeBusinessLogic(msgCtx);
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	
		final Collection<IMessageUnit> submissions = ((TestMessageSubmitter) testCore.getMessageSubmitter()).getAllSubmitted();
		assertEquals(1, submissions.size());
		final IMessageUnit submitted = submissions.iterator().next();
		assertEquals(pmodeId, submitted.getPModeId());
		assertEquals(timestamp, submitted.getTimestamp());
		assertEquals(refToMsgId, submitted.getRefToMessageId());
		
		assertTrue(submitted instanceof IUserMessage);		
		final IUserMessage usrMsg = (IUserMessage) submitted;
		
		assertTrue(CompareUtils.areEqual(Collections.singletonList(senderId), usrMsg.getSender().getPartyIds()));
		assertEquals(senderRole, usrMsg.getSender().getRole());
		assertTrue(CompareUtils.areEqual(Collections.singletonList(receiverId), usrMsg.getReceiver().getPartyIds()));
		assertEquals(receiverRole, usrMsg.getReceiver().getRole());
		
		final ICollaborationInfo ci = usrMsg.getCollaborationInfo();
		assertNotNull(ci);
		assertEquals(conversationId, ci.getConversationId());
		assertTrue(CompareUtils.areEqual(service, ci.getService()));
		assertEquals(action, ci.getAction());
				
		assertFalse(Utils.isNullOrEmpty(usrMsg.getMessageProperties()));
		assertTrue(msgProps.stream().allMatch(o -> usrMsg.getMessageProperties().parallelStream()
														 				.anyMatch(s -> CompareUtils.areEqual(o, s))));
				
		assertEquals(1, usrMsg.getPayloads().size());
		final IPayload payload = usrMsg.getPayloads().iterator().next();
		assertEquals(plContainment, payload.getContainment());
		assertEquals(plCid, payload.getPayloadURI());
		assertEquals(plMimeType, payload.getMimeType());
		assertFalse(Utils.isNullOrEmpty(payload.getProperties()));
		assertTrue(plProps.stream().allMatch(o -> payload.getProperties().parallelStream()
														 				.anyMatch(s -> CompareUtils.areEqual(o, s))));
		final ISchemaReference si = payload.getSchemaReference();
		assertNotNull(si);
		assertEquals(plSchemLoc, si.getLocation());
		assertEquals(plSchemaVersion, si.getVersion());
		assertEquals(plSchemaNS, si.getNamespace());
	}
}
