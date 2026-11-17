package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.Gender
import com.example.ui.theme.JuthoorFemaleAccent
import com.example.ui.theme.JuthoorMaleAccent
import java.io.File

@Composable
fun PersonAvatar(
    fullName: String,
    firstName: String,
    gender: Gender,
    imagePath: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (gender == Gender.MALE) JuthoorMaleAccent else JuthoorFemaleAccent
    val hasValidFile = !imagePath.isNullOrBlank() && File(imagePath).exists()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasValidFile) {
            AsyncImage(
                model = File(imagePath!!),
                contentDescription = "صورة $fullName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val initial = firstName.trim().take(1).ifEmpty { fullName.trim().take(1) }.ifEmpty { "؟" }
            Text(
                text = initial,
                style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
