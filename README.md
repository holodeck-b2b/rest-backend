# Holodeck B2B REST back-end integration
An extension for Holodeck B2B that implements the _Submit_, _Notify_ and _Deliver_ operations using a REST API. For simplicity and as it is a very common use-case this implementation only supports _User Messages_ with a single payload.

__________________
For more information on using Holodeck B2B visit http://holodeck-b2b.org  
Lead developer: Sander Fieten  
Code hosted at https://github.com/holodeck-b2b/rest-backend  
Issue tracker https://github.com/holodeck-b2b/rest-backend/issues  

## API Specification
The REST interface implemented by this extension uses the HTTP POST method to exchange the message meta-data and payload with the message meta-data contained in the HTTP headers and the payload [of a *User Message*] in the HTTP entity body. The following URL context paths are used for the operations:
* Submit : `http://«holodeckb2b-host»/holodeckb2b/restbackend/submit`
* Delivery : `http://«back-end host and base path»/deliver`
* Notify : `http://«back-end host and base path»/notify/receipt` for _Receipt Signal Messages_ and `http://«back-end host
and base path»/notify/error` for _Error Signal Messages_.

The table below shows the HTTP headers used to exchange the message meta-data. As the headers are, with exception of
 _Content-Type_, non standard they are prefixed with {{X-HolodeckB2B-}}.

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
| MessageProperties | Comma separated list of the <i>Message Properties</i> of _the User Message_. Each property is formatted as _`name`_`+ "=" + "[" +`_`type`_` + "]" + `_`value`_ with the type part being optional. | Submit and Delivery |
| ConversationId | The ConversationId contained in the User Message. | Submit and Delivery |
| Service | The Service used by the _User Message_. Formatted as ``"[" +`_`type`_` + "]" + `_`name`_ | Submit and Delivery |
| Action | The Action used by the _User Message_. | Submit and Delivery |
| Content-Type | The MIME Type of the payload. NOTE that this header is a standard HTTP header and therefore not prefixed! | Submit and Delivery |
| Containment | Indicates how a XML payload should be included in the _User Message_. Value can be either "ATTACHMENT" or "BODY". | Submit |
| PayloadProperties | Comma separated list of the payload specific properties. Same formatting as for the _Message Properties_ | Submit and Delivery |
| SchemaNamespace | The name space URI of the schema that defines the content of the payload | Submit and Delivery |
| SchemaVersion | The version of the schema that defines the content of the payload | Submit and Delivery |
| SchemaLocation | The location of the schema that defines the content of the payload | Submit and Delivery |
| Errors | Includes a comma separated list describing the received ebMS Errors. Each property is formatted as `"[" +`_`severity`_` + "]" + `_`error code`_` + "-" + `_`error details`_ | Notify of ebMS Error Messages |   

In the _Deliver_ and _Notify_ operations all applicable headers are provided by the extension, i.e. if the meta-data is not
available for the delivered/notified message the corresponding header is not included.
As message meta-data and P-Mode settings are combined to create a complete set of meta-data the required headers to
include in the _Submission_ is limited to the <i>PMode identifier</i>, while the rest depends on the P-Mode.  
**NOTE:** Although allowed by [HTTP 1.1 (RFC7230)](https://tools.ietf.org/html/rfc7230#section-3.2.2) this extension does not
support the use of multiple occurrences of a header for comma separated list values.

The HTTP status code in the range 2xx indicates that the operation was successful. All other codes should be interpreted as
failure. On _Submission_ the extension adds an additional HTTP header _X-Error_ to the response that includes a (short)
description why the submission was rejected.

## Installation
### Prerequisites  
This extension requires that you have already deployed Holodeck B2B version 4.2.0 or later and the [*"rest-no-error-content"* Axis2 module](https://github.com/holodeck-b2b/axis2-rest-status-only-error).

### Installation and Configuration
First step is to build the extension or download the latest release package. You should now have the `rest-backend-«version».jar` and `hb2b-rest-backend.aar` files available. Copy the jar file to the `lib` directory of the Holodeck B2B instance and copy the aar file to the `repository/services` directory to enable the REST interface in the Holodeck B2B instance. Please note that you cannot activate the extension in a running Holodeck B2B instance and will need to restart the server to activate the REST extension.

No additional configuration is needed for the _Submit_ operation. The _Delivery_ and _Notify_ operations are configured in the
P-Modes by setting the _delivery method_. To use this REST back-end integration set the applicable `DeliveryMethod` element to `org.holodeckb2b.backend.rest.NotifyAndDeliverOperation` and configure its parameters:
1. _URL_ : the URL where the REST service is hosted by the back-end application. As explained above "/deliver" will be added
 to this URL when delivering _User Messages_ and for "/notify/receipt" and "/notify/error" for notification of _Receipt_ respectively _Error_ Signals.
2. _TIMEOUT_ : the time (in milliseconds) the delivery method should wait for the back-end system to accept the delivery and notification. This parameter is optional and when not specified a default timeout of 10 seconds will be used.

## Contributing
We are using the simplified Github workflow to accept modifications which means you should:
* create an issue related to the problem you want to fix or the function you want to add (good for traceability and cross-reference)
* fork the repository
* create a branch (optionally with the reference to the issue in the name)
* write your code
* commit incrementally with readable and detailed commit messages
* submit a pull-request against the master branch of this repository

If your contribution is more than a patch, please contact us beforehand to discuss which branch you can best submit the pull request to.

### Submitting bugs
You can report issues directly on the [project Issue Tracker](https://github.com/holodeck-b2b/rest-backend/issues).  
Please document the steps to reproduce your problem in as much detail as you can (if needed and possible include screenshots).

## Versioning
Version numbering follows the [Semantic versioning](http://semver.org/) approach.

## License
This module is licensed under the General Public License V3 (GPLv3) which is included in the LICENSE in the root of the project.  
**NOTE** that this license only applies to the source code contained in this project and **does not apply to** the implementation of the REST API by **the backend system**.

## Support
Commercial Holodeck B2B support is provided by Chasquis. Visit [Chasquis-Consulting.com](http://chasquis-consulting.com/holodeck-b2b-support/) for more information.
