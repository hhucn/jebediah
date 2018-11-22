# Jebediah - A natural language frontend for D-BAS

## Introduction
Jebediah is a natural frontend solution for D-BAS [[2]](dbas), to integrate the dialog-based argumentation flow into text based chats or even voice agents. 


## System
The prototype implementation for the Facebook Messanger relies on several components. 
They are listed in the way a message is processed from and back to the user. 

Firstly the user interacts with the chat of a Facebook page. The administrator of a Facebook page can add Facebook applications (https://developers.facebook.com/apps) to their site, allowing to access most of the functions of the page for analysis and/or control (automatic posts, polling of interaction data, messaging...). 
Jebediah has such an application. It has the messenger product activated to receive messages from users. 
These messages are anonymous, in the sense that no personal information, like the name or e-mail address is visible. 
They are hidden behind an application and page unique id, which can be used later to fetch said private informations, if Facebook and the user accepts to grant you the authorization.
Furthermore the webhook for the facebook application has to be enabled, to which the facebook messages are send. 
As the webhook is called for multiple events (new message and account linking), we have to route these messages to their appropriate destinations. 

### fb-hook
This task is fullfilled by [fb-hook] a simple web-service to accept a batch of  webhook calls and forward the messages one by one (Batch forwarding should be implemented for a large scale implentation). 
If the received event is a message from a user, it is directed directly to dialogflow to be further processed.
If it is an account linking message, the event is forwared to the [eauth] service.

### eauth
The eauth service is for mapping and resolving a user from an external platform (in this case facebook) to a D-BAS user. 

It is used for account linking between an *existing* D-BAS account and a facebook user, and therefore provides a page where the linking user can log into D-BAS.
The check is made via the D-BAS API (`/login`).
After a successfull check the service gets the unique id from facebook and stores it in relation to the D-BAS nickname. 

To not interrupt the users discussion, but still enabling him to add new content to D-BAS without creating and linking a D-BAS account in advance, Jebediah creates a new D-BAS account for the user if there is no existing linkage.
For this API endpoint exists to add arbitrary relations, which can be queried later.

### Dialogflow
Jebediah uses Googles [Dialogflow] to understand the intent of the user and to extract the data, which is used for the actual dialog with the D-BAS backend.
Dialogflow has a number of example sentences, which a user can say to trigger certain actions. 
The number of sample sentences for a given intent can grow with use, improving the capabilities of the system to interpret messages from the user correctly. 

Dialogflow receives the messages from the fb-hook service and sends them directly back to facebook.
It is possible to eliminate the fb-hook service, if account-linking is not needed, as dialogflow can be used as the Facebook webhook target. 
This decreases the latency between the requesting message and its answer.

Some requests from the user can be answered by Dialogflow itself, like "How can you help me" or "I want to log into D-BAS". 
Dialogflow can even ask the user to add missing information to the last request made, for example if the user didn't add a reason to an opinion.

### Jebediah - Backend
When a request from the user is correctly interpreted by Dialogflow, the jebediah backend will be called.
This backend is the connection point between a D-BAS setup and the services mentioned above. 
It pulls together all relevant information to respond to a request, based on the data in the eauth system and D-BAS itself, forming an natural speech answer.
It tries to match a natural statement with an existing one (a task which is very hard and can be improved a lot), and adds this statement to D-BAS's knowledge base if it is a new one.

It queries eauth for the correct D-BAS account, given an external identification (id, e-mail, ...). 
In the case of no matching user a D-BAS account is created, but only if it is necessary, after all, you have to be logged in to add new content to D-BAS. 
The auto-generated username is added to eauth to recognize the user in the future.

## Problems and possible improvements
1. #### Using the provided integration
    For fast prototyping we used the Facebook integration provided by Dialogflow to communicate between facebook and Dialogflow. While this is a fast and simple solutions it definitely has some drawbacks.
    
    account-linking messages send to Dialogflow are lost, resulting in the need in a proxy (fb-hook) between the sender and Dialogflow. 
        
    Dialogflow pushes the answer to Facebook via the Facebook API, not allowing to control what is really getting to the user. If for example the Jebediah backend, as the webhook for dialogflow, times out after six seconds, returns a faulty answer or , Dialogflow doesn't send any answer to the user, leaving him wondering and possibly annoyed. 

    This could be fixed by sending all Facebook events to Jebediah, letting it query for the answer. This enables us to give the user a precise hint on what is going on. On the other hand this may increase the response time, as the result is not send to Facebook directly anymore.

2. #### Matching the correct statement
    Comparing two statements by their meaning is inherently hard, as their meaning could depend on the context in which they were made. 
    We tried different methods, like various indexing methods built into Elasticsearch to approach this problem, but none of them were accurate enough to be practical in the scope of this work. 

    We decided to dodge this problem as best as we could. Facebook allows not only to respond with plain text but with so called *rich messages*, which can contain hints on what the user may say or even lists of statements like they are found in D-BAS. 
    This was the point were we had to row back from the *all natural* approach back to a more *machine friendly* solution. Because of this we solely concentrated on facebook with text based messaging, as reading lists of statements are possible, but not practical with voice based agents. 

3. #### Spreading too thin
    The use of multiple services which could be updated and run separately was not a bad idea in general but resulted in some problems. For one they increased the round trip time for a message, which can be critical to the acceptance of the agent itself. 
    Additionally storing data in eauth should be safer in the future.

    In its current form there is no synchronization between eauth and D-BAS, resulting in possible outdated data in the eauth storage.
    This resulted in an embarrassing incident in the mids of a live demo, were the demo instance of D-BAS was wiped clean before, while the eauth service still had old, invalid usernames in its store. 
    
    The fb-hook and the eauth service could be integrated directly into the jebediah backend, reducing latency and security by reducing calls over http. 
    The fusion of fb-hook and the jeb backend would be necessary anyway, if we don't want to use the provided Facebook integration from Dialogflow.


## References
1. <a href=jeb></a>_METER, Christian; EBBINGHAUS, Björn; MAUVE, Martin. Jebediah–Arguing with a Social Bot. Computational Models of Argument: Proceedings of COMMA 2018, 2018, 305. Jg., S. 467._
2. <a href=dbas></a>_KRAUTHOFF, Tobias, et al. D-BAS-A Dialog-Based Online Argumentation System. Computational Models of Argument: Proceedings of COMMA 2018, 2018, 305. Jg., S. 325._

[fb-hook]: https://github.com/hhucn/dbas-fb-hook
[eauth]: https://github.com/hhucn/dbas-eauth
[Dialogflow]: https://dialogflow.com