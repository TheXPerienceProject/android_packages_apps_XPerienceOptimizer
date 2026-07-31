// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
// Enable only on android studio
// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.xperience.optimizer.R
import mx.xperience.optimizer.ui.adapters.Status
import mx.xperience.optimizer.ui.theme.XPerienceOptimizerTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class AppUiState(
    val name: String,
    val icon: Painter,
    val status: Status,
    val packageName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOptimizationScreen(
    progress: Int,
    currentAppName: String?,
    appList: List<AppUiState>,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Lógica de Ventana Deslizante (Sliding Window) para mostrar 5 apps
    val filteredList by remember(appList) {
        derivedStateOf {
            if (appList.isEmpty()) return@derivedStateOf emptyList<AppUiState>()

            val firstUnfinishedIndex = appList.indexOfFirst { it.status != Status.DONE }
            
            val startIndex = if (firstUnfinishedIndex == -1) {
                // Todo terminado, mostrar las últimas 5
                (appList.size - 5).coerceAtLeast(0)
            } else {
                // Empezar desde la que se está procesando, pero ajustar para mostrar siempre 5 si es posible
                val preferredStart = firstUnfinishedIndex
                // Asegurarse de que si faltan menos de 5, no nos pasemos del final
                if (preferredStart + 5 > appList.size) {
                    (appList.size - 5).coerceAtLeast(0)
                } else {
                    preferredStart
                }
            }
            
            appList.subList(startIndex, (startIndex + 5).coerceAtMost(appList.size))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_optimisation),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            OptimisationGauge(
                progress = progress.toFloat() / 100f,
                modifier = Modifier.size(240.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "${progress}%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = currentAppName?.let { stringResource(id = R.string.optimizing_app, it) }
                    ?: stringResource(id = R.string.optimization_title),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppListCard(
                appList = filteredList,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun OptimisationGauge(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = size.minDimension / 2 - strokeWidth
            
            val totalTicks = 80
            val startAngle = 135f
            val sweepAngle = 270f
            
            for (i in 0 until totalTicks) {
                val angle = startAngle + (sweepAngle / totalTicks) * i
                val angleRad = (angle * PI / 180.0)
                
                val innerRadius = radius - 15.dp.toPx()
                val outerRadius = radius
                
                val startX = center.x + innerRadius * cos(angleRad).toFloat()
                val startY = center.y + innerRadius * sin(angleRad).toFloat()
                
                val endX = center.x + outerRadius * cos(angleRad).toFloat()
                val endY = center.y + outerRadius * sin(angleRad).toFloat()
                
                val tickProgress = i.toFloat() / totalTicks
                val color = if (tickProgress <= animatedProgress) {
                    primaryColor
                } else {
                    trackColor
                }
                
                drawLine(
                    color = color,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_bolt),
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
fun AppListCard(
    appList: List<AppUiState>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(appList, key = { it.packageName }) { app ->
                AppItem(app = app)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun AppItem(
    app: AppUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.name,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        when (app.status) {
            Status.RUNNING -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sync),
                    contentDescription = "Optimizing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Status.DONE -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {}
        }
    }
}

// Enable only on android studio
//@Preview(showBackground = true)
@Composable
fun AppOptimizationScreenPreview() {
    XPerienceOptimizerTheme {
        AppOptimizationScreen(
            progress = 69,
            currentAppName = "Adobe Acrobat",
            appList = listOf(
                AppUiState("Adobe Acrobat", ColorPainter(Color.Red), Status.RUNNING, "com.adobe.reader"),
                AppUiState("Vocabulary", ColorPainter(Color(0xFFFDE68A)), Status.DONE, "com.vocab"),
                AppUiState("Security Centre", ColorPainter(Color(0xFF22D3EE)), Status.PENDING, "com.security"),
                AppUiState("Android System", ColorPainter(Color(0xFF4B5563)), Status.PENDING, "com.android.system"),
                AppUiState("Battery", ColorPainter(Color(0xFF10B981)), Status.PENDING, "com.battery")
            )
        )
    }
}
