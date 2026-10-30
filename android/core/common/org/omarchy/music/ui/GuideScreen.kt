package org.omarchy.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Changelog & features (the menu): everything the app does and what each release brought, in big type as the lyrics
 *  are. From FEATURES.md (FeatureGuide.kt): every new feature gets its description there. */
@Composable
fun GuideScreen(ui: Ui) {
    val lines = remember { FEATURE_GUIDE.lines().filter { it.isNotBlank() } }
    FullScreen("Changelog & features", { ui.guide = false }) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding()) {
            items(lines.size) { i ->
                val l = lines[i].trim()
                when {
                    l.startsWith("# ") -> Text(rich(l.drop(2)), style = T.title.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    l.startsWith("## ") -> Text(rich(l.drop(3)), style = T.title.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = C.accent),
                        modifier = Modifier.padding(top = 26.dp, bottom = 6.dp))
                    l.startsWith("### ") -> Text(rich(l.drop(4)), style = T.title.copy(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    l.startsWith("- ") || l.startsWith("* ") -> Text(rich("•  " + l.drop(2)), style = T.body.copy(fontSize = 20.sp, lineHeight = 28.sp),
                        modifier = Modifier.padding(vertical = 5.dp))
                    l == "---" -> Column(Modifier.padding(vertical = 8.dp)) {}
                    else -> Text(rich(l), style = T.body.copy(fontSize = 20.sp, lineHeight = 29.sp), modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

/** **bold** and *italic* as Markdown has them. */
private fun rich(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> { val e = s.indexOf("**", i + 2)
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(s.substring(i + 2, e)); pop(); i = e + 2 }
            s[i] == '*' && s.indexOf('*', i + 1) > 0 -> { val e = s.indexOf('*', i + 1)
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(s.substring(i + 1, e)); pop(); i = e + 1 }
            s[i] == '`' && s.indexOf('`', i + 1) > 0 -> { val e = s.indexOf('`', i + 1); append(s.substring(i + 1, e)); i = e + 1 }
            else -> { append(s[i]); i++ }
        }
    }
}
