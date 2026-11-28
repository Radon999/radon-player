// The Rich Presence service inside the Discord app for Android (2026 versions on), which Discord built for apps that
// use its Social SDK: another app on the phone hands it what to show on your profile, and Discord shows it. No sign-in
// and no token pass through it. Discord does not publish this interface for use without its SDK: these three files
// describe it as others worked it out (credit: night.milkyway's antisocialcord, and RainBowFl4sh's
// Poweramp-Discord-RP, MIT). A Discord update may change it; DiscordLink then says it couldn't connect.
package com.discord.socialsdk.rpc;

import com.discord.socialsdk.rpc.IDiscordRpcCallback;
import com.discord.socialsdk.rpc.IDiscordRpcConnection;

interface IDiscordRpcService {
    // The Discord application's ID, the Rich Presence version ("1"), and where Discord answers
    IDiscordRpcConnection connect(long applicationId, String version, IDiscordRpcCallback callback);
}
