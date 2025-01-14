package net.iGap.controllers;

import android.util.Log;

import net.iGap.G;
import net.iGap.helper.DispatchQueue;
import net.iGap.helper.FileLog;
import net.iGap.module.AndroidUtils;
import net.iGap.module.SUID;
import net.iGap.module.TimeUtils;
import net.iGap.module.accountManager.AccountManager;
import net.iGap.module.accountManager.DbManager;
import net.iGap.module.enums.AttachmentFor;
import net.iGap.module.enums.ClientConditionOffline;
import net.iGap.module.enums.ClientConditionVersion;
import net.iGap.module.enums.LocalFileType;
import net.iGap.observers.eventbus.EventManager;
import net.iGap.proto.ProtoGlobal;
import net.iGap.proto.ProtoStoryGetOwnStoryViews;
import net.iGap.proto.ProtoStoryGetStories;
import net.iGap.realm.RealmAttachment;
import net.iGap.realm.RealmAvatar;
import net.iGap.realm.RealmChannelExtra;
import net.iGap.realm.RealmClientCondition;
import net.iGap.realm.RealmOfflineDelete;
import net.iGap.realm.RealmOfflineEdited;
import net.iGap.realm.RealmOfflineListen;
import net.iGap.realm.RealmOfflineSeen;
import net.iGap.realm.RealmRegisteredInfo;
import net.iGap.realm.RealmRoom;
import net.iGap.realm.RealmRoomMessage;
import net.iGap.realm.RealmRoomMessageContact;
import net.iGap.realm.RealmStory;
import net.iGap.realm.RealmStoryProto;
import net.iGap.realm.RealmStoryViewInfo;
import net.iGap.realm.RealmUserInfo;
import net.iGap.request.RequestClientGetRoom;
import net.iGap.request.RequestUserInfo;
import net.iGap.response.UserLoginResponse;
import net.iGap.story.MainStoryObject;
import net.iGap.story.StoryObject;
import net.iGap.story.liststories.StoryFragment;
import net.iGap.structs.AttachmentObject;
import net.iGap.structs.MessageObject;
import net.iGap.structs.RoomContactObject;

import org.stellar.sdk.xdr.Hash;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static net.iGap.proto.ProtoGlobal.RoomMessageStatus.LISTENED;
import static net.iGap.proto.ProtoGlobal.RoomMessageStatus.SEEN;

public class MessageDataStorage extends BaseController {

    private static volatile MessageDataStorage[] instance = new MessageDataStorage[AccountManager.MAX_ACCOUNT_COUNT];
    private DispatchQueue storageQueue = new DispatchQueue("MessageStorage");
    private Realm database;
    private String TAG = getClass().getSimpleName() + " " + currentAccount + " ";
    public final static long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;

    public MessageDataStorage(int currentAccount) {
        super(currentAccount);
        openDatabase();
    }

    public static MessageDataStorage getInstance(int account) {
        MessageDataStorage localInstance = instance[account];
        if (localInstance == null) {
            synchronized (MessageDataStorage.class) {
                localInstance = instance[account];
                if (localInstance == null) {
                    instance[account] = localInstance = new MessageDataStorage(account);
                }
            }
        }
        return localInstance;
    }

    private void openDatabase() {
        storageQueue.postRunnable(() -> {
            try {
                FileLog.i(TAG, "openDatabase: " + AccountManager.getInstance().getCurrentUser().getId() + " path-> " + AccountManager.getInstance().getCurrentUser().getRealmConfiguration());

                if (database == null || database.isClosed()) {
                    database = Realm.getInstance(AccountManager.getInstance().getCurrentUser().getRealmConfiguration());
                }
            } catch (Exception e) {
                FileLog.e(TAG, e);
            }
        });
    }

    public Realm getDatabase() {
        return database;
    }

    public DispatchQueue getStorageQueue() {
        return storageQueue;
    }

    public void processDeleteMessage(long roomId, long messageId, long deleteVersion, boolean update) {
        storageQueue.postRunnable(() -> {
            Log.i(TAG, "processDeleteMessage: roomId " + roomId + " messageId " + messageId + " deleteVersion " + deleteVersion + " update " + update);
            try {
                database.beginTransaction();

                RealmClientCondition realmClientCondition = database.where(RealmClientCondition.class).equalTo("roomId", roomId).findFirst();
                if (realmClientCondition != null) {
                    realmClientCondition.deleteVersion = deleteVersion;
                }

                RealmOfflineDelete offlineDelete = database.where(RealmOfflineDelete.class).equalTo("offlineDelete", messageId).findFirst();
                if (offlineDelete != null) {
                    offlineDelete.deleteFromRealm();
                }

                RealmRoomMessage message = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).findFirst();
                RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();

                if (realmRoom != null && message != null && !message.deleted
                        && !message.isSenderMe()
                        && !message.status.equals(SEEN.toString())
                        && realmRoom.firstUnreadMessage != null
                        && realmRoom.firstUnreadMessage.messageId <= messageId
                        && realmRoom.unreadCount > 0) {

                    realmRoom.unreadCount = realmRoom.unreadCount - 1;

                    if (realmRoom.getUnreadCount() == 0) {
                        realmRoom.firstUnreadMessage = null;
                    }
                }

                if (message != null) {
                    message.deleted = true;
                }

                RealmResults<RealmRoomMessage> replayedMessages = database.where(RealmRoomMessage.class).equalTo("replyTo.messageId", -messageId).findAll();

                if (replayedMessages != null) {
                    for (RealmRoomMessage roomMessage : replayedMessages) {
                        if (roomMessage != null) {
                            roomMessage.replyTo.deleted = true;
                        }
                    }
                }

                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.ON_MESSAGE_DELETE, roomId, messageId, update));

                if (realmRoom != null && realmRoom.lastMessage != null) {
                    if (realmRoom.lastMessage.messageId == messageId) {

                        Number newLastMessageId = database.where(RealmRoomMessage.class)
                                .equalTo("roomId", roomId)
                                .notEqualTo("messageId", messageId)
                                .notEqualTo("deleted", true)
                                .lessThan("messageId", messageId)
                                .max("messageId");

                        if (newLastMessageId != null) {
                            realmRoom.lastMessage = database.where(RealmRoomMessage.class)
                                    .equalTo("messageId", newLastMessageId.longValue())
                                    .findFirst();
                        } else {
                            realmRoom.lastMessage = null;
                        }
                    }
                }
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putUserAvatar(long roomId, ProtoGlobal.Avatar avatar) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "putUserAvatar: " + roomId);
            try {
                database.beginTransaction();
                RealmAvatar realmAvatar = database.where(RealmAvatar.class).equalTo("id", avatar.getId()).findFirst();

                if (realmAvatar == null) {
                    realmAvatar = database.createObject(RealmAvatar.class, avatar.getId());
                }

                realmAvatar.setOwnerId(roomId);
                realmAvatar.setFile(RealmAttachment.build(database, avatar.getFile(), AttachmentFor.AVATAR, null));

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void cleanUp() {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "cleanUp: ");
            try {
                database.close();
                database = null;
            } catch (Exception e) {
                FileLog.e(e);
            }

            openDatabase();
        });
    }

    public void deleteRoomFromStorage(long roomId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteRoomFromStorage: " + roomId);
            try {
                database.beginTransaction();

                RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();

                if (realmRoom != null) {
                    realmRoom.deleteFromRealm();
                }

                database.where(RealmClientCondition.class).equalTo("roomId", roomId).findAll().deleteAllFromRealm();
                database.where(RealmRoomMessage.class).equalTo("roomId", roomId).findAll().deleteAllFromRealm();

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setAttachmentFilePath(String cacheId, String absolutePath, boolean isThumb) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "setAttachmentFilePath: " + cacheId + " " + absolutePath);
            try {
                database.beginTransaction();
                RealmResults<RealmAttachment> attachments = database.where(RealmAttachment.class).equalTo("cacheId", cacheId).findAll();

                for (RealmAttachment attachment : attachments) {
                    if (isThumb) {
                        attachment.setLocalThumbnailPath(absolutePath);
                    } else {
                        attachment.setLocalFilePath(absolutePath);
                    }
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void voteUpdate(ProtoGlobal.RoomMessageReaction messageReaction, long messageId, String voteCount) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();

                RealmChannelExtra realmChannelExtra = database.where(RealmChannelExtra.class).equalTo("messageId", messageId).findFirst();
                if (realmChannelExtra != null) {
                    if (messageReaction == ProtoGlobal.RoomMessageReaction.THUMBS_UP) {
                        realmChannelExtra.setThumbsUp(voteCount);
                    } else {
                        realmChannelExtra.setThumbsDown(voteCount);
                    }
                }

                database.commitTransaction();

            } catch (Exception e) {
                FileLog.e(e);
            }

        });

    }

    private void putLastMessageInternal(final long roomId, RealmRoomMessage lastMessage) {
        try {
            database.beginTransaction();
            RealmRoom room = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();
            if (room != null) {
                room.updatedTime = Math.max(lastMessage.updateTime, lastMessage.createTime);
                room.lastMessage = lastMessage;
            }
            database.commitTransaction();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void resetRoomLastMessage(final long roomId, final long messageId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "resetRoomLastMessage: " + roomId + " " + messageId);
            try {
                if (messageId != 0) {
                    deleteMessage(roomId, messageId, false);
                }

                RealmRoomMessage newMessage = database.where(RealmRoomMessage.class).equalTo("roomId", roomId)
                        .notEqualTo("deleted", true)
                        .sort(new String[]{"messageId"}, new Sort[]{Sort.DESCENDING})
                        .findFirst();

                if (newMessage != null) {
                    putLastMessageInternal(roomId, newMessage);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void deleteMessage(final long roomId, final long messageId, final boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> deleteMessageInternal(roomId, messageId));
        } else {
            deleteMessageInternal(roomId, messageId);
        }
    }

    private void deleteMessageInternal(long roomId, long messageId) {
        FileLog.i(TAG, "deleteMessageInternal: " + roomId + " messageId " + messageId);
        try {
            if (roomId == 0 || messageId == 0) {
                return;
            }

            database.beginTransaction();

            RealmRoomMessage message = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).equalTo("roomId", roomId).findFirst();
            if (message != null) {
                message.deleteFromRealm();
            }

            database.commitTransaction();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String getDisplayNameWithUserId(long userId) {
        FileLog.i(TAG, "getDisplayNameWithUserId: " + userId);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final String[] result = new String[1];

        storageQueue.postRunnable(() -> {
            try {
                RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", userId).findFirst();
                if (realmRegisteredInfo != null) {
                    result[0] = realmRegisteredInfo.getDisplayName();
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }

        return result[0];
    }

    public List<List<String>> getDisplayNameWithUserId(List<Long> userId) {
        FileLog.i(TAG, "getDisplayNameWithUserId: " + userId);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<List<String>> result = new ArrayList<>();

        storageQueue.postRunnable(() -> {
            try {
                for (int i = 0; i < userId.size(); i++) {
                    RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", userId.get(i)).findFirst();
                    if (realmRegisteredInfo != null) {
                        List<String> initializeInfo = new ArrayList<>();
                        initializeInfo.add(realmRegisteredInfo.getDisplayName());
                        initializeInfo.add(realmRegisteredInfo.getColor());
                        result.add(initializeInfo);
                    }

                }
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }

        return result;
    }

    public void putAttachmentToken(final long messageId, final String token) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                RealmAttachment attachment = database.where(RealmAttachment.class).equalTo("id", messageId).findFirst();

                if (attachment != null) {
                    attachment.token = token;
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void deleteRoomAllMessage(final long roomId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteRoomAllMessage: " + roomId);

            try {
                database.beginTransaction();

                RealmResults<RealmRoomMessage> roomMessages = database.where(RealmRoomMessage.class).equalTo("roomId", roomId).findAll();

                if (roomMessages != null && roomMessages.size() > 0) {
                    roomMessages.deleteAllFromRealm();
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateEditedMessage(final long roomId, final long messageId, long documentId, final long messageVersion, final int messageType, final String message, boolean isUpdate) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updateEditedMessage: " + roomId + " " + messageId + " " + message);
            try {
                database.beginTransaction();

                RealmClientCondition realmClientCondition = database.where(RealmClientCondition.class).equalTo("roomId", roomId).findFirst();
                if (realmClientCondition != null) {
                    realmClientCondition.setVersion(messageVersion, ClientConditionVersion.EDIT);
                }

                if (isUpdate) {
                    RealmOfflineEdited offlineEdited = database.where(RealmOfflineEdited.class).equalTo("messageId", messageId).findFirst();
                    if (offlineEdited != null) {
                        offlineEdited.deleteFromRealm();
                    }
                }

                RealmRoomMessage roomMessage = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).findFirst();
                if (roomMessage != null) {
                    roomMessage.setMessage(message);
                    roomMessage.messageVersion = messageVersion;
                    roomMessage.edited = true;
                    roomMessage.setDocumentId(documentId);
                    roomMessage.messageType = ProtoGlobal.RoomMessageType.forNumber(messageType).toString();
                    RealmRoomMessage.addTimeIfNeed(roomMessage, database);

                    switch (roomMessage.getMessageType()) {
                        case IMAGE:
                            roomMessage.messageType = ProtoGlobal.RoomMessageType.IMAGE_TEXT.toString();
                            break;
                        case VIDEO:
                            roomMessage.messageType = ProtoGlobal.RoomMessageType.VIDEO_TEXT.toString();
                            break;
                        case AUDIO:
                            roomMessage.messageType = ProtoGlobal.RoomMessageType.AUDIO_TEXT.toString();
                            break;
                        case GIF:
                            roomMessage.messageType = ProtoGlobal.RoomMessageType.GIF_TEXT.toString();
                            break;
                        case FILE:
                            roomMessage.messageType = ProtoGlobal.RoomMessageType.FILE_TEXT.toString();
                            break;
                    }
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearRoomHistory(final long roomId, final long clearId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "clearRoomHistory: roomId " + roomId + " clearId " + clearId);
            try {
                database.beginTransaction();

                final RealmClientCondition realmClientCondition = database.where(RealmClientCondition.class).equalTo("roomId", roomId).findFirst();
                if (realmClientCondition != null) {
                    realmClientCondition.setClearId(clearId);
                }

                final RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();
                if (realmRoom != null && ((realmRoom.getLastMessage() == null) || (realmRoom.getLastMessage().getMessageId() <= clearId))) {
                    realmRoom.setUnreadCount(0);
                    realmRoom.setLastMessage(null);
                    realmRoom.setFirstUnreadMessage(null);
                    realmRoom.setUpdatedTime(0);
                    realmRoom.setLastScrollPositionMessageId(0);
                    realmRoom.setLastScrollPositionDocumentId(0);
                }

                final RealmResults<RealmRoomMessage> roomMessages = database.where(RealmRoomMessage.class).equalTo("roomId", roomId).lessThanOrEqualTo("messageId", clearId).findAll();

                if (roomMessages != null) {
                    roomMessages.deleteAllFromRealm();
                }

                database.commitTransaction();
                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.CHAT_CLEAR_MESSAGE, roomId, clearId));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updatePinnedMessage(long roomId, long messageId, long documentId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updatePinnedMessage roomId " + roomId + " messageId " + messageId);
            try {
                database.beginTransaction();
                final RealmRoom room = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();

                if (room != null) {
                    room.setPinMessageId(messageId);
                    room.setPinDocumentId(documentId);
                }

                database.commitTransaction();

                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.ON_PINNED_MESSAGE, roomId, messageId));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setRoomClearId(long roomId, long clearMessageId, boolean useQueue) {
        FileLog.i(TAG, "setRoomClearId: roomId " + roomId + " clearMessageId " + clearMessageId + " useQueue " + useQueue);
        if (useQueue) {
            storageQueue.postRunnable(() -> setRoomClearIdInternal(roomId, clearMessageId));
        } else {
            setRoomClearIdInternal(roomId, clearMessageId);
        }
    }

    private void setRoomClearIdInternal(long roomId, long clearMessageId) {
        FileLog.i(TAG, "setRoomClearIdInternal: " + roomId + " " + clearMessageId);
        try {
            database.beginTransaction();
            final RealmClientCondition realmClientCondition = database.where(RealmClientCondition.class).equalTo("roomId", roomId).findFirst();

            if (realmClientCondition != null) {
                realmClientCondition.setClearId(clearMessageId);
            }

            database.commitTransaction();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public RealmRoom getRoom(final long roomId) {
        FileLog.i(TAG, "getRoom: " + roomId);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final RealmRoom[] result = new RealmRoom[1];

        storageQueue.postRunnable(() -> {
            try {
                RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();

                if (realmRoom != null) {
                    result[0] = database.copyFromRealm(realmRoom);
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }

        Log.e(TAG, "getRoom: " + (result[0] != null ? result[0].getTitle() : "NULL"));

        return result[0];
    }

    public long getRoomClearId(final long roomId) {
        FileLog.i(TAG, "getRoomClearId: " + roomId);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final long[] result = new long[1];

        storageQueue.postRunnable(() -> {
            try {
                long clearMessageId = 0;

                RealmResults<RealmRoomMessage> results = database.where(RealmRoomMessage.class).equalTo("roomId", roomId).findAll().sort("messageId", Sort.DESCENDING);
                if (results != null && results.size() > 0) {
                    RealmRoomMessage message = results.first();

                    if (message != null) {
                        clearMessageId = message.getMessageId();
                    }
                }

                result[0] = clearMessageId;
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }

        return result[0];
    }

    public void deleteOfflineAction(final long messageId, final ClientConditionOffline messageStatus, boolean needTransaction, boolean needQueue) {
        if (needQueue) {
            storageQueue.postRunnable(() -> deleteOfflineActionInternal(messageId, messageStatus, needTransaction));
        } else {
            deleteOfflineActionInternal(messageId, messageStatus, needTransaction);
        }
    }

    public void deleteOfflineActionInternal(final long messageId, final ClientConditionOffline messageStatus, boolean needTransaction) {
        FileLog.i(TAG, "deleteOfflineActionInternal: " + messageId + " " + messageStatus.name());

        try {
            if (needTransaction) {
                database.beginTransaction();
            }
            if (messageStatus == ClientConditionOffline.DELETE) {
                RealmOfflineDelete offlineDelete = database.where(RealmOfflineDelete.class).equalTo("offlineDelete", messageId).findFirst();
                if (offlineDelete != null) {
                    offlineDelete.deleteFromRealm();
                }
            } else if (messageStatus == ClientConditionOffline.EDIT) {
                RealmOfflineEdited offlineEdited = database.where(RealmOfflineEdited.class).equalTo("messageId", messageId).findFirst();
                if (offlineEdited != null) {
                    offlineEdited.deleteFromRealm();
                }
            } else if (messageStatus == ClientConditionOffline.SEEN) {
                RealmOfflineSeen offlineSeen = database.where(RealmOfflineSeen.class).equalTo("offlineSeen", messageId).findFirst();
                if (offlineSeen != null) {
                    offlineSeen.deleteFromRealm();
                }
            } else if (messageStatus == ClientConditionOffline.LISTEN) {
                RealmOfflineListen offlineListen = database.where(RealmOfflineListen.class).equalTo("offlineListen", messageId).findFirst();
                if (offlineListen != null) {
                    offlineListen.deleteFromRealm();
                }
            }
            if (needTransaction) {
                database.commitTransaction();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

    }

    public void updateMessageStatus(long roomId, long messageId, long documentId, String updaterAuthorHash, ProtoGlobal.RoomMessageStatus messageStatus, long statusVersion, boolean update) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updateRoomStatusStatus: " + roomId + " " + messageId + " " + updaterAuthorHash + " " + messageStatus.name() + " " + statusVersion + " " + update);
            try {
                database.beginTransaction();
                if (!update) {
                    if (messageStatus == SEEN) {
                        deleteOfflineAction(messageId, ClientConditionOffline.SEEN, false, false);
                    } else if (messageStatus == LISTENED) {
                        deleteOfflineAction(messageId, ClientConditionOffline.LISTEN, false, false);
                    }
                } else {
                    String currentUserAuthorHash = "";
                    RealmUserInfo realmUser = database.where(RealmUserInfo.class).findFirst();
                    if (realmUser != null) {
                        currentUserAuthorHash = realmUser.getAuthorHash();
                    }

                    if (currentUserAuthorHash.equals(updaterAuthorHash) && messageStatus == SEEN) {
                        RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();

                        if (realmRoom != null && (realmRoom.getLastMessage() != null && realmRoom.getLastMessage().getMessageId() <= messageId)) {
                            realmRoom.setUnreadCount(0);
                        }
                    }

                    RealmRoomMessage roomMessage;
                    if (messageStatus != LISTENED) {
                        roomMessage = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).notEqualTo("status", SEEN.toString()).notEqualTo("status", LISTENED.toString()).findFirst();
                    } else {
                        roomMessage = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).findFirst();
                    }

                    if (roomMessage != null) {
                        roomMessage.setStatus(messageStatus.toString());
                        roomMessage.setStatusVersion(statusVersion);
                        roomMessage.setDocumentId(documentId);
                        database.copyToRealmOrUpdate(roomMessage);
                    }

                    if (roomMessage != null || messageStatus == SEEN) {
                        G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.CHAT_UPDATE_STATUS, roomId, messageId, messageStatus));
                    }
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setOfflineSeen(final long roomId, final long messageId, boolean needTransaction, boolean needQueue) {
        if (needQueue) {
            storageQueue.postRunnable(() -> setOfflineSeenInternal(roomId, messageId, needTransaction));
        } else {
            setOfflineSeenInternal(roomId, messageId, needTransaction);
        }
    }

    private void setOfflineSeenInternal(final long roomId, final long messageId, boolean needTransaction) {
        FileLog.i(TAG, "addOfflineSeen: " + roomId + " " + messageId);
        try {
            if (needTransaction) {
                database.beginTransaction();
            }

            RealmClientCondition realmClientCondition = database.where(RealmClientCondition.class).equalTo("roomId", roomId).findFirst();
            if (realmClientCondition != null) {
                realmClientCondition.getOfflineSeen().add(RealmOfflineSeen.put(database, messageId));
            }

            if (needTransaction) {
                database.commitTransaction();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }


    public void setStatusSeenInChat(final long roomId, final long messageId) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "setStatusSeenInChat: " + messageId);
            try {
                database.beginTransaction();
                setOfflineSeen(roomId, messageId, false, false);
                // setOfflineSeenInternal(roomId, messageId, false);

                RealmRoomMessage realmRoomMessage = database.where(RealmRoomMessage.class).equalTo("messageId", messageId).notEqualTo("status", SEEN.toString()).notEqualTo("status", LISTENED.toString()).findFirst();
                if (realmRoomMessage != null) {
                    if (!realmRoomMessage.getStatus().equalsIgnoreCase(SEEN.toString())) {
                        realmRoomMessage.setStatus(SEEN.toString());
                    }
                }

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

    }

    public void deleteFileFromStorage(MessageObject message, DatabaseDelegate delegate) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteFileFromStorage: " + message.id);
            try {
                database.beginTransaction();
                RealmAttachment attachment = database.where(RealmAttachment.class).equalTo("token", message.attachment.token).findFirst();

                if (attachment != null) {
                    attachment.setLocalFilePath("");
                }

                database.commitTransaction();
                G.runOnUiThread(() -> delegate.run(null));

            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void createForwardMessage(final long destinationRoomId, final long newMessageId, MessageObject sourceMessage, boolean isMessage, DatabaseDelegate databaseDelegate) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "createForwardMessage: " + destinationRoomId + " " + newMessageId);
            try {
                final RealmRoomMessage[] forwardedMessage = new RealmRoomMessage[1];
                MessageObject messageObject = null;
                RealmRoomMessage copyMessage = null;
                database.beginTransaction();

                if (isMessage && sourceMessage.forwardedMessage == null) {

                    forwardedMessage[0] = database.createObject(RealmRoomMessage.class, newMessageId);
                    forwardedMessage[0].setCreateTime(TimeUtils.currentLocalTime());
                    forwardedMessage[0].setRoomId(destinationRoomId);
                    forwardedMessage[0].setStatus(ProtoGlobal.RoomMessageStatus.SENDING.toString());
                    forwardedMessage[0].setMessageType(ProtoGlobal.RoomMessageType.forNumber(sourceMessage.messageType));
                    forwardedMessage[0].setMessage(sourceMessage.message);
                    if (sourceMessage.attachment != null) {
                        AttachmentObject attObject = sourceMessage.attachment;
                        LocalFileType type = attObject.filePath == null ? LocalFileType.THUMBNAIL : LocalFileType.FILE;
                        String filePath = attObject.filePath != null ? attObject.filePath : attObject.thumbnailPath;
                        forwardedMessage[0].setAttachment(newMessageId, filePath, attObject.width, attObject.height, attObject.size, attObject.name, attObject.duration, attObject.token, type);
                    }
                    if (sourceMessage.contact != null) {
                        RoomContactObject contactObject = sourceMessage.contact;
                        ProtoGlobal.RoomMessageContact.Builder builder = ProtoGlobal.RoomMessageContact.newBuilder();
                        builder.setPhone(0, contactObject.phones.get(0));
                        builder.setFirstName(contactObject.firstName);
                        builder.setLastName(contactObject.lastName);
                        builder.setEmail(0, contactObject.emails.get(0));

                        RealmRoomMessageContact roomMessageContact = RealmRoomMessageContact.put(database, builder.build());
                        forwardedMessage[0].setRoomMessageContact(roomMessageContact);
                    }
                    forwardedMessage[0].setUserId(AccountManager.getInstance().getCurrentUser().getId());
                    database.copyToRealmOrUpdate(forwardedMessage[0]);
                    copyMessage = database.copyFromRealm(forwardedMessage[0]);
                } else {
                    RealmRoomMessage roomMessage = database.where(RealmRoomMessage.class).equalTo("messageId", sourceMessage.id).findFirst();

                    if (roomMessage != null) {
                        forwardedMessage[0] = database.createObject(RealmRoomMessage.class, newMessageId);
                        if (roomMessage.getForwardMessage() != null) {
                            forwardedMessage[0].setForwardMessage(roomMessage.getForwardMessage());
                            forwardedMessage[0].setHasMessageLink(roomMessage.getForwardMessage().getHasMessageLink());
                        } else {
                            forwardedMessage[0].setForwardMessage(roomMessage);
                            forwardedMessage[0].setHasMessageLink(roomMessage.getHasMessageLink());
                        }
                        forwardedMessage[0].setCreateTime(TimeUtils.currentLocalTime());
                        forwardedMessage[0].setMessageType(ProtoGlobal.RoomMessageType.TEXT);
                        forwardedMessage[0].setRoomId(destinationRoomId);
                        forwardedMessage[0].setStatus(ProtoGlobal.RoomMessageStatus.SENDING.toString());
                        forwardedMessage[0].setUserId(AccountManager.getInstance().getCurrentUser().getId());

                        copyMessage = database.copyFromRealm(forwardedMessage[0]);
                    }
                }
                messageObject = MessageObject.create(copyMessage);
                RealmRoomMessage realmSourceMessage = database.where(RealmRoomMessage.class).equalTo("messageId", sourceMessage.id).findFirst();
                assert realmSourceMessage != null;
                RealmRoomMessage copyOfSource = database.copyFromRealm(realmSourceMessage);
                database.commitTransaction();

                MessageObject finalMessageObject = messageObject;
                RealmRoomMessage finalCopyMessage = copyMessage;
                G.runOnUiThread(() -> {
                    databaseDelegate.run(finalMessageObject, finalCopyMessage, copyOfSource.getRoomId(), copyOfSource.getMessageId());
                });

            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateOwnViews(List<ProtoStoryGetOwnStoryViews.GroupedViews> groupedViews) {
        CountDownLatch countdown = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            try {
                database.executeTransaction(realm -> {
                    int counter = 0;
                    for (int i = 0; i < groupedViews.size(); i++) {
//                        for (int j = 0; j < groupedViews.get(i).getStoryViewsList().size(); j++) {
//                            if (groupedViews.get(i).getStoryViewsList().get(j).getUserId() != AccountManager.getInstance().getCurrentUser().getId()) {
//                                counter++;
//                            }
//                        }

                        RealmStoryProto realmStoryProto = realm.where(RealmStoryProto.class).equalTo("isForReply", false).equalTo("storyId", groupedViews.get(i).getStoryId()).findFirst();
                        if (realmStoryProto != null) {
                            //  realmStoryProto.setViewCount(counter);
                            boolean isExist = false;
                            for (int j = 0; j < groupedViews.get(i).getStoryViewsList().size(); j++) {
                                RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", groupedViews.get(i).getStoryViewsList().get(j).getUserId()).findFirst();
                                RealmStoryViewInfo realmStoryViewInfo;
                                realmStoryViewInfo = realm.where(RealmStoryViewInfo.class).equalTo("id", groupedViews.get(i).getStoryId()).equalTo("userId", groupedViews.get(i).getStoryViewsList().get(j).getUserId()).findFirst();
                                if (realmStoryViewInfo == null) {
                                    realmStoryViewInfo = realm.createObject(RealmStoryViewInfo.class);
                                } else {
                                    isExist = true;
                                }
                                if (realmRegisteredInfo == null) {
                                    realmStoryViewInfo.setDisplayName("");
                                    new RequestUserInfo().userInfo(groupedViews.get(i).getStoryViewsList().get(j).getUserId());
                                } else {
                                    realmStoryViewInfo.setDisplayName(realmRegisteredInfo.getDisplayName());
                                    realmStoryViewInfo.setProfileColor(realmRegisteredInfo.getColor());
                                }

                                realmStoryViewInfo.setId(groupedViews.get(i).getStoryId());
                                realmStoryViewInfo.setUserId(groupedViews.get(i).getStoryViewsList().get(j).getUserId());
                                realmStoryViewInfo.setCreatedTime(groupedViews.get(i).getStoryViewsList().get(j).getViewedAt());
                                if (isExist) {
                                    realmStoryProto.getRealmStoryViewInfos().remove(realmStoryViewInfo);
                                }
                                realmStoryProto.getRealmStoryViewInfos().add(realmStoryViewInfo);
                                isExist = false;
                            }
                        }
                        counter = 0;
                    }
                });
                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.STORY_VIEWS_FETCHED));

                countdown.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countdown.countDown();
            }
        });

        try {
            countdown.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void updateUserAddedStoryWithStoryObjects(final List<ProtoStoryGetStories.GroupedStories> stories) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                List<StoryObject> storyObjects = new ArrayList<>();

                if (stories.size() > 0 && stories.size() >= database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll().size()) {
                    for (int i = 0; i < stories.size(); i++) {
                        if (stories.get(i).getStoriesList().size() > 0) {
                            RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", stories.get(i).getOriginatorId()).findFirst();
                            boolean isForRoom = false;
                            RealmRoom realmRoom = null;
                            for (int j = 0; j < stories.get(i).getStoriesList().size(); j++) {
                                if (stories.get(i).getStoriesList().get(j).getRoomId() != 0) {
                                    realmRoom = database.where(RealmRoom.class).equalTo("id", stories.get(i).getStoriesList().get(j).getRoomId()).findFirst();
                                    isForRoom = true;
                                    if (realmRoom == null) {
                                        new RequestClientGetRoom().clientGetRoom(stories.get(i).getStoriesList().get(j).getRoomId(), RequestClientGetRoom.CreateRoomMode.justInfo);
                                    }
                                } else {
                                    isForRoom = false;
                                }
                                storyObjects.add(StoryObject.create(stories.get(i).getStoriesList().get(j), j, stories.get(i).getSelf() && isForRoom && realmRoom != null ? realmRoom.getTitle() : stories.get(i).getOriginatorName(), !stories.get(i).getSelf() && isForRoom ? realmRoom != null ? realmRoom.getColor() : "#4aca69" : realmRegisteredInfo != null ? realmRegisteredInfo.getColor() : "#4aca69", isForRoom, isForRoom && realmRoom != null && realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL && realmRoom.getChannelRoom().isVerified()));
                            }
                            putStoriesToDatabase(database, stories.get(i).getSeenAllGroupStories(), stories.get(i).getOriginatorId(), storyObjects, stories.get(i).getOriginatorName(), realmRegisteredInfo != null ? realmRegisteredInfo.getColor() : "#4aca69", stories.get(i).getOriginatorValue(), !stories.get(i).getSelf() && stories.get(i).getOriginatorValue() == 1 && (realmRoom != null && realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL) && realmRoom.getChannelRoom().isVerified());
                            storyObjects.removeAll(storyObjects);
                        } else {
                            RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", stories.get(i).getOriginatorId()).findFirst();
                            if (realmStory != null) {
                                realmStory.deleteFromRealm();
                            }
                        }
                    }
                } else if (stories.size() != 0 && stories.size() < database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll().size()) {

                    boolean isExist = false;
                    List<RealmStory> realmStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll();
                    if (realmStories != null && realmStories.size() > 0) {
                        for (int i = 0; i < realmStories.size(); i++) {
                            for (int j = 0; j < stories.size(); j++) {
                                if (stories.get(j).getOriginatorValue() == 0) {
                                    if (realmStories.get(i).getUserId() == stories.get(j).getOriginatorId()) {
                                        isExist = true;
                                        break;
                                    }
                                } else {
                                    if (realmStories.get(i).getRoomId() == stories.get(j).getOriginatorId()) {
                                        isExist = true;
                                        break;
                                    }
                                }

                            }
                            if (!isExist) {
                                long userId = realmStories.get(i).getUserId();
                                long roomId = realmStories.get(i).getRoomId();
                                int orginatorValue = realmStories.get(i).getOrginatorValue();
                                RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(realmStories.get(i).getOrginatorValue() == 0 ? "userId" : "roomId", realmStories.get(i).getOrginatorValue() == 0 ? userId : roomId).findFirst();
                                if (realmStory != null && realmStory.isSentAll()) {
                                    realmStory.deleteFromRealm();
                                }
                                RealmResults<RealmStoryProto> realmStoryProtos = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo(orginatorValue == 0 ? "userId" : "roomId", userId).equalTo("status", MessageObject.STATUS_SENT).findAll();
                                if (realmStoryProtos != null && realmStoryProtos.size() > 0) {

                                    for (int y = 0; y < realmStoryProtos.size(); y++) {
                                        if (realmStoryProtos.get(y).getFile() != null) {
                                            String filepath = realmStoryProtos.get(y).getFile().getLocalFilePath() != null ? realmStoryProtos.get(y).getFile().getLocalFilePath() : AndroidUtils.getFilePathWithCashId(realmStoryProtos.get(y).getFile().getCacheId(), realmStoryProtos.get(y).getFile().getName(), ProtoGlobal.RoomMessageType.STORY);
                                            if (filepath != null) {
                                                File file = new File(filepath);
                                                if (file.exists()) file.delete();
                                            }
                                        }
                                    }


                                    realmStoryProtos.deleteAllFromRealm();
                                }
                            }
                            isExist = false;
                        }
                    }
                    for (int i = 0; i < stories.size(); i++) {

                        if (stories.get(i).getStoriesList().size() > 0) {
                            RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", stories.get(i).getOriginatorId()).findFirst();
                            boolean isForRoom = false;
                            RealmRoom realmRoom = null;
                            for (int j = 0; j < stories.get(i).getStoriesList().size(); j++) {
                                if (stories.get(i).getStoriesList().get(j).getRoomId() != 0) {
                                    realmRoom = database.where(RealmRoom.class).equalTo("id", stories.get(i).getStoriesList().get(j).getRoomId()).findFirst();
                                    isForRoom = true;
                                    if (realmRoom == null) {
                                        new RequestClientGetRoom().clientGetRoom(stories.get(i).getStoriesList().get(j).getRoomId(), RequestClientGetRoom.CreateRoomMode.justInfo);
                                    }
                                } else {
                                    isForRoom = false;
                                }
                                storyObjects.add(StoryObject.create(stories.get(i).getStoriesList().get(j), j, stories.get(i).getSelf() && isForRoom && realmRoom != null ? realmRoom.getTitle() : stories.get(i).getOriginatorName(), !stories.get(i).getSelf() && isForRoom ? realmRoom != null ? realmRoom.getColor() : "#4aca69" : realmRegisteredInfo != null ? realmRegisteredInfo.getColor() : "#4aca69", isForRoom, isForRoom && realmRoom != null && realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL && realmRoom.getChannelRoom().isVerified()));
                            }
                            putStoriesToDatabase(database, stories.get(i).getSeenAllGroupStories(), stories.get(i).getOriginatorId(), storyObjects, stories.get(i).getOriginatorName(), realmRegisteredInfo != null ? realmRegisteredInfo.getColor() : "#4aca69", stories.get(i).getOriginatorValue(), !stories.get(i).getSelf() && stories.get(i).getOriginatorValue() == 1 && (realmRoom != null && realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL) && realmRoom.getChannelRoom().isVerified());
                            storyObjects.removeAll(storyObjects);
                        } else {
                            RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", stories.get(i).getOriginatorId()).findFirst();
                            if (realmStory != null) {
                                realmStory.deleteFromRealm();
                            }
                        }

                    }

                } else if (stories.size() == 0) {
                    RealmResults<RealmStoryProto> realmStoryProtos = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", MessageObject.STATUS_SENT).findAll();

                    if (realmStoryProtos != null && realmStoryProtos.size() > 0) {

                        for (int y = 0; y < realmStoryProtos.size(); y++) {
                            if (realmStoryProtos.get(y).getFile() != null) {
                                String filepath = realmStoryProtos.get(y).getFile().getLocalFilePath() != null ? realmStoryProtos.get(y).getFile().getLocalFilePath() : AndroidUtils.getFilePathWithCashId(realmStoryProtos.get(y).getFile().getCacheId(), realmStoryProtos.get(y).getFile().getName(), ProtoGlobal.RoomMessageType.STORY);
                                if (filepath != null) {
                                    File file = new File(filepath);
                                    if (file.exists()) file.delete();
                                }
                            }
                        }


                        realmStoryProtos.deleteAllFromRealm();
                    }


                    List<RealmStory> realmStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll();
                    if (realmStories != null && realmStories.size() > 0) {
                        for (int i = 0; i < realmStories.size(); i++) {
                            if (realmStories.get(i).getRealmStoryProtos().size() == 0) {
                                database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", realmStories.get(i).getUserId()).findAll().deleteAllFromRealm();
                            }
                        }
                    }

                }

                int[] storyUnReadCount = new int[1];
                RealmResults<RealmStory> otherStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isSeenAll", false).findAll();
                if (otherStories != null && otherStories.size() > 0) {
                    storyUnReadCount[0] = otherStories.size();
                } else {
                    storyUnReadCount[0] = 0;
                }
                database.commitTransaction();

                UserLoginResponse.isFetched = true;

                G.runOnUiThread(() -> {
                    if (G.onUnreadChange != null) {
                        G.onUnreadChange.onChange(storyUnReadCount[0], true);
                    }
                    StoryFragment.storyListFetched = false;
                    getEventManager().postEvent(EventManager.STORY_LIST_FETCHED);
                });

            } catch (Exception e) {
                Log.e("Fskhfjksdhjkshdf", "updateUserAddedStoryWithStoryObjects: " + "/" + e.getMessage());
                FileLog.e(e);
            }
        });

    }

    public void deleteExpiredStories() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmResults<RealmStoryProto> realmStoryProtos = realm.where(RealmStoryProto.class).lessThan("createdAt", System.currentTimeMillis() - MILLIS_PER_DAY).equalTo("status", MessageObject.STATUS_SENT).findAll();
                    if (realmStoryProtos != null && realmStoryProtos.size() > 0) {
                        for (int i = 0; i < realmStoryProtos.size(); i++) {
                            if (realmStoryProtos.get(i).getFile() != null) {
                                String filepath = realmStoryProtos.get(i).getFile().getLocalFilePath() != null ? realmStoryProtos.get(i).getFile().getLocalFilePath() : AndroidUtils.getFilePathWithCashId(realmStoryProtos.get(i).getFile().getCacheId(), realmStoryProtos.get(i).getFile().getName(), ProtoGlobal.RoomMessageType.STORY);
                                if (filepath != null) {
                                    File file = new File(filepath);
                                    if (file.exists()) file.delete();
                                }
                            }
                        }

                        realmStoryProtos.deleteAllFromRealm();
                    }
                    RealmResults<RealmStory> realmStories = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll();
                    if (realmStories != null && realmStories.size() > 0) {
                        for (RealmStory realmStory : realmStories) {
                            if (realmStory != null && realmStory.getRealmStoryProtos().size() == 0) {
                                realmStory.deleteFromRealm();
                            }
                        }
                    }

                });
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public List<StoryObject> getStoryWithIndexSort(long userId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<StoryObject> storyObjects = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                List<RealmStoryProto> realmStories = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("userId", userId).findAll().sort("index", Sort.DESCENDING);

                if (realmStories != null && realmStories.size() > 0) {
                    for (int i = 0; i < realmStories.size(); i++) {
                        storyObjects.add(StoryObject.create(realmStories.get(i)));
                    }
                }
                database.commitTransaction();

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }

        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return storyObjects;
    }

    public RealmAttachment createRealmObject(String path, int[] imageDimens, long attachementId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RealmAttachment[] realmAttachment = new RealmAttachment[1];
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                realmAttachment[0] = database.createObject(RealmAttachment.class, attachementId);
                realmAttachment[0].setLocalFilePath(path);
                realmAttachment[0].setWidth(imageDimens[0]);
                realmAttachment[0].setHeight(imageDimens[1]);
                realmAttachment[0].setSize(new File(path).length());
                realmAttachment[0].setName(new File(path).getName());
                database.commitTransaction();
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }

        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return realmAttachment[0];
    }

    public void putStoriesToDatabaseOffline(boolean isSeenAll, long userId, long roomId, List<StoryObject> stories, String displayName, boolean isForRoom) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            try {

                database.beginTransaction();
                RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(isForRoom ? "roomId" : "userId", isForRoom ? roomId : userId).findFirst();
                if (realmStory == null) {
                    realmStory = database.createObject(RealmStory.class, SUID.id().get());
                    realmStory.setSeenAll(false);
                }

                realmStory.setDisplayName(displayName);
                realmStory.setSessionId(AccountManager.getInstance().getCurrentUser().getId());
                realmStory.setUserId(userId);
                realmStory.setSeenAll(isSeenAll);
//                realmStory.setRoomId(roomId);
                realmStory.setRealmStoryProtos(database, stories);
                database.commitTransaction();

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void putStoriesToDatabase(Realm database, boolean isSeenAll, long userId, List<StoryObject> stories, String displayName, String profileColor, int orginatorValue, boolean isVerified) {
        try {
            RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(orginatorValue == 0 ? "userId" : "roomId", userId).findFirst();
            if (realmStory == null) {
                realmStory = database.createObject(RealmStory.class, SUID.id().get());
                realmStory.setSeenAll(false);
            } else {
                boolean isExist = false;
                for (int i = 0; i < realmStory.getRealmStoryProtos().size(); i++) {
                    for (int j = 0; j < stories.size(); j++) {
                        if (realmStory.getRealmStoryProtos().get(i).getStoryId() == stories.get(j).storyId) {
                            isExist = true;
                            break;
                        }
                    }
                    if (!isExist && realmStory.getRealmStoryProtos().get(i).getStoryId() != 0) {
                        database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", MessageObject.STATUS_SENT).equalTo("storyId", realmStory.getRealmStoryProtos().get(i).getStoryId()).findAll().deleteAllFromRealm();
                        RealmStory userStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                        if (userStory != null && userStory.getRealmStoryProtos().size() == 0) {
                            userStory.deleteFromRealm();
                        } else if (userStory != null && userStory.getIndexOfSeen() > stories.size()) {
                            userStory.setIndexOfSeen(0);
                        }
                    }
                    isExist = false;
                }
            }

            realmStory.setProfileColor(profileColor);
            realmStory.setVerified(isVerified);
            realmStory.setLastCreatedAt(stories.get(stories.size() - 1).createdAt);
            realmStory.setDisplayName(displayName);
            realmStory.setOrginatorValue(orginatorValue);
            realmStory.setSessionId(AccountManager.getInstance().getCurrentUser().getId());
            if (orginatorValue == 0) {
                realmStory.setUserId(userId);
            } else {
                realmStory.setRoomId(userId);
            }
            realmStory.setSeenAll(isSeenAll);
            realmStory.setRealmStoryProtos(database, stories);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }


    public void updateUserAddedStory(final List<ProtoGlobal.Story> stories) {


        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updateUserAddedStory userId " + stories.get(0).getUserId() + " storiesId " + stories.get(0).getId());
            try {
                if (stories.get(0).getTypeValue() == 0) {
                    database.beginTransaction();
                    RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", stories.get(0).getUserId()).findFirst();
                    if (realmRegisteredInfo != null && realmRegisteredInfo.getDisplayName() != null) {
                        RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", stories.get(0).getUserId()).findFirst();
                        if (realmStory == null) {
                            realmStory = database.createObject(RealmStory.class, SUID.id().get());
                        }
                        List<StoryObject> storyObjects = new ArrayList<>();
                        for (int i = 0; i < stories.size(); i++) {
                            if (stories.get(i).getTypeValue() == 0) {
                                storyObjects.add(StoryObject.create(stories.get(i), i, realmRegisteredInfo.getDisplayName(), realmRegisteredInfo != null ? realmRegisteredInfo.getColor() : "#4aca69", false, false));
                            }
                        }

                        realmStory.setLastCreatedAt(storyObjects.get(storyObjects.size() - 1).createdAt);
                        realmStory.setDisplayName(realmRegisteredInfo.getDisplayName());
                        realmStory.setProfileColor(realmRegisteredInfo.getColor());
                        realmStory.setSessionId(AccountManager.getInstance().getCurrentUser().getId());
                        realmStory.setUserId(stories.get(0).getUserId());
                        realmStory.setOrginatorValue(0);
                        realmStory.setSeenAll(false);
                        realmStory.setRealmStoryProtos(database, storyObjects);

                        int[] storyUnReadCount = new int[1];
                        RealmResults<RealmStory> otherStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isSeenAll", false).findAll();
                        if (otherStories != null && otherStories.size() > 0) {
                            storyUnReadCount[0] = otherStories.size();
                        } else {
                            storyUnReadCount[0] = 0;
                        }
                        database.commitTransaction();

                        G.runOnUiThread(() -> {
                            if (G.onUnreadChange != null) {
                                G.onUnreadChange.onChange(storyUnReadCount[0], true);
                            }
                            getEventManager().postEvent(EventManager.STORY_USER_ADD_NEW);
                        });
                    } else {
                        new RequestUserInfo().userInfo(stories.get(0).getUserId());
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });


    }


    public void updateRoomAddedStory(final List<ProtoGlobal.Story> stories) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updateRoomAddedStory roomId " + stories.get(0).getRoomId() + " storiesId " + stories.get(0).getId());
            try {

                database.beginTransaction();
                RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", stories.get(0).getRoomId()).findFirst();
                if (realmRoom != null && realmRoom.getTitle() != null) {
                    RealmStory realmStory;
                    if (stories.get(0).getUserId() == AccountManager.getInstance().getCurrentUser().getId()) {
                        realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", stories.get(0).getUserId()).findFirst();
                    } else {
                        realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("roomId", stories.get(0).getRoomId()).findFirst();
                    }


                    if (realmStory == null) {
                        realmStory = database.createObject(RealmStory.class, SUID.id().get());
                    }
                    List<StoryObject> storyObjects = new ArrayList<>();
                    for (int i = 0; i < stories.size(); i++) {
                        if (stories.get(i).getTypeValue() == 0) {
                            StoryObject storyObject = StoryObject.create(stories.get(i), i, realmRoom.getTitle(), realmRoom != null ? realmRoom.getColor() : "#4aca69", true, realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL && realmRoom.getChannelRoom().isVerified());
                            storyObjects.add(storyObject);
                        }
                    }

                    realmStory.setLastCreatedAt(storyObjects.get(storyObjects.size() - 1).createdAt);
                    realmStory.setDisplayName(realmRoom.getTitle());
                    realmStory.setProfileColor(realmRoom.getColor());
                    realmStory.setSessionId(AccountManager.getInstance().getCurrentUser().getId());
                    if (stories.get(0).getUserId() != AccountManager.getInstance().getCurrentUser().getId()) {
                        realmStory.setUserId(0);
                        realmStory.setOrginatorValue(1);
                        realmStory.setVerified(realmRoom.getType() == ProtoGlobal.Room.Type.CHANNEL && realmRoom.getChannelRoom().isVerified());
                    } else {
                        realmStory.setUserId(AccountManager.getInstance().getCurrentUser().getId());
                    }
                    realmStory.setRoomId(stories.get(0).getRoomId());
                    realmStory.setSeenAll(false);
                    realmStory.setRealmStoryProtos(database, storyObjects);

                    int[] storyUnReadCount = new int[1];
                    RealmResults<RealmStory> otherStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isSeenAll", false).findAll();
                    if (otherStories != null && otherStories.size() > 0) {
                        storyUnReadCount[0] = otherStories.size();
                    } else {
                        storyUnReadCount[0] = 0;
                    }
                    database.commitTransaction();

                    G.runOnUiThread(() -> {
                        if (G.onUnreadChange != null) {
                            G.onUnreadChange.onChange(storyUnReadCount[0], true);
                        }
                        getEventManager().postEvent(EventManager.STORY_USER_ADD_NEW);
                    });
                } else {
                    new RequestClientGetRoom().clientGetRoom(stories.get(0).getRoomId(), RequestClientGetRoom.CreateRoomMode.justInfo);
                }

            } catch (Exception e) {
                FileLog.e(e);
            }
        });


    }

    public void deleteUserStoryWithStoryId(long storyId, long userId) {

        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteUserStoryId " + storyId);
            try {
                database.beginTransaction();
                int counter = 0;
                boolean[] isFromRoomMode = new boolean[1];
                RealmStoryProto realmStoryProto = database.where((RealmStoryProto.class)).equalTo("isForReply", false).equalTo("storyId", storyId).findFirst();
                if (realmStoryProto != null) {
                    isFromRoomMode[0] = realmStoryProto.isForRoom();
                    if (realmStoryProto.getFile() != null) {
                        String filepath = realmStoryProto.getFile().getLocalFilePath() != null ? realmStoryProto.getFile().getLocalFilePath() : AndroidUtils.getFilePathWithCashId(realmStoryProto.getFile().getCacheId(), realmStoryProto.getFile().getName(), ProtoGlobal.RoomMessageType.STORY);
                        if (filepath != null) {
                            File file = new File(filepath);
                            if (file.exists()) file.delete();
                        }
                    }
                    realmStoryProto.deleteFromRealm();
                }
                RealmStory userStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                if (userStory != null && userStory.getRealmStoryProtos() != null &&
                        userStory.getRealmStoryProtos().size() == 0) {
                    userStory.deleteFromRealm();
                } else if (userStory != null && userStory.getRealmStoryProtos() != null && userStory.getRealmStoryProtos().size() > 0) {
                    userStory.setLastCreatedAt(userStory.getRealmStoryProtos().get(userStory.getRealmStoryProtos().size() - 1).getCreatedAt() / 1000L);
                    for (int i = 0; i < userStory.getRealmStoryProtos().size(); i++) {
                        if (userStory.getRealmStoryProtos().get(i).isSeen()) {
                            counter++;
                        }
                    }

                    if (counter == userStory.getRealmStoryProtos().size()) {
                        userStory.setSeenAll(true);
                    }

                    if (userStory.getIndexOfSeen() > userStory.getRealmStoryProtos().size()) {
                        userStory.setIndexOfSeen(0);
                    }
                    counter = 0;
                }


                int[] storyUnReadCount = new int[1];
                RealmResults<RealmStory> otherStories = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isSeenAll", false).findAll();
                if (otherStories != null && otherStories.size() > 0) {
                    storyUnReadCount[0] = otherStories.size();
                } else {
                    storyUnReadCount[0] = 0;
                }
                database.commitTransaction();

                G.runOnUiThread(() -> {
                    if (G.onUnreadChange != null) {
                        G.onUnreadChange.onChange(storyUnReadCount[0], true);
                    }
                    getEventManager().postEvent(EventManager.STORY_DELETED, isFromRoomMode[0]);
                });

            } catch (Exception e) {
                FileLog.e(e);
            }
        });


    }

    public void deleteUserStoryWithUploadId(long uploadId, long userId) {


        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteUserStoryId " + uploadId);
            try {
                database.beginTransaction();
                boolean[] isFromRoomMode = new boolean[1];
                RealmStoryProto realmStoryProto = database.where((RealmStoryProto.class)).equalTo("isForReply", false).equalTo("id", uploadId).findFirst();

                if (realmStoryProto != null) {
                    isFromRoomMode[0] = realmStoryProto.isForRoom();
                    if (realmStoryProto.getFile() != null) {
                        String filepath = realmStoryProto.getFile().getLocalFilePath() != null ? realmStoryProto.getFile().getLocalFilePath() : AndroidUtils.getFilePathWithCashId(realmStoryProto.getFile().getCacheId(), realmStoryProto.getFile().getName(), ProtoGlobal.RoomMessageType.STORY);
                        if (filepath != null) {
                            File file = new File(filepath);
                            if (file.exists()) file.delete();
                        }
                    }

                    realmStoryProto.deleteFromRealm();
                }
                RealmStory userStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                if (userStory.getRealmStoryProtos().size() == 0) {
                    userStory.deleteFromRealm();
                } else {
                    if (database.where(RealmStoryProto.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", MessageObject.STATUS_SENDING).findAll().size() > 0 ||
                            database.where(RealmStoryProto.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", MessageObject.STATUS_FAILED).findAll().size() > 0) {
                        userStory.setSentAll(false);
                    } else {
                        userStory.setSentAll(true);
                    }
                }

                database.commitTransaction();

                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.STORY_DELETED, isFromRoomMode[0]));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });


    }

    public StoryObject getStoryWithUploadId(long uploadId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        StoryObject[] storyObjects = new StoryObject[1];
        storageQueue.postRunnable(() -> {
            try {

                RealmStoryProto realmStoryProto = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("id", uploadId).findFirst();
                if (realmStoryProto != null) {
                    storyObjects[0] = StoryObject.create(realmStoryProto);
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }


        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return storyObjects[0];
    }


    public StoryObject getStoryWithFileToken(String fileToken) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        StoryObject[] storyObjects = new StoryObject[1];
        storageQueue.postRunnable(() -> {
            try {

                RealmStoryProto realmStoryProto = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("fileToken", fileToken).findFirst();
                if (realmStoryProto != null) {
                    storyObjects[0] = StoryObject.create(realmStoryProto);
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }


        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return storyObjects[0];
    }

    public void userAddViewStory(long storyId, int viewdAt, long viewdUserId, long storyOwnerUserId) {


        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "deleteUserStoryId " + storyId);
            try {
                database.beginTransaction();

                RealmRegisteredInfo realmRegisteredInfo = database.where(RealmRegisteredInfo.class).equalTo("id", viewdUserId).findFirst();

                RealmStoryProto realmStoryProto = database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("storyId", storyId).findFirst();

                if (realmStoryProto != null) {
                    realmStoryProto.setSeen(true);
                    realmStoryProto.setViewCount(realmStoryProto.getViewCount() + 1);


                    boolean isExist = false;
                    RealmStoryViewInfo realmStoryViewInfo;
                    realmStoryViewInfo = database.where(RealmStoryViewInfo.class).equalTo("id", storyId).equalTo("userId", viewdUserId).findFirst();
                    if (realmStoryViewInfo == null) {
                        realmStoryViewInfo = database.createObject(RealmStoryViewInfo.class);
                    } else {
                        isExist = true;
                    }
                    if (realmRegisteredInfo == null) {
                        realmStoryViewInfo.setDisplayName("");
                        new RequestUserInfo().userInfo(viewdUserId);
                    } else {
                        realmStoryViewInfo.setProfileColor(realmRegisteredInfo.getColor());
                        realmStoryViewInfo.setDisplayName(realmRegisteredInfo.getDisplayName());
                    }
                    realmStoryViewInfo.setId(storyId);
                    realmStoryViewInfo.setUserId(viewdUserId);
                    realmStoryViewInfo.setCreatedTime(viewdAt);
                    if (isExist) {
                        realmStoryProto.getRealmStoryViewInfos().remove(realmStoryViewInfo);
                    }
                    realmStoryProto.getRealmStoryViewInfos().add(realmStoryViewInfo);
                    isExist = false;
                }


                RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", storyOwnerUserId).findFirst();
                if (realmStory != null) {
                    int counter = 0;
                    for (int i = 0; i < realmStory.getRealmStoryProtos().size(); i++) {
                        if (realmStory.getRealmStoryProtos().get(i).isSeen()) {
                            counter++;
                        }
                    }

                    if (counter == realmStory.getRealmStoryProtos().size()) {
                        RealmStory userRealmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", storyOwnerUserId).findFirst();
                        if (userRealmStory != null) {
                            userRealmStory.setSeenAll(true);
                        }
                    }


                }

                database.commitTransaction();

                G.runOnUiThread(() -> getEventManager().postEvent(EventManager.STORY_USER_ADD_VIEW));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });


    }

    public List<MainStoryObject> getAllStories(String sortAs) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<RealmStory> stories = new ArrayList<>();
        List<MainStoryObject> storyObjects = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {
                if (sortAs != null) {
                    stories.addAll(database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll().sort(sortAs));
                } else {
                    stories.addAll(database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll());
                }


                for (int i = 0; i < stories.size(); i++) {
                    storyObjects.add(MainStoryObject.create(stories.get(i)));
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return storyObjects;
    }

    public StoryObject getCurrentUserStoryById(long storyId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        StoryObject[] stories = new StoryObject[1];
        storageQueue.postRunnable(() -> {
            try {

                RealmStoryProto realmStoryProto = database.where(RealmStoryProto.class).equalTo("storyId", storyId).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findFirst();
                if (realmStoryProto != null) {
                    stories[0] = StoryObject.create(realmStoryProto);
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return stories[0];
    }

    public List<StoryObject> getCurrentUserStories(boolean needIsForRoom) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<StoryObject> stories = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {

                RealmResults<RealmStoryProto> realmStoryProto;
                if (needIsForRoom) {
                    realmStoryProto = database.where(RealmStoryProto.class).equalTo("isForRoom", false).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findAll().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                } else {
                    realmStoryProto = database.where(RealmStoryProto.class).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findAll().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                }
                for (int i = 0; i < realmStoryProto.size(); i++) {
                    stories.add(StoryObject.create(realmStoryProto.get(i)));
                }


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return stories;
    }

    public List<StoryObject> getCurrentUserRoomStories(long roomId, int listMode) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<StoryObject> stories = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {
                RealmResults<RealmStoryProto> realmStoryProto;
                boolean isAbleToAdd = true;
                if (roomId == 0) {
                    realmStoryProto = database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findAll().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                } else {
                    realmStoryProto = database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("roomId", roomId).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findAll().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                }


                for (int i = 0; i < realmStoryProto.size(); i++) {
                    if (listMode == 0) {
                        for (int j = 0; j < stories.size(); j++) {
                            if (realmStoryProto.get(i).getRoomId() == stories.get(j).roomId) {
                                isAbleToAdd = false;
                                break;
                            } else {
                                isAbleToAdd = true;
                            }
                        }
                        if (isAbleToAdd) {
                            stories.add(StoryObject.create(realmStoryProto.get(i)));
                        }
                    } else {
                        stories.add(StoryObject.create(realmStoryProto.get(i)));
                    }


                }


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return stories;
    }

    public List<MainStoryObject> getSortedRoomStoryObjectsInMainStoryObject(long userId, boolean isOtherRoomStory, long roomId, String[] sortBy, Sort[] orderBy) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<MainStoryObject> stories = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {
                RealmResults<RealmStoryProto> realmStoryProtos;
                RealmResults<RealmStory> realmStory;
                RealmStory myRealmStory = null;

                if (isOtherRoomStory) {
                    myRealmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("roomId", roomId).findFirst();
                    realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).sort("lastCreatedAt", Sort.DESCENDING).findAll();
                } else {
                    realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("roomId", roomId).findAll();
                    if (realmStory == null || realmStory.size() == 0) {
                        realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).findAll();
                    }
                }


                if (realmStory != null && realmStory.size() > 0) {
                    for (int i = 0; i < realmStory.size(); i++) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = realmStory.get(i).getRealmStoryProtos().sort(sortBy, orderBy);
                        stories.add(MainStoryObject.create(realmStory.get(i)));

                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            if (realmStoryProtos.get(j).isForRoom() && realmStoryProtos.get(j).getRoomId() == roomId) {
                                storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                            }
                        }

                        stories.get(i).storyObjects = storyObjects;
                    }
                    if (isOtherRoomStory && myRealmStory != null) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = myRealmStory.getRealmStoryProtos().sort(sortBy, orderBy);
                        stories.add(0, MainStoryObject.create(myRealmStory));
                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            if (realmStoryProtos.get(j).isForRoom() && realmStoryProtos.get(j).getRoomId() == roomId) {
                                storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                            }

                        }
                        stories.get(0).storyObjects = storyObjects;
                    }
                } else {
                    if (isOtherRoomStory && myRealmStory != null) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = myRealmStory.getRealmStoryProtos().sort(sortBy, orderBy);
                        stories.add(0, MainStoryObject.create(myRealmStory));
                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                        }
                        stories.get(0).storyObjects = storyObjects;
                    }
                }


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return stories;
    }

    public List<MainStoryObject> getSortedStoryObjectsInMainStoryObject(long userId, String[] sortBy, Sort[] orderBy) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<MainStoryObject> stories = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {
                RealmResults<RealmStoryProto> realmStoryProtos;
                RealmResults<RealmStory> realmStory;
                RealmStory myRealmStory = null;
                RealmStory igapRealmStory = null;
                boolean isExistIgapRoomStory = false;
                igapRealmStory = database.where(RealmStory.class).equalTo("roomId", 2901).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findFirst();
                if (igapRealmStory != null && igapRealmStory.getRealmStoryProtos().size() > 0) {
                    isExistIgapRoomStory = true;
                }
                if (userId == 0) {
                    myRealmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).findFirst();
                    realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).sort("lastCreatedAt", Sort.DESCENDING).findAll();

                } else {
                    realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findAll();
                }

                if (realmStory != null && realmStory.size() > 0) {
                    for (int i = 0; i < realmStory.size(); i++) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = realmStory.get(i).getRealmStoryProtos().sort(sortBy, orderBy);
                        if (isExistIgapRoomStory && realmStory.get(i).getRoomId() == 2901) {
                            stories.add(0, MainStoryObject.create(realmStory.get(i)));
                        } else {
                            stories.add(MainStoryObject.create(realmStory.get(i)));
                        }


                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                        }

                        stories.get(isExistIgapRoomStory && realmStory.get(i).getRoomId() == 2901 ? 0 : i).storyObjects = storyObjects;
                    }
                    if (userId == 0 && myRealmStory != null) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = myRealmStory.getRealmStoryProtos().sort(sortBy, orderBy);
                        stories.add(0, MainStoryObject.create(myRealmStory));
                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                        }
                        stories.get(0).storyObjects = storyObjects;
                    }
                } else {
                    if (userId == 0 && myRealmStory != null) {
                        List<StoryObject> storyObjects = new ArrayList<>();
                        realmStoryProtos = myRealmStory.getRealmStoryProtos().sort(sortBy, orderBy);
                        stories.add(0, MainStoryObject.create(myRealmStory));
                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                        }
                        stories.get(0).storyObjects = storyObjects;
                    }
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return stories;
    }

    public MainStoryObject getStoryById(long userId, boolean needSort) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<MainStoryObject> stories = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "getStoryById: " + "userId: " + userId + " needSort: " + needSort);
            try {
                List<StoryObject> storyObjects = new ArrayList<>();
                MainStoryObject mainStoryObject;
                RealmStory realmStory = database.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                if (realmStory != null) {
                    if (needSort) {
                        RealmResults<RealmStoryProto> realmStoryProtos = realmStory.getRealmStoryProtos().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                        for (int i = 0; i < realmStoryProtos.size(); i++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(i)));
                        }
                        mainStoryObject = MainStoryObject.create(realmStory);
                        mainStoryObject.storyObjects = storyObjects;

                    } else {
                        mainStoryObject = MainStoryObject.create(realmStory);
                    }
                    stories.add(mainStoryObject);
                }
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return (stories.size() > 0 ? stories.get(0) : null);
    }


    public List<MainStoryObject> getOtherUsersStories() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<RealmStory> stories = new ArrayList<>();
        List<MainStoryObject> mainStoryObjects = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {

                boolean isExistIgapRoomStory = false;


                RealmStory igapRealmStory = database.where(RealmStory.class).equalTo("roomId", 2901).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findFirst();

                stories.addAll(database.where(RealmStory.class).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).sort("lastCreatedAt", Sort.DESCENDING).findAll());

                if (igapRealmStory != null && igapRealmStory.getRealmStoryProtos().size() > 0) {
                    isExistIgapRoomStory = true;
                }

                for (int i = 0; i < stories.size(); i++) {
                    List<StoryObject> storyObjects = new ArrayList<>();
                    RealmResults<RealmStoryProto> realmStoryProtos = stories.get(i).getRealmStoryProtos().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                    if (realmStoryProtos != null && realmStoryProtos.size() > 0) {
                        for (int j = 0; j < realmStoryProtos.size(); j++) {
                            storyObjects.add(StoryObject.create(realmStoryProtos.get(j)));
                        }
                        MainStoryObject mainStoryObject = MainStoryObject.create(database.copyFromRealm(stories.get(i)));
                        mainStoryObject.storyObjects = storyObjects;
                        if (isExistIgapRoomStory && stories.get(i).getRoomId() == 2901) {
                            mainStoryObjects.add(0, mainStoryObject);
                        } else {
                            mainStoryObjects.add(mainStoryObject);
                        }

                    }

                }


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return mainStoryObjects;
    }


    public List<StoryObject> getStoryByStatus(long userId, long roomId, int status, boolean isNotNullToken, boolean isForRoom, String[] fieldSort) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<RealmStoryProto> stories = new ArrayList<>();
        List<StoryObject> storyObjects = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {

                if (isNotNullToken) {
                    if (fieldSort == null) {
                        if (isForRoom) {
                            stories.addAll(database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("roomId", roomId).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).isNotNull("fileToken").findAll());
                        } else {
                            stories.addAll(database.where(RealmStoryProto.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).isNotNull("fileToken").findAll());
                        }
                    } else {
                        if (isForRoom) {
                            stories.addAll(database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("roomId", roomId).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).isNotNull("fileToken").findAll().sort(fieldSort, new Sort[]{Sort.ASCENDING}));
                        } else {
                            stories.addAll(database.where(RealmStoryProto.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).isNotNull("fileToken").findAll().sort(fieldSort, new Sort[]{Sort.ASCENDING}));
                        }

                    }
                    database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("roomId", roomId).equalTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).findAll().sort(new String[]{"createdAt", "index"}, new Sort[]{Sort.DESCENDING, Sort.DESCENDING});
                } else {
                    if (isForRoom && roomId != 0) {
                        stories.addAll(database.where(RealmStoryProto.class).equalTo("isForRoom", true).equalTo("roomId", roomId).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).findAll());
                    } else {
                        stories.addAll(database.where(RealmStoryProto.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("status", status).findAll());
                    }

                }


                for (int i = 0; i < stories.size(); i++) {
                    storyObjects.add(StoryObject.create(database.copyFromRealm(stories.get(i))));
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return storyObjects;

    }


    public List<StoryObject> getNotNullTokenStories(long userId, int status) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<RealmStoryProto> stories = new ArrayList<>();
        List<StoryObject> storyObjects = new ArrayList<>();
        storageQueue.postRunnable(() -> {
            try {

                if (status == 0) {
                    stories.addAll(database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("userId", userId).isNotNull("fileToken").findAll());
                } else {
                    stories.addAll(database.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("userId", userId).equalTo("status", status).isNotNull("fileToken").findAll());
                }


                for (int i = 0; i < stories.size(); i++) {
                    storyObjects.add(StoryObject.create(database.copyFromRealm(stories.get(i))));
                }

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return storyObjects;

    }

    public void updateStoryStatus(long id, int status) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStoryProto realmStoryProto = realm.where(RealmStoryProto.class).equalTo("isForReply", false).equalTo("id", id).findFirst();
                    if (realmStoryProto != null) {
                        realmStoryProto.setStatus(status);
                    }
                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    public void updateStoryFileToken(long messageId, String fileToken) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {
                database.executeTransaction(realm -> {
                    RealmStoryProto realmStoryProto = realm.where(RealmStoryProto.class).equalTo("isForReply", false).equalTo("id", messageId).findFirst();
                    if (realmStoryProto != null) {
                        realmStoryProto.setFileToken(fileToken);
                    }

                });
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


    public void updateStorySentStatus(long userId, boolean status) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "updateStorySentStatus: " + "userId: " + userId + " status: " + status);
            try {
                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                    if (realmStory != null) {
                        realmStory.setSentAll(status);
                    }
                });
                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    public void deleteStoryByUserId(long userId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    realm.where(RealmStory.class).equalTo("userId", userId).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).findAll().deleteAllFromRealm();

                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void storySetSeen(long storyId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStoryProto realmStoryProto = realm.where(RealmStoryProto.class).equalTo("isForReply", false).equalTo("storyId", storyId).findFirst();
                    if (realmStoryProto != null) {
                        realmStoryProto.setSeen(true);
                    }
                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public boolean isStorySeen(long storyId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStoryProto realmStoryProto = realm.where(RealmStoryProto.class).equalTo("isForReply", false).equalTo("storyId", storyId).findFirst();
                    if (realmStoryProto != null) {
                        result[0] = realmStoryProto.isSeen();
                    }
                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }


    public boolean isAllStorySeen(boolean isUser, long userId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(isUser ? "userId" : "roomId", userId).findFirst();
                    if (realmStory != null) {
                        result[0] = realmStory.isSeenAll();
                    }
                });


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }


    public void storySetSeenAll(long userId, boolean seen, boolean isUser) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "storySetSeenAll: " + "userId: " + userId + " seen: " + seen);
            try {

                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(isUser ? "userId" : "roomId", userId).findFirst();
                    if (realmStory != null) {
                        realmStory.setSeenAll(seen);
                    }
                });


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public boolean isHaveUnSeenStory() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmResults<RealmStory> realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).findAll();
                    if (realmStory != null && realmStory.size() > 0) {

                        for (int i = 0; i < realmStory.size(); i++) {
                            if (!realmStory.get(i).isSeenAll()) {
                                result[0] = true;
                                break;
                            }
                        }
                    }
                });


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }


    public int getUnSeenStoryCount() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        int[] result = new int[1];
        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmResults<RealmStory> realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).notEqualTo("userId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isSeenAll", false).findAll();
                    if (realmStory != null && realmStory.size() > 0) {
                        result[0] = realmStory.size();
                    }
                });


                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    public void storySetDisplayName(long userId, String displayName) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("userId", userId).findFirst();
                    RealmResults<RealmStoryViewInfo> realmStoryViewInfo = realm.where(RealmStoryViewInfo.class).equalTo("userId", userId).findAll();
                    if (realmStoryViewInfo != null && realmStoryViewInfo.size() > 0) {
                        for (int i = 0; i < realmStoryViewInfo.size(); i++) {
                            realmStoryViewInfo.get(i).setDisplayName(displayName);
                        }
                    }

                    if (realmStory != null) {
                        realmStory.setDisplayName(displayName);
                        if (realmStory.getRealmStoryProtos() != null && realmStory.getRealmStoryProtos().size() > 0) {
                            for (int i = 0; i < realmStory.getRealmStoryProtos().size(); i++) {
                                if (realmStory.getRealmStoryProtos().get(i) != null && !realmStory.getRealmStoryProtos().get(i).isForRoom()) {
                                    realmStory.getRealmStoryProtos().get(i).setDisplayName(displayName);
                                }
                            }
                        }
                        G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.STORY_USER_INFO, userId));
                    }


                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void storySetDisplayName(long roomId, String displayName, boolean isVerified) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("roomId", roomId).findFirst();

                    if (realmStory != null) {
                        realmStory.setDisplayName(displayName);
                        if (realmStory.getRealmStoryProtos() != null && realmStory.getRealmStoryProtos().size() > 0) {
                            for (int i = 0; i < realmStory.getRealmStoryProtos().size(); i++) {
                                if (realmStory.getRealmStoryProtos().get(i) != null) {
                                    realmStory.getRealmStoryProtos().get(i).setDisplayName(displayName);
                                    realmStory.getRealmStoryProtos().get(i).setVerified(isVerified);
                                }
                            }
                        }
                        G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.STORY_ROOM_INFO, roomId));
                    } else {
                        RealmResults<RealmStoryProto> realmStoryProtos = realm.where(RealmStoryProto.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo("isForReply", false).equalTo("roomId", roomId).findAll();
                        if (realmStoryProtos != null && realmStoryProtos.size() > 0) {
                            for (int i = 0; i < realmStoryProtos.size(); i++) {
                                realmStoryProtos.get(i).setDisplayName(displayName);
                                realmStoryProtos.get(i).setVerified(isVerified);
                            }
                            G.runOnUiThread(() -> EventManager.getInstance(AccountManager.selectedAccount).postEvent(EventManager.STORY_ROOM_INFO, roomId));
                        }

                    }


                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void storySetIndexOfSeen(long userId, long roomId, int position) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        storageQueue.postRunnable(() -> {
            try {

                database.executeTransaction(realm -> {
                    RealmStory realmStory = realm.where(RealmStory.class).equalTo("sessionId", AccountManager.getInstance().getCurrentUser().getId()).equalTo(roomId != 0 ? "roomId" : "userId", roomId != 0 ? roomId : userId).findFirst();
                    if (realmStory != null) {
                        realmStory.setIndexOfSeen(position);
                    }
                });

                countDownLatch.countDown();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setLastScrollPosition(final long roomId, final long messageId, long documentId, final int offset) {
        storageQueue.postRunnable(() -> {
            FileLog.i(TAG, "setLastScrollPosition " + messageId);
            try {
                database.beginTransaction();
                RealmRoom realmRoom = database.where(RealmRoom.class).equalTo("id", roomId).findFirst();
                if (realmRoom != null) {
                    realmRoom.setLastScrollPositionMessageId(messageId);
                    realmRoom.setLastScrollPositionDocumentId(documentId);
                    realmRoom.setLastScrollPositionOffset(offset);
                }
                database.commitTransaction();


            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public interface DatabaseDelegate {
        void run(Object... object);
    }
}
