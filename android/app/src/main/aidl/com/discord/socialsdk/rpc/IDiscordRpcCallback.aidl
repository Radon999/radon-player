// What the Discord app says back over its Rich Presence service (see IDiscordRpcService.aidl).
package com.discord.socialsdk.rpc;

interface IDiscordRpcCallback {
    // One answer or event, as JSON (the same as the PC's Rich Presence: READY, the answer to a command, an error)
    void onFrame(String frame);
    // Discord closed the connection (4000: it doesn't know the application ID)
    void onClose(int code, String reason);
}
