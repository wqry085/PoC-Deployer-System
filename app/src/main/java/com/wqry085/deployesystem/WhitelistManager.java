package com.wqry085.deployesystem;

import java.util.Set;


public class WhitelistManager {

    
    public static Set<Integer> getWhitelistedUids() {
        return PolicyClient.getWhitelistedUids();
    }

    
    public static void getWhitelistedUidsAsync(PolicyClient.UidSetCallback callback) {
        PolicyClient.getWhitelistedUidsAsync(callback);
    }

    
    public static boolean addUidToWhitelist(int uid) {
        boolean result = PolicyClient.addUid(uid);
        if (result) {
            
            PolicyClient.save();
        }
        return result;
    }

    
    public static void addUidToWhitelistAsync(int uid, PolicyClient.BooleanCallback callback) {
        PolicyClient.addUidAsync(uid, success -> {
            if (success) {
                PolicyClient.saveAsync(saved -> {
                    if (callback != null) callback.onResult(success);
                });
            } else {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    
    public static boolean removeUidFromWhitelist(int uid) {
        boolean result = PolicyClient.removeUid(uid);
        if (result) {
            PolicyClient.save();
        }
        return result;
    }

    
    public static void removeUidFromWhitelistAsync(int uid, PolicyClient.BooleanCallback callback) {
        PolicyClient.removeUidAsync(uid, success -> {
            if (success) {
                PolicyClient.saveAsync(saved -> {
                    if (callback != null) callback.onResult(success);
                });
            } else {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    
    public static boolean isUidWhitelisted(int uid) {
        return PolicyClient.checkUid(uid);
    }

    
    public static boolean isDaemonRunning() {
        return PolicyClient.isAlive();
    }
}