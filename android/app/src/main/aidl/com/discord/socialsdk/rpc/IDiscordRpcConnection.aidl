// A connection to the Discord app's Rich Presence service (see IDiscordRpcService.aidl).
package com.discord.socialsdk.rpc;

interface IDiscordRpcConnection {
    // One command, as JSON (the same as the PC's Rich Presence: {"cmd":"SET_ACTIVITY",...})
    void sendFrame(String frame);
    void disconnect();
}
