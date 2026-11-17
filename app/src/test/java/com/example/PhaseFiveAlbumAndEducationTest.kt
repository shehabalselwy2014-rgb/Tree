package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JuthoorDatabase
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.model.DatePrecision
import com.example.domain.model.EducationRecord
import com.example.domain.model.EventRecord
import com.example.domain.model.FamilyEventType
import com.example.domain.model.Gender
import com.example.domain.model.MediaType
import com.example.domain.model.Person
import com.example.domain.model.PhotoRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhaseFiveAlbumAndEducationTest {

    private lateinit var database: JuthoorDatabase
    private lateinit var repository: RoomGenealogyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JuthoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomGenealogyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `education events and multi-tagged family album photos persist and delete cleanly`() = runTest {
        val p1 = repository.savePerson(Person("p1", "سعيد محمد", "سعيد", gender = Gender.MALE)).getOrThrow()
        val p2 = repository.savePerson(Person("p2", "نورة علي", "نورة", gender = Gender.FEMALE)).getOrThrow()

        // Multiple education qualifications
        repository.saveEducation(
            EducationRecord(
                id = "e1",
                personId = p1.id,
                qualification = "بكالوريوس هندسة مدنية",
                institution = "جامعة الملك سعود",
                graduationYear = 2012,
            )
        ).getOrThrow()
        repository.saveEducation(
            EducationRecord(
                id = "e2",
                personId = p1.id,
                qualification = "ماجستير إدارة مشاريع",
                graduationYear = 2016,
            )
        ).getOrThrow()
        assertEquals(2, repository.observeEducationForPerson(p1.id).first().size)

        // Multiple events
        repository.saveEvent(
            EventRecord(
                id = "ev1",
                personId = p1.id,
                eventType = FamilyEventType.GRADUATION,
                eventDate = "2016-05-15",
                eventDatePrecision = DatePrecision.EXACT,
                description = "حفل التخرج",
            )
        ).getOrThrow()
        assertEquals(1, repository.observeEventsForPerson(p1.id).first().size)

        // Group photo tagged to multiple relatives
        repository.savePhoto(
            PhotoRecord(
                id = "ph1",
                personId = p1.id,
                taggedPersonIds = listOf(p1.id, p2.id),
                filePath = "/data/local/test_group.jpg",
                description = "صورة عيد الفطر",
                photoDate = "2024-04-10",
                photoDatePrecision = DatePrecision.EXACT,
                mediaType = MediaType.GROUP_PHOTO,
            )
        ).getOrThrow()

        val savedPhotos = repository.observeAllPhotos().first()
        assertEquals(1, savedPhotos.size)
        assertTrue(p1.id in savedPhotos.first().taggedPersonIds)
        assertTrue(p2.id in savedPhotos.first().taggedPersonIds)
        assertEquals(MediaType.GROUP_PHOTO, savedPhotos.first().mediaType)

        repository.deleteEducation("e1").getOrThrow()
        repository.deletePhoto("ph1").getOrThrow()
        assertEquals(1, repository.observeEducationForPerson(p1.id).first().size)
        assertEquals(0, repository.observeAllPhotos().first().size)
    }
}
