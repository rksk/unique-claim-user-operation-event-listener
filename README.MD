# User Operation Event Listener to keep uniqueness of claims

This User Operation Event Listener to keep uniqueness for a given set of claims (eg: email, phone number) of users on [WSO2 Identity Server](https://wso2.com/identity-and-access-management/).

Note: This will be included OOTB in the upcoming WSO2 IS 5.12.0 release and also in the 5.10.0 & 5.11.0 version as an update (updates are available only for subscription customers of WSO2). More info on the OOTB feature can be found at [this blog](https://chathurangapriyadarshana.medium.com/manage-user-claims-uniqueness-on-the-wso2-identity-server-1be461934b7e).

 
### Steps to deploy the extension
- Build the component by running "mvn clean install"
- Copy following jar file which can be found in target directory into <IS_HOME>/repository/components/dropins/
    - unique-claim-user-operation-event-listener-1.0.0.jar
- Configure the User Operation Event Listener by adding following lines into deployment.toml file.
    ```
    [[event_listener]]
    id = "unique-claim-user-operation-event-listener"
    type = "org.wso2.carbon.user.core.listener.UserOperationEventListener"
    name = "org.wso2.custom.user.operation.event.listener.unique.claim.UniqueClaimUserOperationEventListener"
    order = 2
    enable = true
    properties."http://wso2.org/claims/emailaddress" = "The specified email address is already in use by a different user!"
    properties."http://wso2.org/claims/mobile" = "The specified mobile number is already in use by a different user!"
    properties."http://wso2.org/claims/username" = "The username is already in use by a different user!"

    ```
  - The last two lines of above config block defines which claims you want to make unique. You can add any number of claims in the following format.
      ```
      properties."<local-claim-uri>" = "<the message shown to the user when a duplicate is found>"
      ```
- Restart WSO2 IS.

### Testing
- Login to management console and try to update/create two users with the same email address. You will get the error message defined in the configuration.
- Try to patch/create a user via SCIM with an email used by another user.

```
curl 'https://localhost:9443/scim2/Me' \
>   -X 'PATCH' \
>   -H 'Content-Type: application/json' \
>   -H 'Authorization: Bearer 71e0d4b5-3695-3edd-982a-ddd9c1905519' \
>   -d '{"Operations":[{"op":"replace","value":{"emails":["admin@wso2.com"]}}],"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"]}' \
>   -k
>

{"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"scimType":"invalidValue","detail":"The specified email address is already in use by a different user!","status":"400"}
```
