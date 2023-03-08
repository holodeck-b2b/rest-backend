# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## 2.0.0
##### 2023-03-08 
### Added
* HTTP Header _X-HolodeckB2B-Payload-URI_ to specify the URI to reference the payload as an alternative to using the 
 standard _Content-Id_ header

### Changed
* Based on Holodeck B2B 6.0.0

## 1.2.0
##### 2022-09-21
### Added
* Support for setting the value of the Content-Id of the attachment that contains the payload

## 1.1.1
##### 2022-01-31
### Changed
* Added _Content-Length_ http header in Notification and Delivery without payloads requests for better interoperability 

### Fixed
* Issue on delivery of messages using a messaging protocol that has no support for Collaboration Info meta-data [#9](https://github.com/holodeck-b2b/rest-backend/issues/9)

## 1.1.0
##### 2021-12-14
### Changed
* Refactored code to use the generic utility classes from https://github.com/holodeck-b2b/generic-utils
* Improved documentation on PartyId format used in API
* Update log4j API dependency to version 2.15.0 to indicate this version must be used (to mitigate CVE-2021-44228

## 1.0.0
##### 2020-09-03

### Added
* Initial release.
