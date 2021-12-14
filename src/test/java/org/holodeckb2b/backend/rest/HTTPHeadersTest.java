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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.holodeckb2b.common.messagemodel.EbmsError;
import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Property;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError.Severity;
import org.holodeckb2b.interfaces.submit.MessageSubmitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HTTPHeadersTest {

	@Test
	void testParsePartydId() {
				
		try {
			final String partyIdTxt = "SimplePartyId";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, partyIdTxt);
			IPartyId partyId = new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			assertNotNull(partyId);
			assertEquals(partyIdTxt, partyId.getId());
			assertNull(partyId.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		try {
			final String partyIdTxt = "urn:org:holodeckb2b:test:PartyId";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, partyIdTxt);
			IPartyId partyId = new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			assertNotNull(partyId);
			assertEquals(partyIdTxt, partyId.getId());
			assertNull(partyId.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		try {
			final String partyIdTxt = "TypedPartyId";
			final String partyIdType = "urn:org:holodeckb2b:test:pid:type";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, "[" + partyIdType + "]" + partyIdTxt);
			IPartyId partyId = new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			assertNotNull(partyId);
			assertEquals(partyIdTxt, partyId.getId());
			assertEquals(partyIdType, partyId.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		try {
			final String partyIdTxt = "UnTypedPartyId";
			final String partyIdType = "";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, "[" + partyIdType + "]" + partyIdTxt);
			IPartyId partyId = new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			assertNotNull(partyId);
			assertEquals(partyIdTxt, partyId.getId());
			assertNull(partyId.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		try {
			final String invalidPartyIdTxt = "[SimplePartyId";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, invalidPartyIdTxt);
			new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			fail();
		} catch (MessageSubmitException e) {			
		}
		
		try {
			final String invalidPartyIdTxt = "[urn:org:holodeckb2b:test:pid:type]";
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.SENDER_PARTY_ID, invalidPartyIdTxt);
			new HTTPHeaders(headers).getPartydId(HTTPHeaders.SENDER_PARTY_ID);
			fail();
		} catch (MessageSubmitException e) {			
		}
	}
	
	@ParameterizedTest
	@ValueSource(strings = { "=", " =", "= ", " = "})
	void testParseSingleProperty(String nvSeparator) {		
		final String pName = "p[1]";
		final String pValue = "value/urn:org:holodeckb2b:test:1";
		
		try {
			HashMap<String, String> headers = new HashMap<>();
			headers.put(HTTPHeaders.MESSAGE_PROPS, pName + nvSeparator + pValue);			
			Collection<IProperty> props = new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);
			assertFalse(Utils.isNullOrEmpty(props));
			assertTrue(props.size() == 1);
			IProperty p = props.iterator().next();
			assertEquals(pName, p.getName());
			assertEquals(pValue, p.getValue());
			assertNull(p.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		String pType = "urn:org:holodeckb2b:test:prop:type";		
		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, pName + nvSeparator + "[" + pType + "]" + pValue);			
			Collection<IProperty> props = new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);
			assertFalse(Utils.isNullOrEmpty(props));
			assertTrue(props.size() == 1);
			IProperty p = props.iterator().next();
			assertEquals(pName, p.getName());
			assertEquals(pValue, p.getValue());
			assertEquals(pType, p.getType());			
		} catch (MessageSubmitException e) {
			fail();
		}
		
		pType = "";
		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, pName + nvSeparator + "[" + pType + "]" + pValue);			
			Collection<IProperty> props = new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);
			assertFalse(Utils.isNullOrEmpty(props));
			assertTrue(props.size() == 1);
			IProperty p = props.iterator().next();
			assertEquals(pName, p.getName());
			assertEquals(pValue, p.getValue());
			assertNull(p.getType());
		} catch (MessageSubmitException e) {
			fail();
		}

		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, pName + nvSeparator + pValue + " ");			
			Collection<IProperty> props = new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);
			assertFalse(Utils.isNullOrEmpty(props));
			assertTrue(props.size() == 1);
			IProperty p = props.iterator().next();
			assertEquals(pName, p.getName());
			assertEquals(pValue, p.getValue());
			assertNull(p.getType());
		} catch (MessageSubmitException e) {
			fail();
		}
		
		pType = "urn:org:holodeckb2b:test:prop:type";
		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, pName + nvSeparator + "[" + pType + pValue);			
			new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);
			fail();
		} catch (MessageSubmitException e) {			
		}
	}
	
	@ParameterizedTest
	@ValueSource(strings = { ",", " ,", ", ", " , "})
	void testParseMultipleProperties(String propSep) {		
		final String[] names = { "p1", "p2", "p3" };
		final String[] values = { "v1", "v2", "v[3]" };
		final String[] types = { "t1", "", "urn:hb2b:types:2" };
		
		StringBuilder hdrVal = new StringBuilder();
		for(int i = 0; i < 3; i++) {
			hdrVal.append(names[i]).append('='); 
			if (!Utils.isNullOrEmpty(types[i]))
				hdrVal.append('[').append(types[i]).append(']');
			hdrVal.append(values[i]);
			if (i < 2)
				hdrVal.append(propSep);
		}
		
		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, hdrVal.toString());						
			Collection<IProperty> props = new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);		
			assertFalse(Utils.isNullOrEmpty(props));
			assertEquals(3, props.size());
			Iterator<IProperty> it = props.iterator();
			for(int i = 0; i < 3; i++) {
				IProperty p = it.next();
				assertEquals(names[i], p.getName());
				assertEquals(values[i], p.getValue());
				if (!Utils.isNullOrEmpty(types[i]))
					assertEquals(types[i], p.getType());
				else
					assertNull(p.getType());
			}
		} catch (MessageSubmitException e) {
			fail();
		}	

		try {
			HashMap<String, String> headers = new HashMap<>();			
			headers.put(HTTPHeaders.MESSAGE_PROPS, hdrVal.toString() + propSep);						
			new HTTPHeaders(headers).getProperties(HTTPHeaders.MESSAGE_PROPS);		
			fail();
		} catch (MessageSubmitException e) {
		}			
	}
	
	@Test
	void testSetPartyId() {
		HTTPHeaders headers = new HTTPHeaders();
		
		final PartyId noTypePid = new PartyId("urn:org:holodeckb2b:pid:Party1", null);
		headers.setPartyId(HTTPHeaders.SENDER_PARTY_ID, noTypePid);		
		String hdrVal = headers.getHeader(HTTPHeaders.SENDER_PARTY_ID);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals(noTypePid.getId(), hdrVal);
		
		final PartyId typedPid = new PartyId("Party2", "urn:org:holodeckb2b:pid");
		headers.setPartyId(HTTPHeaders.RECEIVER_PARTY_ID, typedPid);		
		hdrVal = headers.getHeader(HTTPHeaders.RECEIVER_PARTY_ID);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals("[" + typedPid.getType() + "]" + typedPid.getId(), hdrVal);		
	}
	
	@Test
	void testSetProperties() {
		HTTPHeaders headers = new HTTPHeaders();
		
		final Property p1 = new Property("p1", "val1");
		final Collection<IProperty> props = new ArrayList<>();
		props.add(p1);
		headers.setProperties(HTTPHeaders.MESSAGE_PROPS, props);
		String hdrVal = headers.getHeader(HTTPHeaders.MESSAGE_PROPS);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals(p1.getName() + "=" + p1.getValue(), hdrVal);
		
		final Property p2 = new Property("p2", "val2", "t1");
		props.add(p2);
		headers.setProperties(HTTPHeaders.MESSAGE_PROPS, props);
		hdrVal = headers.getHeader(HTTPHeaders.MESSAGE_PROPS);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals(p1.getName() + "=" + p1.getValue() + "," 
				+ p2.getName() + "=[" + p2.getType() + "]" + p2.getValue(), hdrVal);
		
	}

	@Test
	void testSetErrorMessage() {
		HTTPHeaders headers = new HTTPHeaders();
		Collection<IEbmsError> errors = new ArrayList<>();
		
		final EbmsError e1 = new EbmsError();
		e1.setErrorCode("TST:0001");
		e1.setSeverity(Severity.failure);
		e1.setErrorDetail("Not so good");
		errors.add(e1);
		
		headers.setErrorMessage(errors);
		
		String hdrVal = headers.getHeader(HTTPHeaders.ERROR_MESSAGE);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals("[" + e1.getSeverity().name() + "]" + e1.getErrorCode() + "-" + e1.getErrorDetail(), hdrVal);
		
		final EbmsError e2 = new EbmsError();
		e2.setErrorCode("TST:0002");
		e2.setSeverity(Severity.warning);
		errors.add(e2);
		
		headers.setErrorMessage(errors);
		
		hdrVal = headers.getHeader(HTTPHeaders.ERROR_MESSAGE);
		assertFalse(Utils.isNullOrEmpty(hdrVal));
		assertEquals("[" + e1.getSeverity().name() + "]" + e1.getErrorCode() + "-" + e1.getErrorDetail() + ","
					+ "[" + e2.getSeverity().name() + "]" + e2.getErrorCode(), hdrVal);		
	}	
}
