package net.iGap.network;

import android.util.Log;

import net.iGap.adapter.items.discovery.DiscoveryItem;
import net.iGap.helper.FileLog;
import net.iGap.proto.ProtoChannelAddMessageReaction;
import net.iGap.proto.ProtoChannelAvatarAdd;
import net.iGap.proto.ProtoChannelCreate;
import net.iGap.proto.ProtoChannelDelete;
import net.iGap.proto.ProtoChannelDeleteMessage;
import net.iGap.proto.ProtoChannelEditMessage;
import net.iGap.proto.ProtoChannelGetMessagesStats;
import net.iGap.proto.ProtoChannelLeft;
import net.iGap.proto.ProtoChannelPinMessage;
import net.iGap.proto.ProtoChannelUpdateReactionStatus;
import net.iGap.proto.ProtoChannelUpdateSignature;
import net.iGap.proto.ProtoChatClearMessage;
import net.iGap.proto.ProtoChatDelete;
import net.iGap.proto.ProtoChatDeleteMessage;
import net.iGap.proto.ProtoChatEditMessage;
import net.iGap.proto.ProtoChatGetRoom;
import net.iGap.proto.ProtoChatUpdateStatus;
import net.iGap.proto.ProtoClientGetDiscovery;
import net.iGap.proto.ProtoClientMuteRoom;
import net.iGap.proto.ProtoClientPinRoom;
import net.iGap.proto.ProtoError;
import net.iGap.proto.ProtoGlobal;
import net.iGap.proto.ProtoGroupClearMessage;
import net.iGap.proto.ProtoGroupCreate;
import net.iGap.proto.ProtoGroupDelete;
import net.iGap.proto.ProtoGroupDeleteMessage;
import net.iGap.proto.ProtoGroupEditMessage;
import net.iGap.proto.ProtoGroupLeft;
import net.iGap.proto.ProtoGroupPinMessage;
import net.iGap.proto.ProtoGroupUpdateStatus;
import net.iGap.proto.ProtoInfoConfig;
import net.iGap.proto.ProtoStoryAddView;
import net.iGap.proto.ProtoStoryDeleteStory;
import net.iGap.proto.ProtoStoryGetOwnStoryViews;
import net.iGap.proto.ProtoStoryGetStories;
import net.iGap.proto.ProtoStoryRoomAddNew;
import net.iGap.proto.ProtoStoryUserAddNew;
import net.iGap.proto.ProtoUserInfo;
import net.iGap.request.RequestPagination;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class IG_RPC {

    public static class Error extends AbstractObject {
        public static int actionId = 0;
        public int minor;
        public int major;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoError.ErrorResponse response = ProtoError.ErrorResponse.parseFrom(message);
            resId = response.getResponse().getId();
            minor = response.getMinorCode();
            major = response.getMajorCode();
        }
    }

    public static class TimeOut_error extends Error {
        public TimeOut_error() {
            Log.e("IG_RPC_timeout", "TimeOut_error");
            major = 5;
            minor = 1;
        }
    }


    public static class Story_Get_Own_Story_Views extends AbstractObject {
        public static int actionId = 1205;
        public int offset;
        public int limit;


        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Story_Get_Own_Story_Views.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryGetOwnStoryViews.StoryGetOwnStoryViews.Builder builder = ProtoStoryGetOwnStoryViews.StoryGetOwnStoryViews.newBuilder();
            builder.setPagination(new RequestPagination().pagination(offset, limit));
            return builder;
        }
    }

    public static class Res_Story_Get_Own_Story_Views extends AbstractObject {
        public static int actionId = 31205;
        public List<ProtoStoryGetOwnStoryViews.GroupedViews> groupedViews;

        public static Res_Story_Get_Own_Story_Views deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Story_Get_Own_Story_Views object = null;
            try {
                object = new Res_Story_Get_Own_Story_Views();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryGetOwnStoryViews.StoryGetOwnStoryViewsResponse response = ProtoStoryGetOwnStoryViews.StoryGetOwnStoryViewsResponse.parseFrom(message);
            resId = response.getResponse().getId();
            groupedViews = response.getGroupedViewsList();
        }
    }


    public static class Story_User_Add_New extends AbstractObject {
        public static int actionId = 1201;
        public List<ProtoStoryUserAddNew.StoryAddRequest> storyAddRequests;


        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Story_User_Add_New.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryUserAddNew.StoryUserAddNew.Builder builder = ProtoStoryUserAddNew.StoryUserAddNew.newBuilder();
            builder.addAllTokenBatch(storyAddRequests);
            return builder;
        }
    }

    public static class Res_Story_User_Add_New extends AbstractObject {
        public static int actionId = 31201;
        public List<ProtoGlobal.Story> stories;


        public static Res_Story_User_Add_New deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Story_User_Add_New object = null;
            try {
                object = new Res_Story_User_Add_New();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryUserAddNew.StoryUserAddNewResponse response = ProtoStoryUserAddNew.StoryUserAddNewResponse.parseFrom(message);
            resId = response.getResponse().getId();
            stories = response.getStoryList();

        }
    }


    public static class Story_Room_Add_New extends AbstractObject {
        public static int actionId = 1202;
        public List<ProtoStoryUserAddNew.StoryAddRequest> storyAddRequests;
        public long roomId;

        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Story_Room_Add_New.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryRoomAddNew.StoryRoomAddNew.Builder builder = ProtoStoryRoomAddNew.StoryRoomAddNew.newBuilder();
            builder.addAllTokenBatch(storyAddRequests);
            builder.setRoomId(roomId);
            return builder;
        }
    }

    public static class Res_Story_Room_Add_New extends AbstractObject {
        public static int actionId = 31202;
        public List<ProtoGlobal.Story> stories;

        public static Res_Story_Room_Add_New deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Story_Room_Add_New object = null;
            try {
                object = new Res_Story_Room_Add_New();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryRoomAddNew.StoryRoomAddNewResponse response = ProtoStoryRoomAddNew.StoryRoomAddNewResponse.parseFrom(message);
            resId = response.getResponse().getId();
            stories = response.getStoryList();
        }
    }


    public static class Get_Stories extends AbstractObject {
        public static int actionId = 1203;
        public int offset;
        public int limit;


        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Get_Stories.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryGetStories.StoryGetStories.Builder builder = ProtoStoryGetStories.StoryGetStories.newBuilder();
            builder.setPagination(new RequestPagination().pagination(offset, limit));
            return builder;
        }
    }

    public static class Res_Get_Stories extends AbstractObject {
        public static int actionId = 31203;
        public List<ProtoStoryGetStories.GroupedStories> stories;

        public static Res_Get_Stories deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Get_Stories object = null;
            try {
                object = new Res_Get_Stories();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryGetStories.StoryGetStoriesResponse response = ProtoStoryGetStories.StoryGetStoriesResponse.parseFrom(message);
            resId = response.getResponse().getId();
            stories = response.getGroupedStoriesList();
        }
    }


    public static class Story_Delete extends AbstractObject {
        public static int actionId = 1206;
        public long storyId;


        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Story_Delete.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryDeleteStory.StoryDeleteStory.Builder builder = ProtoStoryDeleteStory.StoryDeleteStory.newBuilder();
            builder.setStoryId(storyId);
            return builder;
        }
    }

    public static class Res_Story_Delete extends AbstractObject {
        public static int actionId = 31206;
        public long storyId;
        public long userId;

        public static Res_Story_Delete deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Story_Delete object = null;
            try {
                object = new Res_Story_Delete();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryDeleteStory.StoryDeleteStoryResponse response = ProtoStoryDeleteStory.StoryDeleteStoryResponse.parseFrom(message);
            resId = response.getResponse().getId();
            storyId = response.getStoryId();
            userId = response.getUserId();
        }
    }


    public static class Story_Add_View extends AbstractObject {
        public static int actionId = 1204;
        public String storyId;


        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Story_Add_View.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoStoryAddView.StoryAddView.Builder builder = ProtoStoryAddView.StoryAddView.newBuilder();
            builder.setStoryId(storyId);
            return builder;
        }
    }

    public static class Res_Story_Add_View extends AbstractObject {
        public static int actionId = 31204;
        public long storyId;
        public long storyOwnerUserId;
        public long userId;
        public int viewAt;

        public static Res_Story_Add_View deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Story_Add_View object = null;
            try {
                object = new Res_Story_Add_View();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoStoryAddView.StoryAddViewResponse response = ProtoStoryAddView.StoryAddViewResponse.parseFrom(message);
            resId = response.getResponse().getId();
            storyId = response.getStoryId();
            storyOwnerUserId = response.getStoryOwnerUserId();
            userId = response.getUserId();
            viewAt = response.getViewedAt();
        }
    }


    public static class Group_Update_Status extends AbstractObject {
        public static int actionId = 311;
        public long roomId;
        public long messageId;
        public long documentId;
        public ProtoGlobal.RoomMessageStatus roomMessageStatus;

        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Group_Update_Status.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupUpdateStatus.GroupUpdateStatus.Builder builder = ProtoGroupUpdateStatus.GroupUpdateStatus.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setStatus(roomMessageStatus);
            return builder;
        }
    }

    public static class Res_Group_Update_Status extends AbstractObject {
        public static int actionId = 30311;
        public String updaterAuthorHash;
        public long roomId;
        public long messageId;
        public long documentId;
        public long statusVersion;
        public ProtoGlobal.RoomMessageStatus statusValue;

        public static Res_Group_Update_Status deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Update_Status object = null;
            try {
                object = new Res_Group_Update_Status();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupUpdateStatus.GroupUpdateStatusResponse response = ProtoGroupUpdateStatus.GroupUpdateStatusResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            updaterAuthorHash = response.getUpdaterAuthorHash();
            statusValue = response.getStatus();
            statusVersion = response.getStatusVersion();
        }
    }

    public static class Chat_Update_Status extends AbstractObject {
        public static int actionId = 202;
        public long roomId;
        public long messageId;
        public long documentId;
        public ProtoGlobal.RoomMessageStatus roomMessageStatus;

        @Override

        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Chat_Update_Status.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChatUpdateStatus.ChatUpdateStatus.Builder builder = ProtoChatUpdateStatus.ChatUpdateStatus.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setStatus(roomMessageStatus);
            return builder;
        }
    }

    public static class Res_Chat_Update_Status extends AbstractObject {
        public static int actionId = 30202;
        public String updaterAuthorHash;
        public long roomId;
        public long messageId;
        public long documentId;
        public long statusVersion;
        public ProtoGlobal.RoomMessageStatus statusValue;

        public static Res_Chat_Update_Status deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Chat_Update_Status object = null;
            try {
                object = new Res_Chat_Update_Status();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatUpdateStatus.ChatUpdateStatusResponse response = ProtoChatUpdateStatus.ChatUpdateStatusResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            updaterAuthorHash = response.getUpdaterAuthorHash();
            statusValue = response.getStatus();
            statusVersion = response.getStatusVersion();
        }
    }

    public static class Group_Clear_History extends AbstractObject {
        public static int actionId = 304;
        public long roomId;
        public long lastMessageId;

        @Override
        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Group_Clear_History.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupClearMessage.GroupClearMessage.Builder builder = ProtoGroupClearMessage.GroupClearMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setClearId(lastMessageId);

            return builder;
        }
    }

    public static class Res_Group_Clear_History extends AbstractObject {
        public static int actionId = 30304;
        public long roomId;
        public long clearId;


        public static Res_Group_Clear_History deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Clear_History object = null;
            try {
                object = new Res_Group_Clear_History();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupClearMessage.GroupClearMessageResponse response = ProtoGroupClearMessage.GroupClearMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            clearId = response.getClearId();
        }
    }

    public static class Chat_Clear_History extends AbstractObject {
        public static int actionId = 205;
        public long roomId;
        public long lastMessageId;

        @Override
        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Chat_Clear_History.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChatClearMessage.ChatClearMessage.Builder builder = ProtoChatClearMessage.ChatClearMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setClearId(lastMessageId);

            return builder;
        }
    }

    public static class Res_Chat_Clear_History extends AbstractObject {
        public static int actionId = 30205;
        public long roomId;
        public long clearId;


        public static Res_Chat_Clear_History deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Chat_Clear_History object = null;
            try {
                object = new Res_Chat_Clear_History();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatClearMessage.ChatClearMessageResponse response = ProtoChatClearMessage.ChatClearMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            clearId = response.getClearId();
        }
    }

    public static class Channel_AddAvatar extends AbstractObject {
        public static int actionId = 412;
        public long roomId;
        public String attachment;

        @Override
        public int getActionId() {
            return actionId;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Channel_Avatar.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChannelAvatarAdd.ChannelAvatarAdd.Builder builder = ProtoChannelAvatarAdd.ChannelAvatarAdd.newBuilder();

            builder.setRoomId(roomId);
            builder.setAttachment(attachment);

            return builder;
        }
    }

    public static class Res_Channel_Avatar extends AbstractObject {
        public static int actionId = 30412;
        public long roomId;
        public ProtoGlobal.Avatar avatar;


        public static Res_Channel_Avatar deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Avatar object = null;
            try {
                object = new Res_Channel_Avatar();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelAvatarAdd.ChannelAvatarAddResponse response = ProtoChannelAvatarAdd.ChannelAvatarAddResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            avatar = response.getAvatar();
        }
    }

    public static class Group_Create extends AbstractObject {
        public static int actionId = 300;
        public String name;
        public String description;

        @Override
        public Res_Group_Create deserializeResponse(int constructor, byte[] message) {
            return Res_Group_Create.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupCreate.GroupCreate.Builder builder = ProtoGroupCreate.GroupCreate.newBuilder();

            builder.setName(name);
            builder.setDescription(description.trim());

            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Group_Create extends AbstractObject {
        public static int actionId = 30300;

        public String inviteLink;
        public long roomId;

        public static Res_Group_Create deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }
            Res_Group_Create object = null;
            try {
                object = new Res_Group_Create();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupCreate.GroupCreateResponse response = ProtoGroupCreate.GroupCreateResponse.parseFrom(message);
            resId = response.getResponse().getId();
            inviteLink = response.getInviteLink();
            roomId = response.getRoomId();
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Channel_Create extends AbstractObject {
        public static int actionId = 400;
        public String name;
        public String description;

        @Override
        public Res_Channel_Create deserializeResponse(int constructor, byte[] message) {
            return Res_Channel_Create.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChannelCreate.ChannelCreate.Builder builder = ProtoChannelCreate.ChannelCreate.newBuilder();

            builder.setName(name);
            builder.setDescription(description.trim());

            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Create extends AbstractObject {
        public static int actionId = 30400;

        public String inviteLink;
        public long roomId;

        public static Res_Channel_Create deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Create object = null;
            try {
                object = new Res_Channel_Create();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelCreate.ChannelCreateResponse response = ProtoChannelCreate.ChannelCreateResponse.parseFrom(message);
            resId = response.getResponse().getId();
            inviteLink = response.getInviteLink();
            roomId = response.getRoomId();
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Channel_Delete extends AbstractObject {
        public static int actionId = 404;

        public long roomId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Channel_Delete.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChannelDelete.ChannelDelete.Builder builder = ProtoChannelDelete.ChannelDelete.newBuilder();
            builder.setRoomId(roomId);

            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Delete extends AbstractObject {
        public static int actionId = 30404;
        public long roomId;

        public static AbstractObject deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Delete object = null;
            try {
                object = new Res_Channel_Delete();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelDelete.ChannelDeleteResponse response = ProtoChannelDelete.ChannelDeleteResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class InfoConfig extends AbstractObject {
        public static final int actionId = 506;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return Res_Info_Config.deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            return ProtoInfoConfig.InfoConfig.newBuilder();
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Info_Config extends AbstractObject {
        public static final int actionId = 30506;

        public long captionLengthMax;
        public long channelAddMemberLimit;
        public boolean debugMode;
        public long defaultTimeout;
        public long groupAddMemberLimit;
        public long maxFileSize;
        public long messageLengthMax;
        public boolean optimizeMode;
        public String servicesBaseUrl;
        public int fileGateway;
        public boolean showAdvertisement;
        public int defaultTab;
        public HashMap<String, Integer> microServices = new HashMap<>();

        public static Res_Info_Config deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Info_Config object = null;
            try {
                object = new Res_Info_Config();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoInfoConfig.InfoConfigResponse response = ProtoInfoConfig.InfoConfigResponse.parseFrom(message);

            resId = response.getResponse().getId();
            servicesBaseUrl = response.getBaseUrl();
            captionLengthMax = response.getCaptionLengthMax();
            channelAddMemberLimit = response.getChannelAddMemberLimit();
            debugMode = response.getDebugMode();
            defaultTimeout = response.getDefaultTimeout();
            maxFileSize = response.getMaxFileSize();
            optimizeMode = response.getOptimizeMode();
            showAdvertisement = response.getShowAdvertising();
            defaultTab = response.getDefaultTabValue();

            for (int i = 0; i < response.getMicroServiceCount(); i++) {
                ProtoInfoConfig.MicroService microService = response.getMicroServiceList().get(i);
                microServices.put(microService.getName(), microService.getTypeValue());
            }

            Integer file = microServices.get("file");
            if (file != null) {
                fileGateway = file;
            }
        }
    }

    public static class Client_Get_Discovery extends AbstractObject {
        public static final int actionId = 620;

        public int itemId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Client_Get_Discovery().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoClientGetDiscovery.ClientGetDiscovery.Builder builder = ProtoClientGetDiscovery.ClientGetDiscovery.newBuilder();
            builder.setItemId(itemId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Client_Get_Discovery extends AbstractObject {
        public static final int actionId = 30620;

        public ArrayList<DiscoveryItem> items = new ArrayList<>();


        public Res_Client_Get_Discovery deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Client_Get_Discovery object = null;
            try {
                object = new Res_Client_Get_Discovery();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoClientGetDiscovery.ClientGetDiscoveryResponse response = ProtoClientGetDiscovery.ClientGetDiscoveryResponse.parseFrom(message);
            resId = response.getResponse().getId();

            for (ProtoGlobal.Discovery discovery : response.getDiscoveriesList()) {
                items.add(new DiscoveryItem(discovery));
            }

        }
    }

    public static class Chat_edit_message extends AbstractObject {
        public static final int actionId = 203;

        public long roomId;
        public long messageId;
        public long documentId;
        public String message;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Chat_Edit_Message().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChatEditMessage.ChatEditMessage.Builder builder = ProtoChatEditMessage.ChatEditMessage.newBuilder();
            builder.setMessage(message);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Chat_Edit_Message extends AbstractObject {
        public static final int actionId = 30203;

        public String newMessage;
        public long messageId;
        public long documentId;
        public int messageType;
        public long messageVersion;
        public long roomId;

        public Res_Chat_Edit_Message deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Chat_Edit_Message object = null;
            try {
                object = new Res_Chat_Edit_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatEditMessage.ChatEditMessageResponse response = ProtoChatEditMessage.ChatEditMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();

            newMessage = response.getMessage();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            messageType = response.getMessageTypeValue();
            messageVersion = response.getMessageVersion();
            roomId = response.getRoomId();
        }
    }


    public static class Group_edit_message extends AbstractObject {
        public static final int actionId = 325;

        public long roomId;
        public long messageId;
        public long documentId;
        public String message;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Group_Edit_Message().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupEditMessage.GroupEditMessage.Builder builder = ProtoGroupEditMessage.GroupEditMessage.newBuilder();
            builder.setMessage(message);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Group_Edit_Message extends AbstractObject {
        public static final int actionId = 30325;

        public String newMessage;
        public long messageId;
        public long documentId;
        public int messageType;
        public long messageVersion;
        public long roomId;

        public Res_Group_Edit_Message deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Edit_Message object = null;
            try {
                object = new Res_Group_Edit_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupEditMessage.GroupEditMessageResponse response = ProtoGroupEditMessage.GroupEditMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();

            newMessage = response.getMessage();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            messageType = response.getMessageTypeValue();
            messageVersion = response.getMessageVersion();
            roomId = response.getRoomId();
        }
    }

    public static class Channel_edit_message extends AbstractObject {
        public static final int actionId = 425;

        public long roomId;
        public long messageId;
        public long documentId;
        public String message;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Edit_Message().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChatEditMessage.ChatEditMessage.Builder builder = ProtoChatEditMessage.ChatEditMessage.newBuilder();
            builder.setMessage(message);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Edit_Message extends AbstractObject {
        public static final int actionId = 30425;

        public String newMessage;
        public long messageId;
        public long documentId;
        public int messageType;
        public long messageVersion;
        public long roomId;

        public Res_Channel_Edit_Message deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Edit_Message object = null;
            try {
                object = new Res_Channel_Edit_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelEditMessage.ChannelEditMessageResponse response = ProtoChannelEditMessage.ChannelEditMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();

            newMessage = response.getMessage();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            messageType = response.getMessageTypeValue();
            messageVersion = response.getMessageVersion();
            roomId = response.getRoomId();
        }
    }

    public static class Group_pin_message extends AbstractObject {
        public static final int actionId = 326;

        public long roomId;
        public long messageId;
        public long documentId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Group_pin_message_response().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupPinMessage.GroupPinMessage.Builder builder = ProtoGroupPinMessage.GroupPinMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Group_pin_message_response extends AbstractObject {
        public static final int actionId = 30326;

        public ProtoGlobal.RoomMessage pinnedMessage;
        public long roomId;

        public Group_pin_message_response deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Group_pin_message_response object = null;
            try {
                object = new Group_pin_message_response();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupPinMessage.GroupPinMessageResponse response = ProtoGroupPinMessage.GroupPinMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            pinnedMessage = response.getPinnedMessage();
            roomId = response.getRoomId();
        }

    }

    public static class Channel_pin_message extends AbstractObject {
        public static final int actionId = 427;

        public long roomId;
        public long messageId;
        public long documentId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Channel_pin_message_response().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChannelPinMessage.ChannelPinMessage.Builder builder = ProtoChannelPinMessage.ChannelPinMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Channel_pin_message_response extends AbstractObject {
        public static final int actionId = 30427;

        public ProtoGlobal.RoomMessage pinnedMessage;
        public long roomId;

        public Channel_pin_message_response deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Channel_pin_message_response object = null;
            try {
                object = new Channel_pin_message_response();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelPinMessage.ChannelPinMessageResponse response = ProtoChannelPinMessage.ChannelPinMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            pinnedMessage = response.getPinnedMessage();
            roomId = response.getRoomId();
        }
    }

    public static class Channel_Delete_Message extends AbstractObject {

        public static int actionId = 411;
        public long roomId;
        public long messageId;
        public long documentId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Delete_Message().deserializeResponse(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChannelDeleteMessage.ChannelDeleteMessage.Builder builder = ProtoChannelDeleteMessage.ChannelDeleteMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Delete_Message extends AbstractObject {

        public static int actionId = 30411;
        public long roomId;
        public long messageId;
        public long documentId;
        public long deleteVersion;

        @Override

        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Delete_Message object = null;
            try {
                object = new Res_Channel_Delete_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelDeleteMessage.ChannelDeleteMessageResponse response = ProtoChannelDeleteMessage.ChannelDeleteMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            deleteVersion = response.getDeleteVersion();
        }
    }

    public static class Chat_Delete_Message extends AbstractObject {

        public static final int actionId = 204;
        public long roomId;
        public long messageId;
        public long documentId;
        public boolean both;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Chat_Delete_Message().deserializeResponse(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoChatDeleteMessage.ChatDeleteMessage.Builder builder = ProtoChatDeleteMessage.ChatDeleteMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setBoth(both);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Chat_Delete_Message extends AbstractObject {
        public static int actionId = 30204;
        public long roomId;
        public long messageId;
        public long documentId;
        public long deleteVersion;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Chat_Delete_Message object = null;
            try {
                object = new Res_Chat_Delete_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatDeleteMessage.ChatDeleteMessageResponse response = ProtoChatDeleteMessage.ChatDeleteMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            deleteVersion = response.getDeleteVersion();
        }
    }

    public static class Group_Delete_Message extends AbstractObject {

        public static int actionId = 320;
        public long roomId;
        public long messageId;
        public long documentId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Group_Delete_Message().deserializeResponse(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoGroupDeleteMessage.GroupDeleteMessage.Builder builder = ProtoGroupDeleteMessage.GroupDeleteMessage.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Group_Delete_Message extends AbstractObject {

        public static int actionId = 30320;
        public long roomId;
        public long messageId;
        public long documentId;
        public long deleteVersion;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Delete_Message object = null;
            try {
                object = new Res_Group_Delete_Message();
                object.readParams(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupDeleteMessage.GroupDeleteMessageResponse response = ProtoGroupDeleteMessage.GroupDeleteMessageResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            messageId = response.getMessageId();
            documentId = response.getDocumentId();
            deleteVersion = response.getDeleteVersion();
        }
    }

    public static class Channel_Add_Message_Reaction extends AbstractObject {

        public static int actionId = 424;
        public long roomId;
        public long messageId;
        public long documentId;
        public ProtoGlobal.RoomMessageReaction reaction;


        @Override
        public Object getProtoObject() {
            ProtoChannelAddMessageReaction.ChannelAddMessageReaction.Builder builder = ProtoChannelAddMessageReaction.ChannelAddMessageReaction.newBuilder();
            builder.setRoomId(roomId);
            builder.setMessageId(messageId);
            builder.setDocumentId(documentId);
            builder.setReaction(reaction);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Add_Message_Reaction().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Add_Message_Reaction extends AbstractObject {

        public static int actionId = 30424;
        public String reactionCounter;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelAddMessageReaction.ChannelAddMessageReactionResponse response = ProtoChannelAddMessageReaction.ChannelAddMessageReactionResponse.parseFrom(message);
            resId = response.getResponse().getId();
            reactionCounter = response.getReactionCounterLabel();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Add_Message_Reaction object = null;
            try {
                object = new Res_Channel_Add_Message_Reaction();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Channel_Get_Message_Reaction extends AbstractObject {

        public static int actionId = 423;
        public long roomId;
        public HashSet<Long> messageIds;

        @Override
        public Object getProtoObject() {
            ProtoChannelGetMessagesStats.ChannelGetMessagesStats.Builder builder = ProtoChannelGetMessagesStats.ChannelGetMessagesStats.newBuilder();
            builder.setRoomId(roomId);
            builder.addAllMessageId(messageIds);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Get_Message_Reaction().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Channel_Get_Message_Reaction extends AbstractObject {

        public static int actionId = 30423;
        public List<ProtoChannelGetMessagesStats.ChannelGetMessagesStatsResponse.Stats> states;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;


            }

            Res_Channel_Get_Message_Reaction object = null;
            try {
                object = new Res_Channel_Get_Message_Reaction();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();

            }

            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelGetMessagesStats.ChannelGetMessagesStatsResponse response = ProtoChannelGetMessagesStats.ChannelGetMessagesStatsResponse.parseFrom(message);
            resId = response.getResponse().getId();
            states = response.getStatsList();
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Channel_Update_Reaction_Status extends AbstractObject {
        public int actionId = 426;
        public long roomId;
        public boolean reactionStatus;

        @Override
        public Object getProtoObject() {
            ProtoChannelUpdateReactionStatus.ChannelUpdateReactionStatus.Builder builder = ProtoChannelUpdateReactionStatus.ChannelUpdateReactionStatus.newBuilder();
            builder.setRoomId(roomId);
            builder.setReactionStatus(reactionStatus);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Update_Reaction_Status().deserializeResponse(constructor, message);
        }


        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Update_Reaction_Status extends AbstractObject {

        public static int actionId = 30426;
        public long roomId;
        public boolean reactionStatus;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelUpdateReactionStatus.ChannelUpdateReactionStatusResponse response = ProtoChannelUpdateReactionStatus.ChannelUpdateReactionStatusResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            reactionStatus = response.getReactionStatus();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Update_Reaction_Status object = null;
            try {
                object = new Res_Channel_Update_Reaction_Status();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();

            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Channel_Update_Signature extends AbstractObject {

        public int actionId = 422;
        public long roomId;
        public boolean signature;

        @Override
        public Object getProtoObject() {
            ProtoChannelUpdateSignature.ChannelUpdateSignature.Builder builder = ProtoChannelUpdateSignature.ChannelUpdateSignature.newBuilder();
            builder.setRoomId(roomId);
            builder.setSignature(signature);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Update_Signature().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_Channel_Update_Signature extends AbstractObject {

        public static int actionId = 30422;
        public long roomId;
        public boolean signature;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelUpdateSignature.ChannelUpdateSignatureResponse response = ProtoChannelUpdateSignature.ChannelUpdateSignatureResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            signature = response.getSignature();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Update_Signature object = null;
            try {
                object = new Res_Channel_Update_Signature();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();

            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Client_Pin_Room extends AbstractObject {

        public int actionId = 615;
        public long roomId;
        public boolean pin;

        @Override
        public Object getProtoObject() {
            ProtoClientPinRoom.ClientPinRoom.Builder builder = ProtoClientPinRoom.ClientPinRoom.newBuilder();
            builder.setRoomId(roomId);
            builder.setPin(pin);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Client_Pin_Room().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Client_Pin_Room extends AbstractObject {

        public static int actionId = 30615;
        public long roomId;
        public long pinId;

        @Override
        public int getActionId() {
            return actionId;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoClientPinRoom.ClientPinRoomResponse response = ProtoClientPinRoom.ClientPinRoomResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            pinId = response.getPinId();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Client_Pin_Room object = null;
            try {
                object = new Res_Client_Pin_Room();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }
    }

    public static class Client_Mute_Room extends AbstractObject {

        public int actionId = 614;
        public long roomId;
        public ProtoGlobal.RoomMute roomMute;

        @Override
        public Object getProtoObject() {
            ProtoClientMuteRoom.ClientMuteRoom.Builder builder = ProtoClientMuteRoom.ClientMuteRoom.newBuilder();
            builder.setRoomId(roomId);
            builder.setRoomMute(roomMute);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Client_Mute_Room().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Client_Mute_Room extends AbstractObject {

        public static int actionId = 30614;
        public long roomId;
        public ProtoGlobal.RoomMute roomMute;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoClientMuteRoom.ClientMuteRoomResponse response = ProtoClientMuteRoom.ClientMuteRoomResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            roomMute = response.getRoomMute();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Client_Mute_Room object = null;

            try {
                object = new Res_Client_Mute_Room();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Chat_Delete_Room extends AbstractObject {

        public int actionId = 206;
        public long roomId;

        @Override
        public Object getProtoObject() {
            ProtoChatDelete.ChatDelete.Builder builder = ProtoChatDelete.ChatDelete.newBuilder();
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Chat_Delete_Room().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Chat_Delete_Room extends AbstractObject {

        public static int actionId = 30206;
        public long roomId;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatDelete.ChatDeleteResponse response = ProtoChatDelete.ChatDeleteResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Chat_Delete_Room object = null;

            try {
                object = new Res_Chat_Delete_Room();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Group_Delete_Room extends AbstractObject {

        public int actionId = 318;
        public long roomId;

        @Override
        public Object getProtoObject() {
            ProtoGroupDelete.GroupDelete.Builder builder = ProtoGroupDelete.GroupDelete.newBuilder();
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Group_Delete_Room().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Group_Delete_Room extends AbstractObject {

        public static int actionId = 30318;
        public long roomId;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupDelete.GroupDeleteResponse response = ProtoGroupDelete.GroupDeleteResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {

            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Delete_Room object = null;
            try {
                object = new Res_Group_Delete_Room();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Group_Left extends AbstractObject {

        public int actionId = 309;
        public long roomId;

        @Override
        public Object getProtoObject() {
            ProtoGroupLeft.GroupLeft.Builder builder = ProtoGroupLeft.GroupLeft.newBuilder();
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Group_Left().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Group_Left extends AbstractObject {

        public static int actionId = 30309;
        public long roomId;
        public long memberId;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoGroupLeft.GroupLeftResponse response = ProtoGroupLeft.GroupLeftResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            memberId = response.getMemberId();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Group_Left object = null;
            try {
                object = new Res_Group_Left();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Channel_Left extends AbstractObject {

        public int actionId = 409;
        public long roomId;

        @Override
        public Object getProtoObject() {
            ProtoChannelLeft.ChannelLeft.Builder builder = ProtoChannelLeft.ChannelLeft.newBuilder();
            builder.setRoomId(roomId);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_Channel_Left().deserializeResponse(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Res_Channel_Left extends AbstractObject {

        public static int actionId = 30409;
        public long roomId;
        public long memberId;

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChannelLeft.ChannelLeftResponse response = ProtoChannelLeft.ChannelLeftResponse.parseFrom(message);
            resId = response.getResponse().getId();
            roomId = response.getRoomId();
            memberId = response.getMemberId();
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_Channel_Left object = null;
            try {
                object = new Res_Channel_Left();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

    }

    public static class Chat_get_room extends AbstractObject {
        public static final int actionId = 200;
        public long peerId;

        @Override
        public Object getProtoObject() {
            ProtoChatGetRoom.ChatGetRoom.Builder builder = ProtoChatGetRoom.ChatGetRoom.newBuilder();
            builder.setPeerId(peerId);
            return builder;
        }

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_chat_get_room().deserializeObject(constructor, message);
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_chat_get_room extends AbstractObject {
        public static final int actionId = 30200;

        public ProtoGlobal.Room room;

        public AbstractObject deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_chat_get_room object = null;
            try {
                object = new Res_chat_get_room();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return object;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoChatGetRoom.ChatGetRoomResponse response = ProtoChatGetRoom.ChatGetRoomResponse.parseFrom(message);
            resId = response.getResponse().getId();
            room = response.getRoom();
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class User_info extends AbstractObject {
        public static final int actionId = 117;
        public long userId;

        @Override
        public AbstractObject deserializeResponse(int constructor, byte[] message) {
            return new Res_user_info().deserializeObject(constructor, message);
        }

        @Override
        public Object getProtoObject() {
            ProtoUserInfo.UserInfo.Builder builder = ProtoUserInfo.UserInfo.newBuilder();
            builder.setUserId(userId);
            return builder;
        }

        @Override
        public int getActionId() {
            return actionId;
        }
    }

    public static class Res_user_info extends AbstractObject {
        public static final int actionId = 30117;

        public ProtoGlobal.RegisteredUser user;

        public Res_user_info deserializeObject(int constructor, byte[] message) {
            if (constructor != actionId || message == null) {
                return null;
            }

            Res_user_info object = null;
            try {
                object = new Res_user_info();
                object.readParams(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return object;
        }

        @Override
        public int getActionId() {
            return actionId;
        }

        @Override
        public void readParams(byte[] message) throws Exception {
            ProtoUserInfo.UserInfoResponse response = ProtoUserInfo.UserInfoResponse.parseFrom(message);

            user = response.getUser();
            resId = response.getResponse().getId();
        }
    }
}
