# Openapi 4 AWS 

* Website: https://coderazzi.net/openapi4aws
* Github: https://github.com/coderazzi/openapi4aws
* License: MIT license

This is utility to enrich an openapi integration with information specific for the AWS API Gateway.
It allows defining route integrations and authorizers to do automatic (re-)imports in API Gateway.

The openapi integration is extended on two parts:
- security: optionally, adding one or more authorizers.
- paths: extending each method with the associated authorizer and defining an endpoint.

The input to this utility is passed as parameters.

## Security / Authorizers

To specify an authorizer, use the following mandatory parameters:
- **authorizer.name**: name of the authorizer
- **authorizer.identity-source**: header containing the authorization, like: $request.header.Authorization
- **authorizer.audience**: comma separated list of audiences for this authorizer.
- **authorizer.issuer**

The following two parameters are currently optional:
- *authorizer.authorization-type=**oauth2***: currently, it can be only defined as "oauth2".
- *authorizer.authorizer-type=**jwt***: currently, it can be only defined as "jwt".

Multiple authorizers can be defined using a comma separated list in **authorizer.name**, i.e.:

    authorizer.name=authorizer1, authorizer2

It is possible then to specify a different parameter for each authorizer using the syntax:

- **authorizer.audience.*AUTHORIZER_NAME***=audience
- **authorizer.issuer.*AUTHORIZER_NAME***=issuer

## Paths / Integrations

To define routes, the syntax is:

- **path.*PATH***=full_uri[,authorizer_name,scopes]*

For example:

    path.user.post=http://3.64.241.104:12121/user/post

This will define an endpoint for the route /user/post, using no authorizers. 
Alternatively, authorizer plus scopes can be specified:

    path.user.post=http://3.64.241.104:12121/user/post,authorizer1,user.email,user.id

In this case, it uses the authorizer with name "authorizer1", with scopes "user.email" and "user.id"

## Paths / Integrations Using tags

A better option to define integrations is defining openapi tags associated for that path, 
and using then the following parameters:

- **tag.*TAG***=uri[,authorizer_name,scopes]*

For example, if a route */user/post* has an associated tag *Frontend*, and we define:

    tag.frontend=http://3.64.241.105:12121

This path will be extended to use the endpoint: `http://3.64.241.105:12121/user/post`

## Versions

- 1.0 : 12th December 2021.