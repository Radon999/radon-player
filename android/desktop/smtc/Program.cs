// Radon Player's link to Windows' media controls (System Media Transport Controls).
// stdin, one JSON object a line: {"title","artist","album","art","playing","position","duration"} (seconds); "quit" ends.
// stdout, one word a line when a button is pressed: play, pause, toggle, next, previous, stop.
using System.Text.Json;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Storage.Streams;

var player = new MediaPlayer();
player.CommandManager.IsEnabled = false;   // the app plays; this only shows it and passes the buttons on
var smtc = player.SystemMediaTransportControls;
smtc.IsPlayEnabled = smtc.IsPauseEnabled = smtc.IsNextEnabled = smtc.IsPreviousEnabled = smtc.IsStopEnabled = true;
smtc.IsEnabled = true;
var outLock = new object();
void Say(string w) { lock (outLock) { Console.Out.WriteLine(w); Console.Out.Flush(); } }
smtc.ButtonPressed += (_, e) => Say(e.Button switch {
    SystemMediaTransportControlsButton.Play => "play",
    SystemMediaTransportControlsButton.Pause => "pause",
    SystemMediaTransportControlsButton.Next => "next",
    SystemMediaTransportControlsButton.Previous => "previous",
    SystemMediaTransportControlsButton.Stop => "stop",
    _ => "toggle",
});
smtc.PlaybackPositionChangeRequested += (_, e) => Say("seek " + e.RequestedPlaybackPosition.TotalSeconds.ToString(System.Globalization.CultureInfo.InvariantCulture));

string lastKey = "";
string? line;
while ((line = Console.In.ReadLine()) != null) {
    if (line.Trim() == "quit") break;
    try {
        using var doc = JsonDocument.Parse(line);
        var r = doc.RootElement;
        string S(string k) => r.TryGetProperty(k, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";
        double D(string k) => r.TryGetProperty(k, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetDouble() : 0;
        bool playing = r.TryGetProperty("playing", out var p) && p.ValueKind == JsonValueKind.True;
        var title = S("title");
        if (title.Length == 0) { smtc.PlaybackStatus = MediaPlaybackStatus.Stopped; continue; }
        var key = title + "\u0001" + S("artist") + "\u0001" + S("album") + "\u0001" + S("art");
        if (key != lastKey) {
            lastKey = key;
            var u = smtc.DisplayUpdater;
            u.ClearAll();
            u.Type = MediaPlaybackType.Music;
            u.AppMediaId = "Radon Player";
            u.MusicProperties.Title = title;
            u.MusicProperties.Artist = S("artist");
            u.MusicProperties.AlbumTitle = S("album");
            var art = S("art");
            if (art.Length > 0) {
                try {
                    u.Thumbnail = art.StartsWith("http") ? RandomAccessStreamReference.CreateFromUri(new Uri(art))
                        : RandomAccessStreamReference.CreateFromFile(await Windows.Storage.StorageFile.GetFileFromPathAsync(art));
                } catch { }
            }
            u.Update();
        }
        smtc.PlaybackStatus = playing ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;
        var dur = D("duration");
        if (dur > 0) {
            var tl = new SystemMediaTransportControlsTimelineProperties {
                StartTime = TimeSpan.Zero, MinSeekTime = TimeSpan.Zero, EndTime = TimeSpan.FromSeconds(dur),
                MaxSeekTime = TimeSpan.FromSeconds(dur), Position = TimeSpan.FromSeconds(D("position")),
            };
            smtc.UpdateTimelineProperties(tl);
        }
    } catch { }
}
smtc.IsEnabled = false;
