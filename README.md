[![Docker Build Status](https://img.shields.io/docker/build/mroerni/jebediah.svg)](https://hub.docker.com/r/mroerni/jebediah/)
# Jebediah

Jebediah is a virtual assistant frontend for [D-BAS].

The assistant is based on Googles [Dialogflow][] for natural language processing and user intent recognition.



## Services


| Service | Description |
| ------- | ----------- |
| [jebediah][] | The logic behind the assistant |
| [eauth][] | Links 3rd-party user accounts to D-BAS accounts |  
| [fb-hook][] | Connect a facebook page with dialogflow, used for authentication. | 


[jebediah]: https://github.com/hhucn/jebediah/
[eauth]: https://github.com/hhucn/dbas-eauth/
[fb-hook]: https://github.com/hhucn/dbas-fb-hook/

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server
    
## Build

To build a ``.jar`` file for this service, run:

    lein ring uberjar
    
or just use docker!  

    docker build .

## License

Copyright © 2018 Björn Ebbinghaus

[D-BAS]: https://dbas.cs.uni-duesseldorf.de/
[Dialogflow]: https://dialogflow.com/
