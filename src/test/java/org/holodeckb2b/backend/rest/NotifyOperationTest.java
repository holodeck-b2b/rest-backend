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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.holodeckb2b.backend.rest.testhelpers.BackendMock;
import org.holodeckb2b.common.messagemodel.EbmsError;
import org.holodeckb2b.common.messagemodel.ErrorMessage;
import org.holodeckb2b.common.messagemodel.Receipt;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError.Severity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NotifyOperationTest {

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

	@Test
	void testNotifyReceipt() throws Exception {
		final Receipt receipt = new Receipt();
		
		receipt.setPModeId("pm-test-notify");
		receipt.setMessageId(UUID.randomUUID().toString());
		receipt.setTimestamp(new Date());
		receipt.setRefToMessageId(UUID.randomUUID().toString());
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/accept");		
		try {
			dm.init(settings);
			dm.createMessageDeliverer().deliver(receipt);			
		} catch (MessageDeliveryException e) {
			e.printStackTrace();
			fail();
		}
		
		assertTrue(backend.getRequestURL().getPath().endsWith("/notify/receipt"));
		
		HTTPHeaders headers = backend.getRcvdHeaders();		
		assertNotNull(headers);
		assertEquals(receipt.getPModeId(), headers.getHeader(HTTPHeaders.PMODE_ID));
		assertEquals(receipt.getMessageId(), headers.getHeader(HTTPHeaders.MESSAGE_ID));
		assertEquals(receipt.getTimestamp(), Utils.fromXMLDateTime(headers.getHeader(HTTPHeaders.TIMESTAMP)));
		assertEquals(receipt.getRefToMessageId(), headers.getHeader(HTTPHeaders.REF_TO_MESSAGE_ID));		
	}		
	
	@Test
	void testNotifyError() throws Exception {
		final ErrorMessage errMsg = new ErrorMessage();
		
		errMsg.setPModeId("pm-test-notify");
		errMsg.setMessageId(UUID.randomUUID().toString());
		errMsg.setTimestamp(new Date());
		errMsg.setRefToMessageId(UUID.randomUUID().toString());
		
		final EbmsError e1 = new EbmsError();
		e1.setErrorCode("TST:0001");
		e1.setSeverity(Severity.failure);
		e1.setErrorDetail("Not so good");
		errMsg.addError(e1);
		final EbmsError e2 = new EbmsError();
		e2.setErrorCode("TST:0002");
		e2.setSeverity(Severity.warning);
		errMsg.addError(e2);		
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/accept");		
		try {
			dm.init(settings);
			dm.createMessageDeliverer().deliver(errMsg);			
		} catch (MessageDeliveryException e) {
			e.printStackTrace();
			fail();
		}
		
		assertTrue(backend.getRequestURL().getPath().endsWith("/notify/error"));
		
		HTTPHeaders headers = backend.getRcvdHeaders();		
		assertNotNull(headers);
		assertEquals(errMsg.getPModeId(), headers.getHeader(HTTPHeaders.PMODE_ID));
		assertEquals(errMsg.getMessageId(), headers.getHeader(HTTPHeaders.MESSAGE_ID));
		assertEquals(errMsg.getTimestamp(), Utils.fromXMLDateTime(headers.getHeader(HTTPHeaders.TIMESTAMP)));
		assertEquals(errMsg.getRefToMessageId(), headers.getHeader(HTTPHeaders.REF_TO_MESSAGE_ID));
		
		assertFalse(Utils.isNullOrEmpty(headers.getHeader(HTTPHeaders.ERROR_MESSAGE)));		
	}		
	
	@Test
	void testRejectNoRef() throws Exception {
		final ErrorMessage errMsg = new ErrorMessage();
		
		errMsg.setPModeId("pm-test-notify");
		errMsg.setMessageId(UUID.randomUUID().toString());
		errMsg.setTimestamp(new Date());
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/accept");		
		try {
			dm.init(settings);
			dm.createMessageDeliverer().deliver(errMsg);
			fail();
		} catch (MessageDeliveryException e) {
		}		
	}	
	
	@Test
	void testNotifyRejection() throws Exception {
		final Receipt receipt = new Receipt();
		
		receipt.setPModeId("pm-test-notify");
		receipt.setMessageId(UUID.randomUUID().toString());
		receipt.setTimestamp(new Date());
		receipt.setRefToMessageId(UUID.randomUUID().toString());
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/reject");		
		try {
			dm.init(settings);
			dm.createMessageDeliverer().deliver(receipt);
			fail();
		} catch (MessageDeliveryException e) {
		}		
		assertTrue(backend.getRequestURL().getPath().endsWith("/notify/receipt"));		
	}	
	
	@Test
	void testNotifyTimeout() throws Exception {
		final Receipt receipt = new Receipt();
		
		receipt.setPModeId("pm-test-notify");
		receipt.setMessageId(UUID.randomUUID().toString());
		receipt.setTimestamp(new Date());
		receipt.setRefToMessageId(UUID.randomUUID().toString());
		
		NotifyAndDeliverOperation dm = new NotifyAndDeliverOperation();
		Map<String, String> settings = new HashMap<>();
		settings.put(NotifyAndDeliverOperation.P_BACKEND_URL, "http://localhost:" + backend.getPort() + "/timeout");		
		settings.put(NotifyAndDeliverOperation.P_TIMEOUT, "800");		
		try {
			dm.init(settings);
			dm.createMessageDeliverer().deliver(receipt);
			fail();
		} catch (MessageDeliveryException e) {
		}		
		assertTrue(backend.getRequestURL().getPath().endsWith("/notify/receipt"));		
	}	
	
}
