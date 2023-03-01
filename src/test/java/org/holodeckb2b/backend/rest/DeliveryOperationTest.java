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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.holodeckb2b.backend.rest.testhelpers.BackendMock;
import org.holodeckb2b.common.messagemodel.CollaborationInfo;
import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Payload;
import org.holodeckb2b.common.messagemodel.Property;
import org.holodeckb2b.common.messagemodel.SchemaReference;
import org.holodeckb2b.common.messagemodel.Service;
import org.holodeckb2b.common.messagemodel.TradingPartner;
import org.holodeckb2b.common.messagemodel.UserMessage;
import org.holodeckb2b.common.util.CompareUtils;
import org.holodeckb2b.commons.testing.TestUtils;
import org.holodeckb2b.commons.util.FileUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.general.IProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryOperationTest {

	private static BackendMock backend;
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		backend = new BackendMock(1000);
		backend.start();
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
		backend.stop();
	}

	@ParameterizedTest
	@ValueSource(strings = { "test.xml", "logo.png", "random.bin" })
	void testDeliveryOkay(String payloadFile) throws Exception {
		final String plData = TestUtils.getTestResource("payloads/").resolve(payloadFile).toString();
		String mimeType;
		try {
			mimeType = FileUtils.detectMimeType(new File(plData));
		} catch (IOException e1) {
			mimeType = "application/octet-stream";			
		}
		
		final UserMessage userMsg = new UserMessage();
		
		userMsg.setPModeId("pm-test-delivery");
		userMsg.setMessageId(UUID.randomUUID().toString());
		userMsg.setTimestamp(new Date());
		userMsg.setRefToMessageId(UUID.randomUUID().toString());
		
		final TradingPartner sender = new TradingPartner();
		sender.addPartyId(new PartyId("senderId", "urn:org:holodeckb2b:test:partyids"));
		sender.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
		userMsg.setSender(sender);

		final TradingPartner receiver = new TradingPartner();
		receiver.addPartyId(new PartyId("urn:org:holodeckb2b:test:partyids:receiverId", null));
		receiver.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");
		userMsg.setReceiver(receiver);
		
		userMsg.setMessageProperties(Arrays.asList(new Property[] { new Property("mp1", "val1"),
															    new Property("mp2", "val2", "t1")																				
															  }));
		
		CollaborationInfo cInfo = new CollaborationInfo();		
		cInfo.setConversationId("pm-test-conversation");
		cInfo.setService(new Service("Test", "urn:org:holodeckb2b:test"));
		cInfo.setAction("SendMessage");
		userMsg.setCollaborationInfo(cInfo);

		Payload payload = new Payload();
		payload.setContentLocation(plData);
		payload.setMimeType(mimeType);		
		payload.setProperties(Arrays.asList(new Property[] { new Property("pp1", "val1", "t1"),
														     new Property("mp2", "val2")
		   												   }));
		
		SchemaReference schemaInfo = new SchemaReference("http://test.holodeck-b2b.org/test",
														 "http://test.holodeck-b2b.org/test.xsd");
		schemaInfo.setVersion("1.0");
		payload.setSchemaReference(schemaInfo);
		userMsg.addPayload(payload);
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/accept");		
		try {
			dm.init(settings);
			dm.deliver(userMsg);			
		} catch (MessageDeliveryException e) {
			e.printStackTrace();
			fail();
		}
		
		assertTrue(backend.getRequestURL().getPath().endsWith("/deliver"));
		
		HTTPHeaders headers = backend.getRcvdHeaders();		
		assertNotNull(headers);
		assertEquals(userMsg.getPModeId(), headers.getHeader(HTTPHeaders.PMODE_ID));
		assertEquals(userMsg.getMessageId(), headers.getHeader(HTTPHeaders.MESSAGE_ID));
		assertEquals(userMsg.getTimestamp(), Utils.fromXMLDateTime(headers.getHeader(HTTPHeaders.TIMESTAMP)));
		assertEquals(userMsg.getRefToMessageId(), headers.getHeader(HTTPHeaders.REF_TO_MESSAGE_ID));
		
		assertTrue(CompareUtils.areEqual(userMsg.getSender().getPartyIds(), 
										 Collections.singletonList(headers.getPartydId(HTTPHeaders.SENDER_PARTY_ID))));
		assertEquals(userMsg.getSender().getRole(), headers.getHeader(HTTPHeaders.SENDER_ROLE));
		assertTrue(CompareUtils.areEqual(userMsg.getReceiver().getPartyIds(), 
										 Collections.singletonList(headers.getPartydId(HTTPHeaders.RECEIVER_PARTY_ID))));
		assertEquals(userMsg.getReceiver().getRole(), headers.getHeader(HTTPHeaders.RECEIVER_ROLE));

		assertEquals(cInfo.getConversationId(), headers.getHeader(HTTPHeaders.CONVERSATION_ID));
		assertTrue(CompareUtils.areEqual(cInfo.getService(), headers.getService()));
		assertEquals(cInfo.getAction(), headers.getHeader(HTTPHeaders.ACTION));
				
		Collection<IProperty> msgProperties = headers.getProperties(HTTPHeaders.MESSAGE_PROPS);
		assertFalse(Utils.isNullOrEmpty(msgProperties));
		assertTrue(userMsg.getMessageProperties().stream().allMatch(o -> msgProperties.parallelStream()
														 				.anyMatch(r -> CompareUtils.areEqual(o, r))));
				
		assertEquals(payload.getMimeType(), headers.getHeader(HTTPHeaders.MIME_TYPE));
		Collection<IProperty> plProperties = headers.getProperties(HTTPHeaders.PART_PROPS);
		assertFalse(Utils.isNullOrEmpty(plProperties));
		assertTrue(payload.getProperties().stream().allMatch(o -> plProperties.parallelStream()
														 				.anyMatch(r -> CompareUtils.areEqual(o, r))));
		
		assertEquals(schemaInfo.getLocation(), headers.getHeader(HTTPHeaders.SCHEMA_LOCATION));
		assertEquals(schemaInfo.getVersion(), headers.getHeader(HTTPHeaders.SCHEMA_VERSION));
		assertEquals(schemaInfo.getNamespace(), headers.getHeader(HTTPHeaders.SCHEMA_NS));
		
		boolean equal = false;
		int l = 0;
		try (FileInputStream org = new FileInputStream(plData); 
			 ByteArrayInputStream sav = new ByteArrayInputStream(backend.getRcvdData())) {
			int o, s;
			do {
				o = org.read(); s = sav.read(); l += (s >= 0) ? 1 : 0;
				equal = o == s;
			} while (equal && o >= 0 && s >= 0);
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
		assertTrue(equal);	
		assertEquals(l, Integer.parseInt(headers.getHeader("content-length")));
	}
	
	
	@Test
	void testNoPayload() throws Exception {
		final UserMessage userMsg = new UserMessage();
		
		userMsg.setPModeId("pm-test-delivery");
		userMsg.setMessageId(UUID.randomUUID().toString());
		userMsg.setTimestamp(new Date());
		
		final TradingPartner sender = new TradingPartner();
		sender.addPartyId(new PartyId("senderId", "urn:org:holodeckb2b:test:partyids"));
		sender.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
		userMsg.setSender(sender);
		
		final TradingPartner receiver = new TradingPartner();
		receiver.addPartyId(new PartyId("urn:org:holodeckb2b:test:partyids:receiverId", null));
		receiver.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");
		userMsg.setReceiver(receiver);
		
		CollaborationInfo cInfo = new CollaborationInfo();		
		cInfo.setService(new Service("Test", "urn:org:holodeckb2b:test"));
		cInfo.setAction("SendMessage");
		userMsg.setCollaborationInfo(cInfo);
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/accept");		
		try {
			dm.init(settings);
			dm.deliver(userMsg);			
		} catch (MessageDeliveryException e) {
			e.printStackTrace();
			fail();
		}
		
		HTTPHeaders headers = backend.getRcvdHeaders();
		
		assertNotNull(headers);
		assertEquals(userMsg.getPModeId(), headers.getHeader(HTTPHeaders.PMODE_ID));
		assertEquals(userMsg.getMessageId(), headers.getHeader(HTTPHeaders.MESSAGE_ID));
		assertEquals(userMsg.getTimestamp(), Utils.fromXMLDateTime(headers.getHeader(HTTPHeaders.TIMESTAMP)));
		assertEquals(userMsg.getRefToMessageId(), headers.getHeader(HTTPHeaders.REF_TO_MESSAGE_ID));
		
		assertTrue(CompareUtils.areEqual(userMsg.getSender().getPartyIds(), 
										 Collections.singletonList(headers.getPartydId(HTTPHeaders.SENDER_PARTY_ID))));
		assertEquals(userMsg.getSender().getRole(), headers.getHeader(HTTPHeaders.SENDER_ROLE));
		assertTrue(CompareUtils.areEqual(userMsg.getReceiver().getPartyIds(), 
										 Collections.singletonList(headers.getPartydId(HTTPHeaders.RECEIVER_PARTY_ID))));
		assertEquals(userMsg.getReceiver().getRole(), headers.getHeader(HTTPHeaders.RECEIVER_ROLE));

		assertNull(headers.getHeader(HTTPHeaders.CONVERSATION_ID));
		assertTrue(CompareUtils.areEqual(cInfo.getService(), headers.getService()));
		assertEquals(cInfo.getAction(), headers.getHeader(HTTPHeaders.ACTION));
				
		Collection<IProperty> msgProperties = headers.getProperties(HTTPHeaders.MESSAGE_PROPS);
		assertTrue(Utils.isNullOrEmpty(msgProperties));
		
		assertNull(backend.getRcvdData());
		assertTrue(Utils.isNullOrEmpty(headers.getHeader(HTTPHeaders.MIME_TYPE)));
		Collection<IProperty> plProperties = headers.getProperties(HTTPHeaders.PART_PROPS);
		assertTrue(Utils.isNullOrEmpty(plProperties));
		
		assertNull(headers.getHeader(HTTPHeaders.SCHEMA_LOCATION));
		assertNull(headers.getHeader(HTTPHeaders.SCHEMA_VERSION));
		assertNull(headers.getHeader(HTTPHeaders.SCHEMA_NS));			
	}
	
	@Test
	void testRejection() {
		final UserMessage userMsg = new UserMessage();
		
		userMsg.setPModeId("pm-test-delivery");
		userMsg.setMessageId(UUID.randomUUID().toString());
		userMsg.setTimestamp(new Date());
		userMsg.setRefToMessageId(UUID.randomUUID().toString());
		
		final TradingPartner sender = new TradingPartner();
		sender.addPartyId(new PartyId("senderId", "urn:org:holodeckb2b:test:partyids"));
		sender.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
		userMsg.setSender(sender);

		final TradingPartner receiver = new TradingPartner();
		receiver.addPartyId(new PartyId("urn:org:holodeckb2b:test:partyids:receiverId", null));
		receiver.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");
		userMsg.setReceiver(receiver);

		CollaborationInfo cInfo = new CollaborationInfo();		
		cInfo.setService(new Service("Test", "urn:org:holodeckb2b:test"));
		cInfo.setAction("SendMessage");
		userMsg.setCollaborationInfo(cInfo);

		Payload payload = new Payload();
		payload.setContentLocation(TestUtils.getTestResource("payloads/test.xml").toString());
		payload.setMimeType("text/xml");		
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/reject");		
		try {
			dm.init(settings);
			dm.deliver(userMsg);
			fail();
		} catch (MessageDeliveryException e) {			
		}
	}
	
	@Test
	void testTimeout() {
		final UserMessage userMsg = new UserMessage();
		
		userMsg.setPModeId("pm-test-delivery");
		userMsg.setMessageId(UUID.randomUUID().toString());
		userMsg.setTimestamp(new Date());
		userMsg.setRefToMessageId(UUID.randomUUID().toString());
		
		final TradingPartner sender = new TradingPartner();
		sender.addPartyId(new PartyId("senderId", "urn:org:holodeckb2b:test:partyids"));
		sender.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
		userMsg.setSender(sender);

		final TradingPartner receiver = new TradingPartner();
		receiver.addPartyId(new PartyId("urn:org:holodeckb2b:test:partyids:receiverId", null));
		receiver.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");
		userMsg.setReceiver(receiver);

		CollaborationInfo cInfo = new CollaborationInfo();		
		cInfo.setService(new Service("Test", "urn:org:holodeckb2b:test"));
		cInfo.setAction("SendMessage");
		userMsg.setCollaborationInfo(cInfo);

		Payload payload = new Payload();
		payload.setContentLocation(TestUtils.getTestResource("payloads/test.xml").toString());
		payload.setMimeType("text/xml");		
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/timeout");		
		settings.put(NotifyAndDeliverOperation.P_TIMEOUT, "800");		
		try {
			dm.init(settings);
			dm.deliver(userMsg);
			fail();
		} catch (MessageDeliveryException e) {			
		}
	}	
	
}
