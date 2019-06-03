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
import net.iGap.helper.avatar.AvatarHandler;
import net.iGap.helper.avatar.ParamWithAvatarType;
import net.iGap.proto.ProtoError;
import net.iGap.proto.ProtoGroupAvatarDelete;

public class GroupAvatarDeleteResponse extends MessageHandler {

    public int actionId;
    public Object message;
    public String identity;

    public GroupAvatarDeleteResponse(int actionId, Object protoClass, String identity) {
        super(actionId, protoClass, identity);
        this.message = protoClass;
        this.identity = identity;
        this.actionId = actionId;
    }

    @Override
    public void handler() {
        super.handler();

        ProtoGroupAvatarDelete.GroupAvatarDeleteResponse.Builder groupAvatarDelete = (ProtoGroupAvatarDelete.GroupAvatarDeleteResponse.Builder) message;
        if (G.onGroupAvatarDelete != null) {
            G.onGroupAvatarDelete.onDeleteAvatar(groupAvatarDelete.getRoomId(), groupAvatarDelete.getId());
        } else {
            if (G.currentActivity != null && !G.currentActivity.isFinishing()) {
                G.currentActivity.avatarHandler.avatarDelete(new ParamWithAvatarType(null, groupAvatarDelete.getRoomId()).avatarType(AvatarHandler.AvatarType.ROOM), groupAvatarDelete.getId());
            }
        }
    }

    @Override
    public void timeOut() {
        super.timeOut();
        if (G.onGroupAvatarDelete != null) {
            G.onGroupAvatarDelete.onTimeOut();
        }
    }

    @Override
    public void error() {
        super.error();

        ProtoError.ErrorResponse.Builder errorResponse = (ProtoError.ErrorResponse.Builder) message;
        int majorCode = errorResponse.getMajorCode();
        int minorCode = errorResponse.getMinorCode();

        if (G.onGroupAvatarDelete != null) {
            G.onGroupAvatarDelete.onDeleteAvatarError(majorCode, minorCode);
        }
    }
}


