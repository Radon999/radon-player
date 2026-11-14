import org.omarchy.music.*;
import java.nio.file.*;
import java.util.*;

/** The mood rules ported so each proposed change can be tried: scales, how much the key counts, how much the words and
 *  the sound count, and how many moods a song is given. Checked against the app's own rules first (the port must give
 *  the same answer with nothing changed). */
public class Eval2 {
    static class Song { String name, wrote, picked; SoundFeatures f; Map<String, Double> tags, atags; LyricsReading words;
        Map<String, Double> tagAll, tagArtist, label; }
    static List<String> ALL = MoodRules.INSTANCE.getALL();
    static double cl(double v) { return Math.max(0, Math.min(1, v)); }

    // ---- what can be changed ----
    static class P { double loudLo = -28, loudHi = -10, brLo = 1000, brHi = 3500, onLo = 0, onHi = 5, beatHi = 0.5, beatLo = 0;
        double key = 1, soundK = 1.1, lyr = 1, fitsH = 0.26, nearH = 0.30; int most = 5; String name = "as it is"; }

    static Map<String, Double> flat(String s) { Map<String, Double> m = new LinkedHashMap<>(); if (s == null || s.isEmpty()) return m;
        for (String kv : s.split("\\|")) { int i = kv.lastIndexOf('='); if (i > 0) try { m.put(kv.substring(0, i), Double.parseDouble(kv.substring(i + 1)) / 100.0); } catch (Exception e) {} } return m; }
    static Map<String, Double> inv(Map<String, Double> scores) { Map<String, Double> p = new HashMap<>();
        for (var e : scores.entrySet()) p.put(e.getKey(), -0.7 * Math.log(1 - Math.min(0.995, e.getValue()))); return p; }

    static double[] norm(double... w) { double s = 0; for (double x : w) s += x; for (int i = 0; i < w.length; i++) w[i] /= s; return w; }

    static Map<String, Double> sound(SoundFeatures f, P p, double[] cues) {
        double e = cl((f.getLoud() - p.loudLo) / (p.loudHi - p.loudLo));
        double fast = f.getBpm() <= 0 ? 0.5 : cl((f.getBpm() - 85) / 55), slow = 1 - fast;
        double b = cl((f.getBright() - p.brLo) / (p.brHi - p.brLo));
        double d = cl((f.getOnsets() - p.onLo) / (p.onHi - p.onLo));
        double beat = cl((f.getBeat() - p.beatLo) / (p.beatHi - p.beatLo));
        double r = cl(Math.max((f.getRange() - 6) / 14, (f.getVary() - 2) / 6));
        double up = cl(f.getMajor()), down = cl(-f.getMajor());
        double mid = cl(1 - Math.abs(e - 0.4) * 2);
        double dance = f.getBpm() <= 0 ? 0 : cl(1 - Math.abs(f.getBpm() - 120) / 30);
        double[] h = norm(0.45 * p.key, 0.2, 0.2, 0.15), sd = norm(0.45 * p.key, 0.3, 0.25), dk = norm(0.4 * p.key, 0.35, 0.25);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Happy", h[0] * up + h[1] * fast + h[2] * b + h[3] * e);
        m.put("Energetic", 0.45 * e + 0.3 * fast + 0.25 * d);
        m.put("Party", 0.35 * beat + 0.3 * dance + 0.25 * e + 0.1 * up);
        m.put("Hype", 0.35 * e + 0.3 * beat + 0.2 * d + 0.15 * (1 - b));
        m.put("Chill", 0.3 * mid + 0.3 * slow + 0.2 * (1 - d) + 0.2 * beat);
        m.put("Calm", 0.4 * (1 - e) + 0.3 * (1 - d) + 0.3 * slow);
        m.put("Dreamy", 0.3 * (1 - beat) + 0.25 * (1 - d) + 0.2 * (1 - b) + 0.25 * mid);
        m.put("Focus", 0.4 * (1 - r) + 0.3 * mid + 0.3 * (1 - d));
        m.put("Romantic", 0.35 * slow + 0.25 * mid + 0.2 * up + 0.2 * (1 - d));
        m.put("Sad", sd[0] * down + sd[1] * slow + sd[2] * (1 - e));
        m.put("Dark", dk[0] * down + dk[1] * (1 - b) + dk[2] * e);
        m.put("Intense", 0.4 * e + 0.35 * b + 0.25 * d);
        // the cues for when the sound and the words disagree
        cues[0] = cl((Math.min(e, fast) - 0.4) / 0.3) * cl((Math.max(up, b) - 0.1) / 0.4);            // upbeat
        cues[1] = cl((Math.min(1 - e, 1 - fast) - 0.35) / 0.3) * cl((Math.max(down, 1 - b) - 0.3) / 0.4);   // downbeat
        cues[2] = Math.max(-1, Math.min(1, 0.6 * f.getMajor() + 0.5 * (b - 0.5) + 0.4 * (fast - 0.5)));   // valence
        cues[3] = cl(0.5 * e + 0.3 * fast + 0.2 * d);                                                      // energy
        return m;
    }

    static final Map<String, Set<String>> OPP = new HashMap<>();
    static { String[][] o = {{"Happy","Sad"},{"Happy","Dark"},{"Party","Sad"},{"Calm","Intense"},{"Calm","Hype"},{"Calm","Energetic"},{"Chill","Intense"}};
        for (String[] q : o) { OPP.computeIfAbsent(q[0], k -> new HashSet<>()).add(q[1]); OPP.computeIfAbsent(q[1], k -> new HashSet<>()).add(q[0]); } }
    static final Set<String> GLOOMY = Set.of("Sad", "Dark"), GLAD = Set.of("Happy", "Party", "Energetic", "Hype");

    /** The song's moods, best first. tagPts: the points its tags give (all, or its artist's only for the hold-out check). */
    static List<String> judge(Song s, P p, Map<String, Double> tagPts) {
        Map<String, Double> score = new HashMap<>(tagPts);
        double[] cues = new double[4];
        Map<String, Double> snd = sound(s.f, p, cues);
        double avg = snd.values().stream().mapToDouble(x -> x).average().orElse(0);
        Map<String, Double> lyr = s.words != null ? s.words.points() : Map.of();
        for (var e : snd.entrySet()) score.merge(e.getKey(), p.soundK * (lyr.isEmpty() ? 1.0 : MoodRules.INSTANCE.soundWeight(e.getKey())) * (e.getValue() - avg), Double::sum);
        double conflict = 0;
        if (!lyr.isEmpty()) {
            for (var e : lyr.entrySet()) { String m = e.getKey(); double v = cl(e.getValue());
                double against = GLOOMY.contains(m) ? cues[0] : GLAD.contains(m) ? cues[1] : 0;
                double k = Math.max(0.25, v >= 0.6 ? 1 - 0.35 * against : 1 - against);
                score.merge(m, p.lyr * 1.3 * MoodRules.INSTANCE.wordsWeight(m) * v * k, Double::sum); }
            if (!s.words.getInstrumental()) { double sure = cl(s.words.getConfidence() / 0.6);
                double gap = Math.abs(cues[2] - s.words.getValence()) + 0.5 * Math.abs(cues[3] - s.words.getEnergy());
                conflict = cl((gap - 0.6) / 0.9) * sure; }
        }
        List<Map.Entry<String, Double>> r = new ArrayList<>();
        for (String m : ALL) { double pt = score.getOrDefault(m, 0.0); if (pt > 0.08) { double fit = Math.round((1 - Math.exp(-pt / 0.7)) * 100) / 100.0; if (fit > 0.01) r.add(Map.entry(m, fit)); } }
        r.sort((x, y) -> { int c = Double.compare(y.getValue(), x.getValue()); return c != 0 ? c : Integer.compare(ALL.indexOf(x.getKey()), ALL.indexOf(y.getKey())); });
        List<String> out = new ArrayList<>(); if (r.isEmpty()) return out;
        double top = r.get(0).getValue(); out.add(r.get(0).getKey());
        for (int i = 1; i < r.size(); i++) { String m = r.get(i).getKey(); double v = r.get(i).getValue();
            if (out.size() == p.most || v < p.fitsH || v < p.nearH * top) break;
            boolean opp = false; for (String x : out) if (OPP.getOrDefault(m, Set.of()).contains(x)) opp = true;
            if (v < 0.55 && conflict < 0.3 && opp) continue;
            if ((v < 0.40 || v < 0.45 * top) && opp) continue;
            out.add(m); }
        return out;
    }

    static final String[][] WORDS = {
        {"Chill", "chill", "chillout", "chill out", "chilled", "laid back", "laidback", "lounge", "downtempo", "chillhop", "lofi", "lo fi", "rainy day"},
        {"Calm", "mellow", "relax", "relaxing", "calm", "peaceful", "soft", "gentle", "soothing", "quiet", "tranquil", "serene", "sleep", "sleepy", "lullaby"},
        {"Sad", "sad", "melancholic", "melancholy", "melancholia", "depressing", "depressive", "heartbreak", "heartbroken", "sorrow", "crying", "lonely", "grief", "bittersweet"},
        {"Happy", "happy", "feel good", "feelgood", "cheerful", "joyful", "joy", "fun", "sunny", "summer", "uplifting", "optimistic", "good vibes", "positive", "upbeat"},
        {"Energetic", "energetic", "energy", "workout", "running", "gym", "power", "fast", "adrenaline"},
        {"Party", "party", "dance", "club", "dancefloor", "clubbing", "danceable"},
        {"Hype", "hype", "banger", "bangers", "turn up", "pump up", "hard hitting"},
        {"Romantic", "romantic", "love", "love songs", "love song", "sensual", "sexy", "seductive", "slow jams", "slow jam"},
        {"Intense", "angry", "aggressive", "rage", "anger", "heavy", "brutal", "intense", "furious", "violent"},
        {"Dark", "dark", "sinister", "haunting", "creepy", "eerie", "noir", "ominous", "evil"},
        {"Dreamy", "dreamy", "dream", "ethereal", "atmospheric", "spacey", "hazy", "floating", "hypnotic"},
        {"Focus", "focus", "study", "studying", "concentration", "background", "coding", "work"} };
    /** The moods people's own mood words on the song say (no genre words): what a person called it. */
    static Map<String, Double> wordLabel(Map<String, Double> tags) { Map<String, Double> out = new HashMap<>();
        for (var e : tags.entrySet()) { if (e.getValue() < 0.05) continue; String t = " " + e.getKey().toLowerCase().replaceAll("[^\\p{L}\\p{N}&]+", " ").trim() + " ";
            for (String[] g : WORDS) for (int i = 1; i < g.length; i++) if (t.equals(" " + g[i] + " ")) out.merge(g[0], e.getValue(), Double::sum); }
        return out; }
    static List<Song> songs = new ArrayList<>();

    static void run(P p) {
        Map<Integer, Integer> n = new TreeMap<>(); Map<String, Integer> in = new HashMap<>();
        double pr = 0, pp = 0; int pn = 0, pmain = 0; double total = 0;
        double hr = 0, hp = 0; int hn = 0, hmain = 0;
        for (Song s : songs) {
            List<String> ms = judge(s, p, s.tagAll);
            n.merge(ms.size(), 1, Integer::sum); total += ms.size(); for (String m : ms) in.merge(m, 1, Integer::sum);
            if (!s.picked.isEmpty()) { Set<String> want = new HashSet<>(Arrays.asList(s.picked.split(";"))); pn++;
                int hit = 0; for (String m : ms) if (want.contains(m)) hit++;
                pr += hit / (double) want.size(); pp += ms.isEmpty() ? 0 : hit / (double) ms.size(); if (!ms.isEmpty() && want.contains(ms.get(0))) pmain++; }
            if (!s.label.isEmpty()) {   // people's tags on the song, kept out of what the rules see
                List<String> hs = judge(s, p, s.tagArtist); hn++;
                int hit = 0; for (String m : hs) if (s.label.containsKey(m)) hit++;
                hr += hit / (double) s.label.size(); hp += hs.isEmpty() ? 0 : hit / (double) hs.size(); if (!hs.isEmpty() && s.label.containsKey(hs.get(0))) hmain++; }
        }
        StringBuilder a = new StringBuilder(); for (String m : ALL) a.append(String.format("%s %d  ", m.substring(0, 3), Math.round(100.0 * in.getOrDefault(m, 0) / songs.size())));
        double pf = 2 * (pr / pn) * (pp / pn) / ((pr / pn) + (pp / pn)), hf = 2 * (hr / hn) * (hp / hn) / ((hr / hn) + (hp / hn));
        System.out.printf("%n== %s%n   moods a song: %.1f %s%n   on %% of songs: %s%n   owner's %d: found %.0f%%, right %.0f%%, both %.0f, main ok %d   |  people's tags (%d songs, kept out): found %.0f%%, right %.0f%%, both %.0f, main ok %d%%%n",
            p.name, total / songs.size(), n, a, pn, 100 * pr / pn, 100 * pp / pn, 100 * pf, pmain, hn, 100 * hr / hn, 100 * hp / hn, 100 * hf, Math.round(100.0 * hmain / hn));
    }

    static double num(String s) { return s.isEmpty() ? 0 : Double.parseDouble(s); }
    public static void main(String[] a) throws Exception {
        int same = 0, cmp = 0;
        for (String line : Files.readAllLines(Path.of(a[0]))) {
            String[] c = line.split("\t", -1); if (c.length < 15) continue;
            Song s = new Song(); s.name = c[0];
            s.f = new SoundFeatures(num(c[1]), num(c[2]), num(c[3]), num(c[4]), num(c[5]), num(c[6]), num(c[7]), num(c[8]), (int) num(c[9]));
            if (s.f.getLoud() <= -60) continue;
            s.wrote = c[11]; s.picked = c[12]; s.tags = flat(c[13]); s.atags = flat(c[14]);
            s.words = c[10].isEmpty() ? null : LyricsReading.Companion.parse(c[10]);
            s.tagAll = inv(MoodRules.INSTANCE.judge(null, "", s.tags, s.atags, null, Map.of(), Map.of()).getScores());
            s.tagArtist = inv(MoodRules.INSTANCE.judge(null, "", Map.of(), s.atags, null, Map.of(), Map.of()).getScores());
            s.label = wordLabel(s.tags);
            songs.add(s);
            // the port against the app's own rules
            MoodRules.Judgement real = MoodRules.INSTANCE.judge(s.f, "", s.tags, s.atags, s.words, s.words != null ? s.words.points() : Map.of(), Map.of());
            List<String> mine = judge(s, new P(), s.tagAll); List<String> theirs = new ArrayList<>(); for (kotlin.Pair<String, Double> q : real.getMoods()) theirs.add(q.getFirst());
            cmp++; if (mine.equals(theirs)) same++;
        }
        System.out.println("songs: " + songs.size() + "; the port gives the very same moods as the app's rules for " + same + " of " + cmp);
        for (int i = 1; i < a.length; i++) {
            P p = new P(); p.name = a[i];
            for (String kv : a[i].split(",")) { String[] x = kv.split("="); double v = Double.parseDouble(x[1]);
                switch (x[0]) { case "scale": if (v > 0) { p.loudLo = -14; p.loudHi = -5; p.brLo = 1300; p.brHi = 2650; p.onLo = 3.4; p.onHi = 6.0; p.beatLo = 0.2; p.beatHi = 0.68; } break;
                    case "key": p.key = v; break; case "soundK": p.soundK = v; break; case "lyr": p.lyr = v; break;
                    case "fits": p.fitsH = v; break; case "near": p.nearH = v; break; case "most": p.most = (int) v; break; } }
            run(p);
        }
    }
}
