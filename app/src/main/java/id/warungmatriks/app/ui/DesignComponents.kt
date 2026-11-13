package id.warungmatriks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.ui.theme.*

@Composable
internal fun DemoPage(title: String, subtitle: String = "Warung Matriks • Data demo", onBack: (() -> Unit)? = null, badgeText: String = "DEMO", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Kembali") }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Muted)
            }
            if (badgeText.isNotBlank()) Surface(color = PaleBlue, shape = RoundedCornerShape(50)) {
                Text(badgeText, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, color = Blue, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
internal fun DemoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun GroupTitle(title: String, subtitle: String? = null) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
    if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Muted)
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun DemoLabel(text: String, color: Color = Blue, background: Color = PaleBlue) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SectionGap() { Spacer(Modifier.height(22.dp)) }

@Composable
internal fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(color = PaleBlue, shape = RoundedCornerShape(10.dp)) {
            Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronRight, "Buka $title", tint = Muted) }
    }
}

@Composable
internal fun InfoLine(label: String, value: String, color: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun DemoNote(text: String = "Tampilan contoh • belum menyimpan data atau terhubung ke layanan.") {
    Text(text, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
}

@Composable
internal fun DemoAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text(text, fontSize = 12.sp) }
}
