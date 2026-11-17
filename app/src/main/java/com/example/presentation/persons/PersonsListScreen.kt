package com.example.presentation.persons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.Gender
import com.example.domain.model.MaritalStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.util.GenealogyMetricsCalculator
import com.example.domain.util.HijriDateConverter
import com.example.presentation.JuthoorUiState
import com.example.presentation.components.PersonAvatar

enum class PersonSortMode(val arabicLabel: String) {
    RECENT("الأحدث إضافة"),
    ALPHABETICAL("أبجدي (أ - ي)"),
    OLDEST_FIRST("الأكبر سناً"),
    YOUNGEST_FIRST("الأصغر سناً"),
}

@Composable
fun PersonsListScreen(
    uiState: JuthoorUiState,
    onAddPerson: () -> Unit,
    onOpenPersonProfile: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PersonSortMode.RECENT) }
    var genderFilter by remember { mutableStateOf<Gender?>(null) }
    var maritalFilter by remember { mutableStateOf<MaritalStatus?>(null) }
    var branchFilterId by remember { mutableStateOf<String?>(null) }
    var generationFilter by remember { mutableStateOf<Int?>(null) }

    val personsById = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val groupsById = remember(uiState.familyGroups) { uiState.familyGroups.associateBy { it.id } }
    val availableGenerations = remember(uiState.generationByPerson) {
        uiState.generationByPerson.values.distinct().sorted()
    }

    val parentNamesByChild = remember(uiState.parentChildLinks, personsById) {
        uiState.parentChildLinks.groupBy { it.childId }.mapValues { (_, links) ->
            links.mapNotNull { link ->
                if (link.relationshipType == ParentRelationshipType.BIOLOGICAL_FATHER ||
                    link.relationshipType == ParentRelationshipType.BIOLOGICAL_MOTHER ||
                    link.relationshipType == ParentRelationshipType.ADOPTIVE_GUARDIAN
                ) {
                    personsById[link.parentId]?.fullName
                } else null
            }
        }
    }

    val filteredPersons = remember(
        uiState.persons,
        searchQuery,
        sortMode,
        genderFilter,
        maritalFilter,
        branchFilterId,
        generationFilter,
        uiState.generationByPerson,
        parentNamesByChild,
        groupsById,
    ) {
        val q = searchQuery.trim().lowercase()
        uiState.persons.filter { person ->
            val matchesGender = genderFilter == null || person.gender == genderFilter
            val matchesMarital = maritalFilter == null || person.currentMaritalStatus == maritalFilter
            val matchesBranch = branchFilterId == null || person.familyGroupId == branchFilterId
            val matchesGen = generationFilter == null || uiState.generationByPerson[person.id] == generationFilter

            val branchName = person.familyGroupId?.let { groupsById[it]?.groupName }.orEmpty()
            val parentsText = parentNamesByChild[person.id].orEmpty().joinToString(" ")
            val matchesQuery = q.isEmpty() ||
                person.fullName.lowercase().contains(q) ||
                person.firstName.lowercase().contains(q) ||
                (person.familyName?.lowercase()?.contains(q) == true) ||
                branchName.lowercase().contains(q) ||
                parentsText.lowercase().contains(q)

            matchesGender && matchesMarital && matchesBranch && matchesGen && matchesQuery
        }.let { list ->
            when (sortMode) {
                PersonSortMode.RECENT -> list.sortedByDescending { it.createdAt }
                PersonSortMode.ALPHABETICAL -> list.sortedBy { it.fullName }
                PersonSortMode.OLDEST_FIRST -> list.sortedBy {
                    GenealogyMetricsCalculator.extractBirthYear(it.birthDate) ?: Int.MAX_VALUE
                }
                PersonSortMode.YOUNGEST_FIRST -> list.sortedByDescending {
                    GenealogyMetricsCalculator.extractBirthYear(it.birthDate) ?: Int.MIN_VALUE
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPerson,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("persons_fab_add"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة فرد جديد")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("ابحث بالاسم، اسم الأب أو الأم، أو الفرع العائلي") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = "مسح البحث")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("persons_search_input"),
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Sort row
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PersonSortMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = sortMode == mode,
                                    onClick = { sortMode = mode },
                                    label = { Text(mode.arabicLabel) },
                                )
                            }
                        }
                        // Gender & Marital & Generation & Branch filters
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Gender.entries.forEach { gender ->
                                FilterChip(
                                    selected = genderFilter == gender,
                                    onClick = {
                                        genderFilter = if (genderFilter == gender) null else gender
                                    },
                                    label = { Text(gender.arabicLabel) },
                                    modifier = Modifier.testTag("filter_gender_${gender.name.lowercase()}"),
                                )
                            }
                            MaritalStatus.entries.filter { it != MaritalStatus.UNKNOWN }.forEach { status ->
                                FilterChip(
                                    selected = maritalFilter == status,
                                    onClick = {
                                        maritalFilter = if (maritalFilter == status) null else status
                                    },
                                    label = { Text(status.arabicLabel) },
                                )
                            }
                            availableGenerations.forEach { gen ->
                                FilterChip(
                                    selected = generationFilter == gen,
                                    onClick = {
                                        generationFilter = if (generationFilter == gen) null else gen
                                    },
                                    label = { Text("الجيل $gen") },
                                )
                            }
                            uiState.familyGroups.forEach { group ->
                                FilterChip(
                                    selected = branchFilterId == group.id,
                                    onClick = {
                                        branchFilterId = if (branchFilterId == group.id) null else group.id
                                    },
                                    label = { Text("فرع: ${group.groupName}") },
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "النتائج (${filteredPersons.size} من ${uiState.persons.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (filteredPersons.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().testTag("persons_empty_state"),
                        ) {
                            Text(
                                text = if (uiState.persons.isEmpty()) {
                                    "قائمة العائلة فارغة حالياً. اضغط على زر (+) لإضافة أول فرد."
                                } else {
                                    "لا توجد نتائج مطابقة لمعايير البحث والتصفية الحالية."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    items(filteredPersons, key = { it.id }) { person ->
                        val branchName = person.familyGroupId?.let { groupsById[it]?.groupName }
                        val gen = uiState.generationByPerson[person.id] ?: 1
                        val birthFormatted = HijriDateConverter.formatDateForDisplay(
                            person.birthDate,
                            person.birthDatePrecision,
                            uiState.preferences.showHijriDates,
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPersonProfile(person.id) }
                                .testTag("person_list_card_${person.id}"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PersonAvatar(
                                    fullName = person.fullName,
                                    firstName = person.firstName,
                                    gender = person.gender,
                                    imagePath = person.profileImagePath,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(person.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${person.gender.arabicLabel} • الجيل $gen • الميلاد: $birthFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AssistChip(
                                            onClick = { onOpenPersonProfile(person.id) },
                                            label = { Text(person.currentMaritalStatus.arabicLabel) },
                                        )
                                        if (!branchName.isNullOrBlank()) {
                                            AssistChip(
                                                onClick = { onOpenPersonProfile(person.id) },
                                                label = { Text(branchName) },
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "عرض الملف الشخصي",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
