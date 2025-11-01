const {onValueCreated} = require("firebase-functions/v2/database");
const {initializeApp} = require("firebase-admin/app");
const {getDatabase} = require("firebase-admin/database");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();

exports.sendChatNotification = onValueCreated(
    "/conversations/{conversationId}/messages/{messageId}",
    async (event) => {
      const snapshot = event.data;
      const message = snapshot.val();
      const {conversationId} = event.params;
      const senderId = message.senderId;

      if (!message || !message.content) {
        console.log("Message was empty, not sending.");
        return;
      }

      const db = getDatabase();

      // 1. Get sender name
      let senderName = "Someone";
      try {
        const senderSnap = await db.ref(`/users/${senderId}/name`).get();
        if (senderSnap.exists()) {
          senderName = senderSnap.val();
        }
      } catch (e) {
        console.error("Could not get sender name", e);
      }

      // 2. Get conversation info
      const convoSnap = await db.ref(`/conversations/${conversationId}`).get();
      if (!convoSnap.exists()) {
        console.log("Conversation not found.");
        return;
      }

      const conversation = convoSnap.val();

      // 3. Filter recipients
      const recipientIds = Object.keys(conversation.participants || {}).filter(
          (uid) => uid !== senderId,
      );

      if (recipientIds.length === 0) {
        console.log("No recipients to send to.");
        return;
      }

      // 4. Get tokens
      const tokensSnapshots = await Promise.all(
          recipientIds.map((uid) => db.ref(`/users/${uid}/fcmToken`).get()),
      );

      const validTokens = tokensSnapshots
          .map((snap) => snap.val())
          .filter((token) => typeof token === "string" && token.length > 0);

      if (validTokens.length === 0) {
        console.log("No valid FCM tokens found.");
        return;
      }

      // 5. Notification payload
      const payload = {
        data: {
          title: conversation.group ?
          `${senderName} @ ${conversation.name}` :
          senderName,
          body: message.type === "IMAGE" ? "Sent an image" : message.content,
          conversationId,
        },
      };

      // 6. Send notification
      const messaging = getMessaging();
      const response = await messaging.sendEachForMulticast({
        tokens: validTokens,
        data: payload.data,
      });

      // 7. Remove invalid tokens
      response.responses.forEach((res, index) => {
        if (!res.success) {
          const error = res.error;
          console.error("Failure sending to", validTokens[index], error);
          if (error.code === "messaging/registration-token-not-registered") {
            db.ref(`/users/${recipientIds[index]}/fcmToken`).remove();
          }
        }
      });
    },
);
