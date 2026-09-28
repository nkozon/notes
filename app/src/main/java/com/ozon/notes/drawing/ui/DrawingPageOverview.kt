package com.ozon.notes.drawing.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ozon.notes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PageThumbnail(
    pageIndex: Int,
    canvasType: CanvasType,
    pagePositions: List<Rect>,
    pageLayout: PageLayout,
    pdfRenderer: PdfRenderer?,
    pdfInfo: PdfInfo?,
    strokes: List<Stroke>,
    images: List<DrawingImage>,
    modifier: Modifier = Modifier
) {
    val pageRect = pagePositions.getOrNull(pageIndex)
    val pageWidth = pageRect?.width ?: (if (pageLayout.width > 0) pageLayout.width else 800f)
    val pageHeight = pageRect?.height ?: (if (pageLayout.height > 0) pageLayout.height else 1100f)
    val pageTop = pageRect?.top ?: (pageIndex * (pageHeight + pageLayout.spacing))
    val pageBottom = pageRect?.bottom ?: (pageTop + pageHeight)

    val pageStrokes = remember(strokes, pageIndex, pageTop, pageBottom) {
        strokes.filter { stroke ->
            stroke.points.any { it.y in pageTop..pageBottom }
        }
    }

    var thumbnailBitmap by remember(pageIndex, canvasType, pdfRenderer) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(pageIndex, canvasType, pdfRenderer, pageStrokes, pageWidth, pageHeight, pageTop) {
        withContext(Dispatchers.Default) {
            try {
                val maxDim = 180f
                val scaleFactor = (maxDim / maxOf(pageWidth, pageHeight)).coerceIn(0.05f, 0.35f)
                val thumbWidth = (pageWidth * scaleFactor).toInt().coerceAtLeast(1)
                val thumbHeight = (pageHeight * scaleFactor).toInt().coerceAtLeast(1)

                val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                bmp.eraseColor(android.graphics.Color.WHITE)

                if (canvasType == CanvasType.PDF && pdfRenderer != null && (pdfInfo == null || pageIndex < pdfInfo.pageCount)) {
                    val pageSize = pdfInfo?.pageSizes?.getOrNull(pageIndex) ?: PdfPageSize(pageWidth, pageHeight)
                    val pdfBmpWidth = (pageSize.width * scaleFactor).toInt().coerceAtLeast(1)
                    val pdfBmpHeight = (pageSize.height * scaleFactor).toInt().coerceAtLeast(1)
                    val pdfBmp = Bitmap.createBitmap(pdfBmpWidth, pdfBmpHeight, Bitmap.Config.ARGB_8888)
                    pdfBmp.eraseColor(android.graphics.Color.WHITE)

                    var page: PdfRenderer.Page? = null
                    try {
                        synchronized(pdfRenderer) {
                            page = pdfRenderer.openPage(pageIndex)
                            page.render(pdfBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                        val left = pageLayout.marginLeft * scaleFactor
                        val top = pageLayout.marginTop * scaleFactor
                        canvas.drawBitmap(pdfBmp, left, top, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pdfBmp.recycle()
                        page?.let { p ->
                            synchronized(pdfRenderer) { p.close() }
                        }
                    }
                }

                if (pageStrokes.isNotEmpty()) {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    val path = android.graphics.Path()

                    pageStrokes.forEach { stroke ->
                        if (stroke.points.size > 1) {
                            path.reset()
                            stroke.points.forEachIndexed { i, p ->
                                val x = p.x * scaleFactor
                                val y = (p.y - pageTop) * scaleFactor
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            paint.color = stroke.colorArgb
                            paint.strokeWidth = (stroke.width * scaleFactor).coerceAtLeast(1f)
                            canvas.drawPath(path, paint)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    thumbnailBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(pageWidth / pageHeight)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        val bmp = thumbnailBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PageOverviewItem(
    pageIndex: Int,
    pageCount: Int,
    isCurrentViewing: Boolean,
    canvasType: CanvasType,
    pagePositions: List<Rect>,
    pageLayout: PageLayout,
    pdfRenderer: PdfRenderer?,
    pdfInfo: PdfInfo?,
    strokes: List<Stroke>,
    images: List<DrawingImage>,
    onSelect: () -> Unit,
    onAddBefore: () -> Unit,
    onAddAfter: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentViewing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isCurrentViewing) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.82f),
                    contentColor = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isCurrentViewing) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Page Options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Page Before") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAddBefore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Page After") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAddAfter()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Page") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = pageIndex > 0,
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = pageIndex < pageCount - 1,
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Page", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            enabled = pageCount > 1,
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            PageThumbnail(
                pageIndex = pageIndex,
                canvasType = canvasType,
                pagePositions = pagePositions,
                pageLayout = pageLayout,
                pdfRenderer = pdfRenderer,
                pdfInfo = pdfInfo,
                strokes = strokes,
                images = images,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PageOverviewSidePanel(
    canvasType: CanvasType,
    pagePositions: List<Rect>,
    pageLayout: PageLayout,
    pdfRenderer: PdfRenderer?,
    pdfInfo: PdfInfo?,
    pageCount: Int,
    currentViewingPageIndex: Int,
    strokes: List<Stroke>,
    images: List<DrawingImage>,
    onClose: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onAddPageBefore: (Int) -> Unit,
    onAddPageAfter: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(top = 68.dp, bottom = 24.dp, end = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (canvasType == CanvasType.PDF) "PDF Pages" else "Pages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onAddPage,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Page", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pageCount) { index ->
                    PageOverviewItem(
                        pageIndex = index,
                        pageCount = pageCount,
                        isCurrentViewing = index == currentViewingPageIndex,
                        canvasType = canvasType,
                        pagePositions = pagePositions,
                        pageLayout = pageLayout,
                        pdfRenderer = pdfRenderer,
                        pdfInfo = pdfInfo,
                        strokes = strokes,
                        images = images,
                        onSelect = { onJumpToPage(index) },
                        onAddBefore = { onAddPageBefore(index) },
                        onAddAfter = { onAddPageAfter(index) },
                        onMoveUp = { onMovePage(index, index - 1) },
                        onMoveDown = { onMovePage(index, index + 1) },
                        onDuplicate = { onDuplicatePage(index) },
                        onDelete = { onDeletePage(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOverviewBottomSheet(
    canvasType: CanvasType,
    pagePositions: List<Rect>,
    pageLayout: PageLayout,
    pdfRenderer: PdfRenderer?,
    pdfInfo: PdfInfo?,
    pageCount: Int,
    currentViewingPageIndex: Int,
    strokes: List<Stroke>,
    images: List<DrawingImage>,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onAddPageBefore: (Int) -> Unit,
    onAddPageAfter: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (canvasType == CanvasType.PDF) "PDF Pages ($pageCount)" else "Pages ($pageCount)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = onAddPage,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Page")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pageCount) { index ->
                    PageOverviewItem(
                        pageIndex = index,
                        pageCount = pageCount,
                        isCurrentViewing = index == currentViewingPageIndex,
                        canvasType = canvasType,
                        pagePositions = pagePositions,
                        pageLayout = pageLayout,
                        pdfRenderer = pdfRenderer,
                        pdfInfo = pdfInfo,
                        strokes = strokes,
                        images = images,
                        onSelect = { onJumpToPage(index) },
                        onAddBefore = { onAddPageBefore(index) },
                        onAddAfter = { onAddPageAfter(index) },
                        onMoveUp = { onMovePage(index, index - 1) },
                        onMoveDown = { onMovePage(index, index + 1) },
                        onDuplicate = { onDuplicatePage(index) },
                        onDelete = { onDeletePage(index) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
