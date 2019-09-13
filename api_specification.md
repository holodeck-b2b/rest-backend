# Holodeck B2B REST back-end integration

## API Specification
The REST interface implemented by this extension uses the HTTP POST method to exchange the message meta-data and payload with the message meta-data contained in the HTTP headers and the payload [of a *User Message*] in the HTTP entity body. The following URL context paths are used for the operations:
* Submit : `http://«holodeckb2b-host»/holodeckb2b/restbackend/submit`
* Deliver : `http://«back-end host and base path»/deliver`
* Notify : `http://«back-end host and base path»/notify/receipt` for _Receipt Signal Messages_ and `http://«back-end host
and base path»/notify/error` for _Error Signal Messages_.

The table below shows the HTTP headers used to exchange the message meta-data. As the headers are, with exception of
 _Content-Type_, non standard they are prefixed with `X-HolodeckB2B-`.

| Header suffix  | Contains       | Used in operations |
| :------------- | :------------- | :----------------- |
| PModeId | The identifier of the P-Mode that governs the processing of the message       | All           |
| MessageId | The _MessageId_ of the message | All |
| Timestamp | The time stamp of the message | All |
| RefToMessageId | The _RefToMessageId_ contained in the message. | All |
| SenderId | The PartyId<sup>*</sup> of the Sender of the _User Message_. | Submit and Delivery |
| SenderRole | The Role of the Sender of the _User Message_. | Submit and Delivery |
| ReceiverId | The PartyId<sup>*</sup> of the Receiver of the _User Message_. | Submit and Delivery |
| ReceiverRole | The Role of the Receiver of the _User Message_. | Submit and Delivery |
| MessageProperties | Comma separated list of the <i>Message Properties</i> of the _User Message_. Each property is formatted as _`name`_`"=" [ "["`_`type`_`"]" ]`_`value`_ with the type part being optional. | Submit and Delivery |
| ConversationId | The ConversationId contained in the User Message. | Submit and Delivery |
| Service | The Service used by the _User Message_. Formatted as `[ "["`_`type`_`"]"  ] `_`name`_ | Submit and Delivery |
| Action | The Action used by the _User Message_. | Submit and Delivery |
| Content-Type | The MIME Type of the payload. NOTE that this header is a standard HTTP header and therefore not prefixed! | Submit and Delivery |
| Containment | Indicates how a XML payload should be included in the _User Message_. Value can be either "ATTACHMENT" or "BODY". | Submit |
| PayloadProperties | Comma separated list of the payload specific properties. Same formatting as for the _Message Properties_ | Submit and Delivery |
| SchemaNamespace | The name space URI of the schema that defines the content of the payload | Submit and Delivery |
| SchemaVersion | The version of the schema that defines the content of the payload | Submit and Delivery |
| SchemaLocation | The location of the schema that defines the content of the payload | Submit and Delivery |
| Errors | Includes a comma separated list describing the received ebMS Errors. Each property is formatted as `"[" `_`severity`_` "]" `_`error code`_` "-" `_`error details`_ | Notify of ebMS Error Messages |   

In the _Deliver_ and _Notify_ operations all applicable headers are provided by the extension, i.e. if the meta-data is not
available for the delivered/notified message the corresponding header is not included.
As message meta-data and P-Mode settings are combined to create a complete set of meta-data the required headers to
include in the _Submission_ is limited to the <i>PMode identifier</i>, while the rest depends on the P-Mode.  
**NOTE:** Although allowed by [HTTP 1.1 (RFC7230)](https://tools.ietf.org/html/rfc7230#section-3.2.2) this extension does not
support the use of multiple occurrences of a header for comma separated list values.

The HTTP status code in the range 2xx indicates that the operation was successful. All other codes should be interpreted as
failure. On _Submission_ the extension adds an additional HTTP header _X-Error_ to the response that includes a (short)
description why the submission was rejected.
