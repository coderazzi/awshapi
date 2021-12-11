The openapi specification is extended on two parts:
- security: optionally, adding one or more authorizers.
- paths: extending each method with the associated authorizer and defining an endpoint.

To specify one authorizer:
- *security.name*: name of the authorizer
- *security.audiences*: comma separated list of audiences for this authorizer.
- *security.issuer*
- *security.type=oauth2*: only oauth2 allowed.
- *security.authorizer_type=jwt*: only jwt allowed.

To specify multiple authorizers, use as security.name a comma separated list, like for example:

    security.name=authorizer1, authorizer2

It is possible then to specify a different issuer or audience by defining:

- *security.audiences.SECURITY_NAME*=audiences
- *security.issuer.SECURITY_NAME*=issuer

To extend the methods, the syntax is:

- *method.METHOD=full_uri[,security_name,scopes]*

For example:

    method.user.post=http://3.64.241.104:12121/user/post

This will handle the method /user/post, using no security. 
Alternatively, security plus scopes can be specified:

    method.user.post=http://3.64.241.104:12121/user/post,authorizer1,user.email,user.id

A better option to handle methods is using tags associated to that method:

- *tag.TAG=uri[,security_name,scopes]*

In this case, the final URI uses the specified uri, plus the method. 
For example, if a method /user/post has a tag Frontend, and we define:

    tag.Frontend=http://3.64.241.105:12121

The endpoint would be: http://3.64.241.105:12121/user/post

