package org.wso2.custom.user.operation.event.listener.unique.claim;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.core.AbstractIdentityUserOperationEventListener;
import org.wso2.carbon.identity.core.util.IdentityCoreConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.mgt.policy.PolicyViolationException;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreClientException;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.custom.user.operation.event.listener.unique.claim.internal.ServiceComponent;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * A userstore operation event listener to keep the uniqueness of a given set of claims.
 */
public class UniqueClaimUserOperationEventListener extends AbstractIdentityUserOperationEventListener {

    private static final Log log = LogFactory.getLog(UniqueClaimUserOperationEventListener.class);
    private static final String EVENT_LISTENER_TYPE = "org.wso2.carbon.user.core.listener.UserOperationEventListener";

    private static Properties properties;

    public void init() {
        properties = IdentityUtil.readEventListenerProperty(EVENT_LISTENER_TYPE, this.getClass().getName())
                .getProperties();
        if (properties == null || properties.isEmpty()) {
            log.info("No unique claims has been configured!");
        }
    }

    @Override
    public int getExecutionOrderId() {

        int orderId = getOrderId();
        if (orderId != IdentityCoreConstants.EVENT_LISTENER_ORDER_ID) {
            return orderId;
        }
        // This listener should be executed before all the other listeners.
        // 0 and 1 are already reserved for audit loggers, hence using 2.
        return 2;
    }

    public boolean isEnable() {
        if (properties == null || properties.size() == 0) {
            return false;
        }
        return super.isEnable();
    }

    @Override
    public boolean doPreAddUser(String userName, Object credential, String[] roleList, Map<String, String> claims,
                                String profile, UserStoreManager userStoreManager) throws UserStoreException {

        if (!isEnable()) {
            return true;
        }
        checkIfClaimDuplicated(userName, claims, profile, userStoreManager);
        return true;
    }


    @Override
    public boolean doPreSetUserClaimValue(String userName, String claimURI, String claimValue, String profile,
                                          UserStoreManager userStoreManager) throws UserStoreException {

        if (!isEnable()) {
            return true;
        }
        checkIfClaimDuplicated(userName, claimURI, claimValue, profile, userStoreManager);
        return true;
    }

    @Override
    public boolean doPreSetUserClaimValues(String userName, Map<String, String> claims, String profile,
                                           UserStoreManager userStoreManager) throws UserStoreException {

        if (!isEnable()) {
            return true;
        }
        checkIfClaimDuplicated(userName, claims, profile, userStoreManager);
        return true;
    }

    private void checkIfClaimDuplicated(String username, Map<String, String> claims, String profile,
            UserStoreManager userStoreManager)
        throws UserStoreException {

        for (Map.Entry<String, String> claim : claims.entrySet()) {
            checkIfClaimDuplicated(username, claim.getKey(), claim.getValue(), profile, userStoreManager);
        }
    }
    private void checkIfClaimDuplicated(String username, String claimUri, String claimValue, String profile,
                                        UserStoreManager userStoreManager) throws UserStoreException {

        if (StringUtils.isEmpty(claimValue) || properties == null || !properties.containsKey(claimUri)) {
            return;
        }

        String domainName = userStoreManager.getRealmConfiguration().getUserStoreProperty(
                UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        String usernameWithUserstoreDomain = UserCoreUtil.addDomainToName(username, domainName);

        // Get userstoreManager from realm since received one might be a secondary userstore
        UserStoreManager userStoreMgr = getUserstoreManager(userStoreManager.getTenantId());
        String[] userList = userStoreMgr.getUserList(claimUri, claimValue, profile);

        if (ArrayUtils.isEmpty(userList)) {
            return;
        } else if (userList.length == 1) {
            if (usernameWithUserstoreDomain.equalsIgnoreCase(userList[0])) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("A different user was found with the same claim(" + claimUri + ") value " + claimValue
                        + ": " + userList[0]);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Multiple users found with the same claim(" + claimUri + ") value " + claimValue + ": "
                        + Arrays.toString(userList));
            }
        }

        String configuredMessage = "The value defined for " + claimUri + " is already in use by a different user!";
        Object propertyValue = properties.get(claimUri);
        if (propertyValue != null) {
            configuredMessage = propertyValue.toString();
        }

        // PolicyViolationException is used to avoid returning the 500 status code for SCIM APIs
        throw new UserStoreException(new UserStoreClientException(configuredMessage, new PolicyViolationException(configuredMessage)));
    }

    private UserStoreManager getUserstoreManager(int tenantId) throws UserStoreException {

        UserRealm userRealm = null;
        try {
            userRealm = ServiceComponent.getRealmService().getTenantUserRealm(tenantId);
            if (userRealm != null) {
                return  (UserStoreManager) userRealm.getUserStoreManager();
            }
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }
        throw new UserStoreException("User real is null for the tenant " + tenantId + ".");
    }
}
