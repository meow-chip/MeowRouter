MeowRouter
=======================

Routing for cats

## Directory Structure

MeowRouter's directory structure follows the Scala convention, which is:
- `src/main/scala` contains all the source code
- `src/main/test` contains all the unit tests

`src/main/scala/router.scala` contains the source for the main router module
Source code is divided into the following components:
- `acceptor`: Packet receptor + parser
- `data`: Shared data definations
- `arp`: ARP cache
- `forward`: Forwarding table
- `encoder`: Packet serializer
- `transmitter`: Packet transmitter