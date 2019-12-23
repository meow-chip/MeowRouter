MeowRouter
=======================

> Routing for cats

MeowRouter is a hardware accelerated IP packet forwarder running on programmable ICs. MeowRouter utilizes the Cuckoo hashing algorithm, and supports external memory accesses through AXI interface, so it is capable of handling large amount of network traffic / pps with huge routing tables.

This repository contains the RTL source code for the data plane, and is intended to be used alongside an CPU. See [MeowRouter-top](https://github.com/meow-chip/MeowRouter-top) for an example.

## Authors

See `AUTHORS` file

## Directory Structure

MeowRouter's directory structure follows the Scala convention, which is:
- `src/main/scala` contains all the source code
- `src/main/test` contains all the unit tests

`src/main/scala/router.scala` contains the source for the main router module
Source code is divided into the following components:
- `acceptor`: Packet receptor + parser
- `data`: Shared data definations
- `arp`: ARP cache/matcher
- `forward`: Forwarding table
- `encoder`: Packet serializer
- `transmitter`: Packet transmitter
- `adapter`: CPU rx/tx buf

## License
All code under this repository is released under the MIT license. See `LICENSE` file.
