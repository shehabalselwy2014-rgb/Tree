package com.example.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Gender
import com.example.presentation.components.JuthoorBrandLogo
import com.example.ui.theme.JuthoorHeroGradientEnd
import com.example.ui.theme.JuthoorHeroGradientStart

@Composable
fun OnboardingScreen(
    initialFamilyTitle: String,
    initialFamilyMotto: String,
    onComplete: (
        familyTitle: String,
        familyMotto: String,
        founderFirstName: String,
        founderMiddleName: String,
        founderFamilyName: String,
        founderGender: Gender,
        founderBirthDate: String,
    ) -> Unit,
    onSkip: () -> Unit,
) {
    var familyTitle by remember { mutableStateOf(initialFamilyTitle) }
    var familyMotto by remember { mutableStateOf(initialFamilyMotto) }
    var founderFirstName by remember { mutableStateOf("") }
    var founderMiddleName by remember { mutableStateOf("") }
    var founderFamilyName by remember { mutableStateOf("") }
    var founderGender by remember { mutableStateOf(Gender.MALE) }
    var founderBirthDate by remember { mutableStateOf("") }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(listOf(JuthoorHeroGradientStart, JuthoorHeroGradientEnd))
                        )
                        .padding(24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        JuthoorBrandLogo(
                            size = 72.dp,
                            onDarkBackground = true,
                            showBadgeContainer = true,
                        )
                        Text(
                            text = "مرحباً بك في جِذور",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "معالج التأسيس السريع: ثبّت اسم العائلة وأضف الجد المؤسس أو الفرد الأول للبدء فوراً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("هوية العائلة", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = familyTitle,
                            onValueChange = { familyTitle = it },
                            label = { Text("اسم العائلة / الشجرة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_family_title"),
                        )
                        OutlinedTextField(
                            value = familyMotto,
                            onValueChange = { familyMotto = it },
                            label = { Text("العبارة التعريفية (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_family_motto"),
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("الفرد الأول / الجد المؤسس (اختياري)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "يمكنك إدخال أول شخص الآن أو تخطي هذه الخطوة والإضافة لاحقاً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = founderFirstName,
                            onValueChange = { founderFirstName = it },
                            label = { Text("الاسم الأول") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_founder_first"),
                        )
                        OutlinedTextField(
                            value = founderMiddleName,
                            onValueChange = { founderMiddleName = it },
                            label = { Text("اسم الأب / الوسط (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_founder_middle"),
                        )
                        OutlinedTextField(
                            value = founderFamilyName,
                            onValueChange = { founderFamilyName = it },
                            label = { Text("اسم العائلة / اللقب (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_founder_family"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Gender.entries.forEach { option ->
                                FilterChip(
                                    selected = founderGender == option,
                                    onClick = { founderGender = option },
                                    label = { Text(option.arabicLabel) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = founderBirthDate,
                            onValueChange = { founderBirthDate = it },
                            label = { Text("سنة أو تاريخ الميلاد (مثل 1940 أو 1940-05-12)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_founder_birth"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        onComplete(
                            familyTitle,
                            familyMotto,
                            founderFirstName,
                            founderMiddleName,
                            founderFamilyName,
                            founderGender,
                            founderBirthDate,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_complete_button"),
                ) {
                    Text("بدء استخدام جِذور")
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_skip_button"),
                ) {
                    Text("تخطي والدخول للرئيسية مباشرة")
                }
            }
        }
    }
}
