/*
 * This is the source code of iGap for Android
 * It is licensed under GNU AGPL v3.0
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright © 2017 , iGap - www.iGap.net
 * iGap Messenger | Free, Fast and Secure instant messaging application
 * The idea of the Kianiranian Company - www.kianiranian.com
 * All rights reserved.
 */

package net.iGap.response;

import net.iGap.G;
import net.iGap.proto.ProtoError;
import net.iGap.proto.ProtoGroupGetMemberList;
import net.iGap.realm.RealmMember;

public class GroupGetMemberListResponse extends MessageHandler {

    public int actionId;
    public Object message;
    public String identity;

    public GroupGetMemberListResponse(int actionId, Object protoClass, String identity) {
        super(actionId, protoClass, identity);

        this.message = protoClass;
        this.actionId = actionId;
        this.identity = identity;
    }

    @Override
    public void handler() {
        super.handler();
        ProtoGroupGetMemberList.GroupGetMemberListResponse.Builder builder = (ProtoGroupGetMemberList.GroupGetMemberListResponse.Builder) message;
        RealmMember.convertProtoMemberListToRealmMember(builder, identity);
    }

    @Override
    public void timeOut() {
        super.timeOut();
        G.onGroupGetMemberList.onTimeOut();
    }

    @Override
    public void error() {
        super.error();
        ProtoError.ErrorResponse.Builder errorResponse = (ProtoError.ErrorResponse.Builder) message;
        if (G.onGroupGetMemberList != null) {
            G.onGroupGetMemberList.onError(errorResponse.getMajorCode(), errorResponse.getMinorCode());
        }
    }
}


