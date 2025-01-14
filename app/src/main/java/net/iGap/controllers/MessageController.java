package net.iGap.controllers;

import android.text.format.DateUtils;
import android.util.Log;

import net.iGap.G;
import net.iGap.helper.FileLog;
import net.iGap.helper.HelperNotification;
import net.iGap.helper.HelperTimeOut;
import net.iGap.helper.upload.ApiBased.HttpUploader;
import net.iGap.helper.upload.UploadTask;
import net.iGap.module.SUID;
import net.iGap.module.accountManager.AccountManager;
import net.iGap.module.accountManager.DbManager;
import net.iGap.network.AbstractObject;
import net.iGap.network.IG_RPC;
import net.iGap.network.RequestManager;
import net.iGap.observers.eventbus.EventManager;
import net.iGap.proto.ProtoGlobal;
import net.iGap.proto.ProtoStoryUserAddNew;
import net.iGap.realm.RealmChannelExtra;
import net.iGap.realm.RealmRoom;
import net.iGap.realm.RealmStory;
import net.iGap.realm.RealmStoryProto;
import net.iGap.request.RequestClientGetRoom;
import net.iGap.response.UserLoginResponse;
import net.iGap.story.StoryObject;
import net.iGap.story.liststories.StoryFragment;
import net.iGap.structs.MessageObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static net.iGap.proto.ProtoGlobal.Room.Type.CHAT_VALUE;
import static net.iGap.proto.ProtoGlobal.Room.Type.GROUP_VALUE;

public class MessageController extends BaseController implements EventManager.EventDelegate {

    private String lastUploadedAvatarId;
    private long lastUploadedAvatarRoomId;
    public static boolean isSendingStory = false;
    public static boolean isSendingRoomStory = false;
    private static volatile MessageController[] instance = new MessageController[AccountManager.MAX_ACCOUNT_COUNT];
    private String TAG = getClass().getSimpleName() + " " + currentAccount + " ";

    public static MessageController getInstance(int account) {
        MessageController localInstance = instance[account];
        if (localInstance == null) {
            synchronized (MessageController.class) {
                localInstance = instance[account];
                if (localInstance == null) {
                    instance[account] = localInstance = new MessageController(account);
                }
            }
        }
        return localInstance;
    }

    public MessageController(int currentAccount) {
        super(currentAccount);

        G.runOnUiThread(() -> {
            EventManager.getInstance(AccountManager.selectedAccount).addObserver(EventManager.FILE_UPLOAD_FAILED, this);
            EventManager.getInstance(AccountManager.selectedAccount).addObserver(EventManager.FILE_UPLOAD_SUCCESS, this);
        });
    }


    public void onUpdate(AbstractObject object) {
        if (object == null) {
            return;
        }

        FileLog.i(TAG, "onUpdate " + object);

        if (object instanceof IG_RPC.Res_Channel_Avatar) {
            IG_RPC.Res_Channel_Avatar channelAvatar = (IG_RPC.Res_Channel_Avatar) object;
            updateChannelAvatarInternal(channelAvatar);
        } else if (object instanceof IG_RPC.Res_Group_Create) {
            IG_RPC.Res_Group_Create res = (IG_RPC.Res_Group_Create) object;
            new RequestClientGetRoom().clientGetRoom(res.roomId, RequestClientGetRoom.CreateRoomMode.requestFromOwner);
        } else if (object instanceof IG_RPC.Res_Channel_Create) {
            IG_RPC.Res_Channel_Create res = (IG_RPC.Res_Channel_Create) object;
            new RequestClientGetRoom().clientGetRoom(res.roomId, RequestClientGetRoom.CreateRoomMode.requestFromOwner);
        } else if (object instanceof IG_RPC.Res_Channel_Delete) {
            IG_RPC.Res_Channel_Delete res = (IG_RPC.Res_Channel_Delete) object;
            getMessageDataStorage().deleteRoomFromStorage(res.roomId);
        } else if (object instanceof IG_RPC.Res_Group_Edit_Message || object instanceof IG_RPC.Res_Chat_Edit_Message || object instanceof IG_RPC.Res_Channel_Edit_Message) {
            onMessageEditResponse(object, true);
        } else if (object instanceof IG_RPC.Group_pin_message_response || object instanceof IG_RPC.Channel_pin_message_response) {
            onPinMessageResponse(object);
        } else if (object instanceof IG_RPC.Res_Chat_Delete_Message) {
            IG_RPC.Res_Chat_Delete_Message res = (IG_RPC.Res_Chat_Delete_Message) object;
            onDeleteMessageResponse(res, true);
        } else if (object instanceof IG_RPC.Res_Group_Delete_Message) {
            IG_RPC.Res_Group_Delete_Message res = (IG_RPC.Res_Group_Delete_Message) object;
            onDeleteMessageResponse(res, true);
        } else if (object instanceof IG_RPC.Res_Channel_Delete_Message) {
            IG_RPC.Res_Channel_Delete_Message res = (IG_RPC.Res_Channel_Delete_Message) object;
            onDeleteMessageResponse(res, true);
        } else if (object instanceof IG_RPC.Res_Chat_Update_Status || object instanceof IG_RPC.Res_Group_Update_Status) {
            onMessageStatusResponse(object, true);
        } else if (object instanceof IG_RPC.Res_Story_User_Add_New) {
            onUserAddStoryResponse(object, true);
        } else if (object instanceof IG_RPC.Res_Story_Delete) {
            onUserDeletedStoryResponse(object, true);
        } else if (object instanceof IG_RPC.Res_Story_Add_View) {
            onUserStoryAddViewResponse(object, true);
        } else if (object instanceof IG_RPC.Res_Story_Room_Add_New) {
            onRoomAddStoryResponse(object, true);
        }
    }

    public static boolean isBothDelete(long messageTime) {
        long currentTime;
        if (RequestManager.getInstance(AccountManager.selectedAccount).isUserLogin()) {
            currentTime = G.currentServerTime * DateUtils.SECOND_IN_MILLIS;
        } else {
            currentTime = System.currentTimeMillis();
        }

        return !HelperTimeOut.timeoutChecking(currentTime, messageTime, G.bothChatDeleteTime);
    }

    public void sendUpdateStatus(int roomType, long roomId, long messageId, long documentId, int roomMessageStatus) {
        AbstractObject req = null;
        if (roomType == CHAT_VALUE) {
            IG_RPC.Chat_Update_Status chatUpdateStatusReq = new IG_RPC.Chat_Update_Status();
            chatUpdateStatusReq.roomId = roomId;
            chatUpdateStatusReq.messageId = messageId;
            chatUpdateStatusReq.documentId = documentId;
            chatUpdateStatusReq.roomMessageStatus = ProtoGlobal.RoomMessageStatus.forNumber(roomMessageStatus);
            req = chatUpdateStatusReq;
        } else if (roomType == GROUP_VALUE) {
            IG_RPC.Group_Update_Status groupUpdateStatusReq = new IG_RPC.Group_Update_Status();
            groupUpdateStatusReq.roomId = roomId;
            groupUpdateStatusReq.messageId = messageId;
            groupUpdateStatusReq.documentId = documentId;
            groupUpdateStatusReq.roomMessageStatus = ProtoGlobal.RoomMessageStatus.forNumber(roomMessageStatus);
            req = groupUpdateStatusReq;
        }

        getRequestManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                onMessageStatusResponse(response, false);
            }
        });
    }

    public void addMyStory(List<ProtoStoryUserAddNew.StoryAddRequest> storyObjects) {
        isSendingStory = true;
        HttpUploader.isMultiUpload = false;
        HttpUploader.isRoomMultiUpload = false;
        AbstractObject request = null;
        IG_RPC.Story_User_Add_New story_user_addNew = new IG_RPC.Story_User_Add_New();
        story_user_addNew.storyAddRequests = storyObjects;
        request = story_user_addNew;
        getRequestManager().sendRequest(request, (response, error) -> {

            if (error == null) {
                IG_RPC.Res_Story_User_Add_New res = (IG_RPC.Res_Story_User_Add_New) response;
                getMessageDataStorage().updateUserAddedStory(res.stories);
                MessageController.isSendingStory = false;

            } else {
                IG_RPC.Error err = (IG_RPC.Error) error;
                MessageController.isSendingStory = false;
                G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.STORY_UPLOADED_FAILED));
            }
        });

    }


    public void addMyRoomStory(List<ProtoStoryUserAddNew.StoryAddRequest> storyObjects, long roomId) {
        isSendingRoomStory = true;
        HttpUploader.isMultiUpload = false;
        HttpUploader.isRoomMultiUpload = false;
        AbstractObject request = null;
        IG_RPC.Story_Room_Add_New story_room_add_new = new IG_RPC.Story_Room_Add_New();
        story_room_add_new.storyAddRequests = storyObjects;
        story_room_add_new.roomId = roomId;
        request = story_room_add_new;
        getRequestManager().sendRequest(request, (response, error) -> {

            if (error == null) {
                IG_RPC.Res_Story_Room_Add_New res = (IG_RPC.Res_Story_Room_Add_New) response;
                getMessageDataStorage().updateRoomAddedStory(res.stories);
                MessageController.isSendingRoomStory = false;

            } else {
                IG_RPC.Error err = (IG_RPC.Error) error;
                MessageController.isSendingRoomStory = false;
                G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.STORY_UPLOADED_FAILED));
            }
        });

    }


    public void getStories(int contactCount) {
        AbstractObject req = null;
        IG_RPC.Get_Stories get_stories = new IG_RPC.Get_Stories();
        get_stories.offset = 0;
        get_stories.limit = contactCount;
        req = get_stories;
        getRequestManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                IG_RPC.Res_Get_Stories res = (IG_RPC.Res_Get_Stories) response;
                if (res.stories.size() > 0) {
                    getMessageDataStorage().updateUserAddedStoryWithStoryObjects(res.stories);
                }

            } else {

            }
        });

    }

    private void onUserStoryAddViewResponse(AbstractObject response, boolean update) {
        IG_RPC.Res_Story_Add_View story_add_view = (IG_RPC.Res_Story_Add_View) response;
        if (story_add_view.storyOwnerUserId == AccountManager.getInstance().getCurrentUser().getId()) {
            getMessageDataStorage().userAddViewStory(story_add_view.storyId, story_add_view.viewAt, story_add_view.userId, story_add_view.storyOwnerUserId);
        }
    }

    private void onUserDeletedStoryResponse(AbstractObject response, boolean update) {
        IG_RPC.Res_Story_Delete res = (IG_RPC.Res_Story_Delete) response;
        getMessageDataStorage().deleteUserStoryWithStoryId(res.storyId, res.userId);
    }

    private void onUserAddStoryResponse(AbstractObject response, boolean update) {
        IG_RPC.Res_Story_User_Add_New res = (IG_RPC.Res_Story_User_Add_New) response;
        getMessageDataStorage().updateUserAddedStory(res.stories);
    }

    private void onRoomAddStoryResponse(AbstractObject response, boolean update) {
        IG_RPC.Res_Story_Room_Add_New res = (IG_RPC.Res_Story_Room_Add_New) response;
        getMessageDataStorage().updateRoomAddedStory(res.stories);
    }

    private void onMessageStatusResponse(AbstractObject response, boolean update) {
        if (response instanceof IG_RPC.Res_Chat_Update_Status) {
            IG_RPC.Res_Chat_Update_Status res = (IG_RPC.Res_Chat_Update_Status) response;
            getMessageDataStorage().updateMessageStatus(res.roomId, res.messageId, res.documentId, res.updaterAuthorHash, res.statusValue, res.statusVersion, update);
            if (res.statusValue == ProtoGlobal.RoomMessageStatus.SEEN) {
                HelperNotification.getInstance().cancelNotification(res.roomId);
            }
        } else if (response instanceof IG_RPC.Res_Group_Update_Status) {
            IG_RPC.Res_Group_Update_Status res = (IG_RPC.Res_Group_Update_Status) response;
            getMessageDataStorage().updateMessageStatus(res.roomId, res.messageId, res.documentId, res.updaterAuthorHash, res.statusValue, res.statusVersion, update);
        }
    }

    public void clearHistoryMessage(final long roomId) {
        if (roomId > 0) {
            RealmRoom realmRoom = getMessageDataStorage().getRoom(roomId);
            long clearMessageId;

            if (realmRoom == null || !realmRoom.isLoaded() || !realmRoom.isValid()) {
                return;
            }

            if (realmRoom.getLastMessage() != null) {
                clearMessageId = realmRoom.getLastMessage().getMessageId();
            } else {
                clearMessageId = getMessageDataStorage().getRoomClearId(roomId);
            }

            if (clearMessageId == 0) {
                return;
            }

            getMessageDataStorage().setRoomClearId(roomId, clearMessageId, true);

            AbstractObject req = null;

            if (realmRoom.getType() == ProtoGlobal.Room.Type.CHAT) {
                IG_RPC.Chat_Clear_History historyReq = new IG_RPC.Chat_Clear_History();
                historyReq.roomId = roomId;
                historyReq.lastMessageId = clearMessageId;
                req = historyReq;
            } else if (realmRoom.getType() == ProtoGlobal.Room.Type.GROUP) {
                IG_RPC.Group_Clear_History historyReq = new IG_RPC.Group_Clear_History();
                historyReq.roomId = roomId;
                historyReq.lastMessageId = clearMessageId;
                req = historyReq;
            }

            getRequestManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    long clearId = 0;
                    if (response instanceof IG_RPC.Res_Chat_Clear_History) {
                        IG_RPC.Res_Chat_Clear_History resClearMessage = (IG_RPC.Res_Chat_Clear_History) response;
                        clearId = resClearMessage.clearId;
                    } else if (response instanceof IG_RPC.Res_Group_Clear_History) {
                        IG_RPC.Res_Group_Clear_History resClearMessage = (IG_RPC.Res_Group_Clear_History) response;
                        clearId = resClearMessage.clearId;
                    }

                    getMessageDataStorage().clearRoomHistory(roomId, clearId);
                }
            });
        }
    }

    public String saveChannelAvatar(String path, long roomId) {
        lastUploadedAvatarId = String.valueOf(SUID.id().get());
        lastUploadedAvatarRoomId = roomId;

        getUploadManager().upload(new UploadTask(lastUploadedAvatarId, new File(path), ProtoGlobal.RoomMessageType.IMAGE));

        return lastUploadedAvatarId;
    }

    private void updateChannelAvatarInternal(IG_RPC.Res_Channel_Avatar avatar) {
        getMessageDataStorage().putUserAvatar(avatar.roomId, avatar.avatar);
        G.runOnUiThread(() -> getEventManager().postEvent(EventManager.AVATAR_UPDATE, avatar.roomId, avatar.avatar));
    }

    public void deleteChannel(long roomId) {
        IG_RPC.Channel_Delete req = new IG_RPC.Channel_Delete();
        req.roomId = roomId;

        getRequestManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                IG_RPC.Res_Channel_Delete res = (IG_RPC.Res_Channel_Delete) response;
                getMessageDataStorage().deleteRoomFromStorage(res.roomId);
            } else {
                IG_RPC.Error err = (IG_RPC.Error) error;
                if (err.major == 411 && err.minor == 2) {
                    getMessageDataStorage().deleteRoomFromStorage(roomId);
                }
            }
        });
    }

    public void cancelUploadFile(long roomId, MessageObject message) {
        if (message == null || roomId == 0) {
            return;
        }

        getMessageDataStorage().resetRoomLastMessage(roomId, message.id);
    }

    public void editMessage(long messageId, long documentId, long roomId, String newMessage, int chatType) {
        AbstractObject req = null;

        if (chatType == CHAT_VALUE) {
            IG_RPC.Chat_edit_message req_chat_edit = new IG_RPC.Chat_edit_message();
            req_chat_edit.message = newMessage;
            req_chat_edit.messageId = messageId;
            req_chat_edit.documentId = documentId;
            req_chat_edit.roomId = roomId;
            req = req_chat_edit;
        } else if (chatType == GROUP_VALUE) {
            IG_RPC.Group_edit_message req_group_edit = new IG_RPC.Group_edit_message();
            req_group_edit.message = newMessage;
            req_group_edit.messageId = messageId;
            req_group_edit.documentId = documentId;
            req_group_edit.roomId = roomId;
            req = req_group_edit;
        } else if (chatType == ProtoGlobal.Room.Type.CHANNEL_VALUE) {
            IG_RPC.Channel_edit_message req_channel_edit = new IG_RPC.Channel_edit_message();
            req_channel_edit.message = newMessage;
            req_channel_edit.messageId = messageId;
            req_channel_edit.documentId = documentId;
            req_channel_edit.roomId = roomId;
            req = req_channel_edit;
        }

        getRequestManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                onMessageEditResponse(response, false);
            } else {
                IG_RPC.Error err = (IG_RPC.Error) error;
                FileLog.e("Edit message -> Major: " + err.minor + " Minor: " + err.minor);
            }
        });

    }

    private void onMessageEditResponse(AbstractObject response, boolean isUpdate) {
        if (response == null) {
            return;
        }

        long roomId;
        long messageId;
        long documentId;
        int messageType;
        long messageVersion;
        String newMessage;

        if (response instanceof IG_RPC.Res_Chat_Edit_Message) {
            IG_RPC.Res_Chat_Edit_Message res = (IG_RPC.Res_Chat_Edit_Message) response;

            newMessage = res.newMessage;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            messageVersion = res.messageVersion;
            messageType = res.messageType;
        } else if (response instanceof IG_RPC.Res_Group_Edit_Message) {
            IG_RPC.Res_Group_Edit_Message res = (IG_RPC.Res_Group_Edit_Message) response;

            newMessage = res.newMessage;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            messageVersion = res.messageVersion;
            messageType = res.messageType;
        } else {

            IG_RPC.Res_Channel_Edit_Message res = (IG_RPC.Res_Channel_Edit_Message) response;

            newMessage = res.newMessage;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            messageVersion = res.messageVersion;
            messageType = res.messageType;
        }

        getMessageDataStorage().updateEditedMessage(roomId, messageId, documentId, messageVersion, messageType, newMessage, isUpdate);
        G.runOnUiThread(() -> getEventManager().postEvent(EventManager.ON_EDIT_MESSAGE, roomId, messageId, newMessage, isUpdate));

    }

    public void pinMessage(long roomId, long messageId, long documentId, int chatType) {
        AbstractObject req = null;

        if (chatType == GROUP_VALUE) {
            IG_RPC.Group_pin_message group_pin_message = new IG_RPC.Group_pin_message();
            group_pin_message.roomId = roomId;
            group_pin_message.messageId = messageId;
            group_pin_message.documentId = documentId;
            req = group_pin_message;
        } else if (chatType == ProtoGlobal.Room.Type.CHANNEL_VALUE) {
            IG_RPC.Channel_pin_message channel_pin_message = new IG_RPC.Channel_pin_message();
            channel_pin_message.messageId = messageId;
            channel_pin_message.documentId = documentId;
            channel_pin_message.roomId = roomId;
            req = channel_pin_message;
        }

        getRequestManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                onPinMessageResponse(response);
            } else {
                IG_RPC.Error err = (IG_RPC.Error) error;
                FileLog.e("Pin message -> Major: " + err.minor + " Minor: " + err.minor);
            }
        });
    }

    private void onPinMessageResponse(AbstractObject response) {
        if (response == null) {
            return;
        }
        long roomId = 0;
        long messageId = 0;
        long documentId = 0;

        if (response instanceof IG_RPC.Group_pin_message_response) {
            IG_RPC.Group_pin_message_response res = (IG_RPC.Group_pin_message_response) response;
            roomId = res.roomId;
            messageId = res.pinnedMessage.getMessageId();
            documentId = res.pinnedMessage.getDocumentId();
        } else if (response instanceof IG_RPC.Channel_pin_message_response) {
            IG_RPC.Channel_pin_message_response res = (IG_RPC.Channel_pin_message_response) response;
            roomId = res.roomId;
            messageId = res.pinnedMessage.getMessageId();
            documentId = res.pinnedMessage.getDocumentId();
        }

        getMessageDataStorage().updatePinnedMessage(roomId, messageId, documentId);
    }

    public void deleteSelectedMessage(int roomType, long roomId, ArrayList<Long> messageIds, ArrayList<Long> documentIds, ArrayList<Long> bothMessageIds) {
        AbstractObject req = null;
        boolean bothDelete = false;

        for (int i = 0; i < messageIds.size(); i++) {

            if (bothMessageIds != null && bothMessageIds.contains(messageIds.get(i))) {
                bothDelete = true;
            }

            if (roomType == CHAT_VALUE) {
                IG_RPC.Chat_Delete_Message chat_delete_message = new IG_RPC.Chat_Delete_Message();
                chat_delete_message.roomId = roomId;
                chat_delete_message.messageId = messageIds.get(i);
                chat_delete_message.documentId = documentIds.get(i);
                chat_delete_message.both = bothDelete;
                req = chat_delete_message;

            } else if (roomType == GROUP_VALUE) {
                IG_RPC.Group_Delete_Message group_delete_message = new IG_RPC.Group_Delete_Message();
                group_delete_message.roomId = roomId;
                group_delete_message.messageId = messageIds.get(i);
                group_delete_message.documentId = documentIds.get(i);
                req = group_delete_message;

            } else if (roomType == ProtoGlobal.Room.Type.CHANNEL_VALUE) {
                IG_RPC.Channel_Delete_Message channel_delete_message = new IG_RPC.Channel_Delete_Message();
                channel_delete_message.roomId = roomId;
                channel_delete_message.messageId = messageIds.get(i);
                channel_delete_message.documentId = documentIds.get(i);
                req = channel_delete_message;

            }

            getRequestManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    onDeleteMessageResponse(response, false);
                } else {
                    IG_RPC.Error e = (IG_RPC.Error) error;
                    FileLog.e("Delete Message -> Major:" + e.major + "Minor:" + e.minor);
                }
            });
        }
    }

    public void onDeleteMessageResponse(AbstractObject response, boolean update) {

        long roomId = 0;
        long messageId = 0;
        long documentId = 0;
        long deleteVersion = 0;

        if (response instanceof IG_RPC.Res_Chat_Delete_Message) {
            IG_RPC.Res_Chat_Delete_Message res = (IG_RPC.Res_Chat_Delete_Message) response;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            deleteVersion = res.deleteVersion;

        } else if (response instanceof IG_RPC.Res_Group_Delete_Message) {
            IG_RPC.Res_Group_Delete_Message res = (IG_RPC.Res_Group_Delete_Message) response;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            deleteVersion = res.deleteVersion;

        } else if (response instanceof IG_RPC.Res_Channel_Delete_Message) {
            IG_RPC.Res_Channel_Delete_Message res = (IG_RPC.Res_Channel_Delete_Message) response;
            roomId = res.roomId;
            messageId = res.messageId;
            documentId = res.documentId;
            deleteVersion = res.deleteVersion;
        }

        getMessageDataStorage().processDeleteMessage(roomId, messageId, deleteVersion, update);
    }

    public void channelAddMessageVote(MessageObject messageObject, int reaction) {
        final IG_RPC.Channel_Add_Message_Reaction req = new IG_RPC.Channel_Add_Message_Reaction();
        req.messageId = messageObject.id;
        req.documentId = messageObject.documentId;
        req.roomId = messageObject.roomId;
        req.reaction = ProtoGlobal.RoomMessageReaction.forNumber(reaction);

        getRequestManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                IG_RPC.Res_Channel_Add_Message_Reaction res = (IG_RPC.Res_Channel_Add_Message_Reaction) response;
                getMessageDataStorage().voteUpdate(req.reaction, req.messageId, res.reactionCounter);
                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.CHANNEL_ADD_VOTE, req.roomId, req.messageId, res.reactionCounter, req.reaction));
            } else {
                IG_RPC.Error e = (IG_RPC.Error) error;
                FileLog.e("Delete Message -> Major" + e.major + "Minor" + e.minor);
            }
        });
    }

    public void ChannelGetMessageVote(MessageObject messageObject, long roomId, HashSet<Long> messageIds) {
        long messageId;

        if (messageObject.forwardedMessage != null) {
            messageId = messageObject.forwardedMessage.id;
            if (messageId < 0)
                messageId = messageId * (-1);
        } else {
            messageId = messageObject.id;
        }

        messageIds.add(messageId);

        final IG_RPC.Channel_Get_Message_Reaction req = new IG_RPC.Channel_Get_Message_Reaction();
        req.roomId = roomId;
        req.messageIds = messageIds;

        getRequestManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                IG_RPC.Res_Channel_Get_Message_Reaction res = (IG_RPC.Res_Channel_Get_Message_Reaction) response;
                RealmChannelExtra.updateMessageStats(res.states);
                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.CHANNEL_GET_VOTE, res.states));
            } else {
                IG_RPC.Error e = (IG_RPC.Error) error;
                FileLog.e("Delete Message -> Major" + e.major + "Minor" + e.minor);
            }
        });
    }

    @Override
    public void receivedEvent(int id, int account, Object... args) {
        if (id == EventManager.FILE_UPLOAD_SUCCESS) {
            String fileId = (String) args[0];
            String fileToken = (String) args[1];

            if (lastUploadedAvatarId != null && lastUploadedAvatarId.equals(fileId)) {
                IG_RPC.Channel_AddAvatar req = new IG_RPC.Channel_AddAvatar();
                req.attachment = fileToken;
                req.roomId = lastUploadedAvatarRoomId;

                getRequestManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        IG_RPC.Res_Channel_Avatar channelAvatar = (IG_RPC.Res_Channel_Avatar) response;
                        updateChannelAvatarInternal(channelAvatar);
                    }
                });
            }
        } else if (id == EventManager.FILE_UPLOAD_FAILED) {
            String fileId = (String) args[0];

            if (lastUploadedAvatarId != null && lastUploadedAvatarId.equals(fileId)) {
                lastUploadedAvatarId = null;
                lastUploadedAvatarRoomId = -1;
            }
        }
    }
}
