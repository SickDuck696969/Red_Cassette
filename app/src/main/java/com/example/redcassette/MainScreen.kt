package com.example.redcassette

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val RedHoodCrimson = Color(0xFFB71C1C)
val RedHoodDark = Color(0xFF121212)
val RedHoodSurface = Color(0xFF1E1E1E)
val MetalGray = Color(0xFF757575)

@Composable
fun rememberImageBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString != null) {
            withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(Uri.parse(uriString))?.use { bitmap = BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (e: Exception) { e.printStackTrace() }
            }
        } else { bitmap = null }
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RedCassetteViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSongTitle by viewModel.currentSongTitle.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPlaylistName by viewModel.currentPlaylistName.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()

    val appBgUri by viewModel.appBackgroundUri.collectAsState()
    val cassetteLabelUri by viewModel.cassetteLabelUri.collectAsState()
    val labelBitmap = rememberImageBitmap(cassetteLabelUri)

    var showSettings by remember { mutableStateOf(false) }

    // Xin quyền hiển thị Thông báo (Cho Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(background = RedHoodDark, surface = RedHoodSurface, primary = RedHoodCrimson, onPrimary = Color.White, onSurface = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val appBg = rememberImageBitmap(appBgUri)
            if (appBg != null) { Image(bitmap = appBg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.15f) }

            if (showSettings) {
                SettingsScreen(viewModel, onBack = { showSettings = false })
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(currentPlaylistName ?: "Unknown", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CassetteTape(isPlaying = isPlaying, labelBitmap = labelBitmap)

                        Spacer(modifier = Modifier.height(48.dp))
                        Text(text = currentSongTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        Spacer(modifier = Modifier.height(24.dp))

                        Slider(
                            value = progress,
                            onValueChange = { viewModel.isUserSeeking.value = true; viewModel.progress.value = it },
                            onValueChangeFinished = { viewModel.seekTo(progress); viewModel.isUserSeeking.value = false },
                            colors = SliderDefaults.colors(thumbColor = RedHoodCrimson, activeTrackColor = RedHoodCrimson, inactiveTrackColor = MetalGray)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) RedHoodCrimson else MetalGray, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { viewModel.prevSong() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(RedHoodCrimson).clickable { viewModel.togglePlayPause() }, contentAlignment = Alignment.Center) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = { viewModel.nextSong() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = { viewModel.toggleRepeat() }) {
                                val repeatIcon = when (repeatMode) { RepeatMode.ONE -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat }
                                val repeatTint = if (repeatMode == RepeatMode.OFF) MetalGray else RedHoodCrimson
                                Icon(repeatIcon, contentDescription = "Repeat", tint = repeatTint, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CassetteTape(isPlaying: Boolean, labelBitmap: ImageBitmap?) {
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) while (true) rotationAngle.animateTo(targetValue = rotationAngle.value + 360f, animationSpec = tween(durationMillis = 4000, easing = LinearEasing))
        else rotationAngle.stop()
    }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF2C2C2C), Color(0xFF111111)))).border(2.dp, MetalGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.65f).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(RedHoodCrimson.copy(alpha = 0.9f), RedHoodCrimson.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
            if (labelBitmap != null) { Image(bitmap = labelBitmap, contentDescription = "Cassette Label", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Row(modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.4f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF0A0A0A)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { CassetteReel(rotationAngle.value); CassetteReel(rotationAngle.value) }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = 6.dp.toPx(); val padding = 16.dp.toPx()
            drawCircle(Color.Black, radius, Offset(padding, padding)); drawCircle(Color.Black, radius, Offset(size.width - padding, padding))
            drawCircle(Color.Black, radius, Offset(padding, size.height - padding)); drawCircle(Color.Black, radius, Offset(size.width - padding, size.height - padding))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: RedCassetteViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val rootFolderUri by viewModel.rootFolderUri.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val rootSongs by viewModel.rootSongs.collectAsState()

    val appBgUri by viewModel.appBackgroundUri.collectAsState()
    val cassetteLabelUri by viewModel.cassetteLabelUri.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); viewModel.setRootFolder(it.toString()) }
    }
    val appBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); viewModel.setAppBackground(it.toString()) }
    }
    val cassetteLabelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); viewModel.setCassetteLabelUri(it.toString()) }
    }

    var actionPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var selectedSongsForPlaylist by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().background(RedHoodDark)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Cài đặt", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text("Giao diện", color = RedHoodCrimson, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { appBgLauncher.launch(arrayOf("image/*")) }, border = BorderStroke(1.dp, RedHoodCrimson), modifier = Modifier.weight(1f)) {
                        Text(if (appBgUri == null) "Tải Nền Ứng dụng" else "Đổi Nền", color = Color.White)
                    }
                    if (appBgUri != null) { Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { viewModel.setAppBackground(null) }, colors = ButtonDefaults.buttonColors(containerColor = RedHoodCrimson)) { Text("Xoá") } }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Trang trí nhãn Cassette:", color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { cassetteLabelLauncher.launch(arrayOf("image/*")) }, border = BorderStroke(1.dp, RedHoodCrimson), modifier = Modifier.weight(1f)) {
                        Text(if (cassetteLabelUri == null) "Tải Ảnh Lên" else "Đổi Ảnh", color = Color.White)
                    }
                    if (cassetteLabelUri != null) { Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { viewModel.setCassetteLabelUri(null) }, colors = ButtonDefaults.buttonColors(containerColor = RedHoodCrimson)) { Text("Xoá") } }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("Thư mục chứa nhạc (.mp3)", color = RedHoodCrimson, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, RedHoodCrimson)) { Text(if (rootFolderUri == null) "Chọn Folder Gốc" else "Đã chọn thư mục", color = Color.White) }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Danh sách Playlist", color = RedHoodCrimson, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { playlistToEdit = null; selectedSongsForPlaylist = emptySet(); showCreatePlaylist = true }) { Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = RedHoodCrimson) }
                }
            }

            items(allPlaylists) { playlist ->
                val isSelected = selectedPlaylist?.id == playlist.id
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) RedHoodCrimson.copy(alpha = 0.3f) else RedHoodSurface).border(1.dp, if (isSelected) RedHoodCrimson else Color.Transparent, RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { viewModel.selectPlaylist(playlist) }, onLongClick = { actionPlaylist = playlist }).padding(16.dp)) { Text(playlist.name, color = if (isSelected) RedHoodCrimson else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
            }
        }
    }

    if (actionPlaylist != null) {
        AlertDialog(
            onDismissRequest = { actionPlaylist = null }, title = { Text("Tuỳ chọn: ${actionPlaylist!!.name}", color = Color.White) }, containerColor = RedHoodSurface,
            text = { Column { TextButton(onClick = { coroutineScope.launch { selectedSongsForPlaylist = viewModel.getUrisForPlaylist(actionPlaylist!!.id).toSet(); playlistToEdit = actionPlaylist; showCreatePlaylist = true; actionPlaylist = null } }) { Text("Chỉnh sửa (Thêm/Bớt bài hát)", color = Color.White) }; TextButton(onClick = { viewModel.deletePlaylist(actionPlaylist!!); actionPlaylist = null }) { Text("Xoá Playlist", color = RedHoodCrimson) } } },
            confirmButton = { TextButton(onClick = { actionPlaylist = null }) { Text("Đóng", color = MetalGray) } }
        )
    }

    if (showCreatePlaylist) {
        var playlistName by remember { mutableStateOf(playlistToEdit?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false }, title = { Text(if (playlistToEdit == null) "Tạo Playlist Mới" else "Sửa Playlist", color = Color.White) }, containerColor = RedHoodSurface,
            text = {
                Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                    OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Tên Playlist") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(Modifier.height(12.dp)); Text("Chọn bài hát từ Folder gốc:", color = RedHoodCrimson, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        items(rootSongs) { song ->
                            val isChecked = selectedSongsForPlaylist.contains(song.uri.toString())
                            Row(modifier = Modifier.fillMaxWidth().clickable { selectedSongsForPlaylist = if (isChecked) selectedSongsForPlaylist - song.uri.toString() else selectedSongsForPlaylist + song.uri.toString() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = RedHoodCrimson)); Text(song.title, color = Color.White, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { if (playlistName.isNotBlank() && selectedSongsForPlaylist.isNotEmpty()) { if (playlistToEdit == null) viewModel.createPlaylist(playlistName, selectedSongsForPlaylist.toList()) else viewModel.updatePlaylist(playlistToEdit!!.id, selectedSongsForPlaylist.toList()); showCreatePlaylist = false } }, colors = ButtonDefaults.buttonColors(containerColor = RedHoodCrimson)) { Text("Lưu") } },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Huỷ", color = MetalGray) } }
        )
    }
}
@Composable
fun CassetteReel(rotationAngle: Float) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .padding(8.dp)
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotationAngle },
        contentAlignment = Alignment.Center
    ) {
        // Nền tối của đĩa quay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.DarkGray)
        }
        // Trục lõi trắng và các rãnh đen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFEEEEEE), size.width * 0.2f)
            val center = Offset(size.width / 2, size.height / 2)
            for (i in 0 until 6) {
                drawArc(
                    color = Color.Black,
                    startAngle = (i * 60).toFloat(),
                    sweepAngle = 20f,
                    useCenter = true,
                    topLeft = Offset(center.x - size.width * 0.2f, center.y - size.width * 0.2f),
                    size = Size(size.width * 0.4f, size.width * 0.4f)
                )
            }
        }
    }
}