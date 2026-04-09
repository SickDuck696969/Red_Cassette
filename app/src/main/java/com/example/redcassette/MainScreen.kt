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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val RedHoodDark = Color(0xFF121212)
val RedHoodSurface = Color(0xFF1E1E1E)
val MetalGray = Color(0xFF757575)

// Danh sách màu chủ đạo (CÓ MÀU HỒNG)
val ThemeColors = listOf(
    Color(0xFFB71C1C), // Đỏ Crimson
    Color(0xFFE91E63), // Hồng Pink
    Color(0xFF9C27B0), // Tím Purple
    Color(0xFF2196F3), // Xanh dương Blue
    Color(0xFF4CAF50), // Xanh lá Green
    Color(0xFFFFC107)  // Vàng Amber
)

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

@Composable
fun SplashScreen(themeColor: Color, onTimeout: () -> Unit) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }.build()
    }

    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(themeColor),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.drawable.splash_gif,
            imageLoader = imageLoader,
            contentDescription = "Loading...",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .shadow(10.dp, CircleShape)
        )
    }
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
    val playlistBgUri by viewModel.playlistBackgroundUri.collectAsState()

    val themeColorInt by viewModel.appThemeColor.collectAsState()
    val themeColor = Color(themeColorInt)

    val labelBitmap = rememberImageBitmap(cassetteLabelUri)
    val playlistBgBitmap = rememberImageBitmap(playlistBgUri)

    val playbackList by viewModel.currentPlaybackList.collectAsState()
    val currentSongIndex by viewModel.currentSongIndexFlow.collectAsState()
    var showQueueBottomSheet by remember { mutableStateOf(false) }

    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(background = RedHoodDark, surface = RedHoodSurface, primary = themeColor, onPrimary = Color.White, onSurface = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val appBg = rememberImageBitmap(appBgUri)
            if (appBg != null) { Image(bitmap = appBg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.15f) }

            if (showSettings) {
                SettingsScreen(viewModel, themeColor, onBack = { showSettings = false })
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = currentPlaylistName ?: "Unknown",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { showQueueBottomSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )

                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

                        // TRUYỀN THÊM SONG TITLE VÀ THEME COLOR VÀO CASSETTE
                        CassetteTape(isPlaying = isPlaying, labelBitmap = labelBitmap, songTitle = currentSongTitle, themeColor = themeColor)

                        Spacer(modifier = Modifier.height(48.dp))

                        Slider(
                            value = progress,
                            onValueChange = { viewModel.isUserSeeking.value = true; viewModel.progress.value = it },
                            onValueChangeFinished = { viewModel.seekTo(progress); viewModel.isUserSeeking.value = false },
                            colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor, inactiveTrackColor = MetalGray)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) themeColor else MetalGray, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { viewModel.prevSong() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColor).clickable { viewModel.togglePlayPause() }, contentAlignment = Alignment.Center) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = { viewModel.nextSong() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = { viewModel.toggleRepeat() }) {
                                val repeatIcon = when (repeatMode) { RepeatMode.ONE -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat }
                                val repeatTint = if (repeatMode == RepeatMode.OFF) MetalGray else themeColor
                                Icon(repeatIcon, contentDescription = "Repeat", tint = repeatTint, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }

            if (showQueueBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQueueBottomSheet = false },
                    containerColor = RedHoodDark
                ) {
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                        if (playlistBgBitmap != null) {
                            Image(
                                bitmap = playlistBgBitmap,
                                contentDescription = "Playlist Background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                alpha = 0.08f
                            )
                        }

                        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            Text("Đang phát: ${currentPlaylistName}", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(playbackList) { index, song ->
                                    val isCurrent = index == currentSongIndex

                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrent) themeColor.copy(alpha = 0.3f) else Color.Transparent)
                                                .clickable {
                                                    viewModel.playSong(index)
                                                    showQueueBottomSheet = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = if (isCurrent) themeColor else MetalGray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = song.title,
                                                color = if (isCurrent) themeColor else Color.White,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (index < playbackList.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                thickness = 1.dp,
                                                color = MetalGray.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CassetteTape(isPlaying: Boolean, labelBitmap: ImageBitmap?, songTitle: String, themeColor: Color) {
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) while (true) rotationAngle.animateTo(targetValue = rotationAngle.value + 360f, animationSpec = tween(durationMillis = 4000, easing = LinearEasing))
        else rotationAngle.stop()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.58f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF333333), Color(0xFF1A1A1A))))
            .border(1.5.dp, Color(0xFF4D4D4D), RoundedCornerShape(12.dp))
            .shadow(16.dp, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.75f)
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEEEEEE))
        ) {
            if (labelBitmap != null) {
                Image(bitmap = labelBitmap, contentDescription = "Cassette Label", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                // Đổ chuẩn 100% màu chủ đạo làm nền cơ sở
                Box(modifier = Modifier.fillMaxSize().background(themeColor))
                // Phủ thêm Gradient ánh sáng: Nhạt (Trắng) bên trái -> Đậm (Đen) bên phải
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f), // Sáng nhạt bên trái
                            Color.Transparent,              // Giữ màu gốc ở giữa
                            Color.Black.copy(alpha = 0.5f)  // Đậm tối bên phải
                        )
                    )
                ))
            }

            // GIAO DIỆN TÊN BÀI HÁT (Giấy nhớ)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0C9A3)) // Màu giấy ngà vintage
                    .border(1.dp, Color(0xFFBCA680), RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                // THÊM HIỆU ỨNG basicMarquee CHỮ CHẠY TẠI ĐÂY
                Text(
                    text = songTitle,
                    modifier = Modifier.basicMarquee(),
                    color = Color(0xFF2A2A2A),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    maxLines = 1
                    // Đã bỏ TextOverflow.Ellipsis để chữ có thể trượt
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("A", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Column(horizontalAlignment = Alignment.End) {
                    Text("STEREO", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("C-60", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // CỬA SỔ KÍNH
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.40f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF121212))
                    .border(1.5.dp, Color(0xFF555555), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color(0xFF221A1A),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 14.dp.toPx()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CassetteReel(rotationAngle.value)
                    CassetteReel(rotationAngle.value)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.2f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF282828), Color(0xFF111111))))
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 16.dp.toPx()
            val screwRadius = 4.5.dp.toPx()

            val screws = listOf(
                Offset(padding, padding),
                Offset(size.width - padding, padding),
                Offset(padding, size.height - padding),
                Offset(size.width - padding, size.height - padding),
                Offset(size.width / 2, size.height - 10.dp.toPx())
            )

            screws.forEach { center ->
                drawCircle(color = Color(0xFF0A0A0A), radius = screwRadius + 1.dp.toPx(), center = center)
                drawCircle(color = Color(0xFF999999), radius = screwRadius, center = center)
                drawLine(Color(0xFF222222), start = Offset(center.x - screwRadius + 3f, center.y - screwRadius + 3f), end = Offset(center.x + screwRadius - 3f, center.y + screwRadius - 3f), strokeWidth = 2.5f)
                drawLine(Color(0xFF222222), start = Offset(center.x - screwRadius + 3f, center.y + screwRadius - 3f), end = Offset(center.x + screwRadius - 3f, center.y - screwRadius + 3f), strokeWidth = 2.5f)
            }

            val bottomY = size.height - (size.height * 0.1f)
            val holeRadius = 9.dp.toPx()
            drawCircle(color = Color.Black, radius = holeRadius, center = Offset(size.width * 0.28f, bottomY))
            drawCircle(color = Color.Black, radius = holeRadius, center = Offset(size.width * 0.72f, bottomY))
            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(size.width * 0.4f, bottomY))
            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(size.width * 0.6f, bottomY))
        }
    }
}

@Composable
fun CassetteReel(rotationAngle: Float) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .padding(2.dp)
            .graphicsLayer { rotationZ = rotationAngle },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val tapeRadius = size.width * 0.45f
            val plasticHubRadius = size.width * 0.22f
            val innerHoleRadius = size.width * 0.12f

            drawCircle(color = Color(0xFF1C1311), radius = tapeRadius)
            drawCircle(color = Color(0xFF2D201E), radius = tapeRadius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))

            drawCircle(color = Color(0xFFE5E5E5), radius = plasticHubRadius)
            drawCircle(color = Color(0xFFCCCCCC), radius = plasticHubRadius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))

            drawCircle(color = Color(0xFF121212), radius = innerHoleRadius)

            val toothLength = size.width * 0.07f
            for (i in 0 until 6) {
                val angle = (i * 60) * (Math.PI / 180)
                val startX = center.x + (innerHoleRadius - 2f) * Math.cos(angle).toFloat()
                val startY = center.y + (innerHoleRadius - 2f) * Math.sin(angle).toFloat()
                val endX = center.x + (innerHoleRadius + toothLength) * Math.cos(angle).toFloat()
                val endY = center.y + (innerHoleRadius + toothLength) * Math.sin(angle).toFloat()

                drawLine(
                    color = Color(0xFF121212),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 6.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: RedCassetteViewModel, themeColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val rootFolderUri by viewModel.rootFolderUri.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val rootSongs by viewModel.rootSongs.collectAsState()

    val appBgUri by viewModel.appBackgroundUri.collectAsState()
    val cassetteLabelUri by viewModel.cassetteLabelUri.collectAsState()
    val playlistBgUri by viewModel.playlistBackgroundUri.collectAsState()

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
    val playlistBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); viewModel.setPlaylistBackgroundUri(it.toString()) }
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
                Text("Màu sắc chủ đạo", color = themeColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    items(ThemeColors) { color ->
                        val isSelected = color == themeColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .clickable { viewModel.setAppThemeColor(color.toArgb()) }
                        )
                    }
                }
            }

            item {
                Text("Giao diện", color = themeColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { appBgLauncher.launch(arrayOf("image/*")) }, border = BorderStroke(1.dp, themeColor), modifier = Modifier.weight(1f)) {
                        Text(if (appBgUri == null) "Tải Nền Ứng dụng" else "Đổi Nền", color = Color.White)
                    }
                    if (appBgUri != null) { Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { viewModel.setAppBackground(null) }, colors = ButtonDefaults.buttonColors(containerColor = themeColor)) { Text("Xoá") } }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { playlistBgLauncher.launch(arrayOf("image/*")) }, border = BorderStroke(1.dp, themeColor), modifier = Modifier.weight(1f)) {
                        Text(if (playlistBgUri == null) "Tải Nền Danh sách nhạc" else "Đổi Nền Danh sách", color = Color.White)
                    }
                    if (playlistBgUri != null) { Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { viewModel.setPlaylistBackgroundUri(null) }, colors = ButtonDefaults.buttonColors(containerColor = themeColor)) { Text("Xoá") } }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Trang trí nhãn Cassette:", color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { cassetteLabelLauncher.launch(arrayOf("image/*")) }, border = BorderStroke(1.dp, themeColor), modifier = Modifier.weight(1f)) {
                        Text(if (cassetteLabelUri == null) "Tải Ảnh Lên" else "Đổi Ảnh", color = Color.White)
                    }
                    if (cassetteLabelUri != null) { Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { viewModel.setCassetteLabelUri(null) }, colors = ButtonDefaults.buttonColors(containerColor = themeColor)) { Text("Xoá") } }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("Thư mục chứa nhạc (.mp3)", color = themeColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, themeColor)) { Text(if (rootFolderUri == null) "Chọn Folder Gốc" else "Đã chọn thư mục", color = Color.White) }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Danh sách Playlist", color = themeColor, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { playlistToEdit = null; selectedSongsForPlaylist = emptySet(); showCreatePlaylist = true }) { Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = themeColor) }
                }
            }

            items(allPlaylists) { playlist ->
                val isSelected = selectedPlaylist?.id == playlist.id
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) themeColor.copy(alpha = 0.3f) else RedHoodSurface).border(1.dp, if (isSelected) themeColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { viewModel.selectPlaylist(playlist) }, onLongClick = { actionPlaylist = playlist }).padding(16.dp)) { Text(playlist.name, color = if (isSelected) themeColor else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
            }
        }
    }

    if (actionPlaylist != null) {
        AlertDialog(
            onDismissRequest = { actionPlaylist = null }, title = { Text("Tuỳ chọn: ${actionPlaylist!!.name}", color = Color.White) }, containerColor = RedHoodSurface,
            text = { Column { TextButton(onClick = { coroutineScope.launch { selectedSongsForPlaylist = viewModel.getUrisForPlaylist(actionPlaylist!!.id).toSet(); playlistToEdit = actionPlaylist; showCreatePlaylist = true; actionPlaylist = null } }) { Text("Chỉnh sửa (Thêm/Bớt bài hát)", color = Color.White) }; TextButton(onClick = { viewModel.deletePlaylist(actionPlaylist!!); actionPlaylist = null }) { Text("Xoá Playlist", color = themeColor) } } },
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
                    Spacer(Modifier.height(12.dp)); Text("Chọn bài hát từ Folder gốc:", color = themeColor, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        items(rootSongs) { song ->
                            val isChecked = selectedSongsForPlaylist.contains(song.uri.toString())
                            Row(modifier = Modifier.fillMaxWidth().clickable { selectedSongsForPlaylist = if (isChecked) selectedSongsForPlaylist - song.uri.toString() else selectedSongsForPlaylist + song.uri.toString() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = themeColor)); Text(song.title, color = Color.White, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { if (playlistName.isNotBlank() && selectedSongsForPlaylist.isNotEmpty()) { if (playlistToEdit == null) viewModel.createPlaylist(playlistName, selectedSongsForPlaylist.toList()) else viewModel.updatePlaylist(playlistToEdit!!.id, selectedSongsForPlaylist.toList()); showCreatePlaylist = false } }, colors = ButtonDefaults.buttonColors(containerColor = themeColor)) { Text("Lưu") } },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Huỷ", color = MetalGray) } }
        )
    }
}