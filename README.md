# Computer Networking Project 

### File Consists of Client and Server Side (written in Java) ###

## Outline of Client File ##

* The provided Java code includes two classes: Client and Server, which models a network communication scenario.

* The Client class handles client-side operations, including initializing the connection, sending an initial message to the server, and subsequently sending data segments.

* With that in mind, it implements a sliding window protocol, adjusting window size based on received acknowledgments (ACKs) and calculating good-put.

## Outline of Server File ##

* The Server class manages the server-side communication, waiting for a connection setup message from the client, then receiving and processing data segments.

* It also utilizes a sliding window protocol, sending ACKs for correct segments and adjusting window size.

With that being said, these classes simulate reliable data transmission in the network environment. 
