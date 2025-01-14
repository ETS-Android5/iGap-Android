/*
 * This is the source code of iGap for Android
 * It is licensed under GNU AGPL v3.0
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright © 2017 , iGap - www.iGap.net
 * iGap Messenger | Free, Fast and Secure instant messaging application
 * The idea of the Kianiranian Company - www.kianiranian.com
 * All rights reserved.
 */

package net.iGap.request;

import net.iGap.observers.interfaces.OnUserProfileSetBioResponse;
import net.iGap.proto.ProtoUserProfileBio;

public class RequestUserProfileSetBio {

    public void setBio(String bio, OnUserProfileSetBioResponse callback) {
        //RealmRegisteredInfo.updateBio(bio); // just in response update this value

        ProtoUserProfileBio.UserProfileSetBio.Builder builder = ProtoUserProfileBio.UserProfileSetBio.newBuilder();
        builder.setBio(bio);

        RequestWrapper requestWrapper = new RequestWrapper(147, builder,callback);
        try {
            RequestQueue.sendRequest(requestWrapper);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}